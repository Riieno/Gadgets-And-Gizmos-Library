package com.rieno.gadgetsandgizmos.lib.script;

import java.util.function.IntUnaryOperator;

// Measure real tab characters at shared tab stops without replacing the stored source
public final class ScriptTextLayout{
    public static final int TAB_COLUMNS = 4;
    private ScriptTextLayout(){}

    public static Line line(String text, IntUnaryOperator glyphWidth){
        int tabWidth = Math.max(1, glyphWidth.applyAsInt(' ')) * TAB_COLUMNS;
        int[] positions = new int[text.length() + 1], columns = new int[text.length() + 1];
        int x = 0, column = 0;
        for(int idx = 0; idx < text.length();){
            int point = text.codePointAt(idx), count = Character.charCount(point);
            for(int part = 0; part < count; part++){ positions[idx + part] = x; columns[idx + part] = column; }
            x = point == '\t' ? (x / tabWidth + 1) * tabWidth : x + Math.max(0, glyphWidth.applyAsInt(point));
            column = point == '\t' ? (column / TAB_COLUMNS + 1) * TAB_COLUMNS : column + 1;
            idx += count; positions[idx] = x; columns[idx] = column;
        }
        return new Line(text, positions, columns);
    }

    public static final class Line{
        private final String text;
        private final int[] positions, columns;
        private Line(String text, int[] positions, int[] columns){ this.text = text; this.positions = positions; this.columns = columns; }
        public String text(){ return text; }
        public int width(){ return positions[text.length()]; }
        public int x(int offset){ return positions[Math.clamp(offset, 0, text.length())]; }
        public int column(int offset){ return columns[Math.clamp(offset, 0, text.length())]; }
        public int offset(int x){
            for(int idx = 0; idx < text.length();){
                int next = idx + Character.charCount(text.codePointAt(idx));
                if(x < (positions[idx] + positions[next]) / 2.0) return idx;
                idx = next;
            }
            return text.length();
        }
    }
}
