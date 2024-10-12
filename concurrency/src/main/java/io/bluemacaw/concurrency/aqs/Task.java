package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;

@Slf4j
public class Task implements Runnable {
    private final Semaphore semaphore;

    public Task(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    @Override
    public void run() {
        try {
            semaphore.acquire(1); //占用1个资源
            log.info("执行任务方法");
            Thread.sleep(2000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            semaphore.release(1); //释放一个资源
        }
    }
}
