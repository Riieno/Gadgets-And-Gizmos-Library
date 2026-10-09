package com.rieno.gadgetsandgizmos.lib.create.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.registry.CreateDataMaps;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Resolve Create processing surfaces through their actual drivers and connected inventories
public final class CreateWorkerMachines implements WorkerMachineRegistry.Adapter{
    private static final Map<net.minecraft.world.level.Level, Map<BlockPos, AirSources>> AIR_SOURCES = new WeakHashMap<>();
    private static final List<net.minecraft.resources.ResourceLocation> PROCESSOR_TYPES = java.util.stream.Stream.of(
            "create:mixing", "create:compacting", "create:milling", "create:emptying", "create:crushing", "create:cutting",
            "create:pressing", "create:deploying", "create:filling", "create:sequenced_assembly", "create:splashing",
            "create:haunting", "minecraft:smoking", "minecraft:smelting", "minecraft:blasting")
            .map(net.minecraft.resources.ResourceLocation::parse).toList();

    private record AirSources(long tick, List<BlockPos> positions){}

    // Order one moving Create belt from its upstream input to its downstream output
    public static List<BlockPos> movingBeltSegments(Level level, BeltBlockEntity belt){
        if(level == null || belt == null) return List.of();
        float speed = belt.getDirectionAwareBeltMovementSpeed();
        if(speed == 0) return List.of();
        List<BlockPos> segments = new ArrayList<>(BeltBlock.getBeltChain(level, belt.getController()));
        if(speed < 0) java.util.Collections.reverse(segments);
        return segments;
    }

    // Trace the moving workpiece across belt chains joined by an upward-facing saw
    public static List<BlockPos> processingLine(Level level, BlockPos pos){
        if(level == null || pos == null || !WorkerContainerAccess.isLoaded(level, pos)) return List.of();
        List<BlockPos> line = new ArrayList<>(stationRun(level, pos));
        if(line.isEmpty()) return List.of();
        LinkedHashSet<BlockPos> visited = new LinkedHashSet<>(line);
        for(int idx = 0; idx < 256; idx++){
            BlockPos first = line.getFirst();
            Direction direction = stationDirection(level.getBlockEntity(first));
            if(direction == null) break;
            BlockPos previous = first.relative(direction.getOpposite());
            if(!WorkerContainerAccess.isLoaded(level, previous) || visited.contains(previous)) break;
            List<BlockPos> run = stationRun(level, previous);
            if(run.isEmpty() || !run.getLast().equals(previous)
                    || stationDirection(level.getBlockEntity(previous)) != direction) break;
            List<BlockPos> extended = new ArrayList<>(run);
            extended.addAll(line);
            line = extended;
            visited.addAll(run);
        }
        for(int idx = 0; idx < 256; idx++){
            BlockPos last = line.getLast();
            Direction direction = stationDirection(level.getBlockEntity(last));
            if(direction == null) break;
            BlockPos next = last.relative(direction);
            if(!WorkerContainerAccess.isLoaded(level, next) || visited.contains(next)) break;
            List<BlockPos> run = stationRun(level, next);
            if(run.isEmpty() || !run.getFirst().equals(next)
                    || stationDirection(level.getBlockEntity(next)) != direction) break;
            line.addAll(run);
            visited.addAll(run);
        }
        return List.copyOf(line);
    }

    // Limit workpiece insertion to the moving line before its first processing station
    public static List<BlockPos> upstreamWorkpieceSegments(List<BlockPos> line, BlockPos firstStation){
        if(line == null || firstStation == null) return List.of();
        int end = line.indexOf(firstStation);
        return end < 0 ? List.of() : List.copyOf(line.subList(0, end + 1));
    }

    private static List<BlockPos> stationRun(Level level, BlockPos pos){
        BlockEntity be = level.getBlockEntity(pos);
        if(be instanceof BeltBlockEntity belt) return movingBeltSegments(level, belt);
        if(be instanceof SawBlockEntity && stationDirection(be) != null) return List.of(pos);
        return List.of();
    }

    private static Direction stationDirection(BlockEntity be){
        if(be instanceof BeltBlockEntity belt && belt.getDirectionAwareBeltMovementSpeed() != 0)
            return belt.getMovementFacing();
        if(be instanceof SawBlockEntity saw && saw.getSpeed() != 0
                && saw.getBlockState().getValue(BlockStateProperties.FACING) == Direction.UP){
            Vec3 movement = saw.getItemMovementVec();
            return Direction.getNearest(movement.x, movement.y, movement.z);
        }
        return null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public WorkerMachine resolve(WorkerMachineRegistry.Context ctx){
        BlockEntity be = ctx.level().getBlockEntity(ctx.pos());
        if(be instanceof MechanicalCrafterBlockEntity crafter) return new MechanicalCrafterWorkerMachine(ctx, crafter);
        if(be instanceof DepotBlockEntity || be instanceof BeltBlockEntity || be instanceof BasinBlockEntity
                || be instanceof MillstoneBlockEntity || be instanceof CrushingWheelControllerBlockEntity
                || be instanceof SawBlockEntity || be instanceof ItemDrainBlockEntity) return new Processor(ctx, be);
        // A face linked to the press expands to this work position even when no depot is present.
        // Items dropped here are processed by the press in the same way as player-dropped items.
        if(ctx.level().getBlockState(ctx.pos()).isAir()
                && ctx.level().getBlockEntity(ctx.pos().above(2)) instanceof MechanicalPressBlockEntity)
            return new Processor(ctx, null);
        return null;
    }

    @Override
    public List<BlockPos> accessPositions(WorkerMachineRegistry.Context ctx){
        BlockEntity be = ctx.level().getBlockEntity(ctx.pos());
        if(be instanceof WorkerAirProcessingSource source) return source.workerProcessingTargets(ctx.level());
        if(be instanceof MechanicalPressBlockEntity || be instanceof MechanicalMixerBlockEntity
                || be instanceof DeployerBlockEntity || be instanceof SpoutBlockEntity) return List.of(ctx.pos().below(2));
        if(be instanceof BeltBlockEntity belt) return BeltBlock.getBeltChain(ctx.level(), belt.getController());
        if(be instanceof IAirCurrentSource fan){
            List<BlockPos> positions = new ArrayList<>();
            var flow = fan.getAirCurrent();
            if(flow == null || flow.bounds == null) return List.of();
            for(BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(flow.bounds.minX, flow.bounds.minY - 1, flow.bounds.minZ),
                    BlockPos.containing(flow.bounds.maxX, flow.bounds.maxY, flow.bounds.maxZ))){
                if(!WorkerContainerAccess.isLoaded(ctx.level(), pos)) continue;
                var target = ctx.level().getBlockEntity(pos);
                if(target instanceof DepotBlockEntity || target instanceof BeltBlockEntity) positions.add(pos.immutable());
            }
            return positions;
        }
        if(BuiltInRegistries.BLOCK.getKey(ctx.level().getBlockState(ctx.pos()).getBlock()).toString().equals("create:crushing_wheel")){
            List<BlockPos> positions = new ArrayList<>();
            for(Direction dir : Direction.values()){
                BlockPos pos = ctx.pos().relative(dir);
                if(ctx.level().getBlockEntity(pos) instanceof CrushingWheelControllerBlockEntity) positions.add(pos);
            }
            return positions;
        }
        return List.of();
    }

