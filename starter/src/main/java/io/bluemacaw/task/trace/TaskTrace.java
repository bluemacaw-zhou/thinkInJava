package cn.com.wind.IMStarter.task.trace;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行追踪信息
 * 记录单个任务的执行详情
 */
public class TaskTrace {
    private final String traceId;
    private final String taskName;
    private final String taskClass;
    private final String parentTraceId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private String threadName;
    private TaskStatus status;
    private Exception exception;
    private final List<TaskTrace> subTasks;
    private Object result;
    private String displayName; // 显示名称，用于覆盖默认的taskName，特别是在条件任务场景中

    // 元数据：用于记录任务的额外信息
    private final Map<String, Object> metadata;
    private String executionType; // "串行" 或 "并行"
    private Integer retryCount;    // 重试次数
    private Integer currentAttempt; // 当前尝试次数
    private Boolean usedFallback;   // 是否使用了降级
    private String notes;           // 额外说明

    public TaskTrace(String traceId, String taskName, String taskClass, String parentTraceId) {
        this.traceId = traceId;
        this.taskName = taskName;
        this.taskClass = taskClass;
        this.parentTraceId = parentTraceId;
        this.startTime = LocalDateTime.now();
        this.threadName = Thread.currentThread().getName();
        this.status = TaskStatus.RUNNING;
        this.subTasks = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.displayName = null; // 默认不覆盖任务名称
    }

    public void complete(Object result) {
        this.endTime = LocalDateTime.now();
        this.status = TaskStatus.SUCCESS;
        this.result = result;
    }

    public void fail(Exception exception) {
        this.endTime = LocalDateTime.now();
        this.status = TaskStatus.FAILED;
        this.exception = exception;
    }

    public void addSubTask(TaskTrace subTask) {
        this.subTasks.add(subTask);
    }

    public long getDurationMs() {
        if (endTime == null) {
            return Duration.between(startTime, LocalDateTime.now()).toMillis();
        }
        return Duration.between(startTime, endTime).toMillis();
    }

    // 元数据管理
    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public void setRetryInfo(int maxRetries, int currentAttempt) {
        this.retryCount = maxRetries;
        this.currentAttempt = currentAttempt;
    }

    public void setUsedFallback(boolean usedFallback) {
        this.usedFallback = usedFallback;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * 设置显示名称，用于覆盖默认的任务名称
     * 特别适用于条件任务等场景，需要显示实际执行的任务名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取显示名称，如果未设置则返回原始任务名称
     */
    public String getDisplayName() {
        return displayName != null ? displayName : taskName;
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }

    // Getters
    public String getTraceId() { return traceId; }
    public String getTaskName() { return taskName; }
    public String getTaskClass() { return taskClass; }
    public String getParentTraceId() { return parentTraceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getThreadName() { return threadName; }
    public TaskStatus getStatus() { return status; }
    public Exception getException() { return exception; }
    public List<TaskTrace> getSubTasks() { return new ArrayList<>(subTasks); }
    public Object getResult() { return result; }
    public String getExecutionType() { return executionType; }
    public Integer getRetryCount() { return retryCount; }
    public Integer getCurrentAttempt() { return currentAttempt; }
    public Boolean getUsedFallback() { return usedFallback; }
    public String getNotes() { return notes; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s) - %s - %dms - Thread: %s",
                traceId, getDisplayName(), taskClass, status, getDurationMs(), threadName);
    }

    /**
     * 生成树状可视化结构（简洁版本，适合快速理解流程）
     */
    public String toTreeView(String prefix, boolean isLast) {
        StringBuilder sb = new StringBuilder();

        // 1. 任务主行：名称 + 耗时 + 状态
        sb.append(prefix);
        sb.append(isLast ? "└─ " : "├─ ");
        // 使用显示名称，可能已被覆盖为实际执行的任务名称
        sb.append(getDisplayName());
        sb.append(" [").append(getDurationMs()).append("ms] ");

        // 状态图标
        if (status == TaskStatus.SUCCESS) {
            sb.append("✓");
        } else if (status == TaskStatus.FAILED) {
            sb.append("✗");
        } else {
            sb.append("⏳");
        }

        // 2. 额外信息（执行类型、重试、降级等）
        List<String> info = new ArrayList<>();

        if (executionType != null) {
            info.add(executionType);
        }

        if (retryCount != null && retryCount > 0) {
            if (currentAttempt != null && currentAttempt > 1) {
                info.add("Retry " + retryCount + " times, attempt " + currentAttempt + " succeeded");
            } else {
                info.add("Max retry " + retryCount + " times");
            }
        }

        if (Boolean.TRUE.equals(usedFallback)) {
            info.add("Fallback to alternative solution");
        }

        if (notes != null && !notes.isEmpty()) {
            info.add(notes);
        }

        if (!info.isEmpty()) {
            sb.append(" (").append(String.join(", ", info)).append(")");
        }

        // 3. 如果失败，显示错误信息
        if (status == TaskStatus.FAILED && exception != null) {
            sb.append(" - Error: ").append(exception.getMessage());
        }

        sb.append("\n");

        // 4. 递归打印子任务
        if (!subTasks.isEmpty()) {
            String childPrefix = prefix + (isLast ? "   " : "│  ");
            for (int i = 0; i < subTasks.size(); i++) {
                boolean isLastChild = (i == subTasks.size() - 1);
                sb.append(subTasks.get(i).toTreeView(childPrefix, isLastChild));
            }
        }

        return sb.toString();
    }

    /**
     * 生成详细版本（包含所有信息）
     */
    public String toDetailString(int indent) {
        StringBuilder sb = new StringBuilder();
        String prefix = "  ".repeat(indent);

        sb.append(prefix).append("┌─ ").append(taskName)
          .append(" (").append(taskClass).append(")\n");
        sb.append(prefix).append("│  TraceId: ").append(traceId).append("\n");
        sb.append(prefix).append("│  Status: ").append(status).append("\n");
        sb.append(prefix).append("│  Duration: ").append(getDurationMs()).append("ms\n");
        sb.append(prefix).append("│  Thread: ").append(threadName).append("\n");
        sb.append(prefix).append("│  Start: ").append(startTime).append("\n");

        if (endTime != null) {
            sb.append(prefix).append("│  End: ").append(endTime).append("\n");
        }

        if (exception != null) {
            sb.append(prefix).append("│  Error: ").append(exception.getMessage()).append("\n");
        }

        if (!subTasks.isEmpty()) {
            sb.append(prefix).append("│  SubTasks:\n");
            for (TaskTrace subTask : subTasks) {
                sb.append(subTask.toDetailString(indent + 2));
            }
        }

        sb.append(prefix).append("└─────────────────\n");

        return sb.toString();
    }
}
