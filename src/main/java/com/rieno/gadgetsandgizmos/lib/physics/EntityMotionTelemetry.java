package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

// Measure completed world movement without exposing gravity and collision attempts as velocity
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class EntityMotionTelemetry{
    private static final Map<Entity, Motion> SAMPLES = new WeakHashMap<>();

    private EntityMotionTelemetry(){}

    // Read blocks per second from the latest completed tick on the owning server thread
    public static Vec3 velocity(Entity entity){
        if(entity == null || entity.level().isClientSide || entity.isRemoved()) return Vec3.ZERO;
        Level level = entity.level();
        long tick = level.getGameTime();
        Motion motion = SAMPLES.get(entity);
        if(motion == null || motion.level.get() != level){
            motion = new Motion(level, tick, worldPosition(entity));
            SAMPLES.put(entity, motion);
        }
        return tick == motion.tick || tick == motion.tick + 1 ? motion.velocity : Vec3.ZERO;
    }

    // Start a new baseline after a discontinuous move without reporting a teleport as speed
    public static void reset(Entity entity){
        if(entity != null && !entity.level().isClientSide) SAMPLES.remove(entity);
    }

    // Sample tracked entities after their movement, passengers and block entities finish ticking
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var entries = SAMPLES.entrySet().iterator();
        while(entries.hasNext()){
            var entry = entries.next();Entity entity = entry.getKey();
            if(entity == null || entity.isRemoved()){
                entries.remove();continue;
            }
            if(entity.level() == level){
                entry.getValue().advance(level, level.getGameTime(), worldPosition(entity));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTeleport(EntityTeleportEvent event){ reset(event.getEntity()); }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event){ reset(event.getEntity()); }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event){ reset(event.getEntity()); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event){ SAMPLES.clear(); }

    private static Vec3 worldPosition(Entity entity){
        var feet = dev.ryanhcode.sable.Sable.HELPER.getFeetPos(entity, 0.0F);
        return SableTransformApi.toWorldPosition(SableLevelApi.containing(entity), new Vec3(feet.x(), feet.y(), feet.z()));
    }

    private static boolean finite(Vec3 vector){
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    // Keep position history independent of an entity's intended movement and contact flags
    static final class Motion{
        private WeakReference<Level> level;
        private long tick;
        private Vec3 position;
        private Vec3 velocity = Vec3.ZERO;

        Motion(Level level, long tick, Vec3 position){
            this.level = new WeakReference<>(level);this.tick = tick;
            this.position = finite(position) ? position : null;
        }

        void advance(Level level, long tick, Vec3 position){
            if(this.level.get() == level && tick == this.tick) return;
            Vec3 next = Vec3.ZERO;
            if(this.level.get() == level && tick == this.tick + 1 && this.position != null && finite(position)){
                next = position.subtract(this.position).scale(20.0D);
            }
            this.level = new WeakReference<>(level);
            this.tick = tick;this.position = finite(position) ? position : null;
            velocity = finite(next) ? next : Vec3.ZERO;
        }

        Vec3 velocity(){ return velocity; }
    }
}
