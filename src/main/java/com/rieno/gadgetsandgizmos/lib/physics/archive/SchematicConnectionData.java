package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Preserve kinetic endpoints independently of stored inventories and running networks
public final class SchematicConnectionData{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String TAG_KEY = "gadgetsngizmos:connections";

    // Keep connection conversion on the static API
    private SchematicConnectionData(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Describe one source plot and its portable template identity
    public record BodyFrame(UUID sourceId, UUID templateId, BlockPos origin, BlockPos size, net.minecraft.world.phys.Vec3 worldOrigin){
        // Preserve endpoint-only frame construction
        public BodyFrame(UUID sourceId, UUID templateId, BlockPos origin, BlockPos size){
            this(sourceId, templateId, origin, size, net.minecraft.world.phys.Vec3.ZERO);
        }
        // Retain immutable bounds for endpoint resolution
        public BodyFrame{ origin = origin.immutable(); size = size.immutable(); }

        // Check whether a source endpoint belongs to this template body
        public boolean contains(BlockPos pos){
            BlockPos local = pos.subtract(origin);
            return local.getX() >= 0 && local.getY() >= 0 && local.getZ() >= 0
                    && local.getX() < size.getX() && local.getY() < size.getY() && local.getZ() < size.getZ();
        }
    }

    // Retain portable endpoints after Create filters the ordinary safe block data
    public static void capture(ServerLevel level, BlockEntity be, CompoundTag src, CompoundTag safe,
                               Collection<BodyFrame> frames){
        capture(level, be, src, safe, null, frames);
    }

    // Prefer the owning body when imported templates share local coordinate bounds
    public static void capture(ServerLevel level, BlockEntity be, CompoundTag src, CompoundTag safe,
                               UUID sourceId, Collection<BodyFrame> frames){
        if(be == null) return;
        SchematicLinkData.capture(be, src, safe, sourceId, frames);
        if(src.contains(TAG_KEY, Tag.TAG_COMPOUND)){
            safe.put(TAG_KEY, src.getCompound(TAG_KEY).copy());
            if(be instanceof ChainConveyorBlockEntity) safe.remove("Connections");
            if(be instanceof BeltBlockEntity) safe.remove("Controller");
            return;
        }
        CompoundTag data = new CompoundTag();
        if(be instanceof ChainConveyorBlockEntity chain){
            ListTag endpoints = new ListTag();
            for(BlockPos offset : chain.connections){
                CompoundTag endpoint = reference(sourceId, chain.getBlockPos().offset(offset), frames);
                if(endpoint != null) endpoints.add(endpoint);
            }
            data.putString("Type", "chain");
            data.put("Endpoints", endpoints);
            safe.remove("Connections");
        }else if(be instanceof BeltBlockEntity belt){
            CompoundTag endpoint = reference(sourceId, belt.getController(), frames);
            if(endpoint == null) return;
            CompoundTag raw = new CompoundTag();
            belt.write(raw, level.registryAccess(), false);
            for(String key : List.of("IsController", "Length", "Index", "Casing", "Covered", "Dye")){
                Tag val = raw.get(key);
                if(val != null) data.put(key, val.copy());
            }
            data.putString("Type", "belt");
            data.put("Controller", endpoint);
            safe.remove("Controller");
        }else return;
        safe.put(TAG_KEY, data);
    }

    // Restore connections only after every endpoint block has been placed
    public static void restore(ServerLevel level, BlockPos pos, CompoundTag saved,
                               SubLevelSchematicSerializationContext ctx){
        restore(level, pos, saved, ctx, net.minecraft.world.phys.Vec3.ZERO, new org.joml.Quaterniond());
    }

    // Recreate declared world geometry at the chosen schematic placement
    public static void restore(ServerLevel level, BlockPos pos, CompoundTag saved,
                               SubLevelSchematicSerializationContext ctx, net.minecraft.world.phys.Vec3 target,
                               org.joml.Quaterniondc rotation){
        SchematicLinkData.restore(level, pos, saved, ctx, target, rotation);
        CompoundTag data = saved.getCompound(TAG_KEY);
        BlockEntity be = level.getBlockEntity(pos);
        if("chain".equals(data.getString("Type")) && be instanceof ChainConveyorBlockEntity chain){
            chain.connections.clear();
            for(Tag raw : data.getList("Endpoints", Tag.TAG_COMPOUND)){
                if(!(raw instanceof CompoundTag endpoint)) continue;
                BlockPos linkedPos = resolve(endpoint, ctx);
                if(linkedPos != null && level.getBlockEntity(linkedPos) instanceof ChainConveyorBlockEntity)
                    chain.connections.add(linkedPos.subtract(pos));
            }
            chain.connectionStats = null;
            chain.notifyUpdate();
        }else if("belt".equals(data.getString("Type")) && be instanceof BeltBlockEntity belt){
            BlockPos controller = resolve(data.getCompound("Controller"), ctx);
            if(controller == null || !(level.getBlockEntity(controller) instanceof BeltBlockEntity)) return;
            CompoundTag config = data.copy();
            config.remove("Type"); config.remove("Controller");
            belt.loadWithComponents(config, level.registryAccess());
            // Apply topology after Create clears kinetic information for moved block entities
            belt.setController(controller);
            belt.beltLength = data.getInt("Length");
            belt.index = data.getInt("Index");
            belt.notifyUpdate();
        }
    }

    // Encode only endpoints that are included in the exported assembly
    public static CompoundTag reference(BlockPos pos, Collection<BodyFrame> frames){
        return reference(null, pos, frames);
    }

    // Resolve same-body links before considering another template's overlapping bounds
    public static CompoundTag reference(UUID sourceId, BlockPos pos, Collection<BodyFrame> frames){
        if(pos == null || frames == null) return null;
        if(sourceId != null) for(BodyFrame frame : frames){
            if(sourceId.equals(frame.sourceId()) && frame.contains(pos)) return reference(pos, List.of(frame));
        }
        for(BodyFrame frame : frames){
            if(!frame.contains(pos)) continue;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Body", frame.templateId());
            tag.putLong("Pos", pos.subtract(frame.origin()).asLong());
            return tag;
        }
        return null;
    }

    // Resolve one portable endpoint through the complete placement mapping
    public static BlockPos resolve(CompoundTag endpoint, SubLevelSchematicSerializationContext ctx){
        if(ctx == null || !endpoint.hasUUID("Body") || !endpoint.contains("Pos", Tag.TAG_LONG)) return null;
        var mapping = ctx.getMapping(endpoint.getUUID("Body"));
        return mapping == null ? null : mapping.transform().apply(BlockPos.of(endpoint.getLong("Pos")));
    }
}
