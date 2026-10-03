package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerTransportLocksTest{
    // An abandoned route expires, while an active order can renew and release it
    @Test void leasesTransportToOneOrder(){
        Level level = mock(Level.class);
        BlockPos gate = new BlockPos(3, 64, 4);
        AtomicLong time = new AtomicLong();
        when(level.getGameTime()).thenAnswer(call -> time.get());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(level, gate)).thenReturn(true);
            assertTrue(WorkerTransportLocks.acquire(level, first, List.of(gate)));
            assertTrue(WorkerTransportLocks.isLocked(level, gate));
            assertFalse(WorkerTransportLocks.acquire(level, second, List.of(gate)));
            time.set(30L);
            assertTrue(WorkerTransportLocks.acquire(level, first, List.of(gate)));
            time.set(45L);
            assertTrue(WorkerTransportLocks.isLocked(level, gate));
            WorkerTransportLocks.releaseOwner(first);
            assertFalse(WorkerTransportLocks.isLocked(level, gate));
            assertTrue(WorkerTransportLocks.acquire(level, second, List.of(gate)));
            time.set(86L);
            assertFalse(WorkerTransportLocks.isLocked(level, gate));
        }
    }
}
