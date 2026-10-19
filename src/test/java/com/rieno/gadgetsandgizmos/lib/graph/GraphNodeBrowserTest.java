package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphNodeBrowserTest{
    @Test void resolvedExecutionInputsDistinguishFlowNodesFromStandaloneSources(){
        assertFalse(GraphNodeBrowser.standaloneExecutionSource(
                GraphPortOrder.ports("exec_1", "exec", "exec_2", "exec"), Map.of("exec", "exec")));
        assertFalse(GraphNodeBrowser.standaloneExecutionSource(
                Map.of("exec", "exec"), GraphPortOrder.ports("exec_1", "exec", "exec_2", "exec")));
        assertTrue(GraphNodeBrowser.standaloneExecutionSource(Map.of(), Map.of("exec", "exec")));
        assertFalse(GraphNodeBrowser.standaloneExecutionSource(Map.of(), Map.of("value", "number")));
    }

    @Test void unlistedCategoriesRemainReachableAfterPreferredCategories(){
        var labels = Map.of("core", "Core", "math", "Math", "list_map", "Lists and Maps", "addon", "Additional");
        assertEquals(List.of("core", "math", "addon", "list_map"), GraphNodeBrowser.categories(
                List.of("core", "math", "absent"), List.of("list_map", "addon", "core", "math", "addon"), labels::get));
    }
}
