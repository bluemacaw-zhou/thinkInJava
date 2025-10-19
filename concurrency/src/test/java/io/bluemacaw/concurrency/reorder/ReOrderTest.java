package io.bluemacaw.concurrency.reorder;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class ReOrderTest {
    private static int x = 0, y = 0;
    private static int a = 0, b = 0;

    @Test
    public void mainTest() throws Exception {
        int i = 0;
        while (true) {
            i++;

            x = 0; y = 0; a = 0; b = 0;

            // 如果没有指令重排序 那么xy的排列集合中 00是不会出现的
            Thread thread1 = new Thread(() -> {
                a = 1;
                x = b;
            });

            Thread thread2 = new Thread(() -> {
                b = 1;
                y = a;
            });

            thread1.start();
            thread2.start();

            thread1.join();
            thread2.join();

            log.info("第{}次 ({},{})", i, x, y);
            if (x == 0 && y == 0) {
                break;
            }
        }

    }
}
