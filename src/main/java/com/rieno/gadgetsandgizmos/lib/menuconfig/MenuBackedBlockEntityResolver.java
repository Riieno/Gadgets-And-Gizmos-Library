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

    // Resolve the menu backed block entity resolver
    public static <B extends BlockEntity, M extends AbstractContainerMenu & MenuBackedBlockEntityTarget<B>> B resolve(
            IPayloadContext context,
            MenuConfigTarget target,
            Class<M> menuClass,
            Class<B> blockEntityClass) {
        AbstractContainerMenu openMenu = context.player().containerMenu;
        if (menuClass.isInstance(openMenu)) {
            M menu = menuClass.cast(openMenu);

            if (target.pos().equals(menu.getMenuConfigTargetPos())
                    && Objects.equals(target.subLevelId(), menu.getMenuConfigTargetSubLevelId())) {
                B menuTarget = menu.getMenuConfigTargetBlockEntity();
                if (blockEntityClass.isInstance(menuTarget)) {
                    return blockEntityClass.cast(menuTarget);
                }
            }
        }

        BlockEntity directTarget = context.player().level().getBlockEntity(target.pos());
        if (blockEntityClass.isInstance(directTarget)) {
            return blockEntityClass.cast(directTarget);
        }
        return null;
    }
}
