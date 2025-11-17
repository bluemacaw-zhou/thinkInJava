package cn.com.wind.IMStarter.task.trace;

/**
 * 任务执行状态
 */
public enum TaskStatus {
    RUNNING,    // 运行中
    SUCCESS,    // 成功
    FAILED,     // 失败
    SKIPPED     // 跳过（条件任务未满足条件）
}
