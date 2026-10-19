package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Retain known positions across unloading without force-loading sublevels for catalogue reads
public final class SubLevelLocator extends SavedData{
    private static final Factory<SubLevelLocator> FACTORY = new Factory<>(SubLevelLocator::new, SubLevelLocator::load);
    private final Map<UUID, Location> locations = new LinkedHashMap<>();

    public static SubLevelLocator get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(FACTORY, "gadgetsngizmos_sublevel_locations");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture live positions; missing bodies retain an explicitly last-known location
    public void refresh(ServerLevel level){
        var bodies = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getAllSubLevels();
        for(var body : bodies){
            if(body == null || body.isRemoved() || body.getUniqueId() == null || body.getPlot() == null) continue;
            if(locations.size() >= 16384 && !locations.containsKey(body.getUniqueId())) continue;
            var pos = body.logicalPose().position();
            locations.put(body.getUniqueId(), new Location(body.getUniqueId(), body.getName(), new Vec3(pos.x, pos.y, pos.z),
                    body.getPlot().getCenterBlock(), body.boundingBox().maxY(), level.getGameTime()));
        }
        if(!bodies.isEmpty()) setDirty();
    }

    public Location find(UUID id){ return locations.get(id); }
    public List<Location> locations(){ return List.copyOf(locations.values()); }
    public void remove(UUID id){ if(locations.remove(id) != null) setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider){
        ListTag rows = new ListTag();
        locations.values().forEach(val -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("Id", val.id()); row.putString("Name", val.name());
            row.putDouble("X", val.position().x); row.putDouble("Y", val.position().y); row.putDouble("Z", val.position().z);
            row.putLong("Plot", val.plotCenter().asLong()); row.putDouble("Top", val.top()); row.putLong("Seen", val.seen());
            rows.add(row);
        });
        tag.put("Locations", rows);
        return tag;
    }

    private static SubLevelLocator load(CompoundTag tag, HolderLookup.Provider provider){
        SubLevelLocator res = new SubLevelLocator();
        var rows = tag.getList("Locations", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size() && idx < 16384; idx++){
            var row = rows.getCompound(idx);
            if(!row.hasUUID("Id")) continue;
            var val = new Location(row.getUUID("Id"), row.getString("Name"), new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z")),
                    BlockPos.of(row.getLong("Plot")), row.getDouble("Top"), row.getLong("Seen"));
            res.locations.put(val.id(), val);
        }
        return res;
    }

    public record Location(UUID id, String name, Vec3 position, BlockPos plotCenter, double top, long seen){
        public Location{
            Objects.requireNonNull(id, "id");
            name = name == null ? "" : name;
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(plotCenter, "plotCenter");
        }
    }
}
