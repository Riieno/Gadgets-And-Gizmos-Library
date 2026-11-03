package com.rieno.gadgetsandgizmos.lib.client.sound;

import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Select one observation per tablet before touching any playing sound
final class MediaSourceBatch{
    record Source(MediaPlaybackState state, Vec3 pos, boolean relative){}
    private final Map<UUID, Source> sources = new HashMap<>();

    void offer(UUID id, MediaPlaybackState state, Vec3 pos, boolean relative, Vec3 listener){
        Source next = new Source(state, pos, relative);
        sources.merge(id, next, (prev, val) -> {
            var latest = prev.state().revision() >= val.state().revision() ? prev.state() : val.state();
            Source location = prev.relative() != val.relative() ? (prev.relative() ? prev : val)
                    : prev.pos().distanceToSqr(listener) <= val.pos().distanceToSqr(listener) ? prev : val;
            return new Source(latest, location.pos(), location.relative());
        });
    }

    Map<UUID, Source> drain(){
        var res = new HashMap<>(sources);
        sources.clear();
        return res;
    }

    void clear(){ sources.clear(); }
}
