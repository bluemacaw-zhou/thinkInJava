package io.bluemacaw.leetcode;

import org.junit.Test;

// 283 移动零
public class MoveZerosTest {
    @Test
    public void solutionTest() {
        int[] nums = new int[] {0,1,0,3,12};
        solution(nums);
        Util.printArray(nums);
    }

    private static void solution(int[] nums) {
        int j = 0;

        for (int i = 0; i < nums.length; i++) {
            if (nums[i] == 0) {
                continue;
            }

            nums[j] = nums[i];
            j++;
        }

        for (;j < nums.length; j++) {
            nums[j] = 0;
        }
    }
}
