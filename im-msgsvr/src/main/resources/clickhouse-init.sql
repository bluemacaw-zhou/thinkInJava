-- IM消息系统 ClickHouse 数据库初始化脚本
-- 说明：本脚本用于创建消息表，用于存储从MongoDB通过Change Stream同步的消息数据

-- 创建数据库
CREATE DATABASE IF NOT EXISTS im_message;

-- 使用数据库
USE im_message;

-- 查看当前所有表
SHOW TABLES FROM im_message;

-- ============================================================
-- 清理旧表（如果需要重新初始化）
-- 警告：以下操作会删除所有历史数据！
-- ============================================================

-- 删除旧表
DROP TABLE IF EXISTS im_message.message;

-- 验证删除结果
SHOW TABLES FROM im_message;

-- ============================================================
-- 创建消息表
-- ============================================================
-- 时间字段说明：
-- 1. createTime: 使用 DEFAULT now64(3)，在插入记录时自动设置为当前时间
-- 2. updateTime: 使用 MATERIALIZED now64(3)，每次记录变更时自动更新为当前时间
-- 3. msgTime: 消息的实际发送时间，由应用程序指定，不自动更新

CREATE TABLE IF NOT EXISTS im_message.message
(
    -- ============ 主键和标识字段 ============
    id String COMMENT 'MongoDB消息ID (ObjectId)',
    channelId String COMMENT '频道ID (私聊: userId1_userId2, 群聊: groupId)',
    seq Int64 COMMENT '消息序号 (频道内单调递增，等于Channel.message_version)',
    oldMsgId Nullable(String) COMMENT '现行的消息唯一ID (兼容旧系统)',

    -- ============ 发送方信息 ============
    fromId Int64 COMMENT '发送者用户ID',
    fromCompanyId Nullable(String) COMMENT '发送者公司ID (快照)',
    fromCompany Nullable(String) COMMENT '发送者公司名称 (快照)',

    -- ============ 接收方信息 ============
    toId Nullable(Int64) COMMENT '接收者用户ID (私聊场景，群聊为null)',
    toCompanyId Nullable(String) COMMENT '接收者公司ID (私聊场景，群聊为null)',
    toCompany Nullable(String) COMMENT '接收者公司名称 (私聊场景，群聊为null)',

    -- ============ 联系类型 ============
    contactType Int32 COMMENT '联系类型 (0=私聊, 1=群聊)',

    -- ============ 消息内容 ============
    msgType Int32 COMMENT '消息类型 (文本/图片/语音等)',
    content Nullable(String) COMMENT '消息内容',
    contentVersion Nullable(Int32) COMMENT '消息协议版本',

    -- ============ 时间字段 ============
    msgTime DateTime64(3) COMMENT '消息时间 (毫秒级)',
    createTime DateTime64(3) DEFAULT now64(3) COMMENT '创建时间',
    updateTime DateTime64(3) MATERIALIZED now64(3) COMMENT '更新时间 (撤回时会更新)',

    -- ============ 客户端信息 ============
    clientMsgId Nullable(String) COMMENT '客户端消息ID (用于去重)',
    clientInfo Nullable(String) COMMENT '客户端信息',

    -- ============ 状态字段 ============
    deleted Nullable(Int32) COMMENT '删除标记 (0=正常, 1=已删除)',
    status Nullable(Int32) COMMENT '消息状态 (包含撤回状态)'
)
ENGINE = MergeTree()
-- 按月分区 (基于消息时间)
PARTITION BY toYYYYMM(msgTime)
-- 排序键：优化查询性能
-- 1. channelId: 按频道查询消息
-- 2. seq: 频道内消息排序
-- 3. fromId: 按发送者查询
ORDER BY (channelId, seq)
-- 主键：唯一标识消息
PRIMARY KEY (channelId, seq)
-- 索引粒度
SETTINGS index_granularity = 8192
COMMENT 'IM消息表 - 存储从MongoDB同步的消息数据';

-- ============================================================
-- 创建索引以优化查询性能
-- ============================================================

-- 注意：MergeTree引擎的ORDER BY已经创建了主索引
-- 以下是一些可选的跳数索引，用于优化特定查询场景

-- 1. fromId跳数索引 (优化按发送者查询)
-- ALTER TABLE im_message.message
-- ADD INDEX idx_from_id fromId TYPE minmax GRANULARITY 4;

-- 2. toId跳数索引 (优化按接收者查询)
-- ALTER TABLE im_message.message
-- ADD INDEX idx_to_id toId TYPE minmax GRANULARITY 4;

