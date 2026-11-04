package com.rieno.gadgetsandgizmos.lib.documentation;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Read a versioned wiki snapshot and images from the owning mod's packaged resources.
public final class BundledDocumentationSource implements DocumentationSource{
    private static final URI ROOT = URI.create("gng-docs://bundled/");
    private final Function<String, InputStream> resources;
    private final String directory;
    private volatile Map<String, Entry> entries = Map.of();
    private volatile String version = "";

    public BundledDocumentationSource(Class<?> owner, String directory){
        this(owner::getResourceAsStream, directory);
    }

    public BundledDocumentationSource(Function<String, InputStream> resources, String directory){
        this.resources = java.util.Objects.requireNonNull(resources);
        this.directory = "/" + directory.replace('\\', '/').replaceAll("^/+|/+$", "") + "/";
    }

    public boolean hasPages(){ return !entries.isEmpty(); }
    public String version(){ return version; }

    @Override
    public CompletableFuture<Index> pages(){
        return CompletableFuture.supplyAsync(() -> {
            try{
                var root = JsonParser.parseString(new String(read("index.json", 2 * 1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
                var found = new LinkedHashMap<String, Entry>();
                for(var raw : root.getAsJsonArray("pages")){
                    var json = raw.getAsJsonObject();
                    String id = json.get("id").getAsString();
                    String file = safePath(json.get("file").getAsString());
                    try(var stream = resources.apply(directory + file)){
                        if(stream == null) continue;
                    }
                    String title = json.has("title") ? json.get("title").getAsString() : id.replace('-', ' ');
                    var page = new Page(id, title, ROOT.resolve(encode(id)));
                    found.put(id, new Entry(page, file));
                }
                entries = java.util.Collections.unmodifiableMap(found);
                version = root.has("version") ? root.get("version").getAsString() : "";
                return new Index(found.values().stream().map(Entry::page).toList(), false);
            }catch(Exception ex){
                entries = Map.of();
                return new Index(List.of(), false);
            }
        });
    }

    @Override
    public CompletableFuture<Content> load(Page page){
        return CompletableFuture.supplyAsync(() -> {
            Entry entry = entries.get(page.id());
            if(entry == null) throw new java.util.concurrent.CompletionException(new IOException("Documentation page is not bundled"));
            try{
                String markdown = new String(read(entry.file(), 2 * 1024 * 1024), StandardCharsets.UTF_8);
                return new Content(page, MarkdownDocument.parse(markdown), false);
            }catch(IOException ex){ throw new java.util.concurrent.CompletionException(ex); }
        });
    }

    @Override
    public URI assetUri(Page page, String target){
        URI uri = ROOT.resolve(target.replace(" ", "%20"));
        if(!"gng-docs".equals(uri.getScheme()) || !"bundled".equals(uri.getHost())) throw new IllegalArgumentException("Image is not bundled");
        safePath(uri.getPath().replaceFirst("^/", ""));
        return uri;
    }

    @Override
    public CompletableFuture<byte[]> loadAsset(URI uri){
        return CompletableFuture.supplyAsync(() -> {
            try{
                if(!"gng-docs".equals(uri.getScheme()) || !"bundled".equals(uri.getHost())) throw new IOException("Image is not bundled");
                return read(uri.getPath().replaceFirst("^/", ""), 8 * 1024 * 1024);
            }catch(IOException | IllegalArgumentException ex){ throw new java.util.concurrent.CompletionException(ex); }
        });
    }

    @Override
    public URI linkUri(Page page, String target){
        URI uri = page.uri().resolve(target.replace(" ", "%20"));
        if(!"gng-docs".equals(uri.getScheme()) || !"bundled".equals(uri.getHost())) return uri;
        String id = uri.getPath().replaceFirst("^/", "").replaceFirst("\\.md$", "");
        Entry found = entries.get(id);
        if(found == null) found = entries.values().stream().filter(entry -> normalized(entry.page().id()).equals(normalized(id))).findFirst().orElse(null);
        if(found == null) return uri;
        return URI.create(found.page().uri().toString() + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment()));
    }

    private byte[] read(String file, int maximum) throws IOException{
        try(var stream = resources.apply(directory + safePath(file))){
            if(stream == null) throw new IOException("Documentation resource is missing");
            byte[] bytes = stream.readNBytes(maximum + 1);
            if(bytes.length > maximum) throw new IOException("Documentation resource is too large");
            return bytes;
        }
    }

    private static String safePath(String file){
        if(file.isBlank() || file.startsWith("/") || file.contains("\\") || file.contains(":")
                || List.of(file.split("/")).contains("..")) throw new IllegalArgumentException("Invalid bundled documentation path");
        return file;
    }

    private static String encode(String val){ return java.net.URLEncoder.encode(val, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String normalized(String val){ return val.replace('_', '-').replace(' ', '-').toLowerCase(java.util.Locale.ROOT); }
    private record Entry(Page page, String file){}
}
