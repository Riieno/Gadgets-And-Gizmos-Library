package com.rieno.gadgetsandgizmos.lib.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Derive a separate shader at resource reload while leaving the original resources intact
public final class ColorEffectShader{
    private ColorEffectShader(){}

    public static VertexFormat withTint(VertexFormat base){
        var builder = VertexFormat.builder();
        var names = base.getElementAttributeNames();
        var elements = base.getElements();
        for(int idx = 0; idx < names.size(); idx++) builder.add(names.get(idx), elements.get(idx));
        return builder.add("Normal", VertexFormatElement.NORMAL).padding(1).build();
    }

    // Carry an independent tint in the extra attribute so native style selection remains intact
    public static ShaderInstance create(ResourceProvider resources, ResourceLocation source,
                                        ResourceLocation id, VertexFormat format) throws IOException{
        ResourceProvider derived = location -> {
            String prefix = "shaders/core/" + id.getPath();
            if(!location.getNamespace().equals(id.getNamespace()) || !location.getPath().startsWith(prefix + ".")){
                return resources.getResource(location);
            }
            String suffix = location.getPath().substring(prefix.length());
            var original = resources.getResource(ResourceLocation.fromNamespaceAndPath(source.getNamespace(),
                    "shaders/core/" + source.getPath() + suffix));
            if(original.isEmpty()) return original;
            Resource resource = original.get();
            return java.util.Optional.of(new Resource(resource.source(), () -> {
                var writer = new java.io.StringWriter();
                try(var reader = resource.openAsReader()){ reader.transferTo(writer); }
                String transformed = transform(writer.toString(), suffix, id);
                return new ByteArrayInputStream(transformed.getBytes(StandardCharsets.UTF_8));
            }));
        };
        return new ShaderInstance(derived, id, format);
    }

    // Wrap main after the source has produced its original brightness, transparency and detail
    public static String transform(String source, String suffix, ResourceLocation id){
        if(suffix.equals(".json")){
            var json = JsonParser.parseString(source).getAsJsonObject();
            json.addProperty("vertex", id.toString());
            json.addProperty("fragment", id.toString());
            json.remove("blend");
            if(json.has("attributes")) json.getAsJsonArray("attributes").add("Normal");
            return json.toString();
        }
        String renamed = source.replaceFirst("void\\s+main\\s*\\(\\s*\\)", "void colorEffectOriginalMain()");
        int line = renamed.indexOf('\n') + 1;
        String declarations = suffix.equals(".vsh") ? "in vec3 Normal;\nout vec3 colorEffectTint;\n"
                : "in vec3 colorEffectTint;\n";
        renamed = renamed.substring(0, line) + declarations + renamed.substring(line);
        if(suffix.equals(".vsh")) return renamed + "\nvoid main(){ colorEffectOriginalMain(); colorEffectTint = Normal; }\n";
        return renamed + "\nvoid main(){\n    colorEffectOriginalMain();\n"
                + "    if(colorEffectTint.x >= 0.0){\n"
                + "        float brightness = dot(fragColor.rgb, vec3(0.2126, 0.7152, 0.0722));\n"
                + "        fragColor.rgb = brightness * colorEffectTint;\n    }\n}\n";
    }
}
