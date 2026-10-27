package com.rieno.gadgetsandgizmos.lib.storage;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Attribution travels with a shared document independently of its filename and storage location.
public record SharedDocumentMetadata(String authorName, UUID authorId, Instant sharedAt,
                                     String application, String applicationVersion, String gameVersion){
    private static final List<String> FIELDS = List.of("metadataVersion", "author", "sharedAt", "createdAt",
            "application", "applicationVersion", "gameVersion", "metadata", "description", "tags", "license", "importedAt");

    public SharedDocumentMetadata{
        authorName = authorName == null || authorName.isBlank() ? "Unknown" : authorName.strip();
        sharedAt = sharedAt == null ? Instant.now() : sharedAt;
        application = application == null ? "" : application;
        applicationVersion = applicationVersion == null ? "" : applicationVersion;
        gameVersion = gameVersion == null ? "" : gameVersion;
    }

    public void apply(JsonObject document){
        var author = new JsonObject();
        author.addProperty("name", authorName);
        author.addProperty("uuid", authorId == null ? "" : authorId.toString());
        document.addProperty("metadataVersion", 1);
        document.add("author", author);
        document.addProperty("sharedAt", sharedAt.toString());
        if(!document.has("createdAt")) document.addProperty("createdAt", sharedAt.toString());
        document.addProperty("application", application);
        document.addProperty("applicationVersion", applicationVersion);
        document.addProperty("gameVersion", gameVersion);
    }

    public static void copy(JsonObject source, JsonObject target){
        if(source == null) return;
        for(String key : FIELDS) if(source.has(key)) target.add(key, source.get(key).deepCopy());
    }
}
