package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.concurrent.CyclicBarrier;

@Slf4j
public class CyclicBarrierTest {
    @Test
    public void mainTest() throws Exception {
        CyclicBarrier cyclicBarrier = new CyclicBarrier(3);

        for (int i = 0; i < 6; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String threadName = Thread.currentThread().getName();
                        log.info("{}开始等待其他线程", threadName);

                        cyclicBarrier.await(); // 先等待 等待的线程等于3了之后 通过执行
                        log.info("{}开始执行", threadName);
                        Thread.sleep(2000);  // 模拟业务处理

                        log.info("{}执行完毕", threadName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        Thread.sleep(5000);  // 等待子线程执行完成
    }
}
