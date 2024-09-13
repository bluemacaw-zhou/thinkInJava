package io.bluemacaw.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Employee {
    private Long id;
    private String name;
    private BigDecimal salary;
}
