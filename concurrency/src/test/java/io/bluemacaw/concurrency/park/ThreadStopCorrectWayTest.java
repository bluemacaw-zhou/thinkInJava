package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class ThreadStopCorrectWayTest {
    @Test
    public void mainTest() {
        Runnable runnable = () -> {
            int count = 0;

            while (!Thread.currentThread().isInterrupted() && count < 1000) {
                log.info("count = {}", count++);

                try {
                    Thread.sleep(100); // 会被外部的interrupt方法中断
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }

            log.info("stop thread");
        };

        Thread t = new Thread(runnable);
        t.start();

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        t.interrupt();
    }
}
