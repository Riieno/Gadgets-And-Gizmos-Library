package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.mixin.LevelEntityQueryAccess;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

// Query one loaded coordinate space after the caller has selected its SubLevel
public final class SableEntityQueryApi{
    // Prevent construction of the entity query facade
    private SableEntityQueryApi(){}

    // Preserve multipart targets without recursively visiting every neighboring SubLevel
    public static List<Entity> localEntities(Level level, AABB bounds, Predicate<? super Entity> filter){
        if(level == null || bounds == null || filter == null) return List.of();
        List<Entity> res = new ArrayList<>();
        Consumer<Entity> collect = entity -> {
            if(filter.test(entity)) res.add(entity);
        };
        var getter = ((LevelEntityQueryAccess)level).gadgetsngizmos$entityGetter();
        if(getter instanceof SubLevelInclusiveLevelEntityGetter<Entity> inclusive){
            inclusive.getIgnoringSubLevels(bounds, collect);
        }else getter.get(bounds, collect);
        // Parts can intersect the ray even when their parent is outside the queried chunks
        for(var part : level.getPartEntities()){
            if(part.getBoundingBox().intersects(bounds) && filter.test(part)) res.add(part);
        }
        return res;
    }
}
