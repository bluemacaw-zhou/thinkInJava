package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("Session")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Session {
    /**
     * 对于私聊 使用收发双方id小的在前面 id大的在后面 中间以_连接 形如123_456
     * 对于群聊 使用group_连接群id 形如group_123
     */
    @Id
    private String id;

    /**
     * 私聊 - 0 群聊 - 1
     */
    @Field
    private int sessionType;

    @Field
    private long version;
}
