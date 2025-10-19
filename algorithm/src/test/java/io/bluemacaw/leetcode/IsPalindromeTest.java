package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

// 234 回文链表
@Slf4j
public class IsPalindromeTest {
    @Test
    public void solutionTest() {
        ListNode head = constructList();
        boolean result = solution(head);
        log.info("{}", result);
    }

    private static boolean solution(ListNode head) {
        if (null == head) return false;

        ListNode slow = head;
        ListNode fast = head;

        while(fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        if (fast != null) {
            slow = slow.next;
        }

        slow = ListNode.reverseList(slow);
        fast = head;
        while (slow != null) {
            if (fast.val != slow.val) {
                return false;
            }
            slow = slow.next;
            fast = fast.next;
        }

        return true;
    }

    public static ListNode constructList() {
        ListNode node5 = new ListNode(1, null);
        ListNode node4 = new ListNode(2, node5);
        ListNode node3 = new ListNode(3, node4);
        ListNode node2 = new ListNode(2, node3);
        return new ListNode(1, node2);
    }
}
