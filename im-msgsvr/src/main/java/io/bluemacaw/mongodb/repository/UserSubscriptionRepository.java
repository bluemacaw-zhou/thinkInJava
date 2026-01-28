package io.bluemacaw.mongodb.repository;

import io.bluemacaw.mongodb.entity.UserSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserSubscription 数据访问层
 * @author shzhou.michael
 */
@Repository
public interface UserSubscriptionRepository extends MongoRepository<UserSubscription, String> {

    /**
     * 根据用户ID和频道ID查询订阅
     *
     * @param userId 用户ID
     * @param channelId 频道ID
     * @return UserSubscription
     */
    Optional<UserSubscription> findByUserIdAndChannelId(Long userId, String channelId);
}
