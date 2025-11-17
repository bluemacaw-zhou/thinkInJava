package cn.com.wind.IMStarter.task;

import cn.com.wind.IMStarter.task.trace.TaskTrace;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 任务编排器
 * 负责管理线程池和任务执行
 * 要求外部传入线程池，由外部统一管理线程资源
 * 
 * 设计理念：
 * - 不是Spring Bean，由用户根据场景手动创建
 * - 支持多实例，不同业务场景可以有不同的编排器
 * - 线程池由外部管理，支持Spring注入或手动创建
 */
public class TaskOrchestrator {
    private final ExecutorService executorService;

    /**
     * 使用指定线程池创建编排器
     * @param executorService 线程池（必须由外部传入）
     */
    public TaskOrchestrator(ExecutorService executorService) {
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null. Please provide an ExecutorService.");
        }
        this.executorService = executorService;
    }

    /**
     * 执行任务（带链路追踪）
     * @param task 任务
     * @param context 上下文
     * @param <T> 结果类型
     * @return 执行结果
     */
    public <T> T execute(Task<T> task, TaskContext context) throws Exception {
        // 如果是任务组，注入线程池
        if (task instanceof TaskGroup) {
            ((TaskGroup) task).withExecutor(executorService);
        }

        // 开始追踪
        String parentTraceId = context.getCurrentTraceId();
        TaskTrace trace = context.getExecutionTrace().startTask(
                task.getName(),
                task.getClass().getName(),
                parentTraceId
        );
        context.setCurrentTraceId(trace.getTraceId());

        // 设置执行类型
        if (task instanceof TaskChain) {
            trace.setExecutionType("Sequential");
        } else if (task instanceof TaskGroup) {
            trace.setExecutionType("Parallel");
        }

        try {
            T result = task.execute(context);
            context.getExecutionTrace().completeTask(trace.getTraceId(), result);

            // 从context中提取重试/降级信息并更新trace
            updateTraceMetadata(task, trace, context);

            return result;
        } catch (Exception e) {
            context.getExecutionTrace().failTask(trace.getTraceId(), e);

            // 即使失败也提取元数据
            updateTraceMetadata(task, trace, context);

            throw e;
        } finally {
            // 恢复父任务的追踪ID
            context.setCurrentTraceId(parentTraceId);
        }
    }

    /**
     * 异步执行任务
     * @param task 任务
     * @param context 上下文
     * @param <T> 结果类型
     * @return Future 对象
     */
    public <T> Future<T> executeAsync(Task<T> task, TaskContext context) {
        return executorService.submit(() -> {
            // 如果是任务组，注入线程池
            if (task instanceof TaskGroup) {
                ((TaskGroup) task).withExecutor(executorService);
            }
            return task.execute(context);
        });
    }

    /**
     * 执行任务并返回结果封装
     * @param task 任务
     * @param context 上下文
     * @param <T> 结果类型
     * @return 任务结果
     */
    public <T> TaskResult<T> executeWithResult(Task<T> task, TaskContext context) {
        try {
            T result = execute(task, context);
            return TaskResult.success(result, task.getName());
        } catch (Exception e) {
            return TaskResult.failure(e, task.getName());
        }
    }

    /**
     * 创建任务链构建器
     * @return 任务链
     */
    public TaskChain chain() {
        return TaskChain.create();
    }

    /**
     * 创建带名称的任务链构建器
     * @param name 名称
     * @return 任务链
     */
    public TaskChain chain(String name) {
        return TaskChain.create(name);
    }

    /**
     * 创建任务组构建器
     * @return 任务组
     */
    public TaskGroup group() {
        return TaskGroup.create().withExecutor(executorService);
    }

    /**
     * 创建带名称的任务组构建器
     * @param name 名称
     * @return 任务组
     */
    public TaskGroup group(String name) {
        return TaskGroup.create(name).withExecutor(executorService);
    }

    /**
     * 打印执行追踪信息
     * @param context 任务上下文
     */
    public void printTrace(TaskContext context) {
        context.getExecutionTrace().printTrace();
    }

    /**
     * 获取执行统计信息
     * @param context 任务上下文
     * @return 统计信息
     */
    public String getStats(TaskContext context) {
        return context.getExecutionTrace().getStats().toString();
    }

    /**
     * 获取线程池
     * @return 线程池
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * 从context中提取任务元数据并更新trace
     */
    private void updateTraceMetadata(Task<?> task, TaskTrace trace, TaskContext context) {
        // 处理 RetryableTask 的重试信息
        if (task instanceof RetryableTask) {
            RetryableTask<?> retryableTask = (RetryableTask<?>) task;
            // 从 context 中获取重试次数（如果有）
            String retryKey = "retry_attempt_" + retryableTask.getWrappedTask().getName();
            Integer retryAttempt = context.get(retryKey);
            if (retryAttempt != null && retryAttempt > 0) {
                trace.setRetryInfo(retryableTask.getMaxRetries(), retryAttempt + 1); // +1 because attempt starts from 0
            } else {
                // 没有重试，第一次就成功了
                if (retryableTask.getMaxRetries() > 0) {
                    trace.setRetryInfo(retryableTask.getMaxRetries(), 1);
                }
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
}
