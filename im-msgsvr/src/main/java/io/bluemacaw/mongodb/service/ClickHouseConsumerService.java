package io.bluemacaw.mongodb.service;

import com.alibaba.fastjson.JSON;
import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ClickHouse 消费者服务
 * 从 RabbitMQ 消费消息并写入 ClickHouse
 */
@Slf4j
@Service
public class ClickHouseConsumerService {

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    /**
     * 消费批量消息队列
     * 使用自动确认模式（ackMode = "AUTO"）
     */
    @RabbitListener(queues = "${spring.rabbitmq.queueClickhouseBatch}", ackMode = "AUTO")
    public void consumeBatchMessage(Message message) {
        try {
            String jsonEachRow = new String(message.getBody(), "UTF-8");

            // 解析 JSONEachRow 格式（每行一个JSON，用换行符分隔）
            List<MsgAnalysisData> dataList = Arrays.stream(jsonEachRow.split("\n"))
                    .filter(line -> line != null && !line.trim().isEmpty())
                    .map(line -> {
                        try {
                            return JSON.parseObject(line, MsgAnalysisData.class);
                        } catch (Exception e) {
                            log.error("Failed to parse JSON line: {}", line, e);
                            return null;
                        }
                    })
                    .filter(data -> data != null)
                    .collect(Collectors.toList());

            if (dataList.isEmpty()) {
                log.warn("Parsed batch message is empty, skip");
                return;
            }

            // 批量插入 ClickHouse
            batchInsertToClickHouse(dataList);

            log.info("Successfully consumed and inserted batch message: size={}", dataList.size());

        } catch (Exception e) {
            log.error("Failed to consume batch message", e);
            throw new RuntimeException("Batch message consumption failed", e);
        }
    }

    /**
     * 单条插入 ClickHouse
     */
    private void insertToClickHouse(MsgAnalysisData data) {
        String sql = "INSERT INTO message.msg_analysis_data " +
                     "(msgId, fromId, contactId, sessionId, contactType, " +
                     "fromCompanyId, fromCompany, contactCompanyId, contactCompany, " +
                     "oldMsgId, msgType, msgTime, deleted, status, content, " +
                     "contentVersion, clientMsgId, clientInfo, createTime, updateTime) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setStatementParameters(pstmt, data);
            pstmt.executeUpdate();

        } catch (Exception e) {
            log.error("Failed to insert single message to ClickHouse, msgId={}", data.getMsgId(), e);
            throw new RuntimeException("ClickHouse insert failed", e);
        }
    }

    /**
     * 批量插入 ClickHouse
     */
    private void batchInsertToClickHouse(List<MsgAnalysisData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO message.msg_analysis_data " +
                     "(msgId, fromId, contactId, sessionId, contactType, " +
                     "fromCompanyId, fromCompany, contactCompanyId, contactCompany, " +
                     "oldMsgId, msgType, msgTime, deleted, status, content, " +
                     "contentVersion, clientMsgId, clientInfo, createTime, updateTime) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long startTime = System.currentTimeMillis();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 关闭自动提交，使用批处理
            conn.setAutoCommit(false);

            int batchSize = 1000; // 每1000条提交一次
            int count = 0;

            for (MsgAnalysisData data : dataList) {
                setStatementParameters(pstmt, data);
                pstmt.addBatch();
                count++;

                // 每1000条执行一次批量插入
                if (count % batchSize == 0) {
                    pstmt.executeBatch();
                    conn.commit();
                    log.debug("Committed {} records to ClickHouse", count);
                }
            }

            // 提交剩余的记录
            if (count % batchSize != 0) {
                pstmt.executeBatch();
                conn.commit();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch inserted {} records to ClickHouse in {} ms, avg {} ms/record",
                     dataList.size(), duration, duration / (double) dataList.size());

        } catch (Exception e) {
            log.error("Failed to batch insert to ClickHouse", e);
            throw new RuntimeException("ClickHouse batch insert failed", e);
        }
    }

    /**
     * 设置 PreparedStatement 参数
     */
    private void setStatementParameters(PreparedStatement pstmt, MsgAnalysisData data) throws Exception {
        pstmt.setString(1, data.getMsgId());
        pstmt.setLong(2, data.getFromId());
        pstmt.setLong(3, data.getContactId());
        pstmt.setString(4, data.getSessionId());
        pstmt.setInt(5, data.getContactType());
        pstmt.setString(6, data.getFromCompanyId());
        pstmt.setString(7, data.getFromCompany());
        pstmt.setString(8, data.getContactCompanyId());
        pstmt.setString(9, data.getContactCompany());
        pstmt.setString(10, data.getOldMsgId());
        pstmt.setInt(11, data.getMsgType());
        pstmt.setString(12, data.getMsgTime());

        // 处理 Nullable 字段
        if (data.getDeleted() != null) {
            pstmt.setInt(13, data.getDeleted());
        } else {
            pstmt.setNull(13, java.sql.Types.INTEGER);
        }

        if (data.getStatus() != null) {
            pstmt.setInt(14, data.getStatus());
        } else {
            pstmt.setNull(14, java.sql.Types.INTEGER);
        }

        pstmt.setString(15, data.getContent());

        if (data.getContentVersion() != null) {
            pstmt.setInt(16, data.getContentVersion());
        } else {
            pstmt.setNull(16, java.sql.Types.INTEGER);
        }

        pstmt.setString(17, data.getClientMsgId());
        pstmt.setString(18, data.getClientInfo());

        // 处理 LocalDateTime
        if (data.getCreateTime() != null) {
            pstmt.setTimestamp(19, Timestamp.valueOf(data.getCreateTime()));
        } else {
            pstmt.setNull(19, java.sql.Types.TIMESTAMP);
        }

        if (data.getUpdateTime() != null) {
            pstmt.setTimestamp(20, Timestamp.valueOf(data.getUpdateTime()));
        } else {
            pstmt.setNull(20, java.sql.Types.TIMESTAMP);
        }
    }
}
