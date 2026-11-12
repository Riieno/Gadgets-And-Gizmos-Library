package com.rieno.gadgetsandgizmos.lib.control.hardware;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

// Keep simultaneous controllers within the serverbound payload limit
class HardwareControllerSnapshotTest{
    @Test void everyControlOnSixteenDevicesAndAutomaticBindingsRoundTrips(){
        var values = new LinkedHashMap<String, Double>();
        for(var option : HardwareControllerBindings.options()){
            values.put(option.id(), .5);
            for(int slot = 0; slot < 16; slot++) values.put(HardwareControllerBindings.forDevice(option.id(), "0123456789abcdef0123456789abcdef/" + slot), .75);
        }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try{
            HardwareControllerSnapshot.write(buffer, values);
            assertTrue(buffer.readableBytes() < 30000);
            assertEquals(values, HardwareControllerSnapshot.read(buffer));
            assertFalse(buffer.isReadable());
        }finally{ buffer.release(); }
    }
}
