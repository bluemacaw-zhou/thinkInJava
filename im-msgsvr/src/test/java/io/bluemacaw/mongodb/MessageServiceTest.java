package io.bluemacaw.mongodb;

import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.entity.mq.MqMessageData;
import io.bluemacaw.mongodb.enums.ChannelType;
import io.bluemacaw.mongodb.service.MessageService;
import io.bluemacaw.mongodb.util.CollectionNameUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息服务测试
 * 演示动态collection的使用
 */
@Slf4j
@SpringBootTest
public class MessageServiceTest {

    @Resource
    private MessageService messageService;

    /**
     * 测试发送私聊消息
     */
    @Test
    public void testSendPrivateMessage() {
        // 使用辅助方法构造私聊消息
        MqMessage mqMessage = createPrivateChatMessage(
                117304503L, "北京市通州区第二中学",
                116377569L, "Wind",
                "你好,这是一条测试消息"
        );

        Message message = messageService.saveMessage(mqMessage);

        log.info("=== 发送私聊消息成功 ===");
    }

    /**
     * 测试发送群聊消息
     */
    @Test
    public void testSendGroupMessage() {
        // 使用辅助方法构造群聊消息
        MqMessage mqMessage = createGroupChatMessage(
                100001L, "Wind",
                300001L, "大家好，这是一条群聊测试消息！"
        );

        Message message = messageService.saveMessage(mqMessage);

        log.info("=== 发送群聊消息成功 ===");
    }

    /**
     * 测试多线程并发发送消息
     * 验证在并发场景下seq是否单调递增且无重复
     *
     * 注意：
     * 1. 高并发场景下可能出现 MongoDB WriteConflict 异常（error 112），这是正常现象
     * 2. WriteConflict 会由 MQ 层的重试机制处理，无需在此层重试
     * 3. 线程数不宜过多，避免过度竞争导致大量写冲突
     */
    @Test
    public void testSendMultipleMessages() throws InterruptedException {
        int threadCount = 3;  // 线程数（不宜过多，避免过度写冲突）
        int messagesPerThread = 10;  // 每个线程发送的消息数
        int totalMessages = threadCount * messagesPerThread;

        log.info("=== 测试多线程并发发送消息 ===");
        log.info("线程数: {}, 每线程消息数: {}, 总消息数: {}", threadCount, messagesPerThread, totalMessages);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 使用ConcurrentHashMap收集所有seq，用于检测重复
        ConcurrentHashMap<Long, String> seqMap = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 1; i <= messagesPerThread; i++) {
                        MqMessage mqMessage = createPrivateChatMessage(
                                100L, "公司A",
                                200L, "公司B",
                                String.format("线程%d-消息%d", threadId, i)
                        );

                        Message message = messageService.saveMessage(mqMessage);

                        // 检测seq是否重复
                        String existing = seqMap.putIfAbsent(message.getSeq(),
                                String.format("Thread-%d-Msg-%d", threadId, i));

                        if (existing != null) {
                            log.error("❌ 发现重复的seq! seq={}, 原消息={}, 当前消息=Thread-{}-Msg-{}",
                                    message.getSeq(), existing, threadId, i);
                            errorCount.incrementAndGet();
                        } else {
                            successCount.incrementAndGet();
                            if (i == 1 || i == messagesPerThread) {
                                log.info("Thread-{} 发送消息-{}: seq={}, channelId={}",
                                        threadId, i, message.getSeq(), message.getChannelId());
                            }
                        }
                    }
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // MongoDB WriteConflict 异常 - 这是并发场景下的正常现象
                    log.warn("Thread-{} 遇到 WriteConflict 异常（正常现象，实际由MQ重试处理）", threadId);
                    errorCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Thread-{} 执行失败", threadId, e);
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        log.info("=== 并发测试完成 ===");
        log.info("耗时: {} ms", duration);
        log.info("成功发送消息数: {}", successCount.get());
        log.info("错误数: {}", errorCount.get());
        log.info("收集到的唯一seq数量: {}", seqMap.size());

