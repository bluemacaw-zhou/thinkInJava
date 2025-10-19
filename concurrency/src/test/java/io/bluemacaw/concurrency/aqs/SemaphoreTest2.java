package io.bluemacaw.concurrency.aqs;

import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SemaphoreTest2 {
    /**
     * 实现一个同时只能处理5个请求的限流器
     */
    private static final Semaphore semaphore = new Semaphore(5);

    /**
     * 定义一个线程池
     */
    private static ThreadPoolExecutor executor = new ThreadPoolExecutor(
            10,
            50,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(200));

    @Test
    public void mainTest() throws InterruptedException {
        for (; ; ) {
            Thread.sleep(100); // 模拟请求以10个/s的速度
            executor.execute(new Task(semaphore));
        }
    }
}
