package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Map;

// Adapt independent RCS nozzles for assembly control
public interface RcsThrusterControl{
    BlockPos getBlockPos();
    Map<String, Object> position();
    String getCcId();
    void setCcId(String id);
    String getAssemblyComputerCraftAlias();
    void setAssemblyComputerCraftAlias(String alias);
    float getSpeed();
    double getMaxNozzleThrust();
    float getThrottle(Direction nozzle);
    double getNozzleThrust(Direction nozzle);
    boolean isNozzleActive(Direction nozzle);
    void setComputerThrottle(Direction nozzle, float throttle);
    void clearComputerThrottle(Direction nozzle);
}
