package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("books")  
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Book {
    @Id
    private String id;

    @Field
    private String title;

    @Field
    private String type;

    @Field
    private String tag;

    @Field
    private Long favCount;

    @Field
    private String author;
}
