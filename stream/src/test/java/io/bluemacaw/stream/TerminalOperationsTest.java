package io.bluemacaw.stream;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TerminalOperationsTest {
    private static final BigDecimal SALARY_20000 = new BigDecimal("20000.0");

    // foreach
    @Test
    public void printSalaryTest() {
        Collection<Employee> employees = Common.getEmployeeList();
        employees.forEach(System.out::println);
    }

    // findFirst
    @Test
    public void findSalaryMoreThan20000Test() {
        Collection<Employee> employees = Common.getEmployeeList();

        Employee employee = employees
                .stream()
                .filter(e -> e.getSalary().compareTo(SALARY_20000) > 0)
                .findFirst()
                .orElseThrow(RuntimeException::new);
                // .orElse(null);

        log.info("{}", employee);
    }

    // count
    @Test
    public void findSalaryMoreThan20000CountTest() {
        Collection<Employee> employees = Common.getEmployeeList();

        Long count = employees
                .stream()
                .filter(e -> e.getSalary().compareTo(SALARY_20000) > 0)
                .count();

        log.info("{}", count);
    }

    // sort
    @Test
    public void sortSalaryTest() {
        Collection<Employee> srcEmployees = Common.getEmployeeList();

        List<Employee> dstEmployees = srcEmployees
                .stream()
                .sorted((e1, e2) -> e2.getSalary().compareTo(e1.getSalary()))
                .collect(Collectors.toList());

        log.info("{}", dstEmployees);
    }

    // distinct
    @Test
    public void distinct() {
        List<Integer> intList = Arrays.asList(2, 5, 3, 2, 4, 3);
        List<Integer> dstIntList = intList.stream().distinct().collect(Collectors.toList());

        log.info("{}", dstIntList);
    }

    // match
    @Test
    public void matchTest() {
        List<Integer> intList = Arrays.asList(2, 4, 5, 6, 8);

        boolean allMod2 = intList.stream().allMatch(i -> i % 2 == 0);
        boolean oneMod2 = intList.stream().anyMatch(i -> i % 2 == 0);
        boolean noneMod3 = intList.stream().noneMatch(i -> i % 3 == 0);

        log.info("allMod2: {}, oneMod2: {}, noneMod3: {}", allMod2, oneMod2, noneMod3);
    }

    // groupBy
    @Test
    public void groupTest() {
        List<Employee> employees = Common.getEmployeeList();
        Map<Character, List<Employee>> map = employees.stream().collect(Collectors.groupingBy(e -> e.getName().charAt(0)));

        log.info("{}", map);
    }
}
