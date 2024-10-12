package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class Runner implements Runnable {
    private final CyclicBarrier cyclicBarrier;

    public Runner (CyclicBarrier cyclicBarrier) {
        this.cyclicBarrier = cyclicBarrier;
    }

    @Override
    public void run() {
        try {
            String threadName = Thread.currentThread().getName();
            int sleepMills = ThreadLocalRandom.current().nextInt(1000);
            Thread.sleep(sleepMills);
            log.info("{} 选手已就位, 准备共用时: {}ms 已就位选手数: {}", threadName, sleepMills, cyclicBarrier.getNumberWaiting());
            cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }
}
