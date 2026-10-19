package com.rieno.gadgetsandgizmos.lib.compat.aeronautics;

import com.rieno.gadgetsandgizmos.lib.compat.OptionalTypeMatcher;
import com.rieno.gadgetsandgizmos.lib.control.LiftingGasControl;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuoyancyControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Bridge optional Aeronautics heaters without linking its implementation classes
public final class AeronauticsHotAirApi{
    private static final OptionalTypeMatcher BURNER = new OptionalTypeMatcher(
            "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity");
    private static final ClassValue<Map<String, Optional<Method>>> METHODS = new ClassValue<>(){
        @Override protected Map<String, Optional<Method>> computeValue(Class<?> type){ return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Optional<Field>> VOLUME = new ClassValue<>(){
        @Override protected Optional<Field> computeValue(Class<?> type){
            for(Class<?> current = type; current != null; current = current.getSuperclass()){
                try{
                    Field field = current.getDeclaredField("hotAirAmountBehaviour");
                    return field.trySetAccessible() ? Optional.of(field) : Optional.empty();
                }catch(NoSuchFieldException ignored){}
            }
            return Optional.empty();
        }
    };

    private AeronauticsHotAirApi(){}

    // Keep the consumer's existing adapter id so saved block and face bindings still resolve
    public static List<ScmControlProbe> createProbes(@Nullable BlockEntity blockEntity, String adapterId){
        if(!BURNER.test(blockEntity) || !(blockEntity instanceof LiftingGasControl control)) return List.of();
        return List.of(new ScmBuoyancyControl(adapterId, new BurnerSource(blockEntity, control)));
    }

    private static final class BurnerSource implements ScmBuoyancyControl.Source{
        private final BlockEntity burner;
        private final LiftingGasControl control;
        private long sampledAt = Long.MIN_VALUE;
        private ScmBuoyancyControl.Sample cached;

        private BurnerSource(BlockEntity burner, LiftingGasControl control){
            this.burner = burner;
            this.control = control;
        }

        @Override public boolean isAvailable(){ return !burner.isRemoved() && burner.getLevel() != null; }
        @Override public void applyVolume(double volume){
            if(isAvailable() && !burner.getLevel().isClientSide) control.setControlledGasVolume(volume);
        }
        @Override public void restore(){ control.clearControlledGasVolume(); }
        @Override public double responseTimeSeconds(){
            Object gas = invoke(burner, "getLiftingGasType");
            return Math.max(number(invoke(gas, "getFillingTime")), number(invoke(gas, "getEmptyingTime"))) * 0.05D;
        }

        @Override public ScmBuoyancyControl.Sample sample(){
            long tick = burner.getLevel() == null ? Long.MIN_VALUE : burner.getLevel().getGameTime();
            if(cached != null && sampledAt == tick) return cached;
            sampledAt = tick;
            Object balloon = invoke(burner, "getBalloon");
            double maximum = maximumVolume(burner);
            double available = maximum;
            double current = 0.0D;
            Vec3 position = burner.getBlockPos().getCenter();
            if(balloon != null){
                if(invoke(balloon, "getCenter") instanceof Vec3 center) position = center;
                double total = maximum;
                if(invoke(balloon, "getHeaters") instanceof Collection<?> heaters){
                    for(Object heater : heaters){
                        if(heater != burner) total += BURNER.test(heater)
                                ? maximumVolume(heater) : number(invoke(heater, "getGasOutput"));
                    }
                }
                double capacity = number(invoke(balloon, "getCapacity"));
                available = ScmBuoyancyControl.volumeShare(maximum, capacity, total);
                current = number(invoke(balloon, "getTotalFilledVolume")) * (total <= 0.0D ? 0.0D : maximum / total);
            }
            SubLevel body = SableLevelApi.containing(burner);
            Vec3 world = SableTransformApi.toWorldPosition(body, position);
            Vector3d gravity = burner.getLevel() == null ? new Vector3d() : DimensionPhysicsData.getGravity(
                    burner.getLevel(), new Vector3d(world.x, world.y, world.z));
            double pressure = burner.getLevel() == null ? 0.0D : DimensionPhysicsData.getAirPressure(
                    burner.getLevel(), new Vector3d(world.x, world.y, world.z));
            double strength = number(invoke(invoke(burner, "getLiftingGasType"), "getLiftStrength"));
            Vec3 direction = SableTransformApi.toLocalDirection(body, new Vec3(-gravity.x, -gravity.y, -gravity.z));
            cached = new ScmBuoyancyControl.Sample(available, current,
                    strength * gravity.length() * (pressure < 1.0E-5D ? 0.0D : pressure), direction, position);
            return cached;
        }
    }

    private static double maximumVolume(Object burner){
        try{
            Field field = VOLUME.get(burner.getClass()).orElse(null);
            return field != null && field.get(burner) instanceof ScrollValueBehaviour volume
                    ? Math.max(0.0D, volume.value) : 0.0D;
        }catch(IllegalAccessException ignored){ return 0.0D; }
    }

    private static @Nullable Object invoke(@Nullable Object target, String name){
        if(target == null) return null;
        Method method = METHODS.get(target.getClass()).computeIfAbsent(name, key -> {
            try{ return Optional.of(target.getClass().getMethod(key)); }
            catch(NoSuchMethodException ignored){ return Optional.empty(); }
        }).orElse(null);
        if(method == null) return null;
        try{ return method.invoke(target); }
        catch(ReflectiveOperationException ignored){ return null; }
    }

    private static double number(@Nullable Object val){
        return val instanceof Number num && Double.isFinite(num.doubleValue()) ? Math.max(0.0D, num.doubleValue()) : 0.0D;
    }
}
