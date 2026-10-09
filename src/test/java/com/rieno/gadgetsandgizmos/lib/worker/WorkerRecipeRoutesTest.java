package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRecipeRoutesTest{
    @Test void rejectsAnUnavailableRootWithoutReadingItsPrerequisites(){
        WorkerResourceKey output = item("dust"), raw = item("ore");
        var def = recipe(output, raw, WorkerRecipePlan.Operation.PROCESSING);
        var reads = new java.util.ArrayList<WorkerResourceKey>();
        WorkerRecipeSource source = resource -> { reads.add(resource); return resource.equals(output) ? List.of(def) : List.of(); };
        var routes = new WorkerRecipeRoutes(source, Set.of());
        var result = WorkerRecipePlanner.planFromStock(output, 1L, Map.of(raw, 1L), routes, null,
                candidate -> true, plan -> true, null, plan -> 0, null);
        assertFalse(result.chain().executable());
        assertEquals("missing_machine", result.failure().code());
        assertTrue(result.details().getFirst().contains("test:macerating"));
        assertTrue(reads.stream().allMatch(output::equals));
    }

    @Test void keepsHandCraftingWhilePruningAnUnavailableMachineTree(){
        WorkerResourceKey output = item("dust"), raw = item("ore"), hammer = item("hammer");
        var process = recipe(output, raw, WorkerRecipePlan.Operation.PROCESSING);
        var craft = recipe(output, hammer, WorkerRecipePlan.Operation.WORKER_CRAFTING);
        var index = new WorkerRecipeIndex(List.of(process, craft));
        var routes = new WorkerRecipeRoutes(index, Set.of());
        assertEquals(List.of(craft), routes.producing(output));
        assertTrue(routes.relationships(List.of(output)).producing(raw).isEmpty());
        var result = WorkerRecipePlanner.planFromStock(output, 1L, Map.of(hammer, 1L), routes, null,
                candidate -> true, plan -> true, null, plan -> 0, null);
        assertTrue(result.chain().executable());
        assertEquals(WorkerRecipePlan.Operation.WORKER_CRAFTING, result.chain().steps().getFirst().plan().operation());
        assertEquals(routes, new WorkerRecipeRoutes(index, Set.of()));
        assertSame(routes.relationships(List.of(output)), new WorkerRecipeRoutes(index, Set.of()).relationships(List.of(output)));
    }

    private static WorkerRecipeDefinition recipe(WorkerResourceKey output, WorkerResourceKey input, WorkerRecipePlan.Operation op){
        return new WorkerRecipeDefinition(input.id(), ResourceLocation.parse("test:macerating"), op,
                List.of(new WorkerRecipeDefinition.Ingredient(List.of(input), 1L)), output, 1L);
    }
    private static WorkerResourceKey item(String path){ return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.parse("test:" + path)); }
}
