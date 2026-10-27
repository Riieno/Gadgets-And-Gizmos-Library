package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.geometry.ClipBounds;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.opengl.GL15;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

// Test the surface's actual fragments without changing its geometry, shader or depth state
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class RenderSurfaceVisibility implements AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static long frame;
    private final Map<RenderType, RenderType> types = new IdentityHashMap<>();
    private final ArrayDeque<Query> pending = new ArrayDeque<>();
    private final ArrayDeque<Integer> available = new ArrayDeque<>();
    private long drawn = Long.MIN_VALUE;
    private long completed = Long.MIN_VALUE;
    private boolean visible = true;
    private long resolving = Long.MIN_VALUE;
    private boolean passed;
    private boolean closed;
    private final org.joml.Matrix4f clip = new org.joml.Matrix4f();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Advance before camera captures and projected GUI updates consume last frame's results
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginFrame(RenderFrameEvent.Pre evt){ frame++; }

    // Wrap a retained render type and measure the same draw that presents its texture
    public RenderType track(RenderType type){
        if(closed) throw new IllegalStateException("Surface visibility is closed");
        return types.computeIfAbsent(type, TrackedType::new);
    }

    // Present retained geometry without treating its old contents as fresh update demand
    public static RenderType untracked(RenderType type){
        return type instanceof RenderSurfaceVisibility.TrackedType tracked ? tracked.original : type;
    }

    // Treat any visible batch as visible and stop refreshing surfaces absent from the last frame
    public boolean isVisible(){
        if(closed) return false;
        poll();
        return drawn != Long.MIN_VALUE && frame - drawn <= 1 && visible;
    }

    // Keep testing a conservative local volume even while its expensive contents are skipped
    public boolean probe(PoseStack poses, MultiBufferSource buffers, AABB bounds){
        if(closed) throw new IllegalStateException("Surface visibility is closed");
        if(!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)) return true;
        clip.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        if(!ClipBounds.intersects(clip, bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) return false;
        poll();
        boolean res = drawn == Long.MIN_VALUE || frame - drawn > 1 || visible;
        var vertices = buffers.getBuffer(track(ProbeState.TYPE));
        var pose = poses.last().pose();
        float x0 = (float)bounds.minX, y0 = (float)bounds.minY, z0 = (float)bounds.minZ;
        float x1 = (float)bounds.maxX, y1 = (float)bounds.maxY, z1 = (float)bounds.maxZ;
        quad(vertices, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(vertices, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vertices, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vertices, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(vertices, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vertices, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        return res;
    }

    // Release completed and pending queries when the owning surface is retired
    @Override
    public void close(){
        if(closed) return;
        closed = true;
        pending.forEach(query -> GL15.glDeleteQueries(query.id));
        available.forEach(GL15::glDeleteQueries);
        pending.clear();
        available.clear();
        types.clear();
    }

    // Read only completed queries so visibility checks never wait for the GPU
    private void poll(){
        while(!pending.isEmpty()){
            Query query = pending.peek();
            if(GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            boolean val = GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT) != 0;
            pending.remove();
            available.add(query.id);
            if(query.frame != resolving){
                resolving = query.frame;
                passed = val;
            }else{
                passed |= val;
            }
            if(pending.isEmpty() || pending.peek().frame > resolving){
                if(resolving > completed){
                    completed = resolving;
                    visible = passed;
                }else if(resolving == completed){
                    visible |= passed;
                }
            }
        }
    }

    private record Query(int id, long frame){}

    private static void quad(VertexConsumer vertices, org.joml.Matrix4f pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3){
        vertices.addVertex(pose, x0, y0, z0).setColor(-1);
        vertices.addVertex(pose, x1, y1, z1).setColor(-1);
        vertices.addVertex(pose, x2, y2, z2).setColor(-1);
        vertices.addVertex(pose, x3, y3, z3).setColor(-1);
    }

    // Queries inspect depth without contributing pixels or changing the world's depth buffer
    private static final class ProbeState extends RenderStateShard{
        private static final RenderType TYPE = RenderType.create("gadgetsngizmos_visibility_probe",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                        .setWriteMaskState(new WriteMaskStateShard(false, false)).createCompositeState(false));

        private ProbeState(){ super("gadgetsngizmos_visibility_probe", () -> {}, () -> {}); }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Delegate drawing so texture filtering, animations, polygon offset and optional shaders stay intact
    private final class TrackedType extends RenderType{
        private final RenderType original;

        private TrackedType(RenderType original){
            super("gadgetsngizmos_visible_surface", original.format(), original.mode(), original.bufferSize(),
                    original.affectsCrumbling(), original.sortOnUpload(),
                    original::setupRenderState, original::clearRenderState);
            this.original = original;
        }

        @Override
        public void draw(MeshData mesh){
            if(closed){ original.draw(mesh); return; }
            poll();
            drawn = frame;
            if(pending.size() >= 32 || GL15.glGetQueryi(GL15.GL_SAMPLES_PASSED, GL15.GL_CURRENT_QUERY) != 0){
                visible = true;
                original.draw(mesh);
                return;
            }
            int id = available.isEmpty() ? GL15.glGenQueries() : available.remove();
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, id);
            try{
                original.draw(mesh);
            }finally{
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                pending.add(new Query(id, frame));
            }
        }

        @Override public Optional<RenderType> outline(){ return original.outline(); }
        @Override public boolean isOutline(){ return original.isOutline(); }
    }
}
