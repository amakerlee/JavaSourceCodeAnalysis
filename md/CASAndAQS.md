### CAS

***
> CAS 原理

CAS （compare and swap）算法包含三个参数，分别是当前内存值 V，旧的预期值 A，要写入的新值。当且仅当 V 的值等于 A 时，CAS 才会通过原子方式用新的值 B 来更新该内存位置的值，否则不进行任何操作。

CAS 是乐观锁的一种，如果在执行 CAS 的时候发现变量的值已经被修改过了，那么不能简单地继续进行后续更新操作，因为在此之前读到的数据已经是脏数据了。当多个线程使用 CAS 的方法同时更新一个变量时，只有其中一个线程能更新成功，其他线程都将失败。

**例如：**

线程 A 和线程 B 同时对变量 m = 1 执行加 1 的操作，正常情况下希望得到的最终结果是 m = 3（加了两次）。如果不使用 CAS，可能发生的一种情况是：第一阶段两个线程同时取到 m 的值，ma = 1 且 mb = 1（Java 内存模型中，各线程工作内存中分别持有变量副本）。第二阶段两个线程分别对 ma 和 mb 进行更新，此时 ma = 2 且 mb = 2。第三阶段，线程 a 首先将 ma = 2 写入主内存，m = 2，然后线程 b 将 mb = 2 写入主内存，m 的值还是 2。与预期结果不符。

使用 CAS 方式，在线程 b 写入时，首先检查 V 和 A 是否相等，此时 V = 2，而预期的 A = 1，V 不等于 A，不允许写入。

> CAS 的 ABA 问题

