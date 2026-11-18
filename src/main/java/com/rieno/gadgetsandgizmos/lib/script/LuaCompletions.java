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

    // Complete script declarations, API names and literal peripheral wraps without executing player source
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
        while(start > 0 && (Character.isJavaIdentifierPart(source.charAt(start - 1)) || source.charAt(start - 1) == '.' || source.charAt(start - 1) == ':')) start--;
        String prefix = source.substring(start, cursor);
        int boundary = start;
        Map<String, Binding> symbols = symbols(source, tokens.stream().filter(val -> val.end() <= boundary).toList(), start, peripherals);
        List<String> options = new ArrayList<>(api);
        for(var entry : symbols.entrySet()) options.add(entry.getKey() + (entry.getValue().function() ? "(" : ""));
        int dot = prefix.indexOf('.');
        if(dot > 0 && symbols.containsKey(prefix.substring(0, dot)) && symbols.get(prefix.substring(0, dot)).peripheral() != null){
            String variable = prefix.substring(0, dot);
            String method = prefix.substring(dot + 1).toLowerCase(Locale.ROOT);
            options = symbols.get(variable).peripheral().methods().stream().filter(val -> val.toLowerCase(Locale.ROOT).contains(method))
                    .map(val -> LuaScripts.access(variable, val) + "(").toList();
        }else options = options.stream().filter(val -> val.startsWith(prefix) && !val.equals(prefix)).toList();
        int end = cursor;
        while(end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
        return new Suggestions(start, end, !force && prefix.isEmpty() ? List.of() : options.stream().distinct().sorted().limit(100).toList());
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
                && !java.util.Set.of("inputs", "outputs", "ports", "output", "node", "execEvent", "gng", "acc", "_G", "_ENV").contains(name)
                && STANDARD.stream().noneMatch(val -> val.startsWith(name + ".") || val.equals(name + "("));
    }

    private record Binding(ScriptPeripheral peripheral, boolean function){
        private static final Binding VARIABLE = new Binding(null, false), FUNCTION = new Binding(null, true);
    }
    private static final class Scope{
        private String kind;
        private final int braces, depth;
        private final Map<String, Binding> names = new LinkedHashMap<>();
        private final List<String> loopNames = new ArrayList<>();
        private Scope(String kind, int braces, int depth){ this.kind = kind; this.braces = braces; this.depth = depth; }
    }

    // Read declarations from incomplete source without evaluating player code or leaking closed local scopes
    private static Map<String, Binding> symbols(String source, List<Token> tokens, int cursor, List<ScriptPeripheral> peripherals){
        List<Scope> scopes = new ArrayList<>(); scopes.add(new Scope("chunk", 0, 0));
        int braces = 0, depth = 0, previous = 0;
        String last = "";
        for(int idx = 0; idx < tokens.size(); idx++){
            Token token = tokens.get(idx); String word = token.text();
            closeRepeat(scopes, source.substring(previous, token.start()), last, word, depth);
            previous = token.end(); last = word;
            if(word.equals("{")) braces++;
            if(word.equals("}")) braces--;
            if(List.of("(", "[", "{").contains(word)) depth++;
            if(List.of(")", "]", "}").contains(word)) depth--;
            Scope current = scopes.getLast();
            if(word.equals("end")){ if(scopes.size() > 1) scopes.removeLast(); continue; }
            if(word.equals("until")){ current.kind = "until"; continue; }
            if(word.equals("else") || word.equals("elseif")){
                if(scopes.size() > 1 && current.kind.equals("then")) scopes.removeLast();
                if(word.equals("else")) scopes.add(new Scope("then", braces, depth));
                continue;
            }
            if(word.equals("then") || word.equals("repeat")){ scopes.add(new Scope(word, braces, depth)); continue; }
            if(word.equals("while")){ scopes.add(new Scope("while", braces, depth)); continue; }
            if(word.equals("for")){
                Scope loop = new Scope("for", braces, depth); scopes.add(loop);
                int name = idx + 1;
                while(name < tokens.size() && identifier(tokens.get(name).text())){
                    loop.loopNames.add(tokens.get(name).text());
                    if(name + 1 >= tokens.size() || !tokens.get(name + 1).text().equals(",")) break;
                    name += 2;
                }
                idx = Math.min(name, tokens.size() - 1); continue;
            }
            if(word.equals("do")){
                if(current.kind.equals("for") || current.kind.equals("while")){
                    current.kind = "do"; current.loopNames.forEach(name -> current.names.put(name, Binding.VARIABLE));
                }else scopes.add(new Scope("do", braces, depth));
                continue;
            }
            if(word.equals("function")){
                int arg = idx + 1; String name = "";
                if(arg < tokens.size() && identifier(tokens.get(arg).text())){
                    name = tokens.get(arg++).text();
                    while(arg + 1 < tokens.size() && List.of(".", ":").contains(tokens.get(arg).text()) && identifier(tokens.get(arg + 1).text())){
                        name += tokens.get(arg).text() + tokens.get(arg + 1).text(); arg += 2;
                    }
                    boolean local = idx > 0 && tokens.get(idx - 1).text().equals("local");
                    define(local ? current : scope(scopes, name), name, Binding.FUNCTION);
                }
                Scope function = new Scope("function", braces, depth); scopes.add(function);
                if(name.contains(":")) function.names.put("self", Binding.VARIABLE);
                if(arg < tokens.size() && tokens.get(arg).text().equals("(")){
                    while(++arg < tokens.size() && !tokens.get(arg).text().equals(")")){
                        String parameter = tokens.get(arg).text();
                        if(identifier(parameter)) function.names.put(parameter, Binding.VARIABLE);
                    }
                    idx = Math.min(arg, tokens.size() - 1);
                }
                continue;
            }
            boolean local = word.equals("local");
            if(local && idx + 1 < tokens.size() && tokens.get(idx + 1).text().equals("function")) continue;
            if(!local && (!identifier(word) || braces > current.braces || idx > 0 && List.of(".", ":").contains(tokens.get(idx - 1).text()))) continue;
            int pos = local ? idx + 1 : idx;
            List<String> names = new ArrayList<>();
            while(pos < tokens.size() && identifier(tokens.get(pos).text())){
                String name = tokens.get(pos++).text();
                while(pos + 1 < tokens.size() && List.of(".", ":").contains(tokens.get(pos).text()) && identifier(tokens.get(pos + 1).text())){
                    name += tokens.get(pos).text() + tokens.get(pos + 1).text(); pos += 2;
                }
                names.add(name);
                if(pos >= tokens.size() || !tokens.get(pos).text().equals(",")) break;
                pos++;
            }
            boolean assignment = pos < tokens.size() && tokens.get(pos).text().equals("=")
                    && (pos + 1 >= tokens.size() || !tokens.get(pos + 1).text().equals("="));
            if(!local && !assignment || names.isEmpty()) continue;
            List<Binding> bindings = new ArrayList<>(); int value = pos + 1;
            for(int name = 0; name < names.size(); name++){
                bindings.add(assignment ? binding(tokens, value, scopes, peripherals) : Binding.VARIABLE);
                if(name + 1 < names.size()) value = nextValue(source, tokens, value);
            }
            for(int name = 0; name < names.size(); name++) define(local ? current : scope(scopes, names.get(name)), names.get(name), bindings.get(name));
            idx = Math.max(idx, pos - 1);
        }
        closeRepeat(scopes, source.substring(Math.min(previous, cursor), cursor), last, "", depth);
        Map<String, Binding> res = new LinkedHashMap<>();
        for(Scope scope : scopes) scope.names.forEach((name, binding) -> {
            if(scope != scopes.getFirst()) res.keySet().removeIf(val -> val.startsWith(name + ".") || val.startsWith(name + ":"));
            res.put(name, binding);
        });
        return res;
    }

    private static Binding binding(List<Token> tokens, int idx, List<Scope> scopes, List<ScriptPeripheral> peripherals){
        if(idx >= tokens.size()) return Binding.VARIABLE;
        String word = tokens.get(idx).text();
        if(word.equals("function")) return Binding.FUNCTION;
        if(sequence(tokens, idx, "peripheral", ".", "wrap", "(") && idx + 5 < tokens.size() && tokens.get(idx + 5).text().equals(")")){
            String name = unquote(tokens.get(idx + 4).text());
            return new Binding(peripherals.stream().filter(val -> val.accepts(name)).findFirst().orElse(null), false);
        }
        if(!identifier(word)) return Binding.VARIABLE;
        int pos = idx + 1;
        while(pos + 1 < tokens.size() && List.of(".", ":").contains(tokens.get(pos).text()) && identifier(tokens.get(pos + 1).text())){
            word += tokens.get(pos).text() + tokens.get(pos + 1).text(); pos += 2;
        }
        if(pos < tokens.size() && List.of("(", "[", "{", ".", ":").contains(tokens.get(pos).text())) return Binding.VARIABLE;
        return scope(scopes, word).names.getOrDefault(word, Binding.VARIABLE);
    }
    private static Scope scope(List<Scope> scopes, String name){
        String root = name.split("[.:]", 2)[0];
        for(int idx = scopes.size() - 1; idx >= 0; idx--) if(scopes.get(idx).names.containsKey(root)) return scopes.get(idx);
        return scopes.getFirst();
    }
    private static void define(Scope scope, String name, Binding binding){
        if(scope.names.containsKey(name)) scope.names.keySet().removeIf(val -> val.startsWith(name + ".") || val.startsWith(name + ":"));
        scope.names.put(name, binding);
    }
    private static boolean identifier(String text){
        if(text.isEmpty() || !(text.charAt(0) == '_' || text.charAt(0) >= 'A' && text.charAt(0) <= 'Z' || text.charAt(0) >= 'a' && text.charAt(0) <= 'z')) return false;
        for(int idx = 1; idx < text.length(); idx++){
            char ch = text.charAt(idx);
            if(ch != '_' && !(ch >= 'A' && ch <= 'Z') && !(ch >= 'a' && ch <= 'z') && !(ch >= '0' && ch <= '9')) return false;
        }
        return !LuaScripts.keyword(text);
    }
    private static int nextValue(String source, List<Token> tokens, int start){
        int depth = 0, blocks = 0;
        for(int idx = start; idx < tokens.size(); idx++){
            String word = tokens.get(idx).text();
            if(idx > start && depth == 0 && blocks == 0 && source.substring(tokens.get(idx - 1).end(), tokens.get(idx).start()).contains("\n")
                    && !continuation(tokens.get(idx - 1).text()) && !List.of("and", "or", "+", "-", "*", "/", "%", "^", ".").contains(word)) break;
            if(List.of("(", "[", "{").contains(word)) depth++;
            if(List.of(")", "]", "}").contains(word)) depth--;
            if(List.of("function", "then", "do", "repeat").contains(word)) blocks++;
            if(List.of("end", "until").contains(word)) blocks--;
            if(depth == 0 && blocks == 0 && word.equals(",")) return idx + 1;
            if(depth < 0 || blocks < 0 || word.equals(";")) break;
        }
        return tokens.size();
    }
    private static void closeRepeat(List<Scope> scopes, String gap, String previous, String next, int depth){
        if(scopes.size() <= 1 || !scopes.getLast().kind.equals("until") || depth != scopes.getLast().depth) return;
        if(List.of(";", "end", "else", "elseif").contains(next)){ scopes.removeLast(); return; }
        if(!gap.contains("\n") || continuation(previous)
                || List.of("and", "or", "+", "-", "*", "/", "%", "^", ".", ";").contains(next)) return;
        scopes.removeLast();
    }
    private static boolean continuation(String word){ return List.of("until", "and", "or", "not", "=", "<", ">", "~", "+", "-", "*", "/", "%", "^", ".", ",", "(", "[", "{").contains(word); }

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
