package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class ThreadStopErrorWayTest {
    private static final Object lock = new Object();

    /*
     * 等待2秒后 获取锁 之前的线程没有执行完 类似于强制关机的概念
     */
    @Test
    public void mainTest() {
        Thread t1 = new Thread(() -> {
            String threadName = Thread.currentThread().getName();
            synchronized (lock) {
                log.info("{} get lock ... ", threadName);
                try {
                    Thread.sleep(60000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            log.info("{} end", threadName);
        });

        t1.start();

        try {
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        t1.stop(); // 不会执行end的输出 会释放锁
        t1.suspend(); // 不会执行end的输出 不会释放锁

        Thread t2 = new Thread(() -> {
            String threadName = Thread.currentThread().getName();
            log.info("{} waiting lock ... ", threadName);
            synchronized (lock) {
                log.info("{} get lock", threadName);
            }
        });

        t2.start();
    }
}
