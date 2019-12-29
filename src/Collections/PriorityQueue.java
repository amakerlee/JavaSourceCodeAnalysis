package Collections;

/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Consumer;
import sun.misc.SharedSecrets;

/**
 * 一个基于优先级堆的无界优先级队列。根据 Comparable 比较器的自然顺序
 * 确定优先级元素的排列顺序，或者根据构造队列时创建的 Comparator 比较器
 * 排列队列元素。优先级队列不允许 null 元素。依赖于自然顺序的优先级队列
 * 也不允许插入不可比较的对象（这样做可能抛出 ClassCastException 异常）。
 *
 * 队列的头部元素是于指定顺序相关的最小的元素。如果多个元素满足该条件，
 * 那么头部元素是其中任意一个。队列的检索操作 poll, remove, peek, element
 * 等会访问队列的头部元素。
 *
 * 优先级队列是无界的，但是具有控制数组大小的内部容量。该容量应该大于等于
 * 队列的大小。当元素被添加到优先级队列中时，其容量自动增加。没有指定
 * 具体的增长策略。
 *
 * 这个类和它的迭代器实现了 Collection 和 Iterator 接口的所有可选方法。
 * iterator 方法提供的迭代器不能保证以任何特定的顺序遍历优先级队列的元素。
 * 如果需要有序遍历，考虑使用 Arrays.sort(pq.toArray())。
 *
 * 注意该实现不是同步的。多线程不应该同时修改优先级队列，而应该使用线程
 * 安全的 PriorityBlockingQueue 类。
 *
 * 此实现提供了时间代价为 O(log(n)) 的入队和出队方法：offer, poll, remove,
 * add；提供了线性时间代价的 remove 和 contains 方法；除此之外，还有常数
 * 时间代价的 peek, element, size 方法。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @since 1.5
 * @author Josh Bloch, Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class PriorityQueue<E> extends AbstractQueue<E>
        implements java.io.Serializable {

    private static final long serialVersionUID = -7720805057305804111L;

    // 默认初始容量
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * 优先级队列表现为一个平衡的二进制堆：queue[n] 的两个子队列分别为
     * queue[2*n+1] 和 queue[2*(n+1)]。如果队列的顺序由比较器决定，或者按
     * 元素的自然顺序排序。对于堆中的每个节点 n 和 n 的每个后代 d，有 n <= d。
     * 假定队列非空，堆中最小值的元素为 queue[0]。
     */
    transient Object[] queue; // non-private to simplify nested class access

    /**
     * 优先级队列中的元素个数。
     */
    private int size = 0;

    /**
     * 比较器。如果使用元素的自然顺序排序的话此比较器为 null。
     */
    private final Comparator<? super E> comparator;

    /**
     * 优先级队列被结构性修改的次数。
     */
    transient int modCount = 0; // non-private to simplify nested class access

    /**
     * 创建一个容量为默认初始容量的优先级队列。其中元素的顺序为
     * Comparable 自然顺序。
     */
    public PriorityQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    /**
     * 创建一个特定初始容量的优先级队列，其中元素的顺序为 Comparable 的
     * 自然顺序。
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @throws IllegalArgumentException if {@code initialCapacity} is less
     *         than 1
     */
    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * 创建一个容量为默认初始容量，元素排列顺序为比较器指定顺序的优先级
     * 队列。
     *
     * @param  comparator the comparator that will be used to order this
     *         priority queue.  If {@code null}, the {@linkplain Comparable
     *         natural ordering} of the elements will be used.
     * @since 1.8
     */
    public PriorityQueue(Comparator<? super E> comparator) {
        this(DEFAULT_INITIAL_CAPACITY, comparator);
    }

    /**
     * 创建一个容量为默认初始容量，元素排列顺序为比较器指定顺序的优先级
     * 队列。
     *
     * @param  initialCapacity the initial capacity for this priority queue
     * @param  comparator the comparator that will be used to order this
     *         priority queue.  If {@code null}, the {@linkplain Comparable
     *         natural ordering} of the elements will be used.
     * @throws IllegalArgumentException if {@code initialCapacity} is
     *         less than 1
     */
    public PriorityQueue(int initialCapacity,
                         Comparator<? super E> comparator) {
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.queue = new Object[initialCapacity];
        this.comparator = comparator;
    }

    /**
     * 创建一个包含指定集合所有元素的优先级队列。如果指定集合是一个
     * SortedSet 的实例或者是另一个 PriorityQueue，这个优先级队列中的元素
     * 会以相同的顺序排列。否则，这个优先级队列将根据元素的自然顺序排列。
     *
     * @param  c the collection whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified collection
     *         cannot be compared to one another according to the priority
     *         queue's ordering
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(java.util.Collection<? extends E> c) {
        if (c instanceof SortedSet<?>) {
            SortedSet<? extends E> ss = (SortedSet<? extends E>) c;
            this.comparator = (Comparator<? super E>) ss.comparator();
            initElementsFromCollection(ss);
        }
        else if (c instanceof java.util.PriorityQueue<?>) {
            java.util.PriorityQueue<? extends E> pq = (java.util.PriorityQueue<? extends E>) c;
            this.comparator = (Comparator<? super E>) pq.comparator();
            initFromPriorityQueue(pq);
        }
        else {
            this.comparator = null;
            initFromCollection(c);
        }
    }

    /**
     * 创建一个包含指定集合所有元素的优先级队列。这个优先级队列中的元素
     * 会以指定队列相同的顺序排序。
     *
     * @param  c the priority queue whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of {@code c} cannot be
     *         compared to one another according to {@code c}'s
     *         ordering
     * @throws NullPointerException if the specified priority queue or any
     *         of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(java.util.PriorityQueue<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initFromPriorityQueue(c);
    }

    /**
     * 创建一个包含指定 SortedSet 的所有元素的优先级队列。这个优先级队列
     * 中的元素以给定 SortedSet 的顺序排列。
     *
     * @param  c the sorted set whose elements are to be placed
     *         into this priority queue
     * @throws ClassCastException if elements of the specified sorted
     *         set cannot be compared to one another according to the
     *         sorted set's ordering
     * @throws NullPointerException if the specified sorted set or any
     *         of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(SortedSet<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initElementsFromCollection(c);
    }

    // 根据给定的优先级队列初始化此优先级队列，将所有元素复制到此队列中
    // 直接将给定的优先级队列转化为数组，赋值给此队列
    private void initFromPriorityQueue(java.util.PriorityQueue<? extends E> c) {
        if (c.getClass() == java.util.PriorityQueue.class) {
            this.queue = c.toArray();
            this.size = c.size();
        } else {
            initFromCollection(c);
        }
    }

    // 根据给定的 Collection 初始化此优先级队列，将所有元素复制到此队列中
    private void initElementsFromCollection(java.util.Collection<? extends E> c) {
        Object[] a = c.toArray();
        // If c.toArray incorrectly doesn't return Object[], copy it.
        if (a.getClass() != Object[].class)
            // Arrays的copyOf()方法传回的数组是新的数组对象，改变传回数组中
            // 的元素值，不会影响原来的数组。新数组的类型为 Object[]。
            a = Arrays.copyOf(a, a.length, Object[].class);
        int len = a.length;
        // 目的是检查数组即优先级队列中是否有 null 元素，如果有则抛出异常
        if (len == 1 || this.comparator != null)
            for (int i = 0; i < len; i++)
                if (a[i] == null)
                    throw new NullPointerException();
        this.queue = a;
        this.size = a.length;
    }

    /**
     * 根据给定的集合中的元素初始化此队列。
     * Initializes queue array with elements from the given Collection.
     *
     * @param c the collection
     */
    private void initFromCollection(java.util.Collection<? extends E> c) {
        initElementsFromCollection(c);
        // 堆处理
        heapify();
    }

    /**
     * 数组最多能容纳的元素个数。
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 增加数组容量
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        int oldCapacity = queue.length;
        // 如果原容量小于 64，新容量变成 2 * oldCapacity + 2，否则，新容量
        // 变成 1.5 * oldCapacity
        int newCapacity = oldCapacity + ((oldCapacity < 64) ?
                (oldCapacity + 2) :
                (oldCapacity >> 1));
        // 如果新容量比规定的最大容量还要大，那么将新容量设置为整型最大值
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        queue = Arrays.copyOf(queue, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        // 指定容量大于规定的最大容量，将新容量设置为整型最大值，否则设置
        // 为规定的最大容量。
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    /**
     * 将指定元素插入到优先级队列中
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * 将指定元素插入到优先级队列中
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws ClassCastException if the specified element cannot be
     *         compared with elements currently in this priority queue
     *         according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        modCount++;
        int i = size;
        // 扩容，确保队列容量不溢出
        if (i >= queue.length)
            grow(i + 1);
        size = i + 1;
        if (i == 0)
            queue[0] = e;
        else
            // 执行添加操作
            siftUp(i, e);
        return true;
    }

    @SuppressWarnings("unchecked")
    // 返回队列第一个元素（堆顶元素）
    public E peek() {
        return (size == 0) ? null : (E) queue[0];
    }

    // 返回指定元素索引
    private int indexOf(Object o) {
        if (o != null) {
            for (int i = 0; i < size; i++)
                if (o.equals(queue[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 从队列中删除指定元素的单个匹配实例。如果存在一个或多个删除任意一个。
     * 当队列包含指定元素时返回 true（表示操作成功）
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        int i = indexOf(o);
        if (i == -1)
            return false;
        else {
            removeAt(i);
            return true;
        }
    }

    /**
     * 和 remove 不同的是，判断相等时直接判断引用，不使用 equals 方法
     * iterator.remove 需要此方法
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if removed
     */
    boolean removeEq(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == queue[i]) {
                removeAt(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 如果队列包含指定元素返回 true，判断方是否相等使用 equals
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    /**
     * 返回包含队列中所有元素的数组
     * 这些元素没有特定的顺序
     *
     * 返回的数组是“安全”的，因为队列不保留对它的引用。（换句话说，数组
     * 空间是新分配的）。调用者可以随意修改返回的数组。
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        return Arrays.copyOf(queue, size);
    }

    /**
     * 返回一个包含队列中的所有元素的数组返回数组的运行时类型是指定数组的
     * 运行时类型。数组中元素的顺序没有规定。如果指定的数组能容纳队列的
     * 所有元素，则返回指定数组。否则，将按照指定数组的运行时类型和该队列
     * 的大小分配一个新数组。
     *
     * 如果指定数组还有空余的位置，则将其设置为 null。
     *
     * 和 toArray 方法一样，将此方法作为沟通基于数组和基于集合的 API 的桥梁。
     * 除此之外，此方法允许对输出数组的运行时类型进行控制，在精确的计算下，
     * 可以用来节省空间。
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final int size = this.size;
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOf(queue, size, a.getClass());
        System.arraycopy(queue, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    /**
     * 返回队列元素的迭代器。迭代器中元素没有特定顺序。
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private final class Itr implements Iterator<E> {

        private int cursor = 0;

        private int lastRet = -1;

        /**
         * 从堆中未访问的部分移动到已访问的部分的元素，即作为迭代过程中
         * 删除的“不幸”元素。（不幸的元素是那些需要 siftUp 而不是
         * siftDown 的元素）。我们必须在迭代过程中访问列表中所有的元素。
         * 这一步在我们完成普通的迭代之后进行。
         *
         * 我们希望大多数的迭代过程，甚至是包含删除操作的迭代，都不需要
         * 在这个部分存储元素。
         */
        private java.util.ArrayDeque<E> forgetMeNot = null;

        /**
         * 如果该元素是从 forgetMeNot 列表中取出的，则由最近一次调用的
         * next 返回。
         */
        private E lastRetElt = null;

        private int expectedModCount = modCount;

        // 判断是否有下一个元素
        public boolean hasNext() {
            return cursor < size ||
                    (forgetMeNot != null && !forgetMeNot.isEmpty());
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (cursor < size)
                return (E) queue[lastRet = cursor++];
            if (forgetMeNot != null) {
                lastRet = -1;
                lastRetElt = forgetMeNot.poll();
                if (lastRetElt != null)
                    return lastRetElt;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (lastRet != -1) {
                E moved = PriorityQueue.this.removeAt(lastRet);
                lastRet = -1;
                if (moved == null)
                    cursor--;
                else {
                    if (forgetMeNot == null)
                        forgetMeNot = new ArrayDeque<>();
                    forgetMeNot.add(moved);
                }
            } else if (lastRetElt != null) {
                PriorityQueue.this.removeEq(lastRetElt);
                lastRetElt = null;
            } else {
                throw new IllegalStateException();
            }
            expectedModCount = modCount;
        }
    }

    // 返回队列的大小
    public int size() {
        return size;
    }

    /**
     * 删除优先级队列的所有元素。
     * 此方法调用后队列为空。
     */
    public void clear() {
        modCount++;
        for (int i = 0; i < size; i++)
            queue[i] = null;
        size = 0;
    }

    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0)
            return null;
        // s 指向最后一个元素，同时 size 减一
        int s = --size;
        modCount++;
        E result = (E) queue[0];
        E x = (E) queue[s];
        // 删除数组尾部元素
        queue[s] = null;
        // 在数组索引为 0 的位置插入 x（删除堆顶元素），然后向下调整堆结构
        if (s != 0)
            siftDown(0, x);
        // 返回堆顶元素
        return result;
    }

    /**
     * 从队列中删除第 i 个元素。
     *
     * Normally this method leaves the elements at up to i-1,
     * inclusive, untouched.  Under these circumstances, it returns
     * null.  Occasionally, in order to maintain the heap invariant,
     * it must swap a later element of the list with one earlier than
     * i.  Under these circumstances, this method returns the element
     * that was previously at the end of the list and is now at some
     * position before i. This fact is used by iterator.remove so as to
     * avoid missing traversing elements.
     */
    @SuppressWarnings("unchecked")
    private E removeAt(int i) {
        // assert i >= 0 && i < size;
        modCount++;
        // s 为数组中最后一个元素的索引，同时 size 减一
        int s = --size;
        // 如果删除的是最后一个元素，直接将最后一个元素置为 null，并结束
        // 此方法
        if (s == i)
            queue[i] = null;
        else {
            E moved = (E) queue[s];
            queue[s] = null;
            // 将位置 i 的元素替换成 moved，即原来的最后一个元素，然后进行
            // 向下调整操作
            siftDown(i, moved);
            // 如果没有向下调整，说明可能它的位置在上面，接着向上调整
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }

    /**
     * 在 k 位置插入元素 x（从 k 位置开始调整堆，找到元素 x 的位置，忽略
     * 原先 k 位置的元素），通过向上提升 x 直到它大于等于它的父元素或者
     * 根元素，来保证满足堆成立的条件。
     *
     * 为了简化和加速强制转换和比较，Comparable（元素的默认比较器）
     * 和 Comparator（指定的比较器）被分成不同的方法，这两个方法基本
     * 等同。（siftDown 同理）
     *
     * @August 注意此方法为 private，仅类内部调用
     *
     * @param k the position to fill
     * @param x the item to insert
     */
    private void siftUp(int k, E x) {
        if (comparator != null)
            siftUpUsingComparator(k, x);
        else
            siftUpComparable(k, x);
    }

    // 对于默认比较器，插入元素时调整堆的方法
    @SuppressWarnings("unchecked")
    private void siftUpComparable(int k, E x) {
        // <? extends E>，集合中元素类型上限为 E，即只能是 E 或者 E 的子类
        // <? super E>，集合中元素类型下限为 E，即只能是 E 或者 E 的父类
        Comparable<? super E> key = (Comparable<? super E>) x;
        while (k > 0) {
            // 获取 k 的父节点
            int parent = (k - 1) >>> 1;
            // 获取父节点的元素
            Object e = queue[parent];
            // 如果 key 的值大于等于其父节点的值 e，那么不需要调整，结束操作
            // 最后建成小顶堆
            if (key.compareTo((E) e) >= 0)
                break;
            // 否则将父节点的值复制到 k 位置，然后 k 指向父节点的位置，继续比较
            queue[k] = e;
            k = parent;
        }
        // 找到指定元素的位置 k，将指定元素存在 k 位置
        queue[k] = key;
    }

    // 对于指定的比较器，插入元素时调整堆的方法
    @SuppressWarnings("unchecked")
    private void siftUpUsingComparator(int k, E x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            // 如果 x 大于其父节点的值 e，那么不用调整，跳出循环
            if (comparator.compare(x, (E) e) >= 0)
                break;
            queue[k] = e;
            k = parent;
        }
        queue[k] = x;
    }

    /**
     * 在 k 位置插入元素 x，通过向下调整 x 直到它小于等于它的子节点或者
     * 叶节点，来保证满足堆成立的条件。
     *
     * @param k the position to fill
     * @param x the item to insert
     */
    private void siftDown(int k, E x) {
        if (comparator != null)
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }

    // 对于默认的比较器，插入元素时下沉元素的方法
    @SuppressWarnings("unchecked")
    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>)x;
        // half 为队列中间一半的位置，
        int half = size >>> 1;
        // k 小于中间位置索引值时循环，因为堆的叶节点索引大于等于中间
        // 位置索引
        while (k < half) {
            // 获取 k 的左子节点的索引
            int child = (k << 1) + 1;
            // 获取左子节点的值 c
            Object c = queue[child];
            int right = child + 1;
            // 如果存在右子节点，找出两个子节点的值较小的那一个
            if (right < size &&
                    ((Comparable<? super E>) c).compareTo((E) queue[right]) > 0)
                c = queue[child = right];
            // 如果 key 的值小于等于较小子节点的值， 结束循环
            if (key.compareTo((E) c) <= 0)
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = key;
    }

    @SuppressWarnings("unchecked")
    private void siftDownUsingComparator(int k, E x) {
        int half = size >>> 1;
        while (k < half) {
            int child = (k << 1) + 1;
            Object c = queue[child];
            int right = child + 1;
            if (right < size &&
                    comparator.compare((E) c, (E) queue[right]) > 0)
                c = queue[child = right];
            if (comparator.compare(x, (E) c) <= 0)
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = x;
    }

    /**
     * 从最后一个元素的父节点位置开始建堆
     */
    @SuppressWarnings("unchecked")
    private void heapify() {
        for (int i = (size >>> 1) - 1; i >= 0; i--)
            siftDown(i, (E) queue[i]);
    }

    /**
     * 返回用来对堆元素进行排序的比较器 comparator，如果按照元素自身的
     * Comparable 排序的话，返回 null。
     *
     * @return the comparator used to order this queue, or
     *         {@code null} if this queue is sorted according to the
     *         natural ordering of its elements
     */
    public Comparator<? super E> comparator() {
        return comparator;
    }
}

