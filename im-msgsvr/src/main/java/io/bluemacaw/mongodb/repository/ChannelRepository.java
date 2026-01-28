package io.bluemacaw.mongodb.repository;

import io.bluemacaw.mongodb.entity.Channel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Channel 数据访问层
 * @author shzhou.michael
 */
@Repository
public interface ChannelRepository extends MongoRepository<Channel, String> {
}
