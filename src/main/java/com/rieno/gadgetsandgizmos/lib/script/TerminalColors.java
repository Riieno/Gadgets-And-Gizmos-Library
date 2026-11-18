package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Retain direct ARGB cells independently of a terminal's sixteen palette indices
public final class TerminalColors{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final long PALETTE = -1;
    public static final int MAX_CELLS = 262144;
    public static final int MAX_BYTES = 28 + MAX_CELLS * 16;
    private int width, height;
    private long[] foreground, background;
    private long front = PALETTE, back = PALETTE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public TerminalColors(int width, int height){ resize(width, height); }

    public long foreground(){ return front; }
    public long background(){ return back; }
    public void foreground(long val){ front = ink(val); }
    public void background(long val){ back = ink(val); }
    public boolean hasColors(){ return foreground != null || front != PALETTE || back != PALETTE; }
    public long cell(int x, int y, boolean text){ return foreground == null ? PALETTE : (text ? foreground : background)[y * width + x]; }

    // Preserve overlapping cells and use current inks for newly exposed space
    public void resize(int width, int height){
        if(width < 1 || height < 1 || (long)width * height > MAX_CELLS) throw new IllegalArgumentException("Invalid terminal dimensions");
        if(this.width == width && this.height == height) return;
        long[] oldFront = foreground, oldBack = background;
        int oldWidth = this.width, oldHeight = this.height;
        this.width = width; this.height = height;
        foreground = null; background = null;
        if(oldFront == null && front == PALETTE && back == PALETTE) return;
        allocate();
        Arrays.fill(foreground, front); Arrays.fill(background, back);
        if(oldFront == null){
            for(int row = 0; row < Math.min(oldHeight, height); row++){
                Arrays.fill(foreground, row * width, row * width + Math.min(oldWidth, width), PALETTE);
                Arrays.fill(background, row * width, row * width + Math.min(oldWidth, width), PALETTE);
            }
            return;
        }
        for(int row = 0; row < Math.min(oldHeight, height); row++){
            System.arraycopy(oldFront, row * oldWidth, foreground, row * width, Math.min(oldWidth, width));
            System.arraycopy(oldBack, row * oldWidth, background, row * width, Math.min(oldWidth, width));
        }
    }

    private void allocate(){
        if(foreground != null) return;
        foreground = new long[width * height]; background = new long[width * height];
        Arrays.fill(foreground, PALETTE); Arrays.fill(background, PALETTE);
    }

    // Clip both ordinary writes and explicit hex blits to the same character cells
    public void paint(int x, int y, int count, long front, long back){
        if(y < 0 || y >= height || count <= 0) return;
        int first = Math.max(0, x), last = (int)Math.min(width, (long)x + count);
        if(first >= last || foreground == null && front == PALETTE && back == PALETTE) return;
        allocate();
        Arrays.fill(foreground, y * width + first, y * width + last, front);
        Arrays.fill(background, y * width + first, y * width + last, back);
    }
    public void write(int x, int y, int count){ paint(x, y, count, front, back); }
    public void clear(){ for(int row = 0; row < height; row++) write(0, row, width); }
    public void clearLine(int row){ write(0, row, width); }
    public void reset(){ front = back = PALETTE; foreground = background = null; }

    public void scroll(int count){
        if(count == 0 || foreground == null && front == PALETTE && back == PALETTE) return;
        allocate();
        long[] oldFront = foreground.clone(), oldBack = background.clone();
        Arrays.fill(foreground, front); Arrays.fill(background, back);
        for(int row = 0; row < height; row++){
            long src = (long)row + count;
            if(src < 0 || src >= height) continue;
            System.arraycopy(oldFront, (int)src * width, foreground, row * width, width);
            System.arraycopy(oldBack, (int)src * width, background, row * width, width);
        }
    }

    // Transfer immutable bytes through an owning transport or saved-data container
    public byte[] encode(){
        if(!hasColors()) return new byte[0];
        allocate();
        ByteBuffer buf = ByteBuffer.allocate(28 + width * height * 16);
        buf.putInt(1).putInt(width).putInt(height).putLong(front).putLong(back);
        for(int idx = 0; idx < foreground.length; idx++) buf.putLong(foreground[idx]).putLong(background[idx]);
        return buf.array();
    }
    public void decode(byte[] data){
        reset();
        if(data.length == 0) return;
        if(data.length != 28 + width * height * 16) throw new IllegalArgumentException("Invalid terminal color data");
        ByteBuffer buf = ByteBuffer.wrap(data);
        if(buf.getInt() != 1 || buf.getInt() != width || buf.getInt() != height) throw new IllegalArgumentException("Invalid terminal color dimensions");
        front = ink(buf.getLong()); back = ink(buf.getLong()); allocate();
        for(int idx = 0; idx < foreground.length; idx++){
            foreground[idx] = ink(buf.getLong()); background[idx] = ink(buf.getLong());
        }
    }
    private static long ink(long val){
        if(val != PALETTE && (val < 0 || val > 0xFFFFFFFFL)) throw new IllegalArgumentException("Invalid terminal color");
        return val;
    }
    public static String hex(long val){ return val == PALETTE ? "--------" : String.format(Locale.ROOT, "%08x", val); }
    public List<String> rows(boolean text){
        if(!hasColors()) return List.of();
        var rows = new java.util.ArrayList<String>();
        for(int row = 0; row < height; row++){
            StringBuilder line = new StringBuilder(width * 8);
            for(int col = 0; col < width; col++) line.append(hex(cell(col, row, text)));
            rows.add(line.toString());
        }
        return List.copyOf(rows);
    }
}
