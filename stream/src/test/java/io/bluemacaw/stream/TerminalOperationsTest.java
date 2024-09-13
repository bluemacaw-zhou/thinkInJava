package io.bluemacaw.stream;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
                .orElse(null);

        System.out.println(employee);
    }

    // count
    @Test
    public void findSalaryMoreThan20000CountTest() {
        Collection<Employee> employees = Common.getEmployeeList();

        Long count = employees
                .stream()
                .filter(e -> e.getSalary().compareTo(SALARY_20000) > 0)
                .count();

        System.out.println(count);
    }

    // sort
    @Test
    public void sortSalaryTest() {
        Collection<Employee> srcEmployees = Common.getEmployeeList();

        List<Employee> dstEmployees = srcEmployees
                .stream()
                .sorted((e1, e2) -> e2.getSalary().compareTo(e1.getSalary()))
                .collect(Collectors.toList());

        System.out.println(dstEmployees);
    }

    // distinct
    @Test
    public void distinct() {
        List<Integer> intList = Arrays.asList(2, 5, 3, 2, 4, 3);
        List<Integer> dstIntList = intList.stream().distinct().collect(Collectors.toList());
        System.out.println(dstIntList);
    }

    // match
    @Test
    public void matchTest() {
        List<Integer> intList = Arrays.asList(2, 4, 5, 6, 8);

        boolean allMod2 = intList.stream().allMatch(i -> i % 2 == 0);
        boolean oneMod2 = intList.stream().anyMatch(i -> i % 2 == 0);
        boolean noneMod3 = intList.stream().noneMatch(i -> i % 3 == 0);

        System.out.println("allMod2: " + allMod2 + ", oneMod2: " + oneMod2 + ", noneMod3: " + noneMod3);
    }

    // groupBy
}
