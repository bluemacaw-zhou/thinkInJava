package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("DeviceSyncState")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceSyncState {
    @Id
    private String id;

    @Field
    private long userId;

    @Field
    private String deviceId;

    @Field
    private String sessionId;

    @Field
    private int sessionType;

    @Field
    private long lastSyncVersion;
}
