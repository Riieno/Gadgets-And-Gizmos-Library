package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SourcedGraphCatalogTest{
    record File(String id, String name){}

    @Test
    void equalNamesAndIdsKeepDistinctClientAndServerEntriesAcrossRefreshes(){
        var same = new File("steering", "Steering");
        var server = List.of(same);
        var entries = SourcedGraphCatalog.merge(List.of(same), server, File::id, File::name);
        assertEquals(List.of("[C] Steering", "[S] Steering"), entries.stream().map(SourcedGraphCatalog.Entry::label).toList());
        assertNotEquals(entries.getFirst(), entries.getLast());
        var refreshed = SourcedGraphCatalog.merge(List.of(same, new File("braking", "Braking")), server, File::id, File::name);
        assertEquals(3, refreshed.size());
        assertEquals(entries.getLast(), refreshed.getLast());
        assertEquals(List.of(entries.getLast()), SourcedGraphCatalog.merge(List.<File>of(), server, File::id, File::name));
    }
}
