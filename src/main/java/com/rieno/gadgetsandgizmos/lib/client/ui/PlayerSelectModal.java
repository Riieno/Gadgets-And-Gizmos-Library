package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.chat.OnlinePlayerSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// Reusable multi-player picker using the online roster and explicit apply/cancel controls
public final class PlayerSelectModal{
    private final OnlinePlayerSelection selection;
    private final Consumer<List<String>> accepted;
    private final Runnable closed;
    private final String emptyLabel;
    private boolean open = true;
    private String search = "";
    private boolean searchFocused;
    private int scroll;
    private Bounds bounds = new Bounds(0, 0, 0, 0);
    private List<PlayerInfo> online = List.of();
    private List<PlayerInfo> visible = List.of();

    public PlayerSelectModal(Collection<String> initial, String emptyLabel,
                             Consumer<List<String>> accepted, Runnable closed){
        selection = new OnlinePlayerSelection(initial);
        this.emptyLabel = emptyLabel == null ? "No players selected" : emptyLabel;
        this.accepted = accepted == null ? ignored -> {} : accepted;
        this.closed = closed == null ? () -> {} : closed;
    }

    public boolean isOpen(){ return open; }

    public static String selectionLabel(Collection<String> players, String emptyLabel){
        if(players == null || players.isEmpty()) return emptyLabel;
        var connection = Minecraft.getInstance().getConnection();
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for(String id : players){
            PlayerInfo info = null;
            if(connection != null){
                try{ info = connection.getPlayerInfo(java.util.UUID.fromString(id)); }
                catch(IllegalArgumentException ignored){}
            }
            names.add(info == null ? id : info.getProfile().getName());
        }
        return com.rieno.gadgetsandgizmos.lib.chat.ChatPlayers.format(names);
    }


    // Retain the original render entry point for integrations using color-picker chrome
    @Deprecated
    public void render(GuiGraphics graphics, Font font, int width, int height, ColorPickerModal.Chrome chrome,
                       int mouseX, int mouseY){
        renderStyled(graphics, font, width, height, Appearance.fromChrome(chrome), mouseX, mouseY);
    }

