package com.rieno.gadgetsandgizmos.lib.access;

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

// Consult registered access policies before reading or changing world content
public final class WorldAccessPolicy{
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private WorldAccessPolicy(){}

    // Register additional protection rules; every provider must allow access
    public static void register(Provider provider){
        if(provider == null) throw new IllegalArgumentException("Access provider is missing");
        PROVIDERS.add(provider);
    }

    // Check registered protection rules on the owning server thread
    public static boolean canAccess(ServerPlayer player, ServerLevel level, UUID id, BlockPos pos){
        if(player == null || level == null) return false;
        if(ProtectionBypass.isEnabled(player)) return true;
        try{
            for(Provider provider : PROVIDERS) if(!provider.canAccess(player, level, id, pos)) return false;
            return true;
        }catch(RuntimeException | LinkageError err){ return false; }
    }

    // Convert plot-local coordinates before consulting world-position protection providers
    public static boolean canAccessLocal(ServerPlayer player, ServerLevel level, UUID id, BlockPos localPos){
        if(id == null) return canAccess(player, level, null, localPos);
        var body = SableLevelApi.subLevel(level, id);
        if(body == null) return false;
        return canAccess(player, level, id, BlockPos.containing(SableTransformApi.toWorldPosition(body, localPos.getCenter())));
    }

    // Keep owned ships and provider-protected sublevels out of destructive cleanup
    public static boolean unclaimed(ServerLevel level, UUID id, BlockPos pos){
        if(level == null || id == null) return false;
        try{
            if(ShipPermissionManager.get(level.getServer()).isClaimed(id)) return false;
            for(Provider provider : PROVIDERS) if(!provider.unclaimed(level, id, pos)) return false;
            return true;
        }catch(RuntimeException | LinkageError err){ return false; }
    }

    public interface Provider{
        boolean canAccess(ServerPlayer player, ServerLevel level, UUID subLevelId, BlockPos pos);
        boolean unclaimed(ServerLevel level, UUID subLevelId, BlockPos pos);
    }

}
