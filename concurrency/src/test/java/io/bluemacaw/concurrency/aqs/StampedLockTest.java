package io.bluemacaw.concurrency.aqs;

import org.junit.jupiter.api.Test;

public class StampedLockTest {
    @Test
    public void mainTest() throws InterruptedException {
        Point point = new Point();

        //第一次移动x,y
        new Thread(()-> point.move(100,200)).start();
        Thread.sleep(100);

        new Thread(point::distanceFromOrigin).start();
        Thread.sleep(500);

        //第二次移动x,y
        new Thread(()-> point.move(300,400)).start();


        Thread.sleep(7000);
    }
}

