package Collections;

/*
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

/*
 *
 *
 *
 *
 *
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */

import java.io.Serializable;
import java.util.*;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Deque;
import java.util.Queue;
import java.util.function.Consumer;
import sun.misc.SharedSecrets;

/**
 * Deque接口的大小可调整数组的实现。Array deques 没有严格的容量限制；
 * 可以根据需要增长。它们不是线程安全的类，在缺乏外部同步的情况下，
 * 它们不支持多线程同时访问。禁止 null 元素。这个类作为堆栈的时候比
 * Stack 要快，作为队列的时候比 LinkedList 要快。
 *
 * ArrayDeque 支持的大多数运算都是在常数时间内运行。remove,
 * removeFirstOccurrence, removeLastOccurrence, contains,
 * iterator.remove() 和批量操作在线性时间内完成。
 *
 * 这个类的 iterator 方法支持 fail-fast：在迭代器创建之后如果队列被修改，
 * 除非是迭代器自身的 remove 方法，否则迭代器会抛出
 * ConcurrentModificationException 异常。因此，在面对并发修改的时候，
 * 迭代器会快速干净地失败，而不会在未来不确定的时间出现未知风险和
 * 不确定的行为。
 *
 * 这个类是 Java Collections Framework 的成员.
 *
 * @August 这个类中重要的函数有计算大小的 calculateSize，删除指定索引
 * 位置元素的 delete
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.6
 * @param <E> the type of elements held in this collection
 */
