package com.mediconnect.notification;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runnable unit of work consumed by each dispatcher worker thread.
 *
 * <p>Encapsulates one notification delivery attempt with exponential-backoff retry.
 *
 * <h2>Retry strategy</h2>
 * <pre>
 *   Attempt 1: send immediately
 *   Attempt 2: sleep base * 2^0 ± jitter  →  ~200ms
 *   Attempt 3: sleep base * 2^1 ± jitter  →  ~400ms
 *   After 3 failures: mark FAILED, increment counter
 * </pre>
 *
 * <h2>Thread safety</h2>
 * All fields are either {@code final} or {@code local} to {@link #run()}.
 * No shared mutable state — this object is created on the producer thread
 * and consumed by exactly one worker thread: no concurrent access possible.
 *
 * <p>The only "external" shared objects are the {@link AtomicLong} counters
 * passed in by the dispatcher — they use CAS operations (no locks) and are
 * safe for concurrent increment from any thread.
 */
@Slf4j
public class NotificationTask implements Runnable {

    // ── Immutable dependencies ────────────────────────────────────────────────
    private final AppointmentNotificationEvent event;
    private final MockNotificationClient       client;

    // ── Shared counters (AtomicLong — lock-free CAS) ──────────────────────────
    private final AtomicLong processedCount;
    private final AtomicLong failedCount;
    private final AtomicLong totalProcessingTimeNanos;

    // ── Micrometer timer (thread-safe by design) ──────────────────────────────
    private final Timer processingTimer;

    // ── Retry config (immutable after construction) ───────────────────────────
    private final int    maxAttempts;
    private final long   baseDelayMs;
    private final double jitterFactor;

    public NotificationTask(
            AppointmentNotificationEvent event,
            MockNotificationClient       client,
            AtomicLong processedCount,
            AtomicLong failedCount,
            AtomicLong totalProcessingTimeNanos,
            Timer      processingTimer,
            int        maxAttempts,
            long       baseDelayMs,
            double     jitterFactor) {
        this.event                  = event;
        this.client                 = client;
        this.processedCount         = processedCount;
        this.failedCount            = failedCount;
        this.totalProcessingTimeNanos = totalProcessingTimeNanos;
        this.processingTimer        = processingTimer;
        this.maxAttempts            = maxAttempts;
        this.baseDelayMs            = baseDelayMs;
        this.jitterFactor           = jitterFactor;
    }

    /**
     * Execute the notification send with retry + exponential backoff.
     *
     * <p>All retry state ({@code attempt}, {@code lastException}) is <em>local</em>
     * to this method's stack frame — not shared with any other thread.
     */
    @Override
    public void run() {
        long wallStart = System.nanoTime();
        Exception lastException = null;
        boolean sent = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // ── Backoff sleep before retries (not before the first attempt) ──
            if (attempt > 1) {
                long backoff = computeBackoffMs(attempt - 1);
                log.debug("[NotificationTask] Retry {}/{} for {} after {}ms backoff",
                        attempt, maxAttempts, event.getAppointmentId(), backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[NotificationTask] Worker interrupted during backoff — aborting {}", event);
                    break;
                }
            }

            try {
                client.send(event);
                sent = true;
                log.debug("[NotificationTask] Sent {} on attempt {}/{} — appt={}",
                        event.getEventType(), attempt, maxAttempts, event.getAppointmentId());
                break; // success — exit retry loop

            } catch (MockNotificationClient.NotificationSendException e) {
                lastException = e;
                log.warn("[NotificationTask] Attempt {}/{} failed for {}: {}",
                        attempt, maxAttempts, event.getAppointmentId(), e.getMessage());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[NotificationTask] Worker interrupted during send — aborting {}", event);
                break;
            }
        }

        // ── Record wall time ──────────────────────────────────────────────────
        long elapsedNanos = System.nanoTime() - wallStart;
        totalProcessingTimeNanos.addAndGet(elapsedNanos);

        // Record in Micrometer timer (thread-safe — uses CAS internally)
        processingTimer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        // ── Update outcome counters ───────────────────────────────────────────
        if (sent) {
            processedCount.incrementAndGet();
        } else {
            failedCount.incrementAndGet();
            log.error("[NotificationTask] FAILED after {} attempts — appt={}, lastError={}",
                    maxAttempts, event.getAppointmentId(),
                    lastException != null ? lastException.getMessage() : "interrupted");
        }
    }

    /**
     * Exponential backoff with jitter.
     *
     * <pre>
     *   delay = baseDelayMs × 2^(retryNumber-1)   (e.g. 200ms, 400ms, 800ms)
     *   jitter = ±(jitterFactor × delay)           (e.g. ±20% of delay)
     * </pre>
     *
     * <p>Jitter uses {@link ThreadLocalRandom} — thread-confined, no contention.
     *
     * @param retryNumber 1-based retry count (first retry = 1)
     * @return delay in ms (always ≥ 0)
     */
    private long computeBackoffMs(int retryNumber) {
        long base    = baseDelayMs * (1L << (retryNumber - 1)); // 200, 400, 800, …
        // Jitter: scale by (random in [-1, +1]) × jitterFactor × base
        double jitter = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterFactor * base;
        return Math.max(0L, base + (long) jitter);
    }
}
