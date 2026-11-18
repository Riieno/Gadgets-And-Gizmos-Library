package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

// Share fitted terminal rendering and input without owning a script engine or network connection
public final class ScriptTerminalView{
    private final Set<Integer> keys = new LinkedHashSet<>();
    private ScriptTerminal terminal;
    private Consumer<ScriptInput> input;
    private int x, y, width, height, button = -1;
    private double pointerX, pointerY;
    private boolean focused;

    public void input(Consumer<ScriptInput> input){ blur(); this.input = input; }
    public void terminal(ScriptTerminal terminal){ if(terminal == null) blur(); this.terminal = terminal; }
    public void bounds(int x, int y, int width, int height){ this.x = x; this.y = y; this.width = width; this.height = height; }
    public boolean focused(){ return focused; }
    public boolean contains(double x, double y){ return x >= this.x && y >= this.y && x < this.x + width && y < this.y + height; }
    public void render(GuiGraphics graphics){ if(terminal != null && width > 0 && height > 0) TerminalPreview.render(graphics, terminal, x, y, width, height); }

    public boolean mouseClicked(double x, double y, int button){
        var point = point(x, y);
        if(input == null || point == null || button < 0 || button > 2) return false;
        focused = true; this.button = button; pointerX = point[0]; pointerY = point[1];
        input.accept(new ScriptInput("click", pointerX, pointerY, button)); return true;
    }
    public boolean mouseDragged(double x, double y){
        if(button < 0 || input == null) return false;
        var point = point(x, y);
        if(point != null){ pointerX = point[0]; pointerY = point[1]; input.accept(new ScriptInput("drag", pointerX, pointerY, button)); }
        return true;
    }
    public void mouseReleased(){
        if(button >= 0 && input != null) input.accept(new ScriptInput("release", pointerX, pointerY, button));
        button = -1;
    }
    public boolean mouseScrolled(double x, double y, double amount){
        var point = point(x, y);
        if(point == null || input == null || amount == 0) return false;
        input.accept(new ScriptInput("scroll", point[0], point[1], amount > 0 ? -1 : 1)); return true;
    }
    public boolean keyPressed(int key){
        if(!focused || input == null) return false;
        if(key == GLFW.GLFW_KEY_ESCAPE){ blur(); return true; }
        if(key < 0 || key > 512) return true;
        if(key == GLFW.GLFW_KEY_V && Screen.hasControlDown()){
            String text = Minecraft.getInstance().keyboardHandler.getClipboard();
            input.accept(new ScriptInput("paste", 0, 0, 0, text.substring(0, Math.min(16384, text.length()))));
        }else{ keys.add(key); input.accept(new ScriptInput("key", 0, 0, key)); }
        return true;
    }
    public boolean keyReleased(int key){
        if(!focused || input == null) return false;
        if(keys.remove(key)) input.accept(new ScriptInput("key_up", 0, 0, key)); return true;
    }
    public boolean charTyped(char ch){
        if(!focused || input == null) return false;
        if(ch >= 32 && ch <= 255) input.accept(new ScriptInput("char", 0, 0, ch)); return true;
    }
    public void blur(){
        mouseReleased();
        if(input != null) for(int key : keys) input.accept(new ScriptInput("key_up", 0, 0, key));
        keys.clear(); focused = false;
    }
    private double[] point(double x, double y){
        return terminal == null || !contains(x, y) ? null : ScriptInput.point(x - this.x, y - this.y, width, height, terminal.width(), terminal.height());
    }
}
