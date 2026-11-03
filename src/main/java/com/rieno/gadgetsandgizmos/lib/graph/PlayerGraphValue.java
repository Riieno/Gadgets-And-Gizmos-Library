package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Carry stable player identity and copied interaction-time information without retaining an entity
public final class PlayerGraphValue{
    public static final String TYPE = "player";

    // Keep snapshots on the static API
    private PlayerGraphValue(){}

    // Identify maps whose fields can be inspected by ordinary graph collection nodes
    public static boolean isMapType(String type){ return "map".equals(type) || TYPE.equals(type); }

    // Preserve identity when a player is unavailable without inventing a current snapshot
    public static GraphValue reference(@Nullable UUID id){
        return new GraphValue(TYPE, id == null ? Map.of() : Map.of("uuid", GraphValue.string(id.toString()),
                "available", GraphValue.bool(false)));
    }

    // Capture one server player's information on the owning game thread
    public static GraphValue capture(@Nullable ServerPlayer player){ return capture(player, false); }

    // Include interaction mode while retaining the common telemetry field names
    public static GraphValue capture(@Nullable ServerPlayer player, boolean remote){
        if(player == null) return reference(null);
        Map<String, GraphValue> data = new LinkedHashMap<>(EntityTelemetryApi.sample(player, player.position(),
                player.getLookAngle(), new Vec3(0, 1, 0), player.level().dimension().location().toString(), 0));
        data.put("available", GraphValue.bool(true));
        data.put("uuid", GraphValue.string(player.getUUID().toString()));
        data.put("name", GraphValue.string(player.getGameProfile().getName()));
        data.put("display_name", GraphValue.string(player.getDisplayName().getString()));
        data.put("dimension", GraphValue.string(player.level().dimension().location().toString()));
        data.put("x", GraphValue.number(player.getX())); data.put("y", GraphValue.number(player.getY()));
        data.put("z", GraphValue.number(player.getZ()));
        Vec3 look = player.getLookAngle();
        data.put("look_direction", GraphValue.map(Map.of("x", GraphValue.number(look.x), "y", GraphValue.number(look.y),
                "z", GraphValue.number(look.z), "yaw", GraphValue.number(player.getYRot()), "pitch", GraphValue.number(player.getXRot()))));
        data.put("remote", GraphValue.bool(remote));
        data.put("game_mode", GraphValue.string(player.gameMode.getGameModeForPlayer().getName()));
        data.put("experience_level", GraphValue.number(player.experienceLevel));
        data.put("experience_progress", GraphValue.number(player.experienceProgress));
        data.put("total_experience", GraphValue.number(player.totalExperience));
        data.put("score", GraphValue.number(player.getScore()));
        data.put("team", GraphValue.string(player.getTeam() == null ? "" : player.getTeam().getName()));
        data.put("tick", GraphValue.number(player.level().getGameTime()));
        return new GraphValue(TYPE, data);
    }

    // Read identity independently of mutable names or stale snapshot fields
    public static @Nullable UUID id(GraphValue value){
        if(value == null || !TYPE.equals(value.type())) return null;
        try{ return UUID.fromString(value.member("uuid").asString()); }
        catch(IllegalArgumentException err){ return null; }
    }

    // Resolve only a currently online player; snapshot values never grant permissions
    public static @Nullable ServerPlayer resolve(@Nullable MinecraftServer server, GraphValue value){
        UUID id = id(value);
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
}
