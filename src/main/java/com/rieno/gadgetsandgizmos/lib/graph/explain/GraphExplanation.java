package com.rieno.gadgetsandgizmos.lib.graph.explain;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning.Operation.*;
import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSelectionAnalysis.*;

// Render relational behaviour summaries using translatable sentence fragments and bounded expressions
public final class GraphExplanation{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_BEHAVIOURS = 48;
    private static final int MAX_EXPRESSION = 420;
    private static final int MAX_TEXT = 48000;
    private final TextResolver text;
    private int remaining;

    @FunctionalInterface
    public interface TextResolver{
        String resolve(String key, String fallback, Object... args);
    }

    public GraphExplanation(){
        this((key, fallback, args) -> String.format(Locale.ROOT, fallback, args));
    }

    public GraphExplanation(TextResolver text){ this.text = text; }

    // Produce a Markdown list which follows values through the selected stages
    public String explainMarkdown(GraphSelectionAnalysis analysis){
        return new GraphMarkdownExplanation(text).explain(analysis);
    }

    // Associate each readable stage with its node for in-graph annotations
    public List<NodeStep> explainSteps(GraphSelectionAnalysis analysis){
        return new GraphMarkdownExplanation(text).steps(analysis);
    }

    // Retain the node identity separately from its player-facing explanation
    public record NodeStep(String nodeId, String markdown){}

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Return stable paragraphs suitable for a plain text viewer or a Minecraft Component
    public String explain(GraphSelectionAnalysis analysis){
        remaining = 32768;
        List<String> paragraphs = new ArrayList<>();
        if(analysis.selectedCount() == 0) return t("empty", "Select one or more nodes to explain.");
        for(Behaviour behaviour : analysis.behaviours()){
            if(paragraphs.size() >= MAX_BEHAVIOURS) break;
            paragraphs.add(behaviour(behaviour));
        }
        if(analysis.behaviours().size() > MAX_BEHAVIOURS)
            paragraphs.add(t("more", "%s further operations were omitted.", analysis.behaviours().size() - MAX_BEHAVIOURS));
        long entering = analysis.boundaries().stream().filter(Boundary::entering).count();
        long leaving = analysis.boundaries().size() - entering;
        if(entering > 0) paragraphs.add(t("entering", "%s connections enter from outside the selection; their sources are external dependencies.", entering));
        if(leaving > 0) paragraphs.add(t("leaving", "%s connections leave the selection; downstream behaviour is outside this explanation.", leaving));
        Set<String> codes = new HashSet<>();
        analysis.notices().forEach(notice -> codes.add(notice.code()));
        if(codes.contains("cycle")) paragraphs.add(t("cycle", "Cyclic dependencies are present. Traversal stops at cycles; execution and convergence are not assumed."));
        if(codes.contains("reference")) paragraphs.add(t("reference", "Some named references cannot be resolved from the saved graph."));
        if(codes.contains("unsupported")) paragraphs.add(t("unsupported", "Some node types have no supported semantics; only their configured connections can be described."));
        if(codes.contains("port") || codes.contains("topology") || codes.contains("ambiguous") || codes.contains("selection"))
            paragraphs.add(t("invalid", "Missing endpoints, invalid ports or ambiguous configuration limit this explanation."));
        if(codes.contains("limit")) paragraphs.add(t("limit", "Analysis limits were reached; this explanation is incomplete."));
        if(remaining <= 0) paragraphs.add(t("limit", "Analysis limits were reached; this explanation is incomplete."));
        String res = String.join("\n\n", paragraphs);
        return res.length() <= MAX_TEXT ? res : res.substring(0, MAX_TEXT) + "\n\n" + t("limit", "Analysis limits were reached; this explanation is incomplete.");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private String behaviour(Behaviour behaviour){
        GraphNodeMeaning node = behaviour.meaning();
        boolean effect = node.operation() == ACTION || node.operation() == VARIABLE_WRITE || node.operation() == FUNCTION;
        String body;
        if(effect){
            String action = node.settings().getOrDefault("action", node.label());
            List<String> targets = new ArrayList<>(node.targets());
            Expression wired = behaviour.inputs().get("target");
            if(wired != null && !wired.nodeId().isBlank()) targets = List.of(expression(wired, 0));
            String target = targets.isEmpty() ? "" : t("on_targets", " on %s", String.join(", ", targets));
            List<String> vals = new ArrayList<>();
            behaviour.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if(entry.getKey().equals("target") || entry.getKey().equals("merge_like_ports")) return;
                if(!node.effectInputs().isEmpty() && !node.effectInputs().contains(entry.getKey())) return;
                Expression val = entry.getValue();
                if(val.operation() == UNKNOWN && val.nodeId().isBlank()) return;
                vals.add(t("assignment", "%s = %s", node.inputLabels().getOrDefault(entry.getKey(), entry.getKey()), expression(val, 0)));
            });
            String using = vals.isEmpty() ? "" : t("using", " using %s", join(vals, 8));
            body = action + target + using;
        }else if(node.operation() == EVENT){
            body = t("event", "provides an execution trigger for %s", node.settings().getOrDefault("trigger", node.label()));
            if(!behaviour.inputs().isEmpty()) body += t("using", " using %s", arguments(behaviour.inputs(), 0));
        }else if(node.operation() == SOURCE && node.definition().outputs().size() > 1){
            body = t("source", "makes %s available (%s)", node.label(), join(node.outputLabels().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList(), 6));
            Expression target = behaviour.inputs().get("target");
            if(target != null && !target.nodeId().isBlank()) body += t("on_targets", " on %s", expression(target, 0));
        }else if(node.operation() == UNKNOWN){
            body = t("unknown", "contains unsupported node %s with %s inputs and %s outputs",
                    node.label(), node.definition().inputs().size(), node.definition().outputs().size());
        }else{
            String port = node.definition().outputs().keySet().stream().sorted()
                    .filter(key -> !"exec".equals(node.definition().outputs().get(key))).findFirst().orElse("");
            if(node.operation() == CONTROL && node.definition().outputs().containsKey(node.settings().getOrDefault("controlOutput", "value")))
                port = node.settings().getOrDefault("controlOutput", "value");
            String label = node.outputLabels().getOrDefault(port, node.label());
            Expression val = new Expression(behaviour.nodeId(), port, node.operation(),
                    node.operation() == LITERAL ? node.settings().getOrDefault("value", node.label()) : label, behaviour.inputs(), false);
            body = t("provides", "provides %s", expression(val, 0));
        }
        String res = execution(body, behaviour.paths());
        Expression controller = effect && "true".equals(node.settings().get("controlDestination"))
                ? findControl(behaviour.inputs()) : null;
        if(controller != null){
            Expression actual = controller.inputs().get("actual");
            Expression target = controller.inputs().get("target");
            if(actual != null && target != null && actual.operation() != UNKNOWN && target.operation() != UNKNOWN){
                res += " " + t("intent", "This appears intended to regulate %s towards %s using %s; the graph alone does not verify that it succeeds.",
                        expression(actual, 0), expression(target, 0), controller.label());
            }
        }
        for(var ref : node.references()){
            if(!ref.detail().isBlank()) res += " " + ref.detail();
            if(!ref.nodeIds().isEmpty())
                res += " " + t("related", "Related %s references: %s.", ref.kind(), ref.nodeIds().size());
        }
        if(node.operation() == UNKNOWN && !behaviour.inputs().isEmpty()){
            res += " " + t("known_inputs", "Connected or configured inputs: %s.", arguments(behaviour.inputs(), 0));
        }
        return res;
    }

    private String execution(String body, List<ExecutionPath> paths){
        if(paths.isEmpty()) return sentence(body);
        List<String> alternatives = new ArrayList<>();
        for(ExecutionPath path : paths){
            if(!path.connected()){
                alternatives.add(t("untriggered", "No complete execution route is selected for this operation: %s.", body));
                continue;
            }
            List<String> conditions = new ArrayList<>();
            for(Condition condition : path.conditions()){
                String val = expression(condition.expression(), 0);
                String outcome = condition.outcome();
                if(outcome.equals("true") || outcome.equals("exec")) conditions.add(val);
                else if(outcome.equals("false")) conditions.add(t("false", "not (%s)", val));
                else conditions.add(t("case", "%s selects %s", val, outcome));
            }
            String trigger = path.external() ? t("external_trigger", "execution enters from %s outside the selection", path.trigger())
                    : path.trigger();
            if(!path.inputs().isEmpty()) trigger = t("trigger_inputs", "%s (%s)", trigger, arguments(path.inputs(), 0));
            String when = conditions.isEmpty() ? trigger
                    : t("condition", "%s and %s", trigger, String.join(t("and", " and "), conditions));
            String route = path.steps().isEmpty() ? "" : t("via", " through %s", String.join(", ", path.steps().reversed()));
            alternatives.add(t("when", "When %s%s, %s.", when, route, body));
        }
        return String.join(" ", alternatives.stream().distinct().toList());
    }

    private String expression(Expression val, int depth){
        if(depth >= 10 || --remaining <= 0) return val.label();
        if(val.external()) return t("external", "external %s", val.label());
        List<Expression> args = val.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> !Set.of("reset", "operator").contains(entry.getKey())).map(Map.Entry::getValue).toList();
        String a = operand(val, "a", args, 0, depth);
        String b = operand(val, "b", args, 1, depth);
        String res = switch(val.operation()){
            case LITERAL, VARIABLE_READ -> val.label();
            case SOURCE -> val.inputs().containsKey("target")
                    ? t("source_target", "%s on %s", val.label(), named(val, "target", depth)) : val.label();
            case UNKNOWN -> t("missing", "unconfigured %s", val.label());
            case ROUTE -> args.isEmpty() ? val.label() : expression(args.getFirst(), depth + 1);
            case ADD -> t("sum", "the sum of %s and %s", a, b);
            case SUBTRACT -> t("difference", "the difference (%s minus %s)", a, b);
            case MULTIPLY -> t("product", "%s scaled by %s", a, b);
            case DIVIDE -> t("quotient", "%s divided by %s", a, b);
            case AND -> t("both", "(%s and %s)", a, b);
            case OR -> t("either", "(%s or %s)", a, b);
            case NOT -> t("false", "not (%s)", args.isEmpty() ? val.label() : expression(args.getFirst(), depth + 1));
            case COMPARE -> compare(val, a, b, depth);
            case CONTROL -> t("correction", "%s correcting %s towards %s%s", val.label(),
                    named(val, "actual", depth), named(val, "target", depth), controllerSettings(val.inputs(), depth));
            case CLAMP -> t("clamp", "%s limited between %s and %s", named(val, "value", depth), named(val, "min", depth), named(val, "max", depth));
            case MAP_RANGE -> t("range", "%s remapped from [%s, %s] to [%s, %s]", named(val, "value", depth),
                    named(val, "in_min", depth), named(val, "in_max", depth), named(val, "out_min", depth), named(val, "out_max", depth));
            case SELECT -> t("choose", "%s when %s, otherwise %s", named(val, "true", depth), named(val, "condition", depth), named(val, "false", depth));
            default -> val.inputs().isEmpty() ? val.label() : t("transform", "%s from %s", val.label(), arguments(val.inputs(), depth));
        };
        return res.length() <= MAX_EXPRESSION ? res : res.substring(0, MAX_EXPRESSION) + "…";
    }

    private String compare(Expression val, String a, String b, int depth){
        Expression op = val.inputs().get("operator");
        if(op == null || op.operation() != LITERAL) return t("comparison", "a comparison of %s and %s using %s", a, b,
                op == null ? t("missing_operator", "an unconfigured operator") : expression(op, depth + 1));
        String operator = op.label().replace("\"", "");
        String word = switch(operator){
            case "==" -> t("equal", "equals");
            case "!=" -> t("unequal", "does not equal");
            case ">" -> t("greater", "is greater than");
            case "<" -> t("less", "is less than");
            case ">=" -> t("at_least", "is at least");
            case "<=" -> t("at_most", "is at most");
            default -> operator;
        };
        return t("comparison_result", "%s %s %s", a, word, b);
    }

    private String operand(Expression val, String name, List<Expression> args, int idx, int depth){
        Expression arg = val.inputs().get(name);
        if(arg == null && args.size() > idx) arg = args.get(idx);
        return arg == null ? t("missing", "unconfigured %s", name) : expression(arg, depth + 1);
    }

    private String named(Expression val, String key, int depth){
        Expression arg = val.inputs().get(key);
        return arg == null ? t("missing", "unconfigured %s", key) : expression(arg, depth + 1);
    }

    private String arguments(Map<String, Expression> inputs, int depth){
        return argumentsExcept(inputs, Set.of(), depth);
    }

    private String controllerSettings(Map<String, Expression> inputs, int depth){
        Map<String, Expression> configured = new java.util.LinkedHashMap<>();
        inputs.entrySet().stream().filter(entry -> entry.getValue().operation() != UNKNOWN)
                .forEach(entry -> configured.put(entry.getKey(), entry.getValue()));
        String res = argumentsExcept(configured, Set.of("actual", "target"), depth);
        return res.isEmpty() ? "" : " (" + res + ")";
    }

    private String argumentsExcept(Map<String, Expression> inputs, Set<String> omitted, int depth){
        List<String> vals = new ArrayList<>();
        inputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(entry -> !omitted.contains(entry.getKey()))
                .limit(6).forEach(entry -> vals.add(t("assignment", "%s = %s", entry.getKey(), expression(entry.getValue(), depth + 1))));
        return String.join(", ", vals);
    }

    private Expression findControl(Map<String, Expression> inputs){
        ArrayDeque<Expression> pending = new ArrayDeque<>(inputs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList());
        Set<Expression> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int visits = 0;
        while(!pending.isEmpty() && visits++ < 4096){
            Expression next = pending.removeFirst();
            if(!seen.add(next) || next.external()) continue;
            if(next.operation() == CONTROL) return next;
            pending.addAll(next.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList());
        }
        return null;
    }

    private String join(List<String> vals, int limit){
        String res = String.join(", ", vals.subList(0, Math.min(limit, vals.size())));
        return vals.size() > limit ? res + t("additional", " and %s other configured inputs", vals.size() - limit) : res;
    }

    private String sentence(String val){
        return val.isBlank() ? "" : val.substring(0, 1).toUpperCase(Locale.ROOT) + val.substring(1) + ".";
    }

    private String t(String key, String fallback, Object... args){
        return text.resolve("gadgetsngizmos.graph.explain." + key, fallback, args);
    }
}

