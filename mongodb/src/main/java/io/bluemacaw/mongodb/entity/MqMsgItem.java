package io.bluemacaw.mongodb.entity;

import lombok.Data;

@Data
public class MqMsgItem {
    private int msgType;

    private MsgData msgData;
}
