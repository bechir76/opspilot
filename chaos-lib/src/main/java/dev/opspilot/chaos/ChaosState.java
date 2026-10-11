package dev.opspilot.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe holder of the faults currently injected into this JVM.
 * <p>
 * Request-level faults (latency, error rate) are plain volatile fields read on every request.
 * Background faults (memory leak, CPU burn, crash) run on daemon threads owned by this class.
 */
public class ChaosState implements AutoCloseable {

    public static final long MAX_LATENCY_MS = 30_000;
    public static final int MAX_LEAK_MB_PER_SECOND = 1024;
    public static final int MAX_CPU_SECONDS = 600;
    public static final long CRASH_DELAY_MS = 500;
    public static final int CRASH_EXIT_CODE = 1;

    private static final Logger log = LoggerFactory.getLogger(ChaosState.class);
    private static final int MB = 1024 * 1024;
    private static final int PAGE_SIZE = 4096;

    private final IntConsumer exitAction;
    private final ScheduledExecutorService scheduler;

    private volatile long latencyMs;
    private volatile double errorRate;

    // Guarded by "this": everything below is only mutated inside synchronized methods.
    private final List<byte[]> leaked = new ArrayList<>();
    private final AtomicLong leakedBytes = new AtomicLong();
    private volatile int leakMbPerSecond;
    private ScheduledFuture<?> leakTask;

    private final AtomicInteger cpuBurnGeneration = new AtomicInteger();
    private volatile long cpuBurnDeadlineNanos;
    private volatile long cpuSink;

    /** Production constructor: a crash halts the JVM without running shutdown hooks, like a real crash. */
    public ChaosState() {
        this(status -> Runtime.getRuntime().halt(status));
    }

    /** @param exitAction called with the exit code when a crash is triggered (injectable for tests). */
    public ChaosState(IntConsumer exitAction) {
        this.exitAction = exitAction;
        this.scheduler = Executors.newScheduledThreadPool(2, daemonThreads("chaos-scheduler"));
    }

    public void setLatency(long ms) {
        if (ms < 0 || ms > MAX_LATENCY_MS) {
            throw new IllegalArgumentException("latency must be between 0 and " + MAX_LATENCY_MS + " ms, got " + ms);
        }
        latencyMs = ms;
        log.warn("Chaos: latency set to {} ms", ms);
    }

    public void setErrorRate(double rate) {
        if (Double.isNaN(rate) || rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("error rate must be between 0.0 and 1.0, got " + rate);
        }
        errorRate = rate;
        log.warn("Chaos: error rate set to {}", rate);
    }

    /** Starts (or changes the rate of) a memory leak. Already leaked memory is kept. */
    public synchronized void startMemoryLeak(int mbPerSecond) {
        if (mbPerSecond < 1 || mbPerSecond > MAX_LEAK_MB_PER_SECOND) {
            throw new IllegalArgumentException(
                    "memory leak must be between 1 and " + MAX_LEAK_MB_PER_SECOND + " MB/s, got " + mbPerSecond);
        }
        cancelLeakTask();
        leakMbPerSecond = mbPerSecond;
        leakTask = scheduler.scheduleAtFixedRate(() -> leak(mbPerSecond), 0, 1, TimeUnit.SECONDS);
        log.warn("Chaos: memory leak started at {} MB/s", mbPerSecond);
    }

    /** Busy-loops on every available core for the given duration. Replaces any running burn. */
    public void burnCpu(int seconds) {
        if (seconds < 1 || seconds > MAX_CPU_SECONDS) {
            throw new IllegalArgumentException(
                    "cpu burn must be between 1 and " + MAX_CPU_SECONDS + " seconds, got " + seconds);
        }
        int generation = cpuBurnGeneration.incrementAndGet();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        cpuBurnDeadlineNanos = deadline;
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++) {
            Thread burner = new Thread(() -> burn(generation, deadline), "chaos-cpu-burn-" + i);
            burner.setDaemon(true);
            burner.start();
        }
        log.warn("Chaos: burning CPU on {} cores for {} s", cores, seconds);
    }

    /** Logs a fatal message and terminates the process after {@link #CRASH_DELAY_MS}. */
    public void crash() {
        log.error("FATAL: chaos crash requested, terminating process in {} ms with exit code {}",
                CRASH_DELAY_MS, CRASH_EXIT_CODE);
        scheduler.schedule(() -> exitAction.accept(CRASH_EXIT_CODE), CRASH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Clears every fault and releases leaked memory. */
    public synchronized void reset() {
        latencyMs = 0;
        errorRate = 0.0;
        cancelLeakTask();
        leakMbPerSecond = 0;
        leaked.clear();
        leakedBytes.set(0);
        cpuBurnGeneration.incrementAndGet();
        cpuBurnDeadlineNanos = 0;
        log.warn("Chaos: all faults reset");
    }

    public long latencyMs() {
        return latencyMs;
    }

    public double errorRate() {
        return errorRate;
    }

    public int memoryLeakMbPerSecond() {
        return leakMbPerSecond;
    }

    public long leakedBytes() {
        return leakedBytes.get();
    }

    public boolean cpuBurnActive() {
        return cpuBurnDeadlineNanos != 0 && System.nanoTime() < cpuBurnDeadlineNanos;
    }

    public ChaosStatus status() {
        return new ChaosStatus(latencyMs, errorRate, leakMbPerSecond, leakedBytes.get(), cpuBurnActive());
    }

    @Override
    public void close() {
        reset();
        scheduler.shutdownNow();
    }

    private synchronized void leak(int mb) {
        if (leakMbPerSecond == 0) {
            return; // a tick that was already waiting for the lock when reset() ran
        }
        for (int i = 0; i < mb; i++) {
            byte[] chunk = new byte[MB];
            // Write one byte per page so the OS really commits the memory (RSS grows), not just reserves it.
            for (int offset = 0; offset < chunk.length; offset += PAGE_SIZE) {
                chunk[offset] = 1;
            }
            leaked.add(chunk);
            leakedBytes.addAndGet(MB);
        }
    }

    private void cancelLeakTask() {
        if (leakTask != null) {
            leakTask.cancel(false);
            leakTask = null;
        }
    }

    private void burn(int generation, long deadline) {
        long x = 1;
        while (cpuBurnGeneration.get() == generation && System.nanoTime() < deadline) {
            for (int i = 0; i < 10_000; i++) {
                x = x * 6364136223846793005L + 1442695040888963407L;
            }
        }
        cpuSink = x; // keeps the JIT from eliminating the loop
    }

    private static java.util.concurrent.ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
