package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

// Keep client and server graph identities distinct while presenting one catalogue
public final class SourcedGraphCatalog{
    private SourcedGraphCatalog(){}

    public enum Source{
        CLIENT("[C]"), SERVER("[S]");

        private final String prefix;

        Source(String prefix){
            this.prefix = prefix;
        }
    }

    public record Entry(Source source, String id, String name){
        public String label(){
            return source.prefix + " " + name;
        }
    }

    public static <T> List<Entry> merge(List<T> client, List<T> server,
                                        Function<T, String> id, Function<T, String> name){
        List<Entry> entries = new ArrayList<>();
        client.forEach(value -> entries.add(new Entry(Source.CLIENT, id.apply(value), name.apply(value))));
        server.forEach(value -> entries.add(new Entry(Source.SERVER, id.apply(value), name.apply(value))));
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::source).thenComparing(Entry::id));
        return List.copyOf(entries);
    }
}
