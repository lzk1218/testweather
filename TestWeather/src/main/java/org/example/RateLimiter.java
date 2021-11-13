package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private int qps;

    private int window;

    private AtomicInteger accumulated = new AtomicInteger();

    private volatile long lastMillis = 0;

    public RateLimiter(int qps, int window) {
        this.qps = qps;
        this.window = window;
        accumulated = new AtomicInteger();
    }

    public boolean acquire() {

        long l = System.currentTimeMillis();


        if (l - lastMillis > 1000 * window ) {
            synchronized (this) {
                if (l - lastMillis > 1000) {

                    lastMillis = l;
                    accumulated = new AtomicInteger(qps * window);
                }
            }
        }

        return accumulated.decrementAndGet() >= 0;
    }
}
