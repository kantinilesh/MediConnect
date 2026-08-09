package com.mediconnect.dispatcher;

import com.mediconnect.config.NotificationProperties;
import com.mediconnect.notification.AppointmentNotificationEvent;
import com.mediconnect.notification.MockNotificationClient;
import com.mediconnect.notification.NotificationTask;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent notification dispatcher backed by a bounded {@link LinkedBlockingQueue}
 * and a fixed-size {@link ExecutorService} thread pool.
 *
 * <h2>Queue choice — LinkedBlockingQueue vs ArrayBlockingQueue</h2>
 * <p>{@link LinkedBlockingQueue} uses <em>two separate locks</em>:
 * {@code putLock} (producer-side) and {@code takeLock} (consumer-side).
 * {@link java.util.concurrent.ArrayBlockingQueue} uses a <em>single lock</em>
 * for both operations, so every HTTP request thread posting an event and every
 * worker thread dequeuing a task compete for the same mutex.
 *
 * <p>Under the load profile of this dispatcher — N HTTP threads producing concurrently
 * and {@link NotificationProperties#getThreadPoolSize()} threads consuming — the
 * two-lock design eliminates producer/consumer lock contention entirely, giving
 * measurably higher throughput at ≥500 events/min.
 *
 * <p>Capacity ({@value #QUEUE_CAPACITY_COMMENT}) is set via config.
 * At 500/min ≈ 8.3/sec, with 8 threads draining ~66/sec (120ms avg per notification),
 * the buffer absorbs ~15 seconds of burst load before backpressure kicks in.
 *
 * <h2>Backpressure — offer(timeout) then drop</h2>
 * <p>Three options considered:
 * <ol>
 *   <li>{@code put()} — blocks the HTTP request thread; unacceptable (risks Tomcat thread starvation).</li>
 *   <li>{@code offer()} — immediate reject; too aggressive for transient bursts.</li>
 *   <li>{@code offer(timeout)} — chosen. Gives 200ms grace before dropping.
 *       200ms stays within HTTP response-time budgets while shedding load gracefully.
 *       Drops are logged as WARN and counted in {@code mediconnect.notifications.dropped.total}.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <table border="1">
 *   <tr><th>Shared state</th><th>Protection</th></tr>
 *   <tr><td>{@link #taskQueue}</td><td>LinkedBlockingQueue's own putLock/takeLock — no external sync needed</td></tr>
 *   <tr><td>{@link #processedCount}, {@link #failedCount}, {@link #droppedCount}</td>
 *       <td>AtomicLong — lock-free CAS operations</td></tr>
 *   <tr><td>{@link #totalProcessingTimeNanos}</td><td>AtomicLong.addAndGet() — lock-free</td></tr>
 *   <tr><td>{@link #shuttingDown}</td><td>AtomicBoolean — safe read/write from any thread</td></tr>
 *   <tr><td>{@link #workerPool}</td><td>Immutable reference after construction; ExecutorService is thread-safe</td></tr>
 *   <tr><td>{@link MockNotificationClient}</td><td>Stateless; uses ThreadLocalRandom internally</td></tr>
 * </table>
 * <p><strong>No {@code synchronized} blocks are needed anywhere in this class.</strong>
 *
 * <h2>Graceful shutdown</h2>
 * <ol>
 *   <li>Set {@link #shuttingDown} → new events are rejected immediately.</li>
 *   <li>{@code workerPool.shutdown()} → no new tasks accepted; in-flight tasks continue.</li>
 *   <li>Worker loops drain remaining tasks from the queue.</li>
 *   <li>{@code awaitTermination(shutdownTimeoutSeconds)} → wait for clean finish.</li>
 *   <li>If timeout exceeded: {@code shutdownNow()} → interrupt remaining workers.</li>
 * </ol>
 */
@Component
@Slf4j
public class NotificationDispatcher {

    private static final String QUEUE_CAPACITY_COMMENT = "see mediconnect.notification.queue-capacity";

    // ── Core infrastructure ───────────────────────────────────────────────────

    /**
     * The work queue.
     *
     * <p><b>LinkedBlockingQueue chosen over ArrayBlockingQueue</b>:
     * two-lock design lets producers ({@code offer}) and consumers ({@code poll})
     * run concurrently without contention — critical when many HTTP threads and
     * worker threads access the queue simultaneously.
     */
    private final LinkedBlockingQueue<NotificationTask> taskQueue;

    /**
     * Fixed-size thread pool whose threads loop on {@link #taskQueue}.
     *
     * <p>Threads are NOT daemon threads so they finish in-flight work on JVM shutdown.
     * Named {@code notif-worker-N} for easy identification in thread dumps.
     */
    private final ExecutorService workerPool;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final MockNotificationClient  client;
    private final NotificationProperties  props;

    // ── Shared counters — AtomicLong (CAS, lock-free) ─────────────────────────

    /** Total notifications successfully sent (including after retries). */
    final AtomicLong processedCount         = new AtomicLong(0);

    /** Total notifications that exhausted all retry attempts without success. */
    final AtomicLong failedCount            = new AtomicLong(0);

    /** Total notifications dropped due to queue-full backpressure. */
    final AtomicLong droppedCount           = new AtomicLong(0);

    /** Cumulative wall-time spent in NotificationTask.run() (nanoseconds). */
    final AtomicLong totalProcessingTimeNanos = new AtomicLong(0);

    /** True once {@link #shutdown()} is called; producers see this and drop immediately. */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // ── Micrometer ────────────────────────────────────────────────────────────
    private final Timer processingTimer;

    // ── Thread naming ─────────────────────────────────────────────────────────
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    // ── Config ────────────────────────────────────────────────────────────────
    private final long   offerTimeoutMs;
    private final int    shutdownTimeoutSeconds;
    private final int    maxAttempts;
    private final long   baseDelayMs;
    private final double jitterFactor;

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    public NotificationDispatcher(
            MockNotificationClient client,
            NotificationProperties props,
            MeterRegistry          registry) {

        this.client                 = client;
        this.props                  = props;
        this.offerTimeoutMs         = props.getOfferTimeoutMs();
        this.shutdownTimeoutSeconds = props.getShutdownTimeoutSeconds();
        this.maxAttempts            = props.getRetry().getMaxAttempts();
        this.baseDelayMs            = props.getRetry().getBaseDelayMs();
        this.jitterFactor           = props.getRetry().getJitterFactor();

        // ── Bounded queue (LinkedBlockingQueue — two-lock design) ─────────────
        this.taskQueue = new LinkedBlockingQueue<>(props.getQueueCapacity());

        // ── Fixed thread pool — non-daemon, named workers ─────────────────────
        this.workerPool = Executors.newFixedThreadPool(
                props.getThreadPoolSize(),
                r -> {
                    Thread t = new Thread(r, "notif-worker-" + THREAD_COUNTER.incrementAndGet());
                    t.setDaemon(false); // participate in graceful shutdown
                    return t;
                });

        // ── Start worker threads ───────────────────────────────────────────────
        for (int i = 0; i < props.getThreadPoolSize(); i++) {
            workerPool.submit(this::workerLoop);
        }

        // ── Micrometer Timer ──────────────────────────────────────────────────
        this.processingTimer = Timer.builder("mediconnect.notifications.processing.time")
                .description("Wall-clock time per notification task (including retries)")
                .register(registry);

        // ── Micrometer Gauges (current value, read at scrape time) ────────────

        // Queue depth — reads taskQueue.size() which is O(1) in LinkedBlockingQueue
        // (maintained as a separate AtomicInteger internally — thread-safe)
        Gauge.builder("mediconnect.notifications.queue.depth",
                        taskQueue, LinkedBlockingQueue::size)
                .description("Current number of pending notification tasks in the queue")
                .register(registry);

        // Bind AtomicLong counters as gauges so Prometheus can scrape running totals
        Gauge.builder("mediconnect.notifications.processed.total",
                        processedCount, AtomicLong::doubleValue)
                .description("Total notifications successfully sent since startup")
                .register(registry);

        Gauge.builder("mediconnect.notifications.failed.total",
                        failedCount, AtomicLong::doubleValue)
                .description("Total notifications that exhausted all retries")
                .register(registry);

        Gauge.builder("mediconnect.notifications.dropped.total",
                        droppedCount, AtomicLong::doubleValue)
                .description("Total notifications dropped due to full queue (backpressure)")
                .register(registry);

        log.info("[NotificationDispatcher] Initialised — queue={}, threads={}, failureRate={}",
                props.getQueueCapacity(), props.getThreadPoolSize(),
                props.getMockClient().getFailureRate());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event listener — producer side
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Receives appointment lifecycle events published by the service layer.
     *
     * <p>Uses {@code @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)}:
     * <ul>
     *   <li>When called from within a {@code @Transactional} method, fires only after the
     *       DB transaction commits — so we never send a notification for a booking that
     *       rolled back.</li>
     *   <li>{@code fallbackExecution = true} allows the listener to fire even when called
     *       outside a transaction (e.g. from load-test harness or unit tests).</li>
     * </ul>
     *
     * <p>This method returns almost immediately — it just calls {@code queue.offer()} with
     * a 200ms timeout and returns. The actual send happens asynchronously on a worker thread.
     *
     * <p><b>Backpressure</b>: if the queue is still full after 200ms, the event is dropped
     * and logged as WARN. The caller's thread is never blocked for more than 200ms.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAppointmentEvent(AppointmentNotificationEvent event) {
        if (shuttingDown.get()) {
            log.warn("[NotificationDispatcher] Shutting down — dropping event: {}", event);
            droppedCount.incrementAndGet();
            return;
        }

        NotificationTask task = new NotificationTask(
                event,
                client,
                processedCount,
                failedCount,
                totalProcessingTimeNanos,
                processingTimer,
                maxAttempts,
                baseDelayMs,
                jitterFactor);

        try {
            boolean accepted = taskQueue.offer(task, offerTimeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                // Queue was full even after waiting — apply backpressure by dropping
                droppedCount.incrementAndGet();
                log.warn("[NotificationDispatcher] Queue full after {}ms — dropping {} (queue={})",
                        offerTimeoutMs, event, taskQueue.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedCount.incrementAndGet();
            log.warn("[NotificationDispatcher] Interrupted while enqueuing — dropping {}", event);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Worker loop — consumer side
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Long-lived runnable executed by each worker thread.
     *
     * <p>Uses {@code poll(100ms)} instead of {@code take()} so that the shutdown
     * condition ({@link #shuttingDown} + empty queue) is checked regularly.
     * {@code take()} would block forever if no tasks arrive, preventing clean shutdown.
     *
     * <p>Loop exit conditions:
     * <ol>
     *   <li>Thread is interrupted ({@code InterruptedException} in poll) — stops immediately.</li>
     *   <li>{@code shuttingDown == true} AND queue is empty — drains fully then exits.</li>
     * </ol>
     */
    private void workerLoop() {
        String threadName = Thread.currentThread().getName();
        log.debug("[{}] Worker started", threadName);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // poll with timeout so we can re-check shutdown condition
                NotificationTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);

                if (task != null) {
                    task.run(); // retry + backoff logic lives in NotificationTask

                } else if (shuttingDown.get()) {
                    // Queue empty AND shutting down → done
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("[{}] Worker interrupted — exiting", threadName);
                break;
            }
        }

        log.debug("[{}] Worker exiting (processed={} failed={} queued={})",
                threadName, processedCount.get(), failedCount.get(), taskQueue.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graceful shutdown
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by Spring on application shutdown ({@link PreDestroy}).
     *
     * <ol>
     *   <li>Set {@link #shuttingDown} — incoming events are immediately dropped.</li>
     *   <li>{@code workerPool.shutdown()} — no new tasks submitted; active workers continue.</li>
     *   <li>Workers detect shutdown via poll-loop condition and drain the queue.</li>
     *   <li>{@code awaitTermination(N)} — waits for graceful finish.</li>
     *   <li>If timeout exceeded: {@code shutdownNow()} — interrupts remaining threads.</li>
     * </ol>
     */
    @PreDestroy
    public void shutdown() {
        log.info("[NotificationDispatcher] Initiating graceful shutdown — queue={} remaining",
                taskQueue.size());

        // 1. Signal producers to stop enqueuing
        shuttingDown.set(true);

        // 2. Tell executor to drain and stop — no new tasks will be submitted
        workerPool.shutdown();

        // 3. Wait for in-flight + queued tasks to finish
        try {
            boolean cleanShutdown = workerPool.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
            if (!cleanShutdown) {
                log.warn("[NotificationDispatcher] Shutdown timeout ({}s) exceeded — forcing shutdownNow()",
                        shutdownTimeoutSeconds);
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }

        // Final metrics snapshot
        log.info("[NotificationDispatcher] Shutdown complete — processed={} failed={} dropped={} avgTimeMs={}",
                processedCount.get(),
                failedCount.get(),
                droppedCount.get(),
                avgProcessingTimeMs());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics accessors (used by load-test harness and actuator)
    // ─────────────────────────────────────────────────────────────────────────

    public long getProcessedCount()          { return processedCount.get(); }
    public long getFailedCount()             { return failedCount.get(); }
    public long getDroppedCount()            { return droppedCount.get(); }
    public int  getQueueDepth()              { return taskQueue.size(); }
    public long getTotalCompletedCount()     { return processedCount.get() + failedCount.get(); }

    /**
     * Average processing time per task in milliseconds.
     * Division-by-zero safe — returns 0 when no tasks have been processed yet.
     */
    public double avgProcessingTimeMs() {
        long total = processedCount.get() + failedCount.get();
        return total == 0 ? 0.0 : totalProcessingTimeNanos.get() / 1_000_000.0 / total;
    }
}
