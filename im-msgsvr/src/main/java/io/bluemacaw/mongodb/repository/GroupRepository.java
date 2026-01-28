package io.bluemacaw.mongodb.repository;

import io.bluemacaw.mongodb.entity.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Group Repository
 *
 * @author shzhou.michael
 */
@Repository
public interface GroupRepository extends MongoRepository<Group, Long> {
}
