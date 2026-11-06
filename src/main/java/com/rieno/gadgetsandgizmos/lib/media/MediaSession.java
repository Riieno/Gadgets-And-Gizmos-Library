package com.rieno.gadgetsandgizmos.lib.media;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;
import com.rieno.gadgetsandgizmos.lib.text.UserMessageException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

// Keep ordered playlists and a server-authoritative playback timeline
public final class MediaSession{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_PLAYLISTS = 32;
    public static final int MAX_TRACKS = 512;
    public static final int MAX_NAME = 48;
    private final List<Playlist> playlists = new ArrayList<>();
    private UUID selected;
    private MediaPlaybackState playback = MediaPlaybackState.EMPTY;

    public record Playlist(UUID id, String name, List<ResourceLocation> discs){
        public Playlist{ discs = List.copyOf(discs); }
    }

    public List<Playlist> playlists(){ return List.copyOf(playlists); }
    public UUID selected(){ return selected; }
    public MediaPlaybackState playback(){ return playback; }
    public Playlist playlist(UUID id){ return playlists.stream().filter(val -> val.id().equals(id)).findFirst().orElse(null); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public UUID create(String name){
        if(playlists.size() >= MAX_PLAYLISTS) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.playlist_limit_reached"));
        String val = checkedName(name, null);
        UUID id = UUID.randomUUID();
        playlists.add(new Playlist(id, val, List.of()));
        return id;
    }

    public void rename(UUID id, String name){
        Playlist prev = required(id);
        playlists.set(playlists.indexOf(prev), new Playlist(id, checkedName(name, id), prev.discs()));
    }

    public void delete(UUID id, long now){
        playlists.remove(required(id));
        if(id.equals(selected)){
            selected = null;
            playback = playback.paused(now);
        }
    }

    public void toggle(UUID id, ResourceLocation disc, long now, ToIntFunction<ResourceLocation> lengths){
        Playlist prev = required(id);
        var discs = new ArrayList<>(prev.discs());
        int idx = discs.indexOf(disc);
        if(idx >= 0) discs.remove(idx);
        else{
            if(lengths.applyAsInt(disc) <= 0) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.music_disc_unavailable"));
            if(discs.size() >= MAX_TRACKS) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.playlist_track_limit_reached"));
            discs.add(disc);
        }
        playlists.set(playlists.indexOf(prev), new Playlist(id, prev.name(), discs));
        if(id.equals(selected) && disc.equals(playback.discId()) && idx >= 0){
            var queue = queue(List.of(), lengths);
            if(queue.isEmpty()) clearPlayback(now);
            else start(queue.get(idx % queue.size()), now, 0, playback.playing(), lengths);
        }
    }

    public void select(UUID id, long now, ToIntFunction<ResourceLocation> lengths){
        if(id != null) required(id);
        selected = id;
        if(id == null) return;
        var queue = queue(List.of(), lengths);
        if(queue.isEmpty()) clearPlayback(now);
        else start(queue.getFirst(), now, 0, true, lengths);
    }

    public void play(ResourceLocation disc, long now, ToIntFunction<ResourceLocation> lengths){
        if(lengths.applyAsInt(disc) <= 0) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.music_disc_unavailable"));
        Playlist list = playlist(selected);
        if(list == null || !list.discs().contains(disc)) selected = null;
        start(disc, now, 0, true, lengths);
    }

    public void togglePlayback(long now, List<ResourceLocation> catalog, ToIntFunction<ResourceLocation> lengths){
        if(playback.playing()) playback = playback.paused(now);
        else if(playback.discId() != null && lengths.applyAsInt(playback.discId()) > 0
                && (selected == null || queue(catalog, lengths).contains(playback.discId()))){
            start(playback.discId(), now, playback.offsetTicks() >= playback.durationTicks() ? 0 : playback.offsetTicks(), true, lengths);
        }else{
            var queue = queue(catalog, lengths);
            if(!queue.isEmpty()) start(queue.getFirst(), now, 0, true, lengths);
        }
    }

    public void step(int delta, long now, List<ResourceLocation> catalog, ToIntFunction<ResourceLocation> lengths){
        var queue = queue(catalog, lengths);
        if(queue.isEmpty()) return;
        int idx = queue.indexOf(playback.discId());
        if(delta < 0 && idx >= 0 && playback.elapsed(now) > 60) start(queue.get(idx), now, 0, true, lengths);
        else start(queue.get(idx < 0 ? 0 : Math.floorMod(idx + delta, queue.size())), now, 0, true, lengths);
    }

    // Skip whole playlist cycles when a source returns after a long unload
    public boolean advance(long now, ToIntFunction<ResourceLocation> lengths){
        if(!playback.finished(now)) return false;
        var queue = queue(List.of(), lengths);
        if(selected == null || queue.isEmpty()){
            playback = playback.paused(now);
            return true;
        }
        long remaining = playback.position(now) - playback.durationTicks();
        long cycle = queue.stream().mapToLong(lengths::applyAsInt).sum();
        remaining %= Math.max(1, cycle);
        int idx = queue.indexOf(playback.discId());
        for(int count = 0; count < queue.size(); count++){
            idx = Math.floorMod(idx + 1, queue.size());
            ResourceLocation disc = queue.get(idx);
            int duration = lengths.applyAsInt(disc);
            if(remaining < duration){
                start(disc, now, remaining, true, lengths);
                return true;
            }
            remaining -= duration;
        }
        return false;
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        tag.put("Playback", playback.toTag());
        if(selected != null) tag.putUUID("Selected", selected);
        var rows = new ListTag();
        for(Playlist playlist : playlists){
            var row = new CompoundTag();
            row.putUUID("Id", playlist.id());
            row.putString("Name", playlist.name());
            var discs = new ListTag();
            playlist.discs().forEach(id -> discs.add(StringTag.valueOf(id.toString())));
            row.put("Discs", discs);
            rows.add(row);
        }
        tag.put("Playlists", rows);
        return tag;
    }

    public static MediaSession fromTag(CompoundTag tag){
        var res = new MediaSession();
        res.playback = MediaPlaybackState.fromTag(tag.getCompound("Playback"));
        var rows = tag.getList("Playlists", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < Math.min(rows.size(), MAX_PLAYLISTS); idx++){
            var row = rows.getCompound(idx);
            if(!row.hasUUID("Id") || res.playlist(row.getUUID("Id")) != null) continue;
            var discs = new ArrayList<ResourceLocation>();
            var entries = row.getList("Discs", Tag.TAG_STRING);
            for(int entry = 0; entry < Math.min(entries.size(), MAX_TRACKS); entry++){
                var id = ResourceLocation.tryParse(entries.getString(entry));
                if(id != null && !discs.contains(id)) discs.add(id);
            }
            String name = row.getString("Name");
            res.playlists.add(new Playlist(row.getUUID("Id"), name.substring(0, Math.min(MAX_NAME, name.length())), discs));
        }
        if(tag.hasUUID("Selected") && res.playlist(tag.getUUID("Selected")) != null) res.selected = tag.getUUID("Selected");
        return res;
    }

    private Playlist required(UUID id){
        Playlist res = playlist(id);
        if(res == null) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.playlist_unavailable"));
        return res;
    }

    private String checkedName(String name, UUID except){
        String val = name == null ? "" : name.strip().replaceAll("[\\p{Cntrl}]", "");
        if(val.isEmpty() || val.length() > MAX_NAME) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.enter_a_playlist_name_1_48_characters"));
        for(Playlist list : playlists){
            if(!list.id().equals(except) && list.name().equalsIgnoreCase(val)) throw new UserMessageException(Component.translatable("gadgetsngizmos.text.playlist_name_already_used"));
        }
        return val;
    }

    private List<ResourceLocation> queue(List<ResourceLocation> fallback, ToIntFunction<ResourceLocation> lengths){
        Playlist list = playlist(selected);
        return (list == null ? fallback : list.discs()).stream().filter(id -> lengths.applyAsInt(id) > 0).toList();
    }

    private void start(ResourceLocation disc, long now, long offset, boolean playing, ToIntFunction<ResourceLocation> lengths){
        playback = new MediaPlaybackState(disc, playing, offset, now, lengths.applyAsInt(disc), playback.revision() + 1);
    }

    private void clearPlayback(long now){
        playback = new MediaPlaybackState(null, false, 0, now, 0, playback.revision() + 1);
    }
}
