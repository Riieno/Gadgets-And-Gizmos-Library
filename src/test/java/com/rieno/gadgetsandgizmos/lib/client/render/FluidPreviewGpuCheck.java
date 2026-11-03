package com.rieno.gadgetsandgizmos.lib.client.render;

import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL33.*;

// Check actual preview vertices with a hidden portable OpenGL context, without opening a game world
public final class FluidPreviewGpuCheck{
    public static void main(String[] args) throws Exception{
        if(!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize the GPU render check");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(640, 480, "Fluid preview validation", 0, 0);
        if(window == 0) throw new IllegalStateException("Unable to create the hidden GPU context");
        try{
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU render check: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER));
            int vertex = shader(GL_VERTEX_SHADER, "#version 150\nin vec3 Position; in vec4 Color; uniform mat4 MVP; out vec4 tint; void main(){gl_Position=MVP*vec4(Position,1);tint=Color;}");
            int fragment = shader(GL_FRAGMENT_SHADER, "#version 150\nin vec4 tint; out vec4 fragColor; void main(){fragColor=tint;}");
            int program = glCreateProgram();
            glAttachShader(program, vertex); glAttachShader(program, fragment);
            glBindAttribLocation(program, 0, "Position"); glBindAttribLocation(program, 1, "Color");
            glLinkProgram(program);
            if(glGetProgrami(program, GL_LINK_STATUS) == 0) throw new IllegalStateException(glGetProgramInfoLog(program));
            glUseProgram(program);
            var matrix = new Matrix4f().ortho(-1.7F, 1.7F, -1.4F, 1.4F, -20, 20)
                    .rotateX(.35F).rotateY(-.9F).translate(-.5F, -.5F, -1.2F);
            var uniform = BufferUtils.createFloatBuffer(16);
            matrix.get(uniform);
            glUniformMatrix4fv(glGetUniformLocation(program, "MVP"), false, uniform);
            int vao = glGenVertexArrays(), buffer = glGenBuffers();
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 28, 0);
            glEnableVertexAttribArray(1); glVertexAttribPointer(1, 4, GL_FLOAT, false, 28, 12);
            glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glFrontFace(GL_CCW);
            glViewport(0, 0, 640, 480);
            glClearColor(23 / 255F, 29 / 255F, 38 / 255F, 1);
            var output = Path.of(args[1]);
            Files.createDirectories(output);
            var snapshots = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
            for(var val : snapshots){
                var snapshot = val.getAsJsonObject();
                var points = snapshot.getAsJsonArray("vertices");
                var colors = snapshot.getAsJsonArray("colors");
                var data = BufferUtils.createFloatBuffer(points.size() / 4 * 6 * 7);
                for(int quad = 0; quad < points.size(); quad += 4) for(int corner : new int[]{0, 1, 2, 0, 2, 3}){
                    var point = points.get(quad + corner).getAsJsonArray();
                    int color = colors.get(quad + corner).getAsInt();
                    data.put(point.get(0).getAsFloat()).put(point.get(1).getAsFloat()).put(point.get(2).getAsFloat())
                            .put((color >> 16 & 255) / 255F).put((color >> 8 & 255) / 255F).put((color & 255) / 255F).put(1);
                }
                data.flip();
                glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glDrawArrays(GL_TRIANGLES, 0, points.size() / 4 * 6);
                var pixels = BufferUtils.createByteBuffer(640 * 480 * 4);
                glReadPixels(0, 0, 640, 480, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
                int visible = 0;
                for(int y = 0; y < 480; y++) for(int x = 0; x < 640; x++){
                    int idx = (y * 640 + x) * 4;
                    int rgb = (pixels.get(idx) & 255) << 16 | (pixels.get(idx + 1) & 255) << 8 | pixels.get(idx + 2) & 255;
                    if(rgb != 0x171D26) visible++;
                    image.setRGB(x, 479 - y, rgb);
                }
                if(visible < 1000 || glGetError() != GL_NO_ERROR) throw new IllegalStateException("Invalid GPU frame at " + snapshot.get("tick"));
                ImageIO.write(image, "png", output.resolve(String.format("frame-%03d.png", snapshot.get("tick").getAsInt())).toFile());
            }
            glDeleteBuffers(buffer); glDeleteVertexArrays(vao); glDeleteProgram(program);
            glDeleteShader(vertex); glDeleteShader(fragment);
            System.out.println("GPU render check passed for " + snapshots.size() + " frames: " + output);
        }finally{
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static int shader(int type, String source){
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        if(glGetShaderi(shader, GL_COMPILE_STATUS) == 0) throw new IllegalStateException(glGetShaderInfoLog(shader));
        return shader;
    }
}
