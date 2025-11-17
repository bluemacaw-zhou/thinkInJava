-- ClickHouse数据库初始化脚本
-- 使用说明: 连接到ClickHouse后执行此脚本

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS message;

-- 2. 创建目标表(MergeTree引擎，用于持久化存储)
CREATE TABLE IF NOT EXISTS message.msg_analysis_data
(
    msgId String COMMENT '消息ID',
    fromId Int64 COMMENT '发送者ID',
    contactId Int64 COMMENT '联系人ID',
    contactType Int32 COMMENT '联系类型',
    fromCompanyId String COMMENT '发送公司ID',
    fromCompany String COMMENT '发送公司名称',
    contactCompanyId String COMMENT '联系公司ID',
    contactCompany String COMMENT '联系公司名称',
    oldMsgId String COMMENT '旧消息ID',
    msgType Int32 COMMENT '消息类型',
    msgTime String COMMENT '消息时间(时间戳字符串)',
    deleted Nullable(Int32) COMMENT '删除标志',
    status Nullable(Int32) COMMENT '状态',
    content String COMMENT '消息内容',
    contentVersion Nullable(Int32) COMMENT '内容版本',
    clientMsgId String COMMENT '客户端消息ID',
    clientInfo String COMMENT '客户端信息',
    createTime DateTime64(3) COMMENT '创建时间',
    updateTime DateTime64(3) COMMENT '更新时间'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(toDateTime64(toInt64(msgTime)/1000, 3))
ORDER BY (fromId, contactId, msgTime)
SETTINGS index_granularity = 8192
COMMENT '消息分析数据表';

-- 3. 创建RabbitMQ消费表(用于从RabbitMQ消费消息)
CREATE TABLE IF NOT EXISTS message.msg_analysis_queue
(
    msgId String,
    fromId Int64,
    contactId Int64,
    contactType Int32,
    fromCompanyId String,
    fromCompany String,
    contactCompanyId String,
    contactCompany String,
    oldMsgId String,
    msgType Int32,
    msgTime String,
    deleted Nullable(Int32),
    status Nullable(Int32),
    content String,
    contentVersion Nullable(Int32),
    clientMsgId String,
    clientInfo String,
    createTime DateTime64(3),
    updateTime DateTime64(3)
)
ENGINE = RabbitMQ
SETTINGS
    rabbitmq_host_port = '192.168.254.129:5672',
    rabbitmq_exchange_name = 'ex.clickhouse.analysis',
    rabbitmq_exchange_type = 'direct',
    rabbitmq_routing_key_list = 'route.clickhouse.analysis',
    rabbitmq_format = 'JSONEachRow',
    rabbitmq_username = 'admin',
    rabbitmq_password = 'admin',
    rabbitmq_queue_base = 'queue.clickhouse.analysis',
    rabbitmq_num_consumers = 1,
    rabbitmq_max_block_size = 1000,
    rabbitmq_flush_interval_ms = 1000,
    rabbitmq_num_queues = 1
COMMENT 'RabbitMQ消费队列表';

-- 4. 创建物化视图(自动将RabbitMQ表的数据写入MergeTree表)
CREATE MATERIALIZED VIEW IF NOT EXISTS message.msg_analysis_mv TO message.msg_analysis_data
AS SELECT
    msgId,
    fromId,
    contactId,
    contactType,
    fromCompanyId,
    fromCompany,
    contactCompanyId,
    contactCompany,
    oldMsgId,
    msgType,
    msgTime,
    deleted,
    status,
    content,
    contentVersion,
    clientMsgId,
    clientInfo,
    createTime,
    updateTime
FROM message.msg_analysis_queue;

-- 5. 验证创建结果
SHOW TABLES FROM message;

-- 查询示例
-- SELECT count(*) FROM message.msg_analysis_data;
-- SELECT * FROM message.msg_analysis_data LIMIT 10;
