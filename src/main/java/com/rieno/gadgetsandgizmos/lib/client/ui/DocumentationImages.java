package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Image decoding stays off the render thread; texture ownership stays on it.
final class DocumentationImages implements AutoCloseable{
    private final Function<URI, CompletableFuture<byte[]>> load;
    private final Map<URI, Image> images = new HashMap<>();
    private final Runnable changed;
    private boolean closed;

    DocumentationImages(Runnable changed, Function<URI, CompletableFuture<byte[]>> load){
        this.changed = changed;
        this.load = load;
    }

    Image get(URI uri){
        if(closed || uri == null) return Image.FAILED;
        var image = images.get(uri);
        if(image != null) return image;
        if(images.size() >= 64) return Image.FAILED;
        images.put(uri, Image.LOADING);
        load.apply(uri).thenApplyAsync(bytes -> {
            try{
                var decoded = NativeImage.read(new ByteArrayInputStream(bytes));
                if(decoded.getWidth() > 4096 || decoded.getHeight() > 4096){
                    decoded.close();
                    throw new IllegalArgumentException("Documentation image is too large");
                }
                return decoded;
            }catch(Exception ex){ throw new java.util.concurrent.CompletionException(ex); }
        }).whenComplete((decoded, error) -> Minecraft.getInstance().execute(() -> {
            if(closed){
                if(decoded != null) decoded.close();
                return;
            }
            if(error != null) images.put(uri, Image.FAILED);
            else{
                var texture = new DynamicTexture(decoded);
                ResourceLocation id = Minecraft.getInstance().getTextureManager().register("documentation", texture);
                images.put(uri, new Image(id, decoded.getWidth(), decoded.getHeight(), false));
            }
            changed.run();
        }));
        return Image.LOADING;
    }

    @Override
    public void close(){
        closed = true;
        for(var image : images.values()) if(image.texture() != null) Minecraft.getInstance().getTextureManager().release(image.texture());
        images.clear();
    }

    record Image(ResourceLocation texture, int width, int height, boolean failed){
        static final Image LOADING = new Image(null, 320, 100, false);
        static final Image FAILED = new Image(null, 320, 24, true);
    }
}
