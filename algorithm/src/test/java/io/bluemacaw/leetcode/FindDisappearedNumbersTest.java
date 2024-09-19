package io.bluemacaw.leetcode;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

// 448 找到数组中所有消失的数字
public class FindDisappearedNumbersTest {
    @Test
    public void solutionTest() {
        int[] nums = new int[] {4,3,2,7,8,2,3,1};
        List<Integer> result = solution(nums);

        result.forEach(System.out::println);
    }

    private static List<Integer> solution(int[] nums) {
        for (int index = 0; index < nums.length; index++) {
            int operateIndex = Math.abs(nums[index]) - 1;
            if (nums[operateIndex] > 0) {
                nums[operateIndex] = -nums[operateIndex];
            }
        }

        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < nums.length; index++) {
            if (nums[index] > 0) {
                result.add(index + 1);
            }
        }

        return result;
    }
}
