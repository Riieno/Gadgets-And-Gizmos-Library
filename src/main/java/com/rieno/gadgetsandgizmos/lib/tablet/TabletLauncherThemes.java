package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

// Discover painted launcher textures and optional theme names from packs
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class TabletLauncherThemes{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String TEXTURE_DIRECTORY = "textures/gui/apps/screens";
    public static final String DATA_DIRECTORY = "tablet/launcher_themes";
    private static final String TEXTURE_PREFIX = TEXTURE_DIRECTORY + "/launcher_";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<Theme> dataPackThemes = List.of();

    private TabletLauncherThemes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Identify textures that use the shared painted launcher layout
    public static boolean isLauncherTexture(ResourceLocation id){
        if(id == null) return false;
        String path = id.getPath();
        return path.startsWith(TEXTURE_PREFIX) && path.endsWith(".png")
                && path.length() > TEXTURE_PREFIX.length() + 4
                && path.indexOf('/', TEXTURE_PREFIX.length()) < 0;
    }

    // Discover every namespace and let pack metadata override generated titles
    public static List<Theme> discover(ResourceManager resources){
        Map<ResourceLocation, Theme> themes = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        resources.listResources(TEXTURE_DIRECTORY, TabletLauncherThemes::isLauncherTexture)
                .keySet().forEach(id -> themes.put(id, new Theme(id, "")));
        resources.listResources(DATA_DIRECTORY, id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try(var reader = entry.getValue().openAsReader()){
                        JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                        ResourceLocation texture = ResourceLocation.tryParse(data.get("texture").getAsString());
                        if(!isLauncherTexture(texture)) throw new IllegalArgumentException("Invalid launcher texture");
                        String title = data.has("title") ? data.get("title").getAsString() : "";
                        themes.put(texture, new Theme(texture, title));
                    }catch(IOException | RuntimeException err){
                        LOGGER.warn("[G&G-LIB][Tablet] - Could not load launcher theme {}", entry.getKey(), err);
                    }
                });
        return List.copyOf(themes.values());
    }

    // Get the server pack entries for a tablet settings snapshot
    public static List<Theme> dataPackThemes(){
        return dataPackThemes;
    }

    // Refresh server themes when data packs reload
    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent evt){
        evt.addListener((ResourceManagerReloadListener)resources -> dataPackThemes = discover(resources));
    }

    // Clear themes before another server starts in this process
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent evt){
        dataPackThemes = List.of();
    }

    // Keep texture identifiers stable when themes are renamed
    public record Theme(ResourceLocation texture, String title){
        public Theme{
            if(!isLauncherTexture(texture)) throw new IllegalArgumentException("Invalid launcher texture: " + texture);
            title = title == null ? "" : title.strip();
            if(title.isEmpty()){
                String name = texture.getPath().substring(TEXTURE_PREFIX.length(), texture.getPath().length() - 4);
                StringBuilder res = new StringBuilder();
                for(String word : name.split("_")){
                    if(word.isEmpty()) continue;
                    if(!res.isEmpty()) res.append(' ');
                    res.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
                }
                title = res.toString();
            }
            title = title.substring(0, Math.min(64, title.length()));
        }

        // Write an entry for a settings snapshot
        public CompoundTag toTag(){
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", texture.toString());
            tag.putString("Title", title);
            return tag;
        }

        // Read an entry without accepting unrelated textures
        public static Theme fromTag(CompoundTag tag){
            ResourceLocation texture = ResourceLocation.tryParse(tag.getString("Id"));
            return isLauncherTexture(texture) ? new Theme(texture, tag.getString("Title")) : null;
        }
    }
}
