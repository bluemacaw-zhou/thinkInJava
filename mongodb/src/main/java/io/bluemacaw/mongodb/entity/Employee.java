package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document("emp")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Employee {
    @Id
    private Integer id;

    @Field("username")
    private String name;

    @Field
    private int age;

    @Field
    private Double salary;

    @Field
    private LocalDateTime entryDay;
}
