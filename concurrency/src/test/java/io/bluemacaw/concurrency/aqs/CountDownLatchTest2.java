package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/*
 * 让单个线程等待：多个线程(任务)完成后，进行汇总合并
 */
@Slf4j
public class CountDownLatchTest2 {
    @Test
    public void mainTest() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    Thread.sleep(1000 + ThreadLocalRandom.current().nextInt(1000));
                    log.info("{} finish task {}", Thread.currentThread().getName(), index);
                    countDownLatch.countDown();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        // 主线程在阻塞，当计数器==0，就唤醒主线程往下执行。
        countDownLatch.await();
        log.info("主线程:在所有任务运行完成后，进行结果汇总");
    }
}
