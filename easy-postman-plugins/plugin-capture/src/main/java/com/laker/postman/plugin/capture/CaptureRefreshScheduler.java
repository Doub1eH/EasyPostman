package com.laker.postman.plugin.capture;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesces capture updates without postponing a refresh during continuous traffic. */
final class CaptureRefreshScheduler {
    private static final int REFRESH_INTERVAL_MS = 120;

    private final Runnable refreshAction;
    private final int intervalMs;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private Timer timer;

    CaptureRefreshScheduler(Runnable refreshAction) {
        this(refreshAction, REFRESH_INTERVAL_MS);
    }

    CaptureRefreshScheduler(Runnable refreshAction, int intervalMs) {
        this.refreshAction = refreshAction;
        this.intervalMs = intervalMs;
    }

    void requestRefresh() {
        if (!refreshPending.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (timer == null) {
                timer = new Timer(intervalMs, e -> {
                    refreshPending.set(false);
                    refreshAction.run();
                });
                timer.setRepeats(false);
            }
            timer.start();
        });
    }
}
