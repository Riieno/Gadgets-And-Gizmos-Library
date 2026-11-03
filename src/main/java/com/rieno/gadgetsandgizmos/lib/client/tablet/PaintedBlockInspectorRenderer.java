package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiBlockPreview;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

// Share a painted block preview, inspection document and direct-signal control between tablet hosts
public class PaintedBlockInspectorRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final TabletLayout CANVAS = new TabletLayout(320, 180);
    private static final TabletLayout.Rect INSPECT = new TabletLayout.Rect(15, 140, 71, 15);
    private static final TabletLayout.Rect SIGNAL = new TabletLayout.Rect(104, 151, 198, 10);
    private static final int TEXT = 0xFFE3E3E3, MUTED = 0xFF9A9A9A;
    private final ResourceLocation texture;
    private final GuiNineSlice button, track;

    private static final class State implements TabletAppClientState{
        private final ListScrollState scroll = new ListScrollState();
        private final GuiScrollbar bar;
        private CompoundTag snapshot;
        private String target = "";
        private List<Row> rows = List.of();
        private BlockState block = Blocks.AIR.defaultBlockState();
        private boolean signalHeld;
        private int pendingSignal = -1;

        private State(GuiScrollbar bar){ this.bar = bar; }
    }

    private record Row(String name, String value, List<FormattedCharSequence> lines, int height){}

    public PaintedBlockInspectorRenderer(ResourceLocation texture){
        this.texture = texture;
        button = PaintedGuiStyle.slice(region(0, 195, 43, 11), 2, 2, 2, 2);
        track = PaintedGuiStyle.slice(region(0, 195, 43, 11), 2, 2, 2, 2);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public boolean ownsTabletSurface(){ return true; }
    @Override public TabletAppClientState createScreenState(){
        return new State(new GuiScrollbar(region(79, 42, 2, 8), region(49, 185, 2, 8)));
    }

    // Consumers supply their existing inspection and signal actions
    protected void inspect(TabletAppClientContext ctx){ ctx.actions().send("inspect", ""); }
    protected void signal(TabletAppClientContext ctx, int val){ ctx.actions().send("signal", Integer.toString(val)); }

    @Override public void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = state(raw);
        configure(ctx, state);
        var bounds = bounds(ctx);
        double mouseX = (ctx.mouseX() - bounds.left()) / bounds.scaleX();
        double mouseY = (ctx.mouseY() - bounds.top()) / bounds.scaleY();
        var graphics = ctx.graphics();
        List<Component> tooltip = null;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(bounds.left(), bounds.top(), 0);
            graphics.pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            region(0, 0, 320, 180).draw(graphics, 0, 0);
            region(12, 103, 76, 62).draw(graphics, 12, 37, 76, 129);
            text(ctx, ctx.app().title().getString(), 14, 18, 72, 12, .9F, true, TEXT);
            if(ctx.data().getBoolean("Available")){
                GuiBlockPreview.render(graphics, state.block, 18, 42, 64);
                text(ctx, ctx.data().getString("Name"), 15, 111, 71, 22, .8F, true, TEXT);
            }else{
                text(ctx, "?", 22, 52, 56, 48, 4, true, MUTED);
                text(ctx, "No block selected", 16, 114, 69, 18, .7F, true, MUTED);
            }
            button.draw(graphics, INSPECT.left(), INSPECT.top(), INSPECT.width(), INSPECT.height());
            if(INSPECT.contains(mouseX, mouseY)) graphics.fill(16, 141, 85, 154, 0x22FFFFFF);
            text(ctx, "Inspect", 17, 141, 67, 13, .8F, true, TEXT);
            text(ctx, ctx.ui().placed() ? "Attached block" : "Select a world block", 14, 158, 73, 7, .55F, true, MUTED);
            if(!ctx.data().getBoolean("Available")){
                text(ctx, "Block information", 102, 41, 203, 14, 1, false, TEXT);
                wrapped(ctx, ctx.ui().placed() ? "Attach the tablet to a loaded block, then press Inspect."
                        : "Press Inspect, then use the tablet on a block to see its information.", 103, 66, 195, .85F, MUTED);
            }else{
                text(ctx, ctx.data().getString("Name"), 102, 40, 202, 13, 1, false, TEXT);
                text(ctx, ctx.data().getString("Block"), 103, 54, 201, 10, .7F, false, MUTED);
                if(mouseX >= 102 && mouseX < 306 && mouseY >= 40 && mouseY < 65) tooltip = List.of(
                        Component.literal(ctx.data().getString("Name")), Component.literal(ctx.data().getString("Block")));
                var content = content(ctx);
                try(var clip = GuiPoseScissor.push(graphics, content.left(), content.top(), content.left() + content.width(), content.top() + content.height())){
                    int y = content.top() - state.scroll.offset();
                    for(Row row : state.rows){
                        if(y + row.height() > content.top() && y < content.top() + content.height()){
                            text(ctx, row.name(), 103, y + 1, 78, 9, .7F, false, MUTED);
                            int lineY = y + 1;
                            for(var line : row.lines()){
                                graphics.pose().pushPose();
                                graphics.pose().translate(185, lineY, 0);
                                graphics.pose().scale(.75F, .75F, 1);
                                graphics.drawString(ctx.font(), line, 0, 0, TEXT, false);
                                graphics.pose().popPose();
                                lineY += 7;
                            }
                            graphics.fill(103, y + row.height() - 2, 303, y + row.height() - 1, 0xFF383838);
                            if(content.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + row.height()){
                                tooltip = List.of(Component.literal(row.name()), Component.literal(row.value()));
                            }
                        }
                        y += row.height();
                    }
                }
                if(state.scroll.maximumOffset() > 0) state.bar.render(graphics);
                if(ctx.data().getBoolean("Controllable")){
                    int val = state.pendingSignal < 0 ? ctx.data().getInt("Signal") : state.pendingSignal;
                    text(ctx, "Redstone signal: " + val + " / 15", 104, 139, 198, 10, .75F, false, TEXT);
                    track.draw(graphics, SIGNAL.left(), SIGNAL.top(), SIGNAL.width(), SIGNAL.height());
                    var face = track.centerBounds(SIGNAL.left(), SIGNAL.top(), SIGNAL.width(), SIGNAL.height());
                    int filled = face.width() * val / 15;
                    if(filled > 0) graphics.fill(face.x(), face.y(), face.x() + filled, face.y() + face.height(), ctx.app().accentColor() | 0xFF000000);
                    int thumbX = 104 + Math.round((SIGNAL.width() - 4) * val / 15F);
                    graphics.fill(thumbX, 151, thumbX + 4, 161, 0xFFE3E3E3);
                }
            }
            if(ctx.data().contains("Error")) text(ctx, ctx.data().getString("Error"), 102, 165, 204, 7, .6F, false, 0xFFF28B8B);
        }finally{ graphics.pose().popPose(); }
        if(tooltip != null) graphics.renderComponentTooltip(ctx.font(), tooltip, ctx.mouseX(), ctx.mouseY());
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn){
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return false;
        State state = state(raw);
        configure(ctx, state);
        var bounds = bounds(ctx);
        double x = (mouseX - bounds.left()) / bounds.scaleX(), y = (mouseY - bounds.top()) / bounds.scaleY();
        if(INSPECT.contains(x, y)){ inspect(ctx); return true; }
        if(ctx.data().getBoolean("Controllable") && SIGNAL.contains(x, y)){
            state.signalHeld = btn == 0;
            updateSignal(ctx, state, x);
            return true;
        }
        if(state.bar.mouseClicked(x, y, 0)){
            state.scroll.scrollToFraction(state.bar.fraction());
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, double scrollX, double scrollY){
        State state = state(raw);
        configure(ctx, state);
        if(scrollY == 0 || !CANVAS.project(bounds(ctx), content(ctx)).contains(mouseX, mouseY) || state.scroll.maximumOffset() == 0) return false;
        state.scroll.scrollBy((int)Math.round(-scrollY * 16));
        return true;
    }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn, double deltaX, double deltaY){
        State state = state(raw);
        var bounds = bounds(ctx);
        if(state.signalHeld && btn == 0){ updateSignal(ctx, state, (mouseX - bounds.left()) / bounds.scaleX()); return true; }
        if(!state.bar.mouseDragged((mouseY - bounds.top()) / bounds.scaleY(), btn)) return false;
        state.scroll.scrollToFraction(state.bar.fraction());
        return true;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double mouseX, double mouseY, int btn){
        State state = state(raw);
        boolean held = state.signalHeld;
        state.signalHeld = false;
        return state.bar.mouseReleased() || held;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void updateSignal(TabletAppClientContext ctx, State state, double x){
        int val = (int)Math.round(Math.clamp((x - SIGNAL.left()) / (SIGNAL.width() - 4), 0, 1) * 15);
        if(val == state.pendingSignal) return;
        state.pendingSignal = val;
        signal(ctx, val);
    }

    private static TabletLayout.Rect content(TabletAppClientContext ctx){ return new TabletLayout.Rect(102, 70, 202, ctx.data().getBoolean("Controllable") ? 64 : 93); }
    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){ return CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height()); }
    private GuiTextureRegion region(int u, int v, int width, int height){ return new GuiTextureRegion(texture, 320, 300, u, v, width, height); }
    private static State state(TabletAppClientState raw){ if(raw instanceof State state) return state; throw new IllegalArgumentException("Inspector received another application's state"); }

    private void configure(TabletAppClientContext ctx, State state){
        CompoundTag data = ctx.data();
        if(state.snapshot == null || !state.snapshot.equals(data)){
            String target = data.getString("Block") + ":" + data.getLong("Pos") + ":" + data.getString("Dimension") + ":" + data.get("SubLevelId");
            if(!target.equals(state.target)){ state.scroll.scrollToFraction(0); state.bar.reset(); state.signalHeld = false; }
            state.target = target;
            state.snapshot = data;
            state.pendingSignal = -1;
            var id = ResourceLocation.tryParse(data.getString("Block"));
            state.block = data.contains("State", Tag.TAG_COMPOUND) ? NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), data.getCompound("State"))
                    : id == null ? Blocks.AIR.defaultBlockState() : BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            List<Row> rows = new ArrayList<>();
            if(data.getBoolean("Available")){
                var pos = BlockPos.of(data.getLong("Pos"));
                row(ctx, rows, "Position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                if(data.contains("Dimension")) row(ctx, rows, "Dimension", data.getString("Dimension"));
                if(data.hasUUID("SubLevelId")) row(ctx, rows, "Sub-level", data.getUUID("SubLevelId").toString());
                row(ctx, rows, "Hardness", Float.toString(data.getFloat("Hardness")));
                row(ctx, rows, "Block entity", data.getBoolean("BlockEntity") ? "Yes" : "No");
                row(ctx, rows, "Direct control", data.getBoolean("Controllable") ? "Available" : "Unavailable");
                var properties = data.getList("Properties", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < properties.size(); idx++){
                    var property = properties.getCompound(idx);
                    row(ctx, rows, property.getString("Name"), property.getString("Value"));
                }
            }
            state.rows = List.copyOf(rows);
        }
        var content = content(ctx);
        int height = state.rows.stream().mapToInt(Row::height).sum();
        state.scroll.configure(1, Math.max(1, height), content.height());
        state.bar.configure(308, content.top(), 2, content.height(), state.scroll.maximumOffset(), state.scroll.offset(),
                Math.max(8, content.height() * content.height() / Math.max(1, height)));
    }

    private static void row(TabletAppClientContext ctx, List<Row> rows, String name, String value){
        List<FormattedCharSequence> lines = ctx.font().split(Component.literal(value.isEmpty() ? "—" : value), 156);
        rows.add(new Row(name, value, lines, Math.max(14, lines.size() * 7 + 5)));
    }

    private static void text(TabletAppClientContext ctx, String val, int x, int y, int width, int height, float scale, boolean centered, int color){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(val), x, y, width, height, scale, color, centered);
    }

    private static void wrapped(TabletAppClientContext ctx, String val, int x, int y, int width, float scale, int color){
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(x, y, 0);
            ctx.graphics().pose().scale(scale, scale, 1);
            ctx.graphics().drawWordWrap(ctx.font(), Component.literal(val), 0, 0, Math.round(width / scale), color);
        }finally{ ctx.graphics().pose().popPose(); }
    }
}
