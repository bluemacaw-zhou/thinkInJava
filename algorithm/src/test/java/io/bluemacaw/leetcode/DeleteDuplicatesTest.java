package io.bluemacaw.leetcode;

import org.junit.jupiter.api.Test;

// 83 删除排序链表中的重复元素
public class DeleteDuplicatesTest {
    @Test
    public void solutionTest() {
        ListNode head = constructList();
        ListNode.printList(head);

        head = solution(head);
        ListNode.printList(head);
    }

//    public static ListNode solution(ListNode head) {
//        if(null == head) return head;
//
//        ListNode currentNode = head;
//        while (null != currentNode.next) {
//            if (currentNode.val == currentNode.next.val) {
//                currentNode.next = currentNode.next.next;
//            } else {
//                currentNode = currentNode.next;
//            }
//        }
//
//        return head;
//    }

    public static ListNode solution(ListNode head) {
        if(null == head) return head;

        ListNode slow = head;
        ListNode fast = head.next;

        while (null != fast) {
            if (slow.val == fast.val) {
                slow.next = fast.next;
                fast = fast.next;
            } else {
                slow = slow.next;
                fast = fast.next;
            }
        }

        return head;
    }

    private static ListNode constructList() {
        ListNode node5 = new ListNode(3, null);
        ListNode node4 = new ListNode(3, node5);
        ListNode node3 = new ListNode(2, node4);
        ListNode node2 = new ListNode(1, node3);
        return new ListNode(1, node2);
    }
}
