package io.bluemacaw.concurrency.hash;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;

@Slf4j
public class FailFastTest {
    @Test
    public void failFastTest() {
        ArrayList<User> users = new ArrayList<>();
        users.add(new User("Michael", 31));
        users.add(new User("Dendy", 32));
        users.add(new User("Jack", 32));

        Util.printUsers(users);

        // fast fail example
        Iterator<User> iterator = users.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getName().equals("Michael")) {
                // users.add(new User("Park", 30)); // 能触发 ConcurrentModifyException 添加新的元素
                // users.remove(1); // 能触发 ConcurrentModifyException 删除下一个指针
                // iterator.remove(); // 选择删除当前
            }
        }

        // users.removeIf(user -> user.getName().equals("Michael")); // 选择删除正确的方式

        Util.printUsers(users);
    }
}
