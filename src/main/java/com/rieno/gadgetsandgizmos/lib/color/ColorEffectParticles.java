package com.rieno.gadgetsandgizmos.lib.color;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// Transport the original particle and its optional effect together to every observing client
public final class ColorEffectParticles{
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE,
            GadgetsNGizmosLibrary.MOD_ID);
    private static final Map<ResourceLocation, BooleanSupplier> ENABLED = new ConcurrentHashMap<>();
    public static final Supplier<ParticleType<Options>> TYPE = TYPES.register("color_effect", () -> new ParticleType<>(false){
        @Override public MapCodec<Options> codec(){ return Options.CODEC; }
        @Override public StreamCodec<? super RegistryFriendlyByteBuf, Options> streamCodec(){ return Options.STREAM_CODEC; }
    });

    private ColorEffectParticles(){}

    public static void register(IEventBus bus){ TYPES.register(bus); }

    // Owning addons supply local policy without exposing their config classes to the library
    public static void registerPolicy(ResourceLocation id, BooleanSupplier enabled){
        if(ENABLED.putIfAbsent(id, enabled) != null) throw new IllegalArgumentException("Duplicate color effect policy: " + id);
    }

    public static boolean enabled(ResourceLocation id){
        var policy = ENABLED.get(id);
        return policy != null && policy.getAsBoolean();
    }

    public static ParticleOptions wrap(ParticleOptions original, ResourceLocation group, ColorEffectPreset preset){
        if(original instanceof Options || (!preset.active() && !preset.additive())) return original;
        return new Options(original, group, preset);
    }

    public record Options(ParticleOptions original, ResourceLocation group, ColorEffectPreset preset) implements ParticleOptions{
        public static final MapCodec<Options> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ParticleTypes.CODEC.fieldOf("original").forGetter(Options::original),
                ResourceLocation.CODEC.fieldOf("group").forGetter(Options::group),
                ColorEffectPreset.CODEC.fieldOf("preset").forGetter(Options::preset)).apply(instance, Options::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Options> STREAM_CODEC = new StreamCodec<>(){
            @Override public Options decode(RegistryFriendlyByteBuf buf){
                var original = ParticleTypes.STREAM_CODEC.decode(buf);
                var group = buf.readResourceLocation();
                return new Options(original, group, ColorEffectPreset.read(ByteBufCodecs.COMPOUND_TAG.decode(buf)));
            }

            @Override public void encode(RegistryFriendlyByteBuf buf, Options val){
                ParticleTypes.STREAM_CODEC.encode(buf, val.original);
                buf.writeResourceLocation(val.group);
                ByteBufCodecs.COMPOUND_TAG.encode(buf, val.preset.write());
            }
        };

        public Options{
            if(original instanceof Options) throw new IllegalArgumentException("Nested color effect particles are not supported");
        }

        @Override public ParticleType<?> getType(){ return TYPE.get(); }
    }
}