-- 3. msgTime跳数索引 (优化按时间范围查询)
-- ALTER TABLE im_message.message
-- ADD INDEX idx_msg_time msgTime TYPE minmax GRANULARITY 4;

-- 4. fromCompanyId跳数索引 (优化按公司查询)
-- ALTER TABLE im_message.message
-- ADD INDEX idx_from_company_id fromCompanyId TYPE bloom_filter(0.01) GRANULARITY 4;

-- 5. clientMsgId跳数索引 (优化去重查询)
-- ALTER TABLE im_message.message
-- ADD INDEX idx_client_msg_id clientMsgId TYPE bloom_filter(0.01) GRANULARITY 4;

-- ============================================================
-- 验证表创建结果
-- ============================================================

-- 查看表结构
DESC im_message.message;

-- 查看表的详细信息
SHOW CREATE TABLE im_message.message;

-- ============================================================
-- 常用查询示例
-- ============================================================

-- 1. 查询某个频道的最新消息
-- SELECT * FROM im_message.message
-- WHERE channelId = 'channel_123'
-- ORDER BY seq DESC
-- LIMIT 20;

-- 2. 查询某个用户发送的消息
-- SELECT * FROM im_message.message
-- WHERE fromId = 12345
-- ORDER BY msgTime DESC
-- LIMIT 50;

-- 3. 查询某个公司的消息统计
-- SELECT
--     fromCompanyId,
--     fromCompany,
--     count() as msg_count,
--     countIf(msgType = 1) as text_count,
--     countIf(msgType = 2) as image_count
-- FROM im_message.message
-- WHERE fromCompanyId = 'company_123'
-- GROUP BY fromCompanyId, fromCompany;

-- 4. 查询某个时间段的消息量
-- SELECT
--     toDate(msgTime) as date,
--     count() as msg_count
-- FROM im_message.message
-- WHERE msgTime >= '2025-01-01' AND msgTime < '2025-02-01'
-- GROUP BY date
-- ORDER BY date;

-- 5. 查询频道消息序号连续性（检测丢失的消息）
-- SELECT
--     channelId,
--     seq,
--     seq - lagInFrame(seq) OVER (PARTITION BY channelId ORDER BY seq) AS gap
-- FROM im_message.message
-- WHERE channelId = 'channel_123'
-- HAVING gap > 1
-- ORDER BY seq;

-- ============================================================
-- 创建频道表
-- ============================================================
-- 时间字段说明：
-- 1. createTime: 使用 DEFAULT now64(3)，在插入记录时自动设置为当前时间
-- 2. updateTime: 使用 MATERIALIZED now64(3)，每次记录变更时自动更新为当前时间

CREATE TABLE IF NOT EXISTS im_message.channel
(
    -- ============ 主键和标识字段 ============
    id String COMMENT '频道ID (私聊: userId1_userId2, 群聊: group_groupId)',

    -- ============ 频道类型 ============
    channelType Int32 COMMENT '频道类型 (0=私聊, 1=群聊)',

    -- ============ 消息版本号 ============
    messageVersion Int64 COMMENT '消息版本号 (每条新消息时+1)',

    -- ============ 时间字段 ============
    createTime DateTime64(3) DEFAULT now64(3) COMMENT '创建时间',
    updateTime DateTime64(3) MATERIALIZED now64(3) COMMENT '更新时间 - 最新消息时间,用于频道排序'
)
ENGINE = MergeTree()
-- 按月分区 (基于更新时间)
PARTITION BY toYYYYMM(updateTime)
-- 排序键：优化查询性能
-- 1. id: 按频道ID查询
-- 2. channelType: 按频道类型查询
-- 3. updateTime: 按更新时间排序
ORDER BY (id, channelType, updateTime)
-- 主键：唯一标识频道
PRIMARY KEY (id)
-- 索引粒度
SETTINGS index_granularity = 8192
COMMENT '频道表 - 存储从MongoDB同步的频道数据';

-- ============================================================
-- 创建用户订阅表
-- ============================================================
-- 时间字段说明：
-- 1. createTime: 使用 DEFAULT now64(3)，在插入记录时自动设置为当前时间
-- 2. updateTime: 使用 MATERIALIZED now64(3)，每次记录变更时自动更新为当前时间

