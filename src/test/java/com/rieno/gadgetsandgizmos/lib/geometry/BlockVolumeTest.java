package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep a growing column identical through every forwarding block
class BlockVolumeTest{
    private static final BlockPos ORIGIN = new BlockPos(100, 80, -50);

    // Selection from any occupied cell must expose the full box instead of a local segment
    @Test void allOccupiedCellsReturnTheSameCompleteVolume(){
        for(Direction facing : Direction.values()){
            for(double extension : new double[]{0, .25, .75, 1, 1.5, 5.75, 32}){
                var volume = BlockVolume.column(ORIGIN, facing, extension, .75, 1);
                AABB expected = volume.bounds().move(ORIGIN);
                assertEquals(extension + .25,
                        volume.bounds().max(facing.getAxis()) - volume.bounds().min(facing.getAxis()), 1E-12);
                for(int distance = 0; distance <= 33; distance++){
                    BlockPos pos = ORIGIN.relative(facing.getOpposite(), distance);
                    var shape = volume.shapeAt(pos);
                    if(expected.intersects(new AABB(pos))){
                        assertFalse(shape.isEmpty());
                        assertEquals(1, shape.toAabbs().size());
                        assertEquals(expected, shape.bounds().move(pos));
                    }else{
                        assertTrue(shape.isEmpty(), "A retracted cell still published a hitbox");
                    }
                }
            }
        }
    }

    // Both ends must remain selectable across former block and scissor-joint gaps
    @Test void raysHitTheContinuousColumnAtEveryHeight(){
        for(double extension : new double[]{.25, .75, 1, 1.5, 5.75, 32}){
            var volume = BlockVolume.column(BlockPos.ZERO, Direction.UP, extension, .75, 1);
            for(double height = volume.bounds().minY + .01; height < volume.bounds().maxY; height += .13){
                Vec3 from = new Vec3(-2, height, .5), to = new Vec3(2, height, .5);
                BlockHitResult hit = trace(volume, from, to, null);
                assertNotNull(hit, "A ray missed the column at " + height);
                assertEquals(0, hit.getLocation().x, 1E-8);
                assertEquals(height, hit.getLocation().y, 1E-8);
            }
        }
    }

    // Hidden proxy cells must not let a distant volume bypass a nearer obstruction
    @Test void retractedCellsCannotHideANearerSolidBlock(){
        var volume = BlockVolume.column(BlockPos.ZERO, Direction.UP, 2, .75, 1);
        BlockPos wall = new BlockPos(0, -3, 0);
        Vec3 from = new Vec3(.5, -6, .5), to = new Vec3(.5, 3, .5);
        var hit = trace(volume, from, to, wall);
        assertNotNull(hit);
        assertEquals(wall, hit.getBlockPos());
        assertEquals(-3, hit.getLocation().y, 1E-8);
    }

    // The actual head plate must retain its own breaking interaction
    @Test void headFacesRemainDistinctFromTheArmColumn(){
        var volume = BlockVolume.column(BlockPos.ZERO, Direction.UP, 5, .75, 1);
        var head = Shapes.box(0, .75, 0, 1, 1, 1);
        assertTrue(volume.hitBefore(new Vec3(-2, .5, .5), new Vec3(2, .5, .5), head));
        assertFalse(volume.hitBefore(new Vec3(-2, .9, .5), new Vec3(2, .9, .5), head));
        assertFalse(volume.hitBefore(new Vec3(.5, 3, .5), new Vec3(.5, -6, .5), head));
    }

    // Reject malformed dimensions before exposing them to Minecraft collision code
    @Test void invalidVolumesAreRejected(){
        assertThrows(IllegalArgumentException.class,
                () -> BlockVolume.column(ORIGIN, Direction.UP, Double.NaN, .75, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BlockVolume.column(ORIGIN, Direction.UP, -1, .75, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockVolume(ORIGIN, new AABB(0, 0, 0, 0, 1, 1)));
    }

    // Use Minecraft's cell traversal with one shared column and an optional ordinary obstruction
    private static BlockHitResult trace(BlockVolume volume, Vec3 from, Vec3 to, BlockPos wall){
        return BlockGetter.traverseBlocks(from, to, volume, (ctx, pos) -> {
            if(pos.equals(wall)) return Shapes.block().clip(from, to, pos);
            if(pos.getX() != volume.origin().getX() || pos.getZ() != volume.origin().getZ()
                    || pos.getY() > volume.origin().getY() || pos.getY() < volume.origin().getY() - 33) return null;
            return ctx.shapeAt(pos).clip(from, to, pos);
        }, ctx -> null);
    }
}
