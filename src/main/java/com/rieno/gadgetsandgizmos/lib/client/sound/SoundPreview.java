package com.rieno.gadgetsandgizmos.lib.client.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Objects;

// Keep one local preview playing without interrupting other game sounds
public final class SoundPreview implements AutoCloseable{
    private final SoundManager manager;
    private SoundInstance current;
    private ResourceLocation id;

    public SoundPreview(SoundManager manager){ this.manager = Objects.requireNonNull(manager); }

    // Clicking the current sound stops it, while another sound replaces it
    public void toggle(ResourceLocation next){
        Objects.requireNonNull(next);
        boolean same = next.equals(id) && isPlaying(next);
        stop();
        if(same) return;
        id = next;
        current = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(next), 1, 1);
        manager.play(current);
    }

    public boolean isPlaying(ResourceLocation sound){
        return current != null && Objects.equals(id, sound) && manager.isActive(current);
    }

    public void stop(){
        if(current != null) manager.stop(current);
        current = null;
        id = null;
    }

    @Override public void close(){ stop(); }
}
