package io.bluemacaw.stream;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Common {
    public static Employee[] constructEmployees() {
        Employee[] result = {
                new Employee(1L, "Jeff Bezos", new BigDecimal("10000.0")),
                new Employee(2L, "Bill Gates", new BigDecimal("20000.0")),
                new Employee(3L, "Mark Zuckerberg", new BigDecimal("30000.0"))
        };

        return result;
    }

    public static Employee[] constructEmployeeArray(Collection<Employee> employees) {
        return employees.toArray(new Employee[0]);
    }

    public static List<Employee> getEmployeeList() {
        return Arrays.asList(constructEmployees());
    }

    public static List<Employee> getEmployeeList(Employee[] employees) {
        return Arrays.asList(employees);
    }

    public static List<Employee> constructEmployeeListStatic(Employee[] employees) {
        return Stream.of(employees[0], employees[1], employees[3]).collect(Collectors.toList());
    }

    public static List<Employee> constructEmployeeListDynamic(Employee[] employees) {
        Stream.Builder<Employee> employeeBuilder = Stream.builder();
        for (Employee employee : employees) {
            employeeBuilder.accept(employee);
        }
        return employeeBuilder.build().collect(Collectors.toList());
    }

    public static List<List<String>> constructNameNested() {
        return Arrays.asList(
                   Arrays.asList("Jeff", "Bezos"),
                   Arrays.asList("Bill", "Gates"),
                   Arrays.asList("Mark", "Zuckerberg"));
    }
}
