package org.example;




import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterTest {

    @Test
    public void acquireSingleThread() {

        RateLimiter rateLimiter = new RateLimiter(100, 1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        long l1 = System.currentTimeMillis();

        for (int j=0; j<110; j++) {
            if (rateLimiter.acquire()) {
                successCount.incrementAndGet();
            } else {
                failedCount.incrementAndGet();
            }
        }

        long l2 = System.currentTimeMillis();

        Assertions.assertTrue(l2 - l1 < 1000);
        Assertions.assertEquals(100, successCount.get());
        Assertions.assertEquals(10, failedCount.get());

    }

    @Test
    public void acquireMultiThreads() throws InterruptedException {

        RateLimiter rateLimiter = new RateLimiter(100, 1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        long l1 = System.currentTimeMillis();

        for(int i=0; i < 5; i++) {
            Thread t = new Thread(() -> {
                for (int j=0; j<21; j++) {
                    if (rateLimiter.acquire()) {
                        successCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                    }
                }

            });
            t.start();
            t.join();
        }

        long l2 = System.currentTimeMillis();

        Assertions.assertTrue(l2 - l1 < 1000);
        Assertions.assertEquals(100, successCount.get());
        Assertions.assertEquals(5, failedCount.get());

    }
}