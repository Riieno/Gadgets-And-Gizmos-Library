package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalColors;
import dan200.computercraft.core.apis.TermMethods;
import dan200.computercraft.core.methods.NamedMethod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Extend both Lua APIs and peripheral methods while preserving CC's ordinary argument rules
@Mixin(targets = "dan200.computercraft.core.asm.MethodSupplierImpl", remap = false)
public abstract class ComputerCraftTerminalMethodSupplierMixin{
    @Inject(method = "getMethodsImpl", at = @At("RETURN"), cancellable = true)
    private void gadgetsngizmos$methods(Class<?> type, CallbackInfoReturnable<List<NamedMethod<Object>>> cir){
        if(!TermMethods.class.isAssignableFrom(type)) return;
        cir.setReturnValue(cir.getReturnValue().stream().map(method -> new NamedMethod<>(method.name(),
                ComputerCraftTerminalColors.wrap(method.name(), method.method()), false, method.genericType())).toList());
    }
}
