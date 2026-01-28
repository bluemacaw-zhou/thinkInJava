package io.bluemacaw.mongodb.util;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Collection名称工具类
 * 用于生成按月分collection的名称
 */
public class CollectionNameUtil {

    private static final String MESSAGE_COLLECTION_PREFIX = "message_";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 根据当前时间生成消息collection名称
     * @return 格式: messages_202501
     */
    public static String getCurrentMessageCollection() {
        return MESSAGE_COLLECTION_PREFIX + YearMonth.now().format(FORMATTER);
    }

    /**
     * 根据指定时间生成消息collection名称
     * @param dateTime 消息时间
     * @return 格式: messages_202501
     */
    public static String getMessageCollection(LocalDateTime dateTime) {
        if (dateTime == null) {
            return getCurrentMessageCollection();
        }
        return MESSAGE_COLLECTION_PREFIX + dateTime.format(FORMATTER);
    }

    /**
     * 根据年月生成消息collection名称
     * @param year 年份
     * @param month 月份(1-12)
     * @return 格式: messages_202501
     */
    public static String getMessageCollection(int year, int month) {
        return String.format("%s%04d%02d", MESSAGE_COLLECTION_PREFIX, year, month);
    }

    /**
     * 获取指定时间范围内的所有消息collection名称
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return collection名称列表
     */
    public static java.util.List<String> getMessageCollectionRange(
            LocalDateTime startDate, LocalDateTime endDate) {

        java.util.List<String> collections = new java.util.ArrayList<>();

        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        YearMonth current = start;
        while (!current.isAfter(end)) {
            collections.add(MESSAGE_COLLECTION_PREFIX + current.format(FORMATTER));
            current = current.plusMonths(1);
        }

        return collections;
    }
}
