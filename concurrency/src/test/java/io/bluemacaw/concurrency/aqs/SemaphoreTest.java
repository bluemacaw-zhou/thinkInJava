package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;

/*
 * Semaphore是一个计数信号量, Semaphore经常用于限制获取资源的线程数量
 */
@Slf4j
public class SemaphoreTest {
    @Test
    public void mainTest() throws Exception {
        // 声明3个窗口
        Semaphore windows = new Semaphore(3);

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    String threadName = Thread.currentThread().getName();
                    while (!windows.tryAcquire()) {
                        Thread.sleep(1000);
                    }

                    log.info("{}: 开始买票", threadName);
                    Thread.sleep(3000); //模拟买票流程
                    log.info("{}: 购票成功", threadName);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    windows.release(); // 释放窗口
                }
            }).start();
        }

        Thread.sleep(10000);
    }
}
