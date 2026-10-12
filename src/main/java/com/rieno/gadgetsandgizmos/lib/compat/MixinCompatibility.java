package com.rieno.gadgetsandgizmos.lib.compat;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.service.MixinService;

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
}

