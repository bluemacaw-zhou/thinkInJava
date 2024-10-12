package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class WaitNotifyTest {
    private static final Object lock = new Object();
    private static volatile boolean flag = true;

    /*
     * notify不能指定唤醒那个线程 多半是notifyAll
     * park, unpark能指定唤醒的线程
     */
    @Test
    public void mainTest() throws Exception {
        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                while (flag) {
                    try {
                        log.info("wait start ...");
                        lock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                log.info("wait end ....... ");
            }
        });

        Thread t2 = new Thread(() -> {
            if (flag) {
                synchronized (lock) {
                    if (flag) {
                        try {
                            Thread.sleep(2000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        lock.notifyAll();
                        log.info("notify ... ");
                        flag = false;
                        log.info("change flag ... ");
                    }
                }
            }
        });

        t1.start();
        t2.start();

        t2.join();
        t1.join();
    }
}