    private record Processor(WorkerMachineRegistry.Context ctx, BlockEntity be) implements WorkerMachine{
        @Override public java.util.Set<net.minecraft.resources.ResourceLocation> supportedProcessorTypes(){
            return PROCESSOR_TYPES.stream().filter(this::maySupportProcessor).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        @Override public boolean mayProbePreloadedInputsBeforeSupport(){ return true; }
        @Override public boolean maySupportProcessor(net.minecraft.resources.ResourceLocation processorType){
            if(processorType == null) return false;
            String type = processorType.toString();
            BlockEntity driver = ctx.level().getBlockEntity(ctx.pos().above(2));
            if(be instanceof BasinBlockEntity) return type.equals("create:mixing") && driver instanceof MechanicalMixerBlockEntity
                    || type.equals("create:compacting") && driver instanceof MechanicalPressBlockEntity;
            if(be instanceof MillstoneBlockEntity) return type.equals("create:milling");
            if(be instanceof ItemDrainBlockEntity) return type.equals("create:emptying");
            if(be instanceof CrushingWheelControllerBlockEntity) return type.equals("create:crushing");
            if(type.equals("create:sequenced_assembly")) return be instanceof BeltBlockEntity || be instanceof SawBlockEntity;
            if(type.equals("create:cutting")) return be instanceof SawBlockEntity;
            if(type.equals("create:pressing")) return driver instanceof MechanicalPressBlockEntity;
            if(type.equals("create:deploying")) return driver instanceof DeployerBlockEntity;
            if(type.equals("create:filling")) return driver instanceof SpoutBlockEntity;
            return (type.equals("create:splashing") || type.equals("create:haunting")
                    || type.equals("minecraft:smoking") || type.equals("minecraft:smelting")
                    || type.equals("minecraft:blasting")) && !airSources(ctx).isEmpty();
        }
        @Override public boolean maySupportRecipe(net.minecraft.resources.ResourceLocation recipeId,
                                                  net.minecraft.resources.ResourceLocation processorType){
            if(processorType == null) return false;
            if(recipeId != null && ctx.level().getRecipeManager().byKey(recipeId)
                    .map(holder -> holder.value() instanceof SequencedAssemblyRecipe).orElse(false)) return true;
            return maySupportProcessor(processorType);
        }
        @Override public boolean mayHavePreloadedInputs(WorkerRecipeDefinition recipe){
            return recipe != null && ctx.level().getRecipeManager().byKey(recipe.recipeId())
                    .map(holder -> holder.value() instanceof SequencedAssemblyRecipe).orElse(false);
        }
        @Override public int routingPenalty(WorkerRecipePlan plan, WorkerArea area){
            if(plan == null || area == null || plan.stage() >= 0
                    || WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe) return 0;
            if(!conflictingTransport(plan, area).isEmpty()) return 8;
            for(BlockPos pos : processingLine(ctx.level(), ctx.pos())){
                if(pos.equals(ctx.pos()) || !area.contains(pos)) continue;
                BlockEntity station = ctx.level().getBlockEntity(pos);
                if(station != null && !(station instanceof BeltBlockEntity)
                        || WorkerMachineRegistry.accessPositions(ctx.level(), pos.above(2)).contains(pos)) return 8;
            }
            return 0;
        }

        @Override public List<BlockPos> conflictingTransport(WorkerRecipePlan plan, WorkerArea area){
            if(plan == null || area == null || plan.stage() >= 0
                    || WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe)
                return List.of();
            List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
            int station = line.indexOf(ctx.pos());
            if(station < 0 || station + 1 >= line.size()) return List.of();
            LinkedHashSet<BlockPos> gates = new LinkedHashSet<>();
            for(int idx = station + 1; idx < line.size(); idx++){
                BlockPos segment = line.get(idx);
                if(!area.contains(segment)) break;
                for(Direction side : Direction.values()){
                    BlockPos adjacent = segment.relative(side);
                    if(!area.contains(adjacent)) continue;
                    var block = ctx.level().getBlockState(adjacent).getBlock();
                    if(block instanceof AbstractFunnelBlock || block instanceof AbstractChuteBlock)
                        gates.add(adjacent.immutable());
                }
            }
            return List.copyOf(gates);
        }

        @Override public boolean supports(WorkerRecipePlan plan){
            if(plan == null) return false;
            if(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly){
                return !assemblyRoute(plan, assembly).isEmpty();
            }
            String type = plan.processorType().toString();
            BlockEntity driver = ctx.level().getBlockEntity(ctx.pos().above(2));
            if(be instanceof BasinBlockEntity){
                boolean matches = type.equals("create:mixing") && driver instanceof MechanicalMixerBlockEntity
                        || type.equals("create:compacting") && driver instanceof MechanicalPressBlockEntity;
                if(!matches) return false;
                var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
                if(!(recipe instanceof ProcessingRecipe<?, ?> processing)) return false;
                HeatCondition heat = processing.getRequiredHeat();
                return heat.testBlazeBurner(BasinBlockEntity.getHeatLevelOf(
                        ctx.level().getBlockState(ctx.pos().below())))
                        || heat != HeatCondition.NONE
                        && ctx.level().getBlockEntity(ctx.pos().below()) instanceof BlazeBurnerBlockEntity;
            }
            if(be instanceof MillstoneBlockEntity) return type.equals("create:milling");
            if(be instanceof ItemDrainBlockEntity) return type.equals("create:emptying");
            if(be instanceof CrushingWheelControllerBlockEntity) return type.equals("create:crushing");
            if(be instanceof SawBlockEntity) return type.equals("create:cutting")
                    && be.getBlockState().getValue(BlockStateProperties.FACING) == Direction.UP;
            if(type.equals("create:pressing")){
                if(!(driver instanceof MechanicalPressBlockEntity press) || plan.inputs().isEmpty()
                        || plan.inputs().getFirst().resource().type() != WorkerResourceType.ITEM) return false;
                ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(plan.inputs().getFirst().resource().id()));
                if(plan.stage() > 0){
                    var holder = ctx.level().getRecipeManager().byKey(plan.recipeId()).orElse(null);
                    if(!(holder != null && holder.value() instanceof SequencedAssemblyRecipe assembly)) return false;
                    int total = assembly.getLoops() * assembly.getSequence().size();
                    input.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                            new SequencedAssemblyRecipe.SequencedAssembly(plan.recipeId(), plan.stage(),
                                    total == 0 ? 0F : (float)plan.stage() / total));
                }
                if(input.isEmpty()) return false;
                var selected = press.getRecipe(input);
                if(selected.isPresent()) return selected.get().id().equals(plan.recipeId());
                var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
                return recipe instanceof ProcessingRecipe<?, ?>
                        && !recipe.getIngredients().isEmpty()
                        && recipe.getIngredients().getFirst().test(input);
            }
            if(type.equals("create:deploying")) return driver instanceof DeployerBlockEntity
                    && driver.getBlockState().getValue(BlockStateProperties.FACING) == Direction.DOWN;
            if(type.equals("create:filling")) return driver instanceof SpoutBlockEntity;
            return fanSupports(plan);
        }

