package io.bluemacaw.concurrency.hash;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
public class Util {
    public static void printUsers(ArrayList<User> users) {
        for (User user : users) {
            log.info("{}", user);
        }

        log.info("print finish");
    }
}
