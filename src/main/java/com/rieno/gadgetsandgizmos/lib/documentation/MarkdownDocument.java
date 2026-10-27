package com.rieno.gadgetsandgizmos.lib.documentation;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Parse CommonMark and GitHub extensions into an immutable render tree without exposing parser dependencies.
public record MarkdownDocument(List<Node> nodes){
    private static final List<Extension> EXTENSIONS = List.of(AutolinkExtension.create(), TablesExtension.create(),
            StrikethroughExtension.create(), TaskListItemsExtension.create(), FootnotesExtension.create(), HeadingAnchorExtension.create());
    private static final Pattern DETAILS = Pattern.compile("(?is)(<details\\b[^>]*>\\s*<summary\\b[^>]*>.*?</summary>)(.*?)(</details>)");

    public MarkdownDocument{ nodes = List.copyOf(nodes); }

    public static MarkdownDocument parse(String markdown){
        Parser parser = Parser.builder().extensions(EXTENSIONS).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).sanitizeUrls(true).build();
        var ast = parser.parse(markdown == null ? "" : markdown);
        ast.accept(new AbstractVisitor(){
            @Override
            public void visit(HtmlBlock block){
                // GitHub wikis commonly place Markdown tables directly inside details sections
                var matcher = DETAILS.matcher(block.getLiteral());
                block.setLiteral(matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                        match.group(1) + renderer.render(parser.parse(match.group(2).strip())) + match.group(3))));
            }
        });
        var body = Jsoup.parseBodyFragment(renderer.render(ast)).body();
        body.select("script, style, iframe, object, embed, form").remove();
        return new MarkdownDocument(children(body));
    }

    private static List<Node> children(Element parent){
        var nodes = new ArrayList<Node>();
        for(var node : parent.childNodes()){
            if(node instanceof TextNode text) nodes.add(new Node("#text", text.getWholeText(), Map.of(), List.of()));
            else if(node instanceof Element element){
                var attrs = new LinkedHashMap<String, String>();
                for(var attr : element.attributes()) attrs.put(attr.getKey(), attr.getValue());
                nodes.add(new Node(element.normalName(), "", attrs, children(element)));
            }
        }
        return List.copyOf(nodes);
    }

    public record Node(String tag, String text, Map<String, String> attributes, List<Node> children){
        public Node{ attributes = Map.copyOf(attributes); children = List.copyOf(children); }
        public String attribute(String key){ return attributes.getOrDefault(key, ""); }
        public String plainText(){
            if(tag.equals("#text")) return text;
            return children.stream().map(Node::plainText).collect(java.util.stream.Collectors.joining());
        }
    }
}