public class ArrayDeque<E> extends AbstractCollection<E>
        implements java.util.Deque<E>, Cloneable, Serializable
{
    /**
     * 队列的元素都存储在这个数组里。队列的容量就是这个数组的长度，
     * 其长度总是 2 的幂。数组永远不允许变成满的，除非是在 addX 除非
     * 是在 addX 方法中。当数组变成满的时候，它会立刻调整大小 （参阅
     * doubleCapacity），这样就避免了头和尾互相缠绕，使其相等。我们还
     * 保证所有不包含元素的数组单元格始终为 null。
     */
    transient Object[] elements; // 非私有成员，以简化嵌套类的访问。

    /**
     * 队列头部元素的索引（该元素将被 remove 或者 pop 删除）；如果
     * 队列为空，将会是等于 tail 的数。
     */
    transient int head;

    /**
     *队列尾部索引，将下一个元素添加到该索引的下一个位置（通过 addLast(E)，
     * add(E)，或者 push(E)）。
     */
    transient int tail;

    /**
     * 一个新创建的队列的最小容量。必须是 2 的幂。
     */
    private static final int MIN_INITIAL_CAPACITY = 8;

    // ******  Array allocation and resizing utilities ******
    // 数组空间分配和再分配工具

    // 计算容量，大于 numElement 且最接近 2 的整数次方的最小的数
    // 比如，3 算出来是 8，9 算出来是 16，33 算出来是 64
    private static int calculateSize(int numElements) {
        int initialCapacity = MIN_INITIAL_CAPACITY;
        if (numElements >= initialCapacity) {
            // 假设初始容量为 1010010
            initialCapacity = numElements;
            initialCapacity |= (initialCapacity >>>  1);
            // 1111011
            initialCapacity |= (initialCapacity >>>  2);
            // 1111111
            initialCapacity |= (initialCapacity >>>  4);
            // 1111111
            initialCapacity |= (initialCapacity >>>  8);
            // 1111111
            initialCapacity |= (initialCapacity >>> 16);
            // 1111111
            initialCapacity++;
            // 10000000

            if (initialCapacity < 0)   // Too many elements, must back off
                initialCapacity >>>= 1;// Good luck allocating 2 ^ 30 elements
        }
        return initialCapacity;
    }

    /**
     * 分配空数组来保存指定数量的元素。
     *
     * @param numElements  the number of elements to hold
     */
    private void allocateElements(int numElements) {
        elements = new Object[calculateSize(numElements)];
    }

    /**
     * 将队列容量设置为当前的两倍，当队列满时调用，即 head 和 tail
     * 相遇的时候。
     */
    private void doubleCapacity() {
        // assert 如果表达式为 true 则继续执行，如果为 false 抛出
        // AssertionError，并终止执行
        assert head == tail;
        int p = head;
        int n = elements.length;
        // 数组长度减去 head 位置的索引，表示 head 位置右边的元素个数。
        int r = n - p;
        // 新的容量，等于原来容量的两倍
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");
        // 创建新数组
        Object[] a = new Object[newCapacity];
        // 把索引 p 之后的元素复制到新数组从索引 0 开始的位置
        System.arraycopy(elements, p, a, 0, r);
        // 把索引 0 到 p 的元素复制到新数组从索引 r 开始的位置，复制完成
        // 之后的顺序是正确的先后顺序
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
    }

    /**
     * 按顺序（从队列的第一个元素到最后一个元素） 将元素数组中的元素
     * 复制到指定的数组中。假设数组足够大，可以容纳队列中所有元素。
     *
     * @return its argument
     */
    private <T> T[] copyElements(T[] a) {
        // head 在 tail 之前，一次复制，否则分两次复制（同 doubleCapacity）
        if (head < tail) {
            System.arraycopy(elements, head, a, 0, size());
        } else if (head > tail) {
            int headPortionLen = elements.length - head;
            System.arraycopy(elements, head, a, 0, headPortionLen);
            System.arraycopy(elements, 0, a, headPortionLen, tail);
        }
        return a;
    }

    /**
     * 构造一个容量为 16 的空队列
     */
    public ArrayDeque() {
        elements = new Object[16];
    }

    /**
     * 构造初始容量为指定大小的空队列。
     *
     * @param numElements  lower bound on initial capacity of the deque
     */
    public ArrayDeque(int numElements) {
        allocateElements(numElements);
    }

    /**
     * 构造一个包含指定集合所有元素的队列，按照集合迭代器返回的顺序。
     * （集合迭代器返回的第一个元素作为队列第一个元素，或者队列的 front）
     *
     * @param c the collection whose elements are to be placed into the deque
     * @throws NullPointerException if the specified collection is null
     */
    public ArrayDeque(java.util.Collection<? extends E> c) {
        allocateElements(c.size());
        addAll(c);
    }

    // 最核心的插入和提取方法是 addFirst，addLast，pollFirst，pollLast。
    // 其他方法根据这些来定义。

    /**
     * 在队列前插入指定元素。
     *
     * @param e the element to add
     * @throws NullPointerException if the specified element is null
     */
    public void addFirst(E e) {
        if (e == null)
            throw new NullPointerException();
        // @August 注意！！
        // 将 head 减 1，如果 head 为 0 ，运算之后指向数组末尾，防止数组
        // 到头了边界溢出，如果到头了就从末尾再往前。
        // 由于数组长度为 2 的幂，减 1 之后，之前为 1 的位置之前的位置为 0，
        // 之后的位置全为 1，所以和 head - 1 进行与运算后不 改变 head 的值。
        // 如果 head 等于 0，减 1 之后为 -1，二进制表示每一位均为 1 ，进行
        // 与运算之后 head 指向数组末尾。
        elements[head = (head - 1) & (elements.length - 1)] = e;
        if (head == tail)
            doubleCapacity();
    }

    /**
     * 把指定元素添加到队列末尾。
     *
     * This method is equivalent to {@link #add}.
     *
     * @param e the element to add
     * @throws NullPointerException if the specified element is null
     */
    public void addLast(E e) {
        if (e == null)
            throw new NullPointerException();
        // tail指向第一个没有元素的位置
        elements[tail] = e;
        // tail + 1 一旦大于 elements.length - 1，tail 马上变成 0
        if ( (tail = (tail + 1) & (elements.length - 1)) == head)
            doubleCapacity();
    }

    /**
     * 把指定元素插入到队列开头。
     * 添加成功返回 true。
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link java.util.Deque#offerFirst})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    /**
     * 把指定元素添加到队列末尾。
     * 添加成功返回 true。
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    /**
     * 删除第一个元素并返回该元素。
     * 元素为空抛出异常。
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeFirst() {
        E x = pollFirst();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    /**
     * 删除最后一个元素并返回该元素。
     * 元素为空抛出异常。
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E removeLast() {
        E x = pollLast();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    // 删除第一个元素。（将该元素设置为 null）
    // 元素为空返回 null。
    public E pollFirst() {
        int h = head;
        @SuppressWarnings("unchecked")
        E result = (E) elements[h];
        // Element is null if deque empty
        if (result == null)
            return null;
        elements[h] = null;     // Must null out slot
        head = (h + 1) & (elements.length - 1);
        return result;
    }

    // 删除最后一个元素。（将该元素设置为 null）
    // 元素为空返回 null。
    public E pollLast() {
        int t = (tail - 1) & (elements.length - 1);
        @SuppressWarnings("unchecked")
        E result = (E) elements[t];
        if (result == null)
            return null;
        elements[t] = null;
        tail = t;
        return result;
    }

    /**
     * 返回队列的第一个元素。
     * 该元素为空抛出异常。
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getFirst() {
        @SuppressWarnings("unchecked")
        E result = (E) elements[head];
        if (result == null)
            throw new NoSuchElementException();
        return result;
    }

    /**
     * 返回队列的最后一个元素。
     * 该元素为空抛出异常。
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E getLast() {
        @SuppressWarnings("unchecked")
        E result = (E) elements[(tail - 1) & (elements.length - 1)];
        if (result == null)
            throw new NoSuchElementException();
        return result;
    }

    // 返回队列的第一个元素
    @SuppressWarnings("unchecked")
    public E peekFirst() {
        // elements[head] is null if deque empty
        return (E) elements[head];
    }

    // 返回队列的最后一个元素
    @SuppressWarnings("unchecked")
    public E peekLast() {
        return (E) elements[(tail - 1) & (elements.length - 1)];
    }

    /**
     * 删除队列中指定元素的第一个出现项（从头到尾遍历）。
     * 如果队列不包含该元素，不作出任何改变。
     * 如果队列包含指定元素返回 true。
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeFirstOccurrence(Object o) {
        if (o == null)
            return false;
        // mask：面具
        int mask = elements.length - 1;
        int i = head;
        Object x;
        while ( (x = elements[i]) != null) {
            if (o.equals(x)) {
                delete(i);
                return true;
            }
            i = (i + 1) & mask;
        }
        return false;
    }

    /**
     * 删除队列中指定元素的最后一个出现项（从尾到头遍历）。
     * 如果队列不包含该元素，不作出任何改变。
     * 如果队列包含指定元素返回 true。
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeLastOccurrence(Object o) {
        if (o == null)
            return false;
        int mask = elements.length - 1;
        int i = (tail - 1) & mask;
        Object x;
        while ( (x = elements[i]) != null) {
            if (o.equals(x)) {
                delete(i);
                return true;
            }
            i = (i - 1) & mask;
        }
        return false;
    }

    // *** Queue methods ***
    // 队列相关方法

    /**
     * 把指定元素插入到队列尾部
     *
     * 此方法等价于 addLast
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    /**
     * 把指定元素插入到队列尾部
     *
     * 此方法等价于 offerLast
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        return offerLast(e);
    }

    /**
     * 检索并删除队列的头部元素
     *
     * 此方法和 poll 的区别只有：如果队列为空它会抛出异常
     *
     * 此方法等价于 removeFirst
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E remove() {
        return removeFirst();
    }

    /**
     * 检索并删除队列的头部元素（即队列的第一个元素），如果队列为空
     * 返回 null。
     *
     * 此方法等价于 pollFirst。
     *
     * @return the head of the queue represented by this deque, or
     *         {@code null} if this deque is empty
     */
    public E poll() {
        return pollFirst();
    }

    /**
     * 检索但不删除队列的头部元素。这个方法和 peek 不同的地方只有：如果
     * 队列为空会抛出异常。
     *
     * 这个方法等价于 getFirst。
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E element() {
        return getFirst();
    }

    /**
     * 检索但不删除队列的头部元素，如果队列为空返回 null。
     *
     * 此方法等价于 peekFirst。
     *
     * @return the head of the queue represented by this deque, or
     *         {@code null} if this deque is empty
     */
    public E peek() {
        return peekFirst();
    }

    // *** Stack methods ***
    // 堆栈相关操作

    /**
     * 把元素 push 到队列代表的堆栈里面。换句话说，把元素插入到队列头部。
     *
     * 此方法等价于 addFirst。
     *
     * @param e the element to push
     * @throws NullPointerException if the specified element is null
     */
    public void push(E e) {
        addFirst(e);
    }

    /**
     * 对队列所代表的的堆栈进行 pop 操作。换句话说，删除并返回队列的
     * 第一个元素。
     *
     * 此方法等价于 removeFirst。
     *
     * @return the element at the front of this deque (which is the top
     *         of the stack represented by this deque)
     * @throws NoSuchElementException {@inheritDoc}
     */
    public E pop() {
        return removeFirst();
    }


    // 检查
    private void checkInvariants() {
        // assert 如果表达式为 true 则继续执行，如果为 false 抛出
        // AssertionError，并终止执行
        // tail 必须为 null
        assert elements[tail] == null;
        // 如果 head 等于 tail，那么 head 必须为 null，如果 head 不等于 tail，
        // 那么 head 不能为 null 且 tail - 1 不能为 null。
        assert head == tail ? elements[head] == null :
                (elements[head] != null &&
                        elements[(tail - 1) & (elements.length - 1)] != null);
        // head 的前一个必须为 null
        assert elements[(head - 1) & (elements.length - 1)] == null;
    }

    /**
     * 删除指定位置的元素，根据需要调整 head 和 tail。这可能导致数组中
     * 的元素向后或向前移动。
     *
     * 这个方法被称为 delete 而不是 remove，是为了强调它的语义和
     * remove 不同。
     *
     * @return true if elements moved backwards
     */
    private boolean delete(int i) {
        checkInvariants();
        final Object[] elements = this.elements;
        final int mask = elements.length - 1;
        final int h = head;
        final int t = tail;

        // 索引 i 前面的元素个数
        final int front = (i - h) & mask;
        // 索引 i 后面的元素个数
        final int back  = (t - i) & mask;

        // (t - h) & mask 表示数组中已经插入的元素个数，如果此表达式成立则
        // 抛出 ConcurrentModificationException 异常
        // Invariant: head <= i < tail mod circularity
        if (front >= ((t - h) & mask))
            throw new ConcurrentModificationException();

        // Optimize for least element motion
        // 判断索引 i 位于队列的前半部分还是后半部分。从而决定移动的方向，
        // 保证需要移动的元素个数最少
        // 若 front 小于 back，将目标元素之前的元素往后移动
        if (front < back) {
            if (h <= i) {
                System.arraycopy(elements, h, elements, h + 1, front);
            } else { // Wrap around
                System.arraycopy(elements, 0, elements, 1, i);
                elements[0] = elements[mask];
                System.arraycopy(elements, h, elements, h + 1, mask - h);
            }
            elements[h] = null;
            head = (h + 1) & mask;
            return false;
        } else { // 若 front 大于等于 back，将目标元素之后的元素向前移动
            if (i < t) { // Copy the null tail as well
                System.arraycopy(elements, i + 1, elements, i, back);
                tail = t - 1;
            } else { // Wrap around
                System.arraycopy(elements, i + 1, elements, i, mask - i);
                elements[mask] = elements[0];
                System.arraycopy(elements, 1, elements, 0, t);
                tail = (t - 1) & mask;
            }
            return true;
        }
    }

    // *** Collection Methods ***
    // 集合相关的方法

    /**
     * 返回队列中元素的个数
     *
     * @return the number of elements in this deque
     */
    public int size() {
        return (tail - head) & (elements.length - 1);
    }

    /**
     * 如果队列不包含任何元素返回 true。
     *
     * @return {@code true} if this deque contains no elements
     */
    public boolean isEmpty() {
        return head == tail;
    }

    /**
     * 返回队列所有元素的迭代器。顺序是从第一个元素（ head ）到 （ tail ）。
     * 这个顺序也是元素出队列的顺序。
     *
     * @return an iterator over the elements in this deque
     */
    public Iterator<E> iterator() {
        return new DeqIterator();
    }

    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    // 队列迭代器
    private class DeqIterator implements Iterator<E> {
        /**
         * 正序遍历从头结点开始。
         */
        private int cursor = head;

        /**
         *
         * 创建和删除迭代器时记录尾部位置，为了能停止迭代器以及检查并发修改。
         */
        private int fence = tail;

        /**
         * 上一个返回元素的索引。如果删除元素，则 lastRet 设置为 -1。
         */
        private int lastRet = -1;

        public boolean hasNext() {
            return cursor != fence;
        }

        // 返回 cursor 并向后移动一位
        public E next() {
            if (cursor == fence)
                throw new NoSuchElementException();
            @SuppressWarnings("unchecked")
            E result = (E) elements[cursor];
            if (tail != fence || result == null)
                throw new ConcurrentModificationException();
            lastRet = cursor;
            cursor = (cursor + 1) & (elements.length - 1);
            return result;
        }

        // 删除 lastRet 位置的元素
        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            if (delete(lastRet)) { // if left-shifted, undo increment in next()
                cursor = (cursor - 1) & (elements.length - 1);
                fence = tail;
            }
            lastRet = -1;
        }

        // 从 cursor 开始的遍历
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            Object[] a = elements;
            int m = a.length - 1, f = fence, i = cursor;
            cursor = f;
            while (i != f) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                i = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                action.accept(e);
            }
        }
    }

    private class DescendingIterator implements Iterator<E> {
        /*
         * 倒序从尾结点开始的遍历。
         */
        private int cursor = tail;
        private int fence = head;
        private int lastRet = -1;

        public boolean hasNext() {
            return cursor != fence;
        }

        public E next() {
            if (cursor == fence)
                throw new NoSuchElementException();
            cursor = (cursor - 1) & (elements.length - 1);
            @SuppressWarnings("unchecked")
            E result = (E) elements[cursor];
            if (head != fence || result == null)
                throw new ConcurrentModificationException();
            lastRet = cursor;
            return result;
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            if (!delete(lastRet)) {
                cursor = (cursor + 1) & (elements.length - 1);
                fence = head;
            }
            lastRet = -1;
        }
    }

    /**
     * 如果队列包含指定元素返回 true。
     *
     * @param o object to be checked for containment in this deque
     * @return {@code true} if this deque contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null)
            return false;
        int mask = elements.length - 1;
        int i = head;
        Object x;
        // 遍历队列所有元素直到找到为止，否则返回 false。
        while ( (x = elements[i]) != null) {
            if (o.equals(x))
                return true;
            i = (i + 1) & mask;
        }
        return false;
    }

    /**
     * 从队列中删除指定元素的第一个实例。如果队列不包含该元素不作出任何
     * 改变。如果包含指定元素返回 true。
     *
     * 此方法等价于 removeFirstOccurrence。
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if this deque contained the specified element
     */
    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    /**
     * 从队列中删除所有元素。此方法调用后队列为空。
     */
    public void clear() {
        int h = head;
        int t = tail;
        if (h != t) { // clear all cells
            head = tail = 0;
            int i = h;
            int mask = elements.length - 1;
            do {
                // 将所有元素设置为 null
                elements[i] = null;
                i = (i + 1) & mask;
            } while (i != t);
        }
    }

    /**
     * 返回一个包含队列所有元素的数组，顺序为从第一个元素到最后一个元素。
     *
     * 返回的数组是“安全”的，因为队列不会保留任何对它的引用。即该数组保存
     * 在新分配的内存空间里。调用者可以任意修改返回的数组。
     *
     * @return an array containing all of the elements in this deque
     */
    public Object[] toArray() {
        return copyElements(new Object[size()]);
    }

    /**
     * 返回一个按正确的顺序包含 deque 中的所有元素的数组
     * （从第一个元素到最后一个元素）；返回数组的运行时类型是指定数组的
     * 运行时类型。如果指定的数组能容纳队列的所有元素，则返回指定数组。
     * 否则，将按照指定数组的运行时类型和该 deque 的大小分配一个新数组。
     *
     * 如果指定数组还有空余的位置，则将其设置为 null。
     *
     * @param a the array into which the elements of the deque are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this deque
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this deque
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        int size = size();
        if (a.length < size)
            a = (T[])java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), size);
        copyElements(a);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    // *** Object methods ***
    // Object 相关操作

    /**
     * 返回队列的克隆
     *
     * @return a copy of this deque
     */
    public ArrayDeque<E> clone() {
        try {
            @SuppressWarnings("unchecked")
            ArrayDeque<E> result = (ArrayDeque<E>) super.clone();
            // 新队列的所有元素存储在新的数组空间里，和原队列没有任何
            result.elements = Arrays.copyOf(elements, elements.length);
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    private static final long serialVersionUID = 2340985798034038923L;

    /**
     * 把队列存储在 stream 里，即序列化。
     *
     * @serialData The current size ({@code int}) of the deque,
     * followed by all of its elements (each an object reference) in
     * first-to-last order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size());

        // Write out elements in order.
        int mask = elements.length - 1;
        for (int i = head; i != tail; i = (i + 1) & mask)
            s.writeObject(elements[i]);
    }

    /**
     * 反序列化。
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in size and allocate array
        int size = s.readInt();
        int capacity = calculateSize(size);
        SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, capacity);
        allocateElements(size);
        head = 0;
        tail = size;

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            elements[i] = s.readObject();
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * deque.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#ORDERED}, and
     * {@link Spliterator#NONNULL}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this deque
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new DeqSpliterator<E>(this, -1, -1);
    }

    static final class DeqSpliterator<E> implements Spliterator<E> {
        private final ArrayDeque<E> deq;
        private int fence;  // -1 until first use
        private int index;  // current index, modified on traverse/split

        /** Creates new spliterator covering the given array and range */
        DeqSpliterator(ArrayDeque<E> deq, int origin, int fence) {
            this.deq = deq;
            this.index = origin;
            this.fence = fence;
        }

        private int getFence() { // force initialization
            int t;
            if ((t = fence) < 0) {
                t = fence = deq.tail;
                index = deq.head;
            }
            return t;
        }

        public DeqSpliterator<E> trySplit() {
            int t = getFence(), h = index, n = deq.elements.length;
            if (h != t && ((h + 1) & (n - 1)) != t) {
                if (h > t)
                    t += n;
                int m = ((h + t) >>> 1) & (n - 1);
                return new DeqSpliterator<E>(deq, h, index = m);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> consumer) {
            if (consumer == null)
                throw new NullPointerException();
            Object[] a = deq.elements;
            int m = a.length - 1, f = getFence(), i = index;
            index = f;
            while (i != f) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                i = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                consumer.accept(e);
            }
        }

        public boolean tryAdvance(Consumer<? super E> consumer) {
            if (consumer == null)
                throw new NullPointerException();
            Object[] a = deq.elements;
            int m = a.length - 1, f = getFence(), i = index;
            if (i != fence) {
                @SuppressWarnings("unchecked") E e = (E)a[i];
                index = (i + 1) & m;
                if (e == null)
                    throw new ConcurrentModificationException();
                consumer.accept(e);
                return true;
            }
            return false;
        }

        public long estimateSize() {
            int n = getFence() - index;
            if (n < 0)
                n += deq.elements.length;
            return (long) n;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED |
                    Spliterator.NONNULL | Spliterator.SUBSIZED;
        }
    }

}

