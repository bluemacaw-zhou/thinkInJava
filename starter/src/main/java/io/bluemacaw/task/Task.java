package cn.com.wind.IMStarter.task;

/**
 * 任务接口
 * @param <T> 任务执行结果类型
 */
@FunctionalInterface
public interface Task<T> {
    /**
     * 执行任务
     * @param context 任务上下文
     * @return 任务执行结果
     * @throws Exception 执行过程中可能抛出的异常
     */
    T execute(TaskContext context) throws Exception;

    /**
     * 获取任务名称
     * @return 任务名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
