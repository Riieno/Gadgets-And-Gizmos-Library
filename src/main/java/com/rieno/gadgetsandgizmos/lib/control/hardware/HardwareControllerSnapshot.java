package com.rieno.gadgetsandgizmos.lib.control.hardware;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Encode hardware snapshots without repeating device identities for every control
public final class HardwareControllerSnapshot{
    public static final int MAX_VALUES = 2048;
    private static final int MAX_DEVICES = 17;
    private static final List<String> INPUTS = HardwareControllerBindings.options().stream()
            .map(HardwareControllerBindings.BindingOption::id).toList();

    private HardwareControllerSnapshot(){}

    // Keep snapshots for every GLFW device within one serverbound payload
    public static void write(FriendlyByteBuf buffer, Map<String, Double> values){
        if(values.size() > MAX_VALUES) throw new IllegalArgumentException("Too many hardware inputs");
        List<String> devices = new ArrayList<>();
        for(String id : values.keySet()) if(!devices.contains(device(id))) devices.add(device(id));
        if(devices.size() > MAX_DEVICES) throw new IllegalArgumentException("Too many hardware devices");
        buffer.writeVarInt(devices.size());
        devices.forEach(id -> buffer.writeUtf(id, 48));
        buffer.writeVarInt(values.size());
        values.forEach((id, val) -> {
            String device = device(id);
            String input = device.isEmpty() ? id : HardwareControllerBindings.PREFIX + id.substring(id.lastIndexOf('/') + 1);
            int idx = INPUTS.indexOf(input);
            buffer.writeVarInt(devices.indexOf(device));
            buffer.writeVarInt(idx + 1);
            if(idx < 0) buffer.writeUtf(input, 64);
            buffer.writeDouble(val);
        });
    }

    // Rebuild stable automatic and device-scoped binding ids before publishing input
    public static Map<String, Double> read(FriendlyByteBuf buffer){
        int deviceCount = buffer.readVarInt();
        if(deviceCount < 0 || deviceCount > MAX_DEVICES) throw new IllegalArgumentException("Invalid hardware device count");
        List<String> devices = new ArrayList<>();
        for(int idx = 0; idx < deviceCount; idx++) devices.add(buffer.readUtf(48));
        int count = buffer.readVarInt();
        if(count < 0 || count > MAX_VALUES) throw new IllegalArgumentException("Invalid hardware input count");
        Map<String, Double> values = new LinkedHashMap<>();
        for(int idx = 0; idx < count; idx++){
            int device = buffer.readVarInt(), input = buffer.readVarInt();
            if(device < 0 || device >= devices.size() || input < 0 || input > INPUTS.size()) throw new IllegalArgumentException("Invalid hardware input index");
            String binding = input == 0 ? buffer.readUtf(64) : INPUTS.get(input - 1);
            values.put(HardwareControllerBindings.forDevice(binding, devices.get(device)), buffer.readDouble());
        }
        return values;
    }

    private static String device(String binding){
        int end = binding.lastIndexOf('/');
        return end < HardwareControllerBindings.PREFIX.length() ? "" : binding.substring(HardwareControllerBindings.PREFIX.length(), end);
    }
}
