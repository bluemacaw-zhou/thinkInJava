package cn.com.wind.IMStarter.task;

/**
 * 任务完成处理器接口
 * 在任务完成时被调用，无论成功还是失败
 *
 * 调用时机：
 * - 成功时：在任务成功执行后调用，result 不为 null，exception 为 null
 * - 失败时：在 onException 处理器执行后调用，result 为 null，exception 不为 null
 *
 * 典型用途：
 * - 记录执行统计信息
 * - 清理资源
 * - 发送通知
 * - 更新状态
 */
@FunctionalInterface
public interface CompletionHandler {
    /**
     * 处理任务完成事件
     *
     * @param context 任务上下文
     * @param taskName 任务名称
     * @param result 任务执行结果（成功时有值，失败时为 null）
     * @param exception 任务执行异常（失败时有值，成功时为 null）
     */
    void handle(TaskContext context, String taskName, Object result, Exception exception);

    /**
     * 创建一个什么都不做的完成处理器
     */
    static CompletionHandler doNothing() {
        return (context, taskName, result, exception) -> {
            // 不做任何处理
        };
    }

    /**
     * 创建一个记录完成状态到上下文的处理器
     *
     * @param key 记录到上下文的键名
     */
    static CompletionHandler recordToContext(String key) {
        return (context, taskName, result, exception) -> {
            if (exception == null) {
                context.put(key + "_status", "success");
                context.put(key + "_result", result);
            } else {
                context.put(key + "_status", "failed");
                context.put(key + "_error", exception);
            }
        };
    }

    /**
     * 组合多个完成处理器
     * 按顺序依次执行所有处理器
     *
     * @param handlers 要组合的处理器列表
     */
    static CompletionHandler compose(CompletionHandler... handlers) {
        return (context, taskName, result, exception) -> {
            for (CompletionHandler handler : handlers) {
                try {
                    handler.handle(context, taskName, result, exception);
                } catch (Exception e) {
                    // 忽略完成处理器的异常，避免影响主流程
                }
            }
        };
    }
}
