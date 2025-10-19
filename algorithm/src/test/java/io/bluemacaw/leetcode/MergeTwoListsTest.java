package io.bluemacaw.leetcode;

import org.junit.jupiter.api.Test;

// 21 合并两个有序链表
public class MergeTwoListsTest {
    @Test
    public void solutionTest() {
        ListNode list1 = constructList1();
        ListNode list2 = constructList2();

        ListNode head = solution(list1, list2);
        ListNode.printList(head);
    }

    private static ListNode solution(ListNode list1, ListNode list2) {
        if (null == list1) return list2;
        if (null == list2) return list1;

        ListNode head = new ListNode(0, null);
        ListNode currentNode = head;
        while (list1 != null && list2 != null) {
            if (list1.val < list2.val) {
                currentNode.next = list1;
                list1 = list1.next;
            } else {
                currentNode.next = list2;
                list2 = list2.next;
            }

            currentNode = currentNode.next;
        }

        if (list1 == null) currentNode.next = list2;
        if (list2 == null) currentNode.next = list1;

        return head.next;
    }

    private static ListNode constructList1() {
        ListNode node3 = new ListNode(4, null);
        ListNode node2 = new ListNode(2, node3);
        return new ListNode(1, node2);
    }

    public static ListNode constructList2() {
        ListNode node3 = new ListNode(4, null);
        ListNode node2 = new ListNode(3, node3);
        return new ListNode(1, node2);
    }
}