        @Override public boolean supports(WorkerRecipePlan plan, WorkerArea area){
            return supportsAt(plan, new WorkerMachineSite(area, List.of(), List.of()));
        }

        @Override public boolean supportsAt(WorkerRecipePlan plan, WorkerMachineSite site){
            WorkerArea area = site == null ? null : site.area();
            if(!supports(plan)) return false;
            if(!(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly))
                return site == null || site.inputs().isEmpty() || !itemInputsAt(plan, site).isEmpty();
            if(area == null) return true;
            List<AssemblyPort> route = assemblyRoute(plan, assembly);
            if(route.isEmpty() || route.stream().anyMatch(port -> !area.contains(port.pos())
                    || !(ctx.level().getBlockEntity(port.pos()) instanceof SawBlockEntity)
                    && !area.contains(port.pos().above(2)))) return false;
            List<BlockPos> upstream = upstreamWorkpieceSegments(processingLine(ctx.level(), ctx.pos()),
                    route.getFirst().pos());
            if(site.inputs().isEmpty() && upstream.stream().noneMatch(area::contains)
                    || site.outputs().isEmpty() && itemOutputs(plan, area).isEmpty()) return false;
            if(!site.inputs().isEmpty() && site.inputs().stream().noneMatch(input ->
                    isWorkpieceInput(input.pos(), upstream, route) && area.contains(input.pos())
                            && !WorkerContainerAccess.itemHandlers(ctx.level(), input.pos(), input.face()).isEmpty()))
                return false;
            if(!site.outputs().isEmpty() && site.outputs().stream().noneMatch(output ->
                    area.contains(output.pos()) && !WorkerContainerAccess.itemHandlers(
                            ctx.level(), output.pos(), output.face()).isEmpty())) return false;
            List<IItemHandler> inputs = itemInputsAt(plan, site);
            for(WorkerRecipePlan.Input input : plan.inputs()){
                if(input.resource().type() != WorkerResourceType.ITEM) continue;
                if(input.alternatives().stream().noneMatch(resource -> {
                    ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
                    if(sample.isEmpty()) return false;
                    return inputs.stream().anyMatch(port -> {
                    for(int slot = 0; slot < port.getSlots(); slot++){
                        if(port instanceof WorkerItemPort filtered && filtered.accepts().test(sample)
                                || !(port instanceof WorkerItemPort) && port.isItemValid(slot, sample)) return true;
                    }
                    return false;
                    });
                })) return false;
            }
            return true;
        }

        @Override public List<String> routeDiagnostics(WorkerRecipePlan plan, WorkerMachineSite site){
            if(plan != null && be instanceof BasinBlockEntity
                    && WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof ProcessingRecipe<?, ?> recipe){
                HeatCondition heat = recipe.getRequiredHeat();
                if(heat != HeatCondition.NONE && !heat.testBlazeBurner(BasinBlockEntity.getHeatLevelOf(
                        ctx.level().getBlockState(ctx.pos().below())))
                        && !(ctx.level().getBlockEntity(ctx.pos().below()) instanceof BlazeBurnerBlockEntity))
                    return List.of("Recipe needs a captured blaze burner below the basin at " + ctx.pos().toShortString());
            }
            if(plan == null || !(be instanceof BeltBlockEntity || be instanceof SawBlockEntity)
                    || !(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly))
                return List.of();
            List<WorkerRecipePlan> stages = CreateWorkerSequences.stages(ctx.level(), plan, assembly);
            if(stages.isEmpty()) return List.of("Assembly stage ingredients could not be resolved for "
                    + plan.recipeId());
            List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
            if(line.isEmpty()) return List.of("Belt or saw line is stopped at " + ctx.pos().toShortString());
            List<AssemblyPort> route = assemblyRoute(plan, assembly);
            if(route.isEmpty()){
                int stage = 0;
                BlockPos previous = line.getFirst();
                for(BlockPos pos : line){
                    if(stage >= assembly.getSequence().size()) break;
                    WorkerRecipePlan expected = stages.get(stage);
                    var station = new Processor(new WorkerMachineRegistry.Context(ctx.level(), pos, null),
                            ctx.level().getBlockEntity(pos));
                    if(!station.supports(expected)) continue;
                    var recipe = WorkerRecipeCatalog.recipe(ctx.level(), expected);
                    if(recipe instanceof ProcessingRecipe<?, ?> processing
                            && !processing.getFluidIngredients().isEmpty()
                            && station.fluidInputs(expected).isEmpty())
                        return List.of("Stage " + (stage + 1) + " needs fluid input at " + pos.toShortString());
                    previous = pos;
                    stage++;
                }
                if(stage < assembly.getSequence().size()) return List.of("Stage " + (stage + 1)
                        + " needs " + stages.get(stage).processorType() + " after " + previous.toShortString());
                return List.of("Assembly stations are not connected in belt travel order near "
                        + ctx.pos().toShortString());
            }
            WorkerArea area = site == null ? null : site.area();
            if(area == null) return List.of();
            for(AssemblyPort port : route){
                if(!area.contains(port.pos()) || !(ctx.level().getBlockEntity(port.pos()) instanceof SawBlockEntity)
                        && !area.contains(port.pos().above(2)))
                    return List.of("Stage at " + port.pos().toShortString() + " is outside the machine area");
            }
            List<BlockPos> upstream = upstreamWorkpieceSegments(line, route.getFirst().pos());
            if(site.inputs().isEmpty()){
                if(upstream.stream().noneMatch(area::contains))
                    return List.of("No upstream belt input is inside the machine area");
            }else if(site.inputs().stream().noneMatch(input -> area.contains(input.pos())
                    && isWorkpieceInput(input.pos(), upstream, route)
                    && !WorkerContainerAccess.itemHandlers(ctx.level(), input.pos(), input.face()).isEmpty())){
                return List.of("No marked workpiece input is reachable before stage 1 at "
                        + route.getFirst().pos().toShortString());
            }
            if(site.outputs().isEmpty()){
                if(itemOutputs(plan, area).isEmpty()) return List.of("No output inventory is reachable after stage "
                        + assembly.getSequence().size());
            }else if(site.outputs().stream().noneMatch(output -> area.contains(output.pos())
                    && !WorkerContainerAccess.itemHandlers(ctx.level(), output.pos(), output.face()).isEmpty())){
                return List.of("Marked output has no accessible inventory inside the machine area");
            }
            if(!supportsAt(plan, site)) return List.of("A stage supply has no accessible input in the machine area");
            return List.of();
        }

