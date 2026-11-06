package com.rieno.gadgetsandgizmos.lib.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

// Keep custom vault removal and wrench splitting on Create's typed connectivity path
public class ConnectedVaultBlock extends ItemVaultBlock{
    // Initialize a block paired with a ConnectedVaultBlockEntity subtype
    public ConnectedVaultBlock(BlockBehaviour.Properties props){
        super(props);
    }

    // Split custom members before the base wrench handler rotates the block
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext ctx){
        if(ctx.getClickedFace().getAxis().isVertical()
                && ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ConnectedVaultBlockEntity vault){
            ConnectivityHandler.splitMulti(vault);
        }
        return super.onWrenched(state, ctx);
    }

    // Drop only the removed member's items and rebuild the surviving controller groups
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving){
        if(state.is(next.getBlock()) && next.hasBlockEntity()) return;
        if(!(level.getBlockEntity(pos) instanceof ConnectedVaultBlockEntity vault)){
            super.onRemove(state, level, pos, next, moving);
            return;
        }
        ItemHelper.dropContents(level, pos, vault.getInventoryOfBlock());
        level.removeBlockEntity(pos);
        ConnectivityHandler.splitMulti(vault);
    }
}
