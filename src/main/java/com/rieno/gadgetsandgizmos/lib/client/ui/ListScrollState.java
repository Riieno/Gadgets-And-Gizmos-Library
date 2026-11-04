package com.rieno.gadgetsandgizmos.lib.client.ui;

// Pixel scrolling for fixed-height list rows, including partially visible rows.
public final class ListScrollState{
    private int count;
    private int rowHeight = 1;
    private int viewportHeight = 1;
    private int offset;

    public void configure(int count, int rowHeight, int viewportHeight){
        this.count = Math.max(0, count);
        this.rowHeight = Math.max(1, rowHeight);
        this.viewportHeight = Math.max(1, viewportHeight);
        offset = Math.clamp(offset, 0, maximumOffset());
    }

    public int offset(){ return offset; }
    public int maximumOffset(){ return (int)Math.clamp((long)count * rowHeight - viewportHeight, 0L, Integer.MAX_VALUE); }
    public int firstVisibleIndex(){ return offset / rowHeight; }
    public int rowOffset(int index){ return index * rowHeight - offset; }
    public double fraction(){ return maximumOffset() == 0 ? 0 : offset / (double)maximumOffset(); }

    public void scrollBy(int pixels){
        offset = (int)Math.clamp((long)offset + pixels, 0L, maximumOffset());
    }

    public void scrollToFraction(double fraction){
        if(!Double.isFinite(fraction)) return;
        offset = (int)Math.round(Math.clamp(fraction, 0, 1) * maximumOffset());
    }

    public void ensureVisible(int index){
        if(index < 0 || index >= count) return;
        int top = index * rowHeight;
        if(top < offset) offset = top;
        else if(top + rowHeight > offset + viewportHeight) offset = top + rowHeight - viewportHeight;
        offset = Math.clamp(offset, 0, maximumOffset());
    }
}
