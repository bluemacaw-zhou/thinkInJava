package cn.com.wind.entity;

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
}
