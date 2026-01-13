package io.bluemacaw.mongodb.changestream;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Change Stream 事件路由器
 *
 * 负责将 Change Stream 事件路由到对应的处理器
 * 支持动态注册多个处理器
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
public class ChangeStreamManager {

    /**
     * 已注册的处理器列表
     * 使用 CopyOnWriteArrayList 保证线程安全
     */
    private final List<ChangeStreamHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * 注册处理器
     *
     * @param handler 处理器实例
     */
    public void registerHandler(ChangeStreamHandler handler) {
        handlers.add(handler);
        log.info("Registered ChangeStreamHandler: {}", handler.getHandlerName());
    }

    /**
     * 路由事件到对应的处理器
     *
     * @param collectionName collection 名称
     * @param changeEvent    Change Stream 事件
     */
    public void route(String collectionName, ChangeStreamDocument<Document> changeEvent) {
        String operationType = changeEvent.getOperationType().getValue();

        // 查找支持该 collection 的处理器
        boolean handled = false;
        for (ChangeStreamHandler handler : handlers) {
            if (handler.supports(collectionName)) {
                handled = true;
                try {
                    switch (operationType) {
                        case "insert":
                            handler.handleInsert(collectionName, changeEvent);
                            break;
                        case "update":
                            handler.handleUpdate(collectionName, changeEvent);
                            break;
                        case "delete":
                            handler.handleDelete(collectionName, changeEvent);
                            break;
                        case "replace":
                            handler.handleReplace(collectionName, changeEvent);
                            break;
                        default:
                            log.debug("Unhandled operation type: {} for collection: {}", operationType, collectionName);
                    }
                } catch (Exception e) {
                    log.error("Handler {} failed to process {} event for collection: {}",
                            handler.getHandlerName(), operationType, collectionName, e);
                    // 继续处理，不中断其他处理器
                }
            }
        }

        if (!handled) {
            log.warn("No handler found for collection: {}", collectionName);
        }
    }

    /**
     * 获取已注册的处理器数量
     *
     * @return 处理器数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * 获取所有已注册的处理器
     *
     * @return 处理器列表
     */
    public List<ChangeStreamHandler> getAllHandlers() {
        return handlers;
    }

    /**
     * 刷新所有处理器的缓存
     */
    public void flushAllHandlers() {
        for (ChangeStreamHandler handler : handlers) {
            try {
                handler.flush();
            } catch (Exception e) {
                log.error("Failed to flush handler: {}", handler.getHandlerName(), e);
            }
        }
    }
}
