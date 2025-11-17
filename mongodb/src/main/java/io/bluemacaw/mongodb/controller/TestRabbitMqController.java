package cn.com.wind.controller;

import cn.com.wind.config.RabbitmqConfig;
import cn.com.wind.entity.MqMsgItem;
import cn.com.wind.entity.MsgData;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    // 批量发送控制标志
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private final AtomicLong sentCount = new AtomicLong(0);

    // 用户ID池（10个人）
    private static final Long[] FROM_IDS = {
        100001L, 100002L, 100003L, 100004L, 100005L,
        100006L, 100007L, 100008L, 100009L, 100010L
    };

    // 联系人ID池 - 私聊（10个人）
    private static final Long[] PRIVATE_CONTACT_IDS = {
        100001L, 100002L, 100003L, 100004L, 100005L,
        100006L, 100007L, 100008L, 100009L, 100010L
    };

    // 联系人ID池 - 群聊（5个群）
    private static final Long[] GROUP_CONTACT_IDS = {
        300001L, 300002L, 300003L, 300004L, 300005L
    };

    // 公司名称池（Wind占70%权重）
    private static final String[] COMPANIES = {
        "Wind", "Wind", "Wind", "Wind", "Wind", "Wind", "Wind",  // 70%
        "复旦大学", "交通大学", "同济大学", "上海财经大学", "东华大学", "华东师范大学"
    };

    // 消息类型池（0=文本占70%权重）
    private static final Integer[] MSG_TYPES = {
        0, 0, 0, 0, 0, 0, 0,  // 70% 文本消息
        1, 2, 5, 6            // 30% 其他类型（图片、文件、语音、视频）
    };

    private final Random random = new Random();

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

        sendMessage(config.getExchangeMessage(),
                    config.getRouteMessage(),
                    JSON.toJSONString(mqMsgItem));

        return "ok";
    }


    private void sendMessage(String exchangeName, String routeKey, String messageContent) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);

        rabbitTemplate.send(
                exchangeName,
                routeKey,
                new Message(messageContent.getBytes(StandardCharsets.UTF_8), messageProperties));
    }

    /**
     * 开始批量发送消息
     * GET /testRabbitmq/startBatchSend?days=7&batchSize=1000&delayMs=10
     *
     * @param days 时间跨度天数（默认92天，即3个月）
     * @param batchSize 每批发送的消息数量（默认1000）
     * @param delayMs 每批之间的延迟毫秒数（默认100ms）
     * @return 状态信息
     */
    @GetMapping("/startBatchSend")
    public Map<String, Object> startBatchSend(
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
     * 停止批量发送
     * GET /testRabbitmq/stopBatchSend
     */
    @GetMapping("/stopBatchSend")
    public Map<String, Object> stopBatchSend() {
        Map<String, Object> response = new HashMap<>();

        if (isSending.compareAndSet(true, false)) {
            response.put("success", true);
            response.put("message", "Batch sending stopped");
            response.put("sentCount", sentCount.get());
        } else {
            response.put("success", false);
            response.put("message", "No batch sending in progress");
            response.put("sentCount", sentCount.get());
        }

        return response;
    }

    /**
     * 获取批量发送状态
     * GET /testRabbitmq/batchStatus
     */
    @GetMapping("/batchStatus")
    public Map<String, Object> getBatchStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("isSending", isSending.get());
        response.put("sentCount", sentCount.get());
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
     * 生成一条随机消息
     */
    private MessageData generateRandomMessage(LocalDate date) {
        MessageData data = new MessageData();

        // 随机选择发送者
        Long fromId = FROM_IDS[random.nextInt(FROM_IDS.length)];
        data.fromId = fromId;

        // 根据概率选择私聊或群聊（私聊60% vs 群聊40%）
        boolean isPrivateChat = random.nextInt(100) < 60;

        Long contactId;
        if (isPrivateChat) {
            // 私聊：从私聊联系人池中选择
            contactId = PRIVATE_CONTACT_IDS[random.nextInt(PRIVATE_CONTACT_IDS.length)];
            data.contactType = 0;
        } else {
            // 群聊：从群聊联系人池中选择
            contactId = GROUP_CONTACT_IDS[random.nextInt(GROUP_CONTACT_IDS.length)];
            data.contactType = 1;
        }
        data.contactId = contactId;

        // 随机选择公司（Wind占70%）
        String company = COMPANIES[random.nextInt(COMPANIES.length)];
        data.fromCompany = company;
        data.fromCompanyId = company.equals("Wind") ? "wind_corp" : "";

        if (data.contactType == 0) {
            // 私聊：联系人也有公司
            String contactCompany = COMPANIES[random.nextInt(COMPANIES.length)];
            data.contactCompany = contactCompany;
            data.contactCompanyId = contactCompany.equals("Wind") ? "wind_corp" : "";
        } else {
            // 群聊：没有联系人公司
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
            fromId, System.nanoTime());

        // 生成内容
        data.content = generateMessageContent(data.msgType);

        // 生成clientMsgId
        data.clientMsgId = String.format("LC-%d_%d-%d-%d",
            data.msgTime, random.nextInt(10), fromId, contactId);

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

        sendMessage(config.getExchangeMessage(),
                    config.getRouteMessage(),
                    JSON.toJSONString(mqMsgItem));
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
}
