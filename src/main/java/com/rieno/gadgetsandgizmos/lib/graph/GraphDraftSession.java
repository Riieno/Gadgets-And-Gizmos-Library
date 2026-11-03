package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Objects;

// Execute an unsaved graph continuously while its host owns mutation and effect policies
public final class GraphDraftSession<G>{
    public interface Host<G>{
        void reconcile(G prev, G next);
        void tick(G graph);
        void close();
    }

    private final Host<G> host;
    private G graph;
    private long revision = -1;
    private long lastTick = Long.MIN_VALUE;
    private boolean closed;

    public GraphDraftSession(Host<G> host){ this.host = Objects.requireNonNull(host, "host"); }

    // Reject stale mutation batches without changing the running draft
    public boolean replace(long expectedRevision, long nextRevision, G next){
        if(closed || expectedRevision != revision || nextRevision <= revision) return false;
        Objects.requireNonNull(next, "graph");
        host.reconcile(graph, next);
        graph = next;
        revision = nextRevision;
        return true;
    }

    // The owning server tick drives timers independently of editor render frequency
    public void tick(long tick){
        if(closed || graph == null || lastTick == tick) return;
        lastTick = tick;
        host.tick(graph);
    }

    public long revision(){ return revision; }
    public G graph(){ return graph; }

    public void close(){
        if(closed) return;
        closed = true;
        graph = null;
        host.close();
    }
}
