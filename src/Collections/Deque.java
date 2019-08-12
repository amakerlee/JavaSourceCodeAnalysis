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
 * Written by Doug Lea and Josh Bloch with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

package Collections;

import java.util.Iterator;

/**
 * 支持在两端插入和删除的线性集合。"deque" 是 "double ended queue"
 * 的简写，通常读作 "deck"。大多数 Deque 的实现对它们可能包含的元素
 * 数量没有固定的限制。但是这个接口支持有容量限制的 Deque 以及没有
 * 固定大小限制的 Deque。
 *
 * 这个接口定义了访问 deque 两端元素的方法。这些方法用于插入、删除
 * 和检查元素。每一个方法都存在两种形式：一种在操作失败时抛出异常，
 * 另一种返回特定的值（null 或者 false，具体取决于该操作的定义）。后
 * 一种插入操作的形式是专门为有容量限制的 Deque 实现而设计的；在大
 * 多数实现中，插入操作不能失败。
 *
 * 这十二个方法总结如下：
 * Summary of Deque methods
 * First Element (Head):
 * Throws exception:
 * Insert----{@link Deque#addFirst addFirst(e)}
 * Remove----{@link Deque#removeFirst removeFirst()}
 * Examine----{@link Deque#getFirst getFirst()}
 * Special value:
 * Insert----{@link Deque#offerFirst offerFirst(e)}
 * Remove----{@link Deque#pollFirst pollFirst()}
 * Examine----{@link Deque#peekFirst peekFirst()}
 * Last Element (Tail):
 * Throws exception:
 * Insert----{@link Deque#addLast addLast(e)}
 * Remove----{@link Deque#removeLast removeLast()}
 * Examine----{@link Deque#getLast getLast()}
 * Special value:
 * Insert----{@link Deque#offerLast offerLast(e)}
 * Remove----{@link Deque#pollLast pollLast()}
 * Examine----{@link Deque#peekLast peekLast()}
 *
 * 这个接口扩展了 Queue 接口。当一个 deque 用作 queue 时，
 * 将会产生 FIFO(First-In-First-Out) 的行为。元素将会添加到队列末尾，
 * 并删除队列头部的元素。从 Queue 接口继承的方法和下列 Deque 中的
 * 方法完全等价：
 * Comparison of Queue and Deque methods
 * {@code Queue} Method                       {@code Deque} Method
 * {@link Queue#add add(e)}                  {@link #addLast addLast(e)}
 * {@link Queue#offer offer(e)}            {@link #offerLast offerLast(e)}
 * {@link Queue#remove remove()}        {@link #removeFirst removeFirst()}
 * {@link Queue#poll poll()}                     {@link #pollFirst pollFirst()}
 * {@link Queue#element element()}      {@link #getFirst getFirst()}
 * {@link Queue#peek peek()}                {@link #peek peekFirst()}
 *
 * Deques 也可以用作 LIFO(Last-In-First-Out) 的栈。这个接口应该优先
 * 使用遗留的 Stack 类。当一个 deque 用作栈时，元素被添加到队列头部，
 * 并从队列头部删除。Stack 的方法和下列 Deque 中的方法完全等价：
 * Comparison of Stack and Deque methods
 * Stack Method                                 Deque Method
 * {@link #push push(e)}                   {@link #addFirst addFirst(e)}
 * {@link #pop pop()}                         {@link #removeFirst removeFirst()}
 * {@link #peek peek()}                     {@link #peekFirst peekFirst()}
 *
 * 注意，当 deque 被用作 queue 或者 stack 时，peek 方法同样有效；在
 * 上述两种情况下，元素都是从 deque 头部抽取。
 *
 * 这一接口提供了两个方法来删除内部元素，removeFirstOccurrence 和
 * removeLastOccurrence。
 *
 * 和 List 接口不同的是，这个接口不支持根据索引访问任意元素。
 *
 * Deque 的实现并不严格禁止插入 null 元素，但是强烈建议这样做。任何
 * 支持 null 元素的 Deque 实现都强烈建议不要插入 null 元素。这是因为
 * 许多方法都把 null 作为一个特殊返回值，以说明 deque 为空集合。
 *
 * Deque 的实现通常不定义 element-based 版本的 equals 方法和
 * hashCode 方法，而是从 Object 类继承基于标识的版本。
 *
 * 这个接口是 Java Collections Framework 的成员。
 *
 * @author Doug Lea
 * @author Josh Bloch
 * @since  1.6
 * @param <E> the type of elements held in this collection
 */
