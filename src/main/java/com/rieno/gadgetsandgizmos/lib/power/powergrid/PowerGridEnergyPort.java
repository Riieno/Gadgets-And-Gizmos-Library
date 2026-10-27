package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Locate a scaled device connector independently of the owning block's implementation
public record PowerGridEnergyPort(int index, BlockPos owner, Vec3 center, Direction facing, double scale){
    public PowerGridEnergyPort{
        if(index < 0 || scale <= 0 || !Double.isFinite(scale)) throw new IllegalArgumentException("Invalid connector geometry");
        owner = owner.immutable();
    }

    public Vec3 anchor(){
        var inward = facing.getOpposite();
        return new Vec3(.5 + inward.getStepX() * .5, .5 + inward.getStepY() * .5, .5 + inward.getStepZ() * .5);
    }

    public Vec3 project(Vec3 connectorPos){ return center.add(connectorPos.subtract(anchor()).scale(scale)); }
    public Vec3 unproject(Vec3 worldPos){ return worldPos.subtract(center).scale(1 / scale).add(anchor()); }

    // Resolve the optional model through the registry without linking native electrical classes
    public BlockState connectorState(){
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid", "device_connector"));
        var state = block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, facing.getOpposite());
        // Keep the two terminals side by side beneath a mounted screen
        for(var property : state.getProperties()){
            if(property instanceof IntegerProperty rotation && property.getName().equals("rotation")
                    && rotation.getPossibleValues().contains(1)) state = state.setValue(rotation, 1);
        }
        return state;
    }

    // Include both terminals, including the small protrusion beyond the housing
    public AABB bounds(){
        double half = .5 * scale, depth = .25 * scale;
        Vec3 middle = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(depth / 2));
        double x = facing.getAxis() == Direction.Axis.X ? depth / 2 : half;
        double y = facing.getAxis() == Direction.Axis.Y ? depth / 2 : half;
        double z = facing.getAxis() == Direction.Axis.Z ? depth / 2 : half;
        return new AABB(middle.x - x, middle.y - y, middle.z - z, middle.x + x, middle.y + y, middle.z + z);
    }
}
