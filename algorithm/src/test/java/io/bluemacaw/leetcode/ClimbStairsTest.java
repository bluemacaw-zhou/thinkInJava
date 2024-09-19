package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

// 70 爬楼梯
@Slf4j
public class ClimbStairsTest {
    private static final List<Integer> cache = new ArrayList<>();

    static {
        cache.add(0);
        cache.add(1);
        cache.add(2);
    }

    @Test
    public void solutionTest() {
        log.info("{}", solution(5));
        log.info("{}", solution2(5));
    }

    private static int solution(int n) {
        if (cache.size() - 1 >= n) {
            return cache.get(n);
        } else {
            int result = solution(n - 1) + solution(n - 2);
            cache.add(n, result);
            return result;
        }
    }

    private static int solution2(int n) {
        int cacheSize = cache.size() - 1;
        while (cacheSize < n) {
            cacheSize++;
            int result = cache.get(cacheSize - 1) + cache.get(cacheSize - 2);
            cache.add(cacheSize, result);
        }

        return cache.get(n);
    }
}
