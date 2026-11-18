package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.LuaScripts;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.script.LuaSyntax;
import com.rieno.gadgetsandgizmos.lib.script.LuaIndentation;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTextLayout;
import com.rieno.gadgetsandgizmos.lib.script.LuaCompletions;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

// Edit a bounded Lua document with selection, history, line numbers and API completion
public final class CodeEditor{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Font font;
    private final Consumer<String> changed;
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private String text = "";
    private List<LuaSyntax.Span> spans = List.of();
    private List<String> completions = List.of();
    private List<String> matches = List.of();
    private List<ScriptPeripheral> peripherals = List.of();
    private int completionStart;
    private int completionEnd;
    private int suggestion, suggestionScroll;
    private double suggestionWheel;
    private int cursor;
    private int anchor;
    private int scroll;
    private int horizontal;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean focused = true;
    private boolean dragging;
    private boolean draggingHorizontal, suppressSuggestionSpace;
    private ScriptEditorWorkspace.Rect statusBounds;
    private HelpModalStyle style = new HelpModalStyle(0xFF121923, 0xFF1B2532, 0xFF36516D, 0xFFDCE6F2, 0xFF95A9BF, 0xFFC29BFF);

    public CodeEditor(Font font, Consumer<String> changed){ this.font = font; this.changed = changed; }

    public void source(String val){
        source(val, false);
    }

    // Reset selection and history when a host switches to another document with identical text
    public void source(String val, boolean reset){
        if(!reset && text.equals(val)) return;
        if(reset) focused = true;
        text = val;
        spans = LuaSyntax.spans(text);
        cursor = anchor = 0;
        scroll = horizontal = 0;
        undo.clear();
        redo.clear();
        matches = List.of(); suppressSuggestionSpace = false;
    }

    public void appearance(HelpModalStyle style){ this.style = style; }

