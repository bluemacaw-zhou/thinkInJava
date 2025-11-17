package cn.com.wind.IMStarter.task;

import cn.com.wind.IMStarter.task.trace.TaskTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务链 - 串行执行任务
 * 任务按照添加顺序依次执行
 */
public class TaskChain extends CompositeTask<List<TaskResult<?>>> {

    public TaskChain() {
        super();
    }

    public TaskChain(String name) {
        super(name);
    }

    @Override
    public List<TaskResult<?>> execute(TaskContext context) throws Exception {
        List<TaskResult<?>> results = new ArrayList<>();

        try {
            for (Task<?> task : tasks) {
                // 为子任务创建 trace
                String parentTraceId = context.getCurrentTraceId();
                TaskTrace subTrace = context.getExecutionTrace().startTask(
                    task.getName(),
                    task.getClass().getName(),
                    parentTraceId
                );
                context.setCurrentTraceId(subTrace.getTraceId());

                // 设置执行类型
                if (task instanceof TaskChain) {
                    subTrace.setExecutionType("Sequential");
                } else if (task instanceof TaskGroup) {
                    subTrace.setExecutionType("Parallel");
                }

                try {
                    Object result = task.execute(context);
                    context.getExecutionTrace().completeTask(subTrace.getTraceId(), result);
                    results.add(TaskResult.success(result, task.getName()));

                    // 提取元数据（重试、降级信息）
                    updateSubTraceMetadata(task, subTrace, context);

                } catch (Exception e) {
                    context.getExecutionTrace().failTask(subTrace.getTraceId(), e);
                    results.add(TaskResult.failure(e, task.getName()));

                    // 提取元数据
                    updateSubTraceMetadata(task, subTrace, context);

                    // 调用异常处理器（如果有配置）
                    Exception exceptionToThrow = e;
                    try {
                        handleException(context, task.getName(), e);
                        // 异常处理器执行成功但未抛出异常，向上抛出原始异常以中断任务链
                    } catch (Exception handlerException) {
                        // 异常处理器抛出了新异常，使用新异常替代原始异常
                        exceptionToThrow = handlerException;
                    }

                    // 抛出异常中断任务链（finally 会恢复 trace ID）
                    throw exceptionToThrow;
                } finally {
                    // 恢复父任务的 trace ID
                    context.setCurrentTraceId(parentTraceId);
                }
            }

            // 所有任务成功完成，调用当前任务链的完成处理器
            handleCompletion(context, this.getName(), results, null);
            return results;

        } catch (Exception e) {
            // 任务链失败，调用当前任务链的完成处理器
            handleCompletion(context, this.getName(), null, e);
            throw e;
        }
    }

    /**
     * 更新子任务 trace 的元数据
     */
    private void updateSubTraceMetadata(Task<?> task, TaskTrace trace, TaskContext context) {
        // 处理 RetryableTask 的重试信息
        if (task instanceof RetryableTask) {
            RetryableTask<?> retryableTask = (RetryableTask<?>) task;
            String retryKey = "retry_attempt_" + retryableTask.getWrappedTask().getName();
            Integer retryAttempt = context.get(retryKey);
            if (retryAttempt != null && retryAttempt > 0) {
                trace.setRetryInfo(retryableTask.getMaxRetries(), retryAttempt + 1);
            } else if (retryableTask.getMaxRetries() > 0) {
                trace.setRetryInfo(retryableTask.getMaxRetries(), 1);
            }
        }

        // 处理 FallbackTask 的降级信息
        if (task instanceof FallbackTask) {
            Boolean usedFallback = context.get("used_fallback_" + task.getName());
            if (Boolean.TRUE.equals(usedFallback)) {
                trace.setUsedFallback(true);
            }
        }
    }

    /**
     * 创建新的任务链
     */
    public static TaskChain create() {
        return new TaskChain();
    }

    /**
     * 创建带名称的任务链
     */
    public static TaskChain create(String name) {
        return new TaskChain(name);
    }

    /**
     * 添加任务的链式调用
     */
    @Override
    public TaskChain addTask(Task<?> task) {
        super.addTask(task);
        return this;
    }

    /**
     * 设置异常处理器的链式调用
     */
    @Override
    public TaskChain onException(ExceptionHandler handler) {
        super.onException(handler);
        return this;
    }

    /**
     * 设置完成处理器的链式调用
     */
    @Override
    public TaskChain onComplete(CompletionHandler handler) {
        super.onComplete(handler);
        return this;
    }
}
