package cn.com.wind.IMStarter.task;

/**
 * 带降级处理的任务包装器
 * 当主任务失败时，执行降级任务
 */
public class FallbackTask<T> implements Task<T> {
    private final Task<T> primaryTask;
    private final Task<T> fallbackTask;
    private final String name;

    public FallbackTask(Task<T> primaryTask, Task<T> fallbackTask) {
        this.primaryTask = primaryTask;
        this.fallbackTask = fallbackTask;
        this.name = "Fallback[" + primaryTask.getName() + " -> " + fallbackTask.getName() + "]";
    }

    @Override
    public T execute(TaskContext context) throws Exception {
        try {
            T result = primaryTask.execute(context);
            // 主任务成功，记录未使用降级
            context.put("used_fallback_" + this.getName(), false);
            return result;
        } catch (Exception e) {
            // 记录主任务失败信息
            context.put("primary_task_error_" + primaryTask.getName(), e);
            // 标记使用了降级
            context.put("used_fallback_" + this.getName(), true);

            // 执行降级任务
            try {
                return fallbackTask.execute(context);
            } catch (Exception fallbackException) {
                // 降级任务也失败，抛出包含两个异常信息的新异常
                Exception combinedException = new Exception(
                    "Both primary and fallback tasks failed. Primary: " + e.getMessage() +
                    ", Fallback: " + fallbackException.getMessage()
                );
                combinedException.addSuppressed(e);
                combinedException.addSuppressed(fallbackException);
                throw combinedException;
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * 创建带降级的任务
     * @param primaryTask 主任务
     * @param fallbackTask 降级任务
     * @return 降级任务包装器
     */
    public static <T> FallbackTask<T> of(Task<T> primaryTask, Task<T> fallbackTask) {
        return new FallbackTask<>(primaryTask, fallbackTask);
    }
}
