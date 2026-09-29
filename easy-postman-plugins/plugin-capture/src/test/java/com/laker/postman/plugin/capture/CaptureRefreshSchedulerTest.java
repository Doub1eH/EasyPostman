package com.laker.postman.plugin.capture;

import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertTrue;

public class CaptureRefreshSchedulerTest {

    @Test
    public void shouldRefreshDuringContinuousUpdates() throws Exception {
        CountDownLatch refreshedTwice = new CountDownLatch(2);
        AtomicBoolean sending = new AtomicBoolean(true);
        CaptureRefreshScheduler scheduler = new CaptureRefreshScheduler(() -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            refreshedTwice.countDown();
        }, 40);
        Thread sender = new Thread(() -> {
            while (sending.get()) {
                scheduler.requestRefresh();
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "capture-refresh-test-sender");
        sender.start();
        try {
            assertTrue(refreshedTwice.await(1, TimeUnit.SECONDS),
                    "Continuous capture updates must not postpone table refresh indefinitely");
        } finally {
            sending.set(false);
            sender.join(1_000);
        }
    }
}
