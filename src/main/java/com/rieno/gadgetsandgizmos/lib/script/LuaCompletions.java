package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

// Supply CC completion names without loading optional ComputerCraft classes on the client
public final class LuaCompletions{
    private static final List<String> STANDARD = read();
    private LuaCompletions(){}
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS                                                       
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static List<String> standard(){ return STANDARD; }

    public record Suggestions(int start, int end, List<String> values){}

    // Complete target strings and methods from literal wraps without executing player source
    public static Suggestions suggest(String source, int cursor, Collection<String> api, List<ScriptPeripheral> peripherals, boolean force){
        List<Token> tokens = tokens(source);
        for(Token token : tokens){
            if(cursor <= token.start() || cursor > token.end()) continue;
            String raw = token.text();
            boolean quoted = raw.startsWith("\"") || raw.startsWith("'");
            if(!quoted) continue;
            if(cursor == token.end() && raw.length() > 1 && raw.charAt(raw.length() - 1) == raw.charAt(0)) break;
            List<Token> prev = tokens.stream().filter(val -> val.end() <= token.start()).toList();
            List<String> names = List.of();
            if(ends(prev, "peripheral", ".", "wrap", "(") || ends(prev, "peripheral", ".", "call", "(")
                    || ends(prev, "peripheral", ".", "getType", "(") || ends(prev, "peripheral", ".", "getMethods", "(")
                    || ends(prev, "peripheral", ".", "isPresent", "(") || ends(prev, "peripheral", ".", "hasType", "(")
                    || ends(prev, "gng", ".", "peripheral", "(") || ends(prev, "acc", ".", "peripheral", "(")){
                names = peripherals.stream().flatMap(val -> java.util.stream.Stream.concat(java.util.stream.Stream.of(val.name()), val.aliases().stream())).toList();
            }else if(prev.size() >= 6 && ends(prev.subList(0, prev.size() - 2), "peripheral", ".", "call", "(")
                    && prev.getLast().text().equals(",")){
                String name = unquote(prev.get(prev.size() - 2).text());
                names = peripherals.stream().filter(val -> val.accepts(name)).flatMap(val -> val.methods().stream()).toList();
            }
            String prefix = unquote(raw.substring(0, cursor - token.start()) + raw.charAt(0));
            return new Suggestions(token.start(), token.end(), names.stream().filter(val -> val.startsWith(prefix)).distinct().sorted()
                    .map(val -> LuaScripts.value(GraphValue.string(val))).toList());
        }
        for(var span : LuaSyntax.spans(source)){
            if(span.start() < cursor && cursor <= span.end() && (span.kind() == LuaSyntax.Kind.COMMENT || span.kind() == LuaSyntax.Kind.STRING))
                return new Suggestions(cursor, cursor, List.of());
        }
        int start = cursor;
        while(start > 0 && (Character.isJavaIdentifierPart(source.charAt(start - 1)) || source.charAt(start - 1) == '.')) start--;
        String prefix = source.substring(start, cursor);
        List<String> options = new ArrayList<>(api);
        List<Token> before = tokens.stream().filter(val -> val.end() <= cursor).toList();
        Map<String, ScriptPeripheral> bindings = bindings(before, peripherals);
        int dot = prefix.indexOf('.');
        if(dot > 0 && bindings.containsKey(prefix.substring(0, dot))){
            String variable = prefix.substring(0, dot);
            String method = prefix.substring(dot + 1).toLowerCase(Locale.ROOT);
            options = bindings.get(variable).methods().stream().filter(val -> val.toLowerCase(Locale.ROOT).contains(method))
                    .map(val -> LuaScripts.access(variable, val) + "(").toList();
        }else options = options.stream().filter(val -> val.startsWith(prefix) && !val.equals(prefix)).toList();
        return new Suggestions(start, cursor, !force && prefix.isEmpty() ? List.of() : options.stream().distinct().sorted().limit(100).toList());
    }

    // Create a collision-free local name while preserving the peripheral's exact quoted alias
    public static String wrapSnippet(ScriptPeripheral peripheral, String source){
        String base = (peripheral.aliases().isEmpty() ? peripheral.type() : peripheral.preferredName()).replaceAll("[^A-Za-z0-9_]", "_");
        if(!localIdentifier(base)) base = peripheral.type().replaceAll("[^A-Za-z0-9_]", "_");
        if(!localIdentifier(base)) base = "device";
        String name = base;
        for(int idx = 2; java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(name) + "\\b").matcher(source).find(); idx++) name = base + idx;
        return "local " + name + " = peripheral.wrap(" + LuaScripts.value(GraphValue.string(peripheral.preferredName())) + ")\n";
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS                                                        
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private record Token(String text, int start, int end){}