public interface Deque<E> extends Queue<E> {
    /**
     * 如果插入操作不违反容量限制，那么将指定元素插入到队列头部，
     * 如果无空间可用抛出 IllegalStateException 异常。当 deque 有容量
     * 限制时，使用 offerFirst 方法更好。
     *
     * @param e the element to add
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    void addFirst(E e);

    /**
     * 如果插入操作不违反容量限制，那么将指定元素插入到队列尾部，
     * 如果无空间可用抛出 IllegalStateException 异常。当 deque 有容量
     * 限制时，使用 offerLast 方法更好。
     *
     * 这个方法等价于 add 方法。
     *
     * @param e the element to add
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    void addLast(E e);

    /**
     * 如果插入操作不违反容量限制，将指定元素插入到队列头部。当使用
     * 有容量限制的队列时，此方法通常比 addFirst 更可取，因为 addFirst
     * 插入失败只会抛出异常。
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this deque, else
     *         {@code false}
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    boolean offerFirst(E e);

    /**
     * 如果插入操作不违反容量限制，将指定元素插入到队列尾部。当使用
     * 有容量限制的队列时，此方法通常比 addLast 更可取，因为 addLast
     * 插入失败只会抛出异常。
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this deque, else
     *         {@code false}
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    boolean offerLast(E e);

    /**
     * 检索并删除队列第一个元素。这一个方法与 pollFirst 的不同之处在于
     * 如果队列为空，它会抛出异常。
     *
     * @return the head of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E removeFirst();

    /**
     * 检索并删除队列最后一个元素。这一个方法与 pollLast 的不同之处在于
     * 如果队列为空，它会抛出异常。
     *
     * @return the tail of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E removeLast();

    /**
     * 检索并删除队列头部元素。如果队列为空返回 null。
     *
     * @return the head of this deque, or {@code null} if this deque is empty
     */
    E pollFirst();

    /**
     * 检索并删除队列尾部元素。如果队列为空返回 null。
     *
     * @return the tail of this deque, or {@code null} if this deque is empty
     */
    E pollLast();

    /**
     * 检索但不删除队列的第一个元素。这个方法和 peekFirst 方法不同的
     * 是，他会在队列为空时抛出异常。
     *
     * @return the head of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E getFirst();

    /**
     * 检索但不删除队列的最后一个元素。这个方法和 peekLast 方法不同的
     * 是，他会在队列为空时抛出异常。
     *
     * @return the tail of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E getLast();

    /**
     * 检索但不删除队列的头部元素。如果队列为空返回 null。
     *
     * @return the head of this deque, or {@code null} if this deque is empty
     */
    E peekFirst();

    /**
     * 检索但不删除队列的尾部元素。如果队列为空返回 null。
     *
     * @return the tail of this deque, or {@code null} if this deque is empty
     */
    E peekLast();

    /**
     * Removes the first occurrence of the specified element from this deque.
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the first element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>
     * (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if an element was removed as a result of this call
     * @throws ClassCastException if the class of the specified element
     *         is incompatible with this deque
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     */
    boolean removeFirstOccurrence(Object o);

    /**
     * Removes the last occurrence of the specified element from this deque.
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the last element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>
     * (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if an element was removed as a result of this call
     * @throws ClassCastException if the class of the specified element
     *         is incompatible with this deque
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     */
    boolean removeLastOccurrence(Object o);

    // *** Queue methods ***

