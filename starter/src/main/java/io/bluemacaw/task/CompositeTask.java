package cn.com.wind.IMStarter.task;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合任务抽象类
 * 支持任务的组合和异常处理
 */
public abstract class CompositeTask<T> implements Task<T> {
    protected final List<Task<?>> tasks;
    protected ExceptionHandler exceptionHandler;
    protected CompletionHandler completionHandler;
    protected String name;

    public CompositeTask() {
        this.tasks = new ArrayList<>();
        this.exceptionHandler = ExceptionHandler.rethrow();
        this.completionHandler = CompletionHandler.doNothing();
    }

    public CompositeTask(String name) {
        this();
        this.name = name;
    }

    /**
     * 添加子任务
     * @param task 任务
     * @return 当前组合任务
     */
    public CompositeTask<T> addTask(Task<?> task) {
        tasks.add(task);
        return this;
    }

    /**
     * 设置异常处理器
     * @param handler 异常处理器
     * @return 当前组合任务
     */
    public CompositeTask<T> onException(ExceptionHandler handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * 设置完成处理器
     * 在任务完成时被调用，无论成功还是失败
     *
     * @param handler 完成处理器
     * @return 当前组合任务
     */
    public CompositeTask<T> onComplete(CompletionHandler handler) {
        this.completionHandler = handler;
        return this;
    }

    /**
     * 设置任务名称
     * @param name 名称
     * @return 当前组合任务
     */
    public CompositeTask<T> withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getName() {
        return name != null ? name : Task.super.getName();
    }

    /**
     * 处理任务执行异常
     * @param context 上下文
     * @param taskName 任务名称
     * @param exception 异常
     * @throws Exception 异常处理器抛出的异常
     */
    protected void handleException(TaskContext context, String taskName, Exception exception) throws Exception {
        exceptionHandler.handle(context, taskName, exception);
    }

    /**
     * 处理任务完成事件
     * 在任务成功或失败后被调用
     *
     * @param context 上下文
     * @param taskName 任务名称
     * @param result 任务执行结果（成功时有值，失败时为 null）
     * @param exception 任务执行异常（失败时有值，成功时为 null）
     */
    protected void handleCompletion(TaskContext context, String taskName, Object result, Exception exception) {
        try {
            completionHandler.handle(context, taskName, result, exception);
        } catch (Exception e) {
            // 忽略完成处理器的异常，避免影响主流程
            // 可以选择记录日志
        }
    }
}
