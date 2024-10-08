package io.bluemacaw.concurrency.record;

import org.junit.Test;

public class SingletonFactoryTest {
    @Test
    public void mainTest() throws Exception {
        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(() -> {
                SingletonFactory instance = SingletonFactory.getInstance();
                instance.sayReady(); // TODO:没有日志输出
            });

            t.join();
        }
    }
}
