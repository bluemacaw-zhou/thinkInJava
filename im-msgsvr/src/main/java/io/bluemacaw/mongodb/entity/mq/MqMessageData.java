package io.bluemacaw.mongodb.entity.mq;

import lombok.Data;

@Data
public class MqMessageData {
    private String msgId;
    private long fromId;
    private long contactId;
    private int contactType;
    private String fromCompanyId;
    private String fromCompany;

    /**
     * 群聊为空
     */
    private String contactCompanyId;

    /**
     * 群聊为空
     */
    private String contactCompany;
    private String oldMsgId;
    private int msgType;
    private String msgTime;

    private Integer deleted;
    private Integer status;
    private String content;
    private Integer contentVersion;
    private String clientMsgId;
    private String clientInfo;
}
