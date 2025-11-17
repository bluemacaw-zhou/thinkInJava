package cn.com.wind.IMStarter.task;

import cn.com.wind.IMStarter.task.trace.TaskTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 任务组 - 并行执行任务
 * 所有任务同时执行，等待全部完成
 */
public class TaskGroup extends CompositeTask<List<TaskResult<?>>> {

    private ExecutorService executorService;
    private boolean ownExecutor = false;

    public TaskGroup() {
        super();
    }

    public TaskGroup(String name) {
        super(name);
    }

    /**
     * 设置线程池
     * @param executorService 线程池
     * @return 当前任务组
     */
    public TaskGroup withExecutor(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    @Override
    public List<TaskResult<?>> execute(TaskContext context) throws Exception {
        // 如果没有设置线程池，创建默认线程池
        if (executorService == null) {
            executorService = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors())
            );
            ownExecutor = true;
        }

        String parentTraceId = context.getCurrentTraceId();
        List<Future<TaskResult<?>>> futures = new ArrayList<>();

        // 提交所有任务
        for (Task<?> task : tasks) {
            // 为子任务创建 trace（在主线程中创建，确保正确的父子关系）
            TaskTrace subTrace = context.getExecutionTrace().startTask(
                task.getName(),
                task.getClass().getName(),
                parentTraceId
            );

            // 设置执行类型
            if (task instanceof TaskChain) {
                subTrace.setExecutionType("Sequential");
            } else if (task instanceof TaskGroup) {
                subTrace.setExecutionType("Parallel");
            }

            Future<TaskResult<?>> future = executorService.submit(() -> {
                // 在子线程中设置当前 trace ID
                context.setCurrentTraceId(subTrace.getTraceId());

                try {
                    Object result = task.execute(context);
                    context.getExecutionTrace().completeTask(subTrace.getTraceId(), result);

                    // 提取元数据
                    updateSubTraceMetadata(task, subTrace, context);

                    return TaskResult.success(result, task.getName());
                } catch (Exception e) {
                    context.getExecutionTrace().failTask(subTrace.getTraceId(), e);

                    // 提取元数据
                    updateSubTraceMetadata(task, subTrace, context);

                    // 调用异常处理器
                    Exception exceptionToReturn = e;
                    try {
                        handleException(context, task.getName(), e);
                        // 异常处理器未抛出异常
                    } catch (Exception handlerException) {
                        // 异常处理器抛出了异常，使用处理器异常
                        exceptionToReturn = handlerException;
                    }

                    // 返回失败结果
                    return TaskResult.failure(exceptionToReturn, task.getName());
                } finally {
                    // 恢复父任务的 trace ID
                    context.setCurrentTraceId(parentTraceId);
                }
            });
            futures.add(future);
        }

        // 等待任务完成，一旦发现失败任务立即取消所有其他任务
        List<TaskResult<?>> results = new ArrayList<>();
        Exception firstException = null;
        String failedTaskName = null;
        
        for (int i = 0; i < futures.size(); i++) {
            Future<TaskResult<?>> future = futures.get(i);
            Task<?> currentTask = tasks.get(i);
            
            try {
                TaskResult<?> result = future.get();
                results.add(result);
                
                // 一旦发现任务失败，立即取消所有其他任务
                if (!result.isSuccess()) {
                    firstException = result.getException();
                    failedTaskName = result.getTaskName();
                    break;
                }
            } catch (ExecutionException e) {
                // 处理 Future 本身的执行异常
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    firstException = (Exception) cause;
                } else {
                    firstException = new Exception(cause);
                }
                failedTaskName = currentTask.getName();
                break;
            }
        }
        
        // 如果发现失败，取消所有未完成的任务
        if (firstException != null) {
            for (int i = results.size(); i < futures.size(); i++) {
                // 使用false避免中断正在执行的任务（如数据库操作），只取消未开始执行的任务
                futures.get(i).cancel(false);
            }

            // 如果是自己创建的线程池，关闭它
            if (ownExecutor) {
                executorService.shutdown();
            }

            // 调用当前任务组的完成处理器（失败情况）
            handleCompletion(context, this.getName(), null, firstException);

            // 立即抛出异常，不继续收集结果
            throw firstException;
        }

        // 如果是自己创建的线程池，关闭它
        if (ownExecutor) {
            executorService.shutdown();
        }

        // 所有任务成功完成，调用当前任务组的完成处理器
        handleCompletion(context, this.getName(), results, null);

        return results;
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
     * 创建新的任务组
     */
    public static TaskGroup create() {
        return new TaskGroup();
    }

    /**
     * 创建带名称的任务组
     */
    public static TaskGroup create(String name) {
        return new TaskGroup(name);
    }

    /**
     * 添加任务的链式调用
     */
    @Override
    public TaskGroup addTask(Task<?> task) {
        super.addTask(task);
        return this;
    }

    /**
     * 设置异常处理器的链式调用
     */
    @Override
    public TaskGroup onException(ExceptionHandler handler) {
        super.onException(handler);
        return this;
    }

    /**
     * 设置完成处理器的链式调用
     */
    @Override
    public TaskGroup onComplete(CompletionHandler handler) {
        super.onComplete(handler);
        return this;
    }
}
