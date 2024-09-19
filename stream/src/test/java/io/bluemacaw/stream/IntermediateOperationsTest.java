package io.bluemacaw.stream;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class IntermediateOperationsTest {
    private static final BigDecimal SALARY_1000 = new BigDecimal("1000.0");

    // map
    @Test
    public void findByIdTest() {
        List<Long> ids = Stream.of(1L, 3L, 5L).collect(Collectors.toList());

        List<Employee> srcEmployees = Common.getEmployeeList();
        List<Employee> dstEmployees = srcEmployees.stream().map(employee -> {
            boolean match = ids.stream().anyMatch(id -> {
                return id.equals(employee.getId());
            });

            if (match) {
                return employee;
            }

            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());


        log.info("{}", dstEmployees);
    }

    // peek
    @Test
    public void incrementSalaryAndPrintTest() {
        List<Employee> srcEmployees = Common.getEmployeeList();
        List<Employee> dstEmployees = srcEmployees
                .stream()
                .peek(e -> e.setSalary(e.getSalary().add(SALARY_1000)))
                .peek(System.out::println)
                .collect(Collectors.toList());

        log.info("{}", dstEmployees);
    }

    // flatMap
    @Test
    public void getNameListTest() {
        List<List<String>> nameNestedList = Common.constructNameNested();
        List<String> names = nameNestedList
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        log.info("{}", names);
    }
}
