package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;

// Match real Lua block keywords while leaving strings, comments and unrelated lines unchanged
public final class LuaIndentation{
    public static final String INDENT = "\t";
    public record Edit(String source, int cursor){}
    private record Change(int start, int end, String indent){}
    private static final class Block{
        private final String kind, indent;
        private final int line;
        private boolean body;
        private Block(String kind, String indent, int line, boolean body){ this.kind = kind; this.indent = indent; this.line = line; this.body = body; }
    }
    private LuaIndentation(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static String newLineIndent(String source, int cursor){
        cursor = Math.clamp(cursor, 0, source.length());
        int start = lineStart(source, cursor); String indent = leading(source, start);
        List<Block> stack = new ArrayList<>(); String closing = null; Block branch = null;
        for(var span : LuaSyntax.spans(source)){
            if(span.start() >= cursor) break;
            if(span.kind() != LuaSyntax.Kind.KEYWORD || span.end() > cursor) continue;
            String word = source.substring(span.start(), span.end()); int line = lineStart(source, span.start());
            String target = closer(stack, word);
            if(line == start && first(source, line, span.start()) && target != null){
                closing = target; branch = word.equals("else") || word.equals("elseif") ? stack.getLast() : null;
            }
            advance(stack, word, leading(source, line), line);
        }
        if(closing != null) return closing + (branch != null && stack.contains(branch) ? INDENT : "");
        if(!stack.isEmpty()){
            var block = stack.getLast();
            if(block.line == start && block.body) return block.indent + INDENT;
        }
        return indent;
    }

    public static Edit alignClosers(String source, int from, int cursor){
        from = Math.clamp(from, 0, source.length()); cursor = Math.clamp(cursor, from, source.length());
        int firstLine = lineStart(source, from), lastLine = lineStart(source, cursor);
        List<Block> stack = new ArrayList<>(); List<Change> changes = new ArrayList<>();
        for(var span : LuaSyntax.spans(source)){
            if(span.kind() != LuaSyntax.Kind.KEYWORD) continue;
            int line = lineStart(source, span.start()); if(line > lastLine) break;
            String word = source.substring(span.start(), span.end()), indent = leading(source, line);
            String target = closer(stack, word);
            if(line >= firstLine && target != null && first(source, line, span.start()) && !indent.equals(target))
                changes.add(new Change(line, line + indent.length(), target));
            advance(stack, word, target != null && first(source, line, span.start()) ? target : indent, line);
        }
        StringBuilder res = new StringBuilder(source); int nextCursor = cursor;
        for(int idx = changes.size() - 1; idx >= 0; idx--){
            var change = changes.get(idx); res.replace(change.start(), change.end(), change.indent());
            if(change.end() <= cursor) nextCursor += change.indent().length() - change.end() + change.start();
        }
        return res.length() > LuaScripts.MAX_SOURCE_LENGTH ? new Edit(source, cursor) : new Edit(res.toString(), Math.clamp(nextCursor, 0, res.length()));
    }

    public static String outdent(String row){
        if(row.startsWith(INDENT)) return row.substring(1);
        int count = 0; while(count < Math.min(ScriptTextLayout.TAB_COLUMNS, row.length()) && row.charAt(count) == ' ') count++;
        return row.substring(count);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static String closer(List<Block> stack, String word){
        if(stack.isEmpty()) return null;
        var block = stack.getLast();
        return switch(word){
            case "end" -> !block.kind.equals("repeat") ? block.indent : null;
            case "until" -> block.kind.equals("repeat") ? block.indent : null;
            case "else", "elseif" -> block.kind.equals("if") ? block.indent : null;
            default -> null;
        };
    }
    private static void advance(List<Block> stack, String word, String indent, int line){
        switch(word){
            case "if", "for", "while" -> stack.add(new Block(word, indent, line, false));
            case "function", "repeat" -> stack.add(new Block(word, indent, line, true));
            case "then" -> { if(!stack.isEmpty() && stack.getLast().kind.equals("if")) stack.getLast().body = true; }
            case "do" -> {
                if(!stack.isEmpty() && !stack.getLast().body && (stack.getLast().kind.equals("for") || stack.getLast().kind.equals("while"))) stack.getLast().body = true;
                else stack.add(new Block(word, indent, line, true));
            }
            case "end", "until" -> { if(closer(stack, word) != null) stack.removeLast(); }
            default -> {}
        }
    }
    private static int lineStart(String source, int cursor){ return source.lastIndexOf('\n', Math.max(-1, cursor - 1)) + 1; }
    private static String leading(String source, int start){
        int end = start; while(end < source.length() && (source.charAt(end) == ' ' || source.charAt(end) == '\t')) end++;
        return source.substring(start, end);
    }
    private static boolean first(String source, int line, int offset){ return line + leading(source, line).length() == offset; }
}
