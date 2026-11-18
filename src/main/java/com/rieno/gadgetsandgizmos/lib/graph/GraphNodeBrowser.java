package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphTextLayout;

// Keep preferred browser categories first without hiding categories registered by a host
public final class GraphNodeBrowser{
    private GraphNodeBrowser(){}

    public enum Access{ GET, SET, ON, SEND }

    // Describe configured variable and event nodes without choosing a host's node type ids
    public record NamedAccess(String name, Access action){
        public NamedAccess{
            name = Objects.requireNonNull(name).strip();
            Objects.requireNonNull(action);
            if(name.isEmpty()) throw new IllegalArgumentException("A named access requires a name");
        }
        public String category(){ return action == Access.GET || action == Access.SET ? "variables" : "events"; }
        public String label(){ return "[" + name + "] " + (action == Access.SEND ? "Send" : action.name()); }
        public String label(ToIntFunction<String> measure, int width){
            String suffix = "] " + (action == Access.SEND ? "Send" : action.name());
            return "[" + GraphTextLayout.ellipsize(name, measure, width - measure.applyAsInt("[" + suffix)) + suffix;
        }
        public String searchText(){ return name + " " + action.name() + (category().equals("variables") ? " variable" : " named event"); }
        public String id(){ return "graph-access:" + action.name() + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8)); }
    }

    public static List<NamedAccess> namedAccesses(Collection<String> variables, Collection<String> events){
        List<NamedAccess> res = new ArrayList<>();
        for(String name : new LinkedHashSet<>(variables)) if(name != null && !name.isBlank()){
            res.add(new NamedAccess(name, Access.GET)); res.add(new NamedAccess(name, Access.SET));
        }
        for(String name : new LinkedHashSet<>(events)) if(name != null && !name.isBlank()){
            res.add(new NamedAccess(name, Access.ON)); res.add(new NamedAccess(name, Access.SEND));
        }
        return List.copyOf(res);
    }

    public static NamedAccess namedAccess(String id){
        if(id == null || !id.startsWith("graph-access:")) return null;
        String[] parts = id.split(":", 3);
        if(parts.length != 3) return null;
        try{
            return new NamedAccess(new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8), Access.valueOf(parts[1]));
        }catch(IllegalArgumentException err){ return null; }
    }

    // Match each word so a name and an action can be searched together
    public static boolean matches(String query, String... fields){
        if(query == null || query.isBlank()) return true;
        String text = String.join(" ", fields).toLowerCase(Locale.ROOT);
        for(String word : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) if(!text.contains(word)) return false;
        return true;
    }

    // The host supplies resolved ports so dynamically added execution inputs are included
    public static boolean standaloneExecutionSource(Map<String, String> inputs, Map<String, String> outputs){
        return !inputs.containsValue("exec") && outputs.containsValue("exec");
    }

    public static List<String> categories(Collection<String> preferred, Collection<String> available,
                                           Function<String, String> label){
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for(String category : preferred) if(available.contains(category)) ordered.add(category);
        List<String> remaining = new ArrayList<>(new LinkedHashSet<>(available));
        remaining.removeAll(ordered);
        remaining.sort(Comparator.comparing(label, String.CASE_INSENSITIVE_ORDER).thenComparing(Function.identity()));
        ordered.addAll(remaining);
        return List.copyOf(ordered);
    }

    public record Group<T>(String category, List<T> entries){
        public Group{ entries = List.copyOf(entries); }
    }

    // Use one grouping and ordering policy for full libraries and contextual node pickers
    public static <T> List<Group<T>> groups(Collection<T> entries, Function<T, String> category,
                                            Collection<String> preferred, Function<String, String> label,
                                            Function<T, String> name){
        Map<String, List<T>> groups = new LinkedHashMap<>();
        for(T entry : entries) groups.computeIfAbsent(category.apply(entry), ignored -> new ArrayList<>()).add(entry);
        return categories(preferred, groups.keySet(), label).stream().map(id -> {
            List<T> values = groups.get(id);
            values.sort(Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER));
            return new Group<T>(id, values);
        }).toList();
    }
}
