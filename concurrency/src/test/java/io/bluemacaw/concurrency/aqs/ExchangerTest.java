package io.bluemacaw.concurrency.aqs;

import org.junit.Test;

import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 两个线程间数据交换
 */
public class ExchangerTest {
    private static final Exchanger<String> exgr = new Exchanger<String>();
    private static ExecutorService threadPool = Executors.newFixedThreadPool(2);

    @Test
    public void mainTest() throws Exception {
        threadPool.execute(() -> {
            try {
                String A = "银行流水A"; // A录入银行流水数据
                exgr.exchange(A);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        threadPool.execute(() -> {
            try {
                String B = "银行流水B"; // B录入银行流水数据
                String A = exgr.exchange(B);
                System.out.println("A和B数据是否一致：" + A.equals(B) + ", A录入的是："+ A + ", B录入是：" + B);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread.sleep(3000);

        threadPool.shutdown();
    }
}
