package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

// Share painted cargo cards, resource gauges and independent network panes between container apps
public class PaintedContainerRenderer implements TabletAppClientRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    protected static final TabletLayout CANVAS = new TabletLayout(320, 180);
    protected static final TabletLayout.Rect CONTENT = new TabletLayout.Rect(8, 20, 304, 133);
    protected static final TabletLayout.Rect LEFT = new TabletLayout.Rect(8, 20, 147, 133);
    protected static final TabletLayout.Rect RIGHT = new TabletLayout.Rect(162, 20, 149, 133);
    protected static final int TEXT = 0xFFECECEC;
    protected static final int MUTED = 0xFFA4A4A4;
    protected static final int GOLD = 0xFFE3BA63;
    protected static final int RED = 0xFFF26C6C;
    protected final Skin skin;
    private final PaintedItemCard cargoCard;
    private final GuiTextureRegion itemRow;
    private final GuiNineSlice shortButton;
    private final GuiNineSlice longButton;
    private final GuiNineSlice wideGauge;
    private final GuiNineSlice narrowGauge;

    // Atlases share a 320 by 180 frame and their supplied card, row and control partials
    public record Skin(ResourceLocation contents, ResourceLocation storage, ResourceLocation network){}

    public static class State implements TabletAppClientState{
        private final TabletAppPanel panel = new TabletAppPanel();
        private final Map<String, ListScrollState> scrolls = new HashMap<>();
        private final Map<String, GuiScrollbar> bars = new HashMap<>();
        private final Map<String, ScrollArea> areas = new LinkedHashMap<>();
        public TabletAppPanel panel(){ return panel; }
        public ListScrollState scroll(String id){ return scrolls.computeIfAbsent(id, key -> new ListScrollState()); }
        public String networkPage(){ return scroll("network_items").firstVisibleIndex() + "|" + scroll("network_resources").firstVisibleIndex(); }
    }

    private record ScrollArea(TabletLayout.Rect bounds, int step, IntConsumer changed){}

    public PaintedContainerRenderer(Skin skin){
        this.skin = java.util.Objects.requireNonNull(skin);
        cargoCard = new PaintedItemCard(skin.contents());
        itemRow = region(skin.contents(), 178, 331, 142, 26);
        shortButton = PaintedGuiStyle.slice(region(skin.contents(), 277, 364, 43, 11), 1, 1, 1, 1);
        longButton = PaintedGuiStyle.slice(region(skin.contents(), 193, 375, 78, 11), 1, 1, 1, 1);
        wideGauge = PaintedGuiStyle.slice(region(skin.contents(), 18, 303, 302, 26), 2, 2, 2, 2);
        narrowGauge = PaintedGuiStyle.slice(region(skin.contents(), 173, 277, 147, 26), 2, 2, 2, 2);
    }
    @Override public TabletAppClientState createScreenState(){ return new State(); }
    @Override public boolean ownsTabletSurface(){ return true; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public final void render(TabletAppClientContext ctx, TabletAppClientState raw){
        State state = (State)raw;
        var bounds = bounds(ctx);
        var local = local(ctx, bounds);
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(bounds.left(), bounds.top(), 0);
            ctx.graphics().pose().scale((float)bounds.scaleX(), (float)bounds.scaleY(), 1);
            ResourceLocation texture = switch(ctx.tab().id()){
                case "storage" -> skin.storage();
                case "network" -> skin.network();
                default -> skin.contents();
            };
            region(texture, 0, 0, 320, 180).draw(ctx.graphics(), 0, 0);
            state.panel.begin(local, false);
            state.areas.clear();
            renderBody(local, state);
            renderTabs(local, state);
            if(ctx.data().contains("Error")){
                ctx.graphics().fill(10, 141, 309, 153, 0xF0302828);
                text(local, ctx.data().getString("Error"), 12, 142, 295, 10, .7F, RED, false);
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    protected void renderBody(TabletAppClientContext ctx, State state){
        switch(ctx.tab().id()){
            case "resources" -> renderResources(ctx, state);
            case "network" -> renderNetwork(ctx, state);
            default -> renderContents(ctx, state);
        }
    }

    // Keep navigation in the painted footer without changing saved tab identifiers
    protected void renderTabs(TabletAppClientContext ctx, State state){
        var tabs = ctx.app().tabs();
        int width = Math.min(43, (304 - Math.max(0, tabs.size() - 1) * 6) / Math.max(1, tabs.size()));
        for(int idx = 0; idx < tabs.size(); idx++){
            var tab = tabs.get(idx);
            var texture = region(skin.contents(), tab.id().equals(ctx.tab().id()) ? 205 : 277,
                    tab.id().equals(ctx.tab().id()) ? 389 : 364, 43, 11);
            state.panel.paintedButton(ctx, texture, 8 + idx * (width + 6), 160, width, 11,
                    tab.title().getString(), true, () -> ctx.ui().selectTab(tab.id()));
        }
    }

    protected void renderContents(TabletAppClientContext ctx, State state){
        var rows = ctx.data().getList("Items", Tag.TAG_COMPOUND);
        var scroll = state.scroll("contents");
        scroll.configure((rows.size() + 3) / 4, 67, CONTENT.height() + 1);
        try(var clip = clip(ctx, CONTENT)){
            for(int idx = scroll.firstVisibleIndex() * 4; idx < rows.size(); idx++){
                var card = contentCard(scroll, idx);
                int x = card.left(), y = card.top();
                if(y >= CONTENT.top() + CONTENT.height()) break;
                var row = rows.getCompound(idx);
                var stack = item(row);
                cargoCard.draw(ctx, stack, row.getString("Name"), row.getLong("Count"), x, y);
            }
            if(rows.isEmpty()) text(ctx, "No item cargo. View Resources for fluids or FE.", 13, 55, 292, 25, .8F, MUTED, true);
        }
        scrollArea(ctx, state, "contents", CONTENT, 24, null);
        if(CONTENT.contains(ctx.mouseX(), ctx.mouseY())){
            for(int idx = scroll.firstVisibleIndex() * 4; idx < rows.size(); idx++){
                var card = contentCard(scroll, idx);
                int x = card.left(), y = card.top();
                if(y >= CONTENT.top() + CONTENT.height()) break;
                if(card.contains(ctx.mouseX(), ctx.mouseY())){
                    var stack = item(rows.getCompound(idx));
                    if(!stack.isEmpty()) ctx.graphics().renderTooltip(ctx.font(), stack, ctx.mouseX(), ctx.mouseY());
                    break;
                }
            }
        }
    }

    protected void renderResources(TabletAppClientContext ctx, State state){
        var fluids = ctx.data().getList("Fluids", Tag.TAG_COMPOUND);
        int count = fluids.size() + (ctx.data().contains("Energy") ? 1 : 0);
        var scroll = state.scroll("resources");
        scroll.configure(count, 58, CONTENT.height());
        try(var clip = clip(ctx, CONTENT)){
            int idx = 0;
            if(ctx.data().contains("Energy")){
                resource(ctx, "FE", ctx.data().getLong("Energy"), ctx.data().getLong("EnergyCapacity"), null,
                        9, 21 + scroll.rowOffset(idx++), 302);
            }
            for(int fluidIdx = 0; fluidIdx < fluids.size(); fluidIdx++){
                var row = fluids.getCompound(fluidIdx);
                resource(ctx, row.getString("Name"), row.getLong("Amount"), row.getLong("Capacity"), fluid(row),
                        9, 21 + scroll.rowOffset(idx++), 302);
            }
            if(count == 0) text(ctx, "This container stores items", 13, 55, 292, 25, .8F, MUTED, true);
        }
        scrollArea(ctx, state, "resources", CONTENT, 23, null);
    }

    protected void renderNetwork(TabletAppClientContext ctx, State state){
        var inventory = ctx.data().getCompound("Inventory");
        networkPage(ctx, state, inventory.getCompound("Items"), "network_items", LEFT, true);
        networkPage(ctx, state, inventory.getCompound("Resources"), "network_resources", RIGHT, false);
    }

    private void networkPage(TabletAppClientContext ctx, State state, CompoundTag page, String key,
                             TabletLayout.Rect viewport, boolean items){
        var scroll = state.scroll(key);
        int height = items ? 27 : 43;
        scroll.configure(page.getInt("Total"), height, viewport.height());
        var rows = page.getList("Rows", Tag.TAG_COMPOUND);
        int start = page.getInt("Offset");
        try(var clip = clip(ctx, viewport)){
            for(int idx = Math.max(start, scroll.firstVisibleIndex()); idx < start + rows.size(); idx++){
                int y = viewport.top() + 1 + scroll.rowOffset(idx);
                if(y >= viewport.top() + viewport.height()) break;
                var row = rows.getCompound(idx - start);
                if(items){
                    row(ctx, viewport.left() + 3, y, 142, 26);
                    var id = ResourceLocation.tryParse(row.getString("Id"));
                    var stack = id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
                    icon(ctx, stack, viewport.left() + 11, y + 5, 16);
                    text(ctx, "X" + row.getLong("Amount") + " " + row.getString("Name"),
                            viewport.left() + 42, y + 3, viewport.width() - 50, 19, .7F, TEXT, false);
                }else{
                    boolean energy = "energy".equals(row.getString("Type"));
                    text(ctx, row.getString("Name") + ": " + row.getLong("Amount") + " / " + row.getLong("Capacity") + (energy ? " FE" : " mB"),
                            viewport.left() + 3, y + 1, viewport.width() - 8, 11, .6F, energy ? RED : TEXT, false);
                    gauge(ctx, energy ? null : fluid(row), row.getLong("Amount"), row.getLong("Capacity"),
                            viewport.left() + 1, y + 14, 147, 26, energy ? RED : GOLD);
                }
            }
            if(page.getInt("Total") == 0) text(ctx, items ? "No network items" : "No network fluids or FE",
                    viewport.left() + 3, 44, viewport.width() - 6, 20, .75F, MUTED, true);
        }
        scrollArea(ctx, state, key, viewport, height, offset -> ctx.actions().send("refresh", state.networkPage()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    protected final void row(TabletAppClientContext ctx, int x, int y, int width, int height){
        itemRow.drawHorizontallyTiled(ctx.graphics(), x, y, width, height, 30, 2);
    }

    protected final void button(TabletAppClientContext ctx, State state, int x, int y, int width,
                                String label, boolean enabled, Runnable action){
        if(y != 160 && (y < CONTENT.top() || y + 11 > CONTENT.top() + CONTENT.height())) return;
        state.panel.paintedButton(ctx, width > 43 ? longButton : shortButton, x, y, width, 11, label, enabled, action);
    }

    protected final void resource(TabletAppClientContext ctx, String name, long amount, long capacity,
                                  FluidStack fluid, int x, int y, int width){
        row(ctx, x, y, Math.min(142, width), 26);
        if(fluid == null) icon(ctx, energyIcon(ctx), x + 7, y + 5, 16);
        else TabletResourceGauge.fluidFill(ctx.graphics(), fluid, 1, 1, x + 7, y + 5, 16, 16);
        text(ctx, name + " " + amount + " / " + capacity + (fluid == null ? " FE" : " mB"),
                x + 33, y + 3, Math.min(102, width - 38), 20, .65F, fluid == null ? RED : TEXT, false);
        gauge(ctx, fluid, amount, capacity, x, y + 28, width, 26, fluid == null ? RED : GOLD);
    }

    // Let the consuming mod supply its own FE icon without exposing addon items through the library
    protected ItemStack energyIcon(TabletAppClientContext ctx){ return new ItemStack(Items.REDSTONE); }

    // Paint a proportional fill inside the atlas frame; fluids retain their registry texture and tint
    protected final void gauge(TabletAppClientContext ctx, FluidStack fluid, double amount, double capacity,
                                int x, int y, int width, int height, int color){
        gaugeBackground(ctx, x, y, width, height);
        int fill = capacity > 0 && amount > 0 ? (int)Math.min(width - 4, Math.max(1, Math.round(amount / capacity * (width - 4)))) : 0;
        if(fill <= 0) return;
        if(fluid != null && !fluid.isEmpty()){
            TabletResourceGauge.fluidFill(ctx.graphics(), fluid, 1, 1, x + 2, y + 2, fill, height - 4);
            return;
        }
        ctx.graphics().fill(x + 2, y + 2, x + 2 + fill, y + height - 2, color);
        try(var clip = GuiPoseScissor.push(ctx.graphics(), x + 2, y + 2, x + 2 + fill, y + height - 2)){
            for(int row = 0; row < height - 4; row++) for(int stripe = -height; stripe < fill + height; stripe += 12){
                int left = x + 2 + stripe + height - row;
                ctx.graphics().fill(left, y + 2 + row, left + 5, y + 3 + row, 0x66FFFFFF);
            }
        }
    }

    // Use the supplied gauge size while retaining its two-pixel border at other widths
    protected final void gaugeBackground(TabletAppClientContext ctx, int x, int y, int width, int height){
        (width <= 147 ? narrowGauge : wideGauge).draw(ctx.graphics(), x, y, width, height);
    }

    private static TabletLayout.Rect contentCard(ListScrollState scroll, int idx){
        return new TabletLayout.Rect(13 + idx % 4 * 74, 20 + scroll.rowOffset(idx / 4), 72, 66);
    }

    protected final int scrollArea(TabletAppClientContext ctx, State state, String key, TabletLayout.Rect viewport,
                                   int step, IntConsumer changed){
        var scroll = state.scroll(key);
        state.areas.put(key, new ScrollArea(viewport, step, changed));
        var bar = state.bars.computeIfAbsent(key, id -> new GuiScrollbar(0x66333333, 0xFF888888));
        int thumb = Math.max(8, viewport.height() * viewport.height() / Math.max(1, viewport.height() + scroll.maximumOffset()));
        bar.configure(viewport.left() + viewport.width() - 2, viewport.top(), 1, viewport.height(), scroll.maximumOffset(), scroll.offset(), thumb);
        bar.render(ctx.graphics());
        return scroll.offset();
    }

    protected static GuiPoseScissor clip(TabletAppClientContext ctx, TabletLayout.Rect rect){
        return GuiPoseScissor.push(ctx.graphics(), rect.left(), rect.top(), rect.left() + rect.width(), rect.top() + rect.height());
    }

    protected static GuiTextureRegion region(ResourceLocation texture, int u, int v, int width, int height){
        return new GuiTextureRegion(texture, 320, 400, u, v, width, height);
    }

    protected static void text(TabletAppClientContext ctx, String label, int x, int y, int width, int height,
                                float scale, int color, boolean centered){
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(label), x, y, width, height, scale, color, centered);
    }

    protected static ItemStack item(CompoundTag row){
        var level = Minecraft.getInstance().level;
        return level == null ? ItemStack.EMPTY : ItemStack.parseOptional(level.registryAccess(), row.getCompound("Item"));
    }

    protected static FluidStack fluid(CompoundTag row){
        var id = ResourceLocation.tryParse(row.getString("Id"));
        var fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getOptional(id).orElse(Fluids.EMPTY);
        return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, 1);
    }

    protected static void icon(TabletAppClientContext ctx, ItemStack stack, int x, int y, int size){
        if(stack.isEmpty()) return;
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(x, y, 0);
            ctx.graphics().pose().scale(size / 16F, size / 16F, 1);
            ctx.graphics().renderItem(stack, 0, 0);
        }finally{ ctx.graphics().pose().popPose(); }
    }

    @Override public void refresh(TabletAppClientContext ctx, TabletAppClientState raw){
        ctx.actions().send("refresh", "network".equals(ctx.tab().id()) ? ((State)raw).networkPage() : "");
    }

    @Override public boolean mouseClicked(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        var state = (State)raw;
        var bounds = bounds(ctx);
        double localX = (x - bounds.left()) / bounds.scaleX(), localY = (y - bounds.top()) / bounds.scaleY();
        if(state.panel.click(localX, localY, btn)) return true;
        if(btn != 0 && !(btn == 1 && ctx.ui().projected())) return bodyClicked(local(ctx, bounds), state, localX, localY, btn);
        for(var entry : state.areas.entrySet()){
            var bar = state.bars.get(entry.getKey());
            if(!bar.mouseClicked(localX, localY, 0)) continue;
            state.scroll(entry.getKey()).scrollToFraction(bar.fraction());
            changed(state, entry.getKey(), entry.getValue());
            return true;
        }
        return bodyClicked(local(ctx, bounds), state, localX, localY, btn);
    }

    protected boolean bodyClicked(TabletAppClientContext ctx, State state, double x, double y, int btn){ return false; }

    @Override public boolean mouseScrolled(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, double sx, double sy){
        var state = (State)raw;
        var bounds = bounds(ctx);
        double localX = (x - bounds.left()) / bounds.scaleX(), localY = (y - bounds.top()) / bounds.scaleY();
        if(scrollBody(local(ctx, bounds), state, localX, localY, sy)) return true;
        for(var entry : state.areas.entrySet()){
            if(!entry.getValue().bounds().contains(localX, localY)) continue;
            state.scroll(entry.getKey()).scrollBy((int)Math.round(-sy * entry.getValue().step()));
            changed(state, entry.getKey(), entry.getValue());
            return true;
        }
        return false;
    }

    protected boolean scrollBody(TabletAppClientContext ctx, State state, double x, double y, double sy){ return false; }

    @Override public boolean mouseDragged(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn, double dx, double dy){
        var state = (State)raw;
        for(var entry : state.areas.entrySet()){
            var bar = state.bars.get(entry.getKey());
            if(!bar.mouseDragged((y - bounds(ctx).top()) / bounds(ctx).scaleY(), btn == 1 && ctx.ui().projected() ? 0 : btn)) continue;
            state.scroll(entry.getKey()).scrollToFraction(bar.fraction());
            changed(state, entry.getKey(), entry.getValue());
            return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(TabletAppClientContext ctx, TabletAppClientState raw, double x, double y, int btn){
        boolean handled = false;
        for(var bar : ((State)raw).bars.values()) handled |= bar.mouseReleased();
        return handled;
    }
    @Override public boolean keyPressed(TabletAppClientContext ctx, TabletAppClientState raw, int key, int scan, int mods){ return ((State)raw).panel.keyPressed(key, mods); }
    @Override public boolean chatTyped(TabletAppClientContext ctx, TabletAppClientState raw, char ch, int mods){ return ((State)raw).panel.charTyped(ch); }

    private static void changed(State state, String key, ScrollArea area){
        if(area.changed() != null) area.changed().accept(state.scroll(key).firstVisibleIndex());
    }
    private static TabletLayout.Bounds bounds(TabletAppClientContext ctx){ return CANVAS.stretch(ctx.surface().left(), ctx.surface().top(), ctx.surface().width(), ctx.surface().height()); }
    private static TabletAppClientContext local(TabletAppClientContext ctx, TabletLayout.Bounds bounds){
        return new TabletAppClientContext(ctx.app(), ctx.tab(), ctx.data(), ctx.graphics(), ctx.font(), 0, 0, 320, 180,
                new TabletAppClientSurface(0, 0, 320, 180), (int)((ctx.mouseX() - bounds.left()) / bounds.scaleX()),
                (int)((ctx.mouseY() - bounds.top()) / bounds.scaleY()), ctx.actions(), ctx.refresh(), ctx.ui());
    }
}
