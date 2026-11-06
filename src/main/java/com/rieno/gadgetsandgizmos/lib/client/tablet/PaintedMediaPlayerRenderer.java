package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.media.MediaSession;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Share the painted player, disc browser and playlist editor between tablet hosts
public class PaintedMediaPlayerRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int GOLD = 0xFFE3BA63;
    private final ResourceLocation atlas;
    private final ResourceLocation template;
    private final GuiNineSlice button;
    private final GuiNineSlice card;

    private static final class State implements TabletAppClientState{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final ListScrollState scroll = new ListScrollState();
        private final GuiScrollbar bar = new GuiScrollbar(0xFF242424, 0xFF777777);
        private final List<Hit> hits = new ArrayList<>();
        private List<MusicDiscCatalog.Track> catalog;
        private boolean playlists;
        private UUID editing;
        private String pendingName;
        private String filter = "";
        private int listTop;
        private CompoundTag snapshot;
        private MediaSession session;
    }

    private record Hit(TabletLayout.Rect area, Runnable action){}

    public PaintedMediaPlayerRenderer(ResourceLocation atlas, ResourceLocation template){
        this.atlas = atlas;
        this.template = template;
        button = PaintedGuiStyle.slice(region(193, 375, 78, 11), 1, 1, 1, 1);
        card = PaintedGuiStyle.slice(region(0, 334, 72, 66), 2, 2, 2, 2);
    }

    @Override public TabletAppClientState createScreenState(){ return new State(); }
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
        var mc = Minecraft.getInstance();
        if(mc.level == null) return;
        if(state.catalog == null) state.catalog = MusicDiscCatalog.tracks(mc.level.registryAccess());
        if(state.snapshot == null || !state.snapshot.equals(ctx.data())){
            state.snapshot = ctx.data();
            state.session = MediaSession.fromTag(ctx.data());
        }
        var session = state.session;
        if(state.pendingName != null){
            for(var list : session.playlists()){
                if(!list.name().equals(state.pendingName)) continue;
                state.editing = list.id();
                state.playlists = false;
                state.pendingName = null;
                state.panel.setValue("search", "");
                break;
            }
            if(ctx.data().contains("Error")) state.pendingName = null;
        }
        if(state.editing != null && session.playlist(state.editing) == null) state.editing = null;
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            region(0, 0, 320, 180).draw(ctx.graphics(), 0, 0);
            state.panel.begin(local, false);
            state.hits.clear();
            sidebar(local, state, session, mc.level.getGameTime());
            state.panel.paintedInput(local, "search", 101, 20, 208, 14, state.playlists ? LocalizedText.get("gadgetsngizmos.text.search_playlists") : LocalizedText.get("gadgetsngizmos.text.search_discs"));
            String filter = state.panel.value("search").strip().toLowerCase(Locale.ROOT);
            if(!filter.equals(state.filter)){
                state.filter = filter;
                state.scroll.scrollToFraction(0);
            }
            if(state.playlists) playlists(local, state, session);
            else discs(local, state, session);
            int height = 169 - state.listTop;
            state.bar.configure(310, state.listTop, 3, height, state.scroll.maximumOffset(), state.scroll.offset(),
                    Math.max(8, height * height / Math.max(height, state.scroll.maximumOffset() + height)));
            state.bar.render(ctx.graphics());
            if(ctx.data().contains("Error")){
                ctx.graphics().fill(99, 157, 309, 169, 0xF0302525);
                text(local, LocalizedText.read(ctx.data(), "Error").getString(), 101, 158, 204, 10, .7F, 0xFFF28B8B, false);
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    private void sidebar(TabletAppClientContext ctx, State state, MediaSession session, long now){
        var playback = session.playback();
        var track = state.catalog.stream().filter(val -> val.itemId().equals(playback.discId())).findFirst().orElse(null);
        card.draw(ctx.graphics(), 14, 29, 75, 72);
        if(track != null) icon(ctx, track.itemId(), 26, 38, 51);
        GuiControlPreviews.drawFittedWrappedText(ctx.graphics(), ctx.font(), track == null ? Component.translatable("gadgetsngizmos.text.choose_a_music_disc") : track.title(),
                15, 105, 73, 18, .65F, 2, TEXT, true);
        int elapsed = (int)playback.elapsed(now);
        text(ctx, time(elapsed) + " / " + time(playback.durationTicks()), 15, 124, 73, 7, .6F, MUTED, true);
        PaintedGuiStyle.slice(region(173, 277, 147, 26), 2, 2, 2, 2).draw(ctx.graphics(), 15, 132, 73, 7);
        int filled = playback.durationTicks() == 0 ? 0 : (int)(69L * elapsed / playback.durationTicks());
        if(filled > 0) ctx.graphics().fill(17, 134, 17 + filled, 137, 0xFF4D9BA4);
        transport(ctx, state, region(97, 277, 19, 19), 18, "previous");
        transport(ctx, state, region(121, 277, 21, 19), 42, "pause");
        region(playback.playing() ? 67 : 81, 279, playback.playing() ? 11 : 12, 14).draw(ctx.graphics(), 47, 143, 10, 12);
        transport(ctx, state, region(149, 277, 19, 19), 67, "next");
        control(ctx, state, 15, 159, 73, LocalizedText.get("gadgetsngizmos.text.playlists"), () -> {
            state.playlists = !state.playlists;
            state.editing = null;
            reset(state);
        });
    }

    private void transport(TabletAppClientContext ctx, State state, GuiTextureRegion glyph, int x, String action){
        glyph.draw(ctx.graphics(), x, 142, 19, 14);
        state.hits.add(new Hit(new TabletLayout.Rect(x, 140, 20, 17), () -> ctx.actions().send(action, "")));
    }

    private void playlists(TabletAppClientContext ctx, State state, MediaSession session){
        control(ctx, state, 101, 38, 101, LocalizedText.get("gadgetsngizmos.text.new_playlist"), () -> ctx.ui().editText(LocalizedText.get("gadgetsngizmos.text.playlist_name"), "", MediaSession.MAX_NAME, name -> {
            state.pendingName = name.strip();
            ctx.actions().send("playlist_create", name);
        }));
        control(ctx, state, 207, 38, 101, LocalizedText.get("gadgetsngizmos.text.all_discs"), () -> {
            ctx.actions().send("playlist_select", "");
            state.playlists = false;
            reset(state);
        });
        var lists = session.playlists().stream().filter(val -> val.name().toLowerCase(Locale.ROOT).contains(state.filter)).toList();
        state.listTop = 54;
        state.scroll.configure(lists.size(), 25, 115);
        try(var clip = GuiPoseScissor.push(ctx.graphics(), 100, 54, 309, 169)){
            for(int idx = state.scroll.firstVisibleIndex(); idx < lists.size(); idx++){
                int y = 54 + state.scroll.rowOffset(idx);
                if(y >= 169) break;
                var list = lists.get(idx);
                card.draw(ctx.graphics(), 101, y, 207, 23);
                text(ctx, list.name(), 106, y + 3, 156, 10, .8F, list.id().equals(session.selected()) ? GOLD : TEXT, false);
                text(ctx, list.discs().size() + " discs" + (list.id().equals(session.selected()) ? LocalizedText.get("gadgetsngizmos.text.looping") : ""),
                        106, y + 13, 156, 7, .6F, MUTED, false);
                template(196, 178, 9, 9).draw(ctx.graphics(), 289, y + 7);
                hit(state, 282, y, 26, 23, () -> { state.editing = list.id(); state.playlists = false; reset(state); });
                hit(state, 101, y, 179, 23, () -> ctx.actions().send("playlist_select", list.id().toString()));
            }
            if(lists.isEmpty()) text(ctx, LocalizedText.get("gadgetsngizmos.text.create_a_playlist_to_get_started"), 105, 80, 198, 14, .8F, MUTED, true);
        }
    }

    private void discs(TabletAppClientContext ctx, State state, MediaSession session){
        var editing = session.playlist(state.editing);
        var selected = session.playlist(session.selected());
        if(editing != null){
            text(ctx, editing.name(), 103, 39, 109, 10, .75F, GOLD, false);
            control(ctx, state, 216, 38, 20, "", () -> ctx.ui().editText(LocalizedText.get("gadgetsngizmos.text.playlist_name"), editing.name(), MediaSession.MAX_NAME,
                    name -> ctx.actions().send("playlist_rename", editing.id() + "|" + name)));
            template(196, 178, 9, 9).draw(ctx.graphics(), 222, 39);
            control(ctx, state, 239, 38, 34, LocalizedText.get("gadgetsngizmos.text.delete"), () -> ctx.ui().editText(LocalizedText.get("gadgetsngizmos.text.type_delete_to_delete_playlist"), "", 6, val -> {
                if(val.equals("DELETE")) ctx.actions().send("playlist_delete", editing.id().toString());
            }));
            control(ctx, state, 276, 38, 32, LocalizedText.get("gadgetsngizmos.text.done"), () -> { state.editing = null; state.playlists = true; reset(state); });
        }else{
            text(ctx, selected == null ? LocalizedText.get("gadgetsngizmos.text.all_music_discs") : selected.name() + LocalizedText.get("gadgetsngizmos.text.looping"), 103, 39, 176, 10, .75F, MUTED, false);
            if(selected != null){
                control(ctx, state, 285, 38, 23, "", () -> { state.editing = selected.id(); reset(state); });
                template(196, 178, 9, 9).draw(ctx.graphics(), 292, 39);
            }
        }
        var tracks = state.catalog.stream().filter(val -> editing != null || selected == null || selected.discs().contains(val.itemId()))
                .filter(val -> (val.title().getString() + " " + val.itemId()).toLowerCase(Locale.ROOT).contains(state.filter)).toList();
        state.listTop = 53;
        state.scroll.configure((tracks.size() + 2) / 3, 59, 116);
        try(var clip = GuiPoseScissor.push(ctx.graphics(), 100, 53, 309, 169)){
            for(int idx = state.scroll.firstVisibleIndex() * 3; idx < tracks.size(); idx++){
                int x = 101 + (idx % 3) * 69, y = 53 + state.scroll.rowOffset(idx / 3);
                if(y >= 169) break;
                var track = tracks.get(idx);
                card.draw(ctx.graphics(), x, y, 67, 57);
                icon(ctx, track.itemId(), x + 15, y + 3, 37);
                GuiControlPreviews.drawFittedWrappedText(ctx.graphics(), ctx.font(), track.title(), x + 3, y + 42, 61, 12, .55F, 2,
                        track.itemId().equals(session.playback().discId()) ? GOLD : TEXT, true);
                if(editing != null){
                    template(editing.discs().contains(track.itemId()) ? 228 : 212, 163, 9, 9).draw(ctx.graphics(), x + 54, y + 3);
                    hit(state, x, y, 67, 57, () -> ctx.actions().send("playlist_toggle", editing.id() + "|" + track.itemId()));
                }else hit(state, x, y, 67, 57, () -> ctx.actions().send("play", track.itemId().toString()));
            }
            if(tracks.isEmpty()) text(ctx, selected != null ? LocalizedText.get("gadgetsngizmos.text.edit_this_playlist_to_add_music_discs") : LocalizedText.get("gadgetsngizmos.text.no_matching_music_discs"), 105, 85, 198, 20, .8F, MUTED, true);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw;
        var bounds = bounds(ctx);
        x = (x - bounds.left()) / bounds.scaleX();
        y = (y - bounds.top()) / bounds.scaleY();
        if(state.panel.click(x, y, btn)) return true;
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        if(state.bar.mouseClicked(x, y, 0)){
            state.scroll.scrollToFraction(state.bar.fraction());
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
        x = (x - bounds.left()) / bounds.scaleX();
        y = (y - bounds.top()) / bounds.scaleY();
        State state = (State)raw;
        if(x < 100 || x > 314 || y < state.listTop || y > 169) return false;
        state.scroll.scrollBy((int)Math.round(-sy * 25));
        return true;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn, double dx, double dy){
        State state = (State)raw;
        var bounds = bounds(ctx);
        if(!state.bar.mouseDragged((y - bounds.top()) / bounds.scaleY(), btn == 1 && ctx.ui().projected() ? 0 : btn)) return false;
        state.scroll.scrollToFraction(state.bar.fraction());
        return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){ return ((State)raw).bar.mouseReleased(); }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){ return ((State)raw).panel.keyPressed(key, mods); }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char codePoint, int mods){ return ((State)raw).panel.charTyped(codePoint); }

    private static void reset(State state){ state.scroll.scrollToFraction(0); state.panel.setValue("search", ""); state.panel.clearFocus(); }

    private void control(TabletAppClientContext ctx, State state, int x, int y, int width, String label, Runnable action){
        state.panel.paintedButton(ctx, button, x, y, width, 11, label, true, action);
    }

    // Clip hit targets too, so scrolled cards cannot intercept the search or toolbar
    private static void hit(State state, int x, int y, int width, int height, Runnable action){
        int top = Math.max(state.listTop, y), bottom = Math.min(169, y + height);
        if(bottom > top) state.hits.add(new Hit(new TabletLayout.Rect(x, top, width, bottom - top), action));
    }

    private GuiTextureRegion region(int u, int v, int width, int height){ return new GuiTextureRegion(atlas, 320, 400, u, v, width, height); }
    private GuiTextureRegion template(int u, int v, int width, int height){ return new GuiTextureRegion(template, 508, 268, u, v, width, height); }
    private static String time(int ticks){ return (ticks / 1200) + ":" + String.format(Locale.ROOT, "%02d", (ticks / 20) % 60); }

    private static void icon(TabletAppClientContext ctx, ResourceLocation id, int x, int y, int size){
        var graphics = ctx.graphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(size / 16F, size / 16F, 1);
            graphics.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)), 0, 0);
        }finally{ graphics.pose().popPose(); }
    }

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
