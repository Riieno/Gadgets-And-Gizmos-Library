package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceLeaseTest{
    @Test
    void expiryDependsOnTimeAndRenewalRatherThanFrameRate(){
        ResourceLease lease = new ResourceLease(5000);
        assertTrue(lease.isExpired(10));
        lease.renew(10);
        for(int frame = 0; frame < 1000; frame++) assertFalse(lease.isExpired(100));
        assertFalse(lease.isExpired(5009));
        assertTrue(lease.isExpired(5010));
        lease.renew(5000);
        assertFalse(lease.isExpired(5010));
    }

    @Test
    void monotonicClockOverflowRetainsTheElapsedDuration(){
        ResourceLease lease = new ResourceLease(10);
        lease.renew(Long.MAX_VALUE - 5);
        assertFalse(lease.isExpired(Long.MIN_VALUE + 3));
        assertTrue(lease.isExpired(Long.MIN_VALUE + 4));
        assertThrows(IllegalArgumentException.class, () -> new ResourceLease(-1));
    }
}
