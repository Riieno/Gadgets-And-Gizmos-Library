package com.rieno.gadgetsandgizmos.lib.script;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Discover literal declarations without executing code or mistaking comments and strings for calls
public final class ScriptExecPorts{
    private static final Pattern CALL = Pattern.compile("node\\s*\\.\\s*addExec(In|Out)\\s*\\(\\s*");
    private static final java.util.Map<String, Ports> CACHE = new java.util.LinkedHashMap<>(64, .75f, true);
    private ScriptExecPorts(){}

    public record Ports(List<String> inputs, List<String> outputs){
        public Ports{ inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); }
    }

    public static Ports read(String source){
        synchronized(CACHE){
            Ports cached = CACHE.get(source);
            if(cached != null) return cached;
        }
        Ports ports = parse(source);
        synchronized(CACHE){
            CACHE.put(source, ports);
            while(CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return ports;
    }

    private static Ports parse(String source){
        if(!source.contains("addExec")) return new Ports(List.of(), List.of());
        var spans = LuaSyntax.spans(source);
        var inputs = new LinkedHashSet<String>();
        var outputs = new LinkedHashSet<String>();
        var matcher = CALL.matcher(source);
        for(int offset = 0; matcher.find(offset); offset = matcher.end()){
            int start = matcher.start(), arg = matcher.end();
            if(spans.stream().anyMatch(span -> span.start() <= start && span.end() > start
                    && (span.kind() == LuaSyntax.Kind.COMMENT || span.kind() == LuaSyntax.Kind.STRING))) continue;
            var literal = spans.stream().filter(span -> span.start() == arg && span.kind() == LuaSyntax.Kind.STRING).findFirst().orElse(null);
            if(literal == null) continue;
            int end = literal.end();
            while(end < source.length() && Character.isWhitespace(source.charAt(end))) end++;
            if(end >= source.length() || source.charAt(end) != ')') continue;
            String name = decode(source.substring(arg, literal.end()));
            ("In".equals(matcher.group(1)) ? inputs : outputs).add(name);
        }
        return new Ports(new ArrayList<>(inputs), new ArrayList<>(outputs));
    }

    public static String validate(Ports ports, Set<String> inputs, Set<String> outputs){
        for(boolean output : List.of(false, true)){
            for(String name : output ? ports.outputs() : ports.inputs()){
                if(name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) return "Invalid execution port name";
                if((output ? Set.of("valid", "error", "busy", com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode.INSTANCE) : Set.of("name")).contains(name)
                        || (output ? outputs : inputs).contains(name)) return "Execution port '" + name + "' conflicts with another port";
            }
        }
        return "";
    }

    static String decode(String literal){
        if(literal.startsWith("[")){
            int start = literal.indexOf('[', 1), tail = start + 1;
            String text = literal.substring(tail, Math.max(tail, literal.length() - tail)).replace("\r\n", "\n");
            return text.startsWith("\n") ? text.substring(1) : text;
        }
        StringBuilder res = new StringBuilder();
        for(int idx = 1; idx < literal.length() - 1; idx++){
            char ch = literal.charAt(idx);
            if(ch != '\\'){ res.append(ch); continue; }
            if(++idx >= literal.length() - 1) break;
            ch = literal.charAt(idx);
            if(Character.isDigit(ch)){
                int value = ch - '0', count = 1;
                while(count++ < 3 && idx + 1 < literal.length() - 1 && Character.isDigit(literal.charAt(idx + 1))) value = value * 10 + literal.charAt(++idx) - '0';
                res.append((char)value);
            }else if(ch == 'x' && idx + 2 < literal.length() - 1){
                try{ res.append((char)Integer.parseInt(literal.substring(idx + 1, idx + 3), 16)); idx += 2; }
                catch(NumberFormatException ignored){ res.append('x'); }
            }else if(ch == 'z'){
                while(idx + 1 < literal.length() - 1 && Character.isWhitespace(literal.charAt(idx + 1))) idx++;
            }else res.append((char)switch(ch){ case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'a' -> 7; case 'b' -> '\b'; case 'f' -> '\f'; case 'v' -> 11; default -> ch; });
        }
        return res.toString();
    }
}
