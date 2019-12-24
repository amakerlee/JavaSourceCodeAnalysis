package JUC.JUCCollections;
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
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * 一个基于节点的无界的线程安全队列。队列中元素顺序为 FIFO。队列头部元素是
 * 在队列中等待最久的元素。队列尾部元素是等待时间最短的元素。新的元素插入到
 * 队列尾部，获取元素时从队列头部获取。当多线程需要同时访问同一个集合时，
 * ConcurrentLinkedQueue 是合适的选择。和众多的并发集合一样，此类不支持
 * null 元素。
 *
 * 迭代器维持“弱一致性”，返回的元素反应了队列在迭代器创建时或创建后的状态。
 * 它们不会抛出 ConcurrentModificationException 异常，可以与其它操作并发进行。
 * 自创建迭代以来，队列中的元素仅返回一次。
 *
 * 与大多数集合不同，size 方法不是常量时间的操作。由于这些队列的异步性，确定
 * 当前元素的数量需要遍历，所以如果在遍历期间修改此集合，可能会返回不准确的
 * 结果。此外，批量操作函数 addAll, removeAll, retainAll, containsAll, equals,
 * 和 toArray 不能保证原子性。例如，与 addAll 操作并发执行的迭代器只能查看
 * 添加的元素中的某一部分。
 *
 * 此类和它的迭代器实现了 Queue 和 Iterator 接口的所有可选操作。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E>
        implements Queue<E>, java.io.Serializable {
    private static final long serialVersionUID = 196745693267521676L;

    // 节点类
    private static class Node<E> {
        volatile E item;
        volatile ConcurrentLinkedQueue.Node<E> next;

        /**
         * 构造函数。
         */
        Node(E item) {
            UNSAFE.putObject(this, itemOffset, item);
        }

        // CAS 方式改变节点的值
        boolean casItem(E cmp, E val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        // 延迟设置节点的 next，不保证值的改变被其它线程看到。减少不必要的内存屏障，
        // 提高程序效率。
        void lazySetNext(ConcurrentLinkedQueue.Node<E> val) {
            UNSAFE.putOrderedObject(this, nextOffset, val);
        }

        // CAS 方式更新 next 指向
        boolean casNext(ConcurrentLinkedQueue.Node<E> cmp, ConcurrentLinkedQueue.Node<E> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        // Unsafe mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long itemOffset;
        private static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = ConcurrentLinkedQueue.Node.class;
                itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * head 是从第一个节点可以在 O(1) 时间内到达的节点。
     * 不变性：
     * - 所有存活（item 非 null）的节点都可以通过 head 的 succ() 访问到。
     * - head 不等于 null。
     * - head 的 next 不能指向自己。
     * 可变性：
     * - head 的 item 可能为 null，也可能不为 null。
     * - 允许 tail 滞后于 head，即从 head 开始遍历队列，不一定能到达 tail。
     */
    private transient volatile ConcurrentLinkedQueue.Node<E> head;

    /**
     * tail 是从最后一个节点（node.next == null）可以在 O(1) 时间内到达的节点。
     * 不变性：
     * - 最后一个节点可以通过 tail 的 succ() 访问到。
     * - tail 不等于 null。
     * 可变性：
     * - tail 的 item 可能为 null，也可能不为 null。
     * - 允许 tail 滞后于 head，即从 head 开始遍历队列，不一定能到达 tail。
     * - tail 的 next 可以指向自身。
     */
    private transient volatile ConcurrentLinkedQueue.Node<E> tail;

    /**
     * 创造一个初始为空的 ConcurrentLinkedQueue 队列。
     */
    public ConcurrentLinkedQueue() {
        head = tail = new ConcurrentLinkedQueue.Node<E>(null);
    }

    /**
     * 创建一个包含指定集合所有元素的 ConcurrentLinkedQueue 队列，添加的元素
     * 顺序为集合迭代器遍历的顺序。
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        ConcurrentLinkedQueue.Node<E> h = null, t = null;
        // 遍历集合所有元素
        for (E e : c) {
            checkNotNull(e);
            // 创建新的节点
            ConcurrentLinkedQueue.Node<E> newNode = new ConcurrentLinkedQueue.Node<E>(e);
            if (h == null)
                h = t = newNode;
            else {
                // t 是上一个节点（尾节点）
                t.lazySetNext(newNode);
                // t 往后移动一位
                t = newNode;
            }
        }
        if (h == null)
            h = t = new ConcurrentLinkedQueue.Node<E>(null);
        head = h;
        tail = t;
    }

    // Have to override just to update the javadoc

    /**
     * 队列尾部插入指定元素。队列为无界队列，此方法不会抛出 IllegalStateException
     * 异常或者返回 false。
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * CAS 方式将 head 设置为 p节点。如果成功将原来的头结点（h 还是指向原来
     * 的头节点没有发生变化）的 next 指向自己。
     */
    final void updateHead(ConcurrentLinkedQueue.Node<E> h, ConcurrentLinkedQueue.Node<E> p) {
        if (h != p && casHead(h, p))
            h.lazySetNext(h);
    }

    /**
     * 返回指定节点 p 的后继节点，如果 p 的 next 指向自身，说明头结点
     * 已经改变了，返回新的头结点。
     */
    final ConcurrentLinkedQueue.Node<E> succ(ConcurrentLinkedQueue.Node<E> p) {
        ConcurrentLinkedQueue.Node<E> next = p.next;
        return (p == next) ? head : next;
    }

    /**
     * 将指定元素添加到队列尾部。
     * 由于队列时无界队列，此方法不会返回 false。
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        checkNotNull(e);
        final ConcurrentLinkedQueue.Node<E> newNode = new ConcurrentLinkedQueue.Node<E>(e);

        // 由于松弛阈值的存在，tail 并不一定每时每刻都指向队列的最后一个节点，
        // 自旋从 tail 节点开始查找最后一个节点
        for (ConcurrentLinkedQueue.Node<E> t = tail, p = t;;) {
            ConcurrentLinkedQueue.Node<E> q = p.next;
            // 当前节点 p 的下一个节点为 null，说明 p 是最后一个节点
            if (q == null) {
                // CAS 将 p 的 next 指向新创建的 newNode
                if (p.casNext(null, newNode)) {
                    // 成功在队列尾部插入新的节点
                    // 如果 tail 没有指向 p，那么 tail 和真正的尾节点之间至少已经隔了一个
                    // 节点了。此时将 tail 指向真正的尾节点（注意 casTail 可能执行失败）。
                    if (p != t)
                        casTail(t, newNode);
                    // 执行完毕，返回 true
                    return true;
                }
            }
            // 如果当前节点 p 的下一个节点为其自身
            else if (p == q)
                // 此时如果 tail 节点没有变化，p 重新从 tail 节点开始遍历
                // 如果 tail 节点没有变化，则从 head 节点开始往后遍历
                p = (t != (t = tail)) ? t : head;
            else
                // 如果 tail 节点变化，重新获取 tail 节点
                // p 往后移动，继续往后查找
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    // 从队列头部取元素
    public E poll() {
        restartFromHead:
        for (;;) {
            // 从队列头部开始扫描
            for (ConcurrentLinkedQueue.Node<E> h = head, p = h, q;;) {
                E item = p.item;

                // 如果当前节点的 item 不为 null，表示找到了头结点
                // CAS 修改当前节点的 item 为 null
                if (item != null && p.casItem(item, null)) {
                    if (p != h) // hop two nodes at a time
                        // 如果 p 的 next 不等于 null，将 head 设置为 p 的 next
                        // （因为 p 中的 item 会被返回，p 即将变成无效节点）
                        // 如果 p 的 next 等于 null，将 head 设置为 p
                        updateHead(h, ((q = p.next) != null) ? q : p);
                    return item;
                }
                // 以下三种情况前提为 item 等于 null
                // 当前节点的下一个节点为 null（且当前 item 为 null），说明队列中
                // 已经没有有效节点了。将 head 指针设置为 p，并将之前头结点的
                // next 指向自己
                // 返回 null。
                else if ((q = p.next) == null) {
                    updateHead(h, p);
                    return null;
                }
                // 如果当前节点的 next 指向自己，说明已经无效，重新从 head 开始遍历
                else if (p == q)
                    continue restartFromHead;
                // 仅仅只是 item 等于 null，继续往后查找有效 item
                else
                    p = q;
            }
        }
    }

    // 获取头部节点的 item
    public E peek() {
        restartFromHead:
        for (;;) {
            // 从 head 节点开始查找
            for (ConcurrentLinkedQueue.Node<E> h = head, p = h, q;;) {
                E item = p.item;
                // 找到了
                if (item != null || (q = p.next) == null) {
                    updateHead(h, p);
                    return item;
                }
                // head 被重新设置了
                else if (p == q)
                    continue restartFromHead;
                // 继续往后
                else
                    p = q;
            }
        }
    }

    /**
     * 返回队列中第一个有效的节点，如果没有返回 null。这是 poll/peek 的
     * 另一种变体；这里返回的是第一个节点，而不是第一个节点中的元素。
     * 可以使 peek 成为 first 的包装器，但是这样会增加一个额外的 volatile
     * 读操作，并且需要添加一个 retry 循环来处理在与并发 poll 竞争中失败的
     * 可能性。
     */
    ConcurrentLinkedQueue.Node<E> first() {
        restartFromHead:
        for (;;) {
            // 从 head 开始查找
            for (ConcurrentLinkedQueue.Node<E> h = head, p = h, q;;) {
                boolean hasItem = (p.item != null);
                // 当前存在 item 或者已经到最后一个节点了
                if (hasItem || (q = p.next) == null) {
                    updateHead(h, p);
                    return hasItem ? p : null;
                }
                // 头结点已经改变，从新的头结点开始查找
                else if (p == q)
                    continue restartFromHead;
                // 否则往后查找
                else
                    p = q;
            }
        }
    }

    /**
     * 如果队列为空则返回 true。
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        return first() == null;
    }

    /**
     * 返回队列中元素的数量。如果元素个数超过 Integer.MAX_VALUE，返回
     * Integer.MAX_VALUE。
     *
     * 和大多数集合不同，此方法不是常量时间操作。由于队列的异步性（懒删除），
     * 确定当前的元素数量需要 O（n） 时间遍历。
     * 此外，如果在执行此方法期间添加或删除元素，则返回的结果可能不准确。
     * 因此，这个方法在并发的应用程序中通常并不是很有用。
     *
     * @return the number of elements in this queue
     */
    public int size() {
        int count = 0;
        for (ConcurrentLinkedQueue.Node<E> p = first(); p != null; p = succ(p))
            if (p.item != null)
                // Collection.size() spec says to max out
                if (++count == Integer.MAX_VALUE)
                    break;
        return count;
    }

    /**
     * 如果队列包含指定元素，返回 true。
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (ConcurrentLinkedQueue.Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null && o.equals(item))
                return true;
        }
        return false;
    }

    /**从队列中删除指定元素，如果它存在的话。删除成功返回 true。
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o != null) {
            ConcurrentLinkedQueue.Node<E> next, pred = null;
            for (ConcurrentLinkedQueue.Node<E> p = first(); p != null; pred = p, p = next) {
                boolean removed = false;
                E item = p.item;
                // 查找 next
                if (item != null) {
                    if (!o.equals(item)) {
                        next = succ(p);
                        continue;
                    }
                    removed = p.casItem(item, null);
                }

                next = succ(p);
                // 删除 p
                if (pred != null && next != null) // unlink
                    // 使用 CAS 方式删除节点，只有一个线程能删除成功
                    pred.casNext(p, next);
                if (removed)
                    return true;
            }
        }
        return false;
    }

    /**
     * 将指定集合的所有元素添加到队列末尾。以集合迭代器返回的顺序。将自身
     * 作为参数 c 时会抛出 IllegalArgumentException 异常。
     *
     * @param c the elements to be inserted into this queue
     * @return {@code true} if this queue changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     * @throws IllegalArgumentException if the collection is this queue
     */
    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            throw new IllegalArgumentException();

        // 将集合 c 的所有元素构造成一个节点为 Node 的链表
        ConcurrentLinkedQueue.Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            checkNotNull(e);
            ConcurrentLinkedQueue.Node<E> newNode = new ConcurrentLinkedQueue.Node<E>(e);
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else {
                last.lazySetNext(newNode);
                last = newNode;
            }
        }
        if (beginningOfTheEnd == null)
            return false;

        // 找到最后一个节点，将链表添加到节点之后
        for (ConcurrentLinkedQueue.Node<E> t = tail, p = t;;) {
            ConcurrentLinkedQueue.Node<E> q = p.next;
            if (q == null) {
                // p 是最后一个节点
                if (p.casNext(null, beginningOfTheEnd)) {
                    // Successful CAS is the linearization point
                    // for all elements to be added to this queue.
                    if (!casTail(t, last)) {
                        // Try a little harder to update tail,
                        // since we may be adding many elements.
                        t = tail;
                        if (last.next == null)
                            casTail(t, last);
                    }
                    return true;
                }
                // CAS 失败，重新尝试
            }
            else if (p == q)
                // 如果 tail 没有改变，它可能已经被移除队列，此时需要从 head 节点
                // 开始查找。否则如果 tail 改变了，可以从 tail 开始查找。
                p = (t != (t = tail)) ? t : head;
            else
                // 检查 tail，往后移动
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * 返回数组
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        // Use ArrayList to deal with resizing.
        ArrayList<E> al = new ArrayList<E>();
        for (ConcurrentLinkedQueue.Node<E> p = first(); p != null; p = succ(p)) {
            E item = p.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray();
    }

    /**
     * 返回数组
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        // try to use sent-in array
        int k = 0;
        ConcurrentLinkedQueue.Node<E> p;
        for (p = first(); p != null && k < a.length; p = succ(p)) {
            E item = p.item;
            if (item != null)
                a[k++] = (T)item;
        }
        if (p == null) {
            if (k < a.length)
                a[k] = null;
            return a;
        }

        // If won't fit, use ArrayList version
        ArrayList<E> al = new ArrayList<E>();
        for (ConcurrentLinkedQueue.Node<E> q = first(); q != null; q = succ(q)) {
            E item = q.item;
            if (item != null)
                al.add(item);
        }
        return al.toArray(a);
    }

    /**
     * 返回迭代器。
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new ConcurrentLinkedQueue.Itr();
    }

    // 迭代器实现
    private class Itr implements Iterator<E> {
        /**
         * 下一个节点
         */
        private ConcurrentLinkedQueue.Node<E> nextNode;

        /**
         * 此字段保留 item 的值，因为一旦在 hasNext 中声明一个元素存在，之后
         * 必须在下一个 next 调用中返回它。
         */
        private E nextItem;

        /**
         * 上一次返回的 Node，此字段用来支持 remove 操作。
         */
        private ConcurrentLinkedQueue.Node<E> lastRet;

        Itr() {
            advance();
        }

        /**
         * 移动到下一个有效的节点，并返回 item，没有则返回 null。
         */
        private E advance() {
            // 保存当前节点，此节点即将变成下一个节点
            lastRet = nextNode;
            E x = nextItem;

            ConcurrentLinkedQueue.Node<E> pred, p;
            // 如果 nextNode 为null，说明这是初始化操作，游标移到起始位置
            if (nextNode == null) {
                p = first();
                pred = null;
            } else {
                pred = nextNode;
                p = succ(nextNode);
            }

            for (;;) {
                // 说明已经到尾部，迭代结束
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                E item = p.item;
                // 有效节点
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    return x;
                } else {
                    // 无效节点，跳过它继续往前
                    ConcurrentLinkedQueue.Node<E> next = succ(p);
                    if (pred != null && next != null)
                        pred.casNext(p, next);
                    p = next;
                }
            }
        }

        public boolean hasNext() {
            return nextNode != null;
        }

        public E next() {
            if (nextNode == null) throw new NoSuchElementException();
            return advance();
        }

        public void remove() {
            ConcurrentLinkedQueue.Node<E> l = lastRet;
            if (l == null) throw new IllegalStateException();
            // rely on a future traversal to relink.
            l.item = null;
            lastRet = null;
        }
    }
}

