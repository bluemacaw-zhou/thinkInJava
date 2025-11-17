package cn.com.wind.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document("message")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgData {
    @Id
    private String msgId;

    @Field
    private long fromId;

    @Field
    private long contactId;

    @Field
    private int contactType;

    @Field
    private String fromCompanyId;

    @Field
    private String fromCompany;

    /**
     * 群聊为空
     */
    @Field
    private String contactCompanyId;

    /**
     * 群聊为空
     */
    @Field
    private String contactCompany;

    @Field
    private String oldMsgId;

    @Field
    private int msgType;

    @Field
    private String msgTime;

    @Field
    private Integer deleted;

    @Field
    private Integer status;

    @Field
    private String content;

    @Field
    private Integer contentVersion;

    @Field
    private String clientMsgId;

    @Field
    private String clientInfo;

    @Field
    private LocalDateTime createTime;

    @Field
    private LocalDateTime updateTime;
}
