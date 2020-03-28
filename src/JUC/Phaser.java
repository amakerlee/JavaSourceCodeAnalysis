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

package JUC;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class Phaser {

    // long 类型，一共有 64 位
    // 最高位是标志位，1 表示 Phaser 的线程同步已经结束， 0 表示正在进行
    // 除了最高位之外的高 31 位存储当前阶段 phase，最大值为 Integer.MAX_VALUE
    // 中间 16 位用来存储参与者数量
    // 低 16 位用来存储未完成参与者的数量
    private volatile long state;

    // 最多有多少个参与者，换成十进制是 65535
    private static final int  MAX_PARTIES     = 0xffff;
    // 最多有多少个 phase（可以看出 phase 占了高 31 位，parties 占了中间 16 位，未完成 parties 占了低 16 位）
    private static final int  MAX_PHASE       = Integer.MAX_VALUE;
    // 参与者数量 parties 偏移
    private static final int  PARTIES_SHIFT   = 16;
    // 当前阶段 phase 偏移量
    private static final int  PHASE_SHIFT     = 32;
    // 未完成 parties 的掩码，低十六位掩码
    private static final int  UNARRIVED_MASK  = 0xffff;      // to mask ints
    // 参与者 parties 的掩码，中间 16 位
    private static final long PARTIES_MASK    = 0xffff0000L; // to mask longs
    // counts 的掩码，counts 表示低 32 位的值
    private static final long COUNTS_MASK     = 0xffffffffL;
    private static final long TERMINATION_BIT = 1L << 63;

    // 特殊值
    // 0x00000001
    private static final int  ONE_ARRIVAL     = 1;
    private static final int  ONE_PARTY       = 1 << PARTIES_SHIFT;
    // 0x00010001
    private static final int  ONE_DEREGISTER  = ONE_ARRIVAL|ONE_PARTY;
    // 如果 counts（state 的低 32 位）等于 1 表示空，不表示有一个未到达
    // 有一个未到达应该是 0x00010001
    private static final int  EMPTY           = 1;

    // 未完成参与者数量
    private static int unarrivedOf(long s) {
        int counts = (int)s;
        return (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
    }

    // 参与者数量（中间 16 位）
    // 先计算 int，即保留 s 的后 32 位，然后向右移 16 位
    private static int partiesOf(long s) {
        return (int)s >>> PARTIES_SHIFT;
    }

    // 当前节点 phase
    // 先向右移 32 位，然后转 int，直接取后 32 位
    private static int phaseOf(long s) {
        return (int)(s >>> PHASE_SHIFT);
    }

    // 已完成参与者数量
    private static int arrivedOf(long s) {
        // 取低 32 位
        int counts = (int)s;
        // 总的 parties 数（中间 16 位）减去还未完成参与者数量（低 16 位）
        return (counts == EMPTY) ? 0 :
                (counts >>> PARTIES_SHIFT) - (counts & UNARRIVED_MASK);
    }

    /**
     * 此 phaser 的父节点
     */
    private final Phaser parent;

    /**
     * phaser 树的根节点，如果没有树则就表示当前 phaser
     */
    private final Phaser root;

    /**
     * 等待线程的队列，根据 phase 的奇偶性分开
     * 所有子节点共用根节点的这两个队列
     */
    private final AtomicReference<QNode> evenQ;
    private final AtomicReference<QNode> oddQ;

    // 根据当前的 phase 阶段，返回不同的队列
    private AtomicReference<QNode> queueFor(int phase) {
        return ((phase & 1) == 0) ? evenQ : oddQ;
    }

    /**
     * Returns message string for bounds exceptions on arrival.
     */
    private String badArrive(long s) {
        return "Attempted arrival of unregistered party for " +
                stateToString(s);
    }

    /**
     * Returns message string for bounds exceptions on registration.
     */
    private String badRegister(long s) {
        return "Attempt to register more than " +
                MAX_PARTIES + " parties for " + stateToString(s);
    }

    /**
     * arrive 和 arriveAndDeregister 的实现。
     *
     * @param adjust value to subtract from state;
     *               ONE_ARRIVAL for arrive,
     *               ONE_DEREGISTER for arriveAndDeregister
     */
    private int doArrive(int adjust) {
        // arrive 需要把未到达的线程数减 1，
        // arriveAndDeregister 需要把 parties 值和未到达线程数都减 1
        final Phaser root = this.root;
        for (;;) {
            // 获取 state
            long s = (root == this) ? state : reconcileState();
            // 获取 phase
            int phase = (int)(s >>> PHASE_SHIFT);
            // 已经停止，返回 phase
            if (phase < 0)
                return phase;
            // 获取 counts
            int counts = (int)s;
            // 未到达线程数
            int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
            if (unarrived <= 0)
                throw new IllegalStateException(badArrive(s));
            // 尝试 CAS 更新 state，更新失败继续自旋
            if (UNSAFE.compareAndSwapLong(this, stateOffset, s, s-=adjust)) {
                // 所有线程都已经到达
                if (unarrived == 1) {
                    long n = s & PARTIES_MASK;  // base of next state
                    // 获取总的 parties 数
                    int nextUnarrived = (int)n >>> PARTIES_SHIFT;
                    if (root == this) {
                        // 结束
                        if (onAdvance(phase, nextUnarrived))
                            n |= TERMINATION_BIT;
                        // 未到达线程数为 0
                        else if (nextUnarrived == 0)
                            n |= EMPTY;
                        // 正常开启下一个 phase
                        else
                            n |= nextUnarrived;
                        // 计算下一个 phase 的值
                        int nextPhase = (phase + 1) & MAX_PHASE;
                        n |= (long)nextPhase << PHASE_SHIFT;
                        // CAS 更新 state，开启下一个 phase 阶段
                        UNSAFE.compareAndSwapLong(this, stateOffset, s, n);
                        releaseWaiters(phase);
                    }
                    // 下一个未到达线程数为 0
                    else if (nextUnarrived == 0) { // propagate deregistration
                        // 父节点处理 doArrive
                        // 当前状态改为 EMPTY
                        phase = parent.doArrive(ONE_DEREGISTER);
                        UNSAFE.compareAndSwapLong(this, stateOffset,
                                s, s | EMPTY);
                    }
                    // 有 parent 且下一个未到达线程数不为 0
                    // 由父节点处理 doArrive
                    else
                        phase = parent.doArrive(ONE_ARRIVAL);
                }
                return phase;
            }
        }
    }

    /**
     * register 和 bulkRegister 的实现
     *
     * @param registrations number to add to both parties and
     * unarrived fields. Must be greater than zero.
     */
    private int doRegister(int registrations) {
        // adjust 是同时在中间 16 位和低 16 位上加，也就是同时加总的 parties 和未到达 parties
        long adjust = ((long)registrations << PARTIES_SHIFT) | registrations;
        final Phaser parent = this.parent;
        int phase;
        for (;;) {
            // 获取 state 值
            long s = (parent == null) ? state : reconcileState();
            // state 的低 32 位
            int counts = (int)s;
            // parties 的数量
            int parties = counts >>> PARTIES_SHIFT;
            // 未到达 parties 的数量
            int unarrived = counts & UNARRIVED_MASK;
            // 如果超出最大 parties 的限制了，抛出异常
            if (registrations > MAX_PARTIES - parties)
                throw new IllegalStateException(badRegister(s));
            // 当前 phase
            phase = (int)(s >>> PHASE_SHIFT);
            // 当前 phase 的最高位为 1，表示已经停止
            if (phase < 0)
                break;
            // 不是第一个参与者
            if (counts != EMPTY) {                  // not 1st registration
                if (parent == null || reconcileState() == s) {
                    // 未到达数等于 0 说明当前正在执行 onAdvance 方法，等待其执行完毕
                    if (unarrived == 0)             // wait out advance
                        root.internalAwaitAdvance(phase, null);
                    // 否则使用 CAS 修改 state 的值，修改成功跳出循环
                    else if (UNSAFE.compareAndSwapLong(this, stateOffset,
                            s, s + adjust))
                        break;
                }
            }
            // 是第一个参与者且没有 parent
            else if (parent == null) {              // 1st root registration
                // 计算 state 的值，
                long next = ((long)phase << PHASE_SHIFT) | adjust;
                // CAS 修改 state 的值
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next))
                    break;
            }
            // 第一个参与者，有 parent
            else {
                // 锁定当前 phaser
                synchronized (this) {               // 1st sub registration
                    // 再次检查 state 的值，确保没有更新
                    if (state == s) {               // recheck under lock
                        // 说当前 Phaser 是新加入的，把它注册到父 phaser 中去
                        // 注意是注册的 1 个而不是 registrations 个
                        phase = parent.doRegister(1);
                        if (phase < 0)
                            break;
                        // 注册到父节点之后再更新当前 phaser 的 state 直到成功为止
                        while (!UNSAFE.compareAndSwapLong
                                (this, stateOffset, s,
                                        ((long)phase << PHASE_SHIFT) | adjust)) {
                            s = state;
                            phase = (int)(root.state >>> PHASE_SHIFT);
                            // assert (int)s == EMPTY;
                        }
                        break;
                    }
                }
            }
        }
        return phase;
    }

    /**
     * 调整当前 Phaser 的阶段，和 root 保持一致。
     * 在树形结构中根节点是最先进行 phase 跃迁的，因此需要显式同步，以便和
     * 根节点保持一致。
     * reconcileState 利用自旋 + CAS 修改当前 Phaser 的阶段。
     * reconciliation 通常发生在根 Phaser 已跃迁而子 Phaser 还没有跃迁的情况下，
     * 所以需要把 unarrived 设置成 parties
     *
     * @return reconciled state
     */
    private long reconcileState() {
        // root Phaser
        final Phaser root = this.root;
        // 当前状态
        long s = state;
        // root 不是当前 Phaser，说明有 parent
        if (root != this) {
            int phase, p;
            // CAS to root phase with current parties, tripping unarrived
            // 如果 root 的 phase 不等于当前的 phase，说明跃迁了。
            // 如果 root 的 phase 等于当前的 phase，直接返回就行了。
            // 如果跃迁了，尝试把当前的 state 里的 phase 更新为 root 节点的 phase。
            // s & PARTIES_MASK 是 64 位，形如 0x0000000011110000
            // p 是 32 位，形如 0x00001111
            // (s & PARTIES_MASK) | p) 计算出来是 counts
            while ((phase = (int)(root.state >>> PHASE_SHIFT)) !=
                    (int)(s >>> PHASE_SHIFT) &&
                    !UNSAFE.compareAndSwapLong
                            (this, stateOffset, s,
                                    s = (((long)phase << PHASE_SHIFT) |
                                            ((phase < 0) ? (s & COUNTS_MASK) :
                                                    (((p = (int)s >>> PARTIES_SHIFT) == 0) ? EMPTY :
                                                            ((s & PARTIES_MASK) | p))))))
                s = state;
        }
        return s;
    }

    /**
     * 构造函数
     */
    public Phaser() {
        this(null, 0);
    }

    /**
     * 构造函数
     *
     * @param parties the number of parties required to advance to the
     * next phase
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(int parties) {
        this(null, parties);
    }

    /**
     * 构造函数
     *
     * @param parent the parent phaser
     */
    public Phaser(Phaser parent) {
        this(parent, 0);
    }

    /**
     * 构造函数
     *
     * @param parent the parent phaser
     * @param parties the number of parties required to advance to the
     * next phase
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(Phaser parent, int parties) {
        // parties 不能超过 65535
        if (parties >>> PARTIES_SHIFT != 0)
            throw new IllegalArgumentException("Illegal number of parties");
        int phase = 0;
        this.parent = parent;
        // 如果指定 parent 不为 null
        if (parent != null) {
            // 指定 root
            final Phaser root = parent.root;
            this.root = root;
            // 共享父级的等待队列
            this.evenQ = root.evenQ;
            this.oddQ = root.oddQ;
            // 如果当前 phaser 的 parties 不为 0
            if (parties != 0)
                // 注册一个参与者到父级
                phase = parent.doRegister(1);
        }
        else {
            // 如果父级为 null，那么 root 就是自身
            this.root = this;
            this.evenQ = new AtomicReference<QNode>();
            this.oddQ = new AtomicReference<QNode>();
        }
        this.state = (parties == 0) ? (long)EMPTY :
                ((long)phase << PHASE_SHIFT) |
                        ((long)parties << PARTIES_SHIFT) |
                        ((long)parties);
    }

    /**
     * 注册一个新 party。
     *
     * @return the arrival phase number to which this registration
     * applied.  If this value is negative, then this phaser has
     * terminated, in which case registration has no effect.
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     */
    public int register() {
        return doRegister(1);
    }

    /**
     * 批量注册。
     *
     * @param parties the number of additional parties required to
     * advance to the next phase
     * @return the arrival phase number to which this registration
     * applied.  If this value is negative, then this phaser has
     * terminated, in which case registration has no effect.
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     * @throws IllegalArgumentException if {@code parties < 0}
     */
    public int bulkRegister(int parties) {
        if (parties < 0)
            throw new IllegalArgumentException();
        if (parties == 0)
            return getPhase();
        return doRegister(parties);
    }

    /**
     * 使当前线程到达 phase，不等待其他任务到达。返回到达阶段的 phase 值。
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arrive() {
        return doArrive(ONE_ARRIVAL);
    }

    /**
     * 使当前线程到达 phase 并撤销注册，返回到达阶段的 phase 值。
     * 如果当前 Phaser 有父节点，且撤销注册会导致当前 Phaser 的 parties 值变成 0，
     * 那么当前 Phaser 也会从父节点中撤销注册。
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of registered or unarrived parties would become negative
     */
    public int arriveAndDeregister() {
        return doArrive(ONE_DEREGISTER);
    }

    /**
     * 使当前线程到达 phaser 并等待其他任务到达，等价于 awaitAdvance(arrive())。
     * 如果需要等待中断或超时，可以使用awaitAdvance方法完成一个类似的构造。
     * 如果需要在到达后取消注册，可以使用awaitAdvance(arriveAndDeregister())。
     *
     * @return the arrival phase number, or the (negative)
     * {@linkplain #getPhase() current phase} if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arriveAndAwaitAdvance() {
        // Specialization of doArrive+awaitAdvance eliminating some reads/paths
        final Phaser root = this.root;
        // 自旋
        for (;;) {
            // 获取 state
            long s = (root == this) ? state : reconcileState();
            // 获取当前阶段 phase
            int phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                return phase;
            // 获取 counts
            int counts = (int)s;
            // 获取 unarrived
            int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
            if (unarrived <= 0)
                throw new IllegalStateException(badArrive(s));
            // 尝试 CAS 把 state 更新为 state - 1，如果成功进入 if 块
            // 失败了继续自旋
            if (UNSAFE.compareAndSwapLong(this, stateOffset, s,
                    s -= ONE_ARRIVAL)) {
                // 如果不是最后一个到达的，调用 internalAwaitAdvance 方法自旋或进入队列等待
                if (unarrived > 1)
                    return root.internalAwaitAdvance(phase, null);

                // 到这里说明是最后一个到达的
                // 是分层结构，由父节点递归处理 arriveAndAwaitAdvance，相当于进阶
                // 直到根节点的 phase 进阶了才算进阶
                if (root != this)
                    return parent.arriveAndAwaitAdvance();

                // 获取 parties 总数，保存在 nextUnarrived 中
                long n = s & PARTIES_MASK;  // base of next state
                int nextUnarrived = (int)n >>> PARTIES_SHIFT;
                // 得到新的 n
                // 如果已注册的 parties 数等于 0
                if (onAdvance(phase, nextUnarrived))
                    n |= TERMINATION_BIT;
                    // parties 总数等于 0
                else if (nextUnarrived == 0)
                    n |= EMPTY;
                else
                    n |= nextUnarrived;

                // 当前阶段 phase 的值加 1
                int nextPhase = (phase + 1) & MAX_PHASE;
                // n 表示新的 state
                n |= (long)nextPhase << PHASE_SHIFT;
                // 尝试 CAS 更新 state，如果失败了，返回更新前的 phase 的值
                // 这里更新失败直接就返回应该是因为只有最后一个线程了吧
                if (!UNSAFE.compareAndSwapLong(this, stateOffset, s, n))
                    return (int)(state >>> PHASE_SHIFT); // terminated
                // 唤醒其它参与者并进入下一个阶段
                releaseWaiters(phase);
                // 返回下一阶段 phase 的值
                return nextPhase;
            }
        }
    }

    /**
     * 线程等到当前 phase 结束并转到下一个 phase 的过程。
     * 如果当前 phase 不等于执行的 phase 值或者此 phaser 被结束了，立刻返回
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or {@code arriveAndDeregister}.
     * @return the next arrival phase number, or the argument if it is
     * negative, or the (negative) {@linkplain #getPhase() current phase}
     * if terminated
     */
    public int awaitAdvance(int phase) {
        final Phaser root = this.root;
        // 获取当前 state
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);

        // phaser 是否已经结束
        if (phase < 0)
            return phase;
        // 如果 phase 值一致
        if (p == phase)
            return root.internalAwaitAdvance(phase, null);
        return p;
    }

    /**
     * 响应中断。
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or {@code arriveAndDeregister}.
     * @return the next arrival phase number, or the argument if it is
     * negative, or the (negative) {@linkplain #getPhase() current phase}
     * if terminated
     * @throws InterruptedException if thread interrupted while waiting
     */
    public int awaitAdvanceInterruptibly(int phase)
            throws InterruptedException {
        final Phaser root = this.root;
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase) {
            // 创造 QNode
            QNode node = new QNode(this, phase, true, false, 0L);
            p = root.internalAwaitAdvance(phase, node);
            // 如果节点的中断状态被设置，直接中断
            if (node.wasInterrupted)
                throw new InterruptedException();
        }
        return p;
    }

    /**
     * 响应中断和超时。
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or {@code arriveAndDeregister}.
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the next arrival phase number, or the argument if it is
     * negative, or the (negative) {@linkplain #getPhase() current phase}
     * if terminated
     * @throws InterruptedException if thread interrupted while waiting
     * @throws TimeoutException if timed out while waiting
     */
    public int awaitAdvanceInterruptibly(int phase,
                                         long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        final Phaser root = this.root;
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase) {
            QNode node = new QNode(this, phase, true, true, nanos);
            p = root.internalAwaitAdvance(phase, node);
            if (node.wasInterrupted)
                throw new InterruptedException();
            // 没有进入下一个 phase，抛出超时异常
            else if (p == phase)
                throw new TimeoutException();
        }
        return p;
    }

    /**
     * 使当前 Phaser 进入终止状态，已注册的 parties 不受影响，如果是分层结构，则终止所有 Phaser
     */
    public void forceTermination() {
        // Only need to change root state
        final Phaser root = this.root;
        long s;
        while ((s = root.state) >= 0) {
            if (UNSAFE.compareAndSwapLong(root, stateOffset,
                    s, s | TERMINATION_BIT)) {
                // signal all threads
                releaseWaiters(0); // Waiters on evenQ
                releaseWaiters(1); // Waiters on oddQ
                return;
            }
        }
    }

    /**
     * Returns the current phase number. The maximum phase number is
     * {@code Integer.MAX_VALUE}, after which it restarts at
     * zero. Upon termination, the phase number is negative,
     * in which case the prevailing phase prior to termination
     * may be obtained via {@code getPhase() + Integer.MIN_VALUE}.
     *
     * @return the phase number, or a negative value if terminated
     */
    public final int getPhase() {
        return (int)(root.state >>> PHASE_SHIFT);
    }

    /**
     * Returns the number of parties registered at this phaser.
     *
     * @return the number of parties
     */
    public int getRegisteredParties() {
        return partiesOf(state);
    }

    /**
     * Returns the number of registered parties that have arrived at
     * the current phase of this phaser. If this phaser has terminated,
     * the returned value is meaningless and arbitrary.
     *
     * @return the number of arrived parties
     */
    public int getArrivedParties() {
        return arrivedOf(reconcileState());
    }

    /**
     * Returns the number of registered parties that have not yet
     * arrived at the current phase of this phaser. If this phaser has
     * terminated, the returned value is meaningless and arbitrary.
     *
     * @return the number of unarrived parties
     */
    public int getUnarrivedParties() {
        return unarrivedOf(reconcileState());
    }

    /**
     * Returns the parent of this phaser, or {@code null} if none.
     *
     * @return the parent of this phaser, or {@code null} if none
     */
    public Phaser getParent() {
        return parent;
    }

    /**
     * Returns the root ancestor of this phaser, which is the same as
     * this phaser if it has no parent.
     *
     * @return the root ancestor of this phaser
     */
    public Phaser getRoot() {
        return root;
    }

    /**
     * Returns {@code true} if this phaser has been terminated.
     *
     * @return {@code true} if this phaser has been terminated
     */
    public boolean isTerminated() {
        return root.state < 0L;
    }

    /**
     * Overridable method to perform an action upon impending phase
     * advance, and to control termination. This method is invoked
     * upon arrival of the party advancing this phaser (when all other
     * waiting parties are dormant).  If this method returns {@code
     * true}, this phaser will be set to a final termination state
     * upon advance, and subsequent calls to {@link #isTerminated}
     * will return true. Any (unchecked) Exception or Error thrown by
     * an invocation of this method is propagated to the party
     * attempting to advance this phaser, in which case no advance
     * occurs.
     *
     * <p>The arguments to this method provide the state of the phaser
     * prevailing for the current transition.  The effects of invoking
     * arrival, registration, and waiting methods on this phaser from
     * within {@code onAdvance} are unspecified and should not be
     * relied on.
     *
     * <p>If this phaser is a member of a tiered set of phasers, then
     * {@code onAdvance} is invoked only for its root phaser on each
     * advance.
     *
     * <p>To support the most common use cases, the default
     * implementation of this method returns {@code true} when the
     * number of registered parties has become zero as the result of a
     * party invoking {@code arriveAndDeregister}.  You can disable
     * this behavior, thus enabling continuation upon future
     * registrations, by overriding this method to always return
     * {@code false}:
     *
     * <pre> {@code
     * Phaser phaser = new Phaser() {
     *   protected boolean onAdvance(int phase, int parties) { return false; }
     * }}</pre>
     *
     * @param phase the current phase number on entry to this method,
     * before this phaser is advanced
     * @param registeredParties the current number of registered parties
     * @return {@code true} if this phaser should terminate
     */
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties == 0;
    }

    /**
     * Returns a string identifying this phaser, as well as its
     * state.  The state, in brackets, includes the String {@code
     * "phase = "} followed by the phase number, {@code "parties = "}
     * followed by the number of registered parties, and {@code
     * "arrived = "} followed by the number of arrived parties.
     *
     * @return a string identifying this phaser, as well as its state
     */
    public String toString() {
        return stateToString(reconcileState());
    }

    /**
     * Implementation of toString and string-based error messages
     */
    private String stateToString(long s) {
        return super.toString() +
                "[phase = " + phaseOf(s) +
                " parties = " + partiesOf(s) +
                " arrived = " + arrivedOf(s) + "]";
    }

    // Waiting mechanics

    /**
     * 从队列中删除和唤醒线程。
     */
    private void releaseWaiters(int phase) {
        QNode q;   // first element of queue
        Thread t;  // its thread
        AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
        // 唤醒上一代所有的等待线程，确保旧的队列中没有遗留的等待线程
        while ((q = head.get()) != null &&
                q.phase != (int)(root.state >>> PHASE_SHIFT)) {
            // 从 head 开始依次唤醒
            if (head.compareAndSet(q, q.next) &&
                    (t = q.thread) != null) {
                q.thread = null;
                // 唤醒
                LockSupport.unpark(t);
            }
        }
    }

    /**
     * 将当前正在使用的队列中由于超时或者中断不在等待当前 Phaser 的下一阶段的节点移除。
     * 唤醒那些没有被唤醒的节点。
     * Variant of releaseWaiters that additionally tries to remove any
     * nodes no longer waiting for advance due to timeout or
     * interrupt. Currently, nodes are removed only if they are at
     * head of queue, which suffices to reduce memory footprint in
     * most usages.
     *
     * @return current phase on exit
     */
    private int abortWait(int phase) {
        AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
        for (;;) {
            Thread t;
            QNode q = head.get();
            int p = (int)(root.state >>> PHASE_SHIFT);
            if (q == null || ((t = q.thread) != null && q.phase == p))
                return p;
            if (head.compareAndSet(q, q.next) && t != null) {
                q.thread = null;
                LockSupport.unpark(t);
            }
        }
    }

    /** CPU 个数 */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * 自旋次数
     * The number of times to spin before blocking while waiting for
     * advance, per arrival while waiting. On multiprocessors, fully
     * blocking and waking up a large number of threads all at once is
     * usually a very slow process, so we use rechargeable spins to
     * avoid it when threads regularly arrive: When a thread in
     * internalAwaitAdvance notices another arrival before blocking,
     * and there appear to be enough CPUs available, it spins
     * SPINS_PER_ARRIVAL more times before blocking. The value trades
     * off good-citizenship vs big unnecessary slowdowns.
     */
    static final int SPINS_PER_ARRIVAL = (NCPU < 2) ? 1 : 1 << 8;

    /**
     * 实现线程等待。
     *
     * @param phase current phase
     * @param node if non-null, the wait node to track interrupt and timeout;
     * if null, denotes noninterruptible wait
     * @return current phase
     */
    private int internalAwaitAdvance(int phase, QNode node) {
        // assert root == this;
        // 释放上一个 phase 的资源，保证上一代的队列为空
        releaseWaiters(phase-1);          // ensure old queue clean
        // node 是否被加入到队列中
        boolean queued = false;           // true when node is enqueued
        int lastUnarrived = 0;            // to increase spins upon change
        // 自旋次数
        int spins = SPINS_PER_ARRIVAL;
        long s;
        int p;
        // 检查当前阶段是否变化，如果变化了说明进入下一个阶段了，这时候就没有必要自旋了
        while ((p = (int)((s = state) >>> PHASE_SHIFT)) == phase) {
            // 如果 node 为 null，说明是不响应中断的模式
            // 一直自旋，直到自旋的次数用完
            if (node == null) {           // spinning in noninterruptible mode
                // 未完成的参与者数量
                int unarrived = (int)s & UNARRIVED_MASK;
                // unarrived 相比上一次有变化，但小于 cpu 个数，自旋次数增加
                if (unarrived != lastUnarrived &&
                        (lastUnarrived = unarrived) < NCPU)
                    spins += SPINS_PER_ARRIVAL;
                // 有没有中断
                boolean interrupted = Thread.interrupted();
                // 如果被中断或者自旋次数完了
                if (interrupted || --spins < 0) { // need node to record intr
                    // 创建一个新节点
                    node = new QNode(this, phase, false, false, 0L);
                    // 节点的中断状态
                    node.wasInterrupted = interrupted;
                }
            }
            // 如果节点可以结束等待了
            else if (node.isReleasable()) // done or aborted
                break;
            // 如果节点还没有入队列，把 node 加入队列
            else if (!queued) {           // push onto queue
                // 节点入队列
                AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
                // 插入到队列头部
                QNode q = node.next = head.get();
                if ((q == null || q.phase == phase) &&
                        (int)(state >>> PHASE_SHIFT) == phase) // avoid stale enq
                    // 添加成功，把 queued 的值改成 true
                    queued = head.compareAndSet(q, node);
            }
            // node 加入到队列了，等待被唤醒
            else {
                try {
                    // 当前线程进入阻塞状态，跟调用LockSupport.park()一样，等待被唤醒
                    ForkJoinPool.managedBlock(node);
                } catch (InterruptedException ie) {
                    node.wasInterrupted = true;
                }
            }
        }

        // 到这里说明节点所在线程已经被唤醒了
        if (node != null) {
            if (node.thread != null)
                node.thread = null;       // avoid need for unpark()
            if (node.wasInterrupted && !node.interruptible)
                Thread.currentThread().interrupt();
            if (p == phase && (p = (int)(state >>> PHASE_SHIFT)) == phase)
                // 唤醒那些没有被唤醒的节点
                return abortWait(phase); // possibly clean up on abort
        }
        // 唤醒当前阶段阻塞的线程
        releaseWaiters(phase);
        return p;
    }

    /**
     * 在队列中等待的节点
     */
    static final class QNode implements ForkJoinPool.ManagedBlocker {
        final Phaser phaser;
        final int phase;
        final boolean interruptible;
        final boolean timed;
        boolean wasInterrupted;
        long nanos;
        final long deadline;
        // 节点代表的线程
        volatile Thread thread;
        // 单向链表，指向下一个节点
        QNode next;

        QNode(Phaser phaser, int phase, boolean interruptible,
              boolean timed, long nanos) {
            this.phaser = phaser;
            this.phase = phase;
            this.interruptible = interruptible;
            this.nanos = nanos;
            this.timed = timed;
            this.deadline = timed ? System.nanoTime() + nanos : 0L;
            thread = Thread.currentThread();
        }

        // 是否被 release 了，线程为空、phase 改变、被中断、超时，都表示被 release 了
        public boolean isReleasable() {
            if (thread == null)
                return true;
            if (phaser.getPhase() != phase) {
                thread = null;
                return true;
            }
            if (Thread.interrupted())
                wasInterrupted = true;
            if (wasInterrupted && interruptible) {
                thread = null;
                return true;
            }
            if (timed) {
                if (nanos > 0L) {
                    nanos = deadline - System.nanoTime();
                }
                if (nanos <= 0L) {
                    thread = null;
                    return true;
                }
            }
            return false;
        }

        // 阻塞线程
        public boolean block() {
            if (isReleasable())
                return true;
            // 没有时间限制
            else if (!timed)
                LockSupport.park(this);
            // 有时间限制
            else if (nanos > 0L)
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = Phaser.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

