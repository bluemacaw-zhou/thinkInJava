package io.bluemacaw.mongodb.util;

import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.Channel;
import io.bluemacaw.mongodb.entity.UserSubscription;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;

@Slf4j
public class PreparedStatementConverter {
    
    /**
     * 设置 ClickHouse PreparedStatement 参数 - Message 实体
     * 对应表结构：
     * (id, channelId, seq, oldMsgId,
     *  fromId, fromCompanyId, fromCompany,
     *  toId, toCompanyId, toCompany,
     *  contactType, msgType, content, contentVersion,
     *  msgTime, createTime,
     *  clientMsgId, clientInfo, deleted, status)
     * 
     * 注意：updateTime 字段由 ClickHouse 自动管理，不需要手动设置
     */
    public static void setMessageStatementParameters(PreparedStatement pstmt, Message message) throws Exception {
        int idx = 1;

        // 主键和标识字段
        pstmt.setString(idx++, message.getId());
        pstmt.setString(idx++, message.getChannelId());

        if (message.getSeq() != null) {
            pstmt.setLong(idx++, message.getSeq());
        } else {
            pstmt.setNull(idx++, java.sql.Types.BIGINT);
        }

        pstmt.setString(idx++, message.getOldMsgId());

        // 发送方信息
        pstmt.setLong(idx++, message.getFromId());
        pstmt.setString(idx++, message.getFromCompanyId());
        pstmt.setString(idx++, message.getFromCompany());

        // 接收方信息
        if (message.getToId() != null) {
            pstmt.setLong(idx++, message.getToId());
        } else {
            pstmt.setNull(idx++, java.sql.Types.BIGINT);
        }
        pstmt.setString(idx++, message.getToCompanyId());
        pstmt.setString(idx++, message.getToCompany());

        // 联系类型和消息类型
        pstmt.setInt(idx++, message.getContactType());
        pstmt.setInt(idx++, message.getMsgType());

        // 消息内容
        pstmt.setString(idx++, message.getContent());

        if (message.getContentVersion() != null) {
            pstmt.setInt(idx++, message.getContentVersion());
        } else {
            pstmt.setNull(idx++, java.sql.Types.INTEGER);
        }

        // 时间字段 - 设置 msgTime 和 createTime，updateTime 由 ClickHouse 自动管理
        if (message.getMsgTime() != null) {
            pstmt.setTimestamp(idx++, Timestamp.valueOf(message.getMsgTime()));
        } else {
            pstmt.setNull(idx++, java.sql.Types.TIMESTAMP);
        }

        // createTime - 使用 msgTime 作为创建时间，如果 msgTime 为空则使用当前时间
        if (message.getMsgTime() != null) {
            pstmt.setTimestamp(idx++, Timestamp.valueOf(message.getMsgTime()));
        } else {
            pstmt.setTimestamp(idx++, new Timestamp(System.currentTimeMillis()));
        }

        // 客户端信息
        pstmt.setString(idx++, message.getClientMsgId());
        pstmt.setString(idx++, message.getClientInfo());

        // 状态字段
        if (message.getDeleted() != null) {
            pstmt.setInt(idx++, message.getDeleted());
        } else {
            pstmt.setNull(idx++, java.sql.Types.INTEGER);
        }

        if (message.getStatus() != null) {
            pstmt.setInt(idx++, message.getStatus());
        } else {
            pstmt.setNull(idx++, java.sql.Types.INTEGER);
        }
    }
    
    /**
     * 设置 ClickHouse PreparedStatement 参数 - Channel 实体
     * 对应表结构：
     * (id, channelType, messageVersion)
     * 
     * 注意：createTime 和 updateTime 字段由 ClickHouse 自动管理，不需要手动设置
     */
    public static void setChannelStatementParameters(PreparedStatement pstmt, Channel channel) throws Exception {
        if (pstmt == null || channel == null) {
            throw new IllegalArgumentException("PreparedStatement 或 Channel 不能为 null");
        }

        int idx = 1;

        // 主键和标识字段
        pstmt.setString(idx++, channel.getId());

        // 频道类型
        if (channel.getChannelType() != null) {
            pstmt.setInt(idx++, channel.getChannelType());
        } else {
            pstmt.setNull(idx++, Types.INTEGER);
        }

        // 消息版本号
        if (channel.getMessageVersion() != null) {
            pstmt.setLong(idx++, channel.getMessageVersion());
        } else {
            pstmt.setNull(idx++, Types.BIGINT);
        }

        log.debug("Successfully set parameters for Channel, id: {}", channel.getId());
    }
    
    /**
     * 设置 ClickHouse PreparedStatement 参数 - UserSubscription 实体
     * 对应表结构：
     * (id, userId, channelId, channelType, 
     *  lastReadVersion, lastReadTime, 
     *  joinVersion, joinTime, 
     *  leaveVersion, leaveTime)
     * 
     * 注意：createTime 和 updateTime 字段由 ClickHouse 自动管理，不需要手动设置
     */
    public static void setUserSubscriptionStatementParameters(PreparedStatement pstmt, UserSubscription userSubscription) throws Exception {
        if (pstmt == null || userSubscription == null) {
            throw new IllegalArgumentException("PreparedStatement 或 UserSubscription 不能为 null");
        }

        int idx = 1;

        // 订阅ID
        pstmt.setString(idx++, userSubscription.getId());

        // 用户ID
        if (userSubscription.getUserId() != null) {
            pstmt.setLong(idx++, userSubscription.getUserId());
        } else {
            pstmt.setNull(idx++, Types.BIGINT);
        }

        // 频道ID
        pstmt.setString(idx++, userSubscription.getChannelId());

        // 频道类型 (必需字段，不能为null)
        pstmt.setInt(idx++, userSubscription.getChannelType());

        // 用户级已读版本号
        if (userSubscription.getLastReadVersion() != null) {
            pstmt.setLong(idx++, userSubscription.getLastReadVersion());
        } else {
            pstmt.setNull(idx++, Types.BIGINT);
        }

        // 最后已读时间
        if (userSubscription.getLastReadTime() != null) {
            pstmt.setTimestamp(idx++, Timestamp.valueOf(userSubscription.getLastReadTime()));
        } else {
            pstmt.setNull(idx++, Types.TIMESTAMP);
        }

        // 加入时的消息版本号 (必需字段，不能为null)
        pstmt.setLong(idx++, userSubscription.getJoinVersion());

        // 加入时间 (必需字段，不能为null)
        pstmt.setTimestamp(idx++, Timestamp.valueOf(userSubscription.getJoinTime()));

        // 离开时的消息版本号
        if (userSubscription.getLeaveVersion() != null) {
            pstmt.setLong(idx++, userSubscription.getLeaveVersion());
        } else {
            pstmt.setNull(idx++, Types.BIGINT);
        }

        // 退出时间
        if (userSubscription.getLeaveTime() != null) {
            pstmt.setTimestamp(idx++, Timestamp.valueOf(userSubscription.getLeaveTime()));
        } else {
            pstmt.setNull(idx++, Types.TIMESTAMP);
        }

        log.debug("Successfully set parameters for UserSubscription, id: {}", userSubscription.getId());
    }
}
