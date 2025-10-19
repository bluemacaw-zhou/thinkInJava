package io.bluemacaw.concurrency.reorder;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/*
 * 伪共享
 */
@Slf4j
public class FalseSharingTest {
    @Test
    public void mainTest() throws Exception {
        Point point = new Point();

        long start = System.currentTimeMillis();
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100000000; i++) {
                point.increaseX();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100000000; i++) {
                point.increaseY();
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        // 线程安全 但是volatile的修饰拉低了性能表现
        log.info("{},{}", point.getX(), point.getY());
        log.info("{}", System.currentTimeMillis() - start);
    }
}
