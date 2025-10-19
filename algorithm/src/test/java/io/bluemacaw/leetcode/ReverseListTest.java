package io.bluemacaw.leetcode;


import org.junit.jupiter.api.Test;

// 206 反转链表
public class ReverseListTest {
    @Test
    public void solutionTest() {
        ListNode head = constructList();
        ListNode.printList(head);

        // 翻转链表
        ListNode newHead = ListNode.reverseList(head);
        ListNode.printList(newHead);

//        ListNode newHead = ListNode.reverseListRecursion(head);
//        ListNode.printList(newHead);
    }

    public static ListNode constructList() {
        ListNode node5 = new ListNode(5, null);
        ListNode node4 = new ListNode(4, node5);
        ListNode node3 = new ListNode(3, node4);
        ListNode node2 = new ListNode(2, node3);
        return new ListNode(1, node2);
    }
}
