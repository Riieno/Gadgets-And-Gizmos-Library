package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import com.rieno.gadgetsandgizmos.lib.script.ScriptFileStorage;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Borrow a peripheral's actual cached mounts for one server operation
public final class ComputerCraftAttachedStorage implements IComputerAccess, AutoCloseable{
    private final IPeripheral peripheral;
    private final Map<String, ScriptFileStorage> mounts = new LinkedHashMap<>();
    public ComputerCraftAttachedStorage(IPeripheral peripheral){ this.peripheral = peripheral; peripheral.attach(this); }
    public Map<String, ScriptFileStorage> mounts(){ return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(mounts)); }
    @Override public String mount(String path, Mount mount, String drive){
        if(mounts.containsKey(path)) return null;
        mounts.put(path, new ComputerCraftMountStorage(mount)); return path;
    }
    @Override public String mountWritable(String path, WritableMount mount, String drive){ return mount(path, mount, drive); }
    @Override public void unmount(String path){ mounts.remove(path); }
    @Override public int getID(){ return -1; }
    @Override public void queueEvent(String event, Object... args){}
    @Override public String getAttachmentName(){ return "storage_browser"; }
    @Override public Map<String, IPeripheral> getAvailablePeripherals(){ return Map.of(); }
    @Override public IPeripheral getAvailablePeripheral(String name){ return null; }
    @Override public WorkMonitor getMainThreadMonitor(){
        return new WorkMonitor(){
            @Override public boolean canWork(){ return true; }
            @Override public boolean shouldWork(){ return true; }
            @Override public void trackWork(long time, TimeUnit unit){}
        };
    }
    @Override public void close(){ peripheral.detach(this); }
}
