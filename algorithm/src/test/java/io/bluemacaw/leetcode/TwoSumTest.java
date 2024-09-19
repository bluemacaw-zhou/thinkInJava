package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

// 1 两数之和
@Slf4j
public class TwoSumTest {
    public void solutionTest() {
        // log.info("{}", solution());
    }

    // 暴力解法
    public int[] solution(int[] nums, int target){
        int[] result = new int[2];
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    result[0] = i;
                    result[1] = j;
                }
            }
        }

        return result;
    }

    private static final HashMap<Integer, Integer> cache = new HashMap<>();
    public static int[] solution2(int[] nums, int target) {
        int[] result = new int[2];

        for (int i = 0; i < nums.length; i++) {
            if(cache.containsKey(target - nums[i])) {
                result[0] = i;
                result[1] = cache.get(target - nums[i]);
                break;
            } else {
                cache.put(nums[i], i);
            }
        }

        return result;
    }
}
