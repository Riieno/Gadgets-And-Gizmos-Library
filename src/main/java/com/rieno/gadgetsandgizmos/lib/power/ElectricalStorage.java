package com.rieno.gadgetsandgizmos.lib.power;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

// Bind optional electrical converters to one shared energy store and physical attachment faces
public interface ElectricalStorage{
    LongEnergyStorage electricalStorage();
    List<? extends BlockEntity> electricalMembers();
    ConnectorOutputLimits connectorOutputLimits();
}
