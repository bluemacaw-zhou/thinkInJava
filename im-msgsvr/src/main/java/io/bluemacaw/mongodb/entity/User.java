package io.bluemacaw.mongodb.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 用户实体（Mock数据）
 *
 * @author shzhou.michael
 */
@Data
@Document(collection = "user")
public class User {

    /**
     * 用户ID
     */
    @Id
    @Field("_id")
    private Long id;

    /**
     * 公司名称
     */
    @Field("company")
    @Indexed
    private String company;
}
