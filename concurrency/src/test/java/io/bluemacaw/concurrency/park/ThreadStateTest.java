package io.bluemacaw.concurrency.park;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.LockSupport;

@Slf4j
public class ThreadStateTest {
    @Test
    public void mainTest() throws Exception {
        Thread thread = new Thread(LockSupport::park);

        log.info("线程状态: {}", thread.getState());
        thread.start();
        log.info("线程状态: {}", thread.getState());
        Thread.sleep(1000);
        log.info("线程状态: {}", thread.getState());
    }
}
