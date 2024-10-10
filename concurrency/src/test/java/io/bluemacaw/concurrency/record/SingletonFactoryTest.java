package io.bluemacaw.concurrency.record;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class SingletonFactoryTest {
    @Test
    public void mainTest() throws Exception {
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                SingletonFactory instance = SingletonFactory.getInstance();
                instance.sayReady();
            });

            t.start();
            t.join();
        }

        // Thread.currentThread().join();
    }
}
