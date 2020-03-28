## Phaser

和 CountDownLatch、CyclicBarrier 相似，适用于以下场景：

一个大任务可以分为多个阶段完成，且每个阶段的任务可以多个线程并发执行，但是必须上一个阶段的任务都完成了才可以执行下一个阶段的任务。

### 完整源码解析

[Phaser](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/Phaser.java)

### 重要概念

phase：在 CyclicBarrier 称为 generation，在这里叫做阶段（phase）。任意时间点，Phaser只处于某一个阶段，初始阶段为 0，最大达到 Integerr.MAX_VALUE，然后再次归零。当所有 parties 参与者都到达后，phase 值会递增。

parties：参与者。

arrive：注册完 parties（参与者）之后，参与者的初始状态是 unarrived，当参与者到达当前阶段后，状态就会变成 arrived。当阶段的到达参与者数满足条件后（注册的数量等于到达的数量），阶段就会发生进阶——也就是 phase 值加 1。

### 分层模型

在后面的“成员函数”一节中将会看到，几乎所有的函数中都使用了 CAS 操作来保证线程安全。当并发量逐渐增大的时候，CAS 失败的概率非常大，线程将会把大量的时间浪费在自旋上。

Phaser 使用分层来缓解这一压力。笔者认为此处其实更类似于垂直切分。把原来由一个 Phaser 管理的任务，变成多个 Phaser 管理，而这些 Phaser 之间的联系就是 root Phaser。

首先要注意以下两点：

* 所有的 Phaser 共用 evenQ/addQ 队列，这两个队列保存等待线程。
* 当某个 Phaser 连接到树中时，会同时向给节点的父节点注册一个 party。那么显而易见，此处的 party 不表示线程，而表示 Phaser 节点。即，叶子结点的参与者（party）指的是具体的线程（任务），非叶子节点的参与者指的是 Phaser。

当叶子结点的所有 party 到达之后，它会通知自己的父节点 Phaser，父节点的未到达参与者数量减 1，而当父节点所有参与者到达之后，又会继续向上传递。最后，当根节点的参与者都到达时，将会释放队列中等待的线程，并将 phase 数加 1。

必须注意的是，phase 只有 root 能自己改，其他的节点必须以 root 的 phase 为准。

