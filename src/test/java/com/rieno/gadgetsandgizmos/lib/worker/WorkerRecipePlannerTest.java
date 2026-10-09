package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRecipePlannerTest{
    private static final WorkerResourceKey LOG = item("oak_log");
    private static final WorkerResourceKey PLANK = item("oak_planks");
    private static final WorkerResourceKey BIRCH = item("birch_planks");
    private static final WorkerResourceKey TABLE = item("crafting_table");
    private static final WorkerResourceKey STICK = item("stick");

    @Test void reportsTheBlockedRecipeRouteOrIngredient(){
        WorkerResourceKey mechanism = item("precision_mechanism");
        WorkerResourceKey sheet = item("golden_sheet");
        var assembly = recipe("precision_assembly", mechanism, 1, ingredient(sheet, 1));
        var noMachine = WorkerRecipePlanner.planDetailed(mechanism, 1, Map.of(sheet, 1L),
                new WorkerRecipeIndex(List.of(assembly)), null, def -> false, plan -> false, null);
        assertFalse(noMachine.chain().executable());
        assertTrue(noMachine.details().stream().anyMatch(row -> row.contains("Machine route")
                && row.contains("precision_assembly")));
        var noSheet = WorkerRecipePlanner.planDetailed(mechanism, 1, Map.of(),
                new WorkerRecipeIndex(List.of(assembly)), null, def -> true, plan -> true, null);
        assertFalse(noSheet.chain().executable());
        assertTrue(noSheet.details().stream().anyMatch(row -> row.contains("golden_sheet")));
    }

    @Test void reportsBlockedPrerequisiteInsteadOfItsCraftableParent(){
        WorkerResourceKey raw = item("raw_material");
        WorkerResourceKey part = item("craftable_part");
        WorkerResourceKey result = item("finished_item");
        var process = recipe("process_raw", part, 1, ingredient(raw, 1));
        var assemble = recipe("assemble_parts", result, 1, ingredient(part, 1));
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L),
                new WorkerRecipeIndex(List.of(process, assemble)), null,
                def -> def != process, plan -> true, null);
        assertFalse(planned.chain().executable());
        assertTrue(planned.details().stream().anyMatch(detail -> detail.contains("Machine route")
                && detail.contains("process_raw")), planned.details().toString());
        assertTrue(planned.details().stream().noneMatch(detail -> detail.contains("craftable_part needs")));
    }

    @Test void reportsSharedStockShortageBeforeExploringAnImpossibleSchedule(){
        WorkerResourceKey mechanism = item("precision_mechanism");
        WorkerResourceKey sheet = item("golden_sheet");
        var assembly = recipe("precision_assembly", mechanism, 1,
                ingredient(sheet, 1), ingredient(sheet, 1));
        var result = WorkerRecipePlanner.planDetailed(mechanism, 1, Map.of(sheet, 1L),
                new WorkerRecipeIndex(List.of(assembly)), null, def -> true, plan -> true, null);
        assertFalse(result.chain().executable());
        assertTrue(result.details().stream().anyMatch(detail -> detail.contains("golden_sheet")
                && detail.contains("needs 2") && detail.contains("linked stock 1")), result.details().toString());
    }

    @Test void buildsSeveralDistinctMissingInputsFromSharedRawStock(){
        WorkerResourceKey rawMetal = item("raw_metal");
        WorkerResourceKey ingot = item("metal_ingot");
        WorkerResourceKey nugget = item("metal_nugget");
        WorkerResourceKey alloy = item("alloy");
        WorkerResourceKey shaft = item("shaft");
        WorkerResourceKey small = item("small_part");
        WorkerResourceKey large = item("large_part");
        WorkerResourceKey rawPrecious = item("raw_precious");
        WorkerResourceKey preciousIngot = item("precious_ingot");
        WorkerResourceKey sheet = item("precious_sheet");
        WorkerResourceKey result = item("assembled_result");
        List<WorkerRecipeDefinition.Ingredient> assemblyInputs = new java.util.ArrayList<>();
        for(int repeat = 0; repeat < 5; repeat++){
            assemblyInputs.add(ingredient(small, 1));
            assemblyInputs.add(ingredient(large, 1));
            assemblyInputs.add(ingredient(nugget, 1));
        }
        assemblyInputs.add(ingredient(sheet, 1));
        var definitions = List.of(
                recipe("smelt_metal", ingot, 1, ingredient(rawMetal, 1)),
                recipe("metal_nuggets", nugget, 9, ingredient(ingot, 1)),
                recipe("planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("alloy", alloy, 1, ingredient(nugget, 2)),
                recipe("shaft", shaft, 8, ingredient(alloy, 2)),
                recipe("small", small, 1, ingredient(shaft, 1), ingredient(PLANK, 1)),
                recipe("large", large, 1, ingredient(small, 1), ingredient(PLANK, 1)),
                recipe("smelt_precious", preciousIngot, 1, ingredient(rawPrecious, 1)),
                recipe("press", sheet, 1, ingredient(preciousIngot, 1)),
                recipe("assemble", result, 1, assemblyInputs.toArray(WorkerRecipeDefinition.Ingredient[]::new)));
        var planned = WorkerRecipePlanner.planDetailed(result, 1,
                Map.of(LOG, 10L, rawMetal, 15L, nugget, 4L, rawPrecious, 1L), definitions,
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message() + " " + planned.details());
        assertEquals(result, planned.chain().steps().getLast().plan().result());
    }

    @Test void plansRepeatedTaggedInputsAcrossSharedCraftingBranches(){
        WorkerResourceKey log = item("stored_log");
        WorkerResourceKey plank = item("stored_plank");
        WorkerResourceKey rawIron = item("stored_raw_iron");
        WorkerResourceKey iron = item("stored_iron");
        WorkerResourceKey nugget = item("stored_nugget");
        WorkerResourceKey alloy = item("stored_alloy");
        WorkerResourceKey andesite = item("stored_andesite");
        WorkerResourceKey shaft = item("stored_shaft");
        WorkerResourceKey cog = item("stored_cog");
        WorkerResourceKey largeCog = item("stored_large_cog");
        WorkerResourceKey rawGold = item("stored_raw_gold");
        WorkerResourceKey gold = item("stored_gold");
        WorkerResourceKey sheet = item("stored_sheet");
        WorkerResourceKey result = item("stored_result");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        List<WorkerResourceKey> plankTag = new java.util.ArrayList<>();
        for(int idx = 0; idx < 32; idx++){
            WorkerResourceKey otherPlank = item("unstocked_plank_" + idx);
            plankTag.add(otherPlank);
            definitions.add(recipe("unstocked_planks_" + idx, otherPlank, 4,
                    ingredient(item("unstocked_log_" + idx), 1)));
        }
        plankTag.add(plank);
        definitions.add(recipe("stored_planks", plank, 4, ingredient(log, 1)));
        definitions.add(new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("stored_smelting_iron"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(ingredient(rawIron, 1)), iron, 1));
        definitions.add(recipe("stored_nuggets", nugget, 9, ingredient(iron, 1)));
        definitions.add(new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("stored_mixing_alloy"),
                ResourceLocation.withDefaultNamespace("mixing"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(ingredient(andesite, 2), ingredient(nugget, 2)), alloy, 1));
        definitions.add(recipe("stored_shaft", shaft, 8, ingredient(alloy, 2)));
        definitions.add(recipe("stored_cog", cog, 1, ingredient(shaft, 1),
                new WorkerRecipeDefinition.Ingredient(plankTag, 1)));
        definitions.add(recipe("stored_large_cog", largeCog, 1, ingredient(cog, 1),
                new WorkerRecipeDefinition.Ingredient(plankTag, 1)));
        definitions.add(new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("stored_smelting_gold"),
                ResourceLocation.withDefaultNamespace("smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(ingredient(rawGold, 1)), gold, 1));
        definitions.add(new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("stored_press_gold"),
                ResourceLocation.withDefaultNamespace("pressing"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(ingredient(gold, 1)), sheet, 1));
        for(int loops = 1; loops <= 5; loops++){
            List<WorkerRecipeDefinition.Ingredient> stages = new java.util.ArrayList<>();
            for(int idx = 0; idx < loops; idx++){
                stages.add(ingredient(cog, 1));
                stages.add(ingredient(largeCog, 1));
                stages.add(ingredient(nugget, 1));
            }
            stages.add(ingredient(sheet, 1));
            List<WorkerRecipeDefinition> candidateRecipes = new java.util.ArrayList<>(definitions);
            candidateRecipes.add(new WorkerRecipeDefinition(
                    ResourceLocation.withDefaultNamespace("stored_assembly"),
                    ResourceLocation.withDefaultNamespace("processing_machine"),
                    WorkerRecipePlan.Operation.PROCESSING, stages, result, 1));
            var planned = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                    WorkerRecipePlanner.planDetailed(result, 1,
                            Map.of(log, 64L, rawIron, 64L, rawGold, 64L, andesite, 64L, nugget, 4L),
                            candidateRecipes, null, candidate -> true));
            assertTrue(planned.chain().executable(), "loops=" + loops + " " + planned.failure().message()
                    + " " + planned.details());
            assertTrue(planned.chain().steps().stream().anyMatch(step ->
                    step.plan().processorType().getPath().equals("mixing")));
            assertTrue(planned.chain().steps().stream().anyMatch(step ->
                    step.plan().processorType().getPath().equals("pressing")));
        }
    }

    // Installed machine ingredients cover only their own recipe slots and requested quantity
    @Test void plansOnlyMissingWorkpieceWhenAssemblyToolsAreLoaded(){
        WorkerResourceKey cog = item("cogwheel");
        WorkerResourceKey large = item("large_cogwheel");
        WorkerResourceKey nugget = item("iron_nugget");
        WorkerResourceKey sheet = item("golden_sheet");
        WorkerResourceKey mechanism = item("precision_mechanism");
        var assembly = recipe("precision_assembly", mechanism, 1,
                ingredient(cog, 1), ingredient(large, 1), ingredient(nugget, 1), ingredient(sheet, 1));
        var stock = (WorkerRecipePlanner.IngredientStock)(def, idx) -> def == assembly && idx < 3 ? 1L : 0L;
        var planned = WorkerRecipePlanner.planDetailed(mechanism, 1, Map.of(sheet, 1L),
                new WorkerRecipeIndex(List.of(assembly)), null, def -> true, plan -> true,
                null, plan -> 0, stock);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(List.of(mechanism), planned.chain().steps().stream()
                .map(step -> step.plan().result()).toList());
        WorkerResourceKey ingot = item("gold_ingot");
        var press = recipe("press_gold", sheet, 1, ingredient(ingot, 1));
        var nested = WorkerRecipePlanner.planDetailed(mechanism, 1, Map.of(ingot, 1L),
                new WorkerRecipeIndex(List.of(press, assembly)), null, def -> true, plan -> true,
                null, plan -> 0, stock);
        assertEquals(List.of(sheet, mechanism), nested.chain().steps().stream()
                .map(step -> step.plan().result()).toList());
        var second = WorkerRecipePlanner.planDetailed(mechanism, 2, Map.of(sheet, 2L),
                new WorkerRecipeIndex(List.of(assembly)), null, def -> true, plan -> true,
                null, plan -> 0, stock);
        assertFalse(second.chain().executable());
    }

    // Choose a stocked crafting recipe before an equally direct machine-processing route
    @Test void prefersCraftingOverProcessingForTheSameResult(){
        WorkerResourceKey slab = item("oak_slab");
        var saw = new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("a_saw"),
                ResourceLocation.withDefaultNamespace("cutting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(ingredient(PLANK, 1)), slab, 2);
        var crafting = new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("z_craft"),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(ingredient(PLANK, 3)), slab, 6);
        var result = WorkerRecipePlanner.planDetailed(slab, 6, Map.of(PLANK, 3L),
                List.of(saw, crafting), null, plan -> true);
        assertEquals(crafting.recipeId(), result.chain().steps().getLast().plan().recipeId());
        var machineOnly = WorkerRecipePlanner.planDetailed(slab, 6, Map.of(PLANK, 3L),
                List.of(saw, crafting), null, plan -> plan.operation() == WorkerRecipePlan.Operation.PROCESSING);
        assertEquals(saw.recipeId(), machineOnly.chain().steps().getLast().plan().recipeId());
    }

    @Test void prefersEquippedPortableGridOverMechanicalCrafterForSameRecipe(){
        WorkerResourceKey slab = item("oak_slab");
        WorkerResourceKey terminal = item("wireless_crafting_terminal");
        var mechanical = new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("slab"),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(ingredient(PLANK, 3)), slab, 6);
        var portable = new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace("slab"),
                ResourceLocation.withDefaultNamespace("wireless_crafting_terminal"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(ingredient(PLANK, 3), ingredient(terminal, 1)), slab, 6);
        var result = WorkerRecipePlanner.planDetailed(slab, 6,
                Map.of(PLANK, 3L, terminal, 1L), List.of(mechanical, portable), null, plan -> true);
        assertEquals(WorkerRecipePlan.Operation.WORKER_CRAFTING,
                result.chain().steps().getLast().plan().operation());
    }

    // Prefer a stocked crafting route over a shorter route through shared transport
    @Test void prefersUngatedRecipeChain(){
        var shared = recipe("a_shared_line", TABLE, 1, ingredient(LOG, 1));
        var planks = recipe("planks_for_table", PLANK, 4, ingredient(LOG, 1));
        var crafting = recipe("z_crafting_table", TABLE, 1, ingredient(PLANK, 4));
        var result = WorkerRecipePlanner.planDetailed(TABLE, 1, Map.of(LOG, 1L),
                new WorkerRecipeIndex(List.of(shared, planks, crafting)), null,
                def -> true, plan -> true, null,
                plan -> plan.recipeId().equals(shared.recipeId()) ? 8 : 0);
        assertTrue(result.chain().executable());
        assertEquals(List.of(planks.recipeId(), crafting.recipeId()), result.chain().steps().stream()
                .map(step -> step.plan().recipeId()).toList());
    }

    @Test void prefersDirectStockedLogCraftOverEarlierProcessingChain(){
        WorkerResourceKey chips = item("wood_chips");
        var crush = recipe("a_crush_logs", chips, 4, ingredient(LOG, 1));
        var pack = recipe("a_pack_chips", PLANK, 4, ingredient(chips, 4));
        var direct = recipe("z_craft_planks", PLANK, 4, ingredient(LOG, 1));
        var chain = plan(PLANK, 4, Map.of(LOG, 1L), List.of(crush, pack, direct));
        assertEquals(List.of(direct.recipeId()), chain.steps().stream()
                .map(step -> step.plan().recipeId()).toList());
    }

    // Prefer a stocked route through a large plank tag before exploring unavailable variants
    @Test void buildsCogwheelFromLogsBeforeUnavailableChipPlanks(){
        WorkerResourceKey chips = item("wood_chip");
        WorkerResourceKey alloy = item("andesite_alloy");
        WorkerResourceKey shaft = item("shaft");
        List<WorkerResourceKey> planks = new java.util.ArrayList<>();
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        for(int idx = 0; idx < 64; idx++){
            WorkerResourceKey variant = item("chip_plank_" + idx);
            planks.add(variant);
            definitions.add(recipe("chip_block_" + idx, variant, 1, ingredient(chips, 4)));
        }
        planks.add(PLANK);
        for(int idx = 0; idx < 256; idx++){
            definitions.add(recipe("chip_source_" + idx, chips, 4,
                    ingredient(item("unavailable_" + idx), 1)));
        }
        definitions.add(recipe("oak_planks", PLANK, 4, ingredient(LOG, 1)));
        definitions.add(recipe("shaft", shaft, 8, ingredient(alloy, 2)));
        definitions.add(recipe("cogwheel", TABLE, 1,
                ingredient(shaft, 1), new WorkerRecipeDefinition.Ingredient(planks, 1)));
        var chain = plan(TABLE, 1, Map.of(LOG, 1L, alloy, 2L), definitions);
        assertTrue(chain.executable());
        assertEquals(List.of("shaft", "oak_planks", "cogwheel"), chain.steps().stream()
                .map(step -> step.plan().recipeId().getPath()).toList());
    }

    // Follow the stocked inputs through Create's full cogwheel ingredient chain
    @Test void buildsCogwheelFromLogsAndIronIngots(){
        WorkerResourceKey nugget = item("iron_nugget");
        WorkerResourceKey ingot = item("iron_ingot");
        WorkerResourceKey andesite = item("andesite");
        WorkerResourceKey alloy = item("andesite_alloy");
        WorkerResourceKey shaft = item("shaft");
        WorkerResourceKey cogwheel = item("cogwheel");
        var chain = plan(cogwheel, 1, Map.of(LOG, 1L, ingot, 1L, andesite, 4L), List.of(
                recipe("oak_planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("iron_nugget", nugget, 9, ingredient(ingot, 1)),
                recipe("andesite_alloy", alloy, 1, ingredient(andesite, 2), ingredient(nugget, 2)),
                recipe("shaft", shaft, 8, ingredient(alloy, 2)),
                recipe("cogwheel", cogwheel, 1, ingredient(shaft, 1),
                        new WorkerRecipeDefinition.Ingredient(List.of(BIRCH, PLANK), 1))));
        assertEquals(List.of(nugget, alloy, shaft, PLANK, cogwheel), chain.steps().stream()
                .map(step -> step.plan().result()).toList());
    }

    // Skip tag members trapped in unstocked recipe cycles before planning a reachable member
    @Test void resolvesTaggedIngredientPastUnstockedCycles(){
        WorkerResourceKey result = item("tagged_result");
        List<WorkerResourceKey> members = new java.util.ArrayList<>();
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        for(int member = 0; member < 80; member++){
            WorkerResourceKey missing = item("cyclic_member_" + member);
            members.add(missing);
            for(int recipe = 0; recipe < 150; recipe++)
                definitions.add(recipe("cyclic_recipe_" + member + "_" + recipe, missing, 1,
                        ingredient(missing, 1)));
        }
        members.add(PLANK);
        definitions.add(recipe("planks", PLANK, 4, ingredient(LOG, 1)));
        definitions.add(recipe("tagged_result", result, 1,
                new WorkerRecipeDefinition.Ingredient(members, 4)));

        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(LOG, 1L), definitions,
                WorkerRecipePlan.Operation.CRAFTING, plan -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals(List.of(PLANK, result), planned.chain().steps().stream()
                .map(step -> step.plan().result()).toList());
    }

    // A tag is stocked when its amount is split between accepted item ids
    @Test void acceptsTagStockSplitAcrossMembersWithoutProductionRecipes(){
        var result = recipe("split_tag_result", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 8));
        var chain = plan(TABLE, 1, Map.of(PLANK, 4L, BIRCH, 4L), List.of(result));
        assertTrue(chain.executable());
        assertEquals(1, chain.steps().size());
    }

    @Test void craftsOnlyTheMissingPartOfStockSplitAcrossTagMembers(){
        WorkerResourceKey raw = item("tag_member_raw");
        WorkerResourceKey result = item("tagged_assembly");
        var tagged = new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 5);
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(PLANK, 4L, raw, 1L), List.of(
                recipe("make_birch", BIRCH, 1, ingredient(raw, 1)),
                recipe("tagged_assembly", result, 1, tagged)),
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message() + " " + planned.details());
        assertEquals(List.of(BIRCH, result), planned.chain().steps().stream()
                .map(step -> step.plan().result()).toList());
    }

    // Resolve tags at successive recipe levels until a stocked raw ingredient is found
    @Test void resolvesNestedTaggedIngredientsFromStock(){
        WorkerResourceKey otherShaft = item("other_shaft");
        var chain = plan(TABLE, 1, Map.of(LOG, 1L), List.of(
                recipe("planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("sticks", STICK, 4,
                        new WorkerRecipeDefinition.Ingredient(List.of(BIRCH, PLANK), 2)),
                recipe("assembled", TABLE, 1,
                        new WorkerRecipeDefinition.Ingredient(List.of(otherShaft, STICK), 1))));
        assertEquals(List.of(PLANK, STICK, TABLE), chain.steps().stream()
                .map(step -> step.plan().result()).toList());
    }

    // A recipe route with one raw input cannot satisfy two requested tagged ingredients
    @Test void skipsQuantityStarvedTagVariantsBeforeTheStockedRoute(){
        WorkerResourceKey scarce = item("scarce_raw");
        WorkerResourceKey sufficient = item("sufficient_raw");
        WorkerResourceKey viable = item("viable_variant");
        WorkerResourceKey result = item("assembled_result");
        List<WorkerResourceKey> alternatives = new java.util.ArrayList<>();
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        for(int variant = 0; variant < 64; variant++){
            WorkerResourceKey resource = item("starved_variant_" + variant);
            alternatives.add(resource);
            for(int recipe = 0; recipe < 256; recipe++){
                definitions.add(recipe("starved_" + variant + "_" + recipe, resource, 1,
                        ingredient(scarce, 1)));
            }
        }
        alternatives.add(viable);
        definitions.add(recipe("viable", viable, 1, ingredient(sufficient, 1)));
        definitions.add(recipe("assemble", result, 1,
                new WorkerRecipeDefinition.Ingredient(alternatives, 2)));
        var chain = plan(result, 1, Map.of(scarce, 1L, sufficient, 2L), definitions);
        assertTrue(chain.executable());
        assertEquals(viable, chain.steps().getFirst().plan().result());
    }

    // Reach a valid mixed processing route even when its total work exceeds the recursion depth
    @Test void ranksBroadProcessingChainsBeforeUnseededAlternatives(){
        WorkerResourceKey unavailable = item("unavailable_component");
        WorkerResourceKey viable = item("viable_component");
        WorkerResourceKey result = item("assembled_component");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        List<WorkerResourceKey> alternatives = new java.util.ArrayList<>();
        for(int idx = 0; idx < 64; idx++){
            WorkerResourceKey variant = item("missing_component_" + idx);
            alternatives.add(variant);
            definitions.add(recipe("missing_variant_" + idx, variant, 1, ingredient(unavailable, 1)));
        }
        for(int idx = 0; idx < 256; idx++){
            definitions.add(recipe("missing_source_" + idx, unavailable, 1,
                    ingredient(item("missing_material_" + idx), 1)));
        }
        List<WorkerRecipeDefinition.Ingredient> inputs = new java.util.ArrayList<>();
        Map<WorkerResourceKey, Long> stock = new java.util.HashMap<>();
        for(int branch = 0; branch < 9; branch++){
            WorkerResourceKey previous = item("raw_material_" + branch);
            stock.put(previous, 1L);
            for(int stage = 0; stage < 8; stage++){
                WorkerResourceKey output = item("processed_" + branch + "_" + stage);
                definitions.add(new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace(
                        "process_" + branch + "_" + stage), ResourceLocation.withDefaultNamespace("processing"),
                        WorkerRecipePlan.Operation.PROCESSING, List.of(ingredient(previous, 1)), output, 1));
                previous = output;
            }
            inputs.add(ingredient(previous, 1));
        }
        definitions.add(recipe("assemble_viable", viable, 1,
                inputs.toArray(WorkerRecipeDefinition.Ingredient[]::new)));
        alternatives.add(viable);
        definitions.add(recipe("assemble_final", result, 1,
                new WorkerRecipeDefinition.Ingredient(alternatives, 1)));
        var chain = plan(result, 1, stock, definitions);
        assertTrue(chain.executable());
        assertEquals(viable, chain.steps().get(chain.steps().size() - 2).plan().result());
    }

    @Test void triesStockedAlternativeMachineRecipeWhenFirstInputIsAbsent(){
        WorkerResourceKey ore = item("iron_ore");
        WorkerResourceKey raw = item("raw_iron");
        WorkerResourceKey ingot = item("iron_ingot");
        WorkerResourceKey block = item("iron_block");
        var fromOre = recipe("a_smelt_ore", ingot, 1, ingredient(ore, 1));
        var fromRaw = recipe("z_smelt_raw", ingot, 1, ingredient(raw, 1));
        var compact = recipe("compact", block, 1, ingredient(ingot, 9));
        var chain = plan(block, 1, Map.of(raw, 9L), List.of(fromOre, fromRaw, compact));
        assertEquals(List.of(fromRaw.recipeId(), compact.recipeId()), chain.steps().stream()
                .map(step -> step.plan().recipeId()).toList());
    }

    @Test void constrainsOnlyTheFinalRecipeToTheSelectedMachine(){
        var wrong = recipe("a_wrong_machine", TABLE, 1, ingredient(LOG, 1));
        var chosen = recipe("z_selected_machine", TABLE, 1, ingredient(PLANK, 4));
        var planks = recipe("planks", PLANK, 4, ingredient(LOG, 1));
        var result = WorkerRecipePlanner.planDetailed(TABLE, 1, Map.of(LOG, 1L),
                List.of(wrong, chosen, planks), null, plan -> true,
                plan -> plan.recipeId().equals(chosen.recipeId()));
        assertTrue(result.chain().executable());
        assertEquals(List.of(planks.recipeId(), chosen.recipeId()), result.chain().steps().stream()
                .map(step -> step.plan().recipeId()).toList());
    }

    // Select craftable tag alternatives instead of stopping at the first unstocked item
    @Test void buildsTableFromLogsThroughIngredientAlternatives(){
        var planks = recipe("planks", PLANK, 4, ingredient(LOG, 1));
        var table = recipe("table", TABLE, 1, new WorkerRecipeDefinition.Ingredient(List.of(BIRCH, PLANK), 4));
        var chain = plan(TABLE, 1, Map.of(LOG, 1L), List.of(table, planks));
        assertEquals(List.of(PLANK, TABLE), chain.steps().stream().map(step -> step.plan().result()).toList());
        assertEquals(4, chain.steps().getFirst().requestedAmount());
        assertEquals(PLANK, chain.steps().getLast().plan().inputs().getFirst().resource());
    }

    // One tagged ingredient can consume stock split across interchangeable item ids
    @Test void reservesSplitTaggedStockWithoutInventingProduction(){
        var recipe = recipe("tagged", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 1));
        var chain = plan(TABLE, 8, Map.of(PLANK, 4L, BIRCH, 4L), List.of(recipe));
        assertTrue(chain.executable());
        assertEquals(1, chain.steps().size());
        assertEquals(8L, chain.steps().getFirst().requestedAmount());
    }

    // Leave the exact sibling ingredient available while splitting a tagged ingredient
    @Test void backtracksSplitTagsAroundExactSibling(){
        var recipe = recipe("tagged_exact", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 6), ingredient(PLANK, 2));
        var chain = plan(TABLE, 1, Map.of(PLANK, 4L, BIRCH, 4L), List.of(recipe));
        assertTrue(chain.executable());
        assertEquals(List.of(BIRCH, PLANK), chain.steps().getFirst().plan().inputs().stream()
                .map(WorkerRecipePlan.Input::resource).toList());
    }

    // A machine may support only one item from a recipe's tagged input
    @Test void checksMachineSupportForIngredientAlternatives(){
        var tagged = recipe("tagged_machine", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 1));
        assertTrue(WorkerRecipeCatalog.supported(null, tagged,
                plan -> plan.inputs().getFirst().resource().equals(BIRCH)));
    }

    // Keep accepted variants in saved plans so a repaired chain may produce either item
    @Test void repairsMissingIngredientThroughSavedAlternative(){
        var root = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 1, List.of(PLANK, BIRCH))), TABLE, 1);
        var saved = WorkerRecipePlan.fromTag(root.toTag());
        var chain = WorkerRecipePlanner.prerequisites(saved, 0, 0, Map.of(LOG, 1L),
                List.of(recipe("birch", BIRCH, 1, ingredient(LOG, 1))), plan -> true);
        assertEquals(List.of(PLANK, BIRCH), saved.inputs().getFirst().alternatives());
        assertTrue(chain.executable());
        assertEquals(BIRCH, chain.steps().getFirst().plan().result());
    }

    @Test void repairsLegacyPlanUsingLiveRecipeAlternatives(){
        var root = recipe("legacy_root", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 1));
        var saved = new WorkerRecipePlan(root.recipeId(), root.processorType(), root.operation(),
                List.of(new WorkerRecipePlan.Input(PLANK, 1)), TABLE, 1);
        var chain = WorkerRecipePlanner.prerequisites(saved, 0, 0, Map.of(LOG, 1L),
                List.of(root, recipe("birch", BIRCH, 1, ingredient(LOG, 1))), plan -> true);
        assertTrue(chain.executable());
        assertEquals(BIRCH, chain.steps().getFirst().plan().result());
    }

    // Resolve a long prerequisite tree from the only raw resource in storage
    @Test void repairsTwelveMissingCraftingLevels(){
        List<WorkerRecipeDefinition> recipes = new java.util.ArrayList<>();
        WorkerResourceKey input = LOG;
        for(int idx = 0; idx < 12; idx++){
            WorkerResourceKey output = item("stage_" + idx);
            recipes.add(recipe("recipe_" + idx, output, 1, ingredient(input, 1)));
            input = output;
        }
        var root = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING, List.of(new WorkerRecipePlan.Input(input, 1)), TABLE, 1);
        var chain = WorkerRecipePlanner.prerequisites(root, 0, 0, Map.of(LOG, 64L), recipes, plan -> true);
        assertEquals(12, chain.steps().size());
        assertEquals(LOG, chain.steps().getFirst().plan().inputs().getFirst().resource());
        assertEquals(input, chain.steps().getLast().plan().result());
    }

    // Preserve consumed ingredients when repairing a recipe partway through collection
    @Test void repairsOnlyUnconsumedIngredients(){
        var root = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(STICK, 2), new WorkerRecipePlan.Input(PLANK, 6)), TABLE, 1);
        var chain = WorkerRecipePlanner.prerequisites(root, 1, 2, Map.of(LOG, 1L),
                List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1))), plan -> true);
        assertEquals(1, chain.steps().size());
        assertEquals(4, chain.steps().getFirst().requestedAmount());
        assertEquals(PLANK, chain.steps().getFirst().plan().result());
    }

    // Repair the whole current visit instead of scheduling one missing ingredient per craft
    @Test void repairsAllBatchesOfMissingInput(){
        var root = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 4)), TABLE, 1);
        var chain = WorkerRecipePlanner.prerequisites(root, 0, 0L, 8L, Map.of(LOG, 8L),
                List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1))), plan -> true);
        assertEquals(1, chain.steps().size());
        assertEquals(32L, chain.steps().getFirst().requestedAmount());
    }

    // Reserve existing stock across every remaining input before accepting a repair
    @Test void rejectsIncompletePrerequisiteRepairs(){
        var root = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 4), new WorkerRecipePlan.Input(BIRCH, 1)), TABLE, 1);
        assertFalse(WorkerRecipePlanner.prerequisites(root, 0, 0, Map.of(LOG, 1L),
                List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1))), plan -> true).executable());
    }

    // Subtract stored inputs once and craft only the missing portion
    @Test void completesPartiallyStockedIngredient(){
        var chain = plan(TABLE, 1, Map.of(PLANK, 2L, LOG, 1L), List.of(
                recipe("planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("table", TABLE, 1, ingredient(PLANK, 4))));
        assertTrue(chain.executable());
        assertEquals(2, chain.steps().size());
    }

    // Reserve shared ingredients across nested branches without double spending stock
    @Test void reservesSharedNestedDependencies(){
        var tool = item("wooden_pickaxe");
        var recipes = List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("sticks", STICK, 4, ingredient(PLANK, 2)),
                recipe("tool", tool, 1, ingredient(STICK, 2), ingredient(PLANK, 3)));
        assertFalse(plan(tool, 1, Map.of(LOG, 1L), recipes).executable());
        var chain = plan(tool, 1, Map.of(LOG, 2L), recipes);
        assertTrue(chain.executable());
        assertEquals(tool, chain.steps().getLast().plan().result());
        assertEquals(1, chain.steps().stream().filter(step -> step.plan().result().equals(PLANK)).count());
        assertEquals(8, chain.steps().getFirst().requestedAmount());
        assertEquals(List.of(PLANK, STICK, tool), chain.steps().stream()
                .map(step -> step.plan().result()).toList());
    }

    // Total raw materials across separate copper branches before scheduling dependent stages
    @Test void groupsSharedRawProcessingAcrossBranches(){
        var raw = item("raw_copper");
        var ingot = item("copper_ingot");
        var sheet = item("copper_sheet");
        var pipe = item("copper_pipe");
        var thruster = item("thruster");
        var chain = plan(thruster, 4, Map.of(raw, 40L), List.of(
                recipe("smelt", ingot, 1, ingredient(raw, 1)),
                recipe("press", sheet, 1, ingredient(ingot, 1)),
                recipe("pipe", pipe, 1, ingredient(ingot, 2)),
                recipe("thruster", thruster, 1, ingredient(sheet, 2), ingredient(pipe, 1), ingredient(ingot, 1))));
        assertTrue(chain.executable());
        assertEquals(List.of(ingot, sheet, pipe, thruster), chain.steps().stream()
                .map(step -> step.plan().result()).toList());
        assertEquals(20L, chain.steps().getFirst().requestedAmount());
        assertEquals(8L, chain.steps().get(1).requestedAmount());
        assertEquals(4L, chain.steps().get(2).requestedAmount());
        assertEquals(4L, chain.steps().getLast().requestedAmount());
    }

    // Fit an entire production visit into the smallest required input and output route
    @Test void sizesRecipeVisitsFromAllIngredients(){
        var plan = new WorkerRecipePlan(TABLE.id(), ResourceLocation.withDefaultNamespace("crafting"),
                WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(PLANK, 4), new WorkerRecipePlan.Input(STICK, 2)), TABLE, 1);
        assertEquals(8L, plan.batchesFor(16L, Map.of(PLANK, 64L, STICK, 16L), 27L));
        assertEquals(1L, plan.batchesFor(16L, Map.of(PLANK, 4L, STICK, 16L), 27L));
    }

    // Finish all production before the transfer order delivers a large request
    @Test void stagesLargeFinalRequestBeforeDelivery(){
        var chain = plan(PLANK, 12L, Map.of(LOG, 3L), List.of(
                recipe("planks", PLANK, 4, ingredient(LOG, 1))));
        UUID id = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var orders = chain.orders(id, "Planks", 0, player, null);
        assertEquals(2, orders.size());
        assertNull(orders.getFirst().destinationEndpointId());
        assertEquals(12L, orders.getFirst().task().requestedAmount());
        assertEquals(id, orders.getLast().id());
        assertEquals(WorkerWorkOrder.Mode.TRANSFER, orders.getLast().mode());
        assertEquals(player, orders.getLast().destinationEndpointId());
    }

    // Reconsider an earlier ingredient choice when it blocks a later exact ingredient
    @Test void backtracksReservationsAcrossSiblingIngredients(){
        var recipe = recipe("mixed", TABLE, 1,
                new WorkerRecipeDefinition.Ingredient(List.of(PLANK, BIRCH), 1), ingredient(PLANK, 1));
        var chain = plan(TABLE, 1, Map.of(PLANK, 1L, BIRCH, 1L), List.of(recipe));
        assertTrue(chain.executable());
        assertEquals(List.of(BIRCH, PLANK), chain.steps().getFirst().plan().inputs().stream()
                .map(WorkerRecipePlan.Input::resource).toList());
    }

    // Discard failed recipe reservations before considering the next recipe
    @Test void backtracksRecipeBranches(){
        var chain = plan(TABLE, 1, Map.of(LOG, 1L), List.of(
                recipe("a_bad", TABLE, 1, ingredient(LOG, 1), ingredient(BIRCH, 1)),
                recipe("b_good", TABLE, 1, ingredient(LOG, 1))));
        assertTrue(chain.executable());
        assertEquals("b_good", chain.steps().getFirst().plan().recipeId().getPath());
    }

    // Revisit a nested recipe when a sibling needs its first choice of raw material
    @Test void backtracksNestedRecipesForLaterSiblings(){
        var chain = plan(TABLE, 1, Map.of(LOG, 1L, BIRCH, 1L), List.of(
                recipe("a_stick", STICK, 1, ingredient(LOG, 1)),
                recipe("b_stick", STICK, 1, ingredient(BIRCH, 1)),
                recipe("table", TABLE, 1, ingredient(STICK, 1), ingredient(LOG, 1))));
        assertTrue(chain.executable());
        assertEquals("b_stick", chain.steps().getFirst().plan().recipeId().getPath().toString());
    }

    // Reject cycles that cannot be seeded from real storage
    @Test void rejectsUnseededCycles(){
        assertFalse(plan(PLANK, 1, Map.of(), List.of(
                recipe("a", PLANK, 4, ingredient(STICK, 1)),
                recipe("b", STICK, 2, ingredient(PLANK, 1)))).executable());
    }

    // Follow the indexed ingredient links from stocked logs through planks
    @Test void followsIndexedStockThroughTwoRecipeLevels(){
        var planned = WorkerRecipePlanner.planDetailed(TABLE, 1, Map.of(LOG, 1L), List.of(
                recipe("oak_planks", PLANK, 4, ingredient(LOG, 1)),
                recipe("crafting_table", TABLE, 1, ingredient(PLANK, 1), ingredient(PLANK, 1),
                        ingredient(PLANK, 1), ingredient(PLANK, 1))),
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
    }

    // Reuse one recipe batch across different dependent outputs before checking terminal stock
    @Test void reusesSharedIntermediateYieldAcrossBranches(){
        WorkerResourceKey raw = item("shared_batch_raw");
        WorkerResourceKey batch = item("shared_batch");
        WorkerResourceKey left = item("shared_left");
        WorkerResourceKey right = item("shared_right");
        WorkerResourceKey result = item("shared_result");
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L), List.of(
                recipe("batch", batch, 2, ingredient(raw, 1)),
                recipe("left", left, 1, ingredient(batch, 1)),
                recipe("right", right, 1, ingredient(batch, 1)),
                recipe("result", result, 1, ingredient(left, 1), ingredient(right, 1))),
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
    }

    // Reject many recycling routes that cannot increase the one stocked raw input
    @Test void rejectsQuantityStarvedCyclesWithoutExhaustingSearch(){
        WorkerResourceKey raw = item("limited_raw");
        WorkerResourceKey material = item("recycled_material");
        WorkerResourceKey result = item("assembled_result");
        List<WorkerRecipeDefinition> definitions = recyclingRecipes(raw, material, result);
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L), definitions,
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertFalse(planned.chain().executable());
        assertNotEquals("recipe_search_limit", planned.failure().code(), planned.failure().message());
        var fast = stockPlan(result, Map.of(raw, 1L), definitions);
        assertFalse(fast.chain().executable());
        assertNotEquals("recipe_search_limit", fast.failure().code(), fast.failure().message());
    }

    // Reach a stocked route after rejecting the same large set of cyclic alternatives
    @Test void findsViableRecipeBeyondQuantityStarvedCycles(){
        WorkerResourceKey raw = item("limited_raw");
        WorkerResourceKey material = item("recycled_material");
        WorkerResourceKey result = item("assembled_result");
        WorkerResourceKey sufficient = item("sufficient_raw");
        List<WorkerRecipeDefinition> definitions = recyclingRecipes(raw, material, result);
        WorkerResourceKey previous = sufficient;
        for(int idx = 0; idx < 4; idx++){
            WorkerResourceKey output = item("viable_stage_" + idx);
            definitions.add(recipe("viable_stage_" + idx, output, 1, ingredient(previous, 1)));
            previous = output;
        }
        definitions.add(recipe("viable_material", material, 1, ingredient(previous, 1)));
        var planned = WorkerRecipePlanner.planDetailed(result, 1,
                Map.of(raw, 1L, sufficient, 2L), definitions,
                WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals("viable_material", planned.chain().steps().get(4).plan().recipeId().getPath());
        assertTrue(stockPlan(result, Map.of(raw, 1L, sufficient, 2L), definitions).chain().executable());
    }

    // Try an independent stocked route after many recipes compete for the same insufficient input
    @Test void findsStockedRouteBeyondSharedInputDeadEnds(){
        WorkerResourceKey raw = item("shared_raw");
        WorkerResourceKey reserved = item("independent_raw");
        WorkerResourceKey result = item("branched_result");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        for(int outer = 0; outer < 256; outer++){
            WorkerResourceKey left = item("left_" + outer);
            WorkerResourceKey right = item("right_" + outer);
            definitions.add(recipe("trap_" + outer, result, 1,
                    ingredient(left, 1), ingredient(right, 1)));
            definitions.add(recipe("right_" + outer, right, 1, ingredient(raw, 1)));
            for(int inner = 0; inner < 64; inner++){
                definitions.add(recipe("left_" + outer + "_" + inner, left, 1, ingredient(raw, 1)));
            }
        }
        WorkerResourceKey previous = reserved;
        for(int idx = 0; idx < 4; idx++){
            WorkerResourceKey output = item("stocked_stage_" + idx);
            definitions.add(recipe("stocked_stage_" + idx, output, 1, ingredient(previous, 1)));
            previous = output;
        }
        definitions.add(recipe("stocked_result", result, 1, ingredient(previous, 1)));
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L, reserved, 1L),
                definitions, WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals("stocked_result", planned.chain().steps().getLast().plan().recipeId().getPath());
        assertTrue(stockPlan(result, Map.of(raw, 1L, reserved, 1L), definitions).chain().executable());
    }

    // Ignore unavailable tag members when counting stock shared by many candidate recipes
    @Test void findsRouteBeyondQuantityStarvedTaggedInputs(){
        WorkerResourceKey raw = item("tagged_shared_raw");
        WorkerResourceKey absent = item("tagged_absent_raw");
        WorkerResourceKey reserved = item("tagged_independent_raw");
        WorkerResourceKey result = item("tagged_branched_result");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        WorkerResourceKey cycle = item("tagged_cycle");
        definitions.add(recipe("tagged_cycle_first", absent, 1, ingredient(cycle, 1)));
        definitions.add(recipe("tagged_cycle_second", cycle, 1, ingredient(absent, 1)));
        WorkerRecipeDefinition.Ingredient tagged = new WorkerRecipeDefinition.Ingredient(
                List.of(raw, absent), 1);
        for(int outer = 0; outer < 256; outer++){
            WorkerResourceKey left = item("tagged_left_" + outer);
            WorkerResourceKey right = item("tagged_right_" + outer);
            definitions.add(recipe("tagged_trap_" + outer, result, 1,
                    ingredient(left, 1), ingredient(right, 1)));
            definitions.add(recipe("tagged_right_" + outer, right, 1, tagged));
            for(int inner = 0; inner < 64; inner++){
                definitions.add(recipe("tagged_left_" + outer + "_" + inner, left, 1, tagged));
            }
        }
        WorkerResourceKey previous = reserved;
        for(int idx = 0; idx < 4; idx++){
            WorkerResourceKey output = item("tagged_stocked_stage_" + idx);
            definitions.add(recipe("tagged_stocked_stage_" + idx, output, 1, ingredient(previous, 1)));
            previous = output;
        }
        definitions.add(recipe("tagged_stocked_result", result, 1, ingredient(previous, 1)));
        var planned = WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L, reserved, 1L),
                definitions, WorkerRecipePlan.Operation.CRAFTING, candidate -> true);
        assertTrue(planned.chain().executable(), planned.failure().message());
        assertEquals("tagged_stocked_result", planned.chain().steps().getLast().plan().recipeId().getPath());
        assertTrue(stockPlan(result, Map.of(raw, 1L, reserved, 1L), definitions).chain().executable());
    }

    private static WorkerRecipePlanner.Result stockPlan(WorkerResourceKey output,
                                                          Map<WorkerResourceKey, Long> available,
                                                          List<WorkerRecipeDefinition> definitions){
        return WorkerRecipePlanner.planFromStock(output, 1L, available, new WorkerRecipeIndex(definitions), null,
                def -> true, candidate -> true, null, candidate -> 0, (def, idx) -> 0L);
    }

    // Resolve a dense acyclic recipe graph without visiting every equivalent path
    @Test void resolvesSharedRecipeDependenciesWithinTickBudget(){
        WorkerResourceKey raw = item("dense_raw");
        WorkerResourceKey result = item("dense_result");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        List<WorkerResourceKey> previous = List.of(raw);
        for(int depth = 0; depth < 9; depth++){
            List<WorkerResourceKey> next = new java.util.ArrayList<>();
            for(int idx = 0; idx < 6; idx++){
                WorkerResourceKey output = item("dense_" + depth + "_" + idx);
                for(int choice = 0; choice < previous.size(); choice++){
                    definitions.add(recipe("dense_" + depth + "_" + idx + "_" + choice,
                            output, 1, ingredient(previous.get(choice), 1)));
                }
                next.add(output);
            }
            previous = next;
        }
        definitions.add(recipe("dense_final", result, 1, ingredient(previous.getFirst(), 1)));
        var planned = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L), definitions,
                        WorkerRecipePlan.Operation.CRAFTING, candidate -> true));
        assertTrue(planned.chain().executable(), planned.failure().message());
    }

    // A cyclic alternative must not multiply the cost of otherwise shared stock routes
    @Test void resolvesCyclicSharedDependenciesWithinTickBudget(){
        WorkerResourceKey raw = item("cyclic_dense_raw");
        WorkerResourceKey result = item("cyclic_dense_result");
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        List<WorkerResourceKey> previous = List.of(raw);
        for(int depth = 0; depth < 9; depth++){
            List<WorkerResourceKey> next = new java.util.ArrayList<>();
            for(int idx = 0; idx < 6; idx++){
                WorkerResourceKey output = item("cyclic_dense_" + depth + "_" + idx);
                for(int choice = 0; choice < previous.size(); choice++){
                    definitions.add(recipe("cyclic_dense_" + depth + "_" + idx + "_" + choice,
                            output, 1, ingredient(previous.get(choice), 1)));
                }
                definitions.add(recipe("cyclic_loop_" + depth + "_" + idx,
                        output, 1, ingredient(result, 1)));
                next.add(output);
            }
            previous = next;
        }
        definitions.add(recipe("cyclic_dense_final", result, 1, ingredient(previous.getFirst(), 1)));
        var planned = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () ->
                WorkerRecipePlanner.planDetailed(result, 1, Map.of(raw, 1L), definitions,
                        WorkerRecipePlan.Operation.CRAFTING, candidate -> true));
        assertTrue(planned.chain().executable(), planned.failure().message());
    }

    private static List<WorkerRecipeDefinition> recyclingRecipes(WorkerResourceKey raw,
                                                                  WorkerResourceKey material,
                                                                  WorkerResourceKey result){
        List<WorkerRecipeDefinition> definitions = new java.util.ArrayList<>();
        definitions.add(recipe("seed", material, 1, ingredient(raw, 1)));
        for(int outer = 0; outer < 96; outer++){
            WorkerResourceKey intermediate = item("intermediate_" + outer);
            definitions.add(recipe("recover_" + outer, material, 1, ingredient(intermediate, 1)));
            for(int inner = 0; inner < 96; inner++){
                WorkerResourceKey recycled = item("recycled_" + outer + "_" + inner);
                definitions.add(recipe("intermediate_" + outer + "_" + inner,
                        intermediate, 1, ingredient(recycled, 1)));
                definitions.add(recipe("recycled_" + outer + "_" + inner,
                        recycled, 1, ingredient(material, 1)));
            }
        }
        definitions.add(recipe("assemble", result, 1, ingredient(material, 2)));
        return definitions;
    }

    // Preserve the exact request while retaining the recipe batch size
    @Test void keepsRequestedCountSeparateFromOverproduction(){
        var chain = plan(PLANK, 1, Map.of(LOG, 1L), List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1))));
        assertEquals(1, chain.steps().getFirst().requestedAmount());
        assertEquals(4, chain.steps().getFirst().plan().resultAmount());
    }

    // Reject missing processors without consuming the caller's stock snapshot
    @Test void respectsProcessorAvailabilityAndLeavesStockUntouched(){
        Map<WorkerResourceKey, Long> stock = new java.util.HashMap<>(Map.of(LOG, 1L));
        var chain = WorkerRecipePlanner.plan(PLANK, 1, stock,
                List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1))), null, recipe -> false);
        assertFalse(chain.executable());
        assertEquals(Map.of(LOG, 1L), stock);
    }

    // An exhausted nugget route must give way to the raw-material route when replanned.
    @Test void replansCopperIngotFromRawCopperAfterNuggetsRunOut(){
        var raw = item("raw_copper");
        var nugget = item("copper_nugget");
        var ingot = item("copper_ingot");
        var recipes = List.of(recipe("ingot_from_nuggets", ingot, 1, ingredient(nugget, 9)),
                recipe("nuggets_from_ingot", nugget, 9, ingredient(ingot, 1)),
                recipe("smelt_raw_copper", ingot, 1, ingredient(raw, 1)));
        var planned = plan(ingot, 1, Map.of(raw, 370L), recipes);
        assertTrue(planned.executable());
        assertEquals(List.of(ResourceLocation.withDefaultNamespace("smelt_raw_copper")),
                planned.steps().stream().map(step -> step.plan().recipeId()).toList());
    }

    // Stop impossible amount multiplication without returning a partial job
    @Test void rejectsOverflow(){
        assertFalse(plan(PLANK, Long.MAX_VALUE, Map.of(LOG, Long.MAX_VALUE),
                List.of(recipe("planks", PLANK, 4, ingredient(LOG, 1)))).executable());
    }

    private static WorkerRecipeChain plan(WorkerResourceKey result, long amount,
                                           Map<WorkerResourceKey, Long> stock, List<WorkerRecipeDefinition> recipes){
        return WorkerRecipePlanner.plan(result, amount, stock, recipes, WorkerRecipePlan.Operation.CRAFTING, recipe -> true);
    }

    private static WorkerRecipeDefinition recipe(String id, WorkerResourceKey result, long count,
                                                  WorkerRecipeDefinition.Ingredient... ingredients){
        return new WorkerRecipeDefinition(ResourceLocation.withDefaultNamespace(id),
                ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.WORKER_CRAFTING,
                List.of(ingredients), result, count);
    }

    private static WorkerRecipeDefinition.Ingredient ingredient(WorkerResourceKey resource, long amount){
        return new WorkerRecipeDefinition.Ingredient(List.of(resource), amount);
    }

    private static WorkerResourceKey item(String id){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(id));
    }
}
