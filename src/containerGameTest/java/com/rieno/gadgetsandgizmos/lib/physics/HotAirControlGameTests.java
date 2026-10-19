package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.compat.aeronautics.AeronauticsHotAirApi;
import com.rieno.gadgetsandgizmos.lib.control.LiftingGasControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("gadgetsngizmos")
@PrefixGameTestTemplate(false)
public final class HotAirControlGameTests{
    private HotAirControlGameTests(){}

    @GameTest(template = "empty_container", batch = "scm_hot_air")
    public static void twoBurnersShareNativeBalloonCapacityAndRetainHalfLift(GameTestHelper ctx){
        if(!ModList.get().isLoaded("aeronautics")){
            ctx.assertTrue(AeronauticsHotAirApi.createProbes(null, "redstone_block_control").isEmpty(),
                    "Absent Aeronautics must not produce a control");
            ctx.succeed();
            return;
        }
        for(int x = 1; x <= 7; x++){
            for(int y = 2; y <= 7; y++){
                for(int z = 1; z <= 7; z++){
                    ctx.setBlock(new BlockPos(x, y, z), x == 1 || x == 7 || y == 2 || y == 7 || z == 1 || z == 7
                            ? Blocks.WHITE_WOOL : Blocks.AIR);
                }
            }
        }
        BlockPos a = new BlockPos(2, 3, 2);
        BlockPos b = new BlockPos(5, 3, 5);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aeronautics:adjustable_burner"));
        ctx.setBlock(a, block);
        ctx.setBlock(b, block);
        ctx.runAfterDelay(2, () -> {
            BlockEntity first = ctx.getBlockEntity(a);
            BlockEntity second = ctx.getBlockEntity(b);
            var left = AeronauticsHotAirApi.createProbes(first, "redstone_block_control").getFirst();
            var right = AeronauticsHotAirApi.createProbes(second, "redstone_block_control").getFirst();
            left.apply(1);
            right.apply(1);
            invoke(first, "tickBalloonLogic");
            invoke(second, "tickBalloonLogic");
            Object balloon = invoke(first, "getBalloon");
            ctx.assertTrue(balloon != null && balloon == invoke(second, "getBalloon"), "Burners did not join one native enclosure");
            ctx.runAfterDelay(1, () -> {
                try{
                    double capacity = number(balloon, "getCapacity");
                    ctx.assertTrue(capacity > 0, "Native enclosure has no volume");
                    left.apply(0.5);
                    right.apply(0.5);
                    double target = number(first, "getGasOutput") + number(second, "getGasOutput");
                    ctx.assertTrue(Math.abs(target - capacity * 0.5) < 1.0E-6,
                            "Shared enclosure capacity was multiplied or gas targets were rounded");
                    ctx.assertTrue(left.localEffectPosition().equals(invoke(balloon, "getCenter")),
                            "Burner reported its nozzle instead of the balloon's center of lift");
                    ctx.assertTrue(left.localEffectDirection().y > 0.9999, "Burner lift followed its horizontal facing");
                    for(int tick = 0; tick < 1600; tick++){
                        invoke(balloon, "tick");
                        invoke(balloon, "updateGasAmounts");
                    }
                    ctx.assertTrue(Math.abs(number(balloon, "getTotalFilledVolume") - target) < 0.001,
                            "Native filling and cooling did not settle to the allocated lift volume");
                }finally{
                    left.restore();
                    right.restore();
                }
                ctx.succeed();
            });
        });
    }

    @GameTest(template = "empty_container", batch = "scm_hot_air")
    public static void burnerUsesExactGasTargetsAndRestoresNativeRedstone(GameTestHelper ctx){
        if(!ModList.get().isLoaded("aeronautics")){
            ctx.assertTrue(AeronauticsHotAirApi.createProbes(null, "redstone_block_control").isEmpty(),
                    "Absent Aeronautics must not produce a control");
            ctx.succeed();
            return;
        }
        BlockPos pos = new BlockPos(2, 2, 2);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aeronautics:adjustable_burner"));
        ctx.assertTrue(block != Blocks.AIR, "Native adjustable burner is missing");
        ctx.setBlock(pos, block);
        ctx.runAfterDelay(2, () -> {
            BlockEntity burner = ctx.getBlockEntity(pos);
            ctx.assertTrue(burner instanceof LiftingGasControl, "Optional hot-air control mixin was not applied");
            var probes = AeronauticsHotAirApi.createProbes(burner, "redstone_block_control");
            ctx.assertTrue(probes.size() == 1, "Native burner did not expose one lift control");
            ScmControlProbe probe = probes.getFirst();
            try{
                ScrollValueBehaviour volume = volume(burner);
                int maximum = volume.value;
                ctx.assertTrue(probe.maximumForce() > 0, "Cold burner has no startup lift capacity");
                ctx.assertTrue(Math.abs(probe.responseTimeSeconds() - 9.0D) < 1.0E-9, "Native warmup time was not exposed to Hover");
                probe.apply(0.376239);
                double expected = maximum * 0.376239;
                ctx.assertTrue(Math.abs(number(burner, "getGasOutput") - expected) < 1.0E-6,
                        "Gas target was quantized to a redstone step");
                ctx.assertTrue(!burner.saveWithoutMetadata(ctx.getLevel().registryAccess()).contains("GGLControlledGasVolume"),
                        "A transient control owner was persisted into the chunk");
                ctx.assertTrue(Math.abs(burner.getUpdateTag(ctx.getLevel().registryAccess()).getDouble("GGLControlledGasVolume") - expected) < 1.0E-6,
                        "Client update lost the exact gas target");
                ctx.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
                invoke(burner, "updateSignal");
                ctx.assertTrue(Math.abs(number(burner, "getGasOutput") - expected) < 1.0E-6,
                        "Neighbor update overwrote the SCM target");
                ctx.assertTrue(volume.value == maximum, "SCM changed the player's configured gas volume");
                probe.restore();
                ctx.assertTrue(number(burner, "getSignalStrength") == 15, "Release did not restore live redstone");
                ctx.assertTrue(number(burner, "getGasOutput") == maximum, "Release retained a stale gas override");
                ctx.setBlock(pos.east(), Blocks.AIR);
                invoke(burner, "updateSignal");
                ctx.assertTrue(number(burner, "getGasOutput") == 0, "Ordinary redstone stopped responding after release");
            }finally{
                probe.restore();
            }
            ctx.succeed();
        });
    }

    private static ScrollValueBehaviour volume(Object burner){
        for(Class<?> type = burner.getClass(); type != null; type = type.getSuperclass()){
            try{
                var field = type.getDeclaredField("hotAirAmountBehaviour");
                field.setAccessible(true);
                return (ScrollValueBehaviour)field.get(burner);
            }catch(NoSuchFieldException ignored){}
            catch(IllegalAccessException ex){ throw new IllegalStateException(ex); }
        }
        throw new IllegalStateException("Native gas-volume setting is missing");
    }

    private static Object invoke(Object target, String name){
        try{ return target.getClass().getMethod(name).invoke(target); }
        catch(ReflectiveOperationException ex){ throw new IllegalStateException(ex); }
    }

    private static double number(Object target, String name){ return ((Number)invoke(target, name)).doubleValue(); }
}
