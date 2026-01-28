package io.bluemacaw.mongodb.enums;

/**
 * Change Stream 操作类型枚举
 *
 * @author shzhou.michael
 */
public enum ChangeStreamOperationType {

    /**
     * 插入操作
     */
    INSERT("insert"),

    /**
     * 更新操作
     */
    UPDATE("update"),

    /**
     * 替换操作
     */
    REPLACE("replace"),

    /**
     * 删除操作
     */
    DELETE("delete");

    private final String value;

    ChangeStreamOperationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 从字符串值获取枚举
     *
     * @param value 字符串值
     * @return 对应的枚举，如果不存在则返回 null
     */
    public static ChangeStreamOperationType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ChangeStreamOperationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为指定的操作类型
     *
     * @param value 字符串值
     * @return 是否匹配
     */
    public boolean matches(String value) {
        return this.value.equals(value);
    }
}
