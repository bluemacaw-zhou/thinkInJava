package io.bluemacaw.concurrency.visibility;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class VolatileTest2 {
    private static volatile boolean flag = true;

    @Test
    public void mainTest() throws Exception {
        new Thread(() -> {
            while (true) {
                if (flag) {
                    log.info("turn on");
                    flag = false;
                }
            }
        }).start();

        new Thread(() -> {
            while (true) {
                if (!flag) {
                    log.info("turn off");
                    flag = true;
                }
            }
        }).start();


        Thread.sleep(3000);
    }
}
