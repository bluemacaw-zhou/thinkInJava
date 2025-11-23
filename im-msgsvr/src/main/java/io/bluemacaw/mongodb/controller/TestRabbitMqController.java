package io.bluemacaw.mongodb.controller;

import com.alibaba.fastjson.JSON;
import io.bluemacaw.mongodb.config.RabbitmqConfig;
import io.bluemacaw.mongodb.entity.MqMsgItem;
import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import io.bluemacaw.mongodb.entity.MsgData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/testRabbitmq")
public class TestRabbitMqController {
    @Resource
    private RabbitmqConfig config;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    // 批量发送控制标志
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private final AtomicLong sentCount = new AtomicLong(0);

    // ClickHouse批量插入控制标志
    private final AtomicBoolean isInserting = new AtomicBoolean(false);
    private final AtomicLong insertedCount = new AtomicLong(0);

    // 人员池（10个人，Wind占70%）
    private static final Person[] PERSON_POOL = {
        new Person(100001L, "Wind"),
        new Person(100002L, "Wind"),
        new Person(100003L, "Wind"),
        new Person(100004L, "Wind"),
        new Person(100005L, "Wind"),
        new Person(100006L, "Wind"),
        new Person(100007L, "Wind"),
        new Person(100008L, "复旦大学"),
        new Person(100009L, "交通大学"),
        new Person(100010L, "同济大学")
    };

    // 联系人ID池 - 群聊（5个群）
    private static final Long[] GROUP_CONTACT_IDS = {
        300001L, 300002L, 300003L, 300004L, 300005L
    };

    // 消息类型池（0=文本占70%权重）
    private static final Integer[] MSG_TYPES = {
        0, 0, 0, 0, 0, 0, 0,  // 70% 文本消息
        1, 2, 5, 6            // 30% 其他类型（图片、文件、语音、视频）
    };

    private final Random random = new Random();

    /**
     * 发送单条实时消息
     * GET /testRabbitmq/sendMessage
     */
    @GetMapping("/sendMessage")
    public String sendMessage() {
        MqMsgItem mqMsgItem = new MqMsgItem();
        mqMsgItem.setMsgType(1);

        MsgData msgData = new MsgData();
        msgData.setFromId(117304503);
        msgData.setContactId(116377569);
        msgData.setContactType(0);
        msgData.setFromCompanyId("");
        msgData.setFromCompany("北京市通州区第二中学");
        msgData.setContactCompanyId("");
        msgData.setContactCompany("Wind");
        msgData.setOldMsgId("1-06FDECB7:06EFC7E1:52897665:20000219{1|117304503}1");
        msgData.setMsgType(0);
        msgData.setMsgTime("1761617602129");
        msgData.setDeleted(0);
        msgData.setStatus(0);
        msgData.setContent("1.1|0|ODlEIDExMjUxNDAxNS5JQiAyNeaxn+iLj+mTtuihjENEMDE1IEJJRC8tLSAtLS8tLQ0K|5b6u6L2v6ZuF6buR|14|0|0|");
        msgData.setContentVersion(1);
        msgData.setClientMsgId("LC-1761617601839126400_2-117304503-116377569");
        msgData.setClientInfo("PC/Windows");

        mqMsgItem.setMsgData(msgData);

        // 发送到单条消息队列（实时数据）
        sendMessageToQueue(config.getExchangeMessage(),
                          config.getRouteMessage(),
                          JSON.toJSONString(mqMsgItem));

        return "ok";
    }

    /**
     * 批量发送测试消息（当前时间）
     * GET /testRabbitmq/sendMessageBatchTest
     */
    @GetMapping("/sendMessageBatchTest")
    public Map<String, Object> sendMessageBatchTest() {
        Map<String, Object> response = new HashMap<>();

        try {
            LocalDate today = LocalDate.now();
            List<MessageData> messages = new ArrayList<>();

            // 生成100条随机消息
            for (int i = 0; i < 100; i++) {
                MessageData msgData = generateRandomMessage(today);
                messages.add(msgData);
            }

            // 发送所有消息
            for (MessageData msgData : messages) {
                sendGeneratedMessage(msgData);
            }

            response.put("success", true);
            response.put("message", "Successfully sent test messages");
            response.put("count", messages.size());
            response.put("timestamp", System.currentTimeMillis());

            log.info("Sent {} test messages", messages.size());

        } catch (Exception e) {
            log.error("Failed to send test messages", e);
            response.put("success", false);
            response.put("message", "Failed to send test messages: " + e.getMessage());
        }

        return response;
    }


