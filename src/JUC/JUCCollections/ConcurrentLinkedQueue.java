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
 * 和 toArray 不能保证自动执行。例如，与 addAll 操作并发执行的迭代器只能查看
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

    /*
     * This is a modification of the Michael & Scott algorithm,
     * adapted for a garbage-collected environment, with support for
     * interior node deletion (to support remove(Object)).  For
     * explanation, read the paper.
     *
     * Note that like most non-blocking algorithms in this package,
     * this implementation relies on the fact that in garbage
     * collected systems, there is no possibility of ABA problems due
     * to recycled nodes, so there is no need to use "counted
     * pointers" or related techniques seen in versions used in
     * non-GC'ed settings.
     *
     * The fundamental invariants are:
     * - There is exactly one (last) Node with a null next reference,
     *   which is CASed when enqueueing.  This last Node can be
     *   reached in O(1) time from tail, but tail is merely an
     *   optimization - it can always be reached in O(N) time from
     *   head as well.
     * - The elements contained in the queue are the non-null items in
     *   Nodes that are reachable from head.  CASing the item
     *   reference of a Node to null atomically removes it from the
     *   queue.  Reachability of all elements from head must remain
     *   true even in the case of concurrent modifications that cause
     *   head to advance.  A dequeued Node may remain in use
     *   indefinitely due to creation of an Iterator or simply a
     *   poll() that has lost its time slice.
     *
     * The above might appear to imply that all Nodes are GC-reachable
     * from a predecessor dequeued Node.  That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.
     *
     * Both head and tail are permitted to lag.  In fact, failing to
     * update them every time one could is a significant optimization
     * (fewer CASes). As with LinkedTransferQueue (see the internal
     * documentation for that class), we use a slack threshold of two;
     * that is, we update head/tail when the current pointer appears
     * to be two or more steps away from the first/last node.
     *
     * Since head and tail are updated concurrently and independently,
     * it is possible for tail to lag behind head (why not)?
     *
     * CASing a Node's item reference to null atomically removes the
     * element from the queue.  Iterators skip over Nodes with null
     * items.  Prior implementations of this class had a race between
     * poll() and remove(Object) where the same element would appear
     * to be successfully removed by two concurrent operations.  The
     * method remove(Object) also lazily unlinks deleted Nodes, but
     * this is merely an optimization.
     *
     * When constructing a Node (before enqueuing it) we avoid paying
     * for a volatile write to item by using Unsafe.putObject instead
     * of a normal write.  This allows the cost of enqueue to be
     * "one-and-a-half" CASes.
     *
     * Both head and tail may or may not point to a Node with a
     * non-null item.  If the queue is empty, all items must of course
     * be null.  Upon creation, both head and tail refer to a dummy
     * Node with null item.  Both head and tail are only updated using
     * CAS, so they never regress, although again this is merely an
     * optimization.
     */

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
     * - 所有存活的节点都可以通过 head 的 succ() 访问到。
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
     * 返回指定节点 p 的后继节点，如果 p 的 next 指向自身，则返回头结点。
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
     * 返回队列中第一个有效的节点，如果没有返回 null。
     * Returns the first live (non-deleted) node on list, or null if none.
     * This is yet another variant of poll/peek; here returning the
     * first node, not element.  We could make peek() a wrapper around
     * first(), but that would cost an extra volatile read of item,
     * and the need to add a retry loop to deal with the possibility
     * of losing a race to a concurrent poll().
     */
    ConcurrentLinkedQueue.Node<E> first() {
        restartFromHead:
        for (;;) {
            for (ConcurrentLinkedQueue.Node<E> h = head, p = h, q;;) {
                boolean hasItem = (p.item != null);
                if (hasItem || (q = p.next) == null) {
                    updateHead(h, p);
                    return hasItem ? p : null;
                }
                else if (p == q)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        return first() == null;
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     * Additionally, if elements are added or removed during execution
     * of this method, the returned result may be inaccurate.  Thus,
     * this method is typically not very useful in concurrent
     * applications.
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
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
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

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
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
                if (item != null) {
                    if (!o.equals(item)) {
                        next = succ(p);
                        continue;
                    }
                    removed = p.casItem(item, null);
                }

                next = succ(p);
                if (pred != null && next != null) // unlink
                    pred.casNext(p, next);
                if (removed)
                    return true;
            }
        }
        return false;
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this queue, in the order that they are returned by the specified
     * collection's iterator.  Attempts to {@code addAll} of a queue to
     * itself result in {@code IllegalArgumentException}.
     *
     * @param c the elements to be inserted into this queue
     * @return {@code true} if this queue changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     * @throws IllegalArgumentException if the collection is this queue
     */
    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
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

        // Atomically append the chain at the tail of this collection
        for (ConcurrentLinkedQueue.Node<E> t = tail, p = t;;) {
            ConcurrentLinkedQueue.Node<E> q = p.next;
            if (q == null) {
                // p is last node
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
                // Lost CAS race to another thread; re-read next
            }
            else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
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
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     *  <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
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
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new ConcurrentLinkedQueue.Itr();
    }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private ConcurrentLinkedQueue.Node<E> nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private ConcurrentLinkedQueue.Node<E> lastRet;

        Itr() {
            advance();
        }

        /**
         * Moves to next valid node and returns item to return for
         * next(), or null if no such.
         */
        private E advance() {
            lastRet = nextNode;
            E x = nextItem;

            ConcurrentLinkedQueue.Node<E> pred, p;
            if (nextNode == null) {
                p = first();
                pred = null;
            } else {
                pred = nextNode;
                p = succ(nextNode);
            }

            for (;;) {
                if (p == null) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                E item = p.item;
                if (item != null) {
                    nextNode = p;
                    nextItem = item;
                    return x;
                } else {
                    // skip over nulls
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

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData All of the elements (each an {@code E}) in
     * the proper order, followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (ConcurrentLinkedQueue.Node<E> p = first(); p != null; p = succ(p)) {
            Object item = p.item;
            if (item != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in elements until trailing null sentinel found
        ConcurrentLinkedQueue.Node<E> h = null, t = null;
        Object item;
        while ((item = s.readObject()) != null) {
            @SuppressWarnings("unchecked")
            ConcurrentLinkedQueue.Node<E> newNode = new ConcurrentLinkedQueue.Node<E>((E) item);
            if (h == null)
                h = t = newNode;
            else {
                t.lazySetNext(newNode);
                t = newNode;
            }
        }
        if (h == null)
            h = t = new ConcurrentLinkedQueue.Node<E>(null);
        head = h;
        tail = t;
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class CLQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final ConcurrentLinkedQueue<E> queue;
        ConcurrentLinkedQueue.Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        CLQSpliterator(ConcurrentLinkedQueue<E> queue) {
            this.queue = queue;
        }

        public Spliterator<E> trySplit() {
            ConcurrentLinkedQueue.Node<E> p;
            final ConcurrentLinkedQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null) &&
                    p.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                do {
                    if ((a[i] = p.item) != null)
                        ++i;
                    if (p == (p = p.next))
                        p = q.first();
                } while (p != null && i < n);
                if ((current = p) == null)
                    exhausted = true;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                            (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                                    Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            ConcurrentLinkedQueue.Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null)) {
                exhausted = true;
                do {
                    E e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            ConcurrentLinkedQueue.Node<E> p;
            if (action == null) throw new NullPointerException();
            final ConcurrentLinkedQueue<E> q = this.queue;
            if (!exhausted &&
                    ((p = current) != null || (p = q.first()) != null)) {
                E e;
                do {
                    e = p.item;
                    if (p == (p = p.next))
                        p = q.first();
                } while (e == null && p != null);
                if ((current = p) == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() { return Long.MAX_VALUE; }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new ConcurrentLinkedQueue.CLQSpliterator<E>(this);
    }

    /**
     * 如果参数为 null 抛出 NullPointerException 异常。
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    // CAS 将 tail 设置为 val
    private boolean casTail(ConcurrentLinkedQueue.Node<E> cmp, ConcurrentLinkedQueue.Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    // CAS 将 head 设置为 val
    private boolean casHead(ConcurrentLinkedQueue.Node<E> cmp, ConcurrentLinkedQueue.Node<E> val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = ConcurrentLinkedQueue.class;
            headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("tail"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

