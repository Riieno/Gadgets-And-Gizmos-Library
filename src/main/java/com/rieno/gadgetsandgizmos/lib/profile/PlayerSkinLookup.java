package com.rieno.gadgetsandgizmos.lib.profile;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

// Resolve a Minecraft profile name to its current signed skin texture
public final class PlayerSkinLookup {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Cache<String, CompletableFuture<LookupResult>> LOOKUPS = CacheBuilder.newBuilder()
            .maximumSize(128).expireAfterWrite(1, TimeUnit.MINUTES).build();
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_RESPONSE_BYTES = 32 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final URI PROFILE_LOOKUP_URI =
            URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final URI SESSION_PROFILE_URI =
            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player skin lookup
    private PlayerSkinLookup(){
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve one valid Minecraft player name through Mojang's profile services
    public static CompletableFuture<LookupResult> lookup(String playerName){
        String name = normalizePlayerName(playerName);
        if(name.isBlank()) return CompletableFuture.completedFuture(LookupResult.notFound());
        try{
            return LOOKUPS.get(name.toLowerCase(Locale.ROOT), () -> lookup(name, 1));
        }catch(ExecutionException err){
            return CompletableFuture.completedFuture(LookupResult.unavailable());
        }
    }

    // Normalize one Minecraft player name
    public static String normalizePlayerName(String playerName){
        String name = playerName == null ? "" : playerName.strip();
        return name.matches("[A-Za-z0-9_]{3,16}") ? name : "";
    }

    // Read the skin from a resolved profile without making a network request
    public static LookupResult fromProfile(GameProfile profile){
        if(profile == null || normalizePlayerName(profile.getName()).isBlank()) return LookupResult.notFound();
        JsonObject data = new JsonObject();
        JsonArray properties = new JsonArray();
        for(Property val : profile.getProperties().get("textures")){
            JsonObject property = new JsonObject();
            property.addProperty("name", "textures");
            property.addProperty("value", val.value());
            properties.add(property);
        }
        data.add("properties", properties);
        try{
            SkinData skin = skinData(data);
            return skin.url().isBlank() ? LookupResult.unavailable() : LookupResult.resolved(profile, skin);
        }catch(RuntimeException err){
            return LookupResult.unavailable();
        }
    }

    // Resolve one player name with a capped retry count for transient failures
    private static CompletableFuture<LookupResult> lookup(String playerName, int attempt){
        return lookupOnce(playerName).handle((result, error) -> {
            if(error == null) return CompletableFuture.completedFuture(result);
            if(attempt >= MAX_ATTEMPTS) return CompletableFuture.completedFuture(LookupResult.unavailable());
            return lookup(playerName, attempt + 1);
        }).thenCompose(result -> result);
    }

    // Resolve one profile and its texture value
    private static CompletableFuture<LookupResult> lookupOnce(String playerName){
        return send(PROFILE_LOOKUP_URI.resolve(playerName)).thenCompose(profileResponse -> {
            if(notFound(profileResponse.statusCode())) return CompletableFuture.completedFuture(LookupResult.notFound());
            if(profileResponse.statusCode() / 100 != 2) return failedLookup();
            JsonObject profile = jsonObject(profileResponse.body());
            String id = string(profile, "id");
            String resolvedName = string(profile, "name");
            if(!id.matches("[a-fA-F0-9]{32}") || normalizePlayerName(resolvedName).isBlank()){
                return failedLookup();
            }
            return send(SESSION_PROFILE_URI.resolve(id + "?unsigned=false")).thenApply(sessionResponse -> {
                if(notFound(sessionResponse.statusCode())) return LookupResult.notFound();
                if(sessionResponse.statusCode() / 100 != 2) throw new IllegalStateException("Profile unavailable");
                JsonObject session = jsonObject(sessionResponse.body());
                SkinData skin = skinData(session);
                if(skin.url().isBlank()) throw new IllegalStateException("Skin unavailable");
                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(........)(....)(....)(....)(............)", "$1-$2-$3-$4-$5"));
                GameProfile resolved = new GameProfile(uuid, resolvedName);
                for(JsonElement val : session.getAsJsonArray("properties")){
                    if(!val.isJsonObject()) continue;
                    JsonObject property = val.getAsJsonObject();
                    if(!"textures".equals(string(property, "name"))) continue;
                    String value = string(property, "value"), signature = string(property, "signature");
                    resolved.getProperties().put("textures", signature.isBlank()
                            ? new Property("textures", value) : new Property("textures", value, signature));
                }
                return LookupResult.resolved(resolved, skin);
            });
        });
    }

    // Send one bounded profile-service request
    private static CompletableFuture<HttpResponse<String>> send(URI uri){
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Create-Gadgets-and-Gizmos-Worker/1")
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if(response.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES){
                        throw new IllegalStateException("Profile response too large");
                    }
                    return response;
                });
    }

    // Read the signed skin URL and arm model from Mojang's encoded texture property
    private static SkinData skinData(JsonObject profile){
        JsonArray properties = profile.has("properties") && profile.get("properties").isJsonArray()
                ? profile.getAsJsonArray("properties") : new JsonArray();
        for(JsonElement element : properties){
            if(!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if(!"textures".equals(string(property, "name"))) continue;
            String value = string(property, "value");
            if(value.isBlank()) return SkinData.EMPTY;
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject textures = jsonObject(decoded);
            if(!textures.has("textures") || !textures.get("textures").isJsonObject()) return SkinData.EMPTY;
            JsonObject skin = textures.getAsJsonObject("textures").has("SKIN")
                    && textures.getAsJsonObject("textures").get("SKIN").isJsonObject()
                    ? textures.getAsJsonObject("textures").getAsJsonObject("SKIN") : null;
            String url = skin == null ? "" : secureTextureUrl(string(skin, "url"));
            JsonObject metadata = skin != null && skin.has("metadata") && skin.get("metadata").isJsonObject()
                    ? skin.getAsJsonObject("metadata") : null;
            boolean slim = metadata != null && "slim".equalsIgnoreCase(string(metadata, "model"));
            return url.isBlank() ? SkinData.EMPTY : new SkinData(url, slim);
        }
        return SkinData.EMPTY;
    }

    // Convert Mojang's legacy texture URL to the dedicated HTTPS texture host
    private static String secureTextureUrl(String skinUrl){
        if(skinUrl == null || skinUrl.isBlank() || skinUrl.length() > 2048) return "";
        try{
            URI uri = URI.create(skinUrl);
            boolean scheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if(!scheme || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null
                    || uri.getPath() == null || !uri.getPath().startsWith("/texture/")){
                return "";
            }
            return new URI("https", null, "textures.minecraft.net", -1,
                    uri.getPath(), uri.getQuery(), null).toString();
        }catch(IllegalArgumentException ignored){
            return "";
        }catch(java.net.URISyntaxException ignored){
            return "";
        }
    }

    // Parse one expected JSON object
    private static JsonObject jsonObject(String json){
        JsonElement element = JsonParser.parseString(json == null ? "" : json);
        if(!element.isJsonObject()) throw new IllegalStateException("Invalid profile response");
        return element.getAsJsonObject();
    }

    // Read one JSON string field
    private static String string(JsonObject object, String key){
        if(object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    // Check whether the profile service did not find a player
    private static boolean notFound(int status){
        return status == 204 || status == 404;
    }

    // Create one failed lookup future
    private static CompletableFuture<LookupResult> failedLookup(){
        return CompletableFuture.failedFuture(new IllegalStateException("Profile unavailable"));
    }

    // Store one skin texture address and its matching player arm model
    private record SkinData(String url, boolean slim){
        private static final SkinData EMPTY = new SkinData("", false);
    }

    // Store one completed skin lookup
    public record LookupResult(boolean found, String playerName, String skinUrl, boolean slim,
                               Status status, String message, GameProfile profile){
        // Create a resolved player skin result
        private static LookupResult resolved(GameProfile profile, SkinData skin){
            return new LookupResult(true, profile.getName(), skin.url(), skin.slim(), Status.RESOLVED, "Skin applied", profile);
        }

        // Create a missing player result
        private static LookupResult notFound(){
            return new LookupResult(false, "", "", false, Status.PLAYER_DOES_NOT_EXIST, "Player not found", null);
        }

        // Create an unavailable profile-service result
        private static LookupResult unavailable(){
            return new LookupResult(false, "", "", false, Status.UNAVAILABLE, "Skin lookup failed; using Steve", null);
        }
    }

    // Distinguish an unknown player from a profile-service failure
    public enum Status {
        RESOLVED,
        PLAYER_DOES_NOT_EXIST,
        UNAVAILABLE
    }
}
