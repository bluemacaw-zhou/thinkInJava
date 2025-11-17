package cn.com.wind.IMStarter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池监控器
 * 监控线程池运行状态，检测资源是否充足、是否有阻塞
 */
public class ThreadPoolMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolMonitor.class);

    private final ThreadPoolExecutor executor;
    private final String poolName;
    private final ScheduledExecutorService monitorExecutor;

    // 监控指标
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);
    private final AtomicLong totalBlocked = new AtomicLong(0);

    // 告警阈值
    private int queueSizeAlertThreshold = 80; // 队列使用率告警阈值（百分比）
    private int activeThreadAlertThreshold = 90; // 活跃线程数告警阈值（百分比）
    private long blockTimeAlertMs = 1000; // 阻塞时间告警阈值（毫秒）

    public ThreadPoolMonitor(ThreadPoolExecutor executor, String poolName) {
        this.executor = executor;
        this.poolName = poolName;
        this.monitorExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "pool-monitor-" + poolName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 开始监控
     * @param periodSeconds 监控周期（秒）
     */
    public void startMonitoring(int periodSeconds) {
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                collectAndLogMetrics();
            } catch (Exception e) {
                logger.error("Monitor error for pool: {}", poolName, e);
            }
        }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        monitorExecutor.shutdown();
    }

    /**
     * 提交任务（带监控）
     */
    public Future<?> submit(Runnable task) {
        totalSubmitted.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            return executor.submit(() -> {
                long waitTime = System.currentTimeMillis() - startTime;
                if (waitTime > blockTimeAlertMs) {
                    totalBlocked.incrementAndGet();
                    logBlockAlert(waitTime);
                }
                task.run();
            });
        } catch (RejectedExecutionException e) {
            totalRejected.incrementAndGet();
            logRejectAlert();
            throw e;
        }
    }

    /**
     * 收集并记录指标
     */
    private void collectAndLogMetrics() {
        ThreadPoolMetrics metrics = collectMetrics();

        // 构建JSON格式日志
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "POOL_METRICS");
        logData.put("poolName", poolName);
        logData.put("activeThreads", metrics.activeCount);
        logData.put("poolSize", metrics.poolSize);
        logData.put("corePoolSize", metrics.corePoolSize);
        logData.put("maxPoolSize", metrics.maximumPoolSize);
        logData.put("queueSize", metrics.queueSize);
        logData.put("queueCapacity", metrics.queueCapacity);
        logData.put("queueUsagePercent", metrics.queueUsagePercent);
        logData.put("activeThreadPercent", metrics.activeThreadPercent);
        logData.put("completedTasks", metrics.completedTaskCount);
        logData.put("totalSubmitted", totalSubmitted.get());
        logData.put("totalRejected", totalRejected.get());
        logData.put("totalBlocked", totalBlocked.get());
        logData.put("largestPoolSize", metrics.largestPoolSize);

        logger.info(toJson(logData));

        // 检查告警条件
        checkAndAlert(metrics);
    }

    /**
     * 收集线程池指标
     */
    public ThreadPoolMetrics collectMetrics() {
        ThreadPoolMetrics metrics = new ThreadPoolMetrics();
        metrics.poolName = poolName;
        metrics.activeCount = executor.getActiveCount();
        metrics.poolSize = executor.getPoolSize();
        metrics.corePoolSize = executor.getCorePoolSize();
        metrics.maximumPoolSize = executor.getMaximumPoolSize();
        metrics.largestPoolSize = executor.getLargestPoolSize();
        metrics.completedTaskCount = executor.getCompletedTaskCount();
        metrics.taskCount = executor.getTaskCount();

        BlockingQueue<Runnable> queue = executor.getQueue();
        metrics.queueSize = queue.size();
        metrics.queueCapacity = queue.size() + queue.remainingCapacity();

        if (metrics.queueCapacity > 0) {
            metrics.queueUsagePercent = (int) ((metrics.queueSize * 100.0) / metrics.queueCapacity);
        }
        if (metrics.maximumPoolSize > 0) {
            metrics.activeThreadPercent = (int) ((metrics.activeCount * 100.0) / metrics.maximumPoolSize);
        }

        return metrics;
    }

    /**
     * 检查并告警
     */
    private void checkAndAlert(ThreadPoolMetrics metrics) {
        // 队列使用率告警
        if (metrics.queueUsagePercent >= queueSizeAlertThreshold) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("event", "POOL_ALERT");
            alert.put("alertType", "HIGH_QUEUE_USAGE");
            alert.put("poolName", poolName);
            alert.put("queueUsagePercent", metrics.queueUsagePercent);
            alert.put("threshold", queueSizeAlertThreshold);
            alert.put("queueSize", metrics.queueSize);
            alert.put("queueCapacity", metrics.queueCapacity);
            logger.warn(toJson(alert));
        }

        // 活跃线程数告警
        if (metrics.activeThreadPercent >= activeThreadAlertThreshold) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("event", "POOL_ALERT");
            alert.put("alertType", "HIGH_ACTIVE_THREADS");
            alert.put("poolName", poolName);
            alert.put("activeThreadPercent", metrics.activeThreadPercent);
            alert.put("threshold", activeThreadAlertThreshold);
            alert.put("activeCount", metrics.activeCount);
            alert.put("maxPoolSize", metrics.maximumPoolSize);
            logger.warn(toJson(alert));
        }
    }

    /**
     * 记录阻塞告警
     */
    private void logBlockAlert(long waitTime) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("event", "POOL_ALERT");
        alert.put("alertType", "TASK_BLOCKED");
        alert.put("poolName", poolName);
        alert.put("blockTimeMs", waitTime);
        alert.put("threshold", blockTimeAlertMs);
        logger.warn(toJson(alert));
    }

    /**
     * 记录拒绝告警
     */
    private void logRejectAlert() {
        Map<String, Object> alert = new HashMap<>();
        alert.put("event", "POOL_ALERT");
        alert.put("alertType", "TASK_REJECTED");
        alert.put("poolName", poolName);
        alert.put("totalRejected", totalRejected.get());
        logger.error(toJson(alert));
    }

    /**
     * 设置告警阈值
     */
    public void setQueueSizeAlertThreshold(int threshold) {
        this.queueSizeAlertThreshold = threshold;
    }

    public void setActiveThreadAlertThreshold(int threshold) {
        this.activeThreadAlertThreshold = threshold;
    }

    public void setBlockTimeAlertMs(long blockTimeMs) {
        this.blockTimeAlertMs = blockTimeMs;
    }

    /**
     * 转换为JSON
     */
    private String toJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
        }
        json.append("}");
        return json.toString();
    }

    /**
     * 线程池指标
     */
    public static class ThreadPoolMetrics {
        public String poolName;
        public int activeCount;           // 活跃线程数
        public int poolSize;              // 当前线程池大小
        public int corePoolSize;          // 核心线程数
        public int maximumPoolSize;       // 最大线程数
        public int largestPoolSize;       // 历史最大线程数
        public long completedTaskCount;   // 已完成任务数
        public long taskCount;            // 总任务数
        public int queueSize;             // 队列中任务数
        public int queueCapacity;         // 队列容量
        public int queueUsagePercent;     // 队列使用率（百分比）
        public int activeThreadPercent;   // 活跃线程占比（百分比）

        @Override
        public String toString() {
            return String.format(
                "ThreadPoolMetrics{poolName='%s', active=%d/%d(%d%%), queue=%d/%d(%d%%), completed=%d}",
                poolName, activeCount, maximumPoolSize, activeThreadPercent,
                queueSize, queueCapacity, queueUsagePercent,
                completedTaskCount
            );
        }
    }
}
