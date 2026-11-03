package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLauncherLayout;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletLauncherResources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TabletLauncherTest{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Test
    void discoversNamespacedTexturesAndPackTitles(){
        ResourceLocation first = texture("first", "ice");
        ResourceLocation second = texture("second", "ice");
        Map<ResourceLocation, Resource> files = new HashMap<>();
        files.put(first, resource(""));
        files.put(second, resource(""));
        files.put(ResourceLocation.parse("first:textures/gui/apps/screens/launcher_.png"), resource(""));
        files.put(ResourceLocation.parse("first:textures/gui/apps/screens/settings.png"), resource(""));
        files.put(ResourceLocation.parse("first:tablet/launcher_themes/ice.json"),
                resource("{\"texture\":\"" + first + "\",\"title\":\"Frozen Lake\"}"));
        List<TabletLauncherThemes.Theme> themes = TabletLauncherThemes.discover(new TestResources(files));
        assertEquals(List.of(first, second), themes.stream().map(TabletLauncherThemes.Theme::texture).toList());
        assertEquals("Frozen Lake", themes.getFirst().title());
        assertEquals("Ice", themes.getLast().title());
        assertEquals(themes.getFirst(), TabletLauncherThemes.Theme.fromTag(themes.getFirst().toTag()));
    }

    @Test
    void brokenMetadataDoesNotHideOtherThemes(){
        ResourceLocation texture = texture("test", "iron_farm");
        List<TabletLauncherThemes.Theme> themes = TabletLauncherThemes.discover(new TestResources(Map.of(
                texture, resource(""),
                ResourceLocation.parse("test:tablet/launcher_themes/broken.json"), resource("{"),
                ResourceLocation.parse("test:tablet/launcher_themes/invalid.json"),
                resource("{\"texture\":\"test:textures/gui/apps/settings.png\"}"))));
        assertEquals(List.of(new TabletLauncherThemes.Theme(texture, "Iron Farm")), themes);
    }

    @Test
    void clientReloadRemovesMissingThemesAndAppliesServerNames(){
        ResourceLocation first = texture("test", "streaks");
        ResourceLocation second = texture("extra", "forest");
        TabletLauncherResources.reload(new TestResources(Map.of(first, resource(""), second, resource(""))));
        ListTag serverThemes = new ListTag();
        serverThemes.add(new TabletLauncherThemes.Theme(second, "Woodland").toTag());
        serverThemes.add(new TabletLauncherThemes.Theme(texture("missing", "desert"), "Desert").toTag());
        assertEquals(2, TabletLauncherResources.themes(serverThemes).size());
        assertEquals("Woodland", TabletLauncherResources.themes(serverThemes).getFirst().title());
        assertEquals(second, TabletLauncherResources.resolve(second.toString(), first));
        TabletLauncherResources.reload(new TestResources(Map.of(first, resource(""))));
        assertEquals(List.of(first), TabletLauncherResources.themes(serverThemes).stream()
                .map(TabletLauncherThemes.Theme::texture).toList());
        assertEquals(first, TabletLauncherResources.resolve(second.toString(), first));
        assertEquals(first, TabletLauncherResources.resolve("aurora", first));
        TabletLauncherResources.reload(ResourceManager.Empty.INSTANCE);
    }

    @Test
    void usageIsIsolatedByTabletAndExcludesUninstalledApps(){
        MemoryStorage storage = new MemoryStorage();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TabletAppDefinition low = app("first:tools");
        TabletAppDefinition high = app("second:tools");
        TabletAppDefinition removed = app("test:removed");
        TabletAppUsage.recordLaunch(storage, first, low.id());
        TabletAppUsage.recordLaunch(storage, first, high.id());
        TabletAppUsage.recordLaunch(storage, first, high.id());
        for(int idx = 0; idx < 4; idx++) TabletAppUsage.recordLaunch(storage, first, removed.id());
        assertEquals(List.of(high, low), TabletAppUsage.mostUsed(List.of(low, high), storage.tablet(first), 4));
        assertEquals(List.of(low, high), TabletAppUsage.mostUsed(List.of(low, high), storage.tablet(second), 4));
        assertEquals(List.of(high), TabletAppUsage.mostUsed(List.of(low, high), storage.tablet(first), 1));
        assertTrue(TabletAppUsage.mostUsed(List.of(low, high), storage.tablet(first), 0).isEmpty());
        CompoundTag restored = storage.tablet(first).copy();
        assertEquals(List.of(high, low), TabletAppUsage.mostUsed(List.of(low, high), restored, 4));
    }

    @Test
    void launchCountsDoNotWrapAround(){
        MemoryStorage storage = new MemoryStorage();
        UUID tabletId = UUID.randomUUID();
        ResourceLocation appId = ResourceLocation.parse("test:tools");
        storage.updateTablet(tabletId, settings -> {
            CompoundTag usage = new CompoundTag();
            usage.putLong(appId.toString(), Long.MAX_VALUE);
            settings.put("AppUsage", usage);
            return settings;
        });
        TabletAppUsage.recordLaunch(storage, tabletId, appId);
        assertEquals(Long.MAX_VALUE, storage.tablet(tabletId).getCompound("AppUsage").getLong(appId.toString()));
    }

    @Test
    void projectedControlsStayInsideTheirPaintedRegions(){
        for(int[] size : new int[][]{{320, 180}, {492, 286}, {984, 572}}){
            var bounds = TabletLauncherLayout.bounds(20, 30, size[0], size[1]);
            var prev = TabletLauncherLayout.pageButton(bounds, false);
            for(int idx = 0; idx < TabletLauncherLayout.APPS_PER_PAGE; idx++){
                var rect = TabletLauncherLayout.app(bounds, idx);
                assertTrue(rect.top() + rect.height() <= prev.top() + 1);
                assertTrue(rect.left() >= bounds.left());
                assertTrue(rect.left() + rect.width() <= bounds.left() + bounds.width());
                assertTrue(rect.contains(rect.left() + rect.width() / 2.0D, rect.top() + 1));
            }
            for(int idx = 0; idx < TabletLauncherLayout.QUICK_LAUNCH_SIZE; idx++){
                var rect = TabletLauncherLayout.quickLaunch(bounds, idx);
                double x = (rect.left() - bounds.left()) / bounds.scaleX();
                double y = (rect.top() - bounds.top()) / bounds.scaleY();
                assertTrue(x >= 51 && x + rect.width() / bounds.scaleX() <= 270);
                assertTrue(y >= 137 && y + rect.height() / bounds.scaleY() <= 176);
            }
        }
        assertEquals(1, TabletLauncherLayout.pageCount(0));
        assertEquals(1, TabletLauncherLayout.pageCount(10));
        assertEquals(2, TabletLauncherLayout.pageCount(11));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static ResourceLocation texture(String namespace, String name){
        return ResourceLocation.fromNamespaceAndPath(namespace, TabletLauncherThemes.TEXTURE_DIRECTORY + "/launcher_" + name + ".png");
    }

    private static Resource resource(String text){
        return new Resource(null, () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static TabletAppDefinition app(String id){
        return new TabletAppDefinition(ResourceLocation.parse(id), null, null, 0, List.of());
    }

    private record TestResources(Map<ResourceLocation, Resource> files) implements ResourceManager{
        @Override public Set<String> getNamespaces(){ return files.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet()); }
        @Override public Optional<Resource> getResource(ResourceLocation id){ return Optional.ofNullable(files.get(id)); }
        @Override public List<Resource> getResourceStack(ResourceLocation id){ return getResource(id).stream().toList(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter){
            return files.entrySet().stream().filter(entry -> entry.getKey().getPath().startsWith(path + "/") && filter.test(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter){
            return listResources(path, filter).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
        }
        @Override public Stream<PackResources> listPacks(){ return Stream.empty(); }
    }

    private static final class MemoryStorage implements TabletStorage{
        private final Map<UUID, CompoundTag> tablets = new HashMap<>();
        @Override public CompoundTag tablet(UUID tabletId){ return tablets.getOrDefault(tabletId, new CompoundTag()).copy(); }
        @Override public CompoundTag app(UUID tabletId, ResourceLocation appId){ return new CompoundTag(); }
        @Override public Set<ResourceLocation> installedApps(UUID tabletId){ return Set.of(); }
        @Override public CompoundTag updateTablet(UUID tabletId, UnaryOperator<CompoundTag> mutation){
            CompoundTag next = mutation.apply(tablet(tabletId));
            tablets.put(tabletId, next.copy());
            return next.copy();
        }
        @Override public CompoundTag updateApp(UUID tabletId, ResourceLocation appId, UnaryOperator<CompoundTag> mutation){ return new CompoundTag(); }
        @Override public boolean setInstalled(UUID tabletId, ResourceLocation appId, boolean installed){ return false; }
    }
}
