/*
 * Copyright (c) 1994, 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package Collections;

import java.util.EmptyStackException;

/**
 * 此 Stack 类表示后进先出 (LIFO) 的数据结构。它在 Vector 类的基础上
 * 扩展了五个操作，这些操作让向量成为了堆栈。它提供了 push 和 pop
 * 操作，一个获取堆栈顶部元素的 peek 方法，还有一个测试堆栈是否为
 * 空的 empty 方法和一个搜索指定元素离栈顶有多远的 search 方法。
 * 当一个栈被创建的时候，它不包含任何元素。
 *
 * Deque 接口中提供了更多 LIFO 操作的实现，使用中应该优先考虑 Deque。
 * 可以通过以下方式创建该类的对象：
 * Deque<Integer> stack = new ArrayDeque<Integer>();
 *
 * @author  Jonathan Payne
 * @since   JDK1.0
 */
public
class Stack<E> extends Vector<E> {
    /**
     * 创建一个空的栈
     */
    public Stack() {
    }

    /**
     * 将一个元素压到栈顶，此方法和 addElement 方法效果一样。
     *
     * @param   item   the item to be pushed onto this stack.
     * @return  the item argument.
     * @see     java.util.Vector#addElement
     */
    public E push(E item) {
        addElement(item);
        return item;
    }

    /**
     * 移除栈顶元素并返回该元素。
     *
     * @return  The object at the top of this stack (the last item
     *          of the Vector object).
     * @throws  EmptyStackException  if this stack is empty.
     */
    public synchronized E pop() {
        E       obj;
        int     len = size();

        obj = peek();
        removeElementAt(len - 1);

        return obj;
    }

    /**
     * 返回栈顶元素（不删除）。
     *
     * @return  the object at the top of this stack (the last item
     *          of the Vector object).
     * @throws  EmptyStackException  if this stack is empty.
     */
    public synchronized E peek() {
        int     len = size();
        if (len == 0)
            throw new EmptyStackException();
        return elementAt(len - 1);
    }

    /**
     * 判断栈是否为空。
     *
     * @return  true if and only if this stack contains
     *          no items; false otherwise.
     */
    public boolean empty() {
        return size() == 0;
    }

    /**
     * 返回栈中元素的位置。如果对象 o 出现在栈中，这个方法返回离栈顶
     * 最近的该元素的距离。栈顶元素的距离被定义成 1。equals 用来判断
     * 是否匹配。
     *
     * @param   o   the desired object.
     * @return  the 1-based position from the top of the stack where
     *          the object is located; the return value -1
     *          indicates that the object is not on the stack.
     */
    public synchronized int search(Object o) {
        int i = lastIndexOf(o);

        if (i >= 0) {
            return size() - i;
        }
        return -1;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = 1224463164541339165L;
}
