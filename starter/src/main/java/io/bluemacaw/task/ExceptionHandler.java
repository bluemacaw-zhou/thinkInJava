package cn.com.wind.IMStarter.task;

/**
 * 异常处理器接口
 */
@FunctionalInterface
public interface ExceptionHandler {
    /**
     * 处理任务执行过程中的异常
     * @param context 任务上下文
     * @param taskName 出现异常的任务名称
     * @param exception 异常对象
     * @throws Exception 处理过程中可能抛出的异常
     */
    void handle(TaskContext context, String taskName, Exception exception) throws Exception;

    /**
     * 创建一个什么都不做的异常处理器
     */
    static ExceptionHandler ignore() {
        return (context, taskName, exception) -> {
            // 忽略异常
        };
    }

    /**
     * 创建一个记录异常到上下文的处理器
     */
    static ExceptionHandler recordToContext(String key) {
        return (context, taskName, exception) -> {
            context.put(key, exception);
        };
    }

    /**
     * 创建一个抛出异常的处理器
     */
    static ExceptionHandler rethrow() {
        return (context, taskName, exception) -> {
            throw exception;
        };
    }
}