    public String source(){ return text; }
    // Retain caret, scrolling and undo history separately for every open document
    public State state(){ return new State(text, cursor, anchor, scroll, horizontal, List.copyOf(undo), List.copyOf(redo)); }
    public void restore(State state){
        if(state == null || !text.equals(state.text)) return;
        cursor = state.cursor; anchor = state.anchor; scroll = state.scroll; horizontal = state.horizontal;
        undo.clear(); undo.addAll(state.undo); redo.clear(); redo.addAll(state.redo); matches = List.of();
    }
    public static final class State{
        private final String text;
        private final int cursor, anchor, scroll, horizontal;
        private final List<Snapshot> undo, redo;
        private State(String text, int cursor, int anchor, int scroll, int horizontal, List<Snapshot> undo, List<Snapshot> redo){
            this.text = text; this.cursor = cursor; this.anchor = anchor; this.scroll = scroll; this.horizontal = horizontal; this.undo = undo; this.redo = redo;
        }
    }
    public void focused(boolean val){ focused = val; if(!val){ dragging = false; draggingHorizontal = false; matches = List.of(); suppressSuggestionSpace = false; } }
    public boolean focused(){ return focused; }
    public int horizontalOffset(){ return horizontal; }
    private int horizontalLimit(){ return Math.max(0, java.util.Arrays.stream(lines()).mapToInt(row -> layout(row).width()).max().orElse(0) - width + gutter() + 8); }
    private int scrollBarY(){ return y + height - (statusBounds == null ? font.lineHeight + 10 : 8); }
    public void statusBounds(ScriptEditorWorkspace.Rect rect){ statusBounds = rect; }
    static boolean controlDown(int modifiers){ return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || Minecraft.ON_OSX && (modifiers & GLFW.GLFW_MOD_SUPER) != 0 || Screen.hasControlDown(); }
    private void scrollHorizontal(double mx){ horizontal = (int)Math.clamp((mx - x - gutter()) / Math.max(1, width - gutter()) * horizontalLimit(), 0, horizontalLimit()); }
    public void completions(Collection<String> vals){ completions = vals.stream().distinct().sorted().toList(); }
    public void peripherals(List<ScriptPeripheral> vals){ peripherals = List.copyOf(vals); }
    public void bounds(int x, int y, int width, int height){ this.x = x; this.y = y; this.width = width; this.height = height; }
    private int lineHeight(){ return font.lineHeight + 3; }
    private int gutter(){ return Math.max(32, font.width(Integer.toString(lines().length)) + 12); }
    private ScriptTextLayout.Line layout(String row){ return ScriptTextLayout.line(row, point -> font.width(Character.toString(point))); }
    private String[] lines(){ return text.split("\n", -1); }
    private int lineStart(int pos){ return text.lastIndexOf('\n', Math.max(-1, pos - 1)) + 1; }
    private int lineEnd(int pos){ int end = text.indexOf('\n', pos); return end < 0 ? text.length() : end; }
    private int line(int pos){ return (int)text.substring(0, pos).chars().filter(ch -> ch == '\n').count(); }
    private boolean contains(double mx, double my){ return mx >= x && mx < x + width && my >= y && my < y + height; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void render(GuiGraphics graphics){ render(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE); }
    public void render(GuiGraphics graphics, int mx, int my){
        graphics.fill(x, y, x + width, y + height, style.background());
        int gutter = gutter();
        graphics.fill(x, y, x + gutter - 4, y + height, style.panel());
        try(var outerClip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            String[] lines = lines();
            int offset = 0;
            int selectedStart = Math.min(cursor, anchor);
            int selectedEnd = Math.max(cursor, anchor);
            for(int idx = 0; idx < lines.length; idx++){
                String row = lines[idx];
                int top = y + 4 + (idx - scroll) * lineHeight();
                if(top + lineHeight() > y && top < scrollBarY() - font.lineHeight){
                    int left = x + gutter - horizontal;
                    var layout = layout(row);
                    graphics.drawString(font, Integer.toString(idx + 1), x + gutter - 8 - font.width(Integer.toString(idx + 1)), top, style.muted(), false);
                    try(var rowClip = GuiPoseScissor.push(graphics, x + gutter, y, x + width, scrollBarY())){
                        if(selectedStart <= offset + row.length() && selectedEnd > offset){
                            int a = Math.max(0, selectedStart - offset);
                            int b = Math.min(row.length(), selectedEnd - offset);
                            graphics.fill(left + layout.x(a), top - 1,
                                    left + layout.x(b) + (selectedEnd > offset + row.length() ? 4 : 0), top + lineHeight() - 1, style.border());
                        }
                        for(LuaSyntax.Span span : spans){
                            if(span.end() <= offset) continue;
                            if(span.start() >= offset + row.length()) break;
                            int a = Math.max(offset, span.start());
                            int b = Math.min(offset + row.length(), span.end());
                            int col = CodePreview.color(span.kind(), style.text(), style.muted());
                            drawSpan(graphics, row, a - offset, b - offset, left, top, col, layout);
                        }
                        if(focused && cursor >= offset && cursor <= offset + row.length() && System.currentTimeMillis() % 1000 < 550){
                            int caret = left + layout.x(cursor - offset);
                            graphics.fill(caret, top - 1, caret + 1, top + font.lineHeight + 1, style.text());
                        }
                    }
                }
                offset += row.length() + 1;
            }
            if(horizontalLimit() > 0){
                int left = x + gutter, available = width - gutter;
                horizontal = Math.clamp(horizontal, 0, horizontalLimit());
                graphics.fill(left, scrollBarY(), x + width, scrollBarY() + 5, style.panel());
                int thumb = Math.max(14, available * available / (available + horizontalLimit()));
                int thumbOffset = (int)((double)horizontal / horizontalLimit() * (available - thumb));
                graphics.fill(left + thumbOffset, scrollBarY(), left + thumbOffset + thumb, scrollBarY() + 5, style.border());
            }
        }
        var status = statusBounds == null ? new ScriptEditorWorkspace.Rect(x + 5, y + height - font.lineHeight - 2, Math.max(0, width - 10), font.lineHeight) : statusBounds;
        if(status.width() > 0 && status.height() > 0){
            String label = "Ln " + (line(cursor) + 1) + ", Col " + (layout(text.substring(lineStart(cursor), cursor)).column(cursor - lineStart(cursor)) + 1)
                    + "   Tab: complete   Ctrl+Space: suggestions";
            try(var clip = GuiPoseScissor.push(graphics, status.x(), status.y(), status.right(), status.bottom())){
                graphics.drawString(font, label, status.x(), status.y() + (status.height() - font.lineHeight) / 2, style.muted(), false);
            }
        }
        if(!matches.isEmpty()) renderSuggestions(graphics, mx, my);
    }

    // Skip tab glyphs while using their measured space for selections, carets and text
    private void drawSpan(GuiGraphics graphics, String row, int start, int end, int left, int top, int color, ScriptTextLayout.Line layout){
        while(start < end){
            if(row.charAt(start) == '\t'){ start++; continue; }
            int tab = row.indexOf('\t', start), next = tab < 0 ? end : Math.min(end, tab);
            graphics.drawString(font, row.substring(start, next), left + layout.x(start), top, color, false); start = next;
        }
    }

    private int suggestionRows(){ return Math.min(Math.min(6, matches.size()), Math.max(1, (height - 8) / 14)); }
    private int firstSuggestion(){ return Math.clamp(suggestionScroll, 0, Math.max(0, matches.size() - suggestionRows())); }
    private void revealSuggestion(){
        int first = firstSuggestion(), rows = suggestionRows();
        if(suggestion < first) first = suggestion;
        else if(suggestion >= first + rows) first = suggestion - rows + 1;
        suggestionScroll = Math.clamp(first, 0, Math.max(0, matches.size() - rows)); suggestionWheel = 0;
    }

    // Consume wheel movement over the popup without scrolling or dismissing the source editor
    public boolean scrollSuggestions(double mx, double my, double amount){
        if(matches.isEmpty() || !suggestionsBounds().contains(mx, my)) return false;
        if(!Double.isFinite(amount)) return true;
        suggestionWheel -= amount; int steps = (int)suggestionWheel; suggestionWheel -= steps;
        long next = (long)firstSuggestion() + steps; int maximum = Math.max(0, matches.size() - suggestionRows());
        suggestionScroll = Math.clamp(next, 0, maximum);
        if(next < 0 || next > maximum) suggestionWheel = 0;
        suggestion = Math.clamp(suggestion, suggestionScroll, suggestionScroll + suggestionRows() - 1);
        return true;
    }
    private ScriptEditorWorkspace.Rect suggestionsBounds(){
        int popupWidth = Math.min(170, Math.max(1, width)), popupHeight = suggestionRows() * 14 + 4;
        int px = Math.clamp(x + gutter() + layout(text.substring(lineStart(cursor), cursor)).width() - horizontal, x, Math.max(x, x + width - popupWidth));
        int py = Math.clamp(y + 6 + (line(cursor) - scroll + 1) * lineHeight(), y, Math.max(y, y + height - popupHeight - 8));
        return new ScriptEditorWorkspace.Rect(px, py, popupWidth, popupHeight);
    }
    private void renderSuggestions(GuiGraphics graphics, int mx, int my){
        var rect = suggestionsBounds(); int first = firstSuggestion();
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), style.panel());
        for(int idx = first; idx < Math.min(first + suggestionRows(), matches.size()); idx++){
            int top = rect.y() + 2 + (idx - first) * 14;
            boolean hovered = mx >= rect.x() && mx < rect.right() && my >= top && my < top + 14;
            if(idx == suggestion || hovered) graphics.fill(rect.x() + 1, top, rect.right() - 1, top + 14, style.border());
            graphics.drawString(font, font.plainSubstrByWidth(matches.get(idx), Math.max(1, rect.width() - 8)), rect.x() + 4, top + 2, style.text(), false);
        }
    }

    // Suggestions sit above editor controls and must consume clicks before caret movement
    public boolean clickSuggestion(double mx, double my, int button){
        if(matches.isEmpty()) return false;
        var rect = suggestionsBounds(); if(!rect.contains(mx, my)) return false;
        int row = (int)Math.floor((my - rect.y() - 2) / 14);
        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && row >= 0 && row < suggestionRows()) complete(firstSuggestion() + row);
        return true;
    }
    private void complete(int idx){
        if(idx < 0 || idx >= matches.size()) return;
        String val = matches.get(idx); anchor = completionStart; cursor = completionEnd;
        if(val.endsWith("(") && cursor < text.length() && text.charAt(cursor) == '(') val = val.substring(0, val.length() - 1);
        dragging = draggingHorizontal = false; insert(val); matches = List.of();
    }

    public boolean mouseClicked(double mx, double my, int button){
        if(clickSuggestion(mx, my, button)) return true;
        if(!contains(mx, my)){ focused = false; return false; }
        focused = true;
        if(button == 0 && my >= scrollBarY() && my < scrollBarY() + 6 && horizontalLimit() > 0){
            draggingHorizontal = true; scrollHorizontal(mx); return true;
        }
        if(button == 0){ dragging = true; move(position(mx, my), Screen.hasShiftDown()); matches = List.of(); }
        return true;
    }

    public boolean mouseDragged(double mx, double my){
        if(draggingHorizontal){ scrollHorizontal(mx); return true; }
        if(!dragging) return false;
        move(position(mx, my), true);
        return true;
    }

    public void mouseReleased(){ dragging = false; draggingHorizontal = false; }
    public boolean mouseScrolled(double amount){ return mouseScrolled(0, amount, Screen.hasShiftDown()); }
    public boolean mouseScrolled(double horizontal, double amount, boolean shift){
        if(shift || horizontal != 0){
            this.horizontal = Math.clamp(this.horizontal - (int)Math.round((horizontal != 0 ? horizontal : amount) * 24), 0, horizontalLimit());
            matches = List.of(); return true;
        }
        scroll = Math.max(0, Math.min(Math.max(0, lines().length - Math.max(1, height / lineHeight() - 2)), scroll - (int)(amount * 3)));
        matches = List.of();
        return true;
    }

    private int position(double mx, double my){
        String[] rows = lines();
        int row = Math.max(0, Math.min(rows.length - 1, scroll + (int)((my - y - 4) / lineHeight())));
        int offset = 0;
        for(int idx = 0; idx < row; idx++) offset += rows[idx].length() + 1;
        return offset + layout(rows[row]).offset(Math.max(0, (int)mx - x - gutter() + horizontal));
    }

    public boolean charTyped(char ch){ return charTyped(ch, 0); }
    public boolean charTyped(char ch, int modifiers){
        if(focused && ch == ' ' && (suppressSuggestionSpace || controlDown(modifiers))){ suppressSuggestionSpace = false; return true; }
        suppressSuggestionSpace = false;
        if(!focused || ch < 32 || ch == 127) return false;
        insert(String.valueOf(ch));
        suggest(false);
        return true;
    }

    public boolean keyPressed(int key){ return keyPressed(key, 0); }
    public boolean keyPressed(int key, int modifiers){
        if(!focused) return false;
        boolean ctrl = controlDown(modifiers);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown();
        suppressSuggestionSpace = false;
        if(ctrl){
            switch(key){
                case GLFW.GLFW_KEY_A -> { anchor = 0; cursor = text.length(); return true; }
                case GLFW.GLFW_KEY_C -> { clipboard(false); return true; }
                case GLFW.GLFW_KEY_X -> { clipboard(true); return true; }
                case GLFW.GLFW_KEY_V -> { insert(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; }
                case GLFW.GLFW_KEY_Z -> { history(shift ? redo : undo, shift ? undo : redo); return true; }
                case GLFW.GLFW_KEY_Y -> { history(redo, undo); return true; }
                case GLFW.GLFW_KEY_SPACE -> { suppressSuggestionSpace = true; suggest(true); return true; }
            }
        }
        if(key == GLFW.GLFW_KEY_TAB && !shift && matches.isEmpty() && cursor == anchor) suggest(false);
        if(!matches.isEmpty() && !(key == GLFW.GLFW_KEY_TAB && shift)){
            if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP){
                suggestion = Math.floorMod(suggestion + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), matches.size()); revealSuggestion(); return true;
            }
            if(key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ENTER){
                complete(suggestion);
                return true;
            }
            if(key == GLFW.GLFW_KEY_ESCAPE){ matches = List.of(); return true; }
        }
        switch(key){
            case GLFW.GLFW_KEY_LEFT -> move(ctrl ? word(-1) : !shift && cursor != anchor ? Math.min(cursor, anchor) : cursor - 1, shift);
            case GLFW.GLFW_KEY_RIGHT -> move(ctrl ? word(1) : !shift && cursor != anchor ? Math.max(cursor, anchor) : cursor + 1, shift);
            case GLFW.GLFW_KEY_HOME -> move(ctrl ? 0 : lineStart(cursor), shift);
            case GLFW.GLFW_KEY_END -> move(ctrl ? text.length() : lineEnd(cursor), shift);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                int count = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN ? Math.max(1, height / lineHeight() - 2) : 1;
                int direction = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1;
                move(position(x + gutter() + layout(text.substring(lineStart(cursor), cursor)).width() - horizontal,
                        y + 4 + (line(cursor) - scroll + direction * count) * lineHeight()), shift);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> { if(cursor == anchor) anchor = Math.max(0, ctrl ? word(-1) : cursor - 1); insert(""); }
            case GLFW.GLFW_KEY_DELETE -> { if(cursor == anchor) anchor = Math.min(text.length(), ctrl ? word(1) : cursor + 1); insert(""); }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insert("\n" + LuaIndentation.newLineIndent(text, cursor));
            }
            case GLFW.GLFW_KEY_TAB -> indent(shift);
            case GLFW.GLFW_KEY_ESCAPE -> { return false; }
            default -> { return false; }
        }
        matches = List.of();
        return true;
    }

    public void insert(String val){
        focused = true;
        val = val.replace("\r\n", "\n").replace('\r', '\n');
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        int available = LuaScripts.MAX_SOURCE_LENGTH - text.length() + end - start;
        if(val.length() > available) val = val.substring(0, Math.max(0, available));
        String next = text.substring(0, start) + val + text.substring(end);
        var aligned = LuaIndentation.alignClosers(next, start, start + val.length());
        next = aligned.source();
        if(text.equals(next)){ cursor = anchor = aligned.cursor(); reveal(); return; }
        undo.addLast(new Snapshot(text, cursor, anchor));
        while(undo.size() > 100) undo.removeFirst();
        redo.clear();
        text = next;
        cursor = anchor = aligned.cursor();
        update();
    }

    private void update(){ spans = LuaSyntax.spans(text); reveal(); changed.accept(text); }

    private void indent(boolean outdent){
        if(!outdent && cursor == anchor){ insert(LuaIndentation.INDENT); return; }
        int start = lineStart(Math.min(cursor, anchor));
        int end = lineEnd(Math.max(cursor, anchor));
        String[] rows = text.substring(start, end).split("\n", -1);
        for(int idx = 0; idx < rows.length; idx++){
            rows[idx] = outdent ? LuaIndentation.outdent(rows[idx]) : LuaIndentation.INDENT + rows[idx];
        }
        String val = String.join("\n", rows);
        boolean selected = cursor != anchor;
        int column = cursor - start;
        cursor = end;
        anchor = start;
        insert(val);
        if(selected) anchor = start;
        else move(Math.min(start + val.length(), start + Math.max(0, column - (end - start - val.length()))), false);
    }
    private void move(int pos, boolean selecting){ cursor = Math.max(0, Math.min(text.length(), pos)); if(!selecting) anchor = cursor; reveal(); }
    private void reveal(){
        int row = line(cursor);
        int rows = Math.max(1, height / lineHeight() - 2);
        if(row < scroll) scroll = row;
        if(row >= scroll + rows) scroll = row - rows + 1;
        int col = layout(text.substring(lineStart(cursor), cursor)).width();
        int visible = Math.max(20, width - gutter() - 12);
        if(col < horizontal) horizontal = col;
        if(col > horizontal + visible) horizontal = col - visible;
    }

    private void clipboard(boolean cut){
        if(cursor == anchor) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)));
        if(cut) insert("");
    }

    private void history(ArrayDeque<Snapshot> src, ArrayDeque<Snapshot> dst){
        if(src.isEmpty()) return;
        dst.addLast(new Snapshot(text, cursor, anchor));
        Snapshot val = src.removeLast();
        text = val.text(); cursor = val.cursor(); anchor = val.anchor(); matches = List.of(); update();
    }

    private int word(int direction){
        int idx = cursor;
        if(direction < 0){ while(idx > 0 && Character.isWhitespace(text.charAt(idx - 1))) idx--; while(idx > 0 && Character.isJavaIdentifierPart(text.charAt(idx - 1))) idx--; }
        else{ while(idx < text.length() && Character.isJavaIdentifierPart(text.charAt(idx))) idx++; while(idx < text.length() && Character.isWhitespace(text.charAt(idx))) idx++; }
        return idx == cursor ? cursor + direction : idx;
    }

    private void suggest(boolean force){
        var res = LuaCompletions.suggest(text, cursor, completions, peripherals, force);
        completionStart = res.start(); completionEnd = res.end(); matches = res.values();
        suggestion = suggestionScroll = 0; suggestionWheel = 0;
    }

    private record Snapshot(String text, int cursor, int anchor){}
}