如果有两个线程 x 和 y ，如果 x 初次从内存中读取变量值为 A；线程 y 对它进行了一些操作使其变成 B，然后再改回 A，那么线程 x 进行 CAS 的时候就会误认为这个值没有被修改过。尽管 CAS 操作会成功执行，但是不代表它是没有问题的，如果有一个单向链表 A B 组成的栈，栈顶为 A，线程 T1 准备执行 CAS 操作 head.compareAndSet(A,B)，在执行之前线程 T2 介入，T2 将A、B出栈，然后又把C、A放入栈，T2执行完毕；切回线程 T1，T1 发现栈顶元素依然为 A，也会成功执行 CAS 将栈顶元素修改为 B，但因为 B.next 为 null，所以栈结构就会丢弃 C 元素。（引用自[JUC源码分析—CAS和Unsafe](https://www.jianshu.com/p/a897c4b8929f)）

对于 CAS 的解决方案是版本号管理。为每一个变量保存一个版本号，当这个值由 A 变为 B，然后又变成 A 时，版本号会不同。

### AQS

***
> 完整源码解析

[AbstractOwnableSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractOwnableSynchronizer.java) | [AbstractQueuedSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractQueuedSynchronizer.java)

***
> 概述

AQS 是 JUC 框架中构建锁和同步器的基础。一种常用的方式是将 AQS 实例作为某个同步器的属性，从而使用 AQS 的各种方法。AQS 中常用锁的概念有自旋锁（自身循环），重入锁，独占锁，共享锁，读锁，写锁，乐观锁和悲观锁等，这些在后续内容中将会多次出现。

* 独占锁（Exclusive）：同一时刻锁只能被一个线程获取到，例如 ReentrantLock。
* 共享锁（Share）：同一时刻可以被多个线程同时获取的锁，例如 ReadWriteReentrantLock 中的 ReadLock。

AQS 中包含两种队列，如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/AQS.png" width=50% />

一种是同步队列，一种是 Condition 队列（条件队列）。一个 AQS 中可以有多个 Condition 队列。一个节点只能同时存在于同步队列或 Condition 队列中。

***
> 内部类 Node

AQS 同步控制器基于 Node 节点类，节点类主要包含其代表的线程，节点模式为共享还是独占，节点状态，指向前后节点的指针，若其在 Condition 队列中，还将使用指向 Condition 队列中后一个节点的指针 nextWaiter。节点状态在类中用 waitStatus 表示，对于不同类型的锁可以表示不同的特征，如资源数，锁状态等。

```java
    static final class Node {
        /** 节点在共享模式下等待的标记 */
        static final Node SHARED = new Node();
        /** 节点在独占模式下等待的标记 */
        static final Node EXCLUSIVE = null;

        /** 表示等待状态的值，为 1 表示当前节点已被取消调度，进入这个状态
         * 的节点不会再变化 */
        static final int CANCELLED =  1;
        /** 表示等待状态的值，为 -1 表示当前节点的后继节点线程被阻塞，正在
         * 等待当前节点唤醒。后继节点入队列的时候，会将前一个节点的状态
         * 更新为 SIGNAL */
        static final int SIGNAL    = -1;
        /**  表示等待状态的值，为 -1 表示当前节点等待在 condition 上，当其他
         * 线程调用了 Condition 的 signal 方法后，condition 状态的节点将从
         * 等待队列转移到同步队列中，等到获取同步锁。
         * CONDITION 在同步队列里不会用到*/
        static final int CONDITION = -2;
        /**
         * 表示等待状态的值，为 -3 ，在共享锁里使用，表示下一个 acquireShared
         * 操作应该被无条件传播。保证后续节点可以获取共享资源。
         * 共享模式下，前一个节点不仅会唤醒其后继节点，同时也可能会唤醒
         * 后继的后继节点。
         */
        static final int PROPAGATE = -3;

        /**
         * 状态字段，只有如下几个值：
         * SIGNAL: 当前节点的后继节点被（或者即将被）阻塞（通过 park），
         * 因此当前节点在释放或者取消时必须接触对后继节点的阻塞。为了避免
         * 竞争，acquire 方法必须首先表明它们需要一个信号，然后然后尝试原子
         * 获取，如果失败则阻塞。
         * CANCELLED：此节点由于超时或中断而取消。节点永远不会离开此状态。
         * 特别是，具有已取消节点的线程不会再阻塞。
         * CONDITION：此节点此时位于条件队列上。在转移之前它不会被用作
         * 同步队列节点，此时状态被设为 0。（这里此值的使用与此字段的其他
         * 用法无关，但是简化了机制）。
         * PROPAGATE：releaseShared 应该被传播到其它节点。这是在 doReleaseShared
         * 中设置的（仅针对头结点），以确保传播能够继续，即使其它操作已经介入了。
         * 0：以上情况都不是
         *
         * 此值以数字形式组织以简化使用。非负值意味着节点不需要发出信号。
         * 因此，大多数代码不需要检查特定的值，只需要检查符号。
         *
         * 对于正常的同步节点此字段初始化为 0，对于田间节点初始化为
         * CONDITION。可以使用 CAS （或者可能的话，使用无条件的 volatile 写）
         * 修改它。
         */
        volatile int waitStatus;

        /**
         * 与当前节点锁依赖的用于检查等待状态的前辈节点建立的连接。在进入
         * 队列时分配，在退出队列时设置为 null（便于垃圾回收）。此外，在查找
         * 一个未取消的前驱节点时短路，这个前驱节点总是存在，因为头结点
         * 绝不会被取消：一个节点只有在成功 acquire 之后才成为头结点。被取消
         * 的线程 acquire 绝不会成功，而且线程只取消自己，不会取消其他节点。
         */
        volatile Node prev;

        /**
         * 与当前节点 unpack 之后的后续节点建立的连接。在入队时分配，在绕过
         * 已取消的前一个节点时调整，退出队列时设置为 null（方便 GC）。入队
         * 操作直到 attachment 之后才会分配其后继节点，所以看到此字段为 null
         * 并不一定意味着节点在队列尾部。但是，如果 next 字段看起来为 null，
         * 我们可以从 tail 往前以进行双重检查。被取消节点的 next 字段设置成指向
         * 其自身而不是 null，以使 isOnSyncQueue 的工作更简单。
         */
        volatile Node next;

        /**
         * 此节点代表的线程。
         */
        volatile Thread thread;

        /**
         * 连接到在 condition 等待的下一个节点。由于条件队列只在独占模式下被
         * 访问，我们只需要一个简单的链式队列在保存在 condition 中等待的节点。
         * 然后他们被转移到队列中重新执行 acquire。由于 condition 只能是排它的，
         * 我们可以通过使用一个字段，保存特殊值来表示共享模式。
         */
        Node nextWaiter;

        /**
         * 如果节点在共享模式中处于等待状态，返回 true。
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前一个节点，如果为 null 抛出 NullPointerException 异常。
         * 当前一个节点不为 null 时才能使用。非空检查可以省略，此处是为了辅助
         * 虚拟机。
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        // 用来创建初始节点或者共享标记
        Node() {    // Used to establish initial head or SHARED marker
        }

        // addWaiter 使用
        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        //Condition 使用
        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }
```

***
> 内部类 Condition

Condition 类仍然使用 Node 节点作为基础数据结构，每一个 Condition 队列的构建主要包含两个属性，分别作为队列的头结点和队列的尾节点：

```java
        /** Condition 队列的第一个节点 */
        private transient Node firstWaiter;
        /** Condition 队列的最后一个节点 */
        private transient Node lastWaiter;
```

**添加节点（线程进入 Condition 队列）**

将线程添加到 Condition 队列，分为响应中断，不响应中断和有时间限制的等待方法，分别在函数 await，awaitUninterruptibly，awaitNanos 等方法中实现。

**响应中断的 await** 方法中使用私有方法 addConditionWaiter 将线程包装成新的 Node 节点添加到 Condition 队列尾部，然后释放线程持有的锁。如果被唤醒，通过 acquireQueued 方法重新获取同步状态。

```java
        /**
         * 实现可中断 condition 的 wait 方法。
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            // 如果当前线程被中断，抛出 InterruptedException 异常。
            if (Thread.interrupted())
                throw new InterruptedException();
            // 创建和此线程关联的节点，将其加入到 condition 队列中
            Node node = addConditionWaiter();
            // 释放当前线程并返回释放前的状态值
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            // 如果节点不在 sync 中一直循环（阻塞）
            // 同时检查是否发生中断，如果发生则中止循环
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            // 被唤醒后，重新加入到同步队列队尾竞争获取锁，如果竞争不到则会沉睡，等待唤醒重新开始竞争。
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            // 如果在之前的 while 循环中有中断发生，抛出 InterruptedException 异常
            // 延迟响应中断
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }
        
 ```
        
 **addConditionWaiter** 函数中，当前线程从同步队列进入到 Condition 队列，不再是原来的 Node 节点，创建新的节点用于容纳当前线程。**unlinkCancelledWaiters** 方法中遍历 Condition 队列，清除队列中状态不是 Condition 的节点。
 
 ```java
         /**
          * 在等待队列中添加一个新的 waiter 节点。
          * @return its new wait node
          */
         private Node addConditionWaiter() {
             Node t = lastWaiter;
 
             if (t != null && t.waitStatus != Node.CONDITION) {
                 // 遍历链表，清除状态不是 CONDITION 的节点
                 unlinkCancelledWaiters();
                 t = lastWaiter;
             }
             // 创建包含当前线程的新的节点
             Node node = new Node(Thread.currentThread(), Node.CONDITION);
             if (t == null)
                 firstWaiter = node;
             else
                 t.nextWaiter = node;
             lastWaiter = node;
             return node;
         }
         
          /**
           * 从 condition 队列中删除已取消（状态不是 CONDITION 即为已取消）
           * 的等待节点。
           * 只有在持有锁的时候才调用。在 condition 队列中等待时如果发生节点
           * 取消，且看到 lastWaiter 被取消然后插入新节点时调用。
           * （addConditionWaiter 函数中调用）。需要使用此方法在没有 signal 的
           * 时候避免保留垃圾。因此即使它需要完整的遍历，也只有在没有信号的
           * 情况下发生超时或者取消时，它才会起作用。它将会遍历所有节点，而不是
           * 停在一个特定的目标上来取消垃圾节点的连接，且不需要在取消频繁发生时
           * 进行多次重复遍历。
           */
          private void unlinkCancelledWaiters() {
              Node t = firstWaiter;
              Node trail = null;
              // condition 队列不为空，从头结点开始遍历
              while (t != null) {
                  Node next = t.nextWaiter;
                  // 从 condition 中删除节点 t
                  if (t.waitStatus != Node.CONDITION) {
                      t.nextWaiter = null;
                      if (trail == null)
                          firstWaiter = next;
                      else
                          trail.nextWaiter = next;
                      if (next == null)
                          lastWaiter = trail;
                  }
                  else
                      trail = t;
                  // t 移向后一个节点，然后继续循环
                  t = next;
              }
          }
 ```
 
 除此之外，**acquireQueue** 函数用于在 Condition 队列中的节点被唤醒之后，重新竞争同步状态。
 
 ```java
    /**
     * 等待队列中的线程自旋时，以独占且不可中断的方式 acquire。
     * 用于 condition 等待方式中的 acquire。
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        // 标记是否成功拿到资源
        boolean failed = true;
        try {
            // 标记等待过程中是否被中断过
            boolean interrupted = false;
            // 自旋
            for (;;) {
                // 获取 node 的前一个节点
                final Node p = node.predecessor();
                // 如果前一个节点为 head，尝试获取资源
                if (p == head && tryAcquire(arg)) {
                    // 获取成功，将 node 设置为 head，并将 p 的 next 设置为 null，
                    // 以便于回收 p 节点。
                    setHead(node);
                    p.next = null; // help GC
                    // 成功获取资源
                    failed = false;
                    return interrupted;
                }
                // 获取资源失败
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            // 如果没有成功获取资源
            if (failed)
                cancelAcquire(node);
        }
    }
```

**不响应中断的 awaitUninterruptibly 方法**和 await 方法相比，区别是不即时响应中断。若经历了中断过程，在进入同步队列之后，再自行中断。
        
```java       
        /**
         * 实现不中断的 condition 队列上等待。
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                // 如果线程中断，用标志位 interrupted 记录
                if (Thread.interrupted())
                    interrupted = true;
            }
            // 等到进入 sync 队列，如果成功 acquire 或者标志位显示经历了中断
            // 过程，则自行中断。
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }
```

**有时间限制的 awaitNanos 方法**在 await 方法的基础上，加上等待的时间限制。同样采用自旋的方式检查是否已经在同步队列中，如果指定等待时间片被耗尽，则退出自旋，开始竞争同步状态。

```java
        /**
         * 实现 condition 里有时间限制的 wait 方法。
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }
```


**删除（移除）节点**

**signal 函数**唤醒 first 节点，此函数中调用 **doSignal 方法**执行唤醒操作。将会用到 AQS 类中的 **transferForSignal 方法**和 **enq 方法**。enq 方法中使用自旋锁循环等待，直到进入同步队列为止，同时应用 CAS 的方式添加节点，防止节点被覆盖。

**signalAll 方法**和 **doSignalAll 方法**唤醒 Condition 中所有节点，模式与 signal 方法基本一致。

```java
        /**
         * 将最长等待的线程，（第一个节点）如果存在的话，从 condition 等待
         * 队列移动到拥有锁的等待队列。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * 将 condition 中等待的所有线程移动到拥有锁的等待队列。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }
        
        /**
         * 删除和转变节点，直到命中一个并未取消或者为 null 的节点。
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                // 设置新的头结点，并将节点从 condition 等待队列中移除
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
                // 将该节点加入到 sync 队列或者 condition 等待队列为空时跳出循环
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        /**
         * 删除（删除指的是移出 condition 队列而不是垃圾回收）并转变所有节点。
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                // 将 first 节点移出 condition 队列
                first.nextWaiter = null;
                // 唤醒
                transferForSignal(first);
                first = next;
            } while (first != null);
        }
        
    /**
     * 把 condition 队列中的节点移动到 sync 队列中。
     * 如果成功返回 true。
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(Node node) {
        /**
         * 如果不能改变状态，说明节点已经被取消了。
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /**
         * 移动到 sync 队列中，并设置前驱节点的状态来表明线程正在等待。
         * 如果取消或者尝试设置状态失败，则唤醒并重新同步（在这种情况下，
         * 等待状态可能暂时错误，但不会造成任何伤害）。
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        // 如果该节点的状态为 cancel 或者修改状态失败，则直接唤醒
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }
    
    /**
     * 把节点添加到队列中，必要时初始化。
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            // 如果尾节点为 null，需要初始化并设置新的节点为头结点和尾节点。
            if (t == null) { // Must initialize
                // 以 CAS 方式添加，防止多线程添加产生节点覆盖
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;
                // 以 CAS 方式添加，防止多线程添加产生节点覆盖
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }
```

除此之外，Condition 类中还提供了**isOwnedBy** 方法判断 Condition 是否被指定同步器持有，**hasWaiters** 判断是否有线程在 Condition 上等待，**getWaitQueueLength** 获取条件队列上等待的节点个数，**getWaitingThreads** 获取条件队列上所有等到的线程

***
> 类属性

变量 head 和 tail 分别记录同步队列的头结点和尾节点，state 记录同步状态。

```java
    /**
     * 同步队列的头结点，延迟初始化。除了初始化之外，只能通过 setHead
     * 修改。注意：如果 head 存在的话，其状态必须保证不是 CANCELLED。
     */
    private transient volatile Node head;

    /**
     * 同步队列的尾节点，延迟初始化。只能通过入队方法添加新的节点。
     */
    private transient volatile Node tail;

    /**
     * 同步状态。
     */
    private volatile int state;
```

***
> 成员函数

所有成员函数中，核心的方法分为 acquire 和 release 两类。acquire 相关函数用于在独占/共享模式下获取锁，release 相关函数用于在独占/共享模式下释放锁。

> acquire

acquire 相关的实现根据共享/独占，是否相应中断，是否有时间限制等要求，主要有下列函数：

* acquire：独占模式获取锁
* acquireShared：共享模式获取锁
* acquireInterruptibly：独占中断模式获取锁
* acquireSharedInterruptibly：共享中断模式获取锁
* tryAcquireNanos：限时独占模式获取锁
* tryAcquireSharedNanos：限时共享模式获取锁
* doAcquireShared：执行共享模式下获取锁
* doAcquireInterruptibly：执行独占中断模式下获取锁
* doAcquireSharedInterruptibly：执行共享中断模式下释放锁
* doAcquireNanos：执行独占限时模式下获取锁
* doAcquireSharedNanos：执行共享限时模式下获取锁

**acquire** 方法中，首先调用 tryAcquire 执行一次 acquire 操作，如果成功则操作结束，如果失败，则调用 acquireQueued 方法将当前线程包装成节点加入到同步队列尾部，加入队列尾部后仍然在尝试获取锁。

```java
    /**
     * 以独占模式 acquire，忽略中断。通过调用一次或多次 tryAcquire 来实现，
     * 成功后返回。否则线程将入队列，可能会重复阻塞或者取消阻塞，直到
     * tryAcquire 成功。此方法可以用来实现 Lock.lock 方法。
     *
     * 此方法流程如下：
     * tryAcquire 尝试获取资源，如果成功直接返回；
     * addWaiter 将该线程加入到等待队列尾部，并且标记为独占模式；
     * acquireQueued 使线程在等待队列中获取资源，直到取到为止。在整个等待
     * 过程中被中断过返回 true，否则返回 false；
     * 如果线程在等待过程中被中断，它不会响应。直到获取到资源后才进行自我
     * 中断（selfInterrupt），将中断补上。
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
```

**acquireShared** 方法中首先调用 tryAcquireShared 尝试获取共享锁，如果获取成功则结束所有操作，获取失败进入 **doAcquireShared** 方法中自旋，如果该节点是 head 节点的下一个节点，无限循环直到获取成功，否则进入 waiting 状态直到被唤醒（此时仍然在队列中，只是不再自旋获取锁，由前一个节点唤醒）。与独占模式的区别在于调用了 setHeadAndPropagate 将同步状态往后传递，在有剩余资源的情况下，让其他线程也能获取资源。

```java
    /**
     * 忽略中断，以共享模式 acquire。首先调用至少一次 tryAcquireShared，
     * 成功后返回。否则线程将排队，可能会重复阻塞和取消阻塞，不断调用
     * tryAcquiredShared 直到成功。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
    
    /**
     * 共享不中断模式下执行 acquire。
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        // 将节点加入到队列尾部（节点为 SHARED 类型）
        final Node node = addWaiter(Node.SHARED);
        // 是否成功的标志
        boolean failed = true;
        try {
            // 等待过程中是否被中断过的标志
            boolean interrupted = false;
            // 自旋进入等待过程
            for (;;) {
                // p 保存其前驱节点
                final Node p = node.predecessor();
                // 如果节点某个时刻成为了 head 的后继节点，node 被 head 唤醒
                if (p == head) {
                    // 尝试获取资源
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        // 获取资源成功，将 head 指向自己，还有剩余资源可以唤醒
                        // 之后的线程
                        setHeadAndPropagate(node, r);
                        // 辅助回收承载该线程的节点 p
                        p.next = null; // help GC
                        // 如果在等待过程中被中断过，那么此时将中断补上
                        if (interrupted)
                            selfInterrupt();
                        // 改变 failed 标志位，表示获取成功，然后返回（退出此函数）
                        failed = false;
                        return;
                    }
                }
                // p 不是 head 的后继节点，则不能获取资源，寻找安全点，进入
                // waiting 状态，等待被 unpack 或 interrupt。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    // 安心进入休眠状态，设置中断标记为 true，以便后续完成中断
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

**acquireInterruptibly** 方法以响应中断的方式获取锁，随时抛出中断异常。首先检查是否被设置了中断状态，然后调用 tryAcquire 尝试获取锁，如果获取失败，调用 **doAcquireInterruptibly** 进入自旋操作，直到获取到锁或者进入休眠状态或者被中断。

```java
    /**
     * 以独占模式 acquire，如果中断则中止。
     * 首先检查中断状态，然后至少调用一次 tryAcquire，成功直接返回。否则线程
     * 进入队列等待，可能重复阻塞或者取消阻塞，调用 tryAcquire 直到成功或线程
     * 被中断。此方法可用来实现 lockInterruptibly。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        // 中断则抛出异常
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }
    
    /**
     * 以独占中断模式 acquire。
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
            throws InterruptedException {
        // 将当前线程以独占模式创建节点加入等待队列尾部
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                // 不断自旋直到将前驱节点的状态设置为 SIGNAL，然后阻塞当前线程
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    // 如果 parkAndCheckInterrupt 返回 true 即 Thread.interrupted
                    // 返回 true 即线程被中断，则抛出中断异常。
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

**acquireSharedInterruptibly** 方法以响应中断的方式获取共享锁。同样先检查是否有中断标记，然后尝试 tryAcquireShared 获取锁，否则进入 **doAcquireSharedInterruptibly** 自旋。

```java
    /**
     * 以共享模式 acquire，如果中断将中止。首先检查中断状态，然后调用至少一次
     * tryAcquireShared，成功立即返回。否则，想成进入等待队列，可能会重复阻塞
     * 和取消阻塞，调用 tryAcquireShared 直到成功或者线程中断。
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }
    
    /**
     * 共享中断模式下 acquire。
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

**tryAcquireNanos** 方法用于在限定时间内获取锁，超出时间则直接失败。

```java
    /**
     * 尝试以独占模式 acquire，如果中断将中止，如果超出给定时间将失败。首先检查
     * 中断状态，然后至少调用一次 tryAcquire，成功立即返回。否则线程进入等待队列，
     * 可能会重复阻塞或取消阻塞，调用 tryAcquire 直到成功或线程中断或超时。
     * 此方法可用于实现 tryLock(long, TimeUnit)。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }
```

 
 > release
 
**release** 函数用于在独占模式下释放锁，调用 tryRelease 方法释放锁，成功则调用 unpackSuccessor 方法唤醒下一个线程

**releaseShared** 函数用于在共享模式下释放锁，首先调用 tryRelease 方法释放锁，如果成功则调用 doReleaseShared 向后遍历，从而释放所有节点状态为 SIGNAL 的后继节点。

```java
    /**
     * 独占模式下 release。如果 tryRelease 返回 true，则通过解除一个或多个
     * 线程的阻塞来实现。此方法可以用来实现 Lock 的 unlock 方法。
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        // 通过 tryRelease 的返回值来判断是否已经完成释放资源
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                // 唤醒等待队列里的下一个线程
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
    
    /**
     * 共享模式下的 release 操作。如果 tryReleaseShared 返回 true，唤醒一个
     * 或多个线程。
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        // 尝试释放资源
        if (tryReleaseShared(arg)) {
            // 唤醒后继节点
            doReleaseShared();
            return true;
        }
        return false;
    }
    
    /**
     * 共享模式下的释放（资源）操作 -- 信号发送给后继者并确保资源传播。
     * （注意：对于独占模式，如果释放之前需要信号，直接调用 head 的
     * unpackSuccessor。）
     *
     * 在 tryReleaseShared 成功释放资源后，调用此方法唤醒后继线程并保证
     * 后继节点的 release 传播（通过设置 head 的 waitStatus 为 PROPAGATE。
     */
    private void doReleaseShared() {
        /**
         * 确保 release 传播，即使有其它的正在 acquire 或者 release。这是试图
         * 调用 head 唤醒后继者的正常方式，如果需要唤醒的话。但如果没有，
         * 则将状态设置为 PROPAGATE，以确保 release 之后传播继续进行。
         * 此外，我们必须在无限循环下进行，防止新节点插入到里面。另外，与
         * unpackSuccessor 的其他用法不同，我们需要知道是否 CAS 的重置操作
         * 失败，并重新检查。
         */
        // 自旋（无限循环）确保释放后唤醒后继节点
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    // 唤醒后继节点
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }
```
 
 > 辅助函数
 
 * unparkSuccessor：唤醒指定节点的后继节点
 * cancelAcquire：取消获取锁的尝试
 * shouldParkAfterFailedAcquire：检查和更新未能成功 acquire 的节点状态
 * acquireQueued：等待队列中的线程自旋时，以独占且不可中断的方式获取
 * setHeadAndPropagate：将同步状态传递，让之后的线程也能获取资源
 
 除了上述方法之外，用户需要在自己的同步工具中实现以下方法：
 
 * tryAcquire：尝试以独占模式获取锁
 * tryRelease：尝试以独占模式释放锁
 * tryAcquireShared：尝试以共享模式获取锁
 * tryReleaseShared：尝试以共享模式释放锁

***
> 参考

[JUC源码分析—CAS和Unsafe](https://www.jianshu.com/p/a897c4b8929f)
[JUC源码分析—AQS](https://www.jianshu.com/p/a8d27ba5db49)