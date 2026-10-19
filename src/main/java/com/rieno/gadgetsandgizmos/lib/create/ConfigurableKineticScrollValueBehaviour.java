package com.rieno.gadgetsandgizmos.lib.create;

import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

// Keep Create's two direction rows while respecting a configurable speed range
public final class ConfigurableKineticScrollValueBehaviour extends KineticScrollValueBehaviour{
    public ConfigurableKineticScrollValueBehaviour(Component label, SmartBlockEntity blockEntity,
                                                    ValueBoxTransform slot){
        super(label, blockEntity, slot);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult){
        ValueSettingsBoard board = super.createBoard(player, hitResult);
        return new ValueSettingsBoard(board.title(), max, Math.max(1, max / 8), board.rows(), board.formatter());
    }
}
