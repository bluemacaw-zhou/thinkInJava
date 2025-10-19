package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

// 88 合并两个有序数组
@Slf4j
public class MergeTest {
    @Test
    public void solutionTest() {
        int[] nums1 = new int[] {1,2,3,0,0,0};
        int m = 3;

        int[] nums2 = new int[] {2,5,6};
        int n = 3;

        solution(nums1, m, nums2, n);
        log.info("just for break");
    }

    private static void solution(int[] nums1, int m, int[] nums2, int n) {
        if (n == 0) {
            return;
        }

        int mIndex = m - 1;
        int nIndex = n - 1;
        int index = m + n - 1;

        try {
            while (index >= 0) {
                if (mIndex < 0) {
                    nums1[index] = nums2[nIndex];
                    nIndex--;
                    index--;
                    continue;
                }

                if (nIndex < 0) {
                    nums1[index] = nums1[mIndex];
                    mIndex--;
                    index--;
                    continue;
                }

                if (nums1[mIndex] > nums2[nIndex]) {
                    nums1[index] = nums1[mIndex];
                    mIndex--;
                } else {
                    nums1[index] = nums2[nIndex];
                    nIndex--;
                }

                index--;
            }
        } catch (Exception e) {
            Util.printArray(nums1);
            log.error("index: " + index + ", mIndex: " + mIndex + ", nIndex: " + nIndex);
        }
    }
}
