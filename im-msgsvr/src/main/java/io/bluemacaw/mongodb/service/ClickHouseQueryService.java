package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse查询服务
 */
@Slf4j
@Service
public class ClickHouseQueryService {

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    /**
     * 查询消息总数
     */
    public long getTotalCount() {
        String sql = "SELECT count(*) as total FROM message.msg_analysis_data";

        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("total");
            }
        } catch (SQLException e) {
            log.error("Error querying total count from ClickHouse", e);
        }
        return 0;
    }

    /**
     * 查询最近的消息列表
     *
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<MsgAnalysisData> getRecentMessages(int limit) {
        String sql = "SELECT * FROM message.msg_analysis_data ORDER BY createTime DESC LIMIT ?";
        List<MsgAnalysisData> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToMsgAnalysisData(rs));
                }
            }

            log.info("Query recent messages, limit: {}, result size: {}", limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying recent messages from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据发送者ID查询消息
     *
     * @param fromId 发送者ID
     * @param limit  限制数量
     * @return 消息列表
     */
    public List<MsgAnalysisData> getMessagesByFromId(long fromId, int limit) {
        String sql = "SELECT * FROM message.msg_analysis_data WHERE fromId = ? ORDER BY createTime DESC LIMIT ?";
        List<MsgAnalysisData> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, fromId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToMsgAnalysisData(rs));
                }
            }

            log.info("Query messages by fromId: {}, limit: {}, result size: {}", fromId, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by fromId from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据联系人ID查询消息
     *
     * @param contactId 联系人ID
     * @param limit     限制数量
     * @return 消息列表
     */
    public List<MsgAnalysisData> getMessagesByContactId(long contactId, int limit) {
        String sql = "SELECT * FROM message.msg_analysis_data WHERE contactId = ? ORDER BY createTime DESC LIMIT ?";
        List<MsgAnalysisData> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, contactId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToMsgAnalysisData(rs));
                }
            }

            log.info("Query messages by contactId: {}, limit: {}, result size: {}", contactId, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by contactId from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据时间范围查询消息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return 消息列表
     */
    public List<MsgAnalysisData> getMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        String sql = "SELECT * FROM message.msg_analysis_data WHERE createTime BETWEEN ? AND ? ORDER BY createTime DESC LIMIT ?";
        List<MsgAnalysisData> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(startTime));
            pstmt.setTimestamp(2, Timestamp.valueOf(endTime));
            pstmt.setInt(3, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToMsgAnalysisData(rs));
                }
            }

            log.info("Query messages by time range: {} to {}, limit: {}, result size: {}",
                    startTime, endTime, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by time range from ClickHouse", e);
        }

        return result;
    }

    /**
     * 按日期统计消息数量
     *
     * @param days 最近天数
     * @return 日期统计数据
     */
    public List<Map<String, Object>> getMessageCountByDate(int days) {
        String sql = "SELECT toDate(createTime) as date, count(*) as count " +
                "FROM message.msg_analysis_data " +
                "WHERE createTime >= now() - INTERVAL ? DAY " +
                "GROUP BY date " +
                "ORDER BY date DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, days);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getDate("date"));
                    row.put("count", rs.getLong("count"));
                    result.add(row);
                }
            }

            log.info("Query message count by date, days: {}, result size: {}", days, result.size());

        } catch (SQLException e) {
            log.error("Error querying message count by date from ClickHouse", e);
        }

        return result;
    }

    /**
     * 按用户统计消息数量
     *
     * @param limit 限制数量
     * @return 用户统计数据
     */
    public List<Map<String, Object>> getTopSenders(int limit) {
        String sql = "SELECT fromId, count(*) as msg_count " +
                "FROM message.msg_analysis_data " +
                "GROUP BY fromId " +
                "ORDER BY msg_count DESC " +
                "LIMIT ?";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("fromId", rs.getLong("fromId"));
                    row.put("msgCount", rs.getLong("msg_count"));
                    result.add(row);
                }
            }

            log.info("Query top senders, limit: {}, result size: {}", limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying top senders from ClickHouse", e);
        }

        return result;
    }

    /**
     * 执行自定义SQL查询
     *
     * @param sql 自定义SQL语句
     * @return 查询结果
     */
    public List<Map<String, Object>> executeCustomQuery(String sql) {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                result.add(row);
            }

            log.info("Execute custom query, result size: {}", result.size());

        } catch (SQLException e) {
            log.error("Error executing custom query from ClickHouse: {}", sql, e);
        }

        return result;
    }

    /**
     * 将ResultSet映射为MsgAnalysisData对象
     */
    private MsgAnalysisData mapResultSetToMsgAnalysisData(ResultSet rs) throws SQLException {
        MsgAnalysisData data = new MsgAnalysisData();

        data.setMsgId(rs.getString("msgId"));
        data.setFromId(rs.getLong("fromId"));
        data.setContactId(rs.getLong("contactId"));
        data.setContactType(rs.getInt("contactType"));
        data.setFromCompanyId(rs.getString("fromCompanyId"));
        data.setFromCompany(rs.getString("fromCompany"));
        data.setContactCompanyId(rs.getString("contactCompanyId"));
        data.setContactCompany(rs.getString("contactCompany"));
        data.setOldMsgId(rs.getString("oldMsgId"));
        data.setMsgType(rs.getInt("msgType"));
        data.setMsgTime(rs.getString("msgTime"));

        // 处理Nullable字段
        int deleted = rs.getInt("deleted");
        data.setDeleted(rs.wasNull() ? null : deleted);

        int status = rs.getInt("status");
        data.setStatus(rs.wasNull() ? null : status);

        data.setContent(rs.getString("content"));

        int contentVersion = rs.getInt("contentVersion");
        data.setContentVersion(rs.wasNull() ? null : contentVersion);

        data.setClientMsgId(rs.getString("clientMsgId"));
        data.setClientInfo(rs.getString("clientInfo"));

        // 处理时间字段
        Timestamp createTime = rs.getTimestamp("createTime");
        if (createTime != null) {
            data.setCreateTime(createTime.toLocalDateTime());
        }

        Timestamp updateTime = rs.getTimestamp("updateTime");
        if (updateTime != null) {
            data.setUpdateTime(updateTime.toLocalDateTime());
        }

        return data;
    }
}
