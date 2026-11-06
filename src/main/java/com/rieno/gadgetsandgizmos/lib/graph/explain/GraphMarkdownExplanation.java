package com.rieno.gadgetsandgizmos.lib.graph.explain;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning.Operation.*;
import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSelectionAnalysis.*;

// Explain meaningful stages in signal order using short Markdown bullets and immediate input names
final class GraphMarkdownExplanation{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_STEPS = 96;
    private static final int MAX_TEXT = 48000;
    private final GraphExplanation.TextResolver text;
    private final Map<String, Step> byId = new LinkedHashMap<>();
    private final Set<String> consumed = new HashSet<>();
    private final Map<String, Set<String>> usedPorts = new HashMap<>();

    GraphMarkdownExplanation(GraphExplanation.TextResolver text){ this.text = text; }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    String explain(GraphSelectionAnalysis analysis){
        prepare(analysis);
        List<String> bullets = new ArrayList<>();
        int count = 0;
        for(Step step : ordered()){
            if(hidden(step)) continue;
            if(count++ >= MAX_STEPS) break;
            bullets.add("- " + stage(step));
            String body = step.meaning().settings().get("functionMarkdown");
            if(body != null && !body.isBlank()) bullets.add(body.lines().map(line -> "  " + line).collect(java.util.stream.Collectors.joining("\n")));
            if((step.meaning().operation() == ACTION || step.meaning().operation() == VARIABLE_WRITE
                    || step.meaning().operation() == FUNCTION) && !step.paths().isEmpty()){
                if(step.paths().stream().noneMatch(ExecutionPath::connected))
                    bullets.add("- " + t("no_trigger", "%s has no complete trigger connected within this selection.", name(step)));
                else{
                    List<String> routes = step.paths().stream().filter(ExecutionPath::connected).filter(path -> !path.conditions().isEmpty())
                            .map(path -> String.join(t("and", " and "), path.conditions().stream().map(this::condition).toList())).distinct().toList();
                    if(!routes.isEmpty()) bullets.add("- " + t("only_when", "%s only runs when %s.", name(step), String.join(t("or", " or "), routes)));
                }
            }
        }
        if(analysis.selectedCount() == 0) bullets.add("- " + t("empty", "Select some nodes to explain."));
        if(analysis.boundaries().stream().anyMatch(Boundary::entering))
            bullets.add("- " + t("outside_inputs", "Some inputs or triggers come from nodes outside this selection."));
        if(analysis.boundaries().stream().anyMatch(boundary -> !boundary.entering()))
            bullets.add("- " + t("outside_outputs", "Some outputs continue to nodes outside this selection."));
        Set<String> codes = new HashSet<>();
        analysis.notices().forEach(notice -> codes.add(notice.code()));
        if(codes.contains("cycle")) bullets.add("- " + t("cycle", "Some connections loop back on themselves. Their final result cannot be determined here."));
        if(codes.contains("reference")) bullets.add("- " + t("reference", "Some variables, functions or named events could not be found in the saved graph."));
        if(codes.contains("unsupported")) bullets.add("- " + t("unsupported", "Some node types are unfamiliar, so their behaviour cannot be fully explained."));
        if(codes.contains("port") || codes.contains("topology") || codes.contains("ambiguous"))
            bullets.add("- " + t("invalid", "Some connections are missing or conflict with each other."));
        if(count > MAX_STEPS || codes.contains("limit")) bullets.add("- " + t("limit", "This selection is too large or complex to explain completely."));
        String res = String.join("\n\n", bullets);
        if(res.length() <= MAX_TEXT) return res;
        int end = res.lastIndexOf("\n\n", MAX_TEXT);
        return res.substring(0, Math.max(0, end)) + "\n\n- " + t("limit", "This selection is too large or complex to explain completely.");
    }

    // Reuse the same ordered stage descriptions without parsing rendered bullet text
    List<GraphExplanation.NodeStep> steps(GraphSelectionAnalysis analysis){
        prepare(analysis);
        return ordered().stream().filter(step -> !hidden(step)).limit(MAX_STEPS)
                .map(step -> new GraphExplanation.NodeStep(step.nodeId(), stage(step))).toList();
    }

