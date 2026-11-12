package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                    IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.rieno.gadgetsandgizmos.lib.client.render.SceneRenderTargets;
import com.rieno.gadgetsandgizmos.lib.view.ViewCaptureBudget;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.util.function.BooleanSupplier;
import java.util.ArrayDeque;

// Refresh distant terrain independently and reuse its small background while near terrain remains live
final class ViewLodLayer implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ViewCaptureBudget BUDGET = new ViewCaptureBudget(4, 0.05);
    private static final ViewCaptureTimer TIMER = new ViewCaptureTimer();
    private static final ArrayDeque<ViewLodLayer> PENDING = new ArrayDeque<>();
    private static int program, array, transform;
    private final Matrix4f view = new Matrix4f(), projection = new Matrix4f();
    private TextureTarget target;
    private long nextUpdate;
    private boolean ready;
    private boolean queued;
    private long requested;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Never apply the optional terrain budget to the camera's near scene or screen rendering
    void render(Matrix4fc currentView, Matrix4fc currentProjection, BooleanSupplier draw){
        RenderTarget output = ViewSceneRenderer.captureTarget();
        if(output == null) return;
        TIMER.poll(BUDGET);
        long now = System.nanoTime();
        requested = now;
        if(now >= nextUpdate && !queued){ PENDING.addLast(this); queued = true; }
        // Rotate due sources so a busy first feed cannot starve other visible cameras
        while(!PENDING.isEmpty() && now - PENDING.getFirst().requested > 350_000_000L) PENDING.removeFirst().queued = false;
        if(PENDING.peekFirst() == this && BUDGET.ready(now)){
            PENDING.removeFirst(); queued = false;
            int width = Math.min(160, output.width);
            int height = Math.max(1, Math.round(width * output.height / (float)output.width));
            if(target == null || target.width != width || target.height != height){
                if(target != null) target.destroyBuffers();
                target = SceneRenderTargets.createLike(output, width, height, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_LINEAR);
                ready = false;
            }
            view.set(currentView);
            projection.setPerspective((float)Math.toRadians(ViewSceneRenderer.capturePose().fov()), width / (float)height, 8, 65_536);
            long started = System.nanoTime();
            TIMER.begin();
            ViewSceneRenderer.lodTarget(target);
            try{
                target.setClearColor(0, 0, 0, 0);
                GL11.glColorMask(true, true, true, true);
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glClearDepth(1);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                ready = draw.getAsBoolean();
            }finally{
                ViewSceneRenderer.lodTarget(null);
                long end = System.nanoTime();
                TIMER.end(end, end - started);
                BUDGET.completed(end, end - started);
                nextUpdate = end + 250_000_000L;
            }
        }
        if(!ready) return;
        output.bindWrite(true);
        draw(new Matrix4f(projection).mul(view).mul(new Matrix4f(currentView).invert())
                .mul(new Matrix4f(currentProjection).invert()));
    }

    // Reproject the retained view during camera rotation without rerunning a world renderer
    private void draw(Matrix4f matrix){
        if(program == 0){
            program = GL20.glCreateProgram();
            int vert = shader(GL20.GL_VERTEX_SHADER, """
                    #version 150
                    out vec2 Screen;
                    void main(){
                        vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        Screen = pos * 2.0 - 1.0;
                        gl_Position = vec4(Screen, 0.0, 1.0);
                    }
                    """);
            int frag = shader(GL20.GL_FRAGMENT_SHADER, """
                    #version 150
                    uniform sampler2D LodColor;
                    uniform mat4 Reprojection;
                    in vec2 Screen;
                    out vec4 FragColor;
                    void main(){
                        vec4 prev = Reprojection * vec4(Screen, 1.0, 1.0);
                        vec2 uv = prev.xy / prev.w * 0.5 + 0.5;
                        if(prev.w <= 0.0 || any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
                        FragColor = texture(LodColor, uv);
                    }
                    """);
            GL20.glAttachShader(program, vert); GL20.glAttachShader(program, frag);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vert); GL20.glDeleteShader(frag);
            if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            transform = GL20.glGetUniformLocation(program, "Reprojection");
            array = GL30.glGenVertexArrays();
        }
        GL20.glUseProgram(program);
        try(var stack = MemoryStack.stackPush()){
            GL20.glUniformMatrix4fv(transform, false, matrix.get(stack.mallocFloat(16)));
        }
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "LodColor"), 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if(GL.getCapabilities().OpenGL33) GL33.glBindSampler(0, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_BLEND);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glBindVertexArray(array);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Compile only the small library-owned background compositor
    private static int shader(int type, String source){
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    @Override public void close(){
        PENDING.remove(this); queued = false;
        if(target != null) target.destroyBuffers(); target = null;
    }

    // Release the shared timing query and compositor when the world or resource set changes
    static void clear(){
        TIMER.close(); BUDGET.reset(); PENDING.clear();
        if(program != 0) GL20.glDeleteProgram(program);
        if(array != 0) GL30.glDeleteVertexArrays(array);
        program = array = 0;
    }
}
