package com.rieno.gadgetsandgizmos.lib.menuconfig;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

// Resolve menu targets without trusting a client-supplied block entity
public final class MenuBackedBlockEntityResolver {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the menu backed block entity resolver
    private MenuBackedBlockEntityResolver() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve an exact target from the currently open menu only
    public static <B extends BlockEntity, M extends AbstractContainerMenu & MenuBackedBlockEntityTarget<B>> B resolveOpenMenu(
            IPayloadContext context,
            MenuConfigTarget target,
            Class<M> menuClass,
            Class<B> blockEntityClass) {
        if (context == null || context.player() == null || target == null) {
            return null;
        }
        AbstractContainerMenu openMenu = context.player().containerMenu;
        if (!menuClass.isInstance(openMenu)) {
            return null;
        }
        M menu = menuClass.cast(openMenu);
        if (!target.pos().equals(menu.getMenuConfigTargetPos())
                || !Objects.equals(target.subLevelId(), menu.getMenuConfigTargetSubLevelId())) {
            return null;
        }
        B menuTarget = menu.getMenuConfigTargetBlockEntity();
        return blockEntityClass.isInstance(menuTarget)
                ? blockEntityClass.cast(menuTarget) : null;
    }

    // Resolve the menu backed block entity
    public static <B extends BlockEntity, M extends AbstractContainerMenu & MenuBackedBlockEntityTarget<B>> B resolve(
            IPayloadContext context,
            MenuConfigTarget target,
            Class<M> menuClass,
            Class<B> blockEntityClass) {
        B menuTarget = resolveOpenMenu(context, target, menuClass, blockEntityClass);
        if (menuTarget != null) {
            return menuTarget;
        }
        if (context == null || context.player() == null
                || target == null || target.subLevelId() != null) {
            return null;
        }

        BlockEntity directTarget = context.player().level().getBlockEntity(target.pos());
        if (blockEntityClass.isInstance(directTarget)) {
            return blockEntityClass.cast(directTarget);
        }
        return null;
    }
}
