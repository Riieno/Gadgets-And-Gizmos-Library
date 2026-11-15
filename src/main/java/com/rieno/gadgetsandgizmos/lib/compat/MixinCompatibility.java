package com.rieno.gadgetsandgizmos.lib.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.service.MixinService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

// Check optional dependencies before Mixin tries to resolve their targets
public final class MixinCompatibility{
    private MixinCompatibility(){
    }

    // Use the discovery list while the runtime mod list is still being built
    public static boolean isModLoaded(String modId){
        LoadingModList loading = LoadingModList.get();
        if(loading != null) return loading.getModFileById(modId) != null;
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }

    // Check the class resource without loading the target or invoking transformers
    public static boolean canApply(String targetClassName, String... requiredMods){
        for(String modId : requiredMods){
            if(!isModLoaded(modId)) return false;
        }
        String resource = targetClassName.replace('.', '/') + ".class";
        try(InputStream stream = MixinService.getService().getResourceAsStream(resource)){
            return stream != null;
        }catch(IOException ignored){
            return false;
        }
    }

    // Check optional target fields without loading or transforming the target class
    public static boolean hasField(String targetClassName, String name, String descriptor){
        var target = shape(targetClassName);
        return target != null && target.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor));
    }

    // Check an injection signature against the installed dependency instead of its version label
    public static boolean hasMethod(String targetClassName, String name, String descriptor){
        var target = shape(targetClassName);
        return target != null && target.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    private static ClassNode shape(String targetClassName){
        try(InputStream stream = MixinService.getService().getResourceAsStream(targetClassName.replace('.', '/') + ".class")){
            if(stream == null) return null;
            var target = new ClassNode();
            new ClassReader(stream).accept(target, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return target;
        }catch(IOException | IllegalArgumentException ignored){ return null; }
    }
}

