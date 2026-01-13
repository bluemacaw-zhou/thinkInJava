package io.bluemacaw.mongodb.changestream;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.Document;

/**
 * Change Stream 事件处理器接口
 *
 * 不同的 collection 可以有不同的处理逻辑
 * 实现此接口并注册到 ChangeStreamRouter 即可
 *
 * @author shzhou.michael
 */
public interface ChangeStreamHandler {

    /**
     * 判断此处理器是否支持处理该 collection
     *
     * @param collectionName collection 名称
     * @return true 表示支持处理，false 表示不支持
     */
    boolean supports(String collectionName);

    /**
     * 处理插入事件
     *
     * @param collectionName collection 名称
     * @param changeEvent    Change Stream 事件
     */
    void handleInsert(String collectionName, ChangeStreamDocument<Document> changeEvent);

    /**
     * 处理更新事件
     *
     * @param collectionName collection 名称
     * @param changeEvent    Change Stream 事件
     */
    void handleUpdate(String collectionName, ChangeStreamDocument<Document> changeEvent);

    /**
     * 处理删除事件
     *
     * @param collectionName collection 名称
     * @param changeEvent    Change Stream 事件
     */
    void handleDelete(String collectionName, ChangeStreamDocument<Document> changeEvent);

    /**
     * 处理替换事件
     *
     * @param collectionName collection 名称
     * @param changeEvent    Change Stream 事件
     */
    void handleReplace(String collectionName, ChangeStreamDocument<Document> changeEvent);

    /**
     * 获取处理器名称（用于日志）
     *
     * @return 处理器名称
     */
    String getHandlerName();

    /**
     * 刷新缓存（发送累积的事件到 MQ）
     *
     * 在 Change Stream 处理结束时调用，确保所有缓存的事件都发送到 MQ
     */
    void flush();
}
