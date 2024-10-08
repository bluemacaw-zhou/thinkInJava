package io.bluemacaw.concurrency.common;

public class SumUtils {
    /**
     * 数组求和
     */
    public static long sumRange(int[] arr, int lo, int hi) {
        long result = 0;

        for (int j = lo; j < hi; j++) {
            result += arr[j];
        }

        return result;
    }
}
