-- 使用数据库
USE message;

-- 查看当前所有表
SHOW TABLES FROM message;

-- 1. 删除旧的物化视图（必须先删除，因为它依赖于RabbitMQ队列表）
DROP VIEW IF EXISTS message.msg_analysis_mv;
DROP VIEW IF EXISTS message.msg_analysis_mv_single;
DROP VIEW IF EXISTS message.msg_analysis_mv_batch;

-- 2. 删除所有RabbitMQ消费表
DROP TABLE IF EXISTS message.msg_analysis_queue;
DROP TABLE IF EXISTS message.msg_analysis_queue_single;
DROP TABLE IF EXISTS message.msg_analysis_queue_batch;

-- 3. 删除目标数据表（警告：会删除所有历史数据！）
DROP TABLE IF EXISTS message.msg_analysis_data;

-- 4. 验证删除结果（应该显示为空或只剩其他无关表）
SHOW TABLES FROM message;

CREATE TABLE IF NOT EXISTS message.msg_analysis_data
(
    msgId Nullable(String) COMMENT '消息ID',
    fromId Int64 COMMENT '发送者ID',
    contactId Int64 COMMENT '联系人ID',
    sessionId Nullable(String) COMMENT '会话ID（小ID_大ID）',
    contactType Int32 COMMENT '联系类型',
    fromCompanyId Nullable(String) COMMENT '发送公司ID',
    fromCompany Nullable(String) COMMENT '发送公司名称',
    contactCompanyId Nullable(String) COMMENT '联系公司ID',
    contactCompany Nullable(String) COMMENT '联系公司名称',
    oldMsgId Nullable(String) COMMENT '旧消息ID',
    msgType Int32 COMMENT '消息类型',
    msgTime Nullable(String) COMMENT '消息时间(时间戳字符串)',
    deleted Nullable(Int32) COMMENT '删除标志',
    status Nullable(Int32) COMMENT '状态',
    content Nullable(String) COMMENT '消息内容',
    contentVersion Nullable(Int32) COMMENT '内容版本',
    clientMsgId Nullable(String) COMMENT '客户端消息ID',
    clientInfo Nullable(String) COMMENT '客户端信息',
    createTime Nullable(DateTime64(3)) COMMENT '创建时间',
    updateTime Nullable(DateTime64(3)) COMMENT '更新时间'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(toDateTime64(toInt64(ifNull(msgTime, '0'))/1000, 3))
ORDER BY (fromId, contactId, ifNull(msgTime, '0'))
SETTINGS index_granularity = 8192
COMMENT '消息分析数据表';
