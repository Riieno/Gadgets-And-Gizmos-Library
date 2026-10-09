package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

// Describe reusable block templates without owning plots or stored inventories
public record SubLevelSchematic(List<Body> bodies, List<Joint> joints){
    // Preserve block-only templates for existing consumers
    public SubLevelSchematic(List<Body> bodies){ this(bodies, List.of()); }
    // Keep the template body list immutable
    public SubLevelSchematic{
        bodies = List.copyOf(bodies); joints = List.copyOf(joints);
        var ids = new HashSet<UUID>();
        bodies.forEach(body -> ids.add(body.id()));
        if(joints.size() > 1024) throw new IllegalArgumentException("Schematic exceeds the weld limit");
        for(var joint : joints) if(!ids.contains(joint.first()) || !ids.contains(joint.second()))
            throw new IllegalArgumentException("Schematic weld refers to a missing body");
    }

    // Count every block across the connected assembly
    public int blockCount(){ return bodies.stream().mapToInt(body -> body.blocks().size()).sum(); }

    // Send a bounded block-only preview in the assembly's local frame
    public ListTag preview(int limit){
        ListTag rows = new ListTag();
        int stride = Math.max(1, (blockCount() + Math.max(1, limit) - 1) / Math.max(1, limit));
        int idx = 0;
        for(Body body : bodies) for(Block block : body.blocks()){
            if(idx++ % stride != 0 || rows.size() >= limit) continue;
            var point = body.orientation().transform(new org.joml.Vector3d(
                    block.pos().getX() + 0.5, block.pos().getY() + 0.5, block.pos().getZ() + 0.5));
            CompoundTag row = new CompoundTag();
            row.putUUID("Body", body.id());
            row.putLong("Pos", block.pos().asLong());
            row.put("State", NbtUtils.writeBlockState(block.state()));
            row.putDouble("X", body.corner().x + point.x - 0.5);
            row.putDouble("Y", body.corner().y + point.y - 0.5);
            row.putDouble("Z", body.corner().z + point.z - 0.5);
            Quaterniond turn = body.orientation();
            row.putFloat("Qx", (float) turn.x); row.putFloat("Qy", (float) turn.y);
            row.putFloat("Qz", (float) turn.z); row.putFloat("Qw", (float) turn.w);
            rows.add(row);
        }
        return rows;
    }

    // Retain one template body's identity and pose for reference remapping
    public record Body(UUID id, Vec3 corner, Quaterniond orientation, BlockPos size, List<Block> blocks, ImportFrame importFrame){
        // Preserve the native body constructor for existing consumers
        public Body(UUID id, Vec3 corner, Quaterniond orientation, BlockPos size, List<Block> blocks){
            this(id, corner, orientation, size, blocks, new ImportFrame(Format.NATIVE, id, BlockPos.ZERO, -1));
        }
        // Copy mutable pose and block data at the API boundary
        public Body{
            orientation = new Quaterniond(orientation);
            size = size.immutable();
            blocks = List.copyOf(blocks);
            importFrame = importFrame == null ? new ImportFrame(Format.NATIVE, id, BlockPos.ZERO, -1) : importFrame;
        }
        // Return an independent orientation
        @Override public Quaterniond orientation(){ return new Quaterniond(orientation); }
    }

    // Identify the source coordinate system until safe NBT conversion finishes
    public record ImportFrame(Format format, UUID originalId, BlockPos origin, int blueprintId){
        public ImportFrame{ origin = origin.immutable(); }
    }

    // Distinguish portable structure data from foreign blueprint configuration
    public enum Format{ NATIVE, PHOTOMANCY, TOOLGUN }

    // Retain body-local attachment points for imported Toolgun welds
    public record Joint(UUID first, UUID second, JointType type, Vec3 firstAnchor, Vec3 secondAnchor,
                        Quaterniond orientation, Vec3 firstAxis, Vec3 secondAxis){
        public Joint{
            if(first == null || second == null || first.equals(second) || type == null
                    || !finite(firstAnchor) || !finite(secondAnchor) || orientation == null || !orientation.isFinite()
                    || Math.abs(orientation.lengthSquared() - 1) > 0.01)
                throw new IllegalArgumentException("Invalid schematic weld");
            orientation = new Quaterniond(orientation);
            if(Math.abs(orientation.lengthSquared() - 1) > 1E-12) orientation.normalize();
            if(type == JointType.BEARING){
                if(!finite(firstAxis) || !finite(secondAxis) || firstAxis.lengthSqr() < 1E-12 || secondAxis.lengthSqr() < 1E-12)
                    throw new IllegalArgumentException("Invalid schematic bearing axes");
                if(Math.abs(firstAxis.lengthSqr() - 1) > 1E-12) firstAxis = firstAxis.normalize();
                if(Math.abs(secondAxis.lengthSqr() - 1) > 1E-12) secondAxis = secondAxis.normalize();
            }else{ firstAxis = new Vec3(0, 1, 0); secondAxis = firstAxis; }
        }
        @Override public Quaterniond orientation(){ return new Quaterniond(orientation); }
        private static boolean finite(Vec3 val){ return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z); }
    }

    // Describe native attachment modes without exposing Toolgun classes
    public enum JointType{ FIXED, FREE, BEARING }

    // Retain safe block entity configuration alongside one block state
    public record Block(BlockPos pos, BlockState state, CompoundTag data){
        // Copy mutable block data at the API boundary
        public Block{
            pos = pos.immutable();
            data = data == null ? new CompoundTag() : data.copy();
        }
        // Return independent block entity configuration
        @Override public CompoundTag data(){ return data.copy(); }
    }
}
