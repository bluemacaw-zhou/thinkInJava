package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("books")  
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Book {
    private String id;

    private String title;

    private String type;

    private String tag;

    private Long favCount;

    private String author;
}
