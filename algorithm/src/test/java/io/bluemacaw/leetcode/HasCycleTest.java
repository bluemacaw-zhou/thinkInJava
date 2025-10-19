package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

// 141 环形链表
@Slf4j
public class HasCycleTest {
    @Test
    public void solutionTest() {
        ListNode head = constructList();
        boolean result = solution(head);
        log.info("{}", result);
    }

    private static boolean solution(ListNode head) {
        if (null == head) return false;

        ListNode slow = head.next;
        if (null == slow) {
            return false;
        }

        ListNode fast = head.next.next;

        while (null != fast && null != slow) {
            if (fast == slow) {
                return true;
            }

            slow = slow.next;
            if (null != fast.next) {
                fast = fast.next.next;
            } else {
                return false;
            }
        }

        return false;
    }

    private static ListNode constructList() {
        ListNode node4 = new ListNode(4, null);
        ListNode node3 = new ListNode(3, node4);
        ListNode node2 = new ListNode(2, node3);
        ListNode node1 = new ListNode(1, node2);

        node4.next = node2;

        return node1;
    }
}
