package io.bluemacaw.concurrency.reorder;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class FinalFieldTest {
    private static T t;

    @Test
    public void mainTest() throws Exception {
        Thread t1 = new Thread(() -> {
            while (true) {
                t = new T();
            }
        });

        Thread t2 = new Thread(() -> {
            while (true) {
                int i = 0, j = 0;

                if (null != t) {
                    i = t.getX(); // x保证一定是3 final保证的可见性
                    j = t.getY(); // y不一定是4 可能是0

                    log.info("i = {}, j = {}", i, j);
                    if (j == 0) { // 很难复现出来
                        break;
                    }
                }
            }
        });

        t1.start();
        t2.start();

        t2.join();
    }
}
