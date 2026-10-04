package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Reusable HSV color picker modal with live values, hex input, and explicit confirmation.
public final class ColorPickerModal {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MODAL_WIDTH = 306;
    private static final int MODAL_HEIGHT = 264;
    private static final int COLOR_AREA_SIZE = 172;
    private static final int HUE_BAR_WIDTH = 18;
    private static final int COLOR_AREA_STEPS = 48;
    private static final int HUE_BAR_STEPS = 96;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Original color restored when the modal is cancelled.
    private final int originalColor;
    // Called whenever a live preview color changes.
    private final Consumer<Integer> changed;
    // Called after accepting the current color.
    private final Runnable accepted;
    // Called after restoring the original color on cancellation.
    private final Runnable cancelled;
    // Current alpha component.
    private int alpha;
    // Current HSV components.
    private float hue;
    private float saturation;
    private float value;
    // Current hex field text.
    private String hex = "#FFFFFF";
    // Tracks keyboard focus for the hex field.
    private boolean hexFocused;
    // Replaces the complete hex field with the next typed value.
    private boolean replaceHex;
    // Tracks dragging inside the color area.
    private boolean draggingColor;
    // Tracks dragging inside the hue bar.
    private boolean draggingHue;
    // Tracks whether this modal is available for rendering and input.
    private boolean open = true;
    // Latest rendered layout used for input.
    private Bounds bounds = Bounds.EMPTY;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create one color picker with live preview and explicit accept or cancel callbacks.
    public ColorPickerModal(int initialColor, Consumer<Integer> changed, Runnable accepted, Runnable cancelled) {
        originalColor = initialColor;
        this.changed = changed == null ? ignored -> {
        } : changed;
        this.accepted = accepted == null ? () -> {
        } : accepted;
        this.cancelled = cancelled == null ? () -> {
        } : cancelled;
        setColor(initialColor, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check whether this modal should be drawn and receive input.
    public boolean isOpen() {
        return open;
    }

    // Render the picker centered within the supplied screen dimensions.
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, Chrome chrome,
                       int mouseX, int mouseY) {
        if (!open || graphics == null || font == null) return;
        Chrome style = chrome == null ? Chrome.DEFAULT : chrome;
        bounds = layout(screenWidth, screenHeight);
        graphics.fill(0, 0, screenWidth, screenHeight, style.backdrop());
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), style.border());
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, style.panel());
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.y() + 25, style.title());
        graphics.drawString(font, "Color Picker", bounds.x() + 10, bounds.y() + 9, style.primary(), false);

        drawColorArea(graphics, bounds.colorX(), bounds.colorY(), bounds.colorSize());
        drawHueBar(graphics, bounds.hueX(), bounds.colorY(), bounds.hueWidth(), bounds.colorSize());
        drawColorCursor(graphics, bounds.colorX(), bounds.colorY(), bounds.colorSize(), style);
        drawHueCursor(graphics, bounds.hueX(), bounds.colorY(), bounds.hueWidth(), bounds.colorSize(), style);

        graphics.drawString(font, "Hex", bounds.hexLabelX(), bounds.hexY() + 6, style.secondary(), false);
        int hexBorder = hexFocused ? style.accent() : style.border();
        graphics.fill(bounds.hexX(), bounds.hexY(), bounds.hexRight(), bounds.hexBottom(), hexBorder);
        graphics.fill(bounds.hexX() + 1, bounds.hexY() + 1, bounds.hexRight() - 1, bounds.hexBottom() - 1,
                style.raised());
        graphics.drawString(font, hex, bounds.hexX() + 6, bounds.hexY() + 6, style.primary(), false);
        graphics.fill(bounds.previewX(), bounds.hexY(), bounds.previewRight(), bounds.hexBottom(), currentColor());
        graphics.fill(bounds.previewX(), bounds.hexY(), bounds.previewRight(), bounds.hexY() + 1, style.border());
        graphics.fill(bounds.previewX(), bounds.hexBottom() - 1, bounds.previewRight(), bounds.hexBottom(), style.border());
        graphics.fill(bounds.previewX(), bounds.hexY(), bounds.previewX() + 1, bounds.hexBottom(), style.border());
        graphics.fill(bounds.previewRight() - 1, bounds.hexY(), bounds.previewRight(), bounds.hexBottom(), style.border());

        drawButton(graphics, font, bounds.cancelX(), bounds.buttonY(), bounds.buttonWidth(), "Cancel",
                bounds.cancelContains(mouseX, mouseY), style, false);
        drawButton(graphics, font, bounds.acceptX(), bounds.buttonY(), bounds.buttonWidth(), "Accept",
                bounds.acceptContains(mouseX, mouseY), style, true);
    }

    // Handle a mouse press inside this modal.
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return open;
        if (bounds.colorContains(mouseX, mouseY)) {
            draggingColor = true;
            updateColor(mouseX, mouseY);
            return true;
        }
        if (bounds.hueContains(mouseX, mouseY)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }
        if (bounds.hexContains(mouseX, mouseY)) {
            hexFocused = true;
            replaceHex = true;
            return true;
        }
        hexFocused = false;
        replaceHex = false;
        if (bounds.acceptContains(mouseX, mouseY)) {
            accept();
            return true;
        }
        if (bounds.cancelContains(mouseX, mouseY)) {
            cancel();
            return true;
        }
        return true;
    }

    // Handle a color-area drag.
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingColor) {
            updateColor(mouseX, mouseY);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingHue) {
            updateHue(mouseY);
        }
        return true;
    }

    // Handle a modal mouse release.
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingColor = false;
            draggingHue = false;
        }
        return true;
    }

    // Handle the hex field and modal shortcuts.
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            accept();
            return true;
        }
        if (!hexFocused) return true;
        if (keyCode == GLFW.GLFW_KEY_A && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            replaceHex = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceHex) hex = "";
            else if (!hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
            replaceHex = false;
            applyHexIfComplete();
        }
        return true;
    }

    // Handle typed hex characters.
    public boolean charTyped(char character, int modifiers) {
        if (!open || !hexFocused) return open;
        if (replaceHex) {
            hex = "";
            replaceHex = false;
        }
        if (character == '#' && hex.isEmpty()) {
            hex = "#";
            return true;
        }
        if (!Character.toString(character).matches("[0-9a-fA-F]")) return true;
        String raw = hex.startsWith("#") ? hex.substring(1) : hex;
        if (raw.length() >= 8) return true;
        hex = "#" + raw + Character.toUpperCase(character);
        applyHexIfComplete();
        return true;
    }

    // Close this picker after accepting the selected color.
    public void accept() {
        if (!open) return;
        changed.accept(currentColor());
        open = false;
        accepted.run();
    }

    // Close this picker and restore its original live preview color.
    public void cancel() {
        if (!open) return;
        changed.accept(originalColor);
        open = false;
        cancelled.run();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Build the centered modal layout.
    private static Bounds layout(int screenWidth, int screenHeight) {
        int width = Math.min(MODAL_WIDTH, Math.max(260, screenWidth - 16));
        int height = Math.min(MODAL_HEIGHT, Math.max(220, screenHeight - 16));
        int x = Math.max(8, (screenWidth - width) / 2);
        int y = Math.max(8, (screenHeight - height) / 2);
        int colorSize = Math.min(COLOR_AREA_SIZE, Math.max(112, height - 92));
        return new Bounds(x, y, width, height, colorSize);
    }

    // Draw the HSV saturation/value square for the active hue.
    private void drawColorArea(GuiGraphics graphics, int x, int y, int size) {
        int step = Math.max(1, (int) Math.ceil(size / (double) COLOR_AREA_STEPS));
        for (int row = 0; row < size; row += step) {
            float brightness = 1.0F - Mth.clamp((row + step * 0.5F) / size, 0.0F, 1.0F);
            for (int column = 0; column < size; column += step) {
                float saturation = Mth.clamp((column + step * 0.5F) / size, 0.0F, 1.0F);
                graphics.fill(x + column, y + row, Math.min(x + size, x + column + step),
                        Math.min(y + size, y + row + step), hsv(hue, saturation, brightness, 0xFF));
            }
        }
    }

    // Draw the hue strip.
    private static void drawHueBar(GuiGraphics graphics, int x, int y, int width, int height) {
        int step = Math.max(1, (int) Math.ceil(height / (double) HUE_BAR_STEPS));
        for (int row = 0; row < height; row += step) {
            float hue = Mth.clamp((row + step * 0.5F) / height, 0.0F, 1.0F);
            graphics.fill(x, y + row, x + width, Math.min(y + height, y + row + step), hsv(hue, 1.0F, 1.0F, 0xFF));
        }
    }

    // Draw the saturation/value selection indicator.
    private void drawColorCursor(GuiGraphics graphics, int x, int y, int size, Chrome style) {
        int centerX = x + Math.round(saturation * Math.max(0, size - 1));
        int centerY = y + Math.round((1.0F - value) * Math.max(0, size - 1));
        drawOutline(graphics, centerX - 5, centerY - 5, 11, 11, 0xFF000000);
        drawOutline(graphics, centerX - 4, centerY - 4, 9, 9, style.primary());
    }

    // Draw the hue selection indicator.
    private void drawHueCursor(GuiGraphics graphics, int x, int y, int width, int height, Chrome style) {
        int markerY = y + Math.round(hue * Math.max(0, height - 1));
        drawOutline(graphics, x - 2, markerY - 2, width + 4, 5, 0xFF000000);
        drawOutline(graphics, x - 1, markerY - 1, width + 2, 3, style.primary());
    }

    // Draw one picker action button.
    private static void drawButton(GuiGraphics graphics, Font font, int x, int y, int width, String label,
                                   boolean hovered, Chrome style, boolean accent) {
        int border = accent ? style.accent() : style.border();
        int fill = hovered ? style.hovered() : style.raised();
        graphics.fill(x, y, x + width, y + 18, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 17, fill);
        graphics.drawCenteredString(font, label, x + width / 2, y + 5,
                accent ? style.actionText() : style.primary());
    }

    // Update saturation and value from a color-area position.
    private void updateColor(double mouseX, double mouseY) {
        saturation = Mth.clamp((float) ((mouseX - bounds.colorX()) / Math.max(1, bounds.colorSize() - 1)),
                0.0F, 1.0F);
        value = 1.0F - Mth.clamp((float) ((mouseY - bounds.colorY())
                / Math.max(1, bounds.colorSize() - 1)), 0.0F, 1.0F);
        updateLiveColor();
    }

    // Update hue from a hue-bar position.
    private void updateHue(double mouseY) {
        hue = Mth.clamp((float) ((mouseY - bounds.colorY()) / Math.max(1, bounds.colorSize() - 1)),
                0.0F, 1.0F);
        updateLiveColor();
    }

    // Update the live callback and normalized hex value.
    private void updateLiveColor() {
        int color = currentColor();
        hex = toHex(color);
        changed.accept(color);
    }

    // Apply a complete six or eight digit hex field.
    private void applyHexIfComplete() {
        String raw = hex.startsWith("#") ? hex.substring(1) : hex;
        if (raw.length() != 6 && raw.length() != 8) return;
        try {
            long value = Long.parseLong(raw, 16);
            int color = raw.length() == 6 ? 0xFF000000 | (int) value : (int) value;
            setColor(color, true);
        } catch (NumberFormatException ignored) {
        }
    }

    // Apply an ARGB color to the HSV state.
    private void setColor(int color, boolean notify) {
        alpha = color >>> 24 & 0xFF;
        float[] hsv = rgbToHsv(color);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        hex = toHex(color);
        if (notify) changed.accept(color);
    }

    // Get the current ARGB color.
    private int currentColor() {
        return hsv(hue, saturation, value, alpha);
    }

    // Convert HSV components to an ARGB color.
    private static int hsv(float hue, float saturation, float value, int alpha) {
        float h = Mth.clamp(hue, 0.0F, 1.0F) * 6.0F;
        float s = Mth.clamp(saturation, 0.0F, 1.0F);
        float v = Mth.clamp(value, 0.0F, 1.0F);
        int sector = (int) Math.floor(h) % 6;
        float fraction = h - (float) Math.floor(h);
        float p = v * (1.0F - s);
        float q = v * (1.0F - fraction * s);
        float t = v * (1.0F - (1.0F - fraction) * s);
        float red = switch (sector) {
            case 0 -> v;
            case 1 -> q;
            case 2 -> p;
            case 3 -> p;
            case 4 -> t;
            default -> v;
        };
        float green = switch (sector) {
            case 0 -> t;
            case 1 -> v;
            case 2 -> v;
            case 3 -> q;
            case 4 -> p;
            default -> p;
        };
        float blue = switch (sector) {
            case 0 -> p;
            case 1 -> p;
            case 2 -> t;
            case 3 -> v;
            case 4 -> v;
            default -> q;
        };
        return Mth.clamp(alpha, 0, 0xFF) << 24 | Math.round(red * 255.0F) << 16
                | Math.round(green * 255.0F) << 8 | Math.round(blue * 255.0F);
    }

    // Convert an ARGB color into HSV components.
    private static float[] rgbToHsv(int color) {
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float delta = maximum - minimum;
        float hue = 0.0F;
        if (delta > 0.00001F) {
            if (maximum == red) hue = ((green - blue) / delta + 6.0F) % 6.0F;
            else if (maximum == green) hue = (blue - red) / delta + 2.0F;
            else hue = (red - green) / delta + 4.0F;
            hue /= 6.0F;
        }
        float saturation = maximum <= 0.00001F ? 0.0F : delta / maximum;
        return new float[]{hue, saturation, maximum};
    }

    // Format an ARGB color in the theme JSON's supported hex notation.
    private static String toHex(int color) {
        int alpha = color >>> 24 & 0xFF;
        return alpha == 0xFF ? String.format("#%06X", color & 0x00FFFFFF)
                : String.format("#%08X", color);
    }

    // Draw a one-pixel outline.
    private static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // Theme-neutral modal colors supplied by the host screen.
    public record Chrome(int backdrop, int panel, int title, int raised, int hovered, int border,
                         int primary, int secondary, int accent, int actionText) {
        public static final Chrome DEFAULT = new Chrome(0xA8000000, 0xFF1C242B, 0xFF252F38,
                0xFF303B45, 0xFF43505C, 0xFF68747D, 0xFFF2F5F7, 0xFFC1CBD3, 0xFF5DA7D9,
                0xFFFFFFFF);
    }

    // Store the computed picker hit regions.
    private record Bounds(int x, int y, int width, int height, int colorSize) {
        private static final Bounds EMPTY = new Bounds(0, 0, 0, 0, 0);

        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private int colorX() {
            return x + 14;
        }

        private int colorY() {
            return y + 34;
        }

        private int hueX() {
            return colorX() + colorSize + 10;
        }

        private int hueWidth() {
            return HUE_BAR_WIDTH;
        }

        private int hexLabelX() {
            return x + 14;
        }

        private int hexX() {
            return x + 38;
        }

        private int hexY() {
            return colorY() + colorSize + 12;
        }

        private int hexRight() {
            return x + width - 68;
        }

        private int hexBottom() {
            return hexY() + 18;
        }

        private int previewX() {
            return hexRight() + 8;
        }

        private int previewRight() {
            return x + width - 14;
        }

        private int buttonY() {
            return y + height - 28;
        }

        private int buttonWidth() {
            return 76;
        }

        private int acceptX() {
            return x + width - 14 - buttonWidth();
        }

        private int cancelX() {
            return acceptX() - buttonWidth() - 8;
        }

        private boolean colorContains(double mouseX, double mouseY) {
            return mouseX >= colorX() && mouseX < colorX() + colorSize
                    && mouseY >= colorY() && mouseY < colorY() + colorSize;
        }

        private boolean hueContains(double mouseX, double mouseY) {
            return mouseX >= hueX() && mouseX < hueX() + hueWidth()
                    && mouseY >= colorY() && mouseY < colorY() + colorSize;
        }

        private boolean hexContains(double mouseX, double mouseY) {
            return mouseX >= hexX() && mouseX < hexRight() && mouseY >= hexY() && mouseY < hexBottom();
        }

        private boolean acceptContains(double mouseX, double mouseY) {
            return mouseX >= acceptX() && mouseX < acceptX() + buttonWidth()
                    && mouseY >= buttonY() && mouseY < buttonY() + 18;
        }

        private boolean cancelContains(double mouseX, double mouseY) {
            return mouseX >= cancelX() && mouseX < cancelX() + buttonWidth()
                    && mouseY >= buttonY() && mouseY < buttonY() + 18;
        }
    }
}
