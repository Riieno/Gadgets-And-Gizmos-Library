package com.rieno.gadgetsandgizmos.lib.inventory;

// Expose gross input and output without inferring throughput from net stored contents
public interface TransferTrackedStorage{
    TransferMeter.Rates transferRates();
}
