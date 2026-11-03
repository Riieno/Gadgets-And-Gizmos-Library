package com.rieno.gadgetsandgizmos.lib.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Read the spatial entity index without expanding a query into other coordinate spaces
@Mixin(Level.class)
public interface LevelEntityQueryAccess{
    // Resolve the owning level's entity index
    @Invoker("getEntities")
    LevelEntityGetter<Entity> gadgetsngizmos$entityGetter();
}