CREATE TABLE IF NOT EXISTS im_message.user_subscription
(
    -- ============ 主键和标识字段 ============
    id String COMMENT '订阅ID (MongoDB ObjectId)',

    -- ============ 用户和频道信息 ============
    userId Int64 COMMENT '用户ID',
    channelId String COMMENT '频道ID',
    channelType Int32 COMMENT '频道类型 (冗余,避免JOIN)',

    -- ============ 已读进度 ============
    lastReadVersion Nullable(Int64) COMMENT '用户级已读版本号(跨设备共享)',
    lastReadTime Nullable(DateTime64(3)) COMMENT '最后已读时间(用于查询撤回消息)',

    -- ============ 可见性范围 ============
    joinVersion Int64 COMMENT '加入时的消息版本号(可见性起点)',
    joinTime DateTime64(3) COMMENT '加入时间',
    leaveVersion Nullable(Int64) COMMENT '离开时的消息版本号(可见性终点)',
    leaveTime Nullable(DateTime64(3)) COMMENT '退出时间',

    -- ============ 时间字段 ============
    createTime DateTime64(3) DEFAULT now64(3) COMMENT '创建时间',
    updateTime DateTime64(3) MATERIALIZED now64(3) COMMENT '更新时间'
)
ENGINE = MergeTree()
-- 按月分区 (基于更新时间)
PARTITION BY toYYYYMM(updateTime)
-- 排序键：优化查询性能
-- 1. userId: 按用户查询
-- 2. channelId: 按频道查询
-- 3. updateTime: 按更新时间排序
ORDER BY (userId, channelId, updateTime)
-- 索引粒度
SETTINGS index_granularity = 8192
COMMENT '用户订阅表 - 存储从MongoDB同步的用户订阅数据';

-- ============================================================
-- 验证表创建结果
-- ============================================================

-- 查看频道表结构
DESC im_message.channel;

-- 查看频道表的详细信息
SHOW CREATE TABLE im_message.channel;

-- 查看用户订阅表结构
DESC im_message.user_subscription;

-- 查看用户订阅表的详细信息
SHOW CREATE TABLE im_message.user_subscription;

-- ============================================================
-- 常用查询示例 - 频道表
-- ============================================================

-- 1. 查询某个用户的所有私聊频道
-- SELECT * FROM im_message.channel
-- WHERE id LIKE '12345_%' OR id LIKE '%_12345'
-- AND channelType = 0
-- ORDER BY updateTime DESC;

-- 2. 查询活跃的群聊频道（按消息版本排序）
-- SELECT * FROM im_message.channel
-- WHERE channelType = 1
-- ORDER BY messageVersion DESC
-- LIMIT 20;

-- 3. 查询某个时间范围内创建的频道
-- SELECT
--     channelType,
--     count() as channel_count
-- FROM im_message.channel
-- WHERE createTime >= '2025-01-01' AND createTime < '2025-02-01'
-- GROUP BY channelType;

-- ============================================================
-- 常用查询示例 - 用户订阅表
-- ============================================================

-- 1. 查询用户订阅的所有频道
-- SELECT
--     us.userId,
--     us.channelId,
--     c.channelType,
--     us.lastReadVersion,
--     c.messageVersion,
--     c.messageVersion - us.lastReadVersion as unread_count
-- FROM im_message.user_subscription us
-- LEFT JOIN im_message.channel c ON us.channelId = c.id
-- WHERE us.userId = 12345
-- AND us.leaveTime IS NULL
-- ORDER BY us.updateTime DESC;

-- 2. 查询某个频道的所有订阅用户
-- SELECT
--     userId,
--     lastReadVersion,
--     joinTime,
--     lastReadTime
-- FROM im_message.user_subscription
-- WHERE channelId = '12345_67890'
-- AND leaveTime IS NULL
-- ORDER BY joinTime;

-- 3. 查询有未读消息的用户
-- SELECT
--     us.userId,
--     us.channelId,
--     c.channelType,
--     c.messageVersion - us.lastReadVersion as unread_count
-- FROM im_message.user_subscription us
-- LEFT JOIN im_message.channel c ON us.channelId = c.id
-- WHERE us.leaveTime IS NULL
-- AND c.messageVersion > us.lastReadVersion
-- ORDER BY unread_count DESC;

-- 4. 查询用户订阅统计
-- SELECT
--     userId,
--     count() as total_channels,
--     countIf(channelType = 0) as private_channels,
--     countIf(channelType = 1) as group_channels,
--     countIf(leaveTime IS NULL) as active_channels
-- FROM im_message.user_subscription
-- GROUP BY userId
-- ORDER BY total_channels DESC;
