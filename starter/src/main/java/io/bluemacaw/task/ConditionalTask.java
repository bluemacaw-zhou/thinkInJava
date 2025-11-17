package cn.com.wind.IMStarter.task;

import cn.com.wind.IMStarter.task.trace.TaskTrace;
import java.util.function.Predicate;

/**
 * 条件任务
 * 根据条件决定是否执行任务
 */
public class ConditionalTask<T> implements Task<T> {
    private final Predicate<TaskContext> condition;
    private final Task<T> task;
    private final Task<T> elseTask;
    private final String name;

    public ConditionalTask(Predicate<TaskContext> condition, Task<T> task, Task<T> elseTask) {
        this.condition = condition;
        this.task = task;
        this.elseTask = elseTask;
        // Initial name format: Conditional[ConditionalTask]
        this.name = "Conditional[ConditionalTask]";
    }

    public ConditionalTask(Predicate<TaskContext> condition, Task<T> task) {
        this(condition, task, null);
    }

    @Override
    public T execute(TaskContext context) throws Exception {
        try {
            boolean conditionResult = condition.test(context);
            Task<T> taskToExecute;
            String actualTaskName;
            
            if (conditionResult) {
                taskToExecute = task;
                actualTaskName = task.getName();
            } else if (elseTask != null) {
                taskToExecute = elseTask;
                actualTaskName = elseTask.getName();
            } else {
                return null;
            }
            
            // Get current trace and update display name to reflect the actual executing task
            if (context.getExecutionTrace() != null && context.getCurrentTraceId() != null) {
                TaskTrace currentTrace = context.getExecutionTrace().getTrace(context.getCurrentTraceId());
                if (currentTrace != null) {
                    // Update display name to Conditional[actual task name]
                    currentTrace.setDisplayName("Conditional[" + actualTaskName + "]");
                }
            }
            
            return taskToExecute.execute(context);
        } catch (Exception e) {
            // 重新抛出异常，确保异常传播链完整
            throw e;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * 创建条件任务
     * @param condition 条件
     * @param task 满足条件时执行的任务
     * @return 条件任务
     */
    public static <T> ConditionalTask<T> when(Predicate<TaskContext> condition, Task<T> task) {
        return new ConditionalTask<>(condition, task);
    }

    /**
     * Create conditional task (with else branch)
     * @param condition 条件
     * @param task 满足条件时执行的任务
     * @param elseTask Task executed when condition is not met
     * @return 条件任务
     */
    public static <T> ConditionalTask<T> when(Predicate<TaskContext> condition, Task<T> task, Task<T> elseTask) {
        return new ConditionalTask<>(condition, task, elseTask);
    }
}
