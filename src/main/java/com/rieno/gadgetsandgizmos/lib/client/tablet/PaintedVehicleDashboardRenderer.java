package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.render.LoadedTerrainMap;
import com.rieno.gadgetsandgizmos.lib.client.render.NavigationMapLines;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapData;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationHeading;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Share a vehicle dashboard and its embedded ship-resource browser between tablet hosts
public class PaintedVehicleDashboardRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final int TEXT = 0xFFE5E5E5;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int GREEN = 0xFF00D995;
    private final ResourceLocation atlas;
    private final ResourceLocation mediaApp;
    private final String mediaTab;
    private final GuiNineSlice panel;
    private final GuiNineSlice button;
    private final PaintedItemCard itemCard;

    private static final class State implements TabletAppClientState, AutoCloseable{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final ListScrollState scroll = new ListScrollState();
        private final GuiScrollbar bar = new GuiScrollbar(0xFF202020, 0xFF888888);
        private final List<Hit> hits = new ArrayList<>();
        private final LoadedTerrainMap map;
        private final NavigationHeading heading = new NavigationHeading();
        private long headingTime;
        private String category = "items";
        private String filter = "";
        private boolean navigation;
        private ResourceLocation disc;
        private MusicDiscCatalog.Track track;
        private CompoundTag mapTag;
        private NavigationMapData navigationMap = new NavigationMapData(List.of(), null);

        private State(ResourceLocation owner){ map = new LoadedTerrainMap(owner); }
        @Override public void close(){ map.close(); }
    }

    private record Hit(TabletLayout.Rect area, Runnable action){}

    public PaintedVehicleDashboardRenderer(ResourceLocation atlas, ResourceLocation mediaApp, String mediaTab){
        this(atlas, mediaApp, mediaTab, atlas);
    }

    public PaintedVehicleDashboardRenderer(ResourceLocation atlas, ResourceLocation mediaApp, String mediaTab, ResourceLocation manifestContents){
        this.atlas = atlas;
        this.mediaApp = mediaApp;
        this.mediaTab = mediaTab;
        panel = PaintedGuiStyle.slice(region(192, 245, 128, 27), 9, 9, 9, 1);
        button = PaintedGuiStyle.slice(region(178, 331, 142, 26), 2, 2, 2, 2);
        itemCard = new PaintedItemCard(manifestContents);
    }

    @Override public TabletAppClientState createScreenState(){ return new State(atlas); }
    @Override public boolean ownsTabletSurface(){ return true; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State)raw;
        var bounds = bounds(ctx);
        var local = local(ctx, bounds);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            region(0, 0, 320, 180).draw(ctx.graphics(), 0, 0);
            state.panel.begin(local, false);
            state.hits.clear();
            if("manifest".equals(ctx.tab().id())) manifest(local, state);
            else if("stats".equals(ctx.tab().id())) stats(local, state);
            else dashboard(local, state);
            launcher(local, state);
            if(ctx.data().contains("Error")){
                panel.draw(ctx.graphics(), 54, 139, 214, 12);
                text(local, ctx.data().getString("Error"), 58, 140, 206, 10, .65F, 0xFFFF9494, true);
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    private void dashboard(TabletAppClientContext ctx, State state){
        var data = ctx.data();
        if(data.getBoolean("Available")){
            var pos = new Vec3(data.getDouble("X"), data.getDouble("Y"), data.getDouble("Z"));
            var level = Minecraft.getInstance().level;
            double seconds = 0;
            if(level != null && data.contains("SampleTick")){
                seconds = Math.clamp(level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
                        - data.getLong("SampleTick"), 0, 20) / 20D;
                pos = pos.add(data.getDouble("VelocityX") * seconds, data.getDouble("VelocityY") * seconds, data.getDouble("VelocityZ") * seconds);
            }
            long now = System.nanoTime();
            double elapsed = state.headingTime == 0 ? 0 : Math.max(0, now - state.headingTime) * 1.0E-9;
            state.headingTime = now;
            double yaw = state.heading.update(heading(data) - Math.toDegrees(data.getDouble("AngularVelocityY") * seconds), elapsed);
            var frame = state.map.render(ctx.graphics(), pos, yaw, 8, 20, 147, 126);
            var mapTag = data.getCompound("NavigationMap");
            if(state.mapTag == null || !state.mapTag.equals(mapTag)){
                state.mapTag = mapTag;
                state.navigationMap = NavigationMapData.fromTag(mapTag);
            }
            try(var clip = GuiPoseScissor.push(ctx.graphics(), 8, 20, 155, 146)){
                var route = state.navigationMap.routeFrom(pos);
                if(route.isEmpty() && data.getBoolean("HasTarget")) route = List.of(pos,
                        new Vec3(data.getDouble("TargetX"), data.getDouble("TargetY"), data.getDouble("TargetZ")));
                NavigationMapLines.draw(ctx.graphics(), frame, route, 8, 20, 0xFF00DAEC);
                ctx.graphics().pose().pushPose();
                ctx.graphics().pose().translate(0, 0, 2);
                try{
                    ctx.graphics().setColor(1, .7F, .25F, 1);
                    try{
                        for(var marker : state.navigationMap.ships()){
                            var point = frame.project(marker.position());
                            if(!frame.contains(point)) continue;
                            ctx.graphics().pose().pushPose();
                            try{
                                ctx.graphics().pose().translate(8 + point.x(), 20 + point.y(), 0);
                                ctx.graphics().pose().mulPose(new Quaternionf().rotationZ((float)Math.toRadians(NavigationHeading.normalize(marker.heading() - yaw))));
                                region(8, 279, 9, 16).draw(ctx.graphics(), -3, -5, 6, 10);
                            }finally{ ctx.graphics().pose().popPose(); }
                        }
                    }finally{ ctx.graphics().setColor(1, 1, 1, 1); }
                    if(data.getBoolean("HasTarget")){
                        var point = frame.project(new Vec3(data.getDouble("TargetX"), data.getDouble("TargetY"), data.getDouble("TargetZ")));
                        if(frame.contains(point)) region(48, 280, 10, 16).draw(ctx.graphics(), 8 + (int)point.x() - 4, 20 + (int)point.y() - 11, 8, 13);
                    }
                    float x = 8 + frame.width() / 2F, y = 20 + frame.height() / 2F;
                    region(22, 275, 21, 21).draw(ctx.graphics(), Math.round(x) - 9, Math.round(y) - 9, 18, 18);
                    region(8, 279, 9, 16).draw(ctx.graphics(), Math.round(x) - 3, Math.round(y) - 6, 6, 12);
                }finally{ ctx.graphics().pose().popPose(); }
                ctx.graphics().pose().pushPose();
                try{
                    ctx.graphics().pose().translate(0, 0, 5);
                    panel.draw(ctx.graphics(), 19, 122, 125, 26);
                    text(ctx, "Facing: " + data.getString("Facing").toUpperCase(Locale.ROOT), 30, 126, 107, 9, .75F, GREEN, false);
                    text(ctx, String.format(Locale.ROOT, "Speed: %.1f blocks/s", data.getDouble("Speed")), 30, 136, 107, 9, .75F, 0xFFF52D7F, false);
                }finally{ ctx.graphics().pose().popPose(); }
            }
            telemetry(ctx);
        }else{
            state.heading.reset();
            state.headingTime = 0;
            text(ctx, "Ship connection required", 17, 54, 128, 24, .9F, TEXT, true);
            text(ctx, "Connect to an initialized ship with an Advanced Contraption Controller", 15, 82, 132, 48, .65F, MUTED, true);
            text(ctx, "No ship telemetry", 194, 36, 112, 20, .8F, MUTED, true);
        }
        fuel(ctx);
        media(ctx, state);
        if(state.navigation){
            ctx.graphics().pose().pushPose();
            ctx.graphics().pose().translate(0, 0, 50);
            try{
                panel.draw(ctx.graphics(), 54, 87, 112, 63);
                control(ctx, state, 61, 94, 98, "Navigate to coordinates", data.getBoolean("Available"), () ->
                        ctx.ui().editText("X, Y, Z coordinates", "", 96, val -> ctx.actions().send("navigate", val)));
                control(ctx, state, 61, 107, 47, "Hover", data.getBoolean("Available"), () -> ctx.actions().send("hover", ""));
                control(ctx, state, 112, 107, 47, "Dock", data.getBoolean("Available"), () -> ctx.actions().send("dock", ""));
                control(ctx, state, 61, 120, 98, "Ship statistics", true, () -> ctx.ui().selectTab("stats"));
                text(ctx, data.getBoolean("Pilot") ? "Pilot schedule active" : "Manual trip", 62, 134, 96, 8, .65F, MUTED, true);
            }finally{ ctx.graphics().pose().popPose(); }
        }
    }

    private void telemetry(TabletAppClientContext ctx){
        var data = ctx.data();
        text(ctx, "Coordinates", 195, 21, 112, 11, .85F, MUTED, true);
        coordinates(ctx, "", 35);
        ctx.graphics().fill(205, 45, 299, 46, 0xFF777777);
        String eta = data.getLong("EtaSeconds") < 0 ? "--" : duration(data.getLong("EtaSeconds"));
        text(ctx, "Target  |  ETA: " + eta, 195, 49, 112, 10, .75F, GREEN, true);
        if(data.getBoolean("HasTarget")) coordinates(ctx, "Target", 62);
        else text(ctx, "No destination", 196, 62, 110, 10, .75F, MUTED, true);
    }

    private void coordinates(TabletAppClientContext ctx, String prefix, int y){
        var label = Component.empty();
        String[] axes = {"X", "Y", "Z"};
        int[] colors = {0xF52379, 0x17D348, 0x00ACEE};
        for(int idx = 0; idx < 3; idx++){
            if(idx != 0) label.append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
            label.append(Component.literal(axes[idx] + ": ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
            label.append(Component.literal(Long.toString(Math.round(ctx.data().getDouble(prefix + axes[idx]))))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(colors[idx]))));
        }
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), label, 193, y, 116, 11, .75F, TEXT, true);
    }

    private void fuel(TabletAppClientContext ctx){
        double ratio = Math.clamp(ctx.data().getDouble("Fuel"), 0, 1);
        int height = (int)Math.round(124 * ratio);
        if(height > 0) region(149, 363, 40, 9).draw(ctx.graphics(), 161, 145 - height, 3, height);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(159, 128, 0);
            ctx.graphics().pose().mulPose(new Quaternionf().rotationZ((float)-Math.PI / 2));
            text(ctx, "Fuel " + (ctx.data().getBoolean("FuelInfinite") ? "∞" : Math.round(ratio * 100) + "%"), 0, 0, 83, 7, .65F, TEXT, true);
        }finally{ ctx.graphics().pose().popPose(); }
    }

    private void media(TabletAppClientContext ctx, State state){
        boolean installed = ctx.data().getBoolean("MediaInstalled");
        var playback = MediaPlaybackState.fromTag(ctx.data().getCompound("Media"));
        if(!java.util.Objects.equals(state.disc, playback.discId())){
            state.disc = playback.discId();
            var level = Minecraft.getInstance().level;
            state.track = level == null ? null : MusicDiscCatalog.resolve(level.registryAccess(), state.disc);
        }
        Component title = Component.literal(installed ? "Choose music in Media" : "Install Media for music");
        if(state.track != null) title = state.track.title();
        GuiControlPreviews.drawFittedWrappedText(ctx.graphics(), ctx.font(), title, 197, 96, 108, 18, .7F, 2, 0xFFC0DCCB, true);
        var level = Minecraft.getInstance().level;
        double progress = playback.durationTicks() <= 0 || level == null ? 0
                : playback.elapsed(level.getGameTime()) / (double)playback.durationTicks();
        ctx.graphics().fill(207, 116, 299, 117, 0xFF737373);
        ctx.graphics().fill(207, 116, 207 + (int)(92 * progress), 117, TEXT);
        transport(ctx, state, 218, 97, "media_previous", installed);
        transport(ctx, state, 242, 121, "media_pause", installed);
        region(playback.playing() ? 67 : 81, 279, playback.playing() ? 11 : 12, 14).draw(ctx.graphics(), 247, 126, 10, 12);
        transport(ctx, state, 266, 149, "media_next", installed);
    }

    private void transport(TabletAppClientContext ctx, State state, int x, int u, String action, boolean enabled){
        region(u, 277, u == 121 ? 21 : 19, 19).draw(ctx.graphics(), x, 124, 19, 16);
        if(enabled) state.hits.add(new Hit(new TabletLayout.Rect(x, 122, 21, 21), () -> ctx.actions().send(action, "")));
    }

    private void launcher(TabletAppClientContext ctx, State state){
        boolean media = ctx.data().getBoolean("MediaInstalled"), manifest = ctx.data().getBoolean("ManifestInstalled");
        int count = 1 + (media ? 1 : 0) + (manifest ? 1 : 0);
        int x = 154 - (count - 1) * 12;
        launch(ctx, state, x, 48, 280, 10, 16, "Navigation", () -> {
            if(!"drive".equals(ctx.tab().id())){ state.navigation = false; ctx.ui().selectTab("drive"); }
            else state.navigation = !state.navigation;
        });
        if(media){
            x += 24;
            launch(ctx, state, x, 6, 258, 11, 13, "Media", () -> ctx.ui().openApp(mediaApp, mediaTab));
        }
        if(manifest){
            x += 24;
            launch(ctx, state, x, 29, 258, 10, 13, "Ship manifest", () -> { state.navigation = false; ctx.ui().selectTab("manifest"); });
        }
    }

    private void launch(TabletAppClientContext ctx, State state, int x, int u, int v, int width, int height, String title, Runnable action){
        region(121, 277, 21, 19).draw(ctx.graphics(), x, 151, 20, 20);
        region(u, v, width, height).drawFitted(ctx.graphics(), x + 5, 154, 10, 13);
        var area = new TabletLayout.Rect(x, 151, 20, 20);
        state.hits.add(new Hit(area, action));
        if(area.contains(ctx.mouseX(), ctx.mouseY())) text(ctx, title, Math.max(8, x - 25), 173, 70, 5, .5F, TEXT, true);
    }

    private void manifest(TabletAppClientContext ctx, State state){
        panel.draw(ctx.graphics(), 7, 19, 306, 128);
        text(ctx, ctx.data().getString("Ship") + " • Shipping manifest", 15, 23, 290, 12, .9F, TEXT, false);
        if(!ctx.data().getBoolean("Available") || !ctx.data().getBoolean("ManifestInstalled")){
            text(ctx, "Install Manifest and connect to an initialized ship", 25, 65, 270, 30, .85F, MUTED, true);
            return;
        }
        String[] categories = {"items", "fluids", "fuel"};
        String[] names = {"Items", "Fluids", "Fuel"};
        for(int idx = 0; idx < categories.length; idx++){
            String category = categories[idx];
            control(ctx, state, 15 + idx * 48, 38, 45, names[idx], true, () -> {
                state.category = category;
                state.scroll.scrollToFraction(0);
                refresh(ctx, state);
            });
            if(state.category.equals(category)) ctx.graphics().fill(17 + idx * 48, 48, 58 + idx * 48, 49, GREEN);
        }
        region(178, 331, 142, 26).draw(ctx.graphics(), 164, 38, 141, 11);
        state.panel.paintedInput(ctx, "search", 165, 38, 139, 11, "Search resources...");
        String filter = state.panel.value("search").strip();
        if(!filter.equals(state.filter)){
            state.filter = filter;
            state.scroll.scrollToFraction(0);
            refresh(ctx, state);
        }
        var data = ctx.data().getCompound("Manifest");
        if(!state.category.equals(data.getString("Category")) || !state.filter.equals(data.getString("Filter"))) return;
        boolean items = state.category.equals("items");
        int columns = items ? PaintedItemCard.COLUMNS : 1;
        state.scroll.configure((data.getInt("Total") + columns - 1) / columns, items ? PaintedItemCard.ROW_HEIGHT : 23, 89);
        int offset = data.getInt("Offset");
        var rows = data.getList("Rows", Tag.TAG_COMPOUND);
        try(var clip = GuiPoseScissor.push(ctx.graphics(), 13, 53, 308, 142)){
            for(int idx = Math.max(offset, state.scroll.firstVisibleIndex() * columns); idx < offset + rows.size(); idx++){
                int y = 53 + state.scroll.rowOffset(idx / columns);
                if(y >= 142) break;
                var row = rows.getCompound(idx - offset);
                if(items){
                    var id = ResourceLocation.tryParse(row.getString("Id"));
                    var stack = id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
                    itemCard.draw(ctx, stack, row.getString("Name"), row.getLong("Amount"), 13 + idx % columns * 74, y);
                    continue;
                }
                button.draw(ctx.graphics(), 15, y, 286, 21);
                resourceIcon(ctx, row, 19, y + 3);
                text(ctx, row.getString("Name"), 38, y + 3, 170, 10, .8F, TEXT, false);
                String type = row.getString("Type");
                String amount = row.getLong("Amount") + switch(type){ case "fluid" -> " mB"; case "energy" -> " FE"; default -> " items"; };
                text(ctx, amount, 212, y + 3, 84, 10, .75F, TEXT, true);
                long capacity = row.getLong("Capacity");
                text(ctx, capacity > 0 ? "Capacity: " + capacity + (type.equals("fluid") ? " mB" : " FE") : row.getString("Id"),
                        39, y + 13, 257, 6, .55F, MUTED, false);
            }
            if(data.getInt("Total") == 0) text(ctx, "No matching shipping resources", 28, 80, 260, 20, .8F, MUTED, true);
        }
        state.bar.configure(310, 53, 2, 89, state.scroll.maximumOffset(), state.scroll.offset());
        state.bar.render(ctx.graphics());
    }

    private void stats(TabletAppClientContext ctx, State state){
        panel.draw(ctx.graphics(), 7, 19, 306, 128);
        text(ctx, ctx.data().getString("Ship") + " • Ship statistics", 16, 24, 285, 12, .95F, TEXT, false);
        String[] names = {"Speed", "Weight", "Fuel", "Cargo", "Fluids", "Energy"};
        String[] values = {String.format(Locale.ROOT, "%.1f blocks/s", ctx.data().getDouble("Speed")),
                String.format(Locale.ROOT, "%.0f kg", ctx.data().getDouble("Weight")), Math.round(ctx.data().getDouble("Fuel") * 100) + "%",
                ctx.data().getLong("Cargo") + " items", ctx.data().getLong("Fluids") + " mB", ctx.data().getLong("Energy") + " FE"};
        for(int idx = 0; idx < names.length; idx++){
            int x = 18 + (idx % 2) * 146, y = 44 + (idx / 2) * 31;
            button.draw(ctx.graphics(), x, y, 137, 27);
            text(ctx, names[idx], x + 5, y + 3, 125, 9, .7F, MUTED, false);
            text(ctx, values[idx], x + 5, y + 14, 125, 10, .8F, TEXT, false);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public void refresh(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State)raw;
        ctx.actions().send("refresh", "manifest".equals(ctx.tab().id())
                ? state.category + "|" + state.scroll.firstVisibleIndex() * (state.category.equals("items") ? PaintedItemCard.COLUMNS : 1) + "|" + state.filter : "");
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw;
        var bounds = bounds(ctx);
        x = (x - bounds.left()) / bounds.scaleX();
        y = (y - bounds.top()) / bounds.scaleY();
        if(state.panel.click(x, y, btn)) return true;
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        if("manifest".equals(ctx.tab().id()) && state.bar.mouseClicked(x, y, 0)){
            state.scroll.scrollToFraction(state.bar.fraction());
            refresh(ctx, state);
            return true;
        }
        for(Hit hit : state.hits){
            if(!hit.area().contains(x, y)) continue;
            hit.action().run();
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        var bounds = bounds(ctx);
        if(!"manifest".equals(ctx.tab().id()) || !new TabletLayout.Rect(13, 53, 299, 89)
                .contains((x - bounds.left()) / bounds.scaleX(), (y - bounds.top()) / bounds.scaleY())) return false;
        State state = (State)raw;
        state.scroll.scrollBy((int)Math.round(-sy * 23));
        refresh(ctx, state);
        return true;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn, double dx, double dy){
        State state = (State)raw;
        var bounds = bounds(ctx);
        if(!"manifest".equals(ctx.tab().id()) || !state.bar.mouseDragged((y - bounds.top()) / bounds.scaleY(), btn == 1 && ctx.ui().projected() ? 0 : btn)) return false;
        state.scroll.scrollToFraction(state.bar.fraction());
        refresh(ctx, state);
        return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){ return ((State)raw).bar.mouseReleased(); }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){ return ((State)raw).panel.keyPressed(key, mods); }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int mods){ return ((State)raw).panel.charTyped(ch); }

    private void control(TabletAppClientContext ctx, State state, int x, int y, int width, String label, boolean enabled, Runnable action){
        state.panel.paintedButton(ctx, button, x, y, width, 11, label, enabled, action);
    }

    private static void resourceIcon(TabletAppClientContext ctx, CompoundTag row, int x, int y){
        var id = ResourceLocation.tryParse(row.getString("Id"));
        ItemStack stack = ItemStack.EMPTY;
        if(id != null && "item".equals(row.getString("Type"))) stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if(id != null && "fluid".equals(row.getString("Type"))) stack = new ItemStack(BuiltInRegistries.FLUID.get(id).getBucket());
        if(!stack.isEmpty()) ctx.graphics().renderItem(stack, x, y);
        else text(ctx, "fluid".equals(row.getString("Type")) ? "mB" : "FE", x, y + 3, 16, 10, .8F, GREEN, true);
    }

    private static double heading(CompoundTag data){
        if(data.contains("Yaw") && Double.isFinite(data.getDouble("Yaw"))) return data.getDouble("Yaw");
        return switch(data.getString("Facing").toLowerCase(Locale.ROOT)){
            case "north" -> 0; case "east" -> 90; case "south" -> 180; case "west" -> 270;
            default -> data.getDouble("Yaw");
        };
    }

    private GuiTextureRegion region(int u, int v, int width, int height){ return new GuiTextureRegion(atlas, 320, 400, u, v, width, height); }
    private static String duration(long seconds){ return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s"; }
    private static void text(TabletAppClientContext ctx, String val, int x, int y, int width, int height, float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(val), x, y, width, height, scale, color, centered);
    }

    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){
        var surface = ctx.surface();
        return CANVAS.stretch(surface.left(), surface.top(), surface.width(), surface.height());
    }

    private static TabletAppClientContext local(TabletAppClientContext ctx, TabletLayout.Bounds bounds){
        return new TabletAppClientContext(ctx.app(), ctx.tab(), ctx.data(), ctx.graphics(), ctx.font(), 0, 0, 320, 180,
                new TabletAppClientSurface(0, 0, 320, 180), (int)((ctx.mouseX() - bounds.left()) / bounds.scaleX()),
                (int)((ctx.mouseY() - bounds.top()) / bounds.scaleY()), ctx.actions(), ctx.refresh(), ctx.ui());
    }
}
