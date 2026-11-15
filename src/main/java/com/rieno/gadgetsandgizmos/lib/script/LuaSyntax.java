package com.rieno.gadgetsandgizmos.lib.script;

import java.util.ArrayList;
import java.util.List;

// Tokenize Lua strings and comments across lines before coloring identifiers
public final class LuaSyntax{
    public enum Kind{ TEXT, KEYWORD, STRING, COMMENT, NUMBER }
    public record Span(int start, int end, Kind kind){}

    private LuaSyntax(){}

    public static List<Span> spans(String src){
        List<Span> res = new ArrayList<>();
        int idx = 0;
        while(idx < src.length()){
            int start = idx;
            char ch = src.charAt(idx);
            Kind kind = Kind.TEXT;
            if(src.startsWith("--", idx)){
                kind = Kind.COMMENT;
                idx += 2;
                int end = longEnd(src, idx);
                if(end >= 0) idx = end;
                else while(idx < src.length() && src.charAt(idx) != '\n') idx++;
            }else if(ch == '\'' || ch == '"'){
                kind = Kind.STRING;
                idx++;
                while(idx < src.length()){
                    char next = src.charAt(idx++);
                    if(next == '\\' && idx < src.length()) idx++;
                    else if(next == ch) break;
                }
            }else if(ch == '[' && longEnd(src, idx) >= 0){
                kind = Kind.STRING;
                idx = longEnd(src, idx);
            }else if(Character.isLetter(ch) || ch == '_'){
                idx++;
                while(idx < src.length() && (Character.isLetterOrDigit(src.charAt(idx)) || src.charAt(idx) == '_')) idx++;
                if(LuaScripts.keyword(src.substring(start, idx))) kind = Kind.KEYWORD;
            }else if(Character.isDigit(ch)){
                kind = Kind.NUMBER;
                idx++;
                while(idx < src.length()){
                    char next = src.charAt(idx);
                    if(Character.isLetterOrDigit(next) || next == '.' && !src.startsWith("..", idx)) idx++;
                    else if((next == '+' || next == '-') && "eEpP".indexOf(src.charAt(idx - 1)) >= 0) idx++;
                    else break;
                }
            }else idx++;
            res.add(new Span(start, idx, kind));
        }
        return List.copyOf(res);
    }

    private static int longEnd(String src, int start){
        if(start >= src.length() || src.charAt(start) != '[') return -1;
        int idx = start + 1;
        while(idx < src.length() && src.charAt(idx) == '=') idx++;
        if(idx >= src.length() || src.charAt(idx) != '[') return -1;
        String close = "]" + src.substring(start + 1, idx) + "]";
        int end = src.indexOf(close, idx + 1);
        return end < 0 ? src.length() : end + close.length();
    }
}
