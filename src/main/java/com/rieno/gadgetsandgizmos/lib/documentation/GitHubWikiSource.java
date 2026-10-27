package com.rieno.gadgetsandgizmos.lib.documentation;

import org.jsoup.Jsoup;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Discover wiki pages from GitHub's live sidebar and load their original Markdown.
public final class GitHubWikiSource implements DocumentationSource{
    private final URI root;
    private final URI rawRoot;
    private final Function<URI, CompletableFuture<RemoteResources.Download>> fetch;

    public GitHubWikiSource(URI wiki){
        this(wiki, new RemoteResources());
    }

    public GitHubWikiSource(URI wiki, RemoteResources resources){
        this(wiki, uri -> resources.fetch(uri, 2 * 1024 * 1024));
    }

    public GitHubWikiSource(URI wiki, Function<URI, CompletableFuture<RemoteResources.Download>> fetch){
        String path = wiki.getPath().replaceAll("/+$", "");
        if(!"github.com".equalsIgnoreCase(wiki.getHost()) || !path.matches("/[^/]+/[^/]+/wiki")){
            throw new IllegalArgumentException("Expected a GitHub wiki root");
        }
        root = URI.create("https://github.com" + path + "/");
        rawRoot = URI.create("https://raw.githubusercontent.com/wiki" + path.substring(0, path.length() - 5) + "/");
        this.fetch = java.util.Objects.requireNonNull(fetch);
    }

    @Override
    public CompletableFuture<Index> pages(){
        return pageIndex(root.resolve("_pages"), new java.util.HashSet<>())
                .exceptionallyCompose(error -> pageIndex(root, new java.util.HashSet<>()));
    }

    private CompletableFuture<Index> pageIndex(URI uri, java.util.Set<URI> visited){
        if(visited.size() >= 64 || !visited.add(uri)) return CompletableFuture.completedFuture(new Index(List.of(), false));
        return fetch.apply(uri).thenCompose(download -> {
            var html = Jsoup.parse(download.text(), root.toString());
            var list = html.selectFirst("#wiki-pages-box");
            if(list == null) list = html.selectFirst("#wiki-content");
            if(list == null && !uri.equals(root) && !uri.getPath().endsWith("/_pages")) list = html.body();
            if(list == null) return CompletableFuture.failedFuture(new IllegalStateException("Wiki page list is unavailable"));
            var pages = new LinkedHashMap<String, Page>();
            pages.put("Home", new Page("Home", "Home", root.resolve("Home")));
            collectPages(list, pages);
            var next = new ArrayList<URI>();
            for(var link : html.select("a[rel=next][href], .paginate-container a.next_page[href]")){
                URI target = uri.resolve(link.attr("href"));
                if(target.getHost().equals(root.getHost()) && target.getPath().equals(root.resolve("_pages").getPath())) next.add(target);
            }
            for(var fragment : list.select("include-fragment[src]")){
                String target = fragment.absUrl("src");
                if(target.contains("/wiki-pages") || target.contains("/wiki/_pages")) next.add(URI.create(target));
            }
            CompletableFuture<Index> result = CompletableFuture.completedFuture(new Index(List.copyOf(pages.values()), download.stale()));
            for(URI target : next){
                result = result.thenCompose(prev -> pageIndex(target, visited).thenApply(extra -> {
                    var merged = new LinkedHashMap<String, Page>();
                    for(var page : prev.pages()) merged.put(page.id(), page);
                    for(var page : extra.pages()) merged.putIfAbsent(page.id(), page);
                    return new Index(List.copyOf(merged.values()), prev.stale() || extra.stale());
                }));
            }
            return result.thenApply(index -> new Index(index.pages().stream().sorted(
                    Comparator.comparing((Page page) -> !page.id().equals("Home"))
                            .thenComparing(Page::title, String.CASE_INSENSITIVE_ORDER)).toList(), index.stale()));
        });
    }

    private void collectPages(org.jsoup.nodes.Element element, LinkedHashMap<String, Page> pages){
        for(var link : element.select("a[href]")){
            URI uri;
            try{ uri = root.resolve(link.attr("href")); }
            catch(IllegalArgumentException ex){ continue; }
            if(!root.getHost().equalsIgnoreCase(uri.getHost()) || !uri.getPath().startsWith(root.getPath())) continue;
            String encoded = uri.getRawPath().substring(root.getRawPath().length());
            if(encoded.isEmpty() || encoded.contains("/") || encoded.startsWith("_")) continue;
            String id = URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
            String title = link.text().strip();
            pages.putIfAbsent(id, new Page(id, title.isEmpty() ? id.replace('-', ' ') : title, root.resolve(encoded)));
        }
    }

    @Override
    public CompletableFuture<Content> load(Page page){
        URI raw = rawRoot.resolve(encode(page.id()) + ".md");
        return fetch.apply(raw).thenApply(download -> new Content(page, MarkdownDocument.parse(download.text()), download.stale()));
    }

    @Override
    public URI assetUri(Page page, String target){
        return rawRoot.resolve(target.replace(" ", "%20"));
    }

    @Override
    public URI linkUri(Page page, String target){
        URI uri = page.uri().resolve(target.replace(" ", "%20"));
        if(uri.getHost() != null && uri.getHost().equalsIgnoreCase(root.getHost()) && uri.getPath().startsWith(root.getPath())
                && uri.getPath().endsWith(".md")){
            String value = uri.toString();
            uri = URI.create(value.replaceFirst("\\.md(?=#|$)", ""));
        }
        return uri;
    }

    private static String encode(String value){
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

}
