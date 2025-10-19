package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

/*
 * 让多个线程等待：模拟并发，让并发线程一起执行
 */
@Slf4j
public class CountDownLatchTest {
    @Test
    public void mainTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        for (int i = 0; i < 5; i++) {
            Thread t = new Thread(() -> {
                try {
                    //准备完毕…… 运动员都阻塞在等待号令
                    countDownLatch.await();
                    String parter = "[" + Thread.currentThread().getName() + "]";
                    log.info("{} prepare to run ... ", parter);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            t.start();
        }

        Thread.sleep(1000);// 裁判准备发令
        countDownLatch.countDown();// 发令枪：执行发令
        Thread.sleep(2000);// 等待打印完成
    }
}
