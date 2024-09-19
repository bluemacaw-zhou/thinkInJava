package io.bluemacaw.leetcode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListNode {
    public int val;
    public ListNode next;

    public static void printList(ListNode head) {
        ListNode node = head;
        while (null != node) {
            System.out.print(node.val + " ");
            node = node.next;
        }

        System.out.println();
    }

    public static ListNode reverseList(ListNode head) {
        ListNode currentNode = head;
        ListNode currentNodePre = null;

        while (null != currentNode) {
            ListNode temp = currentNode.next;
            currentNode.next = currentNodePre;

            currentNodePre = currentNode;
            currentNode = temp;
        }

        return currentNodePre;
    }

    public static ListNode reverseListRecursion(ListNode head) {
        if (null == head || null == head.next) {
            return head;
        }

        ListNode currentNode = head;
        ListNode currentNodeNext = head.next;

        // 递归寻找最后一个节点
        ListNode newHead = reverseListRecursion(currentNodeNext);

        // 两两节点进行处理
        currentNodeNext.next = currentNode; // 后一个节点指向前一个节点
        currentNode.next = null; // 前一个节点的下一个节点指向空 避免循环链表

        return newHead; // 始终是反转后链表的头结点
    }
}
