package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalAccess;
import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftTerminalState;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import dan200.computercraft.shared.computer.terminal.TerminalState;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Preserve native terminal colors through packets, saved computers and monitor rebuilds
@Mixin(value = NetworkedTerminal.class, remap = false)
public abstract class ComputerCraftNetworkedTerminalMixin{
    // Keep CC's native decoder and its size assertion limited to ordinary character data
    @Redirect(method = "read", at = @At(value = "FIELD", target = "Ldan200/computercraft/shared/computer/terminal/TerminalState;contents:[B"))
    private byte[] gadgetsngizmos$contents(TerminalState state){ return ((ComputerCraftTerminalState)state).nativeContents(); }
    @Inject(method = "write", at = @At("RETURN"))
    private void gadgetsngizmos$snapshot(CallbackInfoReturnable<TerminalState> cir){
        ((ComputerCraftTerminalState)cir.getReturnValue()).terminalColors(((ComputerCraftTerminalAccess)this).terminalColors().encode());
    }
    @Inject(method = "read", at = @At("RETURN"))
    private void gadgetsngizmos$apply(TerminalState state, CallbackInfo ci){
        ((ComputerCraftTerminalAccess)this).terminalColors().decode(((ComputerCraftTerminalState)state).terminalColors());
    }
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void gadgetsngizmos$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir){
        cir.getReturnValue().putByteArray("gadgetsngizmos:terminal_colors", ((ComputerCraftTerminalAccess)this).terminalColors().encode());
    }
    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void gadgetsngizmos$load(CompoundTag tag, CallbackInfo ci){
        ((ComputerCraftTerminalAccess)this).terminalColors().decode(tag.getByteArray("gadgetsngizmos:terminal_colors"));
    }
}
