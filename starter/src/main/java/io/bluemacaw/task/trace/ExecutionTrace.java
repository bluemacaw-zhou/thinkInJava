package cn.com.wind.IMStarter.task.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行链路追踪
 * 管理整个任务执行过程的追踪信息
 */
public class ExecutionTrace {
    private static final Logger logger = LoggerFactory.getLogger("TaskTrace");

    private final String executionId;
    private final LocalDateTime startTime;
    private final Map<String, TaskTrace> traces;
    private final List<TaskTrace> rootTasks;
    private String currentTraceId;

    public ExecutionTrace(String executionId) {
        this.executionId = executionId;
        this.startTime = LocalDateTime.now();
        this.traces = new ConcurrentHashMap<>();
        this.rootTasks = new ArrayList<>();
    }

    /**
     * 开始追踪任务
     */
    public TaskTrace startTask(String taskName, String taskClass, String parentTraceId) {
        String traceId = generateTraceId();
        TaskTrace trace = new TaskTrace(traceId, taskName, taskClass, parentTraceId);
        traces.put(traceId, trace);

        if (parentTraceId == null) {
            synchronized (rootTasks) {
                rootTasks.add(trace);
            }
        } else {
            TaskTrace parentTrace = traces.get(parentTraceId);
            if (parentTrace != null) {
                parentTrace.addSubTask(trace);
            }
        }

        this.currentTraceId = traceId;
        return trace;
    }

    /**
     * 完成任务追踪
     */
    public void completeTask(String traceId, Object result) {
        TaskTrace trace = traces.get(traceId);
        if (trace != null) {
            trace.complete(result);
        }
    }

    /**
     * 标记任务失败
     */
    public void failTask(String traceId, Exception exception) {
        TaskTrace trace = traces.get(traceId);
        if (trace != null) {
            trace.fail(exception);
        }
    }

    /**
     * 获取当前追踪ID
     */
    public String getCurrentTraceId() {
        return currentTraceId;
    }

    /**
     * 获取任务追踪信息
     */
    public TaskTrace getTrace(String traceId) {
        return traces.get(traceId);
    }

    /**
     * 获取所有根任务
     */
    public List<TaskTrace> getRootTasks() {
        return new ArrayList<>(rootTasks);
    }

    /**
     * 获取所有追踪信息
     */
    public Map<String, TaskTrace> getAllTraces() {
        return new ConcurrentHashMap<>(traces);
    }

    /**
     * 生成追踪ID
     */
    private String generateTraceId() {
        return executionId + "-" + System.nanoTime();
    }

    /**
     * 打印树状可视化流程图（推荐使用）
     * 输出为单行日志，便于 filebeat 采集上送到 ES/Kibana
     */
    public void printTreeView() {
        ExecutionStats stats = getStats();

        // 构建树状图文本
        StringBuilder treeText = new StringBuilder();
        for (int i = 0; i < rootTasks.size(); i++) {
            TaskTrace rootTask = rootTasks.get(i);
            boolean isLast = (i == rootTasks.size() - 1);
            treeText.append(rootTask.toTreeView("", isLast));
        }

        // 构建完整的日志内容（包含所有信息）
        StringBuilder logContent = new StringBuilder();
        logContent.append("\n========== Task Execution Flow ==========\n");
        logContent.append("Execution ID: ").append(executionId).append("\n");
        logContent.append("Stats: Total ").append(stats.totalTasks).append(" tasks, ");
        logContent.append("Success ").append(stats.successTasks).append(", ");
        logContent.append("Failed ").append(stats.failedTasks).append(", ");
        logContent.append("Duration ").append(stats.totalDurationMs).append("ms\n");
        logContent.append("=====================================\n");
        logContent.append(treeText);
        logContent.append("=====================================");

        // 通过 log4j 输出单行日志（换行符会被保留在字符串中）
        logger.info(logContent.toString());

        // 同时输出到控制台，方便本地调试
        logger.debug("\n" + logContent.toString() + "\n");
    }

    /**
     * 打印详细追踪信息
     */
    public void printTrace() {
        logger.info("\n========== Execution Trace ==========");
        logger.info("Execution ID: " + executionId);
        logger.info("Start Time: " + startTime);
        logger.info("Root Tasks: " + rootTasks.size());
        logger.info("=====================================\n");

        for (TaskTrace rootTask : rootTasks) {
            logger.info(rootTask.toDetailString(0));
        }
    }

    /**
     * 获取执行统计信息
     */
    public ExecutionStats getStats() {
        int total = traces.size();
        long success = traces.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.SUCCESS)
                .count();
        long failed = traces.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .count();
        long totalDuration = rootTasks.stream()
                .mapToLong(TaskTrace::getDurationMs)
                .sum();

        return new ExecutionStats(total, (int)success, (int)failed, totalDuration);
    }

    public static class ExecutionStats {
        public final int totalTasks;
        public final int successTasks;
        public final int failedTasks;
        public final long totalDurationMs;

        public ExecutionStats(int totalTasks, int successTasks, int failedTasks, long totalDurationMs) {
            this.totalTasks = totalTasks;
            this.successTasks = successTasks;
            this.failedTasks = failedTasks;
            this.totalDurationMs = totalDurationMs;
        }

        @Override
        public String toString() {
            return String.format("Total: %d, Success: %d, Failed: %d, Duration: %dms",
                    totalTasks, successTasks, failedTasks, totalDurationMs);
        }
    }
}
