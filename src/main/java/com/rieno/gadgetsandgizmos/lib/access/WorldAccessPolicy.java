package com.rieno.gadgetsandgizmos.lib.access;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

// Combine claim integrations before reading or changing another player's world content
public final class WorldAccessPolicy{
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private WorldAccessPolicy(){}

    // Register additional protection rules; every provider must allow access
    public static void register(Provider provider){
        if(provider == null) throw new IllegalArgumentException("Access provider is missing");
        PROVIDERS.add(provider);
    }

    // Check current claims on the owning server thread
    public static boolean canAccess(ServerPlayer player, ServerLevel level, UUID id, BlockPos pos){
        if(player == null || level == null) return false;
        try{
            if(ModList.get().isLoaded("aeroclaims") && !AeroAccess.canAccess(player, level, id, pos)) return false;
            if(ModList.get().isLoaded("openpartiesandclaims") && !OpenPartyAccess.canAccess(player, level, pos)) return false;
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

    // Never allow destructive cleanup of registered or claimed sublevels
    public static boolean unclaimed(ServerLevel level, UUID id, BlockPos pos){
        if(level == null || id == null) return false;
        try{
            if(ModList.get().isLoaded("aeroclaims") && !AeroAccess.unclaimed(level, id, pos)) return false;
            if(ModList.get().isLoaded("openpartiesandclaims") && !OpenPartyAccess.unclaimed(level, pos)) return false;
            for(Provider provider : PROVIDERS) if(!provider.unclaimed(level, id, pos)) return false;
            return true;
        }catch(RuntimeException | LinkageError err){ return false; }
    }

    public interface Provider{
        boolean canAccess(ServerPlayer player, ServerLevel level, UUID subLevelId, BlockPos pos);
        boolean unclaimed(ServerLevel level, UUID subLevelId, BlockPos pos);
    }

    // Load optional claim implementation classes only when their mod is present
    private static final class AeroAccess{
        private static boolean canAccess(ServerPlayer player, ServerLevel level, UUID id, BlockPos pos){
            Claim claim = id == null ? null : ClaimManager.getClaimByShipId(level, id.toString());
            Claim location = pos == null ? null : ClaimManager.getClaimAt(level, pos);
            if(claim != null && !ClaimManager.getPermissionResolver().canAccess(player, claim)) return false;
            if(location != null && !ClaimManager.getPermissionResolver().canAccess(player, location)) return false;
            var registration = id == null ? null : RegisteredSublevelManager.getRegistration(id.toString());
            return registration == null || player.hasPermissions(2)
                    || player.getUUID().toString().equals(registration.ownerUuid)
                    || claim != null && ClaimManager.getPermissionResolver().canAccess(player, claim);
        }

        private static boolean unclaimed(ServerLevel level, UUID id, BlockPos pos){
            return ClaimManager.getClaimByShipId(level, id.toString()) == null
                    && (pos == null || ClaimManager.getClaimAt(level, pos) == null)
                    && RegisteredSublevelManager.getRegistration(id.toString()) == null;
        }
    }

    // Use the optional Open Parties and Claims server API for non-event actions
    private static final class OpenPartyAccess{
        private static Object api(ServerLevel level) throws ReflectiveOperationException{
            Class<?> type = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            return type.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, level.getServer());
        }

        private static boolean canAccess(ServerPlayer player, ServerLevel level, BlockPos pos){
            if(pos == null) return false;
            try{
                Object serverApi = api(level);
                Object claims = serverApi.getClass().getMethod("getServerClaimsManager").invoke(serverApi);
                Object claim = claims.getClass().getMethod("get", net.minecraft.resources.ResourceLocation.class,
                        BlockPos.class).invoke(claims, level.dimension().location(), pos);
                if(claim == null) return true;
                Object protection = serverApi.getClass().getMethod("getChunkProtection").invoke(serverApi);
                Method check = protection.getClass().getMethod("hasChunkAccess",
                        net.minecraft.world.entity.Entity.class, net.minecraft.resources.ResourceLocation.class,
                        int.class, int.class);
                return Boolean.TRUE.equals(check.invoke(protection, player,
                        level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4));
            }catch(ReflectiveOperationException | LinkageError err){ return false; }
        }

        private static boolean unclaimed(ServerLevel level, BlockPos pos){
            if(pos == null) return false;
            try{
                Object serverApi = api(level);
                Object claims = serverApi.getClass().getMethod("getServerClaimsManager").invoke(serverApi);
                return claims.getClass().getMethod("get", net.minecraft.resources.ResourceLocation.class,
                        BlockPos.class).invoke(claims, level.dimension().location(), pos) == null;
            }catch(ReflectiveOperationException | LinkageError err){ return false; }
        }
    }
}
