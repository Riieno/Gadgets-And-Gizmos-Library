package com.rieno.gadgetsandgizmos.lib.script;

import java.util.Set;
import java.util.List;
import java.util.Map;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

// Build Lua port access without interpolating player names into executable identifiers
public final class LuaScripts{
    public static final int MAX_SOURCE_LENGTH = 32768;
    private static final Set<String> KEYWORDS = Set.of("and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");

    private LuaScripts(){}

    public static boolean keyword(String val){ return KEYWORDS.contains(val); }

    public static String access(String table, String port){
        return port.matches("[A-Za-z_][A-Za-z0-9_]*") && !keyword(port) ? table + "." + port
                : table + "[\"" + port.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"]";
    }

    public static String inputSnippet(String port){ return "local value = " + access("ports", port) + "\n"; }
    public static String outputSnippet(String port){ return access("output", port) + " = value\n"; }

    // Show snapshots as Lua literals rather than Java collection text
    public static String value(GraphValue val){ return literal(ScriptValues.encode(val), 0); }

    private static String literal(Object val, int depth){
        if(val instanceof String str){
            StringBuilder res = new StringBuilder("\"");
            for(char ch : str.toCharArray()){
                if(ch == '\\' || ch == '"') res.append('\\').append(ch);
                else if(ch < 32 || ch == 127) res.append(String.format(java.util.Locale.ROOT, "\\%03d", (int)ch));
                else res.append(ch);
            }
            return res.append('"').toString();
        }
        if(val instanceof List<?> list) return "{ " + String.join(", ", list.stream().map(entry -> literal(entry, depth + 1)).toList()) + " }";
        if(val instanceof Map<?, ?> map){
            if(map.isEmpty()) return "{}";
            String indent = "  ".repeat(depth + 1);
            return "{\n" + indent + String.join(",\n" + indent, map.entrySet().stream()
                    .map(entry -> "[" + literal(entry.getKey(), depth + 1) + "] = " + literal(entry.getValue(), depth + 1)).toList())
                    + "\n" + "  ".repeat(depth) + "}";
        }
        return String.valueOf(val);
    }

    // Keep exactly one generated line so runtime errors can report the player's source line
    public static String source(String code){ return "local ports, output = ...;\n" + code; }

    // Compile once while supplying an isolated environment and port tables on each pulse
    public static String function(String code){ return "return function(ports, output, _ENV)\n" + code + "\nend"; }

    // Preserve the previous Java API without enabling the removed Lua execution mode
    @Deprecated public static boolean passive(String source){ return false; }
}
