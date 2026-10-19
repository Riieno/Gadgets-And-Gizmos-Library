package com.rieno.gadgetsandgizmos.lib.worker;

// Report whether a worker processor still has active work before its output may be collected
public interface WorkerProcessingProbe {
    // Check whether the processor is still working on the worker's delivered input
    boolean workerProcessingActive();
}