        @Override public List<BlockPos> areaOutputs(WorkerRecipePlan plan, WorkerArea area,
                                                    List<BlockPos> candidates){
            if(plan == null || !(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly))
                return candidates;
            List<AssemblyPort> route = assemblyRoute(plan, assembly);
            if(route.isEmpty() || area == null) return List.of();
            List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
            if(line.isEmpty()) return List.of();
            Direction direction = stationDirection(ctx.level().getBlockEntity(route.getLast().pos()));
            if(direction == null) return List.of();
            BlockPos finalStation = route.getLast().pos();
            LinkedHashSet<BlockPos> inputs = new LinkedHashSet<>();
            for(BlockPos segment : upstreamWorkpieceSegments(line, route.getFirst().pos())){
                inputs.add(segment);
                inputs.add(segment.above());
                var funnel = ctx.level().getBlockState(segment.above());
                if(!(funnel.getBlock() instanceof BeltFunnelBlock)) continue;
                Direction facing = AbstractFunnelBlock.getFunnelFacing(funnel);
                inputs.add(segment.above().relative(facing.getOpposite()));
            }
            return candidates.stream().filter(output -> area.contains(output) && !inputs.contains(output))
                    .sorted(java.util.Comparator.<BlockPos>comparingInt(output -> {
                        int offset = (output.getX() - finalStation.getX()) * direction.getStepX()
                                + (output.getY() - finalStation.getY()) * direction.getStepY()
                                + (output.getZ() - finalStation.getZ()) * direction.getStepZ();
                        return offset >= 0 ? 0 : 1;
                    }).thenComparingInt(output -> output.distManhattan(line.getLast()))).toList();
        }

