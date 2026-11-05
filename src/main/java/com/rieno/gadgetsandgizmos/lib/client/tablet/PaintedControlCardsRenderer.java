package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.ItemPickerScreen;
import com.rieno.gadgetsandgizmos.lib.client.ui.LinkFrequencyModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionModal;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardGrid;
import com.rieno.gadgetsandgizmos.lib.tablet.ControlCardStyle;
import com.rieno.gadgetsandgizmos.lib.tablet.ClockCardSettings;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.lwjgl.glfw.GLFW;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

// Paint a grid of wireless controls and keep card drafts independent of incoming snapshots
public class PaintedControlCardsRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final int TEXT = 0xFFFFFFFF;
    private final ResourceLocation atlas;
    private final GuiTextureRegion frequencyBackground;
    private final ColorPickerModal.TextureAppearance colorAppearance;
    private final PaintedCardArtwork artwork;
    private final GuiNineSlice sliderTrack;

    private record Hit(TabletLayout.Rect area, Runnable action, String tooltip){}
    private record Slider(TabletLayout.Rect area, String id, int strength){}
    private record CardSlot(java.util.UUID id, TabletLayout.Rect area){}

    private static final class State implements TabletAppClientState{
        private final List<Hit> hits = new ArrayList<>();
        private final List<Slider> sliders = new ArrayList<>();
        private CompoundTag draft = new CompoundTag();
        private boolean editor, pending;
        private long pendingStamp;
        private int scroll;
        private Slider dragging;
        private int dragStrength;
        private String notice = "";
        private LinkFrequencyModal frequencies;
        private ColorPickerModal colors;
        private PaintedSelectionModal items;
        private ResourceLocation disc;
        private MusicDiscCatalog.Track track;
        private String pressed = "";
        private boolean buttonHeld;
        private long pressedUntil;
        private TabletSearchDropdown timezone;
        private final List<CardSlot> cards = new ArrayList<>();
        private List<CardSlot> anchors = List.of();
        private List<java.util.UUID> order = List.of(), pendingOrder = List.of();
        private List<java.util.UUID> dragOrder = List.of();
        private CardSlot dragCard;
        private boolean cardDragging;
        private double dragStartX, dragStartY, dragX, dragY;

        @Override public CompoundTag saveDraft(){
            var tag = new CompoundTag();
            tag.putBoolean("Editing", editor); tag.putInt("Scroll", scroll);
            if(editor) tag.put("Card", draft.copy());
            return tag;
        }
        @Override public void loadDraft(CompoundTag tag){
            editor = tag.getBoolean("Editing"); scroll = Math.max(0, tag.getInt("Scroll"));
            draft = tag.getCompound("Card").copy();
        }
    }

    public PaintedControlCardsRenderer(ResourceLocation atlas, GuiTextureRegion frequencyBackground, ColorPickerModal.TextureAppearance colorAppearance){
        this.atlas = atlas;
        this.frequencyBackground = frequencyBackground;
        this.colorAppearance = colorAppearance;
        artwork = new PaintedCardArtwork(atlas);
        sliderTrack = PaintedGuiStyle.slice(region(117, 319, 75, 11), 2, 2, 2, 2);
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
        double mouseX = (ctx.mouseX() - bounds.left()) / bounds.scaleX(), mouseY = (ctx.mouseY() - bounds.top()) / bounds.scaleY();
        if(state.pending && ctx.data().getLong("UpdatedAt") != state.pendingStamp && ctx.data().hasUUID("SavedCard")){
            state.pending = false; state.editor = false; state.notice = "";
        }
        if(state.pending && ctx.data().contains("Error")){
            state.pending = false; state.notice = ctx.data().getString("Error");
        }
        state.hits.clear(); state.sliders.clear(); state.cards.clear();
        var graphics = ctx.graphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.left(), bounds.top(), 0);
            graphics.pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            artwork.background(graphics);
            region(0, 273, 25, 127).draw(graphics, 3, 27, 25, 127);
            sidebar(ctx, state);
            if(state.editor) designer(ctx, state); else grid(ctx, state);
            if(!state.notice.isBlank()) text(ctx, state.notice, 38, 169, 270, 8, .65F);
            if(state.frequencies != null && !state.frequencies.isOpen()) state.frequencies = null;
            if(state.colors != null && !state.colors.isOpen()) state.colors = null;
            if(state.items != null && !state.items.isOpen()) state.items = null;
            if(state.timezone != null && !state.timezone.isOpen()) state.timezone = null;
            graphics.pose().translate(0, 0, 200);
            if(state.frequencies != null) state.frequencies.render(graphics, ctx.font(), 320, 180, (int)mouseX, (int)mouseY);
            else if(state.colors != null) state.colors.renderTextured(graphics, ctx.font(), 320, 180, colorAppearance, (int)mouseX, (int)mouseY);
            else if(state.items != null) state.items.render(graphics, ctx.font(), 320, 180, (int)mouseX, (int)mouseY);
            else if(state.timezone != null) state.timezone.render(graphics, ctx.font());
            else for(var hit : state.hits) if(hit.area().contains(mouseX, mouseY) && !hit.tooltip().isBlank()){
                graphics.renderTooltip(ctx.font(), Component.literal(hit.tooltip()), (int)mouseX, (int)mouseY); break;
            }
        }finally{ graphics.pose().popPose(); }
    }

    private void sidebar(TabletAppClientContext ctx, State state){
        boolean edit = "devices".equals(ctx.tab().id());
        if(!state.editor) ctx.graphics().fill(5, edit ? 68 : 38, 25, edit ? 89 : 59, 0x664E696C);
        item(ctx, new ItemStack(Items.REDSTONE_TORCH), 7, 40, 16);
        hit(state, 5, 36, 21, 25, "Controls", () -> { state.editor = false; state.notice = ""; ctx.ui().selectTab("home"); });
        region(283, 319, 10, 10).draw(ctx.graphics(), 9, 71, 12, 12);
        hit(state, 5, 66, 21, 25, "Edit cards", () -> { state.editor = false; state.notice = ""; ctx.ui().selectTab("devices"); });
        item(ctx, new ItemStack(Items.SPYGLASS), 7, 102, 16);
        hit(state, 5, 97, 21, 25, "Scan a Redstone Link", () -> {
            ctx.ui().selectTab("devices"); ctx.actions().send("begin_reader", "bind_channel");
        });
        item(ctx, new ItemStack(Items.CLOCK), 7, 132, 16);
        hit(state, 5, 127, 21, 25, "Refresh controls", ctx.refresh());
    }

    private void grid(TabletAppClientContext ctx, State state){
        var source = ctx.data().getList("Channels", Tag.TAG_COMPOUND);
        var rows = new ArrayList<CompoundTag>();
        for(int idx = 0; idx < source.size(); idx++) rows.add(source.getCompound(idx));
        var snapshotOrder = rows.stream().map(row -> row.getUUID("Id")).toList();
        if(snapshotOrder.equals(state.pendingOrder) || ctx.data().contains("Error")) state.pendingOrder = List.of();
        var displayedOrder = state.cardDragging ? state.order : state.pendingOrder;
        if(!displayedOrder.isEmpty()) rows.sort(java.util.Comparator.comparingInt(row -> displayedOrder.indexOf(row.getUUID("Id"))));
        boolean edit = "devices".equals(ctx.tab().id());
        var sizes = new ArrayList<ControlCardStyle.Size>();
        for(var row : rows) sizes.add(ControlCardStyle.fromTag(row.getCompound("Card")).size());
        int maximum = ctx.data().getInt("MaxChannels");
        if(maximum <= 0) maximum = 24;
        boolean add = edit && rows.size() < maximum;
        if(add) sizes.add(ControlCardStyle.Size.SINGLE);
        var cells = ControlCardGrid.arrange(sizes, 3);
        int totalRows = cells.stream().mapToInt(cell -> cell.row() + cell.rows()).max().orElse(0);
        state.scroll = Math.clamp(state.scroll, 0, Math.max(0, totalRows - 2));
        try(var clip = GuiPoseScissor.push(ctx.graphics(), 38, 24, 308, 165)){
            for(int idx = 0; idx < cells.size(); idx++){
                var cell = cells.get(idx);
                int x = 44 + cell.column() * 78, y = 26 + (cell.row() - state.scroll) * 72;
                int width = cell.columns() * 78 - 6, height = cell.rows() * 72 - 6;
                if(y + height <= 24 || y >= 165) continue;
                if(idx >= rows.size()){
                    artwork.frame(ctx.graphics(), ControlCardStyle.Size.SINGLE, 0x303030, x, y, width, height);
                    artwork.addIcon(ctx.graphics(), x + (width - 28) / 2, y + 9, 28, 30);
                    text(ctx, "Add Card", x + 5, y + 45, width - 10, 12, .8F);
                    hit(state, x, y, width, height, "Create a control card", () -> edit(state, null));
                }else{
                    var row = rows.get(idx);
                    state.cards.add(new CardSlot(row.getUUID("Id"), new TabletLayout.Rect(x, y, width, height)));
                    if(state.cardDragging && state.dragCard.id().equals(row.getUUID("Id"))) ctx.graphics().renderOutline(x, y, width, height, 0xFFAAAAAA);
                    else card(ctx, state, row, x, y, width, height, edit, !state.cardDragging);
                }
            }
            if(state.cardDragging){
                var row = rows.stream().filter(val -> val.getUUID("Id").equals(state.dragCard.id())).findFirst().orElse(null);
                if(row != null){
                    var area = state.dragCard.area();
                    int x = (int)Math.clamp(area.left() + state.dragX - state.dragStartX, 38, 308 - area.width());
                    int y = (int)Math.clamp(area.top() + state.dragY - state.dragStartY, 24, Math.max(24, 165 - area.height()));
                    ctx.graphics().pose().pushPose();
                    try{ ctx.graphics().pose().translate(0, 0, 100); card(ctx, state, row, x, y, area.width(), area.height(), false, false); }
                    finally{ ctx.graphics().pose().popPose(); }
                }
            }
        }
        if(rows.isEmpty() && !edit){
            text(ctx, "No control cards", 56, 62, 230, 16, 1);
            control(ctx, state, 121, 87, 90, "Create a card", () -> { ctx.ui().selectTab("devices"); edit(state, null); });
        }
        if(totalRows > 2) text(ctx, "Scroll to browse cards", 64, 169, 218, 8, .65F);
    }

    private void mediaCard(TabletAppClientContext ctx, State state, int x, int y, int width, int height, boolean live){
        var playback = MediaPlaybackState.fromTag(ctx.data().getCompound("Media"));
        var level = Minecraft.getInstance().level;
        if(!java.util.Objects.equals(state.disc, playback.discId())){
            state.disc = playback.discId();
            state.track = level == null ? null : MusicDiscCatalog.resolve(level.registryAccess(), state.disc);
        }
        boolean installed = ctx.data().getBoolean("MediaInstalled");
        GuiControlPreviews.drawFittedWrappedText(ctx.graphics(), ctx.font(), !installed ? Component.literal("Install Media")
                        : state.track == null ? Component.literal("Choose music in Media") : state.track.title(),
                x + 6, y + 19, width - 12, height - 54, .65F, 2, TEXT, false);
        int base = x + (width - 72) / 2, bottom = y + height - 66;
        region(117, 304, 62, 12).draw(ctx.graphics(), x + 6, y + height - 36, width - 12, 9);
        double progress = level == null || playback.durationTicks() <= 0 ? 0 : playback.elapsed(level.getGameTime()) / (double)playback.durationTicks();
        ctx.graphics().fill(x + 8, y + height - 34, x + 8 + (int)((width - 16) * progress), y + height - 29, 0xFF264D32);
        region(26, 237, 24, 16).draw(ctx.graphics(), base + 6, bottom + 43, 16, 16);
        region(56, 237, 19, 19).draw(ctx.graphics(), base + 27, bottom + 42, 18, 18);
        if(playback.playing()){
            ctx.graphics().fill(base + 32, bottom + 47, base + 35, bottom + 55, 0xFF303030);
            ctx.graphics().fill(base + 37, bottom + 47, base + 40, bottom + 55, 0xFF303030);
        }else region(14, 238, 12, 16).draw(ctx.graphics(), base + 33, bottom + 46, 7, 10);
        region(81, 237, 24, 16).draw(ctx.graphics(), base + 49, bottom + 43, 16, 16);
        if(live && installed){
            hit(state, x + 5, y + 17, width - 10, Math.max(10, height - 54), "Choose music or a playlist", () -> ctx.ui().openApp(
                    ResourceLocation.fromNamespaceAndPath(ctx.app().id().getNamespace(), "media"), "player"));
            hit(state, base + 4, bottom + 41, 20, 20, "Previous track", () -> ctx.actions().send("media_previous", ""));
            hit(state, base + 26, bottom + 41, 20, 20, playback.playing() ? "Pause music" : "Play music", () -> ctx.actions().send("media_pause", ""));
            hit(state, base + 48, bottom + 41, 20, 20, "Next track", () -> ctx.actions().send("media_next", ""));
        }
    }

    private void card(TabletAppClientContext ctx, State state, CompoundTag row, int x, int y, int width, int height, boolean edit, boolean live){
        var style = ControlCardStyle.fromTag(row.getCompound("Card"));
        int col = style.color();
        artwork.frame(ctx.graphics(), style.size(), col, x, y, width, height);
        text(ctx, row.getString("Label"), x + 6, y + 5, width - (edit ? 28 : 12), 11, .75F);
        String id = row.hasUUID("Id") ? row.getUUID("Id").toString() : "";
        if(edit){
            artwork.deleteIcon(ctx.graphics(), x + width - 24, y + 4, 8);
            artwork.editIcon(ctx.graphics(), x + width - 13, y + 4, 8);
            hit(state, x + width - 25, y + 3, 10, 10, "Delete card", () -> ctx.actions().send("channel_remove", id));
            hit(state, x + width - 14, y + 3, 11, 10, "Edit card", () -> edit(state, row));
        }
        String mode = row.getString("Mode");
        if("media".equals(mode)){ mediaCard(ctx, state, x, y, width, height, live); return; }
        if("clock".equals(mode)){ clockCard(ctx, style, x, y, width, height); return; }
        int iconSize = width > 100 ? 24 : 20;
        int iconX = width > 100 ? x + 12 : x + (width - iconSize) / 2;
        int iconY = y + Math.max(20, (height - iconSize) / 2 - 5);
        item(ctx, stack(style.icon()), iconX, iconY, iconSize);
        int strength = state.dragging != null && state.dragging.id().equals(id) ? state.dragStrength
                : row.contains("SliderValue") ? row.getInt("SliderValue") : row.getInt("Strength");
        if("slider".equals(mode)){
            text(ctx, Math.round(strength / 15D * 100) + "%", width > 100 ? x + 51 : x + 5, y + 23, width > 100 ? width - 60 : width - 10, 18, width > 100 ? 1.4F : .8F);
            int sx = x + 7, sy = y + height - 17, sw = width - 14;
            sliderTrack.draw(ctx.graphics(), sx, sy, sw, 10);
            var face = sliderTrack.centerBounds(sx, sy, sw, 10);
            int filled = (int)Math.round(face.width() * strength / 15D);
            if(filled > 0) try(var clip = GuiPoseScissor.push(ctx.graphics(), face.x(), face.y(), face.x() + filled, face.y() + face.height())){
                sliderTrack.center().drawRecolored(ctx.graphics(), face.x(), face.y(), face.width(), face.height(), col, 0x303030);
            }
            region(191, 301, 15, 15).draw(ctx.graphics(), sx - 3 + (int)((sw - 6) * strength / 15D), sy - 2, 13, 13);
            if(live) state.sliders.add(new Slider(new TabletLayout.Rect(sx, sy - 3, sw, 15), id, strength));
        }else{
            int sx = x + (width - 44) / 2, sy = y + height - 22;
            boolean active = row.getBoolean("Active");
            boolean pulse = "button".equals(mode);
            boolean pressed = pulse && id.equals(state.pressed) && (state.buttonHeld || System.nanoTime() < state.pressedUntil);
            if(pressed) sy += 2;
            region(117, 279, 44, 19).drawRecolored(ctx.graphics(), sx, sy, 44, 19, active || pressed ? col : 0x202020, 0x202020);
            if(!"button".equals(mode)) region(164, 280, 15, 15).draw(ctx.graphics(), sx + (active ? 27 : 2), sy + 2, 15, 15);
            text(ctx, "button".equals(mode) ? "PUSH" : active ? "ON" : "OFF", sx + ("button".equals(mode) ? 10 : active ? 2 : 18), sy + 5,
                    24, 11, .8F);
            if(live) hit(state, sx, y + height - 22, 44, 21, pulse ? "Pulse channel" : "Toggle channel", () -> {
                if(pulse){ state.pressed = id; state.buttonHeld = true; state.pressedUntil = System.nanoTime() + 150_000_000; }
                ctx.actions().send(pulse ? "channel_button" : "channel_toggle", id);
            });
        }
    }

    private void clockCard(TabletAppClientContext ctx, ControlCardStyle style, int x, int y, int width, int height){
        var settings = style.clock();
        var level = Minecraft.getInstance().level;
        var time = settings.time(level == null ? 0 : level.getDayTime(), java.time.Instant.now());
        if(settings.digital()){
            text(ctx, time.format(java.time.format.DateTimeFormatter.ofPattern(settings.realTime() ? "HH:mm:ss" : "HH:mm")), x + 6,
                    y + height / 2 - 4, width - 12, 19, width > 100 ? 1.7F : 1.1F);
        }else{
            int diameter = Math.min(width - 16, height - 36);
            int cx = x + width / 2, cy = y + 18 + (height - 30) / 2;
            region(56, 237, 19, 19).draw(ctx.graphics(), cx - diameter / 2, cy - diameter / 2, diameter, diameter);
            for(int idx = 0; idx < 12; idx++) clockHand(ctx, cx, cy, idx * Math.PI / 6, diameter * .43, diameter * .35, 1, 0xFF303030);
            clockHand(ctx, cx, cy, (time.getHour() % 12 + time.getMinute() / 60D) * Math.PI / 6, diameter * .25, 0, 2, 0xFF303030);
            clockHand(ctx, cx, cy, (time.getMinute() + time.getSecond() / 60D) * Math.PI / 30, diameter * .36, 0, 1, 0xFF000000 | style.color());
        }
        text(ctx, settings.realTime() ? settings.timezone() : "Game time", x + 5, y + height - 10, width - 10, 8, .55F);
    }

    private static void clockHand(TabletAppClientContext ctx, int x, int y, double angle, double end, double start, int width, int color){
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(x, y, 1);
            ctx.graphics().pose().mulPose(new Quaternionf().rotationZ((float)angle));
            ctx.graphics().fill(-width / 2, -(int)end, (width + 1) / 2, -(int)start + 1, color);
        }finally{ ctx.graphics().pose().popPose(); }
    }

    private void designer(TabletAppClientContext ctx, State state){
        var style = ControlCardStyle.fromTag(state.draft.getCompound("Card"));
        int width = style.size().columns * 78 - 6, height = style.size().rows * 72 - 6;
        float scale = Math.min(1, 78F / height);
        int x = Math.round(177 - width * scale / 2);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(x, 22, 0); ctx.graphics().pose().scale(scale, scale, 1);
            card(ctx, state, state.draft, 0, 0, width, height, false, false);
        }finally{ ctx.graphics().pose().popPose(); }
        hit(state, x, 22, Math.round(width * scale), Math.round(height * scale), "Rename card", () ->
                ctx.ui().editText("Card name", state.draft.getString("Label"), 48, val -> state.draft.putString("Label", val)));
        text(ctx, "Card Size", 119, 102, 57, 10, .8F);
        control(ctx, state, 178, 100, 58, style.size().label, () -> {
            var current = ControlCardStyle.fromTag(state.draft.getCompound("Card"));
            setStyle(state, new ControlCardStyle(ControlCardStyle.Size.values()[(current.size().ordinal() + 1) % 3], current.color(), current.icon()));
        });
        boolean media = "media".equals(state.draft.getString("Mode")), clock = "clock".equals(state.draft.getString("Mode"));
        if(!media && !clock) text(ctx, "Frequency", 42, 121, 65, 11, .9F);
        else if(media) text(ctx, "Uses the tablet's Media player", 42, 119, 151, 14, .75F);
        if(clock){
            var settings = style.clock();
            control(ctx, state, 42, 116, 69, settings.digital() ? "Digital" : "Analogue", () -> {
                var current = ControlCardStyle.fromTag(state.draft.getCompound("Card")).clock();
                setClock(state, new ClockCardSettings(!current.digital(), current.realTime(), current.timezone()));
            });
            control(ctx, state, 118, 116, 73, settings.realTime() ? "Real time" : "Game time", () -> {
                var current = ControlCardStyle.fromTag(state.draft.getCompound("Card")).clock();
                setClock(state, new ClockCardSettings(current.digital(), !current.realTime(), current.timezone()));
            });
            if(settings.realTime()) control(ctx, state, 42, 130, 149, settings.timezone() + " v", () -> state.timezone = new TabletSearchDropdown(
                    ClockCardSettings.TIMEZONES, settings.timezone(), 42, 82, 149, val -> {
                var current = ControlCardStyle.fromTag(state.draft.getCompound("Card")).clock();
                setClock(state, new ClockCardSettings(current.digital(), current.realTime(), val));
            }));
        }
        for(int idx = 0; !media && !clock && idx < 2; idx++){
            int slot = idx;
            region(117 + idx * 18, 255, 18, 18).draw(ctx.graphics(), 109 + idx * 18, 116);
            item(ctx, frequency(state.draft, idx), 110 + idx * 18, 117, 16);
            hit(state, 109 + idx * 18, 116, 18, 18, "Choose " + (idx == 0 ? "red" : "blue") + " frequency", () ->
                    state.frequencies = new LinkFrequencyModal(frequencyBackground, region(117, 255, 18, 18), region(135, 255, 18, 18),
                            frequency(state.draft, 0), frequency(state.draft, 1), slot,
                            (first, second) -> { putFrequency(state.draft, 0, first); putFrequency(state.draft, 1, second); }));
        }
        int colorY = clock ? 145 : 139, colorHeight = clock ? 10 : 12;
        text(ctx, "Color", 42, colorY + 2, 58, 9, .9F);
        ctx.graphics().fill(109, colorY, 166, colorY + colorHeight, 0xFF000000 | style.color());
        ctx.graphics().renderOutline(109, colorY, 57, colorHeight, 0xFF888888);
        hit(state, 109, colorY, 57, colorHeight, "Card color", () -> state.colors = new ColorPickerModal(0xFF000000 | style.color(),
                col -> setStyle(state, new ControlCardStyle(ControlCardStyle.fromTag(state.draft.getCompound("Card")).size(), col,
                        ControlCardStyle.fromTag(state.draft.getCompound("Card")).icon())), () -> {}, () -> {}));
        text(ctx, "Type", 42, 160, 58, 10, .9F);
        String mode = state.draft.getString("Mode");
        control(ctx, state, 109, 156, 57, Character.toUpperCase(mode.charAt(0)) + mode.substring(1), () ->
                state.draft.putString("Mode", switch(state.draft.getString("Mode")){ case "toggle" -> "button"; case "button" -> "slider"; case "slider" -> "media"; case "media" -> "clock"; default -> "toggle"; }));
        text(ctx, "Icon", 211, 128, 29, 10, .9F);
        region(165, 249, 25, 24).draw(ctx.graphics(), 244, 119, 24, 24);
        item(ctx, stack(style.icon()), 248, 123, 16);
        hit(state, 244, 119, 24, 24, "Choose card icon", () -> {
            var entries = ItemPickerScreen.registryEntries();
            state.items = new PaintedSelectionModal(ItemPickerScreen.defaultAppearance(), ctx.font(), Component.literal("Select Card Icon"), entries,
                    List.of(style.icon().toString()), false, ids -> {
                if(ids.isEmpty()) return;
                var current = ControlCardStyle.fromTag(state.draft.getCompound("Card"));
                setStyle(state, new ControlCardStyle(current.size(), current.color(), ResourceLocation.parse(ids.getFirst())));
            }, () -> state.items = null);
        });
        boolean fixedOutput = "button".equals(mode) || "toggle".equals(mode);
        if(fixedOutput) text(ctx, "Output", 211, 145, 29, 10, .75F);
        if(fixedOutput) control(ctx, state, 244, 143, 24, Integer.toString(state.draft.getInt("Strength")), () ->
                ctx.ui().editText("Signal strength (0-15)", Integer.toString(state.draft.getInt("Strength")), 2, val -> {
                    try{
                        int strength = Integer.parseInt(val.strip());
                        if(strength >= 0 && strength <= 15){ state.draft.putInt("Strength", strength); state.notice = ""; return; }
                    }catch(NumberFormatException ignored){}
                    state.notice = "Signal strength must be between 0 and 15";
                }));
        control(ctx, state, 207, 156, 43, "Cancel", () -> { state.editor = false; state.pending = false; state.notice = ""; });
        control(ctx, state, 256, 156, 43, "Save", () -> {
            if(state.pending) return;
            state.pending = true; state.pendingStamp = ctx.data().getLong("UpdatedAt"); state.notice = "Saving card...";
            ctx.actions().send("card_save", state.draft.toString());
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw; var bounds = bounds(ctx);
        x = (x - bounds.left()) / bounds.scaleX(); y = (y - bounds.top()) / bounds.scaleY();
        if(state.frequencies != null) return state.frequencies.mouseClicked(ctx.font(), 320, 180, x, y, btn);
        if(state.colors != null) return state.colors.mouseClicked(x, y, btn);
        if(state.items != null) return state.items.mouseClicked(320, 180, x, y, btn);
        if(state.timezone != null) return state.timezone.mouseClicked(x, y, btn);
        if(btn != 0) return false;
        for(var hit : state.hits) if(hit.area().contains(x, y) && (state.editor || x < 27 || y >= 24 && y < 165)){
            hit.action().run(); return true;
        }
        for(var slider : state.sliders) if(slider.area().contains(x, y) && y >= 24 && y < 165){
            state.dragging = slider; state.dragStrength = -1;
            drag(ctx, state, x);
            if(ctx.ui().projected()) state.dragging = null;
            return true;
        }
        if("devices".equals(ctx.tab().id()) && !state.editor) for(var card : state.cards) if(card.area().contains(x, y) && y >= 24 && y < 165){
            state.dragCard = card; state.anchors = List.copyOf(state.cards);
            state.dragStartX = state.dragX = x; state.dragStartY = state.dragY = y;
            state.order = ctx.data().getList("Channels", Tag.TAG_COMPOUND).stream().map(tag -> ((CompoundTag)tag).getUUID("Id")).toList();
            if(!state.pendingOrder.isEmpty()) state.order = state.pendingOrder;
            state.dragOrder = state.order;
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn, double dx, double dy){
        State state = (State)raw; var bounds = bounds(ctx);
        x = (x - bounds.left()) / bounds.scaleX(); y = (y - bounds.top()) / bounds.scaleY();
        if(state.frequencies != null) return state.frequencies.mouseDragged(320, 180, y, btn);
        if(state.colors != null) return state.colors.mouseDragged(x, y, btn);
        if(state.items != null) return state.items.mouseDragged(320, 180, y, btn);
        if(state.timezone != null) return true;
        if(state.dragCard != null && btn == 0){
            state.dragX = x; state.dragY = y;
            if(Math.hypot(x - state.dragStartX, y - state.dragStartY) < 4 && !state.cardDragging) return true;
            state.cardDragging = true;
            double px = x, py = y;
            var target = state.anchors.stream().filter(card -> !card.id().equals(state.dragCard.id()))
                    .min(java.util.Comparator.comparingDouble(card -> Math.hypot(px - card.area().left() - card.area().width() / 2D,
                            py - card.area().top() - card.area().height() / 2D))).orElse(null);
            if(target != null && target.area().contains(x, y)){
                var ordered = new ArrayList<>(state.dragOrder);
                int idx = ordered.indexOf(target.id());
                ordered.remove(state.dragCard.id()); ordered.add(idx, state.dragCard.id());
                state.order = List.copyOf(ordered);
            }
            return true;
        }
        if(state.dragging == null || btn != 0) return false;
        drag(ctx, state, x); return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        State state = (State)raw; var bounds = bounds(ctx);
        state.buttonHeld = false;
        if(state.frequencies != null) return state.frequencies.mouseReleased((x - bounds.left()) / bounds.scaleX(), (y - bounds.top()) / bounds.scaleY(), btn);
        if(state.colors != null) return state.colors.mouseReleased((x - bounds.left()) / bounds.scaleX(), (y - bounds.top()) / bounds.scaleY(), btn);
        if(state.items != null) return state.items.mouseReleased();
        if(state.timezone != null) return true;
        if(state.dragCard != null){
            double px = (x - bounds.left()) / bounds.scaleX(), py = (y - bounds.top()) / bounds.scaleY();
            if(state.cardDragging && px >= 38 && px < 308 && py >= 24 && py < 165){
                state.pendingOrder = state.order;
                ctx.actions().send("card_reorder", String.join("|", state.order.stream().map(java.util.UUID::toString).toList()));
            }
            state.dragCard = null; state.cardDragging = false; return true;
        }
        if(state.dragging == null || btn != 0) return false;
        drag(ctx, state, (x - bounds.left()) / bounds.scaleX());
        state.dragging = null; return true;
    }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        State state = (State)raw;
        if(state.frequencies != null) return state.frequencies.mouseScrolled(sy);
        if(state.colors != null) return true;
        if(state.items != null) return state.items.mouseScrolled(sy);
        if(state.timezone != null) return state.timezone.mouseScrolled(sy);
        if(state.editor) return false;
        state.scroll = Math.max(0, state.scroll - (int)Math.signum(sy)); return true;
    }

    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){
        State state = (State)raw;
        if(state.frequencies != null) return state.frequencies.keyPressed(key, scan, mods);
        if(state.colors != null) return state.colors.keyPressed(key, scan, mods);
        if(state.items != null) return state.items.keyPressed(key, scan, mods);
        if(state.timezone != null) return state.timezone.keyPressed(key);
        if(state.editor && key == GLFW.GLFW_KEY_ESCAPE){ state.editor = false; state.pending = false; return true; }
        return false;
    }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int mods){
        State state = (State)raw;
        if(state.frequencies != null) return state.frequencies.charTyped(ch, mods);
        if(state.colors != null) return state.colors.charTyped(ch, mods);
        if(state.timezone != null) return state.timezone.charTyped(ch);
        return state.items != null && state.items.charTyped(ch, mods);
    }

    // Publish changed analog values during clicks and drags, including hosts without release events
    private static void drag(TabletAppClientContext ctx, State state, double x){
        int strength = (int)Math.round(Math.clamp((x - state.dragging.area().left()) / state.dragging.area().width(), 0, 1) * 15);
        if(strength == state.dragStrength) return;
        state.dragStrength = strength;
        ctx.actions().send("channel_slider", state.dragging.id() + "|" + strength);
    }
    private static void edit(State state, CompoundTag row){
        state.editor = true; state.pending = false; state.notice = "";
        state.draft = row == null ? new CompoundTag() : row.copy();
        if(row == null){
            state.draft.putString("Label", "New Card"); state.draft.putString("Mode", "toggle"); state.draft.putInt("Strength", 15);
            state.draft.putInt("SliderValue", 0);
            state.draft.putInt("FirstColor", -1); state.draft.putInt("SecondColor", -1);
            state.draft.put("Card", new ControlCardStyle(ControlCardStyle.Size.SINGLE, 0x303030, ResourceLocation.withDefaultNamespace("redstone")).toTag());
        }
    }
    private static void setStyle(State state, ControlCardStyle style){
        state.draft.put("Card", new ControlCardStyle(style.size(), style.color(), style.icon(), ControlCardStyle.fromTag(state.draft.getCompound("Card")).clock()).toTag());
    }
    private static void setClock(State state, ClockCardSettings clock){
        var style = ControlCardStyle.fromTag(state.draft.getCompound("Card"));
        state.draft.put("Card", new ControlCardStyle(style.size(), style.color(), style.icon(), clock).toTag());
    }
    private static ItemStack frequency(CompoundTag row, int idx){
        ItemStack stack = stack(ResourceLocation.tryParse(row.getString(idx == 0 ? "FirstItem" : "SecondItem")));
        int color = row.contains(idx == 0 ? "FirstColor" : "SecondColor") ? row.getInt(idx == 0 ? "FirstColor" : "SecondColor") : -1;
        if(!stack.isEmpty() && color >= 0) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }
    private static void putFrequency(CompoundTag row, int idx, ItemStack stack){
        row.putString(idx == 0 ? "FirstItem" : "SecondItem", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        var color = stack.get(DataComponents.DYED_COLOR);
        row.putInt(idx == 0 ? "FirstColor" : "SecondColor", color == null ? -1 : color.rgb());
    }
    private static ItemStack stack(ResourceLocation id){ return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY); }
    private void control(TabletAppClientContext ctx, State state, int x, int y, int width, String label, Runnable action){
        artwork.button(ctx.graphics(), x, y, width, 12); text(ctx, label, x + 2, y + 1, width - 4, 10, .8F);
        hit(state, x, y, width, 12, label, action);
    }
    private static void hit(State state, int x, int y, int width, int height, String tooltip, Runnable action){ state.hits.add(new Hit(new TabletLayout.Rect(x, y, width, height), action, tooltip)); }
    private static void text(TabletAppClientContext ctx, String label, int x, int y, int width, int height, float scale){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(label), x, y, width, height, scale, TEXT, false);
    }
    private static void item(TabletAppClientContext ctx, ItemStack stack, int x, int y, int size){
        if(stack.isEmpty()) return;
        ctx.graphics().pose().pushPose();
        try{ ctx.graphics().pose().translate(x, y, 0); ctx.graphics().pose().scale(size / 16F, size / 16F, 1); ctx.graphics().renderItem(stack, 0, 0); }
        finally{ ctx.graphics().pose().popPose(); }
    }
    private GuiTextureRegion region(int x, int y, int width, int height){ return new GuiTextureRegion(atlas, 320, 400, x, y, width, height); }
    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){ var surface = ctx.surface(); return CANVAS.stretch(surface.left(), surface.top(), surface.width(), surface.height()); }
}
