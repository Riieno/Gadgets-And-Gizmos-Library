package com.rieno.gadgetsandgizmos.lib.documentation;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// A live page index and Markdown content, independent of the owning screen.
public interface DocumentationSource{
    CompletableFuture<Index> pages();
    CompletableFuture<Content> load(Page page);
    URI assetUri(Page page, String target);
    URI linkUri(Page page, String target);

    default CompletableFuture<byte[]> loadAsset(URI uri){
        return RemoteResources.shared().fetch(uri, 8 * 1024 * 1024).thenApply(RemoteResources.Download::bytes);
    }

    record Page(String id, String title, URI uri){}
    record Index(List<Page> pages, boolean stale){
        public Index{ pages = List.copyOf(pages); }
    }
    record Content(Page page, MarkdownDocument document, boolean stale){}
}
