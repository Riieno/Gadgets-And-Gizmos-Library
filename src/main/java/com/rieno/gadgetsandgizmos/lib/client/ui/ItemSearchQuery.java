package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// Share item browser searches without requiring a recipe viewer to be installed
public final class ItemSearchQuery{
    private static final Pattern TOKENS = Pattern.compile("(-?[@#$]?\"[^\"]*\"|[^\\s|]+)|([|])");

    private ItemSearchQuery(){}

    // Cache searchable metadata once when a browser opens
    public record Entry(String id, String name, String mod, List<String> tags){
        public Entry{
            id = normalize(id);
            name = normalize(name);
            mod = normalize(mod);
            tags = tags.stream().map(ItemSearchQuery::normalize).toList();
        }
    }

    // Resolve one registered item's display name, owning mod and current item tags
    public static Entry item(ResourceLocation id, ItemStack stack){
        if(id == null || stack == null || stack.isEmpty()) return new Entry("", "", "", List.of());
        String mod = ModList.get().getModContainerById(id.getNamespace())
                .map(container -> container.getModInfo().getDisplayName()).orElse(id.getNamespace());
        return new Entry(id.toString(), stack.getHoverName().getString(), mod,
                stack.getTags().map(tag -> tag.location().toString()).toList());
    }

    // Spaces require every term, pipes accept alternatives, and a leading minus excludes a term
    public static Predicate<Entry> compile(String query){
        List<List<Predicate<Entry>>> groups = new ArrayList<>();
        List<Predicate<Entry>> group = new ArrayList<>();
        var tokens = TOKENS.matcher(normalize(query));
        while(tokens.find()){
            if(tokens.group(2) != null){
                if(!group.isEmpty()) groups.add(List.copyOf(group));
                group.clear();
                continue;
            }
            String token = tokens.group(1);
            boolean exclude = token.startsWith("-");
            if(exclude) token = token.substring(1);
            if(token.isEmpty()) continue;
            char prefix = token.charAt(0);
            boolean scoped = prefix == '@' || prefix == '#' || prefix == '$';
            String value = (scoped ? token.substring(1) : token).replace("\"", "");
            if(value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
            if(value.isBlank()) continue;
            String term = value;
            Predicate<Entry> match = switch(prefix){
                case '@' -> entry -> entry.mod().contains(term) || entry.id().split(":", 2)[0].contains(term);
                case '#', '$' -> entry -> entry.tags().stream().anyMatch(tag -> tag.contains(term));
                default -> entry -> entry.name().contains(term) || (term.indexOf(':') >= 0
                        ? entry.id().contains(term)
                        : entry.id().substring(entry.id().indexOf(':') + 1).contains(term));
            };
            group.add(exclude ? match.negate() : match);
        }
        if(!group.isEmpty()) groups.add(List.copyOf(group));
        return entry -> groups.isEmpty() || groups.stream().anyMatch(terms -> terms.stream().allMatch(term -> term.test(entry)));
    }

    private static String normalize(String value){ return value == null ? "" : value.toLowerCase(Locale.ROOT).strip(); }
}
