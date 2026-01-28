package io.bluemacaw.mongodb.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 频道类型枚举
 *
 * @author michael
 * @since 2026-01-05
 */
@Getter
public enum ChannelType {

    /**
     * 私聊频道
     * Channel ID格式: userId1_userId2 (小ID在前)
     */
    PRIVATE(0, "private", "私聊"),

    /**
     * 群聊频道
     * Channel ID格式: group_groupId
     */
    GROUP(1, "group", "群聊");

    /**
     * 数据库存储值
     */
    private final int code;

    /**
     * 英文标识
     */
    private final String type;

    /**
     * 中文描述
     */
    private final String description;

    ChannelType(int code, String type, String description) {
        this.code = code;
        this.type = type;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     *
     * @param code 数据库存储值
     * @return 枚举值，如果不存在则返回null
     */
    public static ChannelType fromCode(int code) {
        for (ChannelType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据type获取枚举
     *
     * @param type 英文标识
     * @return 枚举值，如果不存在则返回null
     */
    public static ChannelType fromType(String type) {
        if (type == null) {
            return null;
        }
        for (ChannelType channelType : values()) {
            if (channelType.type.equalsIgnoreCase(type)) {
                return channelType;
            }
        }
        return null;
    }

    /**
     * JSON序列化时使用code值
     */
    @JsonValue
    public int toValue() {
        return code;
    }

    /**
     * 判断是否是私聊
     */
    public boolean isPrivate() {
        return this == PRIVATE;
    }

    /**
     * 判断是否是群聊
     */
    public boolean isGroup() {
        return this == GROUP;
    }
}
