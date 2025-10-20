package io.bluemacaw.mongodb;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@SpringBootTest
public class TemplateTest {
    @Resource
    MongoTemplate mongoTemplate;

    @Test
    public void testCreateCollection(){
        boolean exists = mongoTemplate.collectionExists("books");
        log.info("collection books is exists: {}", exists);
    }

}
