package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep concrete capability types intact after retiring global container guards
@SuppressWarnings("deprecation")
class ContainerAccessRegistryTest{
    // Preserve extra interfaces and object identity required by capability owners
    @Test void capabilityCompatibilityMethodsReturnTheOriginalObject(){
        IItemHandlerModifiable handler = mock(IItemHandlerModifiable.class);
        assertSame(handler, ContainerAccessRegistry.wrap(null, BlockPos.ZERO, handler));
        assertSame(handler, ContainerAccessRegistry.unwrap(handler));
        assertInstanceOf(IItemHandlerModifiable.class, ContainerAccessRegistry.wrap(null, BlockPos.ZERO, handler));
        assertNull(ContainerAccessRegistry.wrap(null, BlockPos.ZERO, null));
        assertNull(ContainerAccessRegistry.unwrap(null));
    }

    // Former policy registrations must not reintroduce removed access restrictions
    @Test void retiredPoliciesCannotChangeContainerAccess(){
        ContainerAccessRegistry.Provider provider = mock(ContainerAccessRegistry.Provider.class);
        ContainerAccessRegistry.register(provider);
        assertTrue(ContainerAccessRegistry.canOpen(null, null, BlockPos.ZERO));
        assertTrue(ContainerAccessRegistry.canInsert(null, BlockPos.ZERO, null));
        assertTrue(ContainerAccessRegistry.canExtract(null, BlockPos.ZERO, null));
        verifyNoInteractions(provider);
    }
}
