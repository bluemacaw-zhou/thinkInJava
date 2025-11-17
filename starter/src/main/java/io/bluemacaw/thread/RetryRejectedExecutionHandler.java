package cn.com.wind.IMStarter.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author shzhou.michael
 */
@Slf4j
public class RetryRejectedExecutionHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        try {
            log.warn("Task rejected, queue size: {}, active threads: {}", executor.getQueue().size(), executor.getActiveCount());

            if (!executor.isShutdown()) {
                Thread.sleep(500);
                executor.getQueue().put(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " + executor.toString(), e);
        }
    }
}
