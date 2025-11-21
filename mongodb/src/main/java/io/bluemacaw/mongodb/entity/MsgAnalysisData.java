package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgAnalysisData {
   private String msgId;

    private long fromId;

    private long contactId;

    private String sessionId;

    private int contactType;

    private String fromCompanyId;

    private String fromCompany;

    private String contactCompanyId;

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 生成 sessionId
     * 规则：比较 fromId 和 contactId，小的在前，大的在后，用下划线拼接
     * 例如：fromId=1001, contactId=2001 → sessionId="1001_2001"
     *      fromId=5000, contactId=3000 → sessionId="3000_5000"
     */
    public void generateSessionId() {
        if (fromId < contactId) {
            this.sessionId = fromId + "_" + contactId;
        } else {
            this.sessionId = contactId + "_" + fromId;
        }
    }

    /**
     * 静态方法：根据两个ID生成 sessionId
     */
    public static String generateSessionId(long id1, long id2) {
        if (id1 < id2) {
            return id1 + "_" + id2;
        } else {
            return id2 + "_" + id1;
        }
    }
}
