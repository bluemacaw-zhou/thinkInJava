package io.bluemacaw.mongodb.repository;

import io.bluemacaw.mongodb.entity.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Session 数据访问层
 */
@Repository
public interface SessionRepository extends MongoRepository<Session, String> {
}
