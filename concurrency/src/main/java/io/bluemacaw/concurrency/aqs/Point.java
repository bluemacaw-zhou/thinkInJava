package io.bluemacaw.concurrency.aqs;

import lombok.extern.slf4j.Slf4j;
import sun.security.ssl.SSLLogger;

import java.util.concurrent.locks.StampedLock;

/**
 * 读写锁
 */
@Slf4j
public class Point {
    private final StampedLock stampedLock = new StampedLock();

    private volatile double x;
    private volatile double y;

    public void move(double deltaX, double deltaY) {
        // 获取写锁
        long stamp = stampedLock.writeLock();
        log.info("获取到writeLock");
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            // 释放写锁
            stampedLock.unlockWrite(stamp);
            log.info("释放writeLock");
        }
    }

    // 计算当前坐标到原点的距离
    public double distanceFromOrigin() {
        // 获得一个乐观读锁
        long stamp = stampedLock.tryOptimisticRead(); // 有乐观锁的概念 并不是一定要阻塞

        double currentX = x;
        log.info("第1次读, x:{}, y:{}, currentX:{}", x, y, currentX);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double currentY = y;
        log.info("第2次读, x:{}, y:{}, currentX:{}, currentY:{}", x, y, currentX, currentY);

        // 检查乐观读锁后是否有其他写锁发生
        if (!stampedLock.validate(stamp)) {
            // 获取一个悲观读锁
            stamp = stampedLock.readLock();
            try {
                currentX = x;
                currentY = y;

                log.info("最终结果, x:{}, y:{}, currentX:{}, currentY:{}", x, y, currentX, currentY);
            } finally {
                // 释放悲观读锁
                stampedLock.unlockRead(stamp);
            }
        }

        double distance = Math.sqrt(currentX * currentX + currentY * currentY);
        log.info("距离为:{}", distance);

        return distance;
    }
}
