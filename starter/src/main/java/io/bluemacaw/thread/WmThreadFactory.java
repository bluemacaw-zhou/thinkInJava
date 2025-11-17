package cn.com.wind.IMStarter.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author shzhou.michael
 */
public class WmThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public WmThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
        // 设置为非守护线程
        thread.setDaemon(false);
        // 设置线程优先级
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    }
}
