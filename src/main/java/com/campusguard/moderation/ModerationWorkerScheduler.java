package com.campusguard.moderation;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Wakes a bounded background worker; database claims and periodic recovery preserve durability. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "campusguard.moderation.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ModerationWorkerScheduler {
    private static final Logger log = LoggerFactory.getLogger(ModerationWorkerScheduler.class);
    private final ModerationWorker worker;
    private final ModerationProperties properties;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
            task -> {
                Thread thread = new Thread(task, "moderation-dispatch");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public ModerationWorkerScheduler(ModerationWorker worker, ModerationProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reportCommitted(ModerationQueueWakeup ignored) {
        poll();
    }

    /**
     * Periodic recovery also finds work whose process died before delivering the
     * commit hint. Deployments may increase the interval when the database sleeps
     * while idle; newly committed reports still wake the worker immediately.
     */
    @Scheduled(
            fixedDelayString = "${campusguard.moderation.poll-interval:2s}",
            initialDelayString = "${campusguard.moderation.poll-interval:2s}")
    public void poll() {
        // At most one active drain and one pending hint, regardless of report
        // volume. Dropping extra hints cannot drop the persisted case rows.
        executor.execute(() -> {
            try {
                while (!executor.isShutdown() && worker.runOnce() == properties.batchSize()) {
                    // Drain full batches so recovery does not require another interval.
                }
            } catch (RuntimeException ex) {
                log.error("Moderation dispatch failed; persisted work will be retried.", ex);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
