package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import dan200.computercraft.core.terminal.Terminal;

import java.nio.ByteBuffer;

// Keep monitor discovery and configuration separate from explicit drawing
public final class ComputerCraftMonitorTerminal extends Terminal{
    private boolean output;

    public ComputerCraftMonitorTerminal(int width, int height, boolean colour, Runnable changed){
        super(width, height, colour, changed);
    }

    public synchronized boolean hasOutput(){ return output; }
    public synchronized void releaseOutput(){ output = false; setChanged(); }

    // Host geometry changes must not claim an unused display
    public synchronized void resizeAndClear(int width, int height){
        super.resize(width, height); super.clear();
    }

    private void drawing(){
        if(output) return;
        output = true; setChanged();
    }

    @Override public synchronized void write(String text){
        if(!text.isEmpty()) drawing();
        super.write(text);
    }
    @Override public synchronized void blit(ByteBuffer text, ByteBuffer foreground, ByteBuffer background){
        if(text.hasRemaining()) drawing();
        super.blit(text, foreground, background);
    }
    @Override public synchronized void clear(){ drawing(); super.clear(); }
    @Override public synchronized void clearLine(){ drawing(); super.clearLine(); }
    @Override public synchronized void scroll(int count){ drawing(); super.scroll(count); }
    @Override public synchronized void setLine(int row, String text, String foreground, String background){
        drawing(); super.setLine(row, text, foreground, background);
    }
}
