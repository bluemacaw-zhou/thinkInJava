package io.bluemacaw.concurrency.visibility;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class VolatileNotAtomTest {
    private volatile static int sum = 0;

    @Test
    public void mainTest() throws Exception {
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    sum++;
                }
            });

            thread.start();
            thread.join();
        }

        // Thread.sleep(3000); // 直接主线程sleep并不能得到想要的结果

        log.info("{}", sum);
    }
}
