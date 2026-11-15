package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;

// Identify declaration expressions without treating strings or comments as executable code
public final class ScriptPortDeclarations{
    private ScriptPortDeclarations(){}
    public record Declaration(String key, String name, boolean output, boolean execution, int firstLine, int lastLine){}

    public static List<Declaration> read(String source){
        var tokens = LuaSyntax.spans(source).stream().filter(span -> span.kind() != LuaSyntax.Kind.COMMENT
                && !source.substring(span.start(), span.end()).isBlank()).toList();
        List<Declaration> res = new ArrayList<>();
        for(int idx = 0; idx + 4 < tokens.size(); idx++){
            var start = tokens.get(idx);
            if(!text(source, start).equals("node") || !text(source, tokens.get(idx + 1)).equals(".")) continue;
            String method = text(source, tokens.get(idx + 2));
            if(!List.of("addInputPort", "addOutputPort", "addExecIn", "addExecOut").contains(method)
                    || !text(source, tokens.get(idx + 3)).equals("(")) continue;
            int end = idx + 4, depth = 1;
            for(; end < tokens.size(); end++){
                String token = text(source, tokens.get(end));
                if(token.equals("(")) depth++;
                if(token.equals(")") && --depth == 0) break;
            }
            if(end >= tokens.size()) continue;
            StringBuilder key = new StringBuilder(method);
            for(int arg = idx + 4; arg < end; arg++) key.append('\u0000').append(text(source, tokens.get(arg)));
            boolean output = method.equals("addOutputPort") || method.equals("addExecOut");
            boolean execution = method.startsWith("addExec");
            String name = end == idx + 5 && tokens.get(idx + 4).kind() == LuaSyntax.Kind.STRING
                    ? ScriptExecPorts.decode(text(source, tokens.get(idx + 4))) : "";
            res.add(new Declaration(key.toString(), name, output, execution,
                    1 + (int)source.substring(0, start.start()).chars().filter(ch -> ch == '\n').count(),
                    1 + (int)source.substring(0, tokens.get(end).end()).chars().filter(ch -> ch == '\n').count()));
        }
        return List.copyOf(res);
    }

    public static String at(String source, int line, boolean output, boolean execution, String name){
        return read(source).stream().filter(val -> val.output() == output && val.execution() == execution
                && (val.name().equals(name) || val.firstLine() <= line && val.lastLine() >= line)).map(Declaration::key).findFirst().orElse("");
    }
    private static String text(String source, LuaSyntax.Span span){ return source.substring(span.start(), span.end()); }
}
