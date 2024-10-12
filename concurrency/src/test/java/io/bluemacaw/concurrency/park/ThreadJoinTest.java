package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class ThreadJoinTest {
    @Test
    public void mainTest() throws Exception {
        Thread t = new Thread(() -> {
            log.info("Thread t begin ... ");

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            log.info("Thread t end");
        });

        long start = System.currentTimeMillis();
        t.start();
        t.join();

        log.info("执行时间: {}", (System.currentTimeMillis() - start));
        log.info("Main end");
    }
}
