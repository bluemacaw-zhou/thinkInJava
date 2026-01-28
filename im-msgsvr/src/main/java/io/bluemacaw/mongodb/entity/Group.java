package io.bluemacaw.mongodb.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * 群组实体（Mock数据）
 *
 * @author shzhou.michael
 */
@Data
@Document(collection = "group")
public class Group {

    /**
     * 群ID
     */
    @Id
    @Field("_id")
    private Long id;

    /**
     * 群成员用户ID列表
     */
    @Field("member_user_ids")
    private List<Long> memberUserIds;
}