*关于分层模型，[Java多线程进阶（二二）—— J.U.C之synchronizer框架：Phaser](https://segmentfault.com/a/1190000015979879) 中有详细的图文解释。*

### 类属性

state 是此类中最重要的属性，所有的操作都围绕 state 进行。

state 是 long 类型，一共有 64 个 bit，其中包含了以下四项：

* 是否停止，占据最高位，1 表示 Phaser 已经停止，0 表示没有停止。
* 当前阶段 phase，占据除最高位之外的高 31 位。通常 phase 简略地用高 32 位表示，当 phase 小于 0 即最高位为 1 时，表示 Phaser 已经停止；当 phase 大于 0 时表示 Phaser 还没有停止。
* 参与者总数 parties，占据中间 16 位。
* 还未到达的参与数，占据低 16 位置。源码中有些地方会出现 counts，表示 state 的低 32 位，也就是把 parties 和 unarrived 合在了一起。

```java
    // long 类型，一共有 64 位
    // 最高位是标志位，1 表示 Phaser 的线程同步已经结束， 0 表示正在进行
    // 除了最高位之外的高 31 位存储当前阶段 phase，最大值为 Integer.MAX_VALUE
    // 中间 16 位用来存储参与者数量
    // 低 16 位用来存储未完成参与者的数量
    private volatile long state;

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
```

### 常量

state 是此类中最重要的属性，所有的操作都围绕 state 进行。

state 中包含了四个属性和大量的位运算，下面的常量为这些位运算提供支持。

```java
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
```

### 成员函数

#### register

注册一个新的 party，有两个 public 方法可供调用，分别是注册一个 party 的 register 和批量注册的 bulkRegister。

```java
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
```

**doRegister**

它们都调用了 doRegister 来完成注册的功能。

最外层是自旋操作，自旋结束后返回的是当前阶段的 phase 值。

每一次自旋首先获取 state，进而获取 phase、parties、unarrived。如果是多层结构，需要使用 reconcileState 函数获取 state，在次函数中还会同步当前 Phaser 的状态值，使其和 root 保持一致。

然后对以下三种情况分别进行讨论：

* 要注册的 party 不是 Phaser 中的第一个参与者：如果 unarrived 的值为 0，说明当前正在两个 phase 之间的过渡期（onAdvance 方法正在执行），调用 root.internalAwaitAdvance 等待其执行完毕；如果 unarrived 不为 0，直接修改 state 进行注册，修改成功表示注册成功，马上结束自旋，否则继续下一次自旋。

* 要注册的 party 是第一个参与者，但不是分层结构或者已经到 root 了：（不需要向父节点注册）直接修改 state。

* 要注册的 party 是第一个参与者，而且是分层结构且没到 root：说明 Phaser 是新加入的节点，首先需要将自身注册到父节点 Phaser 中，之后更新自身的 state 直到成功为止，最后跳出自旋。

```java
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
```

**reconcileState**

reconcileState 用来调整当前 Phaser 的 phase 值，使其和 root 保持一致。

phase 值永远以 root 为准，只有 root 才能进阶。

```java
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
```

**internalAwaitAdvance**

internalAwaitAdvance 是比较重要的一个函数，很多函数都调用了它。它的作用是自旋和等待，也可能会把线程加入到等待队列中。

首先调用 releaseWaiters 唤醒上一阶段的所有等待线程，确保旧的队列中没有遗留的等待线程。

进入循环，循环停止的条件是 phase 变化了，即进入了下一个阶段。

每一次循环分为以下几种情况：

* 如果 node 等于 null，表示仍然在自旋的过程中，继续自旋。如果被中断或者自旋次数用完了，则创造新节点赋值给 node，下一次就不会进入这一个 if 分支了。

* 如果节点的 isReleasable 函数返回 true，说明可以结束等待了，直接跳出 while 循环。

* 如果节点没有进入队列，把节点插入到队列头部。

* 如果节点已经进入队列了，阻塞节点线程，等待被唤醒。

```java
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
                return abortWait(phase); // possibly clean up on abort
        }
        // 唤醒当前阶段阻塞的线程
        releaseWaiters(phase);
        return p;
    }
```

**releaseWaiters**

释放指定 phase 阶段的所有等待线程。

```java
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
```

**节点类 QNode**

```java
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
```

#### arrive

使当前线程到达 phase，不等待其他任务到达。

和 arrive 相关的有两个方法，分别是：

```java
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
```

它们都调用了 doArrive。

**doArrive**

实现的思路是把 state 的值更改为 state - adjust。另外，如果当前线程是最后一个到达的线程，还需要执行“进阶”相关的操作。

进阶相关的操作指的是 phase 变成下一个阶段，具体来说就是需要修改 state。

进阶操作主要修改的是 phase 值，分成以下几种情况：

* 如果当前 Phaser 是 root Phaser（或者当前 Phaser 没有父节点）：执行进阶操作，首先判断 Phaser 是否结束，然后把 phase 值加 1，把 unarrived 的值重置为 parties 的值。

* 如果当前 Phaser 不是 root Phaser，且新的 parties 为 0，调用父节点的 doArrive 函数把当前 Phaser 从父节点中卸载，然后把当前 Phaser 状态改为 EMPTY。

* 如果当前 Phaser 不是 root Phaser，且新的 parties 不为 0，直接调用父节点的 doArrive 函数，参数为 ONE_ARRIVAL。

由此可以看出，进阶操作需要层层递进，最终决定进阶（修改 phase 值）的，有且只有 root 节点。当 root 的所有子节点都完成进阶了，root 才会修改 phase 宣布进阶。

当 root 修改 phase 值之后，需要把这一改变传递到所有的子节点中，这一操作是通过 reconcileState 来完成的。只要是多层模式，需要获取 state 时都只能通过 reconcileState 来获取。

```java
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
                        // Phaser 结束
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
```

#### arriveAndAwaitAdvance

使当前线程到达 phase 并等待其他任务到达，等价于 awaitAdvance(arrive())。

最外层是自旋操作，直到执行某一个 return 语句。

对于每一次自旋，首先获取 state、phase、parties、unarrived 等，然后尝试 CAS 把 unarrived 减 1。如果修改失败，继续自旋，直到成功为止。

成功之后继续判断，分为以下几种情况（有先后顺序）：

* 如果不是最后一个到达的线程：调用 root.internalAwaitAdvance 进入自旋和等待。

* 如果是最后一个到达的，是分层模式且还没到 root：说明需要进阶了，调用父节点的 arriveAndAwaitAdvance 继续向上传递，因为只有 root 才能进阶。

* 如果是最后一个到达的，但不是分层模式或者已经到达 root 节点：可以进阶了，进阶的步骤和上面的 doArrive 一样。

```java
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
```

#### awaitAdvance

awaitAdvance 有一个参数 phase 用于判断。如果 phase 等于当前 Phaser 的 phase，调用 root.internalAwaitAdvance 自旋和等待。如果不相等，直接返回当前 Phaser 的 phase 值。

和 arriveAndAwaitAdvance 相比，awaitAdvance 像是一次性的。直接传入 phase 判断，而没有 arrive 的过程，也没有更改 state。

```java
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
```

### CountDownLatch 的 Phaser 版本

> 程序由 [Java多线程进阶（二二）—— J.U.C之synchronizer框架：Phaser](https://segmentfault.com/a/1190000015979879) 改造而来。

创建 10 个线程分别执行任务，每个任务需要的时间不同。main 线程等待这 10 个线程的任务完成之后，才继续往下运行。

```java
import java.util.concurrent.Phaser;

public class test {
    public static void main(String[] args) {
        Phaser phaser = new Phaser();
        phaser.bulkRegister(11);
        for (int i = 0; i < 10; i++) {
            new Thread(new Task(phaser), "Thread-" + i).start();
        }
        // main 线程等待
        int i = phaser.arriveAndAwaitAdvance();
        System.out.println(Thread.currentThread().getName() + ": 执行完任务，当前 phase =" + i + "");
    }
}

class Task implements Runnable {
    private final Phaser phaser;

    Task(Phaser phaser) {
        this.phaser = phaser;
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        int prefix = "Thread-".length();
        int num = Integer.valueOf(name.substring(prefix));
        try {
            Thread.sleep(num * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 当前线程不等待
        int i = phaser.arrive();
        System.out.println(Thread.currentThread().getName() + ": 执行完任务，当前 phase =" + i + "");
    }
}

```

测试结果显示，在主线程继续执行之前，phase 一直为 0，说明 arrive 返回的是当前的 phase。当主线程的 arriveAndAwaitAdvance 返回之后，当前的 phase 就结束了，arriveAndAwaitAdvance 函数返回值是 1。

### CyclicBarrier 的 Phaser 版本

> 程序由 [Java多线程进阶（二二）—— J.U.C之synchronizer框架：Phaser](https://segmentfault.com/a/1190000015979879) 改造而来。

重写 onAdvance 函数，指定 Phaser 停止的条件。此处设置当 phase 的值（从 0 开始）达到 ROUND - 1，或者不存在任何注册的 party 时 Phaser 停止。

```java
import java.util.concurrent.Phaser;

public class test {
    public static void main(String[] args) {
        // 一共进行几轮
        final int ROUND = 2;
        // 一共有几个 party
        final int PARTIES = 3;
        Phaser phaser = new Phaser() {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("---------------PHASE[" + phase + "], Parties[" + registeredParties + "] ---------------");
                return phase + 1 >= ROUND  || registeredParties == 0;
            }
        };
        phaser.bulkRegister(PARTIES);
        for (int i = 0; i < PARTIES; i++) {
            new Thread(new Task(phaser), "Thread-" + i).start();
        }
    }
}

class Task implements Runnable {
    private final Phaser phaser;

    Task(Phaser phaser) {
        this.phaser = phaser;
    }

    @Override
    public void run() {
        while (!phaser.isTerminated()) {
            String name = Thread.currentThread().getName();
            int prefix = "Thread-".length();
            int num = Integer.valueOf(name.substring(prefix));
            try {
                Thread.sleep(num * 1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 线程相互等待
            int i = phaser.arriveAndAwaitAdvance();
            System.out.println(Thread.currentThread().getName() + ": 执行完任务");
        }
    }
}
```

### Phaser 分层功能的应用

> 程序由 [Java多线程进阶（二二）—— J.U.C之synchronizer框架：Phaser](https://segmentfault.com/a/1190000015979879) 改造而来。

把 10 个线程任务注册在 3 个 Phaser 上（前两个 Phaser 各有 4 个任务，最后一个有 2 个任务），而这 3 个 Phaser 有一个公共的父 Phaser。

```java
import java.util.concurrent.Phaser;

public class test {
    private static final int TASKS_PER_PHASER = 4;      // 每个Phaser对象对应的工作线程（任务）数

    public static void main(String[] args) {
        // 一共进行几轮
        final int ROUND = 3;
        // 一共有几个 party
        final int PARTIES = 10;
        Phaser phaser = new Phaser() {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("---------------PHASE[" + phase + "],Parties[" + registeredParties + "] ---------------");
                return phase + 1 >= ROUND || registeredParties == 0;
            }
        };

        Task[] taskers = new Task[PARTIES];
        build(taskers, 0, taskers.length, phaser);       // 根据任务数,为每个任务分配 Phaser 对象


        for (int i = 0; i < taskers.length; i++) {          // 执行任务
            Thread thread = new Thread(taskers[i]);
            thread.start();
        }
    }

    private static void build(Task[] taskers, int lo, int hi, Phaser phaser) {
        if (hi - lo > TASKS_PER_PHASER) {
            for (int i = lo; i < hi; i += TASKS_PER_PHASER) {
                int j = Math.min(i + TASKS_PER_PHASER, hi);
                build(taskers, i, j, new Phaser(phaser));
            }
        } else {
            for (int i = lo; i < hi; ++i)
                taskers[i] = new Task(phaser);
        }

    }
}

class Task implements Runnable {
    private final Phaser phaser;

    Task(Phaser phaser) {
        this.phaser = phaser;
        this.phaser.register();
    }

    @Override
    public void run() {
        while (!phaser.isTerminated()) {
            String name = Thread.currentThread().getName();
            int prefix = "Thread-".length();
            int num = Integer.valueOf(name.substring(prefix));
            try {
                Thread.sleep(num * 1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 线程互相等待
            int i = phaser.arriveAndAwaitAdvance();
            System.out.println(Thread.currentThread().getName() + ": 执行完任务");
        }
    }
}
```

### 参考

* [Java多线程进阶（二二）—— J.U.C之synchronizer框架：Phaser](https://segmentfault.com/a/1190000015979879)
* [死磕 java同步系列之Phaser源码解析](https://www.cnblogs.com/tong-yuan/p/11614755.html)
* [《java.util.concurrent 包源码阅读》28 Phaser 第二部分](https://www.cnblogs.com/wanly3643/p/3988575.html)
