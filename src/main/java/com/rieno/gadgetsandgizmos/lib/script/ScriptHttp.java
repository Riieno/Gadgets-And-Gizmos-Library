package com.rieno.gadgetsandgizmos.lib.script;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// Fetch bounded HTTP responses off the game thread without granting local network access
public final class ScriptHttp{
    public record Request(String url, String method, String body, Map<String, String> headers){
        public Request{ headers = Map.copyOf(headers); }
    }
    public record Response(int code, String body, Map<String, String> headers){}
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), task -> { Thread thread = new Thread(task, "G&G Lua HTTP"); thread.setDaemon(true); return thread; });
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private ScriptHttp(){}

    public static CompletionStage<Response> request(Request req){
        return CompletableFuture.supplyAsync(() -> {
            try{
                URI uri = URI.create(req.url());
                for(int redirect = 0; redirect <= 5; redirect++){
                    validate(uri);
                    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
                    req.headers().forEach(request::header);
                    request.method(req.method(), req.body() == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(req.body()));
                    var response = CLIENT.send(request.build(), info -> new Body());
                    if(response.statusCode() >= 300 && response.statusCode() < 400 && response.headers().firstValue("location").isPresent()){
                        uri = uri.resolve(response.headers().firstValue("location").orElseThrow()); continue;
                    }
                    byte[] bytes = response.body();
                    Map<String, String> headers = new java.util.LinkedHashMap<>();
                    response.headers().map().forEach((key, vals) -> headers.put(key, String.join(", ", vals)));
                    return new Response(response.statusCode(), new String(bytes, StandardCharsets.UTF_8), Map.copyOf(headers));
                }
                throw new IllegalArgumentException("Too many HTTP redirects");
            }catch(Exception ex){ throw new CompletionException(ex); }
        }, EXECUTOR);
    }

    private static void validate(URI uri) throws Exception{
        if(!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Expected an HTTP or HTTPS URL");
        for(InetAddress address : InetAddress.getAllByName(uri.getHost())){
            byte[] bytes = address.getAddress();
            if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC)
                throw new IllegalArgumentException("Local network HTTP requests are unavailable");
        }
    }

    // Limit the body while it arrives so oversized or stalled responses do not retain a worker
    private static final class Body implements HttpResponse.BodySubscriber<byte[]>{
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody(){ return result; }
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){ this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers){
            for(var buffer : buffers){
                if(data.size() + buffer.remaining() > 32768){
                    subscription.cancel(); result.completeExceptionally(new ScriptException("HTTP response exceeds 32768 bytes")); return;
                }
                byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); data.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error){ result.completeExceptionally(error); }
        @Override public void onComplete(){ result.complete(data.toByteArray()); }
    }
}
