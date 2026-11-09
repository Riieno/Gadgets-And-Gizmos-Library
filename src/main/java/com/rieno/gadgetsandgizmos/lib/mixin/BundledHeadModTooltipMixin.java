package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.item.BundledSupporterHeadStack;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

// Correct registry-based mod labels appended after the normal tooltip event
@Mixin(value = ItemStack.class, priority = 400)
public abstract class BundledHeadModTooltipMixin{
    // Replace only a viewer's trailing mod label on a packaged player head
    @WrapMethod(method = "getTooltipLines")
    private List<Component> gadgetsngizmos$headModLabel(Item.TooltipContext ctx, Player player, TooltipFlag flag,
                                                       Operation<List<Component>> originalTooltip){
        List<Component> lines = originalTooltip.call(ctx, player, flag);
        ItemStack stack = (ItemStack)(Object)this;
        String owner = BundledSupporterHeadStack.creatorModId(stack);
        if(owner == null || lines == null || lines.isEmpty()) return lines;
        String original = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        if(owner.equals(original)) return lines;
        String originalName = ModList.get().getModContainerById(original)
                .map(mod -> mod.getModInfo().getDisplayName()).orElse(original);
        Component last = lines.getLast();
        var style = last.getStyle();
        if(!last.getString().equals(originalName) || !style.isItalic()
                || style.getColor() == null || style.getColor().getValue() != ChatFormatting.BLUE.getColor()) return lines;
        String name = ModList.get().getModContainerById(owner)
                .map(mod -> mod.getModInfo().getDisplayName()).orElse(owner);
        lines.set(lines.size() - 1, Component.literal(name).setStyle(style));
        return lines;
    }
}
