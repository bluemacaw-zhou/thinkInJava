package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.LockSupport;

@Slf4j
public class ParkUnParkTest {
    private volatile Object store = null;
    private final Object goodsLock = new Object();

    /*
     * 正常使用方式
     */
    @Test
    public void parkUnparkTest() throws Exception {
        Thread consumer = new Thread(() -> {
            log.info("consumer wait store open ... ");

            while (null == store) {
                LockSupport.park();
                log.info("buy goods success!");
            }
        });
        consumer.start();

        Thread.sleep(3000);

        store = new Object();
        LockSupport.unpark(consumer);

        log.info("store open, notify consumer ... ");

        Thread.sleep(2000);
    }

    /*
     * 死锁
     */
    @Test
    public void parkUnparkExceptionTest() throws Exception {
        Thread consumer = new Thread(() -> {
            log.info("consumer wait store open ... ");

            if (null == store) {
                synchronized (goodsLock) {
                    LockSupport.park();
                    log.info("buy goods success!");
                }
            }
        });
        consumer.start();

        Thread.sleep(3000);

        store = new Object();

        synchronized (goodsLock) {
            LockSupport.unpark(consumer);
            log.info("store open, notify consumer ... ");
        }
    }

    /*
     * 单次park 多次unpark 线程正常运行
     */
    @Test
    public void moreUnparkTest() {
        LockSupport.unpark(Thread.currentThread());
        LockSupport.unpark(Thread.currentThread());
        LockSupport.unpark(Thread.currentThread());
        log.info("调用了三次unpark");

        LockSupport.park(Thread.currentThread());
        log.info("调用了一次park");
    }

    /*
     * 多次park 单次unpark 等待, 即使调用相同次数的unpark也不会正常唤醒
     */
    @Test
    public void moreParkTest() {
        LockSupport.park(Thread.currentThread());
        log.info("调用了一次park");

        LockSupport.park(Thread.currentThread());
        LockSupport.park(Thread.currentThread());
        log.info("又调用了两次park");

        LockSupport.unpark(Thread.currentThread());
        log.info("调用了一次unpark方法");

        LockSupport.unpark(Thread.currentThread());
        LockSupport.unpark(Thread.currentThread());
        log.info("又调用了两次unpark方法");
    }
}
