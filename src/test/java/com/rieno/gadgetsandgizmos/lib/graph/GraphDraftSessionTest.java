package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphDraftSessionTest{
    @Test
    void editsExecuteWithoutSavingAndRejectStaleChanges(){
        class Host implements GraphDraftSession.Host<Integer>{
            int count;
            int closed;
            @Override public void reconcile(Integer prev, Integer next){}
            @Override public void tick(Integer graph){ count += graph; }
            @Override public void close(){ closed++; }
        }
        Host host = new Host();
        var session = new GraphDraftSession<Integer>(host);
        assertTrue(session.replace(-1, 0, 1));
        session.tick(10);
        session.tick(10);
        assertEquals(1, host.count);
        assertTrue(session.replace(0, 1, 2));
        assertFalse(session.replace(0, 2, 99));
        session.tick(11);
        assertEquals(3, host.count);
        session.close();
        session.close();
        session.tick(12);
        assertFalse(session.replace(1, 2, 99));
        assertEquals(3, host.count);
        assertEquals(1, host.closed);
    }
}