    // Use the host's established panel, field, button and selection colors
    public void renderStyled(GuiGraphics graphics, Font font, int width, int height, Appearance appearance,
                             int mouseX, int mouseY){
        if(!open || graphics == null || font == null) return;
        var connection = Minecraft.getInstance().getConnection();
        online = connection == null ? List.of() : connection.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER)).toList();
        String query = search.toLowerCase(Locale.ROOT);
        visible = online.stream().filter(info -> info.getProfile().getName().toLowerCase(Locale.ROOT).contains(query)).toList();
        int panelWidth = Math.max(160, Math.min(640, width - 32));
        int panelHeight = Math.max(150, Math.min(420, height - 40));
        bounds = new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        scroll = Mth.clamp(scroll, 0, Math.max(0, visible.size() - bounds.rows()));
        Appearance style = appearance == null ? Appearance.fromChrome(null) : appearance;
        graphics.fill(0, 0, width, height, 0xA8000000);
        style.panel().draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawString(font, "Player Select", bounds.x() + 12, bounds.y() + 10, style.titleColor(), false);
        style.field().draw(graphics, bounds.x() + 10, bounds.y() + 31, bounds.width() - 20, 20, searchFocused);
        String queryText = search.isEmpty() ? "Search online players..." : search;
        graphics.drawString(font, font.plainSubstrByWidth(queryText, bounds.width() - 34), bounds.x() + 16,
                bounds.y() + 38, search.isEmpty() ? style.mutedColor() : style.nameColor(), false);
        int end = Math.min(visible.size(), scroll + bounds.rows());
        for(int idx = scroll; idx < end; idx++){
            PlayerInfo info = visible.get(idx);
            int rowY = bounds.y() + 60 + (idx - scroll) * 54;
            boolean chosen = selection.contains(player(info));
            boolean hover = contains(mouseX, mouseY, bounds.x() + 10, rowY, bounds.width() - 20, 50);
            graphics.fill(bounds.x() + 10, rowY, bounds.right() - 10, rowY + 50, hover ? style.rowHovered() : style.row());
            if(chosen) graphics.renderOutline(bounds.x() + 10, rowY, bounds.width() - 20, 50, style.accentColor());
            graphics.drawString(font, chosen ? "[x]" : "[ ]", bounds.x() + 16, rowY + 20,
                    chosen ? style.accentColor() : style.mutedColor(), false);
            PlayerFaceRenderer.draw(graphics, info.getSkin(), bounds.x() + 44, rowY + 9, 32);
            graphics.drawString(font, font.plainSubstrByWidth(info.getProfile().getName(), bounds.width() - 110),
                    bounds.x() + 86, rowY + 11, style.nameColor(), false);
            graphics.drawString(font, "Online", bounds.x() + 86, rowY + 27, style.mutedColor(), false);
        }
        if(visible.isEmpty()) graphics.drawString(font, "No matching online players.", bounds.x() + 14,
                bounds.y() + 66, style.mutedColor(), false);
        long offline = selection.offlineCount(online.stream().map(PlayerSelectModal::player).toList());
        String status = selection.identifiers().isEmpty() ? emptyLabel : selection.identifiers().size()
                + " selected" + (offline == 0 ? "" : " (" + offline + " saved offline)");
        graphics.drawString(font, font.plainSubstrByWidth(status, bounds.width() - 24), bounds.x() + 12,
                bounds.bottom() - 48, style.mutedColor(), false);
        String[] labels = {"All", "None", "Cancel", "Apply"};
        for(int idx = 0; idx < labels.length; idx++){
            int x = bounds.buttonX(idx);
            int buttonWidth = bounds.buttonWidth(idx);
            boolean hover = contains(mouseX, mouseY, x, bounds.bottom() - 32, buttonWidth, 20);
            style.button().draw(graphics, font, x, bounds.bottom() - 32, buttonWidth, 20, labels[idx], hover);
        }
        if(visible.size() > bounds.rows()){
            int trackHeight = bounds.rows() * 54;
            int handle = Math.max(10, trackHeight * bounds.rows() / visible.size());
            int y = bounds.y() + 60 + (trackHeight - handle) * scroll / Math.max(1, visible.size() - bounds.rows());
            graphics.fill(bounds.right() - 7, y, bounds.right() - 4, y + handle, style.accentColor());
        }
    }

    public boolean mouseClicked(double x, double y, int button){
        if(!open) return false;
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        searchFocused = contains(x, y, bounds.x() + 10, bounds.y() + 31, bounds.width() - 20, 21);
        for(int idx = 0; idx < 4; idx++){
            if(!contains(x, y, bounds.buttonX(idx), bounds.bottom() - 32, bounds.buttonWidth(idx), 20)) continue;
            switch(idx){
                case 0 -> selection.selectAll(visible.stream().map(PlayerSelectModal::player).toList());
                case 1 -> selection.clear();
                case 2 -> cancel();
                case 3 -> accept();
            }
            return true;
        }
        int end = Math.min(visible.size(), scroll + bounds.rows());
        for(int idx = scroll; idx < end; idx++){
            if(contains(x, y, bounds.x() + 10, bounds.y() + 60 + (idx - scroll) * 54, bounds.width() - 20, 50)){
                selection.toggle(player(visible.get(idx)));
                return true;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double amount){
        if(!open) return false;
        scroll = Mth.clamp(scroll - (int)Math.signum(amount), 0, Math.max(0, visible.size() - bounds.rows()));
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers){
        if(!open) return false;
        if(key == GLFW.GLFW_KEY_ESCAPE) cancel();
        else if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) accept();
        else if(searchFocused && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()){
            search = search.substring(0, search.length() - 1); scroll = 0;
        }
        return true;
    }

    public boolean charTyped(char ch, int modifiers){
        if(!open) return false;
        if(searchFocused && !Character.isISOControl(ch) && search.length() < 64){ search += ch; scroll = 0; }
        return true;
    }

    public void accept(){
        if(!open) return;
        open = false;
        accepted.accept(selection.identifiers());
        closed.run();
    }

    public void cancel(){
        if(!open) return;
        open = false;
        closed.run();
    }

    private static OnlinePlayerSelection.Player player(PlayerInfo info){
        return new OnlinePlayerSelection.Player(info.getProfile().getId(), info.getProfile().getName());
    }

    private static boolean contains(double x, double y, int left, int top, int width, int height){
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    @FunctionalInterface
    public interface PanelRenderer{
        void draw(GuiGraphics graphics, int x, int y, int width, int height);
    }

    @FunctionalInterface
    public interface ButtonRenderer{
        void draw(GuiGraphics graphics, Font font, int x, int y, int width, int height, String label, boolean hovered);
    }

    @FunctionalInterface
    public interface FieldRenderer{
        void draw(GuiGraphics graphics, int x, int y, int width, int height, boolean focused);
    }

    public record Appearance(PanelRenderer panel, ButtonRenderer button, FieldRenderer field,
                             int titleColor, int nameColor, int mutedColor, int accentColor, int row, int rowHovered){
        public Appearance{
            java.util.Objects.requireNonNull(panel);
            java.util.Objects.requireNonNull(button);
            java.util.Objects.requireNonNull(field);
        }

        private static Appearance fromChrome(ColorPickerModal.Chrome chrome){
            ColorPickerModal.Chrome style = chrome == null ? ColorPickerModal.Chrome.DEFAULT : chrome;
            return new Appearance((graphics, x, y, width, height) -> {
                graphics.fill(x, y, x + width, y + height, style.border());
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, style.panel());
            }, (graphics, font, x, y, width, height, label, hovered) -> {
                graphics.fill(x, y, x + width, y + height, hovered ? style.hovered() : style.raised());
                graphics.renderOutline(x, y, width, height, style.border());
                graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, style.actionText());
            }, (graphics, x, y, width, height, focused) -> {
                graphics.fill(x, y, x + width, y + height, style.raised());
                graphics.renderOutline(x, y, width, height, focused ? style.accent() : style.border());
            }, style.accent(), style.secondary(), style.secondary(), style.accent(), style.raised(), style.hovered());
        }
    }

    private record Bounds(int x, int y, int width, int height){
        int right(){ return x + width; }
        int bottom(){ return y + height; }
        int rows(){ return Math.max(1, (height - 118) / 54); }
        int buttonWidth(int idx){
            if(width < 300) return Math.max(1, (width - 38) / 4);
            return switch(idx){ case 0, 1 -> 54; case 2 -> 62; default -> 64; };
        }
        int buttonX(int idx){
            if(width < 300) return x + 10 + idx * (buttonWidth(idx) + 6);
            return switch(idx){ case 0 -> x + 10; case 1 -> x + 70; case 2 -> right() - 142; default -> right() - 74; };
        }
    }
}
