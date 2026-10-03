package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Recheck permissions when a container is locked after its menu was opened
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuAccessMixin{
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$guardClick(int slot, int button, ClickType type, Player player, CallbackInfo ci){
        if(!(player instanceof ServerPlayer serverPlayer)) return;
        for(Slot entry : slots){
            boolean allowed = ContainerAccessRegistry.canOpen(serverPlayer, entry.container);
            if(entry instanceof SlotItemHandler handler) allowed &= ContainerAccessRegistry.canOpen(serverPlayer, handler.getItemHandler());
            if(allowed) continue;
            serverPlayer.closeContainer();
            ci.cancel();
            return;
        }
    }
}
