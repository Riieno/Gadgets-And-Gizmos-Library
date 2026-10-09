package com.rieno.gadgetsandgizmos.lib.control;

// Synchronize the name of the controller currently commanding a provider
public interface ControlOwnerReceiver{
    void setControllerOwner(String channelId, String displayKey);
    void releaseControllerOwner(String channelId);
}
