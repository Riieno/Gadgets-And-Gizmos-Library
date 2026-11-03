package com.rieno.gadgetsandgizmos.lib.client.sound;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// Stream observed sources and discard sounds as soon as their hosts disappear
public final class MediaPlaybackEngine{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Map<UUID, Entry> sources = new HashMap<>();
    private final MediaSourceBatch observations = new MediaSourceBatch();
    private ClientLevel level;
    private long tick;

    private static final class Entry{
        private MediaPlaybackState state;
        private TrackSound sound;
        private boolean relative;
        private long seen;
        private long retry;
        private long started;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void observe(UUID id, MediaPlaybackState state, Vec3 pos, boolean relative){
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null || id == null || state == null || pos == null || !Double.isFinite(pos.lengthSqr())) return;
        if(level != mc.level){ clear(); level = mc.level; }
        observations.offer(id, state, pos, relative, mc.player.position());
    }

    private void apply(UUID id, MediaSourceBatch.Source source){
        var mc = Minecraft.getInstance();
        var state = source.state();
        var pos = source.pos();
        boolean relative = source.relative();
        Entry entry = sources.computeIfAbsent(id, key -> new Entry());
        if(entry.state != null && state.revision() < entry.state.revision()) state = entry.state;
        entry.seen = tick;
        boolean changed = !state.equals(entry.state) || relative != entry.relative;
        entry.state = state;
        entry.relative = relative;
        boolean audible = state.playing() && !state.finished(level.getGameTime())
                && (relative || mc.player.position().distanceToSqr(pos) < 64 * 64);
        if(changed || !audible) stop(entry);
        if(!audible){ entry.retry = tick; return; }
        if(entry.sound != null){
            entry.sound.position(pos);
            if(entry.sound.failed || tick - entry.started >= 20 && !mc.getSoundManager().isActive(entry.sound)){
                boolean failed = entry.sound.failed;
                stop(entry);
                entry.retry = tick + (failed ? 100 : 1);
            }
        }
        if(entry.sound != null || (!changed && tick < entry.retry)) return;
        var track = MusicDiscCatalog.resolve(level.registryAccess(), state.discId());
        entry.retry = tick + 20;
        if(track == null) return;
        entry.started = tick;
        entry.sound = new TrackSound(track, state.elapsed(level.getGameTime()), pos, relative);
        mc.getSoundManager().play(entry.sound);
    }

    public void tick(){
        var mc = Minecraft.getInstance();
        if(level != mc.level){ clear(); level = mc.level; }
        if(level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        observations.drain().forEach(this::apply);
        sources.entrySet().removeIf(val -> {
            if(tick - val.getValue().seen <= 3) return false;
            stop(val.getValue());
            return true;
        });
    }

    public void clear(){
        sources.values().forEach(this::stop);
        sources.clear();
        observations.clear();
        level = null;
    }

    private void stop(Entry entry){
        if(entry.sound == null) return;
        entry.sound.cancel();
        Minecraft.getInstance().getSoundManager().stop(entry.sound);
        entry.sound = null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        STREAMING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final class TrackSound extends AbstractTickableSoundInstance{
        private final long offset;
        private volatile boolean cancelled;
        private volatile boolean failed;
        private volatile PcmAudioStream stream;

        private TrackSound(MusicDiscCatalog.Track track, long offset, Vec3 pos, boolean relative){
            super(track.sound(), SoundSource.RECORDS, RandomSource.create());
            this.offset = offset;
            this.relative = relative;
            volume = relative ? 1 : 4;
            attenuation = relative ? Attenuation.NONE : Attenuation.LINEAR;
            position(pos);
        }

        private void position(Vec3 pos){ x = pos.x; y = pos.y; z = pos.z; }
        private void cancel(){
            cancelled = true;
            stop();
            PcmAudioStream val = stream;
            if(val != null) CompletableFuture.runAsync(() -> {
                try{ val.close(); }catch(IOException ignored){}
            }, Util.nonCriticalIoPool());
        }
        @Override public void tick(){}
        @Override public boolean canStartSilent(){ return true; }

        @Override public WeighedSoundEvents resolve(SoundManager manager){
            var res = super.resolve(manager);
            if(sound != SoundManager.EMPTY_SOUND){
                sound = new Sound(sound.getLocation(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                        sound.getType(), true, false, sound.getAttenuationDistance());
            }
            return res;
        }

        // Decode seeks away from the render and sound threads
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping){
            return buffers.getStream(sound.getPath(), false).thenApplyAsync(decoded -> {
                try{
                    var res = new PcmAudioStream(decoded, !relative);
                    this.stream = res;
                    res.seekTicks(offset, () -> cancelled);
                    if(cancelled){ res.close(); throw new IOException("Media source stopped"); }
                    return (AudioStream)res;
                }catch(IOException | RuntimeException err){
                    try{
                        if(this.stream != null) this.stream.close();
                        else decoded.close();
                    }catch(IOException ignored){}
                    throw new CompletionException(err);
                }
            }, Util.nonCriticalIoPool()).whenComplete((res, err) -> {
                if(err == null || cancelled) return;
                failed = true;
                LogUtils.getLogger().warn("Unable to stream music {}", getLocation(), err);
            });
        }
    }
}
