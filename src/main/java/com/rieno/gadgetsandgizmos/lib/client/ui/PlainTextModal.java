package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

// Display local text or Markdown with scrolling, selection and clipboard support inside an existing screen
public final class PlainTextModal{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Component title;
    private final String content;
    private final Component copyLabel;
    private final ListScrollState scroll = new ListScrollState();
    private List<String> lines = List.of();
    private int layoutWidth = -1;
    private int lineHeight = 12;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean dragging;
    private boolean draggingScroll;
    private MarkdownView markdown;
    // Keep the caller's painted frame and controls in their original texture coordinates
    private TextureAppearance appearance;
    // Run an optional caller-owned action beside the copy control
    private Component actionLabel;
    private Runnable action;

    public PlainTextModal(Component title, String content, Component copyLabel){
        this.title = title;
        this.content = content;
        this.copyLabel = copyLabel;
    }

    // Use the owning mod's painted panel without changing text or clipboard behavior
    public PlainTextModal withAppearance(TextureAppearance appearance){
        this.appearance = Objects.requireNonNull(appearance);
        layoutWidth = -1;
        return this;
    }

    // Add a contextual action without coupling the reusable modal to its host screen
    public PlainTextModal withAction(Component label, Runnable action){
        actionLabel = Objects.requireNonNull(label);
        this.action = Objects.requireNonNull(action);
        return this;
    }

