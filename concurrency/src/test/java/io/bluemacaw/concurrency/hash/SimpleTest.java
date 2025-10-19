package io.bluemacaw.concurrency.hash;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class SimpleTest {
    @Test
    public void simpleTest() {
        HashMap<String, String> hashMap = new HashMap<>();

        hashMap.put("张三", "张三-value");
        hashMap.put("王五", "王五-value");

        hashMap.put("刘一", "刘一-value");
        hashMap.put("陈二", "陈二-value");
        hashMap.put("李四", "李四-value");

        log.info("just for break");
    }
}
