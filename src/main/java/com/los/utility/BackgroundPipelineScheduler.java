package com.los.utility;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages local background execution pipelines using ScheduledExecutorService.
 * Runs OverdueReviewMonitor and CoreBankingPushUtility in parallel background threads.
 */
public class BackgroundPipelineScheduler {

    private final ScheduledExecutorService scheduler;
    private final OverdueReviewMonitor overdueMonitor;
    private final CoreBankingPushUtility coreBankingPushUtility;
    private boolean running = false;

    public BackgroundPipelineScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "LOS-Background-Worker");
            t.setDaemon(true);
            return t;
        });
        this.overdueMonitor = new OverdueReviewMonitor();
        this.coreBankingPushUtility = new CoreBankingPushUtility();
    }

    /**
     * Starts the background pipelines.
     * Overdue Monitor runs every 10 minutes (initial delay 5 seconds).
     * Core Banking Push runs every 30 seconds (initial delay 10 seconds).
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        // Pipeline 1: Overdue Review Monitor
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                overdueMonitor.run();
            } catch (Exception e) {
                System.err.println("[BackgroundPipelineScheduler] Error in OverdueReviewMonitor: " + e.getMessage());
            }
        }, 5, 600, TimeUnit.SECONDS);

        // Pipeline 2: Core Banking Push Utility
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                coreBankingPushUtility.run();
            } catch (Exception e) {
                System.err.println("[BackgroundPipelineScheduler] Error in CoreBankingPushUtility: " + e.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);

        System.out.println("[BackgroundPipelineScheduler] Background executor pipelines started successfully.");
    }

    /**
     * Gracefully shuts down the background pipelines.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[BackgroundPipelineScheduler] Background executor pipelines stopped.");
    }

    public boolean isRunning() {
        return running;
    }
}
