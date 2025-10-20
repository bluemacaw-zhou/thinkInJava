package io.bluemacaw.mongodb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
public class MongoConfig {
    @Bean
    MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory mongoDatabaseFactory,
            MongoMappingContext context,
            MongoCustomConversions conversions){

        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDatabaseFactory);
        MappingMongoConverter mappingMongoConverter = new MappingMongoConverter(dbRefResolver,context);
        mappingMongoConverter.setCustomConversions(conversions);

        //构造DefaultMongoTypeMapper，将typeKey设置为空值
        mappingMongoConverter.setTypeMapper(new DefaultMongoTypeMapper(null));

        return mappingMongoConverter;
    }
}