    /**
     * Inserts the specified element into the queue represented by this deque
     * (in other words, at the tail of this deque) if it is possible to do so
     * immediately without violating capacity restrictions, returning
     * {@code true} upon success and throwing an
     * {@code IllegalStateException} if no space is currently available.
     * When using a capacity-restricted deque, it is generally preferable to
     * use {@link #offer(Object) offer}.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    boolean add(E e);

    /**
     * Inserts the specified element into the queue represented by this deque
     * (in other words, at the tail of this deque) if it is possible to do so
     * immediately without violating capacity restrictions, returning
     * {@code true} upon success and {@code false} if no space is currently
     * available.  When using a capacity-restricted deque, this method is
     * generally preferable to the {@link #add} method, which can fail to
     * insert an element only by throwing an exception.
     *
     * <p>This method is equivalent to {@link #offerLast}.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this deque, else
     *         {@code false}
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    boolean offer(E e);

    /**
     * Retrieves and removes the head of the queue represented by this deque
     * (in other words, the first element of this deque).
     * This method differs from {@link #poll poll} only in that it throws an
     * exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #removeFirst()}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E remove();

    /**
     * Retrieves and removes the head of the queue represented by this deque
     * (in other words, the first element of this deque), or returns
     * {@code null} if this deque is empty.
     *
     * <p>This method is equivalent to {@link #pollFirst()}.
     *
     * @return the first element of this deque, or {@code null} if
     *         this deque is empty
     */
    E poll();

    /**
     * Retrieves, but does not remove, the head of the queue represented by
     * this deque (in other words, the first element of this deque).
     * This method differs from {@link #peek peek} only in that it throws an
     * exception if this deque is empty.
     *
     * <p>This method is equivalent to {@link #getFirst()}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException if this deque is empty
     */
    E element();

    /**
     * Retrieves, but does not remove, the head of the queue represented by
     * this deque (in other words, the first element of this deque), or
     * returns {@code null} if this deque is empty.
     *
     * <p>This method is equivalent to {@link #peekFirst()}.
     *
     * @return the head of the queue represented by this deque, or
     *         {@code null} if this deque is empty
     */
    E peek();


    // *** Stack methods ***

    /**
     * Pushes an element onto the stack represented by this deque (in other
     * words, at the head of this deque) if it is possible to do so
     * immediately without violating capacity restrictions, throwing an
     * {@code IllegalStateException} if no space is currently available.
     *
     * <p>This method is equivalent to {@link #addFirst}.
     *
     * @param e the element to push
     * @throws IllegalStateException if the element cannot be added at this
     *         time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this deque
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this deque
     */
    void push(E e);

    /**
     * Pops an element from the stack represented by this deque.  In other
     * words, removes and returns the first element of this deque.
     *
     * <p>This method is equivalent to {@link #removeFirst()}.
     *
     * @return the element at the front of this deque (which is the top
     *         of the stack represented by this deque)
     * @throws NoSuchElementException if this deque is empty
     */
    E pop();


    // *** Collection methods ***

    /**
     * Removes the first occurrence of the specified element from this deque.
     * If the deque does not contain the element, it is unchanged.
     * More formally, removes the first element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>
     * (if such an element exists).
     * Returns {@code true} if this deque contained the specified element
     * (or equivalently, if this deque changed as a result of the call).
     *
     * <p>This method is equivalent to {@link #removeFirstOccurrence(Object)}.
     *
     * @param o element to be removed from this deque, if present
     * @return {@code true} if an element was removed as a result of this call
     * @throws ClassCastException if the class of the specified element
     *         is incompatible with this deque
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     */
    boolean remove(Object o);

    /**
     * Returns {@code true} if this deque contains the specified element.
     * More formally, returns {@code true} if and only if this deque contains
     * at least one element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this deque is to be tested
     * @return {@code true} if this deque contains the specified element
     * @throws ClassCastException if the type of the specified element
     *         is incompatible with this deque
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *         deque does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     */
    boolean contains(Object o);

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements in this deque
     */
    public int size();

    /**
     * Returns an iterator over the elements in this deque in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * @return an iterator over the elements in this deque in proper sequence
     */
    Iterator<E> iterator();

    /**
     * Returns an iterator over the elements in this deque in reverse
     * sequential order.  The elements will be returned in order from
     * last (tail) to first (head).
     *
     * @return an iterator over the elements in this deque in reverse
     * sequence
     */
    Iterator<E> descendingIterator();

}
