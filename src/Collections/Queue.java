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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package Collections;

import java.util.*;

/**
 * 这是一个用于在进行处理之前保存元素的集合类。除了基本的 Collection 框架
 * 提供的操作外，队列额外提供插入，提取和检查操作。这些方法都一两种形式
 * 存在：一种在操作失败时抛出异常，另一种返回特殊值（null 或者 false，取决于
 * 操作）。后一种插入操作的形式是专门为受容量限制的 Queue 设计的，在大多数
 * 实现方式下，插入操作不能失败。
 *
 * Summary of Queue methods
 * Throws exception:
 *  Insert----{@link Queue#add add(e)}
 *  Remove----{@link Queue#remove remove()}
 *  Examine----{@link Queue#element element()}
 *  Returns special value:
 *  Insert----{@link Queue#offer offer(e)}
 *  Remove----{@link Queue#poll poll()}
 *  Examine----{@link Queue#peek peek()}
 *
 * 队列通常（但并不一定）以 FIFO（先进先出）的方式对元素排序。例外的情况
 * 包括优先队列（根据提供的比较器或者元素自然顺序对元素排序）和 先进后出的
 * LIFO 队列（或堆栈）。
 * 无论使用什么顺序，都是通过 remove 或 poll 来删除 head 元素的。在一个FIFO
 * 队列中，所有的新元素都插入队列尾部。其他类型的队列可能使用不同的放置
 * 规则。每一个 Queue 的实现都必须指定排序规则。
 *
 * 如果可能，offer方法插入一个元素，否则返回false。这和 Collection.add 方法
 * 不同，Collection.add 方法只会在添加失败的时候抛出未检查的异常。offer 方法
 * 用于在故障是正常的情况下使用，而不是在异常情况（例如在固定容量，或有界
 * 队列）下使用。
 *
 * remove 和 poll 方法从列表头部删除元素，并返回该元素。
 * 确切地说，从队列删除哪一个元素是队列的排序策略决定的，每一个实现都不一
 * 样。remove 和 poll 方法只在队列为空时行为不同：remove 抛出异常，而 poll
 * 方法返回 null。
 *
 * element 和 peek 方法返回队列的头部元素，但不会删除。
 *
 * Queue 接口不定义阻塞队列方法，尽管这些方法在并发编程中很常见。这些方法
 * 在 java.util.concurrent.BlockingQueue 接口中定义，它们等待元素出现或空间
 * 可用。
 *
 * Queue 的实现通常不允许插入 null 元素，尽管有的实现，例如 LinkedList 不禁止
 * 插入 null。
 * 即使在允许插入 null 的实现中，null 也不应该插入到 Queue 中，因为 poll 将
 * null 用作特殊的返回值，用来表示队列不包含任何元素。
 *
 * Queue 的实现通常不定义 element-based 版本的 equals 方法和 hashCode 方法，而是
 * 从 Object 类继承基于标识的版本，因为 element-based 的相等在元素相同但
 * 排序属性不同的队列里的定义并不确定。
 *
 * 这个接口是 Java Collections Framework 的成员。
 *
 * @see java.util.Collection
 * @see LinkedList
 * @see PriorityQueue
 * @see java.util.concurrent.LinkedBlockingQueue
 * @see java.util.concurrent.BlockingQueue
 * @see java.util.concurrent.ArrayBlockingQueue
 * @see java.util.concurrent.LinkedBlockingQueue
 * @see java.util.concurrent.PriorityBlockingQueue
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public interface Queue<E> extends Collection<E> {
    /**
     * 如果插入操作不违反容量限制，那么将指定元素插入到队列中，成功后返回
     * true，如果无空间可用抛出 IllegalStateException 异常。
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     *         this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this queue
     */
    boolean add(E e);

    /**
     * 如果插入操作不违反容量限制，立即将指定元素插入到队列中。
     * 当使用有容量限制的队列时，此方法通常比 add 更可取，因为 add 插入失败
     * 只会抛出异常。
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     *         {@code false}
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     *         this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this queue
     */
    boolean offer(E e);

    /**
     * 检索并删除队列头。这一个方法与 poll 的不同之处在于
     * Retrieves and removes the head of this queue.  This method differs
     * from {@link #poll poll} only in that it throws an exception if this
     * queue is empty.
     *
     * @return the head of this queue
     * @throws NoSuchElementException if this queue is empty
     */
    E remove();

    /**
     * Retrieves and removes the head of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    E poll();

    /**
     * Retrieves, but does not remove, the head of this queue.  This method
     * differs from {@link #peek peek} only in that it throws an exception
     * if this queue is empty.
     *
     * @return the head of this queue
     * @throws NoSuchElementException if this queue is empty
     */
    E element();

    /**
     * Retrieves, but does not remove, the head of this queue,
     * or returns {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    E peek();
}
