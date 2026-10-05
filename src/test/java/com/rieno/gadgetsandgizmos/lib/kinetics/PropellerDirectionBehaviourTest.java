package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verify propulsion reversal, saved direction and body projection
class PropellerDirectionBehaviourTest {
    // Initialize Minecraft registries for Create behaviours
    @BeforeAll
    static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(java.util.List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    // Reverse propulsion for either shaft direction on every bearing orientation
    @Test
    void reversingPropulsionPreservesMagnitude(){
        for(Direction facing : Direction.values()){
            for(double speed : new double[]{-256.0D, 0.0D, 128.0D}){
                double pull = PropellerThrustDirection.PULL_WHEN_CLOCKWISE.signedSpeed(facing, speed);
                double push = PropellerThrustDirection.PUSH_WHEN_CLOCKWISE.signedSpeed(facing, speed);
                assertEquals(facing.getAxisDirection().getStep() * speed, pull);
                assertEquals(-pull, push);
                assertEquals(Math.abs(speed), Math.abs(push));
            }
        }
    }

    // Preserve the selected direction in client updates and schematics
    @Test
    void savedDirectionUsesItsOwnKeyAndDefaultsOlderBearings(){
        var direction = behaviour();
        direction.setValue(1);
        CompoundTag saved = new CompoundTag();
        saved.putInt("ScrollValue", 42);
        direction.write(saved, null, true);
        assertEquals(42, saved.getInt("ScrollValue"));
        var restored = behaviour();
        restored.read(saved, null, true);
        assertEquals(PropellerThrustDirection.PUSH_WHEN_CLOCKWISE, restored.get());

        CompoundTag schematic = new CompoundTag();
        direction.writeSafe(schematic, null);
        assertTrue(direction.isSafeNBT());
        restored.read(schematic, null, false);
        assertEquals(PropellerThrustDirection.PUSH_WHEN_CLOCKWISE, restored.get());
        restored.read(new CompoundTag(), null, false);
        assertEquals(PropellerThrustDirection.PULL_WHEN_CLOCKWISE, restored.get());
    }

    // External controls use the same saved direction and client update path
    @Test
    void externalDirectionValuesUpdateTheBlockSelector(){
        var owner = mock(SmartBlockEntity.class);
        var direction = new PropellerDirectionBehaviour(Component.literal("Direction"),
                owner, "TestThrustDirection");
        assertEquals(java.util.List.of("Toward", "Away"), PropellerThrustDirection.controlOptions());
        assertEquals("Toward", direction.get().controlValue());
        assertTrue(direction.setDirection(" away "));
        assertEquals(PropellerThrustDirection.PUSH_WHEN_CLOCKWISE, direction.get());
        assertEquals("Away", direction.get().controlValue());
        assertFalse(direction.setDirection("invalid"));
        assertFalse(direction.setDirection(null));
        assertEquals(PropellerThrustDirection.PUSH_WHEN_CLOCKWISE, direction.get());
        assertTrue(direction.setDirection("Away"));
        verify(owner, times(1)).setChanged();
        verify(owner, times(1)).sendData();
        assertTrue(direction.setDirection("TOWARD"));
        assertEquals(PropellerThrustDirection.PULL_WHEN_CLOCKWISE, direction.get());
        verify(owner, times(2)).sendData();
    }

    // Keep the projection on the four body faces and match the gyroscopic offset
    @Test
    void projectionMatchesTheGyroscopicBearingOnAllOrientations(){
        var slot = (ValueBoxTransform.Sided) behaviour().getSlotPositioning();
        var reference = new DirectionalExtenderScrollOptionSlot((state, side) ->
                side.getAxis() != state.getValue(BlockStateProperties.FACING).getAxis());
        LevelAccessor level = mock(LevelAccessor.class);
        BlockState state = mock(BlockState.class);
        for(Direction facing : Direction.values()){
            when(state.getValue(BlockStateProperties.FACING)).thenReturn(facing);
            for(Direction side : Direction.values()){
                slot.fromSide(side);
                reference.fromSide(side);
                boolean bodyFace = facing.getAxis() != side.getAxis();
                assertEquals(bodyFace, slot.shouldRender(level, BlockPos.ZERO, state));
                if(!bodyFace) continue;
                Vec3 expected = reference.getLocalOffset(level, BlockPos.ZERO, state)
                        .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-0.125D));
                assertEquals(expected, slot.getLocalOffset(level, BlockPos.ZERO, state));
                assertTrue(slot.testHit(level, BlockPos.ZERO, state, expected));
            }
        }
    }

    // Create an independent selector without an addon block entity
    private static PropellerDirectionBehaviour behaviour(){
        return new PropellerDirectionBehaviour(Component.literal("Direction"),
                mock(SmartBlockEntity.class), "TestThrustDirection");
    }
}
