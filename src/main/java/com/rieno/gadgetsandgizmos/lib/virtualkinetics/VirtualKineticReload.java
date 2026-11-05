package com.rieno.gadgetsandgizmos.lib.virtualkinetics;

import com.rieno.gadgetsandgizmos.lib.kinetics.KineticGraphHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Reconcile saved consumers with their live virtual source before rotation propagation
public final class VirtualKineticReload{
    private VirtualKineticReload(){}

    // Return false while a saved source chain is unavailable and needs another attempt
    public static boolean restore(KineticBlockEntity consumer){
        if(consumer.getLevel() == null || consumer.getLevel().isClientSide
                || consumer instanceof VirtualKineticBlockEntity) return true;
        List<KineticBlockEntity> chain = new ArrayList<>();
        Set<KineticBlockEntity> visited = new HashSet<>();
        KineticBlockEntity source = consumer;
        while(!(source instanceof VirtualKineticBlockEntity)){
            if(source.isSource() || !source.hasSource()) return true;
            if(!visited.add(source)) return false;
            chain.add(source);
            source = resolveSource(source);
            if(source == null) return false;
        }
        // Restore upstream members first so stale consumers cannot drive their own source
        for(int idx = chain.size() - 1; idx >= 0; idx--){
            KineticBlockEntity target = chain.get(idx);
            Float speed = KineticGraphHelper.getConveyedSpeed(source, target);
            if(speed == null || !Float.isFinite(speed)) return false;
            if(target.getTheoreticalSpeed() != speed || !Objects.equals(target.network, source.network)){
                target.detachKinetics();
                target.removeSource();
                target.updateSpeed = true;
                target.attachKinetics();
            }
            source = target;
        }
        return true;
    }

    // Resolve the persisted slot without loading an unavailable source chunk
    @Nullable
    private static KineticBlockEntity resolveSource(KineticBlockEntity consumer){
        var level = consumer.getLevel();
        if(level == null || !consumer.hasSource() || !level.isLoaded(consumer.source)) return null;
        BlockEntity source = level.getBlockEntity(consumer.source);
        int slot = consumer instanceof VirtualKineticSourceLink link ? link.ct$getConnectedVirtualKineticSlot() : -1;
        if(consumer.source instanceof VirtualKineticPos pos) slot = pos.ct$getVirtualSlot();
        if(slot >= 0){
            return source instanceof VirtualKineticProvider provider ? provider.ct$getVirtualKinetic(slot) : null;
        }
        return source instanceof KineticBlockEntity kinetic ? kinetic : null;
    }
}
