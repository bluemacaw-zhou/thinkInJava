# MongoDB 动态 Collection 使用指南

## 问题背景

在IM系统中,消息量巨大,需要按月分collection存储:
- `messages_202501` - 2025年1月的消息
- `messages_202502` - 2025年2月的消息
- `messages_202503` - 2025年3月的消息

## 为什么不能用 `@Document(collection = "#{表达式}")`

### 问题1: `@Document` 是编译时注解

```java
// ❌ 错误做法 - 这不会工作!
@Document(collection = "#{@message.getCollectionName()}")
public class MsgData {
    public String getCollectionName() {
        // 这个方法是实例方法,在类加载时无法调用
        return "messages_" + ...;
    }
}
```

**原因:**
- `@Document` 注解在类加载时就确定了collection名称
- 而 `getCollectionName()` 是实例方法,需要对象实例才能调用
- SpEL表达式 `#{}` 虽然可以引用Bean,但无法调用实例方法

### 问题2: 即使用静态方法也不行

```java
// ❌ 这种方式也有问题
@Document(collection = "#{T(io.bluemacaw.mongodb.util.CollectionNameUtil).getCurrentMessageCollection()}")
public class MsgData { }
```

**问题:**
- 只能获取当前时间的collection名称
- 查询历史消息时,需要根据消息时间动态确定collection
- 这种方式无法满足需求

## 正确的解决方案

### 方案: 使用 MongoTemplate 动态指定 Collection

```java
// 1. 实体类不指定具体的collection名称
@Document  // 不指定collection属性
public class MsgData {
    // 实体字段...
}

// 2. 使用MongoTemplate在运行时指定collection
String collectionName = CollectionNameUtil.getMessageCollection(LocalDateTime.now());
mongoTemplate.insert(message, collectionName);  // 保存到指定collection

// 3. 查询时也指定collection
List<MsgData> messages = mongoTemplate.find(query, MsgData.class, collectionName);
```

## 完整示例

### 1. Collection名称工具类

```java
public class CollectionNameUtil {
    private static final String MESSAGE_COLLECTION_PREFIX = "messages_";

    // 获取当前月份的collection
    public static String getCurrentMessageCollection() {
        return MESSAGE_COLLECTION_PREFIX + YearMonth.now().format("yyyyMM");
    }

    // 根据时间获取collection
    public static String getMessageCollection(LocalDateTime dateTime) {
        return MESSAGE_COLLECTION_PREFIX + dateTime.format("yyyyMM");
    }

    // 获取时间范围内的所有collection
    public static List<String> getMessageCollectionRange(
            LocalDateTime startDate, LocalDateTime endDate) {
        // 返回 [messages_202501, messages_202502, messages_202503]
    }
}
```

### 2. 消息发送 - 动态保存

```java
@Service
public class MessageService {
    @Resource
    private MongoTemplate mongoTemplate;

    public MsgData sendMessage(...) {
        LocalDateTime now = LocalDateTime.now();

        // 构建消息对象
        MsgData message = new MsgData();
        message.setContent("消息内容");
        message.setCreateTime(now);

        // 动态确定collection并保存
        String collectionName = CollectionNameUtil.getMessageCollection(now);
        MsgData savedMessage = mongoTemplate.insert(message, collectionName);

        return savedMessage;
    }
}
```

### 3. 消息查询 - 跨月查询

```java
public List<MsgData> queryMessages(String channelId,
                                    Long sinceVersion,
                                    Long untilVersion,
                                    LocalDateTime startDate,
                                    LocalDateTime endDate) {
    // 获取需要查询的所有collection
    List<String> collections = CollectionNameUtil.getMessageCollectionRange(
        startDate, endDate
    );
    // 输出: [messages_202412, messages_202501, messages_202502]

    List<MsgData> allMessages = new ArrayList<>();

    // 遍历每个collection查询
    for (String collection : collections) {
        Query query = Query.query(
            Criteria.where("channel_id").is(channelId)
                    .and("seq").gt(sinceVersion)
                    .and("seq").lte(untilVersion)
        );

        // 从指定collection查询
        List<MsgData> messages = mongoTemplate.find(
            query, MsgData.class, collection
        );
        allMessages.addAll(messages);
    }

    // 跨collection查询后需要重新排序
    allMessages.sort((m1, m2) -> m1.getSeq().compareTo(m2.getSeq()));

    return allMessages;
}
```

