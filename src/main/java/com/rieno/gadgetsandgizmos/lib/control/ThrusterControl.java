package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.core.BlockPos;

import java.util.Map;

// Adapt a thruster's native controls without exposing its implementation class
public interface ThrusterControl{
    BlockPos getBlockPos();
    Map<String, Object> position();
    String getCcId();
    void setCcId(String id);
    String getAssemblyComputerCraftAlias();
    void setAssemblyComputerCraftAlias(String alias);
    float getThrottle();
    float getComputerThrottle();
    void setThrottle(float throttle);
    void clearCcThrottleOverride();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    String getControlMode();
    void setControlMode(String mode);
    int getFuelAmount();
    int getFuelCapacity();
    String getFuelTypeId();
    double getEstimatedBurnSeconds();
    double getThrust();
    double getRealThrust();
    double getLiftCapacity();
    double getAirflow();
    boolean isActive();
    boolean isSoulThruster();
    void enableSoulThruster();
    void disableSoulThruster();
    int getSignalStrength();
}
