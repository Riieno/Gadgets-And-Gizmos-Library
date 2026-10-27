package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource;
import com.rieno.gadgetsandgizmos.lib.probe.ContainerData;
import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// Add aggregate telemetry alongside ComputerCraft's native storage methods
public final class ContainerDataPeripheral implements GenericSource{
    private final String id;

    public ContainerDataPeripheral(ResourceLocation id){ this.id = id.toString(); }

    @Override public String id(){ return id; }

    // Read every resource type represented by this network entry
    @LuaFunction(mainThread = true)
    public Map<String, Object> getData(ContainerDataSource source){
        return ContainerData.capture(source).values();
    }

    // Group stored items by their display name
    @LuaFunction(mainThread = true)
    public Map<String, Long> getItems(ContainerDataSource source){
        return ContainerData.capture(source).items();
    }

    // Read the total number of stored items
    @LuaFunction(mainThread = true)
    public long getItemCount(ContainerDataSource source){
        return ContainerData.capture(source).itemCount();
    }

    // Read the total item capacity across the represented containers
    @LuaFunction(mainThread = true)
    public long getItemCapacity(ContainerDataSource source){
        return ContainerData.capture(source).itemCapacity();
    }
}
