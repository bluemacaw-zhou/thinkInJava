package io.bluemacaw.mongodb.repository;

import io.bluemacaw.mongodb.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * User Repository
 *
 * @author shzhou.michael
 */
@Repository
public interface UserRepository extends MongoRepository<User, Long> {

    /**
     * 根据公司查询用户
     */
    List<User> findByCompany(String company);
}