    // Index the selection once for either the modal or its graph annotations
    private void prepare(GraphSelectionAnalysis analysis){
        byId.clear();
        consumed.clear();
        usedPorts.clear();
        analysis.steps().stream().sorted(java.util.Comparator.comparing(Step::nodeId)).forEach(step -> byId.put(step.nodeId(), step));
        byId.values().forEach(step -> consumed.addAll(step.predecessors()));
        byId.values().forEach(step -> step.inputs().values().stream().filter(val -> !val.external() && !val.nodeId().isBlank())
                .forEach(val -> usedPorts.computeIfAbsent(val.nodeId(), ignored -> new HashSet<>()).add(val.port())));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private List<Step> ordered(){
        Map<String, Integer> counts = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for(Step step : byId.values()){
            List<String> prev = step.predecessors().stream().filter(byId::containsKey).distinct().toList();
            counts.put(step.nodeId(), prev.size());
            for(String id : prev) next.computeIfAbsent(id, ignored -> new ArrayList<>()).add(step.nodeId());
        }
        PriorityQueue<String> pending = new PriorityQueue<>();
        counts.forEach((id, count) -> { if(count == 0) pending.add(id); });
        List<Step> res = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while(!pending.isEmpty()){
            String id = pending.remove();
            if(!visited.add(id)) continue;
            res.add(byId.get(id));
            for(String child : next.getOrDefault(id, List.of())){
                int remaining = counts.computeIfPresent(child, (key, count) -> count - 1);
                if(remaining == 0) pending.add(child);
            }
        }
        byId.forEach((id, step) -> { if(visited.add(id)) res.add(step); });
        return res;
    }

    private boolean hidden(Step step){
        if(step.meaning().operation() != LITERAL && step.meaning().operation() != ROUTE) return false;
        return consumed.contains(step.nodeId());
    }

    private String stage(Step step){
        GraphNodeMeaning node = step.meaning();
        String input = value(step.inputs().get("value"));
        return switch(node.operation()){
            case EVENT -> event(step);
            case MAP_RANGE -> known(step, "in_min", "in_max", "out_min", "out_max")
                    ? t("range", "%s sends its value through %s, changing the range from %s–%s to %s–%s.",
                            subject(step.inputs().get("value")), name(step),
                            input(step, "in_min"), input(step, "in_max"), input(step, "out_min"), input(step, "out_max"))
                    : t("range_connected", "%s changes %s from its input range to its output range. Some range limits come from other inputs or are missing.", name(step), input);
            case RAMP -> ramp(step);
            case ROUND -> t("round", "%s rounds %s to the nearest whole number.", name(step), input);
            case FLOOR -> t("floor", "%s rounds %s down to a whole number.", name(step), input);
            case CEIL -> t("ceil", "%s rounds %s up to a whole number.", name(step), input);
            case SMOOTHING -> t("smooth", "%s smooths changes in %s so the output changes more gradually.", name(step), input);
            case VARIABLE_WRITE -> t("store", "%s is stored in the variable %s.",
                    capital(input), bold(node.settings().getOrDefault("variable", node.label())));
            case VARIABLE_READ -> t("read_variable", "%s reads the current value of the variable %s.",
                    name(step), bold(node.settings().getOrDefault("variable", node.label())));
            case ADD -> t("add", "%s adds %s and %s.", name(step), input(step, "a"), input(step, "b"));
            case SUBTRACT -> t("subtract", "%s subtracts %s from %s.", name(step), input(step, "b"), input(step, "a"));
            case MULTIPLY -> t("multiply", "%s multiplies %s by %s.", name(step), input(step, "a"), input(step, "b"));
            case DIVIDE -> t("divide", "%s divides %s by %s.", name(step), input(step, "a"), input(step, "b"));
            case COMPARE -> t("compare", "%s checks whether %s.", name(step), comparison(step.inputs()));
            case AND -> t("and_check", "%s checks that %s and %s are both true.", name(step), input(step, "a"), input(step, "b"));
            case OR -> t("or_check", "%s checks whether either %s or %s is true.", name(step), input(step, "a"), input(step, "b"));
            case NOT -> t("invert", "%s reverses the true/false value of %s.", name(step), input);
            case CLAMP -> t("clamp", "%s keeps %s between %s and %s.", name(step), input, input(step, "min"), input(step, "max"));
            case SELECT -> t("select", "%s chooses %s when %s is true, otherwise %s.", name(step),
                    input(step, "true"), input(step, "condition"), input(step, "false"));
            case CONTROL -> t("control", "%s compares %s with the target %s and calculates a correction.",
                    name(step), input(step, "actual"), input(step, "target"));
            case BRANCH -> t("branch", "%s follows its true or false output depending on %s.", name(step),
                    input(step, step.inputs().containsKey("condition") ? "condition" : "open"));
            case SWITCH -> t("switch", "%s chooses an output using %s.", name(step), input(step, "selector"));
            case LOOP -> t("loop", "%s repeats its body up to %s times, then continues through its completion output.",
                    name(step), input(step, "iterations"));
            case FLOW -> flow(step);
            case ACTION -> action(step);
            case FUNCTION -> t("function", "%s calls the function %s.", name(step), bold(node.settings().getOrDefault("function", node.label())));
            case SOURCE -> source(step);
            case LITERAL -> t("literal", "%s supplies the value %s.", name(step), escape(node.settings().getOrDefault("value", "")));
            case ROUTE -> t("route", "%s passes %s onwards.", name(step), input);
            case UNKNOWN -> t("unknown", "%s is an unfamiliar node. Its behaviour cannot be determined from its connections alone.", name(step));
            case GENERIC -> generic(step);
        };
    }

    private String event(Step step){
        GraphNodeMeaning node = step.meaning();
        if("binding".equals(node.settings().get("inputKind"))){
            String binding = node.settings().getOrDefault("binding", "");
            String input = binding.isBlank() ? t("its_input", "its input") : bold(binding);
            return switch(node.settings().getOrDefault("pulse", "both")){
                case "rising" -> t("input_rising", "%s sends a pulse when %s becomes active.", name(step), input);
                case "falling" -> t("input_falling", "%s sends a pulse when %s becomes inactive.", name(step), input);
                default -> t("input_both", "%s sends a pulse when %s becomes active and again when it becomes inactive.", name(step), input);
            };
        }
        if("key".equals(node.settings().get("inputKind"))){
            String key = bold("[" + node.settings().getOrDefault("binding", "?") + "]");
            String res = switch(node.settings().getOrDefault("pulse", "both")){
                case "rising" -> t("key_pressed", "%s sends a pulse when %s is pressed.", name(step), key);
                case "falling" -> t("key_released", "%s sends a pulse when %s is released.", name(step), key);
                default -> t("key_both", "%s sends a pulse when %s is pressed and again when it is released.", name(step), key);
            };
            return res + " " + t("key_value", "Its value is 1 while the key is held and 0 when released.");
        }
        if("compare".equals(node.settings().get("predicate"))){
            Map<String, Expression> args = new HashMap<>(step.inputs());
            args.put("operator", new Expression("", "operator", LITERAL,
                    node.literals().getOrDefault("operator", ""), Map.of(), false));
            return t("event_comparison", "%s sends a pulse on each update when %s.", name(step), comparison(args));
        }
        return t("event", "%s starts this part of the graph when %s.", name(step), escape(node.settings().getOrDefault("trigger", node.label())));
    }

    private String ramp(Step step){
        String res = t("ramp", "%s receives %s. Each pulse switches it between moving towards that value and returning towards 0.",
                name(step), value(step.inputs().get("value")));
        if(known(step, "rise")) res += " " + t("rise", "It rises by %s per tick.", input(step, "rise"));
        if(known(step, "fall")){
            if(number(step.inputs().get("fall")) == 0) res += " " + t("no_fall", "Fall is 0, so it will not decrease.");
            else res += " " + t("fall", "It falls by %s per tick.", input(step, "fall"));
        }
        if(known(step, "reset_delay") && number(step.inputs().get("reset_delay")) > 0)
            res += " " + t("reset_delay", "It waits %s ticks before falling.", input(step, "reset_delay"));
        return res;
    }

    private String source(Step step){
        String description = step.meaning().settings().getOrDefault("description", "");
        if(!description.isBlank()) return t("described", "%s: %s", name(step), escape(description));
        List<String> ports = usedPorts.getOrDefault(step.nodeId(), Set.of()).stream()
                .map(port -> port.replace('_', ' ')).sorted().limit(6).toList();
        return ports.isEmpty() ? t("source", "%s supplies data to the connected nodes.", name(step))
                : t("source_ports", "%s supplies readings such as %s.", name(step), escape(String.join(", ", ports)));
    }

    private String action(Step step){
        GraphNodeMeaning node = step.meaning();
        if(node.settings().containsKey("event"))
            return t("send_event", "%s sends the named event %s.", name(step), bold(node.settings().get("event")));
        String target = node.settings().getOrDefault("targetLabel", String.join(", ", node.targets()));
        Expression wired = step.inputs().get("target");
        String destination = wired != null && !wired.nodeId().isBlank() ? t("to_target", " on %s", value(wired))
                : target.isBlank() ? "" : t("to_target", " on %s", escape(target));
        List<String> vals = new ArrayList<>();
        step.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if(Set.of("target", "merge_like_ports", "type", "default").contains(entry.getKey())) return;
            if(!node.effectInputs().isEmpty() && !node.effectInputs().contains(entry.getKey())) return;
            if(entry.getValue().operation() == UNKNOWN && entry.getValue().nodeId().isBlank()) return;
            vals.add(t("property", "%s to %s", escape(node.inputLabels().getOrDefault(entry.getKey(),
                    entry.getKey().replace('_', ' '))), value(entry.getValue())));
        });
        String res = vals.isEmpty() ? t("action", "%s carries out its configured action%s.", name(step), destination)
                : t("write", "%s sets %s%s.", name(step), String.join(", ", vals.subList(0, Math.min(4, vals.size()))), destination);
        if("true".equals(node.settings().get("controlDestination")) && usesControl(step))
            res += " " + t("intent", "This appears intended to adjust the measured value towards its target; it does not guarantee that the control will work.");
        return res;
    }

    private boolean usesControl(Step step){
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(step.inputs().values().stream()
                .filter(val -> !val.nodeId().isBlank() && !val.external()).map(Expression::nodeId).toList());
        while(!pending.isEmpty() && visited.size() < 4096){
            String id = pending.removeFirst();
            if(!visited.add(id)) continue;
            Step prev = byId.get(id);
            if(prev == null) continue;
            if(prev.meaning().operation() == CONTROL) return true;
            if(prev.meaning().operation() == ACTION || prev.meaning().operation() == VARIABLE_WRITE) continue;
            pending.addAll(prev.inputs().values().stream().filter(val -> !val.nodeId().isBlank() && !val.external()).map(Expression::nodeId).toList());
        }
        return false;
    }

    private String generic(Step step){
        String description = step.meaning().settings().getOrDefault("description", "");
        if(!description.isBlank()) return t("described", "%s: %s", name(step), escape(description));
        List<String> inputs = step.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue)
                .filter(val -> !val.nodeId().isBlank() || val.operation() == LITERAL).map(this::value).distinct().limit(3).toList();
        List<String> outputs = step.meaning().definition().outputs().entrySet().stream()
                .filter(entry -> !entry.getValue().equals("exec"))
                .map(entry -> entry.getKey().replace('_', ' ') + " (" + entry.getValue() + ")").toList();
        return inputs.isEmpty() ? t("source_ports", "%s supplies readings such as %s.", name(step), escape(String.join(", ", outputs)))
                : t("generic_inputs", "%s uses %s to produce its output.", name(step), String.join(", ", inputs));
    }

    private String flow(Step step){
        if(known(step, "ticks")) return t("wait", "%s waits %s ticks before continuing.", name(step), input(step, "ticks"));
        if(known(step, "count")) return t("count", "%s allows its output to run up to %s times.", name(step), input(step, "count"));
        return t("flow", "%s passes execution to the connected steps.", name(step));
    }

    private String condition(Condition condition){
        String res = condition.expression().operation() == COMPARE ? comparison(condition.expression().inputs()) : value(condition.expression());
        return condition.outcome().equals("true") || condition.outcome().equals("exec") ? res
                : condition.outcome().equals("false") ? t("false", "%s is false", res)
                : t("case", "%s selects %s", res, escape(condition.outcome()));
    }

    private String comparison(Map<String, Expression> inputs){
        Expression op = inputs.get("operator");
        String word = switch(op == null ? "" : op.label().replace("\"", "")){
            case "==" -> t("equal", "equals");
            case "!=" -> t("unequal", "does not equal");
            case ">" -> t("greater", "is greater than");
            case "<" -> t("less", "is less than");
            case ">=" -> t("at_least", "is at least");
            case "<=" -> t("at_most", "is at most");
            default -> t("compared", "matches the configured comparison with");
        };
        return value(inputs.get("a")) + " " + word + " " + value(inputs.get("b"));
    }

    private String input(Step step, String port){ return value(step.inputs().get(port)); }
    private boolean known(Step step, String... ports){
        for(String port : ports){
            Expression val = step.inputs().get(port);
            if(val == null || val.operation() != LITERAL || val.external()) return false;
        }
        return true;
    }

    private double number(Expression val){
        try{ return Double.parseDouble(val.label()); }
        catch(NumberFormatException ex){ return Double.NaN; }
    }

    private String subject(Expression val){
        Step prev = val == null ? null : byId.get(val.nodeId());
        return prev == null ? capital(value(val)) : name(prev);
    }

    private String value(Expression val){
        if(val == null || val.operation() == UNKNOWN && val.nodeId().isBlank()) return t("missing", "an input that is not set");
        if(val.external()) return t("external", "%s from outside this selection", escape(val.label()));
        if(val.operation() == LITERAL) return escape(val.label());
        Set<String> visited = new HashSet<>();
        while(visited.size() < 32 && visited.add(val.nodeId())){
            Step prev = byId.get(val.nodeId());
            if(prev == null) return escape(val.label());
            if(prev.meaning().operation() == LITERAL) return escape(prev.meaning().settings().getOrDefault("targetLabel",
                    prev.meaning().settings().getOrDefault("value", val.label())));
            if(prev.meaning().operation() != ROUTE || !prev.inputs().containsKey("value")) break;
            val = prev.inputs().get("value");
            if(val.external()) return t("external", "%s from outside this selection", escape(val.label()));
            if(val.operation() == LITERAL) return escape(val.label());
        }
        Step prev = byId.get(val.nodeId());
        if(prev == null) return escape(val.label());
        return Set.of("value", "Out", "Result", "result", "").contains(val.port())
                ? t("output", "the output from %s", name(prev))
                : t("port", "the %s from %s", escape(val.port().replace('_', ' ')), name(prev));
    }

    private String name(Step step){ return bold(step.meaning().label()); }
    private String bold(String val){ return "**" + escape(val) + "**"; }
    private String capital(String val){ return val.isBlank() ? val : val.substring(0, 1).toUpperCase(Locale.ROOT) + val.substring(1); }

    // Treat configured names as text rather than Markdown links, images or formatting
    private String escape(String val){
        StringBuilder res = new StringBuilder();
        for(int idx = 0; idx < val.length(); idx++){
            char ch = val.charAt(idx);
            if(Character.isISOControl(ch)){ res.append(' '); continue; }
            if("\\*_[]()#!<>|".indexOf(ch) >= 0 || ch == 96) res.append((char)92);
            res.append(ch);
        }
        return res.toString();
    }

    private String t(String key, String fallback, Object... args){
        return text.resolve("gadgetsngizmos.graph.explain.step." + key, fallback, args);
    }
}

