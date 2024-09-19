package io.bluemacaw.leetcode;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

// 160 相交链表
@Slf4j
public class GetIntersectionNodeTest {
    private static final ListNode nodeC3 = new ListNode(5, null);
    private static final ListNode nodeC2 = new ListNode(4, nodeC3);
    private static final ListNode nodeC1 = new ListNode(8, nodeC2);

    @Test
    public void solutionTest() {
        ListNode headA = constructList1();
        ListNode headB = constructList2();

        ListNode node = solution(headA, headB);
        log.info("{}", node.val);
    }

    private static ListNode solution(ListNode headA, ListNode headB) {
        if (headA == null || headB == null) return null;

        ListNode travelA = headA;
        ListNode travelB = headB;

        while (travelA != travelB) {
            travelA = (travelA == null) ? headB : travelA.next;
            travelB = (travelB == null) ? headA : travelB.next;
        }

        return travelA;
    }

    private static ListNode constructList1() {
        ListNode node2 = new ListNode(1, nodeC1);
        return new ListNode(4, node2);
    }

    private static ListNode constructList2() {
        ListNode node3 = new ListNode(1, nodeC1);
        ListNode node2 = new ListNode(6, node3);
        return new ListNode(5, node2);
    }
}