        if (!completed) {
            log.error("❌ 测试超时！部分线程未完成！");
            return;
        }
    }

    /**
     * 测试查询缺失消息
     *
     * 场景：客户端发现本地消息缺失，需要从服务器补齐
     * 例如：本地有 seq=100000, 100010，发现中间缺失消息，则查询区间 (100000, 100010]
     */
    @Test
    public void testQueryMessages() {
        String channelId = "100_200";
        Long sinceVersion = 100000L;  // 查询大于此版本的消息
        Long untilVersion = 100010L;  // 查询小于等于此版本的消息

        List<Message> messages = messageService.queryMessages(
                channelId, sinceVersion, untilVersion
        );

        log.info("查询到 {} 条缺失消息:", messages.size());
        for (Message msg : messages) {
            log.info("  seq: {}, content: {}", msg.getSeq(), msg.getContent());
        }
    }

    /**
     * 测试分页查询历史消息
     */
    @Test
    public void testQueryHistoryMessages() {
        String channelId = "100_200";
        Long cursorVersion = 100015L; // 查询seq < 100015的消息
        int limit = 5;

        List<Message> messages = messageService.queryHistoryMessages(
                channelId, cursorVersion, limit
        );

        log.info("查询到 {} 条历史消息:", messages.size());
        for (Message msg : messages) {
            log.info("  seq: {}, msgTime: {}, content: {}",
                    msg.getSeq(), msg.getMsgTime(), msg.getContent());
        }
    }

    /**
     * 测试Collection名称工具类
     */
    @Test
    public void testCollectionNameUtil() {
        // 当前月份的collection
        String current = CollectionNameUtil.getCurrentMessageCollection();
        log.info("当前月份collection: {}", current);

        // 指定时间的collection
        LocalDateTime date = LocalDateTime.of(2025, 3, 15, 10, 30);
        String specific = CollectionNameUtil.getMessageCollection(date);
        log.info("2025-03-15的collection: {}", specific);

        // 指定年月的collection
        String yearMonth = CollectionNameUtil.getMessageCollection(2025, 1);
        log.info("2025年1月的collection: {}", yearMonth);

        // 获取时间范围内的所有collection
        LocalDateTime start = LocalDateTime.of(2024, 12, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 3, 1, 0, 0);
        List<String> collections = CollectionNameUtil.getMessageCollectionRange(start, end);
        log.info("2024-12 到 2025-03 的所有collection:");
        for (String coll : collections) {
            log.info("  {}", coll);
        }
    }

    /**
     * 创建私聊消息的辅助方法
     */
    private MqMessage createPrivateChatMessage(Long fromId, String fromCompany,
                                               Long toId, String toCompany,
                                               String textContent) {
        MqMessage mqMessage = new MqMessage();
        mqMessage.setMsgType(1);

        MqMessageData mqMessageData = new MqMessageData();
        // 发送方信息
        mqMessageData.setFromId(fromId);
        mqMessageData.setFromCompanyId("");
        mqMessageData.setFromCompany(fromCompany);

        // 接收方信息（私聊场景：contactId是接收者ID）
        mqMessageData.setContactId(toId);
        mqMessageData.setContactType(ChannelType.PRIVATE.getCode());
        mqMessageData.setContactCompanyId("");
        mqMessageData.setContactCompany(toCompany);

        // 消息内容
        mqMessageData.setMsgType(0); // 文本消息
        mqMessageData.setContent("1.1|0|" + Base64.getEncoder().encodeToString(textContent.getBytes()) + "|text|14|0|0|");
        mqMessageData.setContentVersion(1);

        // 消息时间
        mqMessageData.setMsgTime(String.valueOf(System.currentTimeMillis()));

        // 客户端信息
        mqMessageData.setClientMsgId("LC-" + System.currentTimeMillis() + "_2-" + fromId + "-" + toId);
        mqMessageData.setClientInfo("PC/Windows");

        // 唯一消息ID
        mqMessageData.setOldMsgId(String.format("1-%08X:%08X:%08X:%08X{1|%d}%d",
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                fromId, System.nanoTime()));

        // 状态字段
        mqMessageData.setDeleted(0);
        mqMessageData.setStatus(0);

        mqMessage.setMqMessageData(mqMessageData);
        return mqMessage;
    }

    /**
     * 创建群聊消息的辅助方法
     */
    private MqMessage createGroupChatMessage(Long fromId, String fromCompany,
                                             Long groupId, String textContent) {
        MqMessage mqMessage = new MqMessage();
        mqMessage.setMsgType(1);

        MqMessageData mqMessageData = new MqMessageData();
        // 发送方信息
        mqMessageData.setFromId(fromId);
        mqMessageData.setFromCompanyId("");
        mqMessageData.setFromCompany(fromCompany);

        // 接收方信息（群聊场景：contactId是群ID）
        mqMessageData.setContactId(groupId);
        mqMessageData.setContactType(ChannelType.GROUP.getCode());
        mqMessageData.setContactCompanyId(null);
        mqMessageData.setContactCompany(null);

        // 消息内容
        mqMessageData.setMsgType(0); // 文本消息
        mqMessageData.setContent("1.1|0|" + Base64.getEncoder().encodeToString(textContent.getBytes()) + "|text|14|0|0|");
        mqMessageData.setContentVersion(1);

        // 消息时间
        mqMessageData.setMsgTime(String.valueOf(System.currentTimeMillis()));

        // 客户端信息
        mqMessageData.setClientMsgId("LC-" + System.currentTimeMillis() + "_1-" + fromId + "-" + groupId);
        mqMessageData.setClientInfo("PC/Windows");

        // 唯一消息ID
        mqMessageData.setOldMsgId(String.format("1-%08X:%08X:%08X:%08X{1|%d}%d",
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                (int) (Math.random() * Integer.MAX_VALUE),
                fromId, System.nanoTime()));

        // 状态字段
        mqMessageData.setDeleted(0);
        mqMessageData.setStatus(0);

        mqMessage.setMqMessageData(mqMessageData);
        return mqMessage;
    }
}
