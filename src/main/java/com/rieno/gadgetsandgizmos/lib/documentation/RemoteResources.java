package com.rieno.gadgetsandgizmos.lib.documentation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// Fetch public documentation without blocking the game thread, revalidating cached responses.
public final class RemoteResources{
    private static final class Shared{ private static final RemoteResources INSTANCE = new RemoteResources(); }

    public static RemoteResources shared(){ return Shared.INSTANCE; }
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<URI, Cached> cache = new ConcurrentHashMap<>();

    public CompletableFuture<Download> fetch(URI uri, int maximumBytes){
        if(maximumBytes < 1 || maximumBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("A bounded resource size is required");
        if(!isWebUri(uri)) return CompletableFuture.failedFuture(new IOException("Unsupported documentation address"));
        Cached cached = cache.get(uri);
        Cached prev = cached != null && cached.download().bytes().length <= maximumBytes ? cached : null;
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "GadgetsAndGizmos-Documentation")
                .header("Cache-Control", "no-cache");
        if(prev != null && !prev.etag().isEmpty()) request.header("If-None-Match", prev.etag());
        var streamRef = new AtomicReference<java.io.InputStream>();
        return client.sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> { streamRef.set(response.body()); return response; })
                .thenApplyAsync(response -> {
                    try(var stream = response.body()){
                        if(response.statusCode() == 304 && prev != null) return prev.download();
                        if(response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                        byte[] bytes = stream.readNBytes(maximumBytes + 1);
                        if(bytes.length > maximumBytes) throw new IOException("Documentation resource is too large");
                        var result = new Download(bytes, response.headers().firstValue("Content-Type").orElse(""), false);
                        retain(uri, new Cached(result, response.headers().firstValue("ETag").orElse("")));
                        return result;
                    }catch(IOException ex){
                        throw new java.util.concurrent.CompletionException(ex);
                    }
                }).orTimeout(25, TimeUnit.SECONDS).whenComplete((download, error) -> {
                    if(error != null && streamRef.get() != null){
                        try{ streamRef.get().close(); }
                        catch(IOException ignored){}
                    }
                }).exceptionallyCompose(ex -> prev == null ? CompletableFuture.failedFuture(ex)
                        : CompletableFuture.completedFuture(new Download(prev.download().bytes(), prev.download().contentType(), true)));
    }

    private synchronized void retain(URI uri, Cached value){
        cache.put(uri, value);
        long bytes = cache.values().stream().mapToLong(entry -> entry.download().bytes().length).sum();
        var entries = cache.entrySet().iterator();
        while((cache.size() > 64 || bytes > 32L * 1024 * 1024) && entries.hasNext()){
            var entry = entries.next();
            bytes -= entry.getValue().download().bytes().length;
            entries.remove();
        }
    }

    public static boolean isWebUri(URI uri){
        return uri != null && uri.getHost() != null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    public record Download(byte[] bytes, String contentType, boolean stale){
        public String text(){ return new String(bytes, StandardCharsets.UTF_8); }
    }

    private record Cached(Download download, String etag){}
}