    private static boolean localIdentifier(String name){
        return name.matches("[A-Za-z_][A-Za-z0-9_]*") && !LuaScripts.keyword(name)
                && !java.util.Set.of("ports", "output", "node", "execEvent", "gng", "acc", "_G", "_ENV").contains(name)
                && STANDARD.stream().noneMatch(val -> val.startsWith(name + ".") || val.equals(name + "("));
    }

    // Retain local shadows and function parameters while inspecting the cursor's lexical blocks
    private static Map<String, ScriptPeripheral> bindings(List<Token> tokens, List<ScriptPeripheral> peripherals){
        List<Map<String, ScriptPeripheral>> scopes = new ArrayList<>(); scopes.add(new LinkedHashMap<>());
        for(int idx = 0; idx < tokens.size(); idx++){
            String word = tokens.get(idx).text();
            if(List.of("end", "until", "else", "elseif").contains(word) && scopes.size() > 1) scopes.removeLast();
            if(List.of("function", "then", "do", "repeat", "else").contains(word)){
                scopes.add(new LinkedHashMap<>());
                if(word.equals("function")){
                    int arg = idx + 1;
                    while(arg < tokens.size() && !tokens.get(arg).text().equals("(")) arg++;
                    while(++arg < tokens.size() && !tokens.get(arg).text().equals(")")){
                        String name = tokens.get(arg).text();
                        if(name.matches("[A-Za-z_][A-Za-z0-9_]*")) scopes.getLast().put(name, null);
                    }
                }
            }
            if(!word.matches("[A-Za-z_][A-Za-z0-9_]*") || LuaScripts.keyword(word)) continue;
            boolean local = idx > 0 && tokens.get(idx - 1).text().equals("local");
            if(idx + 2 >= tokens.size() || !tokens.get(idx + 1).text().equals("=")){
                if(local) scopes.getLast().put(word, null);
                continue;
            }
            if(tokens.get(idx + 2).text().equals("=") || idx > 0 && List.of(".", "[").contains(tokens.get(idx - 1).text())) continue;
            ScriptPeripheral bound;
            if(idx + 7 < tokens.size() && sequence(tokens, idx + 2, "peripheral", ".", "wrap", "(") && tokens.get(idx + 7).text().equals(")")){
                String name = unquote(tokens.get(idx + 6).text());
                bound = peripherals.stream().filter(val -> val.accepts(name)).findFirst().orElse(null);
            }else bound = idx + 3 < tokens.size() && List.of(".", "(", "[").contains(tokens.get(idx + 3).text())
                    ? null : scope(scopes, tokens.get(idx + 2).text()).get(tokens.get(idx + 2).text());
            (local ? scopes.getLast() : scope(scopes, word)).put(word, bound);
        }
        Map<String, ScriptPeripheral> res = new LinkedHashMap<>(); scopes.forEach(res::putAll);
        res.values().removeIf(java.util.Objects::isNull); return res;
    }

    private static Map<String, ScriptPeripheral> scope(List<Map<String, ScriptPeripheral>> scopes, String name){
        for(int idx = scopes.size() - 1; idx >= 0; idx--) if(scopes.get(idx).containsKey(name)) return scopes.get(idx);
        return scopes.getFirst();
    }

    private static List<Token> tokens(String source){
        return LuaSyntax.spans(source).stream().filter(span -> span.kind() != LuaSyntax.Kind.COMMENT && !source.substring(span.start(), span.end()).isBlank())
                .map(span -> new Token(source.substring(span.start(), span.end()), span.start(), span.end())).toList();
    }
    private static boolean ends(List<Token> tokens, String... vals){ return tokens.size() >= vals.length && sequence(tokens, tokens.size() - vals.length, vals); }
    private static boolean sequence(List<Token> tokens, int start, String... vals){
        if(start + vals.length > tokens.size()) return false;
        for(int idx = 0; idx < vals.length; idx++) if(!tokens.get(start + idx).text().equals(vals[idx])) return false;
        return true;
    }
    private static String unquote(String val){
        if(val.length() < 2 || val.charAt(val.length() - 1) != val.charAt(0) || val.charAt(0) != '\'' && val.charAt(0) != '"') return "";
        StringBuilder res = new StringBuilder();
        for(int idx = 1; idx < val.length() - 1; idx++){
            char ch = val.charAt(idx);
            if(ch == '\\' && idx < val.length() - 2){
                ch = val.charAt(++idx);
                if(Character.isDigit(ch)){
                    int end = idx + 1; while(end < Math.min(idx + 3, val.length() - 1) && Character.isDigit(val.charAt(end))) end++;
                    res.append((char)Integer.parseInt(val.substring(idx, end))); idx = end - 1; continue;
                }
                ch = switch(ch){ case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> ch; };
            }
            res.append(ch);
        }
        return res.toString();
    }

    private static List<String> read(){
        try(InputStream stream = LuaCompletions.class.getResourceAsStream("completions.txt")){
            return stream == null ? List.of() : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().filter(val -> !val.isBlank()).toList();
        }catch(IOException ex){ return List.of(); }
    }
}