    // Render local Markdown with the existing document renderer and no external asset loading
    public PlainTextModal withMarkdown(){
        if(markdown != null) markdown.close();
        markdown = new MarkdownView(target -> java.net.URI.create("local:/disabled"), uri -> java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalArgumentException("External assets are disabled in a text modal")));
        markdown.selectable();
        markdown.document(MarkdownDocument.parse(content));
        return this;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, HelpModalStyle style){
        var bounds = bounds(width, height);
        var body = body(bounds);
        if(markdown == null && layoutWidth != body.width()){
            layoutWidth = body.width();
            lineHeight = font.lineHeight + 3;
            lines = font.getSplitter().splitLines(content, Math.max(1, body.width() - 12), Style.EMPTY).stream()
                    .map(net.minecraft.network.chat.FormattedText::getString).toList();
            selectionStart = -1;
            selectionEnd = -1;
        }
        scroll.configure(lines.size() * lineHeight, 1, body.height());
        HelpModalFrame.render(graphics, font, width, height, bounds, title, style, mouseX, mouseY, frame());
        if(markdown != null) markdown.render(graphics, font, body, style, mouseX, mouseY);
        else{
            try(var clip = GuiPoseScissor.push(graphics, body.x(), body.y(), body.right() - 7, body.bottom())){
                int first = Math.max(0, scroll.offset() / lineHeight);
                int last = Math.min(lines.size(), first + body.height() / lineHeight + 2);
                for(int idx = first; idx < last; idx++){
                    int y = body.y() + idx * lineHeight - scroll.offset();
                    if(selected(idx)) graphics.fill(body.x(), y, body.right() - 8, y + lineHeight, style.border());
                    graphics.drawString(font, lines.get(idx), body.x() + 2, y + 1, style.text(), false);
                }
            }
            HelpModalFrame.scrollbar(graphics, body, scroll, style);
        }
        var copy = copy(bounds);
        if(appearance == null){
            graphics.fill(copy.x(), copy.y(), copy.right(), copy.bottom(), copy.contains(mouseX, mouseY) ? style.border() : style.panel());
            graphics.drawCenteredString(font, copyLabel, copy.x() + copy.width() / 2, copy.y() + 5, style.text());
        }else{
            HelpModalFrame.texture(graphics, appearance.copyButton(), copy);
            if(copy.contains(mouseX, mouseY)) graphics.fill(copy.x(), copy.y(), copy.right(), copy.bottom(), 0x33FFFFFF);
            HelpModalFrame.text(graphics, font, copyLabel, copy, frame().footerColor(), true);
        }
        if(action != null){
            var control = action(bounds);
            if(appearance == null){
                graphics.fill(control.x(), control.y(), control.right(), control.bottom(),
                        control.contains(mouseX, mouseY) ? style.border() : style.panel());
                HelpModalFrame.text(graphics, font, actionLabel, control, style.text(), true);
            }else{
                HelpModalFrame.texture(graphics, appearance.copyButton(), control);
                if(control.contains(mouseX, mouseY)) graphics.fill(control.x(), control.y(), control.right(), control.bottom(), 0x33FFFFFF);
                HelpModalFrame.text(graphics, font, actionLabel, control, frame().footerColor(), true);
            }
        }
    }

    public boolean mouseClicked(int width, int height, double mouseX, double mouseY, int button, Runnable close){
        var bounds = bounds(width, height);
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if(HelpModalFrame.close(bounds, frame()).contains(mouseX, mouseY)){ close.run(); return true; }
        if(action != null && action(bounds).contains(mouseX, mouseY)){ action.run(); return true; }
        if(copy(bounds).contains(mouseX, mouseY)){ copyText(); return true; }
        var body = body(bounds);
        if(markdown != null){ markdown.click(body, mouseX, mouseY, ignored -> {}); return true; }
        if(body.contains(mouseX, mouseY)){
            if(mouseX >= body.right() - 7){
                draggingScroll = true;
                scroll.scrollToFraction((mouseY - body.y()) / Math.max(1, body.height()));
            }else{
                selectionStart = lineAt(body, mouseY);
                selectionEnd = selectionStart;
                dragging = true;
            }
        }
        return true;
    }

    public boolean mouseDragged(int width, int height, double mouseY){
        var body = body(bounds(width, height));
        if(markdown != null){ markdown.drag(body, mouseY); return true; }
        if(draggingScroll) scroll.scrollToFraction((mouseY - body.y()) / Math.max(1, body.height()));
        if(dragging){
            if(mouseY < body.y()) scroll.scrollBy(-lineHeight);
            if(mouseY >= body.bottom()) scroll.scrollBy(lineHeight);
            selectionEnd = lineAt(body, mouseY);
        }
        return true;
    }

    public boolean mouseReleased(){ if(markdown != null) markdown.release(); dragging = false; draggingScroll = false; return true; }

    public boolean mouseScrolled(double amount){
        if(markdown != null) markdown.scroll(amount, false);
        else scroll.scrollBy((int)Math.round(-amount * lineHeight * 3));
        return true;
    }

    public boolean keyPressed(int key, Runnable close){
        if(key == GLFW.GLFW_KEY_ESCAPE){ close.run(); return true; }
        if(markdown != null){
            if(Screen.hasControlDown() && key == GLFW.GLFW_KEY_A) markdown.selectAll();
            else if(Screen.hasControlDown() && key == GLFW.GLFW_KEY_C) copyText();
            else if(key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) markdown.end(key == GLFW.GLFW_KEY_END);
            else if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) markdown.scroll(key == GLFW.GLFW_KEY_DOWN ? -1 : -8, false);
            else if(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) markdown.scroll(key == GLFW.GLFW_KEY_UP ? 1 : 8, false);
            return true;
        }
        if(Screen.hasControlDown() && key == GLFW.GLFW_KEY_A){
            selectionStart = 0;
            selectionEnd = lines.size() - 1;
        }else if(Screen.hasControlDown() && key == GLFW.GLFW_KEY_C){
            copyText();
        }else if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN){
            scroll.scrollBy(lineHeight * (key == GLFW.GLFW_KEY_DOWN ? 1 : 12));
        }else if(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP){
            scroll.scrollBy(-lineHeight * (key == GLFW.GLFW_KEY_UP ? 1 : 12));
        }else if(key == GLFW.GLFW_KEY_HOME) scroll.scrollToFraction(0);
        else if(key == GLFW.GLFW_KEY_END) scroll.scrollToFraction(1);
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private void copyText(){
        if(markdown != null){
            String selected = markdown.selectedText();
            Minecraft.getInstance().keyboardHandler.setClipboard(markdown.allSelected() || selected.isEmpty() ? content : selected);
            return;
        }
        if(selectionStart < 0 || lines.isEmpty()){
            Minecraft.getInstance().keyboardHandler.setClipboard(content);
            return;
        }
        int first = Math.max(0, Math.min(selectionStart, selectionEnd));
        int last = Math.min(lines.size() - 1, Math.max(selectionStart, selectionEnd));
        String selected = first == 0 && last == lines.size() - 1 ? content : String.join("\n", lines.subList(first, last + 1));
        Minecraft.getInstance().keyboardHandler.setClipboard(selected);
    }

    private boolean selected(int idx){
        return selectionStart >= 0 && idx >= Math.min(selectionStart, selectionEnd) && idx <= Math.max(selectionStart, selectionEnd);
    }

    private int lineAt(HelpModalFrame.Rect body, double mouseY){
        return Math.max(0, Math.min(lines.size() - 1, ((int)mouseY - body.y() + scroll.offset()) / lineHeight));
    }

    private HelpModalFrame.Rect body(HelpModalFrame.Rect bounds){
        if(appearance != null) return HelpModalFrame.region(bounds, frame(), appearance.content());
        return new HelpModalFrame.Rect(bounds.x() + 10, bounds.y() + 32, Math.max(1, bounds.width() - 20), Math.max(1, bounds.height() - 68));
    }

    private HelpModalFrame.Rect copy(HelpModalFrame.Rect bounds){
        if(appearance != null) return HelpModalFrame.region(bounds, frame(), appearance.copy());
        return new HelpModalFrame.Rect(bounds.right() - 94, bounds.bottom() - 28, 84, 20);
    }

    // Keep the optional action beside Copy using the same painted control dimensions
    private HelpModalFrame.Rect action(HelpModalFrame.Rect bounds){
        if(appearance != null){
            var copy = appearance.copy();
            return HelpModalFrame.region(bounds, frame(), new HelpModalAppearance.Region(
                    Math.max(0, copy.x() - copy.width() - 3), copy.y(), copy.width(), copy.height()));
        }
        var copy = copy(bounds);
        return new HelpModalFrame.Rect(copy.x() - 128, copy.y(), 120, copy.height());
    }

    // Fit the complete painted panel and use the same bounds for drawing and input
    private HelpModalFrame.Rect bounds(int width, int height){
        return HelpModalFrame.bounds(width, height, 680, frame());
    }

    // Retain the original frame for callers which do not supply artwork
    private HelpModalAppearance frame(){ return appearance == null ? null : appearance.frame(); }

    // Describe caller-owned artwork and the content and copy regions inside it
    public record TextureAppearance(HelpModalAppearance frame, GuiTextureRegion copyButton,
                                    HelpModalAppearance.Region content, HelpModalAppearance.Region copy){
        // Keep every interactive region within the painted panel
        public TextureAppearance{
            Objects.requireNonNull(frame);
            Objects.requireNonNull(copyButton);
            for(var region : List.of(content, copy)){
                if(region.x() + region.width() > frame.panel().width()
                        || region.y() + region.height() > frame.panel().height())
                    throw new IllegalArgumentException("Text modal regions must fit inside their painted panel");
            }
        }
    }
}

