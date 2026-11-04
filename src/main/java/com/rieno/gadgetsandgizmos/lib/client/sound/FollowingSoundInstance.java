package com.rieno.gadgetsandgizmos.lib.client.sound;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

// Follow a live world position for both one-shot and looping sounds
public final class FollowingSoundInstance extends AbstractTickableSoundInstance{
    private final Supplier<Vec3> position;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // A missing position ends playback when its source is no longer available
    public FollowingSoundInstance(SoundEvent sound, SoundSource source, float volume, float pitch,
            boolean loop, Vec3 initialPosition, Supplier<Vec3> position){
        super(sound, source, RandomSource.create());
        this.position = Objects.requireNonNull(position);
        this.volume = volume;
        this.pitch = pitch;
        looping = loop;
        delay = 0;
        updatePosition(Objects.requireNonNull(initialPosition));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve translation and rotation from the same local point throughout playback
    public static FollowingSoundInstance onSubLevel(SoundEvent sound, SoundSource source, float volume,
            float pitch, boolean loop, ClientLevel level, UUID subLevelId, Vec3 localPosition, Vec3 initialPosition){
        Objects.requireNonNull(level);
        Objects.requireNonNull(subLevelId);
        Objects.requireNonNull(localPosition);
        return new FollowingSoundInstance(sound, source, volume, pitch, loop, initialPosition, new Supplier<>(){
            private boolean resolved;
            private int pendingTicks;

            @Override public Vec3 get(){
                if(Minecraft.getInstance().level != level) return null;
                SubLevel subLevel = SableLevelApi.subLevel(level, subLevelId);
                if(subLevel == null || subLevel.isRemoved()){
                    // Sound packets can arrive before the first client SubLevel snapshot
                    return !resolved && ++pendingTicks <= 20 ? initialPosition : null;
                }
                resolved = true;
                return SableTransformApi.projectOut(level, SableTransformApi.toWorldPosition(subLevel, localPosition));
            }
        });
    }

    // Update only the source position so playback and loop timing remain continuous
    @Override public void tick(){
        Vec3 next = position.get();
        if(next == null || !Double.isFinite(next.x) || !Double.isFinite(next.y) || !Double.isFinite(next.z)){
            stop();
            return;
        }
        updatePosition(next);
    }

    // Apply one world position to the audio source
    private void updatePosition(Vec3 pos){
        x = pos.x;
        y = pos.y;
        z = pos.z;
    }
}
