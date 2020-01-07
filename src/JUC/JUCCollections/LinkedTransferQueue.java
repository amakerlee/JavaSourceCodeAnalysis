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
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * LinkedTransferQueue 是基于链表的无界 TransferQueue。按照 FIFO 的
 * 顺序对元素排序。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @since 1.7
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public class LinkedTransferQueue<E> extends AbstractQueue<E>
        implements TransferQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * *** Overview of Dual Queues with Slack ***
     *
     * Dual Queues, introduced by Scherer and Scott
     * (http://www.cs.rice.edu/~wns1/papers/2004-DISC-DDS.pdf) are
     * (linked) queues in which nodes may represent either data or
     * requests.  When a thread tries to enqueue a data node, but
     * encounters a request node, it instead "matches" and removes it;
     * and vice versa for enqueuing requests. Blocking Dual Queues
     * arrange that threads enqueuing unmatched requests block until
     * other threads provide the match. Dual Synchronous Queues (see
     * Scherer, Lea, & Scott
     * http://www.cs.rochester.edu/u/scott/papers/2009_Scherer_CACM_SSQ.pdf)
     * additionally arrange that threads enqueuing unmatched data also
     * block.  Dual Transfer Queues support all of these modes, as
     * dictated by callers.
     *
     * A FIFO dual queue may be implemented using a variation of the
     * Michael & Scott (M&S) lock-free queue algorithm
     * (http://www.cs.rochester.edu/u/scott/papers/1996_PODC_queues.pdf).
     * It maintains two pointer fields, "head", pointing to a
     * (matched) node that in turn points to the first actual
     * (unmatched) queue node (or null if empty); and "tail" that
     * points to the last node on the queue (or again null if
     * empty). For example, here is a possible queue with four data
     * elements:
     *
     *  head                tail
     *    |                   |
     *    v                   v
     *    M -> U -> U -> U -> U
     *
     * The M&S queue algorithm is known to be prone to scalability and
     * overhead limitations when maintaining (via CAS) these head and
     * tail pointers. This has led to the development of
     * contention-reducing variants such as elimination arrays (see
     * Moir et al http://portal.acm.org/citation.cfm?id=1074013) and
     * optimistic back pointers (see Ladan-Mozes & Shavit
     * http://people.csail.mit.edu/edya/publications/OptimisticFIFOQueue-journal.pdf).
     * However, the nature of dual queues enables a simpler tactic for
     * improving M&S-style implementations when dual-ness is needed.
     *
     * In a dual queue, each node must atomically maintain its match
     * status. While there are other possible variants, we implement
     * this here as: for a data-mode node, matching entails CASing an
     * "item" field from a non-null data value to null upon match, and
     * vice-versa for request nodes, CASing from null to a data
     * value. (Note that the linearization properties of this style of
     * queue are easy to verify -- elements are made available by
     * linking, and unavailable by matching.) Compared to plain M&S
     * queues, this property of dual queues requires one additional
     * successful atomic operation per enq/deq pair. But it also
     * enables lower cost variants of queue maintenance mechanics. (A
     * variation of this idea applies even for non-dual queues that
     * support deletion of interior elements, such as
     * j.u.c.ConcurrentLinkedQueue.)
     *
     * Once a node is matched, its match status can never again
     * change.  We may thus arrange that the linked list of them
     * contain a prefix of zero or more matched nodes, followed by a
     * suffix of zero or more unmatched nodes. (Note that we allow
     * both the prefix and suffix to be zero length, which in turn
     * means that we do not use a dummy header.)  If we were not
     * concerned with either time or space efficiency, we could
     * correctly perform enqueue and dequeue operations by traversing
     * from a pointer to the initial node; CASing the item of the
     * first unmatched node on match and CASing the next field of the
     * trailing node on appends. (Plus some special-casing when
     * initially empty).  While this would be a terrible idea in
     * itself, it does have the benefit of not requiring ANY atomic
     * updates on head/tail fields.
     *
     * We introduce here an approach that lies between the extremes of
     * never versus always updating queue (head and tail) pointers.
     * This offers a tradeoff between sometimes requiring extra
     * traversal steps to locate the first and/or last unmatched
     * nodes, versus the reduced overhead and contention of fewer
     * updates to queue pointers. For example, a possible snapshot of
     * a queue is:
     *
     *  head           tail
     *    |              |
     *    v              v
     *    M -> M -> U -> U -> U -> U
     *
     * The best value for this "slack" (the targeted maximum distance
     * between the value of "head" and the first unmatched node, and
     * similarly for "tail") is an empirical matter. We have found
     * that using very small constants in the range of 1-3 work best
     * over a range of platforms. Larger values introduce increasing
     * costs of cache misses and risks of long traversal chains, while
     * smaller values increase CAS contention and overhead.
     *
     * Dual queues with slack differ from plain M&S dual queues by
     * virtue of only sometimes updating head or tail pointers when
     * matching, appending, or even traversing nodes; in order to
     * maintain a targeted slack.  The idea of "sometimes" may be
     * operationalized in several ways. The simplest is to use a
     * per-operation counter incremented on each traversal step, and
     * to try (via CAS) to update the associated queue pointer
     * whenever the count exceeds a threshold. Another, that requires
     * more overhead, is to use random number generators to update
     * with a given probability per traversal step.
     *
     * In any strategy along these lines, because CASes updating
     * fields may fail, the actual slack may exceed targeted
     * slack. However, they may be retried at any time to maintain
     * targets.  Even when using very small slack values, this
     * approach works well for dual queues because it allows all
     * operations up to the point of matching or appending an item
     * (hence potentially allowing progress by another thread) to be
     * read-only, thus not introducing any further contention. As
     * described below, we implement this by performing slack
     * maintenance retries only after these points.
     *
     * As an accompaniment to such techniques, traversal overhead can
     * be further reduced without increasing contention of head
     * pointer updates: Threads may sometimes shortcut the "next" link
     * path from the current "head" node to be closer to the currently
     * known first unmatched node, and similarly for tail. Again, this
     * may be triggered with using thresholds or randomization.
     *
     * These ideas must be further extended to avoid unbounded amounts
     * of costly-to-reclaim garbage caused by the sequential "next"
     * links of nodes starting at old forgotten head nodes: As first
     * described in detail by Boehm
     * (http://portal.acm.org/citation.cfm?doid=503272.503282) if a GC
     * delays noticing that any arbitrarily old node has become
     * garbage, all newer dead nodes will also be unreclaimed.
     * (Similar issues arise in non-GC environments.)  To cope with
     * this in our implementation, upon CASing to advance the head
     * pointer, we set the "next" link of the previous head to point
     * only to itself; thus limiting the length of connected dead lists.
     * (We also take similar care to wipe out possibly garbage
     * retaining values held in other Node fields.)  However, doing so
     * adds some further complexity to traversal: If any "next"
     * pointer links to itself, it indicates that the current thread
     * has lagged behind a head-update, and so the traversal must
     * continue from the "head".  Traversals trying to find the
     * current tail starting from "tail" may also encounter
     * self-links, in which case they also continue at "head".
     *
     * It is tempting in slack-based scheme to not even use CAS for
     * updates (similarly to Ladan-Mozes & Shavit). However, this
     * cannot be done for head updates under the above link-forgetting
     * mechanics because an update may leave head at a detached node.
     * And while direct writes are possible for tail updates, they
     * increase the risk of long retraversals, and hence long garbage
     * chains, which can be much more costly than is worthwhile
     * considering that the cost difference of performing a CAS vs
     * write is smaller when they are not triggered on each operation
     * (especially considering that writes and CASes equally require
     * additional GC bookkeeping ("write barriers") that are sometimes
     * more costly than the writes themselves because of contention).
     *
     * *** Overview of implementation ***
     *
     * We use a threshold-based approach to updates, with a slack
     * threshold of two -- that is, we update head/tail when the
     * current pointer appears to be two or more steps away from the
     * first/last node. The slack value is hard-wired: a path greater
     * than one is naturally implemented by checking equality of
     * traversal pointers except when the list has only one element,
     * in which case we keep slack threshold at one. Avoiding tracking
     * explicit counts across method calls slightly simplifies an
     * already-messy implementation. Using randomization would
     * probably work better if there were a low-quality dirt-cheap
     * per-thread one available, but even ThreadLocalRandom is too
     * heavy for these purposes.
     *
     * With such a small slack threshold value, it is not worthwhile
     * to augment this with path short-circuiting (i.e., unsplicing
     * interior nodes) except in the case of cancellation/removal (see
     * below).
     *
     * We allow both the head and tail fields to be null before any
     * nodes are enqueued; initializing upon first append.  This
     * simplifies some other logic, as well as providing more
     * efficient explicit control paths instead of letting JVMs insert
     * implicit NullPointerExceptions when they are null.  While not
     * currently fully implemented, we also leave open the possibility
     * of re-nulling these fields when empty (which is complicated to
     * arrange, for little benefit.)
     *
     * All enqueue/dequeue operations are handled by the single method
     * "xfer" with parameters indicating whether to act as some form
     * of offer, put, poll, take, or transfer (each possibly with
     * timeout). The relative complexity of using one monolithic
     * method outweighs the code bulk and maintenance problems of
     * using separate methods for each case.
     *
     * Operation consists of up to three phases. The first is
     * implemented within method xfer, the second in tryAppend, and
     * the third in method awaitMatch.
     *
     * 1. Try to match an existing node
     *
     *    Starting at head, skip already-matched nodes until finding
     *    an unmatched node of opposite mode, if one exists, in which
     *    case matching it and returning, also if necessary updating
     *    head to one past the matched node (or the node itself if the
     *    list has no other unmatched nodes). If the CAS misses, then
     *    a loop retries advancing head by two steps until either
     *    success or the slack is at most two. By requiring that each
     *    attempt advances head by two (if applicable), we ensure that
     *    the slack does not grow without bound. Traversals also check
     *    if the initial head is now off-list, in which case they
     *    start at the new head.
     *
     *    If no candidates are found and the call was untimed
     *    poll/offer, (argument "how" is NOW) return.
     *
     * 2. Try to append a new node (method tryAppend)
     *
     *    Starting at current tail pointer, find the actual last node
     *    and try to append a new node (or if head was null, establish
     *    the first node). Nodes can be appended only if their
     *    predecessors are either already matched or are of the same
     *    mode. If we detect otherwise, then a new node with opposite
     *    mode must have been appended during traversal, so we must
     *    restart at phase 1. The traversal and update steps are
     *    otherwise similar to phase 1: Retrying upon CAS misses and
     *    checking for staleness.  In particular, if a self-link is
     *    encountered, then we can safely jump to a node on the list
     *    by continuing the traversal at current head.
     *
     *    On successful append, if the call was ASYNC, return.
     *
     * 3. Await match or cancellation (method awaitMatch)
     *
     *    Wait for another thread to match node; instead cancelling if
     *    the current thread was interrupted or the wait timed out. On
     *    multiprocessors, we use front-of-queue spinning: If a node
     *    appears to be the first unmatched node in the queue, it
     *    spins a bit before blocking. In either case, before blocking
     *    it tries to unsplice any nodes between the current "head"
     *    and the first unmatched node.
     *
     *    Front-of-queue spinning vastly improves performance of
     *    heavily contended queues. And so long as it is relatively
     *    brief and "quiet", spinning does not much impact performance
     *    of less-contended queues.  During spins threads check their
     *    interrupt status and generate a thread-local random number
     *    to decide to occasionally perform a Thread.yield. While
     *    yield has underdefined specs, we assume that it might help,
     *    and will not hurt, in limiting impact of spinning on busy
     *    systems.  We also use smaller (1/2) spins for nodes that are
     *    not known to be front but whose predecessors have not
     *    blocked -- these "chained" spins avoid artifacts of
     *    front-of-queue rules which otherwise lead to alternating
     *    nodes spinning vs blocking. Further, front threads that
     *    represent phase changes (from data to request node or vice
     *    versa) compared to their predecessors receive additional
     *    chained spins, reflecting longer paths typically required to
     *    unblock threads during phase changes.
     *
     *
     * ** Unlinking removed interior nodes **
     *
     * In addition to minimizing garbage retention via self-linking
     * described above, we also unlink removed interior nodes. These
     * may arise due to timed out or interrupted waits, or calls to
     * remove(x) or Iterator.remove.  Normally, given a node that was
     * at one time known to be the predecessor of some node s that is
     * to be removed, we can unsplice s by CASing the next field of
     * its predecessor if it still points to s (otherwise s must
     * already have been removed or is now offlist). But there are two
     * situations in which we cannot guarantee to make node s
     * unreachable in this way: (1) If s is the trailing node of list
     * (i.e., with null next), then it is pinned as the target node
     * for appends, so can only be removed later after other nodes are
     * appended. (2) We cannot necessarily unlink s given a
     * predecessor node that is matched (including the case of being
     * cancelled): the predecessor may already be unspliced, in which
     * case some previous reachable node may still point to s.
     * (For further explanation see Herlihy & Shavit "The Art of
     * Multiprocessor Programming" chapter 9).  Although, in both
     * cases, we can rule out the need for further action if either s
     * or its predecessor are (or can be made to be) at, or fall off
     * from, the head of list.
     *
     * Without taking these into account, it would be possible for an
     * unbounded number of supposedly removed nodes to remain
     * reachable.  Situations leading to such buildup are uncommon but
     * can occur in practice; for example when a series of short timed
     * calls to poll repeatedly time out but never otherwise fall off
     * the list because of an untimed call to take at the front of the
     * queue.
     *
     * When these cases arise, rather than always retraversing the
     * entire list to find an actual predecessor to unlink (which
     * won't help for case (1) anyway), we record a conservative
     * estimate of possible unsplice failures (in "sweepVotes").
     * We trigger a full sweep when the estimate exceeds a threshold
     * ("SWEEP_THRESHOLD") indicating the maximum number of estimated
     * removal failures to tolerate before sweeping through, unlinking
     * cancelled nodes that were not unlinked upon initial removal.
     * We perform sweeps by the thread hitting threshold (rather than
     * background threads or by spreading work to other threads)
     * because in the main contexts in which removal occurs, the
     * caller is already timed-out, cancelled, or performing a
     * potentially O(n) operation (e.g. remove(x)), none of which are
     * time-critical enough to warrant the overhead that alternatives
     * would impose on other threads.
     *
     * Because the sweepVotes estimate is conservative, and because
     * nodes become unlinked "naturally" as they fall off the head of
     * the queue, and because we allow votes to accumulate even while
     * sweeps are in progress, there are typically significantly fewer
     * such nodes than estimated.  Choice of a threshold value
     * balances the likelihood of wasted effort and contention, versus
     * providing a worst-case bound on retention of interior nodes in
     * quiescent queues. The value defined below was chosen
     * empirically to balance these under various timeout scenarios.
     *
     * Note that we cannot self-link unlinked interior nodes during
     * sweeps. However, the associated garbage chains terminate when
     * some successor ultimately falls off the head of the list and is
     * self-linked.
     */

    /** 如果是多处理器则为 true */
    private static final boolean MP =
            Runtime.getRuntime().availableProcessors() > 1;

    /**
     * 用于计算自旋次数
     */
    private static final int FRONT_SPINS   = 1 << 7;

    /**
     * 用于计算自旋次数
     */
    private static final int CHAINED_SPINS = FRONT_SPINS >>> 1;

    /**
     * The maximum number of estimated removal failures (sweepVotes)
     * to tolerate before sweeping through the queue unlinking
     * cancelled nodes that were not unlinked upon initial
     * removal. See above for explanation. The value must be at least
     * two to avoid useless sweeps when removing trailing nodes.
     */
    static final int SWEEP_THRESHOLD = 32;

    /**
     * 队列的节点
     */
    static final class Node {
        // 如果这是一个请求数据的节点，值为 false
        // 如果这是一个已经有数据的节点，值为 true
        final boolean isData;
        volatile Object item;
        volatile Node next;
        // 等待数据的线程
        volatile Thread waiter; // null until waiting

        // CAS 修改 next
        final boolean casNext(Node cmp, Node val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        // CAS 修改 Item
        final boolean casItem(Object cmp, Object val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        /**
         * 构造函数
         */
        Node(Object item, boolean isData) {
            UNSAFE.putObject(this, itemOffset, item); // relaxed write
            this.isData = isData;
        }

        /**
         * 将节点的 next 设置成自身。
         */
        final void forgetNext() {
            UNSAFE.putObject(this, nextOffset, this);
        }

        /**
         * CAS 将节点的 item 设置成自身，将 waiter 设置成 null。
         */
        final void forgetContents() {
            UNSAFE.putObject(this, itemOffset, this);
            UNSAFE.putObject(this, waiterOffset, null);
        }

        /**
         * 如果此节点已经匹配，返回 true
         */
        final boolean isMatched() {
            Object x = item;
            // 如果节点是自链接节点，或者
            // 节点是数据节点但 x == null，或者
            // 节点是请求节点但 x != null，
            // 表示节点不符合原本的设定，已经被匹配过了
            return (x == this) || ((x == null) == isData);
        }

        /**
         * 如果节点是未匹配的请求节点，返回 true。
         */
        final boolean isUnmatchedRequest() {
            return !isData && item == null;
        }

        /**
         * 由于匹配失败，模式冲突而不能将给定模式添加到节点中，返回 true
         * Returns true if a node with the given mode cannot be
         * appended to this node because this node is unmatched and
         * has opposite data mode.
         */
        final boolean cannotPrecede(boolean haveData) {
            boolean d = isData;
            Object x;
            return d != haveData && (x = item) != this && (x != null) == d;
        }

        /**
         * 尝试匹配节点
         */
        final boolean tryMatchData() {
            // assert isData;
            Object x = item;
            // 1. item 不为 null
            // 2. 不是自链接节点
            // 3. 成功将 item 变成 null
            // 那么可以唤醒线程，节点匹配成功
            if (x != null && x != this && casItem(x, null)) {
                LockSupport.unpark(waiter);
                return true;
            }
            return false;
        }

        private static final long serialVersionUID = -3375979862319811754L;

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long itemOffset;
        private static final long nextOffset;
        private static final long waiterOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                waiterOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("waiter"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** 头节点；在第一次 enqueue 操作之前为 null */
    transient volatile Node head;

    /** 尾节点；在第一次 append 操作之前为 null */
    private transient volatile Node tail;

    /** The number of apparent failures to unsplice removed nodes */
    private transient volatile int sweepVotes;

    // CAS 方法改变头结点/尾节点/sweepVotes
    private boolean casTail(Node cmp, Node val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    private boolean casHead(Node cmp, Node val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    private boolean casSweepVotes(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, sweepVotesOffset, cmp, val);
    }

    /**
     * xfer 方法中 how 参数可能有 NOW, ASYNC, SYNC, TIMED 四个值
     */
    // 用于没有等待时间限制的 poll，tryTransfer
    private static final int NOW   = 0;
    // 用于 offer, put, add
    private static final int ASYNC = 1;
    // 用于 transfer, take
    private static final int SYNC  = 2;
    // 用于有等待时间限制的 poll, tryTransfer
    private static final int TIMED = 3;

    @SuppressWarnings("unchecked")
    // 返回 item
    static <E> E cast(Object item) {
        // assert item == null || item.getClass() != Node.class;
        return (E) item;
    }

    /**
     * 所有队列方法实现的基础。
     *
     * take 操作的 e 为 null，否则为 item
     * put 操作的 haveData 为 true，take 操作为 false
     * how 参数有四个可能的值，分别为 NOW, ASYNC, SYNC, or TIMED
     * nanos 在模式为 TIMED 时使用
     *
     * 如果匹配成功返回 item，否则返回 e
     *
     * @param e the item or null for take
     * @param haveData true if this is a put, else a take
     * @param how NOW, ASYNC, SYNC, or TIMED
     * @param nanos timeout in nanosecs, used only if mode is TIMED
     * @return an item if matched, else e
     * @throws NullPointerException if haveData mode but e is null
     */
    private E xfer(E e, boolean haveData, int how, long nanos) {
        if (haveData && (e == null))
            throw new NullPointerException();
        Node s = null;                        // the node to append, if needed

        retry:
        for (;;) {                            // restart on append race
            // 两层循环，内层从 head 开始匹配
            for (Node h = head, p = h; p != null;) { // find & match first node
                // p 节点的模式
                boolean isData = p.isData;
                // p 节点的值
                Object item = p.item;
                // 1. p 不是自链接节点
                // 2. isData 为 true 的时候如果 item 不等于 null（数据节点）
                // 或者 isData 为 false 且 item 等于 null（请求节点）
                // 满足以上两点表示找到有效节点，进入匹配
                if (item != p && (item != null) == isData) { // unmatched
                    // 已经有数据节点但是是 put 操作
                    // 没有数据但是是 take 操作
                    // 两者的模式一样，无法匹配，跳出内层循环
                    if (isData == haveData)   // can't match
                        break;
                    // 尝试 CAS 方式修改 item 为指定的 e（e 可能为 null，可能为具体的值）
                    if (p.casItem(item, e)) {
                        // 匹配成功
                        for (Node q = p; q != h;) {
                            Node n = q.next;
                            // 更新 head 为匹配节点 p 的 next 节点
                            if (head == h && casHead(h, n == null ? q : n)) {
                                // 旧的节点指向自身等待回收
                                // 然后跳出循环
                                h.forgetNext();
                                break;
                            }
                            // CAS 失败
                            // head != null 且 head.next ！= null 且 head.next 已经被匹配过了
                            // 即松弛度大于等于 2，重新循环（重新循环时 h 是新的 head，
                            // q 是 head.next）
                            // 否则跳出
                            if ((h = head)   == null ||
                                    (q = h.next) == null || !q.isMatched())
                                break;
                        }
                        // 唤醒 p 节点上等待的线程
                        LockSupport.unpark(p.waiter);
                        // 返回匹配到的元素
                        return LinkedTransferQueue.<E>cast(item);
                    }
                }
                // 继续往后
                Node n = p.next;
                // 遇到自链接节点重新获取 head
                p = (p != n) ? n : (h = head); // Use head if p offlist
            }

            // 没有匹配到
            // 如果操作是 NOW 类型，不进入 if，直接返回 e
            // 如果这个操作不是 NOW 类型，进入 if
            if (how != NOW) {
                // 如果是第一次进入这里
                if (s == null)
                    s = new Node(e, haveData);
                // 尝试将创建的节点添加到尾部，并返回其上一个节点
                Node pred = tryAppend(s, haveData);
                // 如果上一个节点为 null，与其它不同模式线程竞争失败
                // 重新外层循环
                if (pred == null)
                    continue retry;           // lost race vs opposite mode
                // 如果不是 ASYNC，自旋/让步/阻塞当前线程直到节点被匹配或者
                // 取消返回（如果是 TIMED，超时返回）
                // 如果是 ASYNC，if 执行完毕，直接 return e
                if (how != ASYNC)
                    return awaitMatch(s, pred, e, (how == TIMED), nanos);
            }
            return e;
        }
    }

    /**
     * 尝试在尾部添加节点
     *
     * @param s the node to append
     * @param haveData true if appending in data mode
     * @return null on failure due to losing race with append in
     * different mode, else s's predecessor, or s itself if no
     * predecessor
     */
    private Node tryAppend(Node s, boolean haveData) {
        // 从 tail 开始往后查找
        for (Node t = tail, p = t;;) {        // move p to last node and append
            Node n, u;                        // temps for reads of next & tail
            // tail 和 head 都为 null，链表中没有节点
            if (p == null && (p = head) == null) {
                // 如果 CAS 设置 head 为 s 成功，就返回
                // 返回的不是前驱节点（没有前驱节点），返回自身
                if (casHead(null, s))
                    return s;                 // initialize
            }
            // 队列中永远只有一种类型的操作，要么是 put，要么是 take
            // 如果模式冲突，不允许添加，返回 null
            else if (p.cannotPrecede(haveData))
                return null;                  // lost race vs opposite mode
            // 没到最后一个节点，继续往后
            else if ((n = p.next) != null)    // not last; keep traversing
                // p 重新指向 tail 节点
                p = p != t && t != (u = tail) ? (t = u) : // stale tail
                        (p != n) ? n : null;      // restart if off list
            // CAS 将 s 设置为 p 的下一个节点
            // 设置失败说明 p 的 next 已经被修改
            else if (!p.casNext(null, s))
                p = p.next;                   // re-read on CAS failure
            // s 入队成功
            else {
                // 更新 tail
                if (p != t) {                 // update if slack now >= 2
                    while ((tail != t || !casTail(t, s)) &&
                            (t = tail)   != null &&
                            (s = t.next) != null && // advance and retry
                            (s = s.next) != null && s != t);
                }
                // 返回
                return p;
            }
        }
    }

    /**
     * 自旋/让步/阻塞直到节点 s 被匹配或者调用者放弃。
     *
     * @param s the waiting node
     * @param pred the predecessor of s, or s itself if it has no
     * predecessor, or null if unknown (the null case does not occur
     * in any current calls but may in possible future extensions)
     * @param e the comparison value for checking match
     * @param timed if true, wait only until timeout elapses
     * @param nanos timeout in nanosecs, used only if timed is true
     * @return matched item, or e if unmatched on interrupt or timeout
     */
    private E awaitMatch(Node s, Node pred, E e, boolean timed, long nanos) {
        // 计算超时时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        // 当前线程
        Thread w = Thread.currentThread();
        // 自旋次数
        int spins = -1; // initialized after first item and cancel checks
        // 随机数，随机让一些自旋的线程让出时间片
        ThreadLocalRandom randomYields = null; // bound if needed

        for (;;) {
            Object item = s.item;
            // 如果 s 节点的值被修改了，说明它被匹配到了
            if (item != e) {                  // matched
                // s 变成自链接节点
                s.forgetContents();           // avoid garbage
                // 返回匹配到的元素
                return LinkedTransferQueue.<E>cast(item);
            }
            // 响应中断
            if ((w.isInterrupted() || (timed && nanos <= 0)) &&
                    s.casItem(e, s)) {        // cancel
                // 删除 s
                unsplice(pred, s);
                return e;
            }

            // 如果自旋次数小于 0
            if (spins < 0) {                  // establish spins at/near front
                // spinsFor 计算自旋次数
                if ((spins = spinsFor(pred, s.isData)) > 0)
                    // 初始化随机数
                    randomYields = ThreadLocalRandom.current();
            }
            else if (spins > 0) {             // spin
                // 剩余自旋次数减一
                --spins;
                // 随机让出时间片
                if (randomYields.nextInt(CHAINED_SPINS) == 0)
                    Thread.yield();           // occasionally yield
            }
            else if (s.waiter == null) {
                // 更新 s 的 waiter 为当前线程
                s.waiter = w;                 // request unpark then recheck
            }
            else if (timed) {
                // 有超时限制
                nanos = deadline - System.nanoTime();
                if (nanos > 0L)
                    LockSupport.parkNanos(this, nanos);
            }
            else {
                // 没有自旋次数了
                // 直接阻塞，等待被唤醒
                LockSupport.park(this);
            }
        }
    }

    /**
     * 根据给定的前驱节点和数据模式返回 spin/yield 的值。
     */
    private static int spinsFor(Node pred, boolean haveData) {
        if (MP && pred != null) {
            if (pred.isData != haveData)      // phase change
                return FRONT_SPINS + CHAINED_SPINS;
            if (pred.isMatched())             // probably at front
                return FRONT_SPINS;
            if (pred.waiter == null)          // pred apparently spinning
                return CHAINED_SPINS;
        }
        return 0;
    }

    /* -------------- Traversal methods -------------- */

    /**
     * 返回 p 的后继节点，如果 p 的 next 指向自身，返回 head 节点。
     */
    final Node succ(Node p) {
        Node next = p.next;
        return (p == next) ? head : next;
    }

    /**
     * 返回给定模式第一个未匹配的节点，如果没有返回 null。
     */
    private Node firstOfMode(boolean isData) {
        for (Node p = head; p != null; p = succ(p)) {
            if (!p.isMatched())
                return (p.isData == isData) ? p : null;
        }
        return null;
    }

    /**
     * Returns the item in the first unmatched node with isData; or
     * null if none.  Used by peek.
     */
    private E firstDataItem() {
        for (Node p = head; p != null; p = succ(p)) {
            Object item = p.item;
            if (p.isData) {
                if (item != null && item != p)
                    return LinkedTransferQueue.<E>cast(item);
            }
            else if (item == null)
                return null;
        }
        return null;
    }

    /**
     * 遍历并统计给定模式下未匹配的节点数量。
     */
    private int countOfMode(boolean data) {
        int count = 0;
        for (Node p = head; p != null; ) {
            if (!p.isMatched()) {
                if (p.isData != data)
                    return 0;
                if (++count == Integer.MAX_VALUE) // saturated
                    break;
            }
            Node n = p.next;
            if (n != p)
                p = n;
            else {
                count = 0;
                p = head;
            }
        }
        return count;
    }

    /* -------------- Removal methods -------------- */

    /**
     * 解除指定的删除/取消节点和前驱节点的链接。
     *
     * @param pred a node that was at one time known to be the
     * predecessor of s, or null or s itself if s is/was at head
     * @param s the node to be unspliced
     */
    final void unsplice(Node pred, Node s) {
        s.forgetContents(); // forget unneeded fields
        /*
         * See above for rationale. Briefly: if pred still points to
         * s, try to unlink s.  If s cannot be unlinked, because it is
         * trailing node or pred might be unlinked, and neither pred
         * nor s are head or offlist, add to sweepVotes, and if enough
         * votes have accumulated, sweep.
         */
        if (pred != null && pred != s && pred.next == s) {
            Node n = s.next;
            if (n == null ||
                    (n != s && pred.casNext(s, n) && pred.isMatched())) {
                for (;;) {               // check if at, or could be, head
                    Node h = head;
                    if (h == pred || h == s || h == null)
                        return;          // at head or list empty
                    if (!h.isMatched())
                        break;
                    Node hn = h.next;
                    if (hn == null)
                        return;          // now empty
                    if (hn != h && casHead(h, hn))
                        h.forgetNext();  // advance head
                }
                if (pred.next != pred && s.next != s) { // recheck if offlist
                    for (;;) {           // sweep now if enough votes
                        int v = sweepVotes;
                        if (v < SWEEP_THRESHOLD) {
                            if (casSweepVotes(v, v + 1))
                                break;
                        }
                        else if (casSweepVotes(v, 0)) {
                            sweep();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Unlinks matched (typically cancelled) nodes encountered in a
     * traversal from head.
     */
    private void sweep() {
        for (Node p = head, s, n; p != null && (s = p.next) != null; ) {
            if (!s.isMatched())
                // Unmatched nodes are never self-linked
                p = s;
            else if ((n = s.next) == null) // trailing node is pinned
                break;
            else if (s == n)    // stale
                // No need to also check for p == s, since that implies s == n
                p = head;
            else
                p.casNext(s, n);
        }
    }

    /**
     * remove(Object) 函数的主要实现
     */
    private boolean findAndRemove(Object e) {
        if (e != null) {
            for (Node pred = null, p = head; p != null; ) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null && item != p && e.equals(item) &&
                            p.tryMatchData()) {
                        unsplice(pred, p);
                        return true;
                    }
                }
                else if (item == null)
                    break;
                pred = p;
                if ((p = p.next) == pred) { // stale
                    pred = null;
                    p = head;
                }
            }
        }
        return false;
    }

    /**
     * 构造函数
     */
    public LinkedTransferQueue() {
    }

    /**
     * 构造函数
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedTransferQueue(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * 元素添加到末尾
     *
     * @throws NullPointerException if the specified element is null
     */
    public void put(E e) {
        xfer(e, true, ASYNC, 0);
    }

    /**
     * 元素添加到末尾
     *
     * @return {@code true} (as specified by
     *  {@link java.util.concurrent.BlockingQueue#offer(Object,long,TimeUnit)
     *  BlockingQueue.offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    /**
     * 元素添加到末尾
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    /**
     * 元素添加到队列末尾
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    /**
     * 如果可以的话，立即将元素转移给正在等待的消费者。
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e) {
        return xfer(e, true, NOW, 0) == null;
    }

    /**
     * 将元素转移给消费者，如果需要的话等待，直到元素被消费者接收。
     *
     * @throws NullPointerException if the specified element is null
     */
    public void transfer(E e) throws InterruptedException {
        if (xfer(e, true, SYNC, 0) != null) {
            Thread.interrupted(); // failure possible only due to interrupt
            throw new InterruptedException();
        }
    }

    /**
     * 将元素转移给消费者，如果需要的话等待，直到时间到期或者元素被
     * 消费者接收。
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (xfer(e, true, TIMED, unit.toNanos(timeout)) == null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    public E take() throws InterruptedException {
        E e = xfer(null, false, SYNC, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = xfer(null, false, TIMED, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }

    public E poll() {
        return xfer(null, false, NOW, 0);
    }

    /**
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * 返回迭代器
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    public E peek() {
        return firstDataItem();
    }

    /**
     * 队列为空返回 true
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        for (Node p = head; p != null; p = succ(p)) {
            if (!p.isMatched())
                return !p.isData;
        }
        return true;
    }

    // 有消费者在等待返回 true
    public boolean hasWaitingConsumer() {
        return firstOfMode(false) != null;
    }

    /**
     * 返回元素个数。
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return countOfMode(true);
    }

    // 返回等待的消费者人数
    public int getWaitingConsumerCount() {
        return countOfMode(false);
    }

    /**
     * 删除指定元素
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        return findAndRemove(o);
    }

    /**
     * 是否包含指定元素
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (Node p = head; p != null; p = succ(p)) {
            Object item = p.item;
            if (p.isData) {
                if (item != null && item != p && o.equals(item))
                    return true;
            }
            else if (item == null)
                break;
        }
        return false;
    }

    /**
     * 没有容量限制
     *
     * @return {@code Integer.MAX_VALUE} (as specified by
     *         {@link java.util.concurrent.BlockingQueue#remainingCapacity()
     *         BlockingQueue.remainingCapacity})
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long sweepVotesOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = LinkedTransferQueue.class;
            headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("tail"));
            sweepVotesOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("sweepVotes"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