    /**
     * 发送消息到指定队列
     */
    private void sendMessageToQueue(String exchangeName, String routeKey, String messageContent) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);

        rabbitTemplate.send(
                exchangeName,
                routeKey,
                new Message(messageContent.getBytes(StandardCharsets.UTF_8), messageProperties));
    }

    /**
     * 批量发送历史数据
     * GET /testRabbitmq/sendMessageBatch?days=7&batchSize=1000&delayMs=10
     *
     * @param days 时间跨度天数（默认92天，即3个月）
     * @param batchSize 每批发送的消息数量（默认1000）
     * @param delayMs 每批之间的延迟毫秒数（默认100ms）
     * @return 状态信息
     */
    @GetMapping("/sendMessageBatch")
    public Map<String, Object> sendMessageBatch(
            @RequestParam(defaultValue = "92") int days,
            @RequestParam(defaultValue = "1000") int batchSize,
            @RequestParam(defaultValue = "100") int delayMs) {

        Map<String, Object> response = new HashMap<>();

        if (days <= 0 || days > 365) {
            response.put("success", false);
            response.put("message", "Invalid days parameter, must be between 1 and 365");
            return response;
        }

        if (!isSending.compareAndSet(false, true)) {
            response.put("success", false);
            response.put("message", "Batch sending is already running");
            response.put("sentCount", sentCount.get());
            return response;
        }

        sentCount.set(0);
        final int finalDays = days;

        // 在新线程中执行批量发送
        new Thread(() -> {
            try {
                log.info("Starting batch send: days={}, batchSize={}, delayMs={}", finalDays, batchSize, delayMs);

                // 生成时间分布的消息列表
                List<MessageData> messages = generateMessagesWithTimeDistribution(finalDays);
                log.info("Generated {} messages for {} days", messages.size(), finalDays);

                // 按批次发送
                int totalMessages = messages.size();
                for (int i = 0; i < totalMessages; i += batchSize) {
                    if (!isSending.get()) {
                        log.info("Batch send stopped by user");
                        break;
                    }

                    int end = Math.min(i + batchSize, totalMessages);
                    for (int j = i; j < end; j++) {
                        MessageData msgData = messages.get(j);
                        sendGeneratedMessage(msgData);
                        sentCount.incrementAndGet();
                    }

                    long sent = sentCount.get();
                    if (sent % 10000 == 0) {
                        log.info("Batch send progress: {}/{} messages sent", sent, totalMessages);
                    }

                    // 批次间延迟
                    if (end < totalMessages && delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }

                log.info("Batch send completed: {} messages sent", sentCount.get());

            } catch (Exception e) {
                log.error("Batch send error", e);
            } finally {
                isSending.set(false);
            }
        }).start();

        response.put("success", true);
        response.put("message", "Batch sending started");
        response.put("days", days);
        response.put("batchSize", batchSize);
        response.put("delayMs", delayMs);

        // 预估消息量
        int workDays = (int) (days * 5.0 / 7.0); // 约5/7是工作日
        int weekendDays = days - workDays;
        long estimatedMessages = (long) workDays * 200000 + (long) weekendDays * 1000;
        response.put("estimatedMessages", estimatedMessages);

        return response;
    }

    /**
     * 生成带时间分布的消息列表
     * 工作日：每天20万条消息
     * 休息日：每天1000条消息
     *
     * @param days 时间跨度天数
     * @return 消息列表
     */
    private List<MessageData> generateMessagesWithTimeDistribution(int days) {
        List<MessageData> messages = new ArrayList<>();

        // 从今天往前推指定天数
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        log.info("Generating messages from {} to {} ({} days)", startDate, endDate, days);

        LocalDate currentDate = startDate;
        int dayCount = 0;
        while (!currentDate.isAfter(endDate)) {
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            int messagesPerDay = isWeekend ? 1000 : 200000;

            // 为这一天生成消息
            for (int i = 0; i < messagesPerDay; i++) {
                MessageData msgData = generateRandomMessage(currentDate);
                messages.add(msgData);
            }

            dayCount++;
            if (dayCount % 10 == 0) {
                log.info("Generated data for {} days, total messages: {}", dayCount, messages.size());
            }

            currentDate = currentDate.plusDays(1);
        }

        // 打乱消息顺序，使发送更真实
        Collections.shuffle(messages);

        return messages;
    }

    /**
     * 为指定日期生成指定数量的消息
     * @param date 日期
     * @param messageCount 消息数量
     * @return 消息列表
     */
    private List<MessageData> generateMessagesForDay(LocalDate date, int messageCount) {
        List<MessageData> messages = new ArrayList<>(messageCount);
        for (int i = 0; i < messageCount; i++) {
            messages.add(generateRandomMessage(date));
        }
        return messages;
    }

    /**
     * 生成一条随机消息
     */
    private MessageData generateRandomMessage(LocalDate date) {
        MessageData data = new MessageData();

        // 随机选择发送者
        Person sender = PERSON_POOL[random.nextInt(PERSON_POOL.length)];
        data.fromId = sender.id;
        data.fromCompany = sender.companyName;
        data.fromCompanyId = "";

        // 根据概率选择私聊或群聊（私聊60% vs 群聊40%）
        boolean isPrivateChat = random.nextInt(100) < 60;

        if (isPrivateChat) {
            // 私聊：从人员池中随机选择一个人作为接收者
            Person receiver = PERSON_POOL[random.nextInt(PERSON_POOL.length)];
            data.contactId = receiver.id;
            data.contactType = 0;
            data.contactCompany = receiver.companyName;
            data.contactCompanyId = "";
        } else {
            // 群聊：从群聊池中选择
            data.contactId = GROUP_CONTACT_IDS[random.nextInt(GROUP_CONTACT_IDS.length)];
            data.contactType = 1;
            data.contactCompany = "";
            data.contactCompanyId = "";
        }

        // 随机消息类型（文本占70%）
        data.msgType = MSG_TYPES[random.nextInt(MSG_TYPES.length)];

        // 生成当天的随机时间戳
        LocalDateTime dateTime = date.atTime(
            random.nextInt(24),
            random.nextInt(60),
            random.nextInt(60)
        );
        data.msgTime = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 生成唯一的oldMsgId
        data.oldMsgId = String.format("1-%08X:%08X:%08X:%08X{1|%d}%d",
            random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(),
            sender.id, System.nanoTime());

        // 生成内容
        data.content = generateMessageContent(data.msgType);

        // 生成clientMsgId
        data.clientMsgId = String.format("LC-%d_%d-%d-%d",
            data.msgTime, random.nextInt(10), sender.id, data.contactId);

        data.clientInfo = "PC/Windows";

        return data;
    }

    /**
     * 根据消息类型生成内容
     */
    private String generateMessageContent(int msgType) {
        switch (msgType) {
            case 0: // 文本
                String[] textTemplates = {
                    "Hello, this is a test message",
                    "Meeting at 3 PM today",
                    "Please review the document",
                    "Great work on the project!",
                    "Can we schedule a call?",
                    "Thanks for your help",
                    "Looking forward to the presentation"
                };
                return "1.1|0|" + Base64.getEncoder().encodeToString(
                    textTemplates[random.nextInt(textTemplates.length)].getBytes()) + "|text|14|0|0|";

            case 1: // 图片
                return "1.1|1|aW1hZ2VfZGF0YQ==|image.jpg|14|0|0|";

            case 2: // 文件
                return "1.1|2|ZmlsZV9kYXRh|document.pdf|14|0|0|";

            case 5: // 语音
                return "1.1|5|dm9pY2VfZGF0YQ==|voice.mp3|14|0|0|";

            case 6: // 视频
                return "1.1|6|dmlkZW9fZGF0YQ==|video.mp4|14|0|0|";

            default:
                return "1.1|0|dGVzdA==|text|14|0|0|";
        }
    }

    /**
     * 发送生成的消息
     */
    private void sendGeneratedMessage(MessageData data) {
        MqMsgItem mqMsgItem = new MqMsgItem();
        mqMsgItem.setMsgType(1);

        MsgData msgData = new MsgData();
        msgData.setFromId(data.fromId);
        msgData.setContactId(data.contactId);
        msgData.setContactType(data.contactType);
        msgData.setFromCompanyId(data.fromCompanyId);
        msgData.setFromCompany(data.fromCompany);
        msgData.setContactCompanyId(data.contactCompanyId);
        msgData.setContactCompany(data.contactCompany);
        msgData.setOldMsgId(data.oldMsgId);
        msgData.setMsgType(data.msgType);
        msgData.setMsgTime(String.valueOf(data.msgTime));
        msgData.setDeleted(0);
        msgData.setStatus(0);
        msgData.setContent(data.content);
        msgData.setContentVersion(1);
        msgData.setClientMsgId(data.clientMsgId);
        msgData.setClientInfo(data.clientInfo);

        mqMsgItem.setMsgData(msgData);

        // 发送到批量消息队列（历史数据导入）
        sendMessageToQueue(config.getExchangeMessage(),
                          config.getRouteMessageBatch(),
                          JSON.toJSONString(mqMsgItem));
    }

    /**
     * 内部类：人员信息
     */
    private static class Person {
        Long id;
        String companyName;

        Person(Long id, String companyName) {
            this.id = id;
            this.companyName = companyName;
        }
    }

    /**
     * 内部类：消息数据
     */
    private static class MessageData {
        Long fromId;
        Long contactId;
        Integer contactType;
        String fromCompanyId;
        String fromCompany;
        String contactCompanyId;
        String contactCompany;
        String oldMsgId;
        Integer msgType;
        Long msgTime;
        String content;
        String clientMsgId;
        String clientInfo;
    }

    /**
     * 批量插入测试数据到ClickHouse（高性能版本）
     * GET /testRabbitmq/batchInsertClickHouse?days=92&batchSize=5000&threadCount=10
     *
     * @param days 时间跨度天数（默认92天，即3个月）
     * @param batchSize 每批插入的消息数量（默认5000，可调整为更大值提升性能）
     * @param threadCount 并发插入线程数（默认10，根据CPU核心数调整）
     * @return 状态信息
     */
    @GetMapping("/batchInsertClickHouse")
    public Map<String, Object> batchInsertClickHouse(
            @RequestParam(defaultValue = "92") int days,
            @RequestParam(defaultValue = "5000") int batchSize,
            @RequestParam(defaultValue = "10") int threadCount) {

        Map<String, Object> response = new HashMap<>();

        if (days <= 0 || days > 365) {
            response.put("success", false);
            response.put("message", "Invalid days parameter, must be between 1 and 365");
            return response;
        }

        if (batchSize < 100 || batchSize > 50000) {
            response.put("success", false);
            response.put("message", "Invalid batchSize parameter, must be between 100 and 50000");
            return response;
        }

        if (threadCount < 1 || threadCount > 50) {
            response.put("success", false);
            response.put("message", "Invalid threadCount parameter, must be between 1 and 50");
            return response;
        }

        if (!isInserting.compareAndSet(false, true)) {
            response.put("success", false);
            response.put("message", "Batch inserting is already running");
            response.put("insertedCount", insertedCount.get());
            return response;
        }

        insertedCount.set(0);
        final int finalDays = days;
        final int finalBatchSize = batchSize;
        final int finalThreadCount = threadCount;

        // 在新线程中执行批量插入
        new Thread(() -> {
            ExecutorService executor = null;
            try {
                long startTime = System.currentTimeMillis();
                log.info("Starting ClickHouse batch insert: days={}, batchSize={}, threadCount={}",
                         finalDays, finalBatchSize, finalThreadCount);

                // 创建线程池进行并发插入
                executor = Executors.newFixedThreadPool(finalThreadCount);

                // 计算日期范围
                LocalDate endDate = LocalDate.now();
                LocalDate startDate = endDate.minusDays(finalDays - 1);

                log.info("Inserting data from {} to {} ({} days)", startDate, endDate, finalDays);

                // 按天生成和插入数据，避免内存占用过大
                LocalDate currentDate = startDate;
                int dayCount = 0;

                while (!currentDate.isAfter(endDate)) {
                    if (!isInserting.get()) {
                        log.info("Batch insert stopped by user");
                        break;
                    }

                    DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
                    boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
                    int messagesPerDay = isWeekend ? 1000 : 200000;

                    // 为当天生成消息列表
                    final LocalDate dateToGenerate = currentDate;
                    List<MessageData> dayMessages = generateMessagesForDay(dateToGenerate, messagesPerDay);

                    dayCount++;
                    log.info("Generated {} messages for day {} ({}/{}), isWeekend={}",
                             dayMessages.size(), dateToGenerate, dayCount, finalDays, isWeekend);

                    // 将当天的消息分片给多个线程并发插入
                    int messagesPerThread = (messagesPerDay + finalThreadCount - 1) / finalThreadCount;
                    List<Runnable> tasks = new ArrayList<>();

                    for (int i = 0; i < finalThreadCount; i++) {
                        int startIdx = i * messagesPerThread;
                        int endIdx = Math.min(startIdx + messagesPerThread, dayMessages.size());

                        if (startIdx >= dayMessages.size()) {
                            break;
                        }

                        List<MessageData> threadMessages = new ArrayList<>(dayMessages.subList(startIdx, endIdx));
                        final int threadId = i;

                        tasks.add(() -> {
                            try {
                                insertMessagesToClickHouse(threadMessages, finalBatchSize, threadId);
                            } catch (Exception e) {
                                log.error("Thread {} failed to insert messages for date {}", threadId, dateToGenerate, e);
                            }
                        });
                    }

                    // 提交所有任务并等待完成
                    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                    for (Runnable task : tasks) {
                        futures.add(executor.submit(task));
                    }

                    // 等待当天所有任务完成
                    for (java.util.concurrent.Future<?> future : futures) {
                        future.get();
                    }

                    // 释放内存
                    dayMessages.clear();

                    long currentInserted = insertedCount.get();
                    long elapsed = System.currentTimeMillis() - startTime;
                    double avgThroughput = (currentInserted * 1000.0) / elapsed;

                    log.info("Day {}/{} completed, total inserted: {}, avg throughput: {:.2f} msg/s",
                             dayCount, finalDays, currentInserted, avgThroughput);

                    currentDate = currentDate.plusDays(1);
                }

                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.MINUTES);

                long duration = System.currentTimeMillis() - startTime;
                long totalInserted = insertedCount.get();
                double throughput = (totalInserted * 1000.0) / duration;

                log.info("ClickHouse batch insert completed: {} messages inserted in {} ms, throughput: {:.2f} msg/s",
                         totalInserted, duration, throughput);

            } catch (Exception e) {
                log.error("ClickHouse batch insert error", e);
            } finally {
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdownNow();
                }
                isInserting.set(false);
            }
        }).start();

        response.put("success", true);
        response.put("message", "ClickHouse batch inserting started");
        response.put("days", days);
        response.put("batchSize", batchSize);
        response.put("threadCount", threadCount);

        // 预估消息量
        int workDays = (int) (days * 5.0 / 7.0);
        int weekendDays = days - workDays;
        long estimatedMessages = (long) workDays * 200000 + (long) weekendDays * 1000;
        response.put("estimatedMessages", estimatedMessages);

        return response;
    }

    /**
     * 停止批量插入
     * GET /testRabbitmq/stopBatchInsert
     */
    @GetMapping("/stopBatchInsert")
    public Map<String, Object> stopBatchInsert() {
        Map<String, Object> response = new HashMap<>();

        if (isInserting.compareAndSet(true, false)) {
            response.put("success", true);
            response.put("message", "Batch inserting stopped");
            response.put("insertedCount", insertedCount.get());
        } else {
            response.put("success", false);
            response.put("message", "No batch inserting is running");
        }

        return response;
    }

    /**
     * 获取批量插入状态
     * GET /testRabbitmq/batchInsertStatus
     */
    @GetMapping("/batchInsertStatus")
    public Map<String, Object> batchInsertStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("isInserting", isInserting.get());
        response.put("insertedCount", insertedCount.get());
        return response;
    }

    /**
     * 将消息列表批量插入到ClickHouse（高性能版本）
     * 使用JDBC批处理和连接复用优化性能
     */
    private void insertMessagesToClickHouse(List<MessageData> messages, int batchSize, int threadId) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO message.msg_analysis_data " +
                     "(msgId, fromId, contactId, sessionId, contactType, " +
                     "fromCompanyId, fromCompany, contactCompanyId, contactCompany, " +
                     "oldMsgId, msgType, msgTime, deleted, status, content, " +
                     "contentVersion, clientMsgId, clientInfo, createTime, updateTime) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long threadStartTime = System.currentTimeMillis();
        int totalProcessed = 0;

        try (Connection conn = clickHouseDataSource.getConnection()) {
            // 关闭自动提交，使用批处理
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int count = 0;

                for (MessageData msgData : messages) {
                    if (!isInserting.get()) {
                        log.info("Thread {} stopped by user request", threadId);
                        break;
                    }

                    // 转换为 MsgAnalysisData
                    MsgAnalysisData analysisData = convertToAnalysisData(msgData);

                    // 设置参数
                    setClickHouseStatementParameters(pstmt, analysisData);
                    pstmt.addBatch();
                    count++;
                    totalProcessed++;

                    // 每达到batchSize条执行一次批量插入
                    if (count >= batchSize) {
                        pstmt.executeBatch();
                        conn.commit();

                        long inserted = insertedCount.addAndGet(count);

                        // 每10万条记录打印一次进度
                        if (inserted % 100000 == 0 || (inserted / count) % 10 == 0) {
                            long elapsed = System.currentTimeMillis() - threadStartTime;
                            double threadThroughput = (totalProcessed * 1000.0) / elapsed;
                            log.info("Thread {} progress: inserted {} records, throughput: {:.2f} msg/s, total: {}",
                                     threadId, count, threadThroughput, inserted);
                        }

                        count = 0;
                    }
                }

                // 提交剩余的记录
                if (count > 0) {
                    pstmt.executeBatch();
                    conn.commit();
                    insertedCount.addAndGet(count);
                }

                long threadDuration = System.currentTimeMillis() - threadStartTime;
                double threadThroughput = (totalProcessed * 1000.0) / threadDuration;
                log.info("Thread {} completed: {} messages inserted in {} ms, throughput: {:.2f} msg/s",
                         threadId, totalProcessed, threadDuration, threadThroughput);

            }
        } catch (Exception e) {
            log.error("Thread {} failed to insert messages to ClickHouse", threadId, e);
            throw new RuntimeException("ClickHouse batch insert failed for thread " + threadId, e);
        }
    }

    /**
     * 转换 MessageData 为 MsgAnalysisData
     */
    private MsgAnalysisData convertToAnalysisData(MessageData msgData) {
        MsgAnalysisData analysisData = new MsgAnalysisData();

        analysisData.setMsgId(UUID.randomUUID().toString().replace("-", ""));
        analysisData.setFromId(msgData.fromId);
        analysisData.setContactId(msgData.contactId);
        analysisData.setContactType(msgData.contactType);
        analysisData.setFromCompanyId(msgData.fromCompanyId);
        analysisData.setFromCompany(msgData.fromCompany);
        analysisData.setContactCompanyId(msgData.contactCompanyId);
        analysisData.setContactCompany(msgData.contactCompany);
        analysisData.setOldMsgId(msgData.oldMsgId);
        analysisData.setMsgType(msgData.msgType);
        analysisData.setMsgTime(String.valueOf(msgData.msgTime));
        analysisData.setDeleted(0);
        analysisData.setStatus(0);
        analysisData.setContent(msgData.content);
        analysisData.setContentVersion(1);
        analysisData.setClientMsgId(msgData.clientMsgId);
        analysisData.setClientInfo(msgData.clientInfo);

        // 生成 sessionId
        analysisData.generateSessionId();

        // 设置时间戳
        LocalDateTime now = LocalDateTime.now();
        analysisData.setCreateTime(now);
        analysisData.setUpdateTime(now);

        return analysisData;
    }

    /**
     * 设置 ClickHouse PreparedStatement 参数
     */
    private void setClickHouseStatementParameters(PreparedStatement pstmt, MsgAnalysisData data) throws Exception {
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
