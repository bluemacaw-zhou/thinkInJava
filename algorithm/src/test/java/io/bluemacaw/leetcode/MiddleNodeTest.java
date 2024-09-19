package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;

// 876 链表的中间节点
@Slf4j
public class MiddleNodeTest {
    public static void main(String[] args) {
        ListNode head = constructList();
        ListNode node = solution(head);
        log.info("{}", node.val);
    }

    private static ListNode solution(ListNode head) {
        if (null == head) return null;

        ListNode slow = head;
        ListNode fast = head;

        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        return slow;
    }

    private static ListNode constructList() {
//        ListNode node5 = new ListNode(5, null);
        ListNode node4 = new ListNode(4, null);
        ListNode node3 = new ListNode(3, node4);
        ListNode node2 = new ListNode(2, node3);
        return new ListNode(1, node2);
    }
}
