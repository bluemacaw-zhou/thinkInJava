package io.bluemacaw.mongodb.controller.support;

import com.alibaba.fastjson.JSON;
import io.bluemacaw.mongodb.config.RabbitmqConfig;
import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.entity.mq.MqMessageData;
import io.bluemacaw.mongodb.entity.mq.MqAggregatedMessageData;
import io.bluemacaw.mongodb.enums.ChannelType;
import io.bluemacaw.mongodb.service.GroupService;
import io.bluemacaw.mongodb.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private UserService userService;

    @Resource
    private GroupService groupService;

    // 批量发送控制标志
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private final AtomicLong sentCount = new AtomicLong(0);

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
        MqMessage mqMessage = new MqMessage();
        mqMessage.setMsgType(1);

        LocalDateTime now = LocalDateTime.now();

        MqMessageData mqMessageData = new MqMessageData();
        mqMessageData.setFromId(117304503L);
        mqMessageData.setContactId(116377569L);  // 私聊场景：contactId是接收者ID
        mqMessageData.setContactType(ChannelType.PRIVATE.getCode());  // 私聊
        mqMessageData.setFromCompanyId("");
        mqMessageData.setFromCompany("北京市通州区第二中学");
        mqMessageData.setContactCompanyId("");
        mqMessageData.setContactCompany("Wind");
        mqMessageData.setOldMsgId("1-06FDECB7:06EFC7E1:52897665:20000219{1|117304503}1");
        mqMessageData.setMsgType(0);
        mqMessageData.setMsgTime(String.valueOf(System.currentTimeMillis()));
        mqMessageData.setDeleted(0);
        mqMessageData.setStatus(0);
        mqMessageData.setContent("1.1|0|ODlEIDExMjUxNDAxNS5JQiAyNeaxn+iLj+mTtuihjENEMDE1IEJJRC8tLSAtLS8tLQ0K|5b6u6L2v6ZuF6buR|14|0|0|");
        mqMessageData.setContentVersion(1);
        mqMessageData.setClientMsgId("LC-1761617601839126400_2-117304503-116377569");
        mqMessageData.setClientInfo("PC/Windows");

        mqMessage.setMqMessageData(mqMessageData);

        // 发送到单条消息队列（实时数据）
        sendMessageToQueue(config.getExchangeMessage(),
                          config.getRouteMessage(),
                          JSON.toJSONString(mqMessage));

        return "ok";
    }

    /**
     * 批量发送历史数据（按日期范围连续生成多天消息）
     * GET /testRabbitmq/sendMessageBatchByDateRange?startDate=2024-01-01&days=7&delayMs=100
     *
     * 新行为定义：
     * - 从指定日期开始，连续生成多天的消息
     * - 每天20万条私聊消息
     * - 活跃用户不超过3万人
     * - 每个channel的消息不超过100条
     * - 至少有2000个不同的channel
     * - 按channelId聚合后发送到 queue.message.batch
     *
     * @param startDate 起始日期（格式：yyyy-MM-dd，默认今天）
     * @param days 连续生成的天数（默认1天）
     * @param delayMs 每个channel消息包之间的延迟毫秒数（默认100ms）
     * @return 状态信息
     */
    @GetMapping("/sendMessageBatchByDateRange")
    public Map<String, Object> sendMessageBatchByDateRange(
            @RequestParam(required = false) String startDate,
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "0") int delayMs) {

        Map<String, Object> response = new HashMap<>();

        // 验证参数
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

        // 解析起始日期
        LocalDate start;
        try {
            start = (startDate == null || startDate.isEmpty())
                    ? LocalDate.now()
                    : LocalDate.parse(startDate);
        } catch (Exception e) {
            isSending.set(false);
            response.put("success", false);
            response.put("message", "Invalid date format. Please use yyyy-MM-dd");
            return response;
        }

        sentCount.set(0);
        final LocalDate finalStartDate = start;
        final int finalDays = days;

        // 在新线程中执行批量发送
        new Thread(() -> {
            try {
                long overallStartTime = System.currentTimeMillis();
                log.info("Starting batch send: startDate={}, days={}, delayMs={}",
                         finalStartDate, finalDays, delayMs);

                // 循环生成每一天的消息
                for (int dayOffset = 0; dayOffset < finalDays; dayOffset++) {
                    if (!isSending.get()) {
                        log.info("Batch send stopped by user at day {}/{}", dayOffset + 1, finalDays);
                        break;
                    }

                    LocalDate currentDate = finalStartDate.plusDays(dayOffset);
                    log.info("Generating messages for date: {} ({}/{})",
                             currentDate, dayOffset + 1, finalDays);

                    // 为当天生成并发送消息
                    sendMessageBatch(currentDate, delayMs);

                    log.info("Completed day {}/{}: {} total messages sent so far",
                             dayOffset + 1, finalDays, sentCount.get());
                }

                long duration = System.currentTimeMillis() - overallStartTime;
                log.info("Batch send completed: {} days, {} total messages sent in {} ms",
                         finalDays, sentCount.get(), duration);

            } catch (Exception e) {
                log.error("Batch send error", e);
            } finally {
                isSending.set(false);
            }
        }).start();

        response.put("success", true);
        response.put("message", "Batch sending started");
        response.put("startDate", start.toString());
        response.put("days", days);
        response.put("delayMs", delayMs);
        response.put("estimatedMessagesPerDay", 200000);
        response.put("estimatedTotalMessages", 200000 * days);
        response.put("estimatedChannelsPerDay", "2000+");

        return response;
    }

    /**
     * 内部方法：为指定日期生成并发送一天的消息（按channel聚合）
     *
     * @param date 消息日期
     * @param delayMs 每个channel消息包之间的延迟毫秒数
     */
    private void sendMessageBatch(LocalDate date, int delayMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("Generating messages for date: {}", date);

        // 生成一天的消息（按channel聚合）
        Map<String, List<Message>> channelMessages = generateOneDayMessagesGroupedByChannel(date);

        log.info("Generated {} channels with {} messages for date {}",
                 channelMessages.size(),
                 channelMessages.values().stream().mapToInt(List::size).sum(),
                 date);

        // 按channel发送聚合后的消息
        int channelCount = 0;
        for (Map.Entry<String, List<Message>> entry : channelMessages.entrySet()) {
            if (!isSending.get()) {
                log.info("Batch send stopped by user");
                break;
            }

            String channelId = entry.getKey();
            List<Message> messages = entry.getValue();

            // 发送该channel的聚合消息到批量队列
            sendChannelMessageBatch(channelId, messages, date);

            int messageCount = messages.size();
            sentCount.addAndGet(messageCount);
            channelCount++;

            if (channelCount % 100 == 0) {
                log.info("Progress for {}: {}/{} channels sent, {} messages",
                         date, channelCount, channelMessages.size(), messageCount);
            }

            // channel间延迟
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Completed date {}: {} channels, {} messages sent in {} ms",
                 date, channelCount, channelMessages.values().stream().mapToInt(List::size).sum(), duration);
    }

    /**
     * 生成一天的消息并按channelId分组
     * 规则：
     * - 总共20万条消息（50%私聊 + 50%群聊）
     * - 私聊：至少1000个不同的channel，每个channel不超过100条消息
     * - 群聊：随机分配到100个群，每个群不超过1000条消息
     * - 活跃用户不超过3万人
     *
     * @param date 消息日期
     * @return Map<ChannelId, List<Message>>
     */
    private Map<String, List<Message>> generateOneDayMessagesGroupedByChannel(LocalDate date) {
        Map<String, List<Message>> channelMessagesMap = new LinkedHashMap<>();

//        final int TOTAL_MESSAGES = 1;
        final int TOTAL_MESSAGES = 200000;
        final int PRIVATE_MESSAGES = TOTAL_MESSAGES / 2;  // 50%私聊
        final int GROUP_MESSAGES = TOTAL_MESSAGES - PRIVATE_MESSAGES;  // 50%群聊

        final int MIN_PRIVATE_CHANNELS = 1000;
        final int MAX_MESSAGES_PER_PRIVATE_CHANNEL = 100;
        final int MAX_MESSAGES_PER_GROUP_CHANNEL = 1000;

        int generatedMessages = 0;
        int channelIndex = 0;

        // ========== 1. 生成私聊消息 ==========
        log.info("Generating private chat messages: {} messages", PRIVATE_MESSAGES);
        int privateMessagesGenerated = 0;

        while (privateMessagesGenerated < PRIVATE_MESSAGES) {
            // 为这个私聊channel生成随机数量的消息（1到100条之间）
            int messagesInThisChannel = random.nextInt(MAX_MESSAGES_PER_PRIVATE_CHANNEL) + 1;

            // 确保不超过剩余消息数
            int remaining = PRIVATE_MESSAGES - privateMessagesGenerated;
            messagesInThisChannel = Math.min(messagesInThisChannel, remaining);

            // 随机获取两个不同的用户（通过ID范围随机，主键查询）
            io.bluemacaw.mongodb.entity.User[] userPair = userService.getRandomUserPair();
            io.bluemacaw.mongodb.entity.User user1 = userPair[0];
            io.bluemacaw.mongodb.entity.User user2 = userPair[1];

            String channelId = generatePrivateChannelId(user1.getId(), user2.getId());

            List<Message> channelMessageList = new ArrayList<>();

            // 为该channel生成消息
            for (int i = 0; i < messagesInThisChannel; i++) {
                Message message = generatePrivateMessage(date, user1, user2);
                channelMessageList.add(message);
            }

            channelMessagesMap.put(channelId, channelMessageList);
            privateMessagesGenerated += messagesInThisChannel;
            channelIndex++;

            if (channelIndex % 500 == 0) {
                log.info("Generated {} private channels, {} messages so far", channelIndex, privateMessagesGenerated);
            }
        }

        log.info("Private chat messages generated: {} channels, {} messages", channelIndex, privateMessagesGenerated);

        // ========== 2. 生成群聊消息 ==========
        log.info("Generating group chat messages: {} messages", GROUP_MESSAGES);
        int groupMessagesGenerated = 0;

        while (groupMessagesGenerated < GROUP_MESSAGES) {
            // 为这个群生成随机数量的消息（1到1000条之间）
            int messagesInThisChannel = random.nextInt(MAX_MESSAGES_PER_GROUP_CHANNEL) + 1;

            // 确保不超过剩余消息数
            int remaining = GROUP_MESSAGES - groupMessagesGenerated;
            messagesInThisChannel = Math.min(messagesInThisChannel, remaining);

            // 随机获取一个群（通过ID范围随机，主键查询）
            io.bluemacaw.mongodb.entity.Group group = groupService.getRandomGroup();
            String channelId = String.valueOf(group.getId());

            // 获取群成员
            List<io.bluemacaw.mongodb.entity.User> members = groupService.getGroupMembers(group.getId());
            if (members.isEmpty()) {
                continue;
            }

            // 获取或创建该群的消息列表
            List<Message> channelMessageList = channelMessagesMap.computeIfAbsent(channelId, k -> new ArrayList<>());

            // 为该群生成消息
            for (int i = 0; i < messagesInThisChannel; i++) {
                Message message = generateGroupMessage(date, group.getId(), members);
                channelMessageList.add(message);
            }

            groupMessagesGenerated += messagesInThisChannel;

            if (groupMessagesGenerated % 10000 == 0) {
                log.info("Group messages generated: {} messages so far", groupMessagesGenerated);
            }
        }

        log.info("Group chat messages generated: {} messages", groupMessagesGenerated);
        log.info("Total channels generated: {} (private + group), total messages: {}",
                channelMessagesMap.size(), privateMessagesGenerated + groupMessagesGenerated);

        return channelMessagesMap;
    }

    /**
     * 生成私聊channelId（由两个用户ID组成，小ID在前）
     */
    private String generatePrivateChannelId(long userId1, long userId2) {
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);
        return minId + "_" + maxId;
    }

    /**
     * 生成私聊消息
     */
    private Message generatePrivateMessage(LocalDate date, io.bluemacaw.mongodb.entity.User user1,
                                          io.bluemacaw.mongodb.entity.User user2) {
        Message message = new Message();

        // 随机选择发送方和接收方（双向通信）
        boolean user1Sends = random.nextBoolean();
        io.bluemacaw.mongodb.entity.User fromUser = user1Sends ? user1 : user2;
        io.bluemacaw.mongodb.entity.User toUser = user1Sends ? user2 : user1;

        message.setFromId(fromUser.getId());
        message.setFromCompany(fromUser.getCompany());
        message.setFromCompanyId("");

        message.setToId(toUser.getId());
        message.setToCompany(toUser.getCompany());
        message.setToCompanyId("");
        message.setContactType(ChannelType.PRIVATE.getCode());

        // 设置channelId
        message.setChannelId(generatePrivateChannelId(user1.getId(), user2.getId()));

        // 随机消息类型（文本占70%）
        message.setMsgType(MSG_TYPES[random.nextInt(MSG_TYPES.length)]);

        // 生成当天的随机时间戳
        LocalDateTime sendMessageTime = date.atTime(
            random.nextInt(24),
            random.nextInt(60),
            random.nextInt(60)
        );

        message.setMsgTime(sendMessageTime);

        // 生成唯一的oldMsgId
        message.setOldMsgId(String.format("1-%08X:%08X:%08X:%08X{1|%d}%d",
                random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(),
                fromUser.getId(), System.nanoTime()));

        // 生成内容
        message.setContent(generateMessageContent(message.getMsgType()));
        message.setContentVersion(1);

        // 生成clientMsgId
        message.setClientMsgId(String.format("LC-%d_%d-%d-%d",
                System.currentTimeMillis(), random.nextInt(10), fromUser.getId(), toUser.getId()));

        message.setClientInfo("PC/Windows");

        return message;
    }

    /**
     * 生成群聊消息
     */
    private Message generateGroupMessage(LocalDate date, Long groupId,
                                        List<io.bluemacaw.mongodb.entity.User> members) {
        Message message = new Message();

        // 从群成员中随机选择一个发送者
        io.bluemacaw.mongodb.entity.User fromUser = members.get(random.nextInt(members.size()));

        message.setFromId(fromUser.getId());
        message.setFromCompany(fromUser.getCompany());
        message.setFromCompanyId("");

        message.setToId(groupId);  // 群聊的toId是群ID
        message.setToCompany(null);
        message.setToCompanyId(null);
        message.setContactType(ChannelType.GROUP.getCode());

        // 设置channelId（群聊的channelId就是groupId）
        message.setChannelId(String.valueOf(groupId));

        // 随机消息类型（文本占70%）
        message.setMsgType(MSG_TYPES[random.nextInt(MSG_TYPES.length)]);

        // 生成当天的随机时间戳
        LocalDateTime sendMessageTime = date.atTime(
            random.nextInt(24),
            random.nextInt(60),
            random.nextInt(60)
        );

        message.setMsgTime(sendMessageTime);

        // 生成唯一的oldMsgId
        message.setOldMsgId(String.format("1-%08X:%08X:%08X:%08X{1|%d}%d",
                random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt(),
                fromUser.getId(), System.nanoTime()));

        // 生成内容
        message.setContent(generateMessageContent(message.getMsgType()));
        message.setContentVersion(1);

        // 生成clientMsgId
        message.setClientMsgId(String.format("LC-%d_%d-%d-%d",
                System.currentTimeMillis(), random.nextInt(10), fromUser.getId(), groupId));

        message.setClientInfo("PC/Windows");

        return message;
    }

    /**
     * 发送channel的批量消息到队列
     * 直接发送 MqAggregatedMessageData 结构到 queue.message.batch
     */
    private void sendChannelMessageBatch(String channelId, List<Message> messages, LocalDate date) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 将消息列表转换为MqMessage列表
        List<MqMessage> mqMessageList = new ArrayList<>();
        for (Message message : messages) {
            MqMessageData mqMessageData = convertMessageToMsgData(message);

            MqMessage mqMessage = new MqMessage();
            mqMessage.setMsgType(1);  // 单条消息类型
            mqMessage.setMqMessageData(mqMessageData);

            mqMessageList.add(mqMessage);
        }

        // 确定channelType（根据第一条消息）
        int channelType = messages.get(0).getContactType();

        // 创建聚合消息数据结构
        MqAggregatedMessageData aggregatedData = new MqAggregatedMessageData();
        aggregatedData.setChannelId(channelId);
        aggregatedData.setChannelType(channelType);
        aggregatedData.setMessageDate(date.toString());  // yyyy-MM-dd格式
        aggregatedData.setMessages(mqMessageList);
        aggregatedData.setMessageCount(messages.size());

        // 发送到批量消息队列（queue.message.batch）
        // 直接发送 MqAggregatedMessageData 的 JSON
        sendMessageToQueue(config.getExchangeMessage(),
                          config.getRouteMessageBatch(),
                          JSON.toJSONString(aggregatedData));
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
    private void sendGeneratedMessage(Message message) {
        MqMessage mqMessage = new MqMessage();
        mqMessage.setMsgType(1);

        // 将Message转换为MsgData
        MqMessageData mqMessageData = convertMessageToMsgData(message);
        mqMessage.setMqMessageData(mqMessageData);

        // 发送到批量消息队列（历史数据导入）
        sendMessageToQueue(config.getExchangeMessage(),
                          config.getRouteMessageBatch(),
                          JSON.toJSONString(mqMessage));
    }

    /**
     * 将Message实体转换为MsgData (MQ消息格式)
     */
    private MqMessageData convertMessageToMsgData(Message message) {
        MqMessageData mqMessageData = new MqMessageData();
        mqMessageData.setFromId(message.getFromId());
        mqMessageData.setFromCompanyId(message.getFromCompanyId());
        mqMessageData.setFromCompany(message.getFromCompany());
        mqMessageData.setOldMsgId(message.getOldMsgId());
        mqMessageData.setMsgType(message.getMsgType());
        mqMessageData.setContent(message.getContent());
        mqMessageData.setContentVersion(message.getContentVersion());
        mqMessageData.setClientMsgId(message.getClientMsgId());
        mqMessageData.setClientInfo(message.getClientInfo());
        mqMessageData.setDeleted(message.getDeleted());
        mqMessageData.setStatus(message.getStatus());
        mqMessageData.setContactType(message.getContactType());

        // 处理时间字段
        if (message.getMsgTime() != null) {
            long timestamp = message.getMsgTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            mqMessageData.setMsgTime(String.valueOf(timestamp));
        }

        // 根据contactType设置contactId和contactCompany
        if (message.getContactType() == ChannelType.PRIVATE.getCode()) {
            // 私聊：contactId是接收者ID
            mqMessageData.setContactId(message.getToId());
            mqMessageData.setContactCompanyId(message.getToCompanyId());
            mqMessageData.setContactCompany(message.getToCompany());
        } else {
            // 群聊：contactId是群ID (message.toId存储的是群ID)
            mqMessageData.setContactId(message.getToId());
            mqMessageData.setContactCompanyId(null);
            mqMessageData.setContactCompany(null);
        }

        return mqMessageData;
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
                new org.springframework.amqp.core.Message(messageContent.getBytes(StandardCharsets.UTF_8), messageProperties));
    }
}
