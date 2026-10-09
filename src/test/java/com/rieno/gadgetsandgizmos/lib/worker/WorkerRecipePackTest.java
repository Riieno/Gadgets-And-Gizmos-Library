package com.rieno.gadgetsandgizmos.lib.worker;

import com.google.gson.JsonParser;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "gg.worker.pack", matches = ".+")
class WorkerRecipePackTest{
    // Exercise exported pack recipes without changing the installed pack or its world
    @Test void craftsToolsAndMachinesThroughPackPrerequisites() throws Exception{
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        var data = JsonParser.parseString(Files.readString(Path.of(System.getProperty("gg.worker.pack")))).getAsJsonArray();
        for(var entry : data){
            var recipe = entry.getAsJsonObject();
            List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
            for(var slot : recipe.getAsJsonArray("inputs")){
                var input = slot.getAsJsonArray();
                List<WorkerResourceKey> alternatives = new ArrayList<>();
                for(var resource : input.get(0).getAsJsonArray()) alternatives.add(item(resource.getAsString()));
                inputs.add(new WorkerRecipeDefinition.Ingredient(alternatives, input.get(1).getAsLong()));
            }
            definitions.add(new WorkerRecipeDefinition(ResourceLocation.parse(recipe.get("id").getAsString()),
                    ResourceLocation.parse(recipe.get("type").getAsString()),
                    WorkerRecipePlan.Operation.valueOf(recipe.get("op").getAsString()), inputs,
                    item(recipe.get("result").getAsString()), recipe.get("count").getAsLong()));
        }
        var index = new WorkerRecipeIndex(definitions);
        index.relationships(List.of(item("createthrusters:thruster")));
        Map<WorkerResourceKey, Long> thrusterStock = Map.of(item("minecraft:raw_iron"), 128L, item("minecraft:raw_copper"), 128L,
                item("minecraft:oak_log"), 64L, item("minecraft:netherrack"), 64L, item("minecraft:sand"), 64L,
                item("minecraft:cobblestone"), 64L, item("minecraft:redstone"), 64L);
        long thrusterStart = System.nanoTime();
        var thruster = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> WorkerRecipePlanner.planFromStock(
                item("createthrusters:thruster"), 1L, thrusterStock, index, null, def -> true, plan -> true,
                null, plan -> 0, (def, idx) -> 0L));
        assertTrue(thruster.chain().executable(), thruster.details().toString());
        assertTrue(thruster.chain().steps().stream().anyMatch(step ->
                step.plan().processorType().toString().equals("create:pressing")));
        assertTrue(thruster.chain().steps().stream().noneMatch(step ->
                step.plan().result().id().getPath().endsWith("ore_hammer")), "A linked press should avoid crafting ore hammers");
        WorkerRecipeSearchTest.verifyStock(thruster.chain(), thrusterStock);
        System.out.println("Thruster from base ingredients: " + thruster.chain().steps().size() + " steps, "
                + (System.nanoTime() - thrusterStart) / 1_000_000L + " ms");
        Set<ResourceLocation> machines = thruster.chain().steps().stream().map(step -> step.plan().processorType())
                .collect(java.util.stream.Collectors.toSet());
        try(var scheduler = new DeferredWorkScheduler()){
            var lookup = new WorkerRecipeLookup();
            var checks = new AtomicInteger();
            Thread owner = Thread.currentThread();
            Supplier<WorkerRecipePlanner.Result> request = () -> lookup.planFromStock(scheduler, index,
                    item("createthrusters:thruster"), 1L, thrusterStock, null, "linked machines",
                    (def, work) -> work.onOwnerThread(() -> {
                        assertSame(owner, Thread.currentThread());
                        checks.incrementAndGet();
                        return def.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING || machines.contains(def.processorType());
                    }), plan -> plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING || machines.contains(plan.processorType()),
                    null, plan -> 0, (def, idx) -> 0L);
            long started = System.nanoTime();
            var deferred = assertTimeout(Duration.ofSeconds(1), () -> poll(scheduler, request));
            assertTrue(deferred.chain().executable(), deferred.details().toString());
            WorkerRecipeSearchTest.verifyStock(deferred.chain(), thrusterStock);
            assertSame(deferred, request.get());
            System.out.println("Deferred thruster at 20 ticks/s: " + (System.nanoTime() - started) / 1_000_000L
                    + " ms, " + checks.get() + " support queries");
            var preloadChecks = new AtomicInteger();
            var supportChecks = new AtomicInteger();
            Supplier<WorkerRecipePlanner.Result> unavailable = () -> lookup.planFromStock(scheduler, index,
                    item("createthrusters:thruster"), 1L, thrusterStock, null, "missing stonecutter",
                    (def, work) -> work.onOwnerThread(() -> {
                        supportChecks.incrementAndGet();
                        return def.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                            || machines.contains(def.processorType()) && !def.processorType().equals(ResourceLocation.withDefaultNamespace("stonecutting"));
                    }),
                    plan -> !plan.processorType().equals(ResourceLocation.withDefaultNamespace("stonecutting")),
                    null, plan -> 0, (def, idx) -> { preloadChecks.incrementAndGet(); return 0L; });
            long missingStart = System.nanoTime();
            var rejected = assertTimeout(Duration.ofSeconds(1), () -> poll(scheduler, unavailable));
            System.out.println("Missing stonecutter " + (System.nanoTime() - missingStart) / 1_000_000L
                    + " ms: " + supportChecks.get() + " support, " + preloadChecks.get() + " preload checks");
            assertFalse(rejected.chain().executable());
        }
        var noCutting = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> WorkerRecipePlanner.planFromStock(
                item("createthrusters:thruster"), 1L, thrusterStock, index, null,
                def -> def.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                        || machines.contains(def.processorType()) && !def.processorType().equals(ResourceLocation.withDefaultNamespace("stonecutting")),
                plan -> !plan.processorType().equals(ResourceLocation.withDefaultNamespace("stonecutting")),
                null, plan -> 0, (def, idx) -> 0L));
        assertFalse(noCutting.chain().executable(), "Industrial iron needs a linked stonecutter");
        Map<WorkerResourceKey, Long> stock = Map.of(item("minecraft:iron_ore"), 64L, item("minecraft:oak_log"), 64L,
                item("minecraft:cobblestone"), 64L, item("minecraft:redstone"), 64L, item("minecraft:copper_ore"), 64L,
                item("minecraft:andesite"), 64L, item("minecraft:zinc_ingot"), 64L, item("create:zinc_ingot"), 64L,
                item("minecraft:quartz"), 64L, item("minecraft:stripped_oak_log"), 64L);
        for(String output : List.of("minecraft:iron_pickaxe", "minecraft:piston", "create:mechanical_crafter", "simulated:red_portable_engine")){
            long started = System.nanoTime();
            var planned = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> WorkerRecipePlanner.planFromStock(item(output),
                    1L, stock, index, null, def -> true, plan -> true, null, plan -> 0, (def, idx) -> 0L), output);
            assertTrue(planned.chain().executable(), output + ": " + planned.failure() + " " + planned.details());
            WorkerRecipeSearchTest.verifyStock(planned.chain(), stock);
            System.out.println(output + ": " + planned.chain().steps().size() + " steps, "
                    + (System.nanoTime() - started) / 1_000_000L + " ms");
        }
        var missing = new java.util.HashMap<>(stock);
        missing.remove(item("minecraft:stripped_oak_log"));
        var unavailable = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> WorkerRecipePlanner.planFromStock(
                item("create:mechanical_crafter"), 1L, missing, index, null, def -> true, plan -> true,
                null, plan -> 0, (def, idx) -> 0L));
        assertFalse(unavailable.chain().executable());
        assertNotEquals("recipe_search_limit", unavailable.failure().code());
    }

    private static WorkerResourceKey item(String id){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.parse(id));
    }

    private static WorkerRecipePlanner.Result poll(DeferredWorkScheduler scheduler,
                                                    Supplier<WorkerRecipePlanner.Result> request){
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while(System.nanoTime() < deadline){
            try{ return request.get(); }
            catch(DeferredWorkScheduler.Pending pending){
                scheduler.tick();
                LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
            }
        }
        fail("The deferred recipe request never completed");
        return null;
    }
}
