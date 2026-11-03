package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static org.lwjgl.opengl.GL33.*;

// Exercise full-bright item lighting and delayed shell draws with Minecraft's real shaders
public final class FluidPreviewMaterialGpuCheck{
    private static final int WIDTH = 640, HEIGHT = 480;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void main(String[] args) throws Exception{
        if(!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize the material check");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Fluid material validation", 0, 0);
        if(window == 0) throw new IllegalStateException("Unable to create the hidden material context");
        try(var resources = new ZipFile(args[2])){
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Native fluid material check: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER));
            int[] textures = {texture(0, 1, 1, 255), texture(1, 16, 16, 255), texture(2, 16, 16, 0)};
            glActiveTexture(GL_TEXTURE2);
            var bright = BufferUtils.createByteBuffer(4).putInt(-1).flip();
            glTexSubImage2D(GL_TEXTURE_2D, 0, 15, 15, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bright);
            int vao = glGenVertexArrays(), buffer = glGenBuffers();
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 48, 0);
            glEnableVertexAttribArray(1); glVertexAttribPointer(1, 4, GL_FLOAT, false, 48, 12);
            glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, 48, 28);
            glVertexAttribI2i(3, 0, 10); glVertexAttribI2i(4, 240, 240);
            glEnableVertexAttribArray(5); glVertexAttribPointer(5, 3, GL_FLOAT, false, 48, 36);
            var view = new Matrix4f().rotateX(.35F).rotateY(-.9F).translate(-.5F, -.5F, -1.2F);
            var projection = new Matrix4f().ortho(-1.7F, 1.7F, -1.4F, 1.4F, -20, 20);
            var snapshots = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
            for(int mode = 0; mode < 3; mode++){
                boolean emissive = mode > 0, legacy = mode == 2;
                String id = legacy ? "rendertype_entity_translucent_emissive" : "rendertype_entity_translucent_cull";
                int vertex = shader(GL_VERTEX_SHADER, source(resources, "assets/minecraft/shaders/core/" + id + ".vsh", false));
                int fragment = shader(GL_FRAGMENT_SHADER, source(resources, "assets/minecraft/shaders/core/" + id + ".fsh", false));
                int program = glCreateProgram();
                glAttachShader(program, vertex); glAttachShader(program, fragment);
                String[] attributes = {"Position", "Color", "UV0", "UV1", "UV2", "Normal"};
                for(int idx = 0; idx < attributes.length; idx++) glBindAttribLocation(program, idx, attributes[idx]);
                glLinkProgram(program);
                if(glGetProgrami(program, GL_LINK_STATUS) == 0) throw new IllegalStateException(glGetProgramInfoLog(program));
                glUseProgram(program);
                matrix(program, "ModelViewMat", view); matrix(program, "ProjMat", projection);
                glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
                glUniform1i(glGetUniformLocation(program, "Sampler1"), 1);
                glUniform1i(glGetUniformLocation(program, "Sampler2"), 2);
                glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
                glUniform3f(glGetUniformLocation(program, "Light0_Direction"), 0, 0, 1);
                glUniform3f(glGetUniformLocation(program, "Light1_Direction"), 0, 0, 1);
                glUniform1f(glGetUniformLocation(program, "FogStart"), 10);
                glUniform1f(glGetUniformLocation(program, "FogEnd"), 1000);
                glUniform4f(glGetUniformLocation(program, "FogColor"), 0, 0, 0, 1);
                glUniform1i(glGetUniformLocation(program, "FogShape"), 0);
                glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                if(legacy) glDisable(GL_CULL_FACE); else glEnable(GL_CULL_FACE);
                glFrontFace(GL_CCW); glViewport(0, 0, WIDTH, HEIGHT);
                String label = legacy ? "legacy-emissive" : emissive ? "emissive" : "ordinary";
                var output = Path.of(args[1], label);
                Files.createDirectories(output);
                for(var val : snapshots){
                    var snapshot = val.getAsJsonObject();
                    var points = snapshot.getAsJsonArray("vertices");
                    var colors = snapshot.getAsJsonArray("colors");
                    var lights = snapshot.getAsJsonArray("lights");
                    for(var light : lights) if(light.getAsInt() != 0xF000F0) throw new IllegalStateException("Emissive geometry lost its full-bright light");
                    int light = emissive ? lights.get(0).getAsInt() : 0;
                    glVertexAttribI2i(4, light & 65535, light >>> 16);
                    matrix(program, "ModelViewMat", view); matrix(program, "ProjMat", projection);
                    var order = new ArrayList<Integer>();
                    for(int idx = 0; idx < points.size(); idx += 4) order.add(idx);
                    order.sort(Comparator.comparingDouble(idx -> viewDepth(points, idx, view)));
                    var data = BufferUtils.createFloatBuffer(points.size() / 4 * 6 * 12);
                    for(int quad : order) for(int corner : new int[]{0, 1, 2, 0, 2, 3}){
                        var point = points.get(quad + corner).getAsJsonArray();
                        int color = colors.get(quad + corner).getAsInt();
                        data.put(point.get(0).getAsFloat()).put(point.get(1).getAsFloat()).put(point.get(2).getAsFloat())
                                .put((color >> 16 & 255) / 255F).put((color >> 8 & 255) / 255F).put((color & 255) / 255F).put(1)
                                .put(.5F).put(.5F).put(0).put(0).put(1);
                    }
                    data.flip(); glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
                    glDepthMask(true); glClearColor(23 / 255F, 29 / 255F, 38 / 255F, 1);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    glDepthMask(!legacy);
                    glDrawArrays(GL_TRIANGLES, 0, points.size() / 4 * 6);
                    var pixels = pixels();
                    var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                    int visible = 0;
                    long brightness = 0;
                    for(int y = 0; y < HEIGHT; y++) for(int x = 0; x < WIDTH; x++){
                        int idx = (y * WIDTH + x) * 4;
                        int red = pixels.get(idx) & 255, green = pixels.get(idx + 1) & 255, blue = pixels.get(idx + 2) & 255;
                        int rgb = red << 16 | green << 8 | blue;
                        if(rgb != 0x171D26){ visible++; brightness += red + green + blue; }
                        image.setRGB(x, HEIGHT - 1 - y, rgb);
                    }
                    double average = brightness / (double)Math.max(1, visible * 3);
                    if(visible < 1000 || glGetError() != GL_NO_ERROR || (emissive ? average < 25 : average > 2)){
                        throw new IllegalStateException("Invalid " + id + " frame " + snapshot.get("tick") + ": visible=" + visible + ", brightness=" + average);
                    }
                    ImageIO.write(image, "png", output.resolve(String.format("frame-%03d.png", snapshot.get("tick").getAsInt())).toFile());
                    // A shared glow buffer may flush before the fixed item buffer containing the barrel
                    shell(program, .9F, -1, 1, 0x685347);
                    var behind = pixels();
                    int overwritten = 0;
                    for(int idx = 0; idx < WIDTH * HEIGHT; idx++){
                        if(rgb(pixels, idx) != 0x171D26 && rgb(pixels, idx) != rgb(behind, idx)) overwritten++;
                    }
                    if(legacy ? overwritten < visible * .9D : overwritten != 0){
                        throw new IllegalStateException(label + " delayed rear shell overwrite: " + overwritten + "/" + visible);
                    }
                    if(legacy){
                        System.out.println("Reproduced the previous glow failure: delayed shell hides " + overwritten + " fluid pixels");
                        break;
                    }
                    shell(program, -.9F, -.3F, .3F, 0x685347);
                    var front = pixels();
                    int occluded = 0, retained = 0;
                    for(int idx = 0; idx < WIDTH * HEIGHT; idx++) if(rgb(pixels, idx) != 0x171D26){
                        if(rgb(front, idx) == rgb(pixels, idx)) retained++;
                        else occluded++;
                    }
                    if(occluded < 100 || retained < 1000 || glGetError() != GL_NO_ERROR){
                        throw new IllegalStateException(label + " does not respect front and rear barrel occlusion");
                    }
                }
                glDeleteProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
                if(!legacy) System.out.println(label + ": " + snapshots.size() + " frames passed with dark lighting and delayed front/rear shell draws");
            }
            glDeleteBuffers(buffer); glDeleteVertexArrays(vao);
            for(int texture : textures) glDeleteTextures(texture);
        }finally{
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static double viewDepth(JsonArray points, int first, Matrix4f view){
        double z = 0;
        for(int idx = 0; idx < 4; idx++){
            var p = points.get(first + idx).getAsJsonArray();
            z += view.m02() * p.get(0).getAsFloat() + view.m12() * p.get(1).getAsFloat() + view.m22() * p.get(2).getAsFloat();
        }
        return z;
    }

    private static ByteBuffer pixels(){
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static int rgb(ByteBuffer pixels, int idx){
        return (pixels.get(idx * 4) & 255) << 16 | (pixels.get(idx * 4 + 1) & 255) << 8 | pixels.get(idx * 4 + 2) & 255;
    }

    private static void shell(int program, float depth, float left, float right, int color){
        matrix(program, "ModelViewMat", new Matrix4f()); matrix(program, "ProjMat", new Matrix4f());
        glVertexAttribI2i(4, 240, 240);
        float[][] points = {{left, -1, depth}, {right, -1, depth}, {right, 1, depth}, {left, 1, depth}};
        var data = BufferUtils.createFloatBuffer(6 * 12);
        for(int idx : new int[]{0, 1, 2, 0, 2, 3}){
            data.put(points[idx]).put((color >> 16 & 255) / 255F).put((color >> 8 & 255) / 255F)
                    .put((color & 255) / 255F).put(1).put(.5F).put(.5F).put(0).put(0).put(1);
        }
        data.flip(); glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
        glDepthMask(true); glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private static int texture(int unit, int width, int height, int brightness){
        int texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, texture);
        var data = BufferUtils.createByteBuffer(width * height * 4);
        for(int idx = 0; idx < width * height; idx++) data.put((byte)brightness).put((byte)brightness).put((byte)brightness).put((byte)255);
        data.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    private static void matrix(int program, String name, Matrix4f matrix){
        var values = BufferUtils.createFloatBuffer(16);
        matrix.get(values);
        glUniformMatrix4fv(glGetUniformLocation(program, name), false, values);
    }

    private static String source(ZipFile resources, String name, boolean included) throws Exception{
        String text;
        try(var stream = resources.getInputStream(resources.getEntry(name))){ text = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        if(included) text = text.replaceAll("(?m)^#version[^\r\n]*", "");
        var matcher = Pattern.compile("#moj_import\\s+<([^>]+)>").matcher(text);
        var res = new StringBuilder();
        while(matcher.find()){
            matcher.appendReplacement(res, java.util.regex.Matcher.quoteReplacement(source(resources, "assets/minecraft/shaders/include/" + matcher.group(1), true)));
        }
        matcher.appendTail(res);
        return res.toString();
    }

    private static int shader(int type, String source){
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        if(glGetShaderi(shader, GL_COMPILE_STATUS) == 0) throw new IllegalStateException(glGetShaderInfoLog(shader));
        return shader;
    }
}