        @Override public List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area){
            return preloadedInputs(plan, area, 1L);
        }

        @Override public List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area, long batches){
            return preloadedInputs(plan, area, batches, List.of());
        }

        @Override public List<Long> preloadedInputsAt(WorkerRecipePlan plan, WorkerMachineSite site, long batches){
            return preloadedInputs(plan, site == null ? null : site.area(), batches,
                    site == null ? List.of() : site.inputs());
        }

        private List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area, long batches,
                                           List<WorkerMachineSite.Port> inputs){
            if(plan == null || batches <= 0L
                    || !(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly))
                return List.of();
            List<Map<WorkerResourceKey, Long>> stages = preloadedStageStock(plan, area, inputs);
            if(stages.isEmpty()) return List.of();
            List<Long> stocked = new ArrayList<>(java.util.Collections.nCopies(plan.inputs().size(), 0L));
            List<Long> required = new ArrayList<>();
            for(var sequence : assembly.getSequence()){
                if(!(sequence.getRecipe() instanceof ProcessingRecipe<?, ?> step)) return List.of();
                for(int idx = 1; idx < step.getIngredients().size(); idx++) required.add((long)assembly.getLoops());
                for(var fluid : step.getFluidIngredients()) required.add((long)fluid.amount() * assembly.getLoops());
            }
            required.add(1L);
            if(required.size() != stages.size()) return List.of();
            for(int stageIdx = 0; stageIdx < stages.size(); stageIdx++){
                Map<WorkerResourceKey, Long> stage = stages.get(stageIdx);
                long limit = required.get(stageIdx);
                limit = batches > Long.MAX_VALUE / limit ? Long.MAX_VALUE : limit * batches;
                for(var entry : stage.entrySet()){
                    if(limit <= 0L) break;
                    for(int idx = 0; idx < plan.inputs().size(); idx++){
                        if(!plan.inputs().get(idx).alternatives().contains(entry.getKey())) continue;
                        long credited = Math.min(limit, Math.max(0L, entry.getValue()));
                        stocked.set(idx, Math.min(Long.MAX_VALUE - stocked.get(idx), credited) + stocked.get(idx));
                        limit -= credited;
                        break;
                    }
                }
            }
            return List.copyOf(stocked);
        }

        @Override public List<Long> preloadedInputs(WorkerRecipeDefinition recipe, WorkerArea area){
            return preloadedInputs(recipe, area, List.of());
        }

        @Override public List<Long> preloadedInputsAt(WorkerRecipeDefinition recipe, WorkerMachineSite site){
            return preloadedInputs(recipe, site == null ? null : site.area(),
                    site == null ? List.of() : site.inputs());
        }

        private List<Long> preloadedInputs(WorkerRecipeDefinition recipe, WorkerArea area,
                                           List<WorkerMachineSite.Port> inputs){
            if(recipe == null || recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty()))
                return List.of();
            WorkerRecipePlan plan = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                    recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(
                            input.alternatives().getFirst(), input.amount(), input.alternatives())).toList(),
                    recipe.result(), recipe.resultAmount());
            List<Map<WorkerResourceKey, Long>> stages = preloadedStageStock(plan, area, inputs);
            if(stages.isEmpty()) return List.of();
            List<Long> stocked = new ArrayList<>(java.util.Collections.nCopies(recipe.ingredients().size(), 0L));
            for(int idx = 0; idx < Math.min(stages.size(), stocked.size()); idx++){
                WorkerRecipeDefinition.Ingredient input = recipe.ingredients().get(idx);
                long count = 0L;
                for(var entry : stages.get(idx).entrySet()){
                    if(input.alternatives().contains(entry.getKey())) count += entry.getValue();
                }
                stocked.set(idx, count);
            }
            return List.copyOf(stocked);
        }

        // Read actual held items and fluids from each ordered station without counting workpiece outputs
        private List<Map<WorkerResourceKey, Long>> preloadedStageStock(WorkerRecipePlan plan, WorkerArea area,
                                                                       List<WorkerMachineSite.Port> inputs){
            if(plan == null || !(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly))
                return List.of();
            List<AssemblyPort> route = assemblyRoute(plan, assembly);
            if(route.isEmpty()) return List.of();
            List<Map<WorkerResourceKey, Long>> stocked = new ArrayList<>();
            for(AssemblyPort port : route){
                if(!(WorkerRecipeCatalog.recipe(ctx.level(), port.plan()) instanceof ProcessingRecipe<?, ?> step))
                    return List.of();
                for(int slot = 1; slot < step.getIngredients().size(); slot++){
                    Map<WorkerResourceKey, Long> contents = new LinkedHashMap<>();
                    if(area == null || area.contains(port.pos().above(2))){
                        for(IItemHandler handler : WorkerContainerAccess.itemHandlers(ctx.level(), port.pos().above(2), null)){
                            for(int itemSlot = 0; itemSlot < handler.getSlots(); itemSlot++){
                                ItemStack stack = handler.getStackInSlot(itemSlot);
                                if(stack.isEmpty() || !step.getIngredients().get(slot).test(stack)) continue;
                                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                                        BuiltInRegistries.ITEM.getKey(stack.getItem()));
                                contents.merge(resource, (long)stack.getCount(), Long::sum);
                            }
                        }
                    }
                    stocked.add(Map.copyOf(contents));
                }
                for(var fluid : step.getFluidIngredients()){
                    Map<WorkerResourceKey, Long> contents = new LinkedHashMap<>();
                    if(area == null || area.contains(port.pos().above(2))){
                        for(IFluidHandler handler : WorkerContainerAccess.fluidHandlers(ctx.level(), port.pos().above(2), null)){
                            for(int tank = 0; tank < handler.getTanks(); tank++){
                                var stack = handler.getFluidInTank(tank);
                                if(stack.isEmpty() || !fluid.ingredient().test(stack)) continue;
                                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.FLUID,
                                        BuiltInRegistries.FLUID.getKey(stack.getFluid()));
                                contents.merge(resource, (long)stack.getAmount(), Long::sum);
                            }
                        }
                    }
                    stocked.add(Map.copyOf(contents));
                }
            }
            Map<WorkerResourceKey, Long> workpieces = new LinkedHashMap<>();
            LinkedHashSet<BlockPos> entry = new LinkedHashSet<>();
            List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
            for(BlockPos segment : inputs.isEmpty() ? upstreamWorkpieceSegments(line, route.getFirst().pos())
                    : List.<BlockPos>of()){
                entry.add(segment);
                var funnel = ctx.level().getBlockState(segment.above());
                if(!(funnel.getBlock() instanceof BeltFunnelBlock)
                        || !(ctx.level().getBlockEntity(segment) instanceof BeltBlockEntity belt)) continue;
                Direction facing = AbstractFunnelBlock.getFunnelFacing(funnel);
                var shape = funnel.getValue(BeltFunnelBlock.SHAPE);
                boolean pushing = shape == BeltFunnelBlock.Shape.PUSHING
                        || shape != BeltFunnelBlock.Shape.PULLING && facing == belt.getMovementFacing();
                if(pushing) entry.add(segment.above().relative(facing.getOpposite()));
            }
            List<BlockPos> upstream = upstreamWorkpieceSegments(line, route.getFirst().pos());
            for(WorkerMachineSite.Port input : inputs){
                if(isWorkpieceInput(input.pos(), upstream, route)) entry.add(input.pos());
            }
            java.util.Set<IItemHandler> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for(BlockPos input : entry){
                if(area != null && !area.contains(input)) continue;
                Direction face = inputs.stream().filter(port -> port.pos().equals(input))
                        .map(WorkerMachineSite.Port::face).findFirst().orElse(null);
                for(IItemHandler handler : WorkerContainerAccess.itemHandlers(ctx.level(), input, face)){
                    if(!seen.add(handler)) continue;
                    for(int slot = 0; slot < handler.getSlots(); slot++){
                        ItemStack stack = handler.getStackInSlot(slot);
                        if(stack.isEmpty() || !assembly.getIngredient().test(stack)) continue;
                        WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                                BuiltInRegistries.ITEM.getKey(stack.getItem()));
                        workpieces.merge(resource, (long)stack.getCount(), Long::sum);
                    }
                }
            }
            stocked.add(Map.copyOf(workpieces));
            return List.copyOf(stocked);
        }

        // Require the matching air stream to intersect the item, including its processing medium
        private boolean fanSupports(WorkerRecipePlan plan){
            String type = plan.processorType().toString();
            if(!type.equals("create:splashing") && !type.equals("create:haunting")
                    && !type.equals("minecraft:smoking") && !type.equals("minecraft:smelting")
                    && !type.equals("minecraft:blasting")) return false;
            Object required = null;
            Vec3 itemPos = Vec3.atCenterOf(ctx.pos()).add(0, 0.65, 0);
            for(BlockPos candidate : airSources(ctx)){
                if(!(ctx.level().getBlockEntity(candidate) instanceof IAirCurrentSource fan)) continue;
                if(fan instanceof WorkerAirProcessingSource source){
                    if(source.supportsWorkerRecipe(ctx.level(), ctx.pos(), plan)) return true;
                    continue;
                }
                if(required == null) required = switch(type){
                    case "create:splashing" -> AllFanProcessingTypes.SPLASHING;
                    case "create:haunting" -> AllFanProcessingTypes.HAUNTING;
                    case "minecraft:smoking" -> AllFanProcessingTypes.SMOKING;
                    default -> AllFanProcessingTypes.BLASTING;
                };
                var flow = fan.getAirCurrent();
                if(flow == null || flow.direction == null || flow.bounds == null
                        || !flow.bounds.intersects(new AABB(itemPos, itemPos).inflate(0.15))) continue;
                float offset = (float)itemPos.subtract(Vec3.atCenterOf(candidate))
                        .dot(Vec3.atLowerCornerOf(flow.direction.getNormal()));
                if(flow.getTypeAt(Math.abs(offset)) == required) return true;
            }
            return false;
        }

        // Reuse nearby source positions for every recipe checked against this input block
        private static List<BlockPos> airSources(WorkerMachineRegistry.Context ctx){
            long tick = ctx.level().getGameTime();
            synchronized(AIR_SOURCES){
                Map<BlockPos, AirSources> cache = AIR_SOURCES.computeIfAbsent(ctx.level(), key -> new LinkedHashMap<>());
                AirSources cached = cache.get(ctx.pos());
                if(cached != null && cached.tick() == tick) return cached.positions();
                LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
                for(Direction dir : Direction.values()){
                    for(int distance = 1; distance <= 32; distance++){
                        BlockPos center = ctx.pos().relative(dir, distance);
                        if(!WorkerContainerAccess.isLoaded(ctx.level(), center)) break;
                        for(int height = 0; height <= 1; height++){
                            BlockPos plane = center.above(height);
                            for(int first = -1; first <= 1; first++){
                                for(int second = -1; second <= 1; second++){
                                    BlockPos candidate = switch(dir.getAxis()){
                                        case X -> plane.offset(0, first, second);
                                        case Y -> plane.offset(first, 0, second);
                                        case Z -> plane.offset(first, second, 0);
                                    };
                                    if(WorkerContainerAccess.isLoaded(ctx.level(), candidate)
                                            && ctx.level().getBlockEntity(candidate) instanceof IAirCurrentSource){
                                        positions.add(candidate.immutable());
                                    }
                                }
                            }
                        }
                    }
                }
                if(cache.size() >= 1024 && !cache.containsKey(ctx.pos())) cache.remove(cache.keySet().iterator().next());
                List<BlockPos> found = List.copyOf(positions);
                cache.put(ctx.pos(), new AirSources(tick, found));
                return found;
            }
        }

        @Override public List<BlockPos> members(){
            if(be instanceof BeltBlockEntity belt) return BeltBlock.getBeltChain(ctx.level(), belt.getController());
            return List.of(ctx.pos());
        }

        @Override public boolean prepare(WorkerRecipePlan plan){
            if(!supports(plan)) return false;
            if(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe) return true;
            if(be instanceof BasinBlockEntity basin && plan.result().type() == WorkerResourceType.ITEM){
                return basin.getFilter().setFilter(new ItemStack(BuiltInRegistries.ITEM.get(plan.result().id())));
            }
            if(be instanceof SawBlockEntity saw && plan.result().type() == WorkerResourceType.ITEM){
                var filter = saw.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
                return filter != null && filter.setFilter(new ItemStack(BuiltInRegistries.ITEM.get(plan.result().id())));
            }
            return true;
        }

        @Override public List<IItemHandler> itemInputs(WorkerRecipePlan plan){
            return itemInputs(plan, null);
        }

        @Override public List<IItemHandler> itemInputs(WorkerRecipePlan plan, WorkerArea area){
            return itemInputsAt(plan, new WorkerMachineSite(area, List.of(), List.of()));
        }

        @Override public List<IItemHandler> itemInputsAt(WorkerRecipePlan plan, WorkerMachineSite site){
            WorkerArea area = site == null ? null : site.area();
            if(plan != null && WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly){
                List<AssemblyPort> route = assemblyRoute(plan, assembly);
                if(route.isEmpty() || area != null && route.stream().anyMatch(port -> !area.contains(port.pos())))
                    return List.of();
                List<IItemHandler> ports = new ArrayList<>();
                for(AssemblyPort port : route){
                    var recipe = WorkerRecipeCatalog.recipe(ctx.level(), port.plan());
                    if(recipe.getIngredients().size() < 2) continue;
                    Ingredient held = recipe.getIngredients().get(1);
                    if(area != null && !area.contains(port.pos().above(2))) continue;
                    for(var handler : WorkerContainerAccess.itemHandlers(ctx.level(), port.pos().above(2), null)){
                        ports.add(new WorkerItemPort(handler, held::test, assembly.getLoops()));
                    }
                }
                if(site != null && !site.inputs().isEmpty()){
                    List<BlockPos> upstream = upstreamWorkpieceSegments(processingLine(ctx.level(), ctx.pos()),
                            route.getFirst().pos());
                    for(WorkerMachineSite.Port input : site.inputs()){
                        if(area != null && !area.contains(input.pos())
                                || !isWorkpieceInput(input.pos(), upstream, route))
                            continue;
                        for(var handler : WorkerContainerAccess.itemHandlers(ctx.level(), input.pos(), input.face())){
                            ports.add(new WorkerItemPort(handler, stack -> assembly.getIngredient().test(stack)
                                    || CreateWorkerSequences.recirculates(plan, assembly, stack)));
                        }
                    }
                    return List.copyOf(ports);
                }
                List<BlockPos> segments = processingLine(ctx.level(), ctx.pos());
                List<BlockPos> upstream = upstreamWorkpieceSegments(segments, route.getFirst().pos());
                if(upstream.isEmpty()) return List.of();
                for(BlockPos pos : upstream){
                    if(area != null && (!area.contains(pos) || !area.contains(pos.above()))) continue;
                    var funnel = ctx.level().getBlockState(pos.above());
                    if(!(funnel.getBlock() instanceof BeltFunnelBlock)) continue;
                    if(!(ctx.level().getBlockEntity(pos) instanceof BeltBlockEntity segment)) continue;
                    Direction facing = AbstractFunnelBlock.getFunnelFacing(funnel);
                    var shape = funnel.getValue(BeltFunnelBlock.SHAPE);
                    boolean pushing = shape == BeltFunnelBlock.Shape.PUSHING
                            || shape != BeltFunnelBlock.Shape.PULLING && facing == segment.getMovementFacing();
                    if(pushing && !funnel.getOptionalValue(BlockStateProperties.POWERED).orElse(false)){
                        BlockPos source = pos.above().relative(facing.getOpposite());
                        if(area != null && !area.contains(source)) continue;
                        for(var handler : WorkerContainerAccess.itemHandlers(ctx.level(), source, null)){
                            ports.add(new WorkerItemPort(handler, stack -> assembly.getIngredient().test(stack)
                                    || CreateWorkerSequences.recirculates(plan, assembly, stack)));
                        }
                    }
                }
                for(BlockPos pos : upstream){
                    if(area == null || area.contains(pos)){
                        for(var handler : WorkerContainerAccess.itemHandlers(ctx.level(), pos, null)){
                            ports.add(new WorkerItemPort(handler, stack -> assembly.getIngredient().test(stack)
                                    || CreateWorkerSequences.recirculates(plan, assembly, stack), 1));
                        }
                    }
                }
                return ports;
            }
            if(be instanceof BasinBlockEntity basin){
                if(plan == null && ctx.level().getBlockEntity(ctx.pos().below()) instanceof BlazeBurnerBlockEntity)
                    return List.of(new BurnerFuelPort(ctx.level(), ctx.pos().below()));
                return List.of(basin.getInputInventory());
            }
            if(be instanceof MillstoneBlockEntity mill) return List.of(mill.inputInv);
            List<IItemHandler> inputs = be == null && ctx.level().getBlockState(ctx.pos()).isAir()
                    ? List.of(new GroundWorkpieceHandler(ctx.level(), ctx.pos()))
                    : WorkerContainerAccess.itemHandlers(ctx.level(), ctx.pos(), ctx.side());
            if(be instanceof BeltBlockEntity && plan != null && site != null && !site.inputs().isEmpty()){
                List<BlockPos> upstream = upstreamWorkpieceSegments(processingLine(ctx.level(), ctx.pos()), ctx.pos());
                List<IItemHandler> marked = new ArrayList<>();
                for(var port : site.inputs()){
                    if(area != null && !area.contains(port.pos())) continue;
                    if(ctx.level().getBlockEntity(port.pos()) instanceof BeltBlockEntity && !upstream.contains(port.pos())) continue;
                    marked.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), port.pos(), port.face()));
                }
                inputs = marked;
            }
            if(plan == null || !plan.processorType().toString().equals("create:deploying")) return inputs;
            var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
            if(recipe == null || recipe.getIngredients().size() < 2) return List.of();
            Ingredient base = recipe.getIngredients().getFirst();
            Ingredient held = recipe.getIngredients().get(1);
            List<IItemHandler> ports = new ArrayList<>();
            for(var input : inputs) ports.add(new WorkerItemPort(input, base::test));
            if(area == null || area.contains(ctx.pos().above(2))){
                for(var input : WorkerContainerAccess.itemHandlers(ctx.level(), ctx.pos().above(2), null)){
                    ports.add(new WorkerItemPort(input, held::test));
                }
            }
            return ports;
        }

        @Override public List<IItemHandler> itemOutputs(WorkerRecipePlan plan){
            return itemOutputs(plan, null);
        }

        @Override public List<IItemHandler> itemOutputs(WorkerRecipePlan plan, WorkerArea area){
            if(be instanceof MillstoneBlockEntity mill) return List.of(mill.outputInv);
            if(plan != null && WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly){
                List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
                List<AssemblyPort> route = assemblyRoute(plan, assembly);
                if(line.isEmpty() || route.isEmpty()) return List.of();
                List<IItemHandler> outputs = new ArrayList<>();
                for(BlockPos receiver : assemblyOutputPositions(line, route.getLast().pos())){
                    if(area == null || area.contains(receiver))
                        outputs.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), receiver, null));
                }
                int lastStage = line.indexOf(route.getLast().pos());
                for(int idx = lastStage; idx < line.size(); idx++){
                    BlockPos pos = line.get(idx);
                    if(area == null || area.contains(pos))
                        outputs.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), pos, null));
                }
                return outputs;
            }
            List<IItemHandler> receivers = new ArrayList<>();
            for(BlockPos pos : outputReceivers()){
                if(area == null || area.contains(pos))
                    receivers.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), pos, null));
            }
            List<IItemHandler> outputs = new ArrayList<>();
            if(be instanceof BasinBlockEntity basin) outputs.add(basin.getOutputInventory());
            else if(be == null && ctx.level().getBlockState(ctx.pos()).isAir())
                outputs.add(new GroundWorkpieceHandler(ctx.level(), ctx.pos()));
            else for(BlockPos pos : members()) outputs.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), pos, null));
            if(area != null && plan != null && plan.stage() < 0){
                List<BlockPos> line = processingLine(ctx.level(), ctx.pos());
                int station = line.indexOf(ctx.pos());
                for(int idx = station; idx >= 0 && idx < line.size(); idx++){
                    BlockPos segment = line.get(idx);
                    if(!area.contains(segment)) break;
                    outputs.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), segment, null));
                }
            }
            outputs.addAll(receivers);
            return outputs;
        }

        @Override public List<IItemHandler> recirculationOutputs(WorkerRecipePlan plan){
            return recirculationOutputs(plan, null);
        }

        @Override public List<IItemHandler> recirculationOutputs(WorkerRecipePlan plan, WorkerArea area){
            if(plan == null || !(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly)) return List.of();
            return itemOutputs(plan, area).stream().map(handler -> (IItemHandler)new WorkerItemOutput(handler,
                    stack -> CreateWorkerSequences.recirculates(plan, assembly, stack))).toList();
        }

        @Override public List<IFluidHandler> fluidInputs(WorkerRecipePlan plan){
            if(plan != null && WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof SequencedAssemblyRecipe assembly){
                List<IFluidHandler> ports = new ArrayList<>();
                for(AssemblyPort port : assemblyRoute(plan, assembly)){
                    if(!(WorkerRecipeCatalog.recipe(ctx.level(), port.plan()) instanceof ProcessingRecipe<?, ?> processing)) continue;
                    var inputs = new Processor(new WorkerMachineRegistry.Context(ctx.level(), port.pos(), null),
                            ctx.level().getBlockEntity(port.pos())).fluidInputs(port.plan());
                    for(var fluid : processing.getFluidIngredients()){
                        int amount = (int)Math.min(Integer.MAX_VALUE, (long)fluid.amount() * assembly.getLoops());
                        for(var input : inputs) ports.add(new WorkerFluidPort(input, fluid.ingredient()::test, amount));
                    }
                }
                return ports;
            }
            if(be instanceof BasinBlockEntity basin) return List.of(basin.getTanks().getFirst().getPrimaryHandler());
            if(plan != null && plan.processorType().toString().equals("create:filling")){
                return WorkerContainerAccess.fluidHandlers(ctx.level(), ctx.pos().above(2), null);
            }
            return WorkerContainerAccess.fluidHandlers(ctx.level(), ctx.pos(), ctx.side());
        }

        @Override public List<IFluidHandler> fluidOutputs(WorkerRecipePlan plan){
            if(be instanceof BasinBlockEntity basin){
                List<IFluidHandler> outputs = new ArrayList<>();
                outputs.add(basin.getTanks().getSecond().getPrimaryHandler());
                for(BlockPos pos : outputReceivers()) outputs.addAll(WorkerContainerAccess.fluidHandlers(ctx.level(), pos, null));
                return outputs;
            }
            return WorkerContainerAccess.fluidHandlers(ctx.level(), ctx.pos(), ctx.side());
        }

        @Override public Supply supply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available){
            if(!(be instanceof BasinBlockEntity) || plan == null) return Supply.READY;
            if(!(WorkerRecipeCatalog.recipe(ctx.level(), plan) instanceof ProcessingRecipe<?, ?> recipe))
                return Supply.READY;
            HeatCondition required = recipe.getRequiredHeat();
            if(required == HeatCondition.NONE || required.testBlazeBurner(BasinBlockEntity.getHeatLevelOf(
                    ctx.level().getBlockState(ctx.pos().below())))) return Supply.READY;
            if(!(ctx.level().getBlockEntity(ctx.pos().below()) instanceof BlazeBurnerBlockEntity))
                return new Supply(null, 0L, "A blaze burner is required below the basin");
            return available.entrySet().stream().filter(entry -> entry.getValue() > 0L
                            && entry.getKey().type() == WorkerResourceType.ITEM)
                    .filter(entry -> burnerFuel(new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey().id())),
                            required == HeatCondition.SUPERHEATED))
                    .filter(entry -> new BurnerFuelPort(ctx.level(), ctx.pos().below()).insertItem(0,
                            new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey().id())), true).isEmpty())
                    .map(entry -> new Supply(entry.getKey(), 1L, "Feeding blaze burner"))
                    .findFirst().orElse(new Supply(null, 0L, required == HeatCondition.SUPERHEATED
                            ? "No usable superheated blaze-burner fuel is stocked"
                            : "No usable blaze-burner fuel is stocked"));
        }

        // Follow physical spouts, belt ends and collecting funnels to their receiving inventories
        private List<BlockPos> outputReceivers(){
            java.util.Set<BlockPos> receivers = new java.util.LinkedHashSet<>();
            if(be instanceof BasinBlockEntity){
                Direction facing = be.getBlockState().getValue(BasinBlock.FACING);
                if(facing != Direction.DOWN) receivers.add(ctx.pos().below().relative(facing));
            }else if(be instanceof SawBlockEntity saw){
                receivers.add(ctx.pos().offset(BlockPos.containing(saw.getItemMovementVec())));
            }else if(be instanceof CrushingWheelControllerBlockEntity){
                Direction facing = be.getBlockState().getValue(BlockStateProperties.FACING);
                receivers.add(ctx.pos().below().relative(facing, facing.getAxis() == Direction.Axis.Y ? 0 : 1));
            }else if(be instanceof BeltBlockEntity belt){
                List<BlockPos> segments = movingBeltSegments(ctx.level(), belt);
                if(!segments.isEmpty()){
                    BlockPos end = segments.getLast();
                    if(ctx.level().getBlockEntity(end) instanceof BeltBlockEntity segment){
                        receivers.add(end.relative(segment.getMovementFacing()));
                    }
                }
                for(BlockPos pos : segments){
                    if(!(ctx.level().getBlockEntity(pos) instanceof BeltBlockEntity segment)) continue;
                    var funnel = ctx.level().getBlockState(pos.above());
                    if(!(funnel.getBlock() instanceof BeltFunnelBlock)) continue;
                    var shape = funnel.getValue(BeltFunnelBlock.SHAPE);
                    Direction facing = AbstractFunnelBlock.getFunnelFacing(funnel);
                    if(shape == BeltFunnelBlock.Shape.PULLING || shape != BeltFunnelBlock.Shape.PUSHING
                            && facing != segment.getMovementFacing()) receivers.add(pos.above().relative(facing.getOpposite()));
                }
            }
            return List.copyOf(receivers);
        }

        // Locate collectors after the final station without treating upstream feeders as outputs
        private List<BlockPos> assemblyOutputPositions(List<BlockPos> line, BlockPos finalStation){
            int first = line.indexOf(finalStation);
            if(first < 0) return List.of();
            LinkedHashSet<BlockPos> outputs = new LinkedHashSet<>();
            for(int idx = first; idx < line.size(); idx++){
                BlockPos pos = line.get(idx);
                BlockEntity station = ctx.level().getBlockEntity(pos);
                if(station instanceof BeltBlockEntity belt){
                    var funnel = ctx.level().getBlockState(pos.above());
                    if(funnel.getBlock() instanceof BeltFunnelBlock){
                        var shape = funnel.getValue(BeltFunnelBlock.SHAPE);
                        Direction facing = AbstractFunnelBlock.getFunnelFacing(funnel);
                        if(shape == BeltFunnelBlock.Shape.PULLING || shape != BeltFunnelBlock.Shape.PUSHING
                                && facing != belt.getMovementFacing())
                            outputs.add(pos.above().relative(facing.getOpposite()));
                    }
                    if(idx == line.size() - 1) outputs.add(pos.relative(belt.getMovementFacing()));
                }else if(station instanceof SawBlockEntity saw && idx == line.size() - 1){
                    outputs.add(pos.offset(BlockPos.containing(saw.getItemMovementVec())));
                }
            }
            return List.copyOf(outputs);
        }

        // A marked deployer or other stage supply is not an inlet for the moving workpiece.
        private boolean isWorkpieceInput(BlockPos input, List<BlockPos> upstream, List<AssemblyPort> route){
            if(route.stream().anyMatch(port -> input.equals(port.pos().above(2)))) return false;
            // A marked container may feed the upstream belt through a chute, arm or another mod's transport.
            // Belt marks themselves must still be before the first station.
            return !(ctx.level().getBlockEntity(input) instanceof BeltBlockEntity) || upstream.contains(input);
        }

        // Match the ordered machine stations on one physical belt before feeding a complete assembly
        private List<AssemblyPort> assemblyRoute(WorkerRecipePlan plan, SequencedAssemblyRecipe assembly){
            if(!(be instanceof BeltBlockEntity || be instanceof SawBlockEntity)) return List.of();
            List<WorkerRecipePlan> stages = CreateWorkerSequences.stages(ctx.level(), plan, assembly);
            if(stages.size() < assembly.getSequence().size()) return List.of();
            List<BlockPos> segments = processingLine(ctx.level(), ctx.pos());
            if(segments.isEmpty()) return List.of();
            List<AssemblyPort> route = new ArrayList<>();
            int idx = 0;
            for(BlockPos pos : segments){
                if(idx >= assembly.getSequence().size()) break;
                WorkerRecipePlan stage = stages.get(idx);
                var processor = new Processor(new WorkerMachineRegistry.Context(ctx.level(), pos, null), ctx.level().getBlockEntity(pos));
                if(!processor.supports(stage)) continue;
                var processing = WorkerRecipeCatalog.recipe(ctx.level(), stage);
                if(processing instanceof ProcessingRecipe<?, ?> recipe
                        && !recipe.getFluidIngredients().isEmpty() && processor.fluidInputs(stage).isEmpty())
                    return List.of();
                route.add(new AssemblyPort(pos, stage));
                idx++;
            }
            return idx == assembly.getSequence().size() ? route : List.of();
        }

    }

    private record AssemblyPort(BlockPos pos, WorkerRecipePlan plan){}

    // Match Create's data-driven fuel rules before choosing a regular or superheated supply.
    private static boolean burnerFuel(ItemStack stack, boolean superheated){
        if(stack.isEmpty()) return false;
        var holder = stack.getItem().builtInRegistryHolder();
        boolean special = holder.getData(CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS) != null
                || AllTags.AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.matches(stack);
        if(superheated) return special;
        return special || holder.getData(CreateDataMaps.REGULAR_BLAZE_BURNER_FUELS) != null
                || AllTags.AllItemTags.BLAZE_BURNER_FUEL_REGULAR.matches(stack)
                || stack.getBurnTime(null) > 0;
    }

    // Present the real burner interaction as a one-slot supply port; simulation never consumes fuel.
    private record BurnerFuelPort(Level level, BlockPos pos) implements IItemHandler{
        @Override public int getSlots(){ return 1; }
        @Override public ItemStack getStackInSlot(int slot){ return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot){ return slot == 0 ? 1 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack){
            return slot == 0 && burnerFuel(stack, false);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate){ return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){
            if(slot != 0 || stack.isEmpty() || !burnerFuel(stack, false)
                    || !(level.getBlockState(pos).getBlock() instanceof BlazeBurnerBlock)) return stack;
            ItemStack offered = stack.copyWithCount(1);
            var result = BlazeBurnerBlock.tryInsert(level.getBlockState(pos), level, pos,
                    offered, false, false, simulate);
            if(result.getResult() != InteractionResult.SUCCESS) return stack;
            return stack.copyWithCount(stack.getCount() - 1);
        }
    }
}
