package cn.com.wind.IMStarter.task;

import cn.com.wind.IMStarter.task.trace.ExecutionTrace;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的任务上下文
 * 用于在任务之间共享数据
 */
public class TaskContext {
    private final ConcurrentHashMap<String, Object> data;
    private final String contextId;
    private final ExecutionTrace executionTrace;
    private final ThreadLocal<String> currentTraceId;

    public TaskContext() {
        this.data = new ConcurrentHashMap<>();
        // 从 MDC 中获取 traceId，如果不存在或为空则自动生成
        this.contextId = getOrGenerateContextId();
        this.executionTrace = new ExecutionTrace(contextId);
        this.currentTraceId = new ThreadLocal<>();
    }

    /**
     * 从 MDC 中获取 contextId，如果不存在或为空则生成新的
     */
    private String getOrGenerateContextId() {
        // 尝试从 MDC 中获取 traceId（按优先级尝试多个常用 key）
        String mdcTraceId = getMdcTraceId();

        if (mdcTraceId != null && !mdcTraceId.trim().isEmpty()) {
            return mdcTraceId;
        }

        // MDC 中没有 traceId，生成新的 UUID
        String generatedId = java.util.UUID.randomUUID().toString();

        // 将生成的 ID 设置回 MDC（如果 MDC 为空的话，方便后续日志使用）
        if (mdcTraceId == null) {
            MDC.put("traceId", generatedId);
        }

        return generatedId;
    }

    /**
     * 从 MDC 中读取 traceId（尝试多个常用的 key）
     */
    private String getMdcTraceId() {
        // 按优先级尝试常见的 trace ID key
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId;
        }

        traceId = MDC.get("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId;
        }

        traceId = MDC.get("X-Request-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId;
        }

        traceId = MDC.get("requestId");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId;
        }

        return null;
    }

    /**
     * 设置上下文数据
     * @param key 键
     * @param value 值
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取上下文数据
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * 获取上下文数据（Optional封装）
     * @param key 键
     * @return Optional封装的值
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional(String key) {
        return Optional.ofNullable((T) data.get(key));
    }

    /**
     * 获取上下文数据，如果不存在则返回默认值
     * @param key 键
     * @param defaultValue 默认值
     * @return 值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    /**
     * 检查是否包含指定键
     * @param key 键
     * @return 是否包含
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * 移除指定键的数据
     * @param key 键
     * @return 被移除的值
     */
    @SuppressWarnings("unchecked")
    public <T> T remove(String key) {
        return (T) data.remove(key);
    }

    /**
     * 清空上下文数据
     */
    public void clear() {
        data.clear();
    }

    /**
     * 获取所有数据的只读视图
     * @return 数据的只读Map
     */
    public Map<String, Object> getAll() {
        return new ConcurrentHashMap<>(data);
    }

    /**
     * 获取上下文ID
     * @return 上下文ID
     */
    public String getContextId() {
        return contextId;
    }

    /**
     * 获取执行追踪
     * @return 执行追踪
     */
    public ExecutionTrace getExecutionTrace() {
        return executionTrace;
    }

    /**
     * 设置当前线程的追踪ID
     * @param traceId 追踪ID
     */
    public void setCurrentTraceId(String traceId) {
        currentTraceId.set(traceId);
    }

    /**
     * 获取当前线程的追踪ID
     * @return 追踪ID
     */
    public String getCurrentTraceId() {
        return currentTraceId.get();
    }

    /**
     * 清除当前线程的追踪ID
     */
    public void clearCurrentTraceId() {
        currentTraceId.remove();
    }

    @Override
    public String toString() {
        return "TaskContext{" +
                "contextId='" + contextId + '\'' +
                ", data=" + data +
                '}';
    }
}
