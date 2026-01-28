package io.bluemacaw.mongodb.entity.mq;

import lombok.Data;

/**
 * @author shzhou.michael
 */
@Data
public class MqMessage {
    private int msgType;

    private MqMessageData mqMessageData;
}
