package dev.opspilot.chaos;

/** Snapshot of the active faults, returned by {@code GET /chaos}. */
public record ChaosStatus(
        long latencyMs,
        double errorRate,
        int memoryLeakMbPerSecond,
        long leakedBytes,
        boolean cpuBurnActive) {
}
