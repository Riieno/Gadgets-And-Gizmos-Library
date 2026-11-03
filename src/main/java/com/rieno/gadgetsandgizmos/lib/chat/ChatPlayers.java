package com.rieno.gadgetsandgizmos.lib.chat;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.PlayerGraphValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Convert editable player lists without depending on a particular graph editor
public final class ChatPlayers{
    private ChatPlayers(){}

    // Preserve invalid entries as empty identifiers so a nonempty list cannot become an unrestricted audience
    public static List<String> identifiers(GraphValue value){
        if(value != null && PlayerGraphValue.TYPE.equals(value.type())) return List.of(identifier(value));
        if(value == null || !"list".equals(value.type()) || !(value.value() instanceof List<?> entries)) return List.of();
        List<String> players = new ArrayList<>(entries.size());
        for(Object entry : entries){
            if(entry instanceof GraphValue val) entry = PlayerGraphValue.TYPE.equals(val.type())
                    ? identifier(val) : "string".equals(val.type()) ? val.asString() : "";
            players.add(entry instanceof String name ? name : "");
        }
        return List.copyOf(players);
    }

    // Keep an invalid player reference nonempty so it cannot become an unrestricted audience
    private static String identifier(GraphValue value){
        var id = PlayerGraphValue.id(value);
        return id == null ? "" : id.toString();
    }

    // Accept comma-separated names or UUIDs in a player-list text field
    public static List<String> parse(String text){
        if(text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[,\\p{javaWhitespace}]+"))
                .filter(entry -> !entry.isBlank()).toList();
    }

    public static String format(Collection<String> players){
        if(players == null || players.isEmpty()) return "";
        return players.stream().filter(entry -> entry != null && !entry.isBlank())
                .collect(Collectors.joining(", "));
    }
}
