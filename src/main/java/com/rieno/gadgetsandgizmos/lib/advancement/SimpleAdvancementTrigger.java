package com.rieno.gadgetsandgizmos.lib.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// Let a consuming mod grant its own criterion after validating server gameplay
public final class SimpleAdvancementTrigger extends SimpleCriterionTrigger<SimpleAdvancementTrigger.Instance>{
    @Override
    public Codec<Instance> codec(){ return Instance.CODEC; }

    public void trigger(ServerPlayer player){ trigger(player, instance -> true); }

    public record Instance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance{
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
        ).apply(instance, Instance::new));
    }
}
