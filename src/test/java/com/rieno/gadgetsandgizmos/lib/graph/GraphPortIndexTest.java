package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GraphPortIndexTest{
    @Test void ownershipAndStartsFollowCallerOrderAndKeepTheFirstOverlappingSection(){
        var first = new Section(List.of("a", "a.child", "shared"));var second = new Section(List.of("b", "shared"));
        var index = GraphPortIndex.of(List.of("b", "a.child", "a", "shared"), List.of(first, second), Section::ports);
        assertSame(first, index.section("shared"));assertSame(first, index.section("a.child"));
        assertSame(second, index.startingAt("b"));assertSame(first, index.startingAt("a.child"));
        assertNull(index.startingAt("a"));assertNull(index.section("missing"));
    }

    @Test void thousandsOfPortLookupsDoNotRevisitSectionLists(){
        List<String> ports = new ArrayList<>();List<Section> sections = new ArrayList<>();
        for(int group = 0; group < 50; group++){
            List<String> members = new ArrayList<>();
            for(int row = 0; row < 100; row++){ String port = "p" + (group * 100 + row);ports.add(port);members.add(port); }
            sections.add(new Section(List.copyOf(members)));
        }
        AtomicInteger reads = new AtomicInteger();
        var index = GraphPortIndex.of(ports, sections, section -> { reads.incrementAndGet();return section.ports(); });
        assertEquals(50, reads.get());
        for(int frame = 0; frame < 20; frame++) for(String port : ports) assertNotNull(index.section(port));
        assertEquals(50, reads.get());
    }

    @Test void controlRowsIncludeHeadersAndSpacersWithoutCreatingPhantomPorts(){
        var rows = GraphNodeLayout.rows(List.of(new GraphNodeLayout.Row("", 2), new GraphNodeLayout.Row("a", 1),
                new GraphNodeLayout.Row("", 1), new GraphNodeLayout.Row("b", 2), new GraphNodeLayout.Row("hidden", 0)));
        assertEquals(2, rows.offsets().get("a"));assertEquals(4, rows.offsets().get("b"));assertEquals(6, rows.height());
        assertFalse(rows.offsets().containsKey("hidden"));assertFalse(rows.offsets().containsKey(""));
        assertThrows(UnsupportedOperationException.class, () -> rows.offsets().put("other", 1));
    }

    private record Section(List<String> ports){}
}
