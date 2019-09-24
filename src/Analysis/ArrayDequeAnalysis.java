package Analysis;

import java.util.*;

public class ArrayDequeAnalysis {

    public static void main(String[] args) {
        ArrayDequeAnalysis ada = new ArrayDequeAnalysis();
        ada.testForArrayDequeAndStackAsStack();
    }

//    // ArrayDeque 和 LinkedList 作为队列时的性能测试
//    public void testForArrayDequeAndLinkedListAsQueue() {
//        int n = 10000;
//        ArrayDeque<Integer> deque = new ArrayDeque<>();
//        LinkedList<Integer> l = new LinkedList<>();
//
//        long startTimeA = 0L;
//        long endTimeA = 0L;
//        long startTimeL = 0L;
//        long endTimeL = 0L;
//
//        // 插入
//        startTimeA = System.nanoTime();
//        insertNTimes(deque, n);
//        endTimeA = System.nanoTime();
//        System.out.println("在 ArrayDeque 插入 " + n + " 个数的时间消耗: " + (endTimeA - startTimeA) + "ns");
//
//        startTimeL = System.nanoTime();
//        insertNTimes(l, n);
//        endTimeL = System.nanoTime();
//        System.out.println("在 LinkedList 插入 " + n + " 个数的时间消耗: " + (endTimeL - startTimeL) + "ns");
//
//        // 删除
//        startTimeA = System.nanoTime();
//        removeNTimes(deque, n);
//        endTimeA = System.nanoTime();
//        System.out.println("在 ArrayDeque 删除 " + n + " 个数的时间消耗: " + (endTimeA - startTimeA) + "ns");
//
//        startTimeL = System.nanoTime();
//        removeNTimes(l, n);
//        endTimeL = System.nanoTime();
//        System.out.println("在 LinkedList 删除 " + n + " 个数的时间消耗: " + (endTimeL - startTimeL) + "ns");
//
//        // 在队列尾部插入 n 次、获取 n 次、队列头部删除 n 次，重复进行以上步骤 n 次
//        //向下取整
//        n = (int)(Math.sqrt(n));
////        int n = 10000;
//        deque.clear();
//        l.clear();
//        startTimeA = System.nanoTime();
//        insertGetRemoveNTimes(deque, n);
//        endTimeA = System.nanoTime();
//        System.out.println("在 ArrayDeque 插入，查询，删除 " + n + " 个数 " + n + " 次的时间消耗: " + (endTimeA - startTimeA) + "ns");
//        startTimeL = System.nanoTime();
//        insertGetRemoveNTimes(l, n);
//        endTimeL = System.nanoTime();
//        System.out.println("在 LinkedList 插入，查询，删除 " + n + " 个数 " + n + " 次的时间消耗: " + (endTimeL - startTimeL) + "ns");
//    }
//
//    // 在队列头部插入 n 个数
//    private void insertNTimes(Deque<Integer> dq, int n) {
//        for (int i = 0; i < n; i++) {
//            dq.addFirst(n);
//        }
//    }
//
//    // 在队列头部删除 n 个数
//    private void removeNTimes(Deque<Integer> dq, int n) {
//        for (int i = 0; i < n; i++) {
//            dq.removeFirst();
//        }
//    }
//
//    // 在队列尾部插入 n 次、获取 n 次、队列头部删除 n 次，重复进行以上步骤 n 次
//    private void insertGetRemoveNTimes(Deque<Integer> dq, int n) {
//        for (int k = 0; k < n; k++) {
//            for (int i = 0; i < n; i++)
//                dq.addFirst(i);
//            for (int i = 0; i < n; i++)
//                dq.getFirst();
//            for (int i = 0; i < n; i++)
//                dq.removeFirst();
//        }
//    }


    // ArrayDeque 和 Stack 作为栈时的性能测试
    public void testForArrayDequeAndStackAsStack() {
        int n = 100000000;
        ArrayDeque<Integer> deque = new ArrayDeque<>();
        Stack<Integer> st = new Stack<>();

        long startTimeA = 0L;
        long endTimeA = 0L;
        long startTimeL = 0L;
        long endTimeL = 0L;

//        // 插入
//        startTimeA = System.nanoTime();
//        for (int i = 0; i < n; i++) {
//            deque.addLast(n);
//        }
//        endTimeA = System.nanoTime();
//        System.out.println("在 ArrayDeque 插入 " + n + " 个数的时间消耗: " + (endTimeA - startTimeA) + "ns");
//
//        startTimeL = System.nanoTime();
//        for (int i = 0; i < n; i++) {
//            st.push(n);
//        }
//        endTimeL = System.nanoTime();
//        System.out.println("在 Stack 插入 " + n + " 个数的时间消耗: " + (endTimeL - startTimeL) + "ns");
//
//        // 删除
//        startTimeA = System.nanoTime();
//        for (int i = 0; i < n; i++) {
//            deque.removeLast();
//        }
//        endTimeA = System.nanoTime();
//        System.out.println("在 ArrayDeque 删除 " + n + " 个数的时间消耗: " + (endTimeA - startTimeA) + "ns");
//
//        startTimeL = System.nanoTime();
//        for (int i = 0; i < n; i++) {
//            st.pop();
//        }
//        endTimeL = System.nanoTime();
//        System.out.println("在 Stack 删除 " + n + " 个数的时间消耗: " + (endTimeL - startTimeL) + "ns");

        // 在队列尾部插入 n 次、获取 n 次、队列头部删除 n 次，重复进行以上步骤 n 次
        // 向下取整
        n = (int)(Math.sqrt(n));
        deque.clear();
        st.clear();
        startTimeA = System.nanoTime();
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++)
                deque.addLast(i);
            for (int i = 0; i < n; i++)
                deque.getLast();
            for (int i = 0; i < n; i++)
                deque.removeLast();
        }
        endTimeA = System.nanoTime();
        System.out.println("在 ArrayDeque 插入，查询，删除 " + n + " 个数 " + n + " 次的时间消耗: " + (endTimeA - startTimeA) + "ns");
        startTimeL = System.nanoTime();
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++)
                st.push(i);
            for (int i = 0; i < n; i++)
                st.peek();
            for (int i = 0; i < n; i++)
                st.pop();
        }
        endTimeL = System.nanoTime();
        System.out.println("在 Stack 插入，查询，删除 " + n + " 个数 " + n + " 次的时间消耗: " + (endTimeL - startTimeL) + "ns");
    }
}