### 4. 历史消息分页查询

```java
public List<MsgData> queryHistoryMessages(String channelId,
                                           Long cursorVersion,
                                           int limit,
                                           int maxMonths) {
    LocalDateTime now = LocalDateTime.now();
    List<MsgData> allMessages = new ArrayList<>();

    // 从当前月往前查询最多maxMonths个月
    for (int i = 0; i < maxMonths; i++) {
        LocalDateTime monthDate = now.minusMonths(i);
        String collection = CollectionNameUtil.getMessageCollection(monthDate);

        Query query = Query.query(
            Criteria.where("channel_id").is(channelId)
                    .and("seq").lt(cursorVersion)
        );
        query.with(Sort.by("seq").descending());
        query.limit(limit - allMessages.size());

        List<MsgData> messages = mongoTemplate.find(
            query, MsgData.class, collection
        );
        allMessages.addAll(messages);

        if (allMessages.size() >= limit) {
            break; // 凑够了就停止
        }
    }

    return allMessages;
}
```

## 核心要点总结

### ✅ 正确做法

1. **实体类**: 不指定或留空collection名称
   ```java
   @Document  // 不写collection属性
   public class MsgData { }
   ```

2. **保存时**: 通过MongoTemplate指定collection
   ```java
   mongoTemplate.insert(message, "messages_202501");
   ```

3. **查询时**: 通过MongoTemplate指定collection
   ```java
   mongoTemplate.find(query, MsgData.class, "messages_202501");
   ```

4. **跨月查询**: 遍历多个collection
   ```java
   for (String collection : ["messages_202412", "messages_202501"]) {
       mongoTemplate.find(query, MsgData.class, collection);
   }
   ```

### ❌ 错误做法

```java
// 错误1: 试图用实例方法
@Document(collection = "#{@message.getCollectionName()}")

// 错误2: 用静态方法但无法动态变化
@Document(collection = "#{T(Util).getCurrentCollection()}")

// 错误3: 硬编码collection名称
@Document(collection = "messages_202501")  // 无法按月分collection
```

## 性能优化建议

### 1. 索引创建

为每个按月分collection创建必要的索引:

```java
@PostConstruct
public void ensureIndexes() {
    String collection = CollectionNameUtil.getCurrentMessageCollection();

    // 创建复合索引
    mongoTemplate.indexOps(collection)
        .ensureIndex(new Index()
            .on("channel_id", Sort.Direction.ASC)
            .on("seq", Sort.Direction.ASC)
            .unique());
}
```

### 2. 限制查询月份数

```java
// 避免一次性查询太多月份
int MAX_QUERY_MONTHS = 3;  // 最多查询3个月
```

### 3. 使用批量插入

```java
List<MsgData> messages = ...;
String collection = CollectionNameUtil.getCurrentMessageCollection();
mongoTemplate.insert(messages, collection);  // 批量插入
```

## 数据清理策略

### 自动删除旧collection

```java
@Scheduled(cron = "0 0 2 1 * ?")  // 每月1号凌晨2点执行
public void cleanOldCollections() {
    LocalDateTime threshold = LocalDateTime.now().minusYears(2);
    String oldCollection = CollectionNameUtil.getMessageCollection(threshold);

    if (mongoTemplate.collectionExists(oldCollection)) {
        mongoTemplate.dropCollection(oldCollection);
        log.info("删除旧collection: {}", oldCollection);
    }
}
```

## 常见问题

### Q1: 为什么不用 `@Document(collection = "固定名称")`?
A: 因为需要按月分collection,无法用固定名称。

### Q2: MongoTemplate会不会性能差?
A: 不会。MongoTemplate底层也是MongoDB Driver,性能相同。

### Q3: 如何处理月末跨月的消息?
A: 按消息的createTime确定collection,每条消息只存储在一个collection中。

### Q4: 如何保证查询的完整性?
A:
- 明确查询的时间范围
- 获取该时间范围内的所有collection
- 遍历查询并合并结果
- 按seq重新排序

### Q5: Repository还能用吗?
A: Repository适合固定collection的场景,动态collection建议直接用MongoTemplate。

## 总结

动态collection的核心是:
1. **不在@Document中固定collection名称**
2. **使用MongoTemplate在运行时指定collection**
3. **提供工具类统一管理collection名称生成逻辑**
4. **查询时考虑跨collection的情况**

这种方式完全满足IM系统按月分collection的需求! 🎉
