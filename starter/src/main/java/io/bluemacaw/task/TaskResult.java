package cn.com.wind.IMStarter.task;

import java.util.Optional;

/**
 * 任务执行结果
 * @param <T> 结果类型
 */
public class TaskResult<T> {
    private final T result;
    private final Exception exception;
    private final boolean success;
    private final String taskName;

    private TaskResult(T result, Exception exception, boolean success, String taskName) {
        this.result = result;
        this.exception = exception;
        this.success = success;
        this.taskName = taskName;
    }

    public static <T> TaskResult<T> success(T result, String taskName) {
        return new TaskResult<>(result, null, true, taskName);
    }

    public static <T> TaskResult<T> failure(Exception exception, String taskName) {
        return new TaskResult<>(null, exception, false, taskName);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public T getResult() {
        return result;
    }

    public Optional<T> getResultOptional() {
        return Optional.ofNullable(result);
    }

    public Exception getException() {
        return exception;
    }

    public Optional<Exception> getExceptionOptional() {
        return Optional.ofNullable(exception);
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public String toString() {
        return "TaskResult{" +
                "success=" + success +
                ", taskName='" + taskName + '\'' +
                ", result=" + result +
                ", exception=" + exception +
                '}';
    }
}
