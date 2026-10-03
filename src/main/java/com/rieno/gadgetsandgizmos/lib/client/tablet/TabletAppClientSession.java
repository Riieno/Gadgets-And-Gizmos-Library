package com.rieno.gadgetsandgizmos.lib.client.tablet;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           IMPORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/


public final class TabletAppClientSession{
    private final Map<ResourceLocation, TabletAppClientState> states = new HashMap<>();
    private final Map<ResourceLocation, CompoundTag> observedDrafts = new HashMap<>();
    private static final Map<Object, Map<UUID, Map<ResourceLocation, CompoundTag>>> DRAFTS = new WeakHashMap<>();
    private Object scope;
    private UUID tablet;

    // Scope drafts to one connection and physical tablet without retaining either screen
    public void bindDrafts(Object scope, UUID tablet){
        if(this.scope == scope && Objects.equals(this.tablet, tablet)) return;
        clear();
        this.scope = scope;
        this.tablet = tablet;
    }

    public TabletAppClientState stateFor(ResourceLocation appId, TabletAppClientRenderer render){
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(render, "render");
        TabletAppClientState state = states.computeIfAbsent(appId,
                ignored -> Objects.requireNonNull(render.createScreenState(), "render state"));
        if(scope != null && tablet != null){
            CompoundTag draft = DRAFTS.getOrDefault(scope, Map.of()).getOrDefault(tablet, Map.of()).get(appId);
            if(draft != null && !draft.equals(observedDrafts.get(appId))){
                state.loadDraft(draft.copy());
                observedDrafts.put(appId, draft.copy());
            }
        }
        observedDrafts.computeIfAbsent(appId, ignored -> state.saveDraft().copy());
        return state;
    }

    // Store bounded copies of editable drafts when an interaction completes
    public void saveDrafts(){
        if(scope == null || tablet == null || states.isEmpty()) return;
        Map<UUID, Map<ResourceLocation, CompoundTag>> tablets = DRAFTS.computeIfAbsent(scope, key -> new LinkedHashMap<>());
        Map<ResourceLocation, CompoundTag> apps = tablets.computeIfAbsent(tablet, key -> new HashMap<>());
        states.forEach((id, state) -> {
            CompoundTag draft = state.saveDraft();
            if(!draft.isEmpty() && !draft.equals(observedDrafts.get(id))){
                apps.put(id, draft.copy());
                observedDrafts.put(id, draft.copy());
            }
        });
        while(tablets.size() > 256) tablets.remove(tablets.keySet().iterator().next());
    }

    public void clear(){
        saveDrafts();
        for(TabletAppClientState state : states.values()){
            if(state instanceof AutoCloseable closeable){
                try{ closeable.close(); }catch(Exception err){ throw new IllegalStateException("Cannot release tablet app state", err); }
            }
        }
        states.clear();
        observedDrafts.clear();
    }
}
