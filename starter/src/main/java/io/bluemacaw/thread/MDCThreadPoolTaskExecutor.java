package cn.com.wind.IMStarter.thread;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author shzhou.michael
 */
@Slf4j
public class MDCThreadPoolTaskExecutor extends ThreadPoolExecutor {
    public MDCThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public MDCThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public MDCThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public MDCThreadPoolTaskExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void execute(Runnable task) {
        // 获取当前线程MDC的上下文
        Map<String, String> context = MDC.getCopyOfContextMap();

        super.execute(() -> {
            try {
                if (null != context) {
                    MDC.setContextMap(context);
                }

                task.run();
            } finally {
                MDC.clear();
            }
        });
    }

    @Override
    public Future<?> submit(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();

        return super.submit(() -> {
            try {
                if (null != context) {
                    MDC.setContextMap(context);
                }

                task.run();
            } finally {
                MDC.clear();
            }
        });
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();

        // 包装Callable，确保MDC上下文传递
        Callable<T> wrappedTask = () -> {
            try {
                if (context != null) {
                    MDC.setContextMap(context);
                }

                return task.call();
            } finally {
                MDC.clear();
            }
        };

        // 提交包装后的任务
        return super.submit(wrappedTask);
    }
}
