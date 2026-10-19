package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.view.ViewCaptureBudget;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

// Read GPU capture cost only after the driver reports a completed query
final class ViewCaptureTimer{
    private int query;
    private boolean measuring;
    private boolean pending;
    private long completedAt;
    private long cpuCost;

    // Collect completed timing without forcing the CPU to wait for the GPU
    void poll(ViewCaptureBudget budget){
        if(!pending || GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
        long duration = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
        budget.completed(completedAt, Math.max(cpuCost, duration));
        pending = false;
    }
    // Avoid nesting elapsed-time queries used by other render profilers
    void begin(){
        if(pending || !GL.getCapabilities().OpenGL33
                || GL15.glGetQueryi(GL33.GL_TIME_ELAPSED, GL15.GL_CURRENT_QUERY) != 0) return;
        if(query == 0) query = GL15.glGenQueries();
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        measuring = true;
    }
    // Leave the result on the GPU until a later frame can read it immediately
    void end(long now, long duration){
        if(!measuring) return;
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        completedAt = now;
        cpuCost = duration;
        measuring = false;
        pending = true;
    }
    // Release the query with the rest of the world's capture resources
    void close(){
        if(measuring) GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        if(query != 0) GL15.glDeleteQueries(query);
        query = 0;
        pending = measuring = false;
    }
}
