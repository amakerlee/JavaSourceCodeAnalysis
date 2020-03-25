## AbstractQueuedSynchronizer

### 完整源码解析

[AbstractOwnableSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractOwnableSynchronizer.java) | [AbstractQueuedSynchronizer](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractQueuedSynchronizer.java)

### 概述

AQS (AbstractQueuedSynchronizer) 是 JUC 框架中构建锁和同步器的基础。Lock、CountDownLatch、Semaphore 等都是基于 AQS 创造出来的。

AQS 的核心思想是：线程请求共享资源，如果资源可用，则线程成为活跃的工作线程，并且将资源锁定，不再允许其他线程使用。如果线程请求的资源已经被锁定了，那么将线程进入队列等待，直到资源被释放。

如果被请求的共享资源空闲，则将当前请求资源的线程设置为有效的工作线程，并且将共享资源设置为锁定状态。如果被请求的共享资源被占用，那么就需要一套线程阻塞等待以及被唤醒时锁分配的机制，这个机制AQS是用CLH队列锁实现的，即将暂时获取不到锁的线程加入到队列中。

一种常用的方式是将 AQS 实例作为某个同步器的属性，从而使用 AQS 的各种方法。AQS 中常用锁的概念有自旋锁（自身循环），重入锁，独占锁，共享锁，读锁，写锁，乐观锁和悲观锁等，这些在后续内容中将会多次出现。

* 独占锁（Exclusive）：同一时刻锁只能被一个线程获取到，例如 ReentrantLock。
* 共享锁（Share）：同一时刻可以被多个线程同时获取的锁，例如 ReadWriteReentrantLock 中的 ReadLock。

AQS 中包含两种队列，如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/AQS.png" width=50% />

同步队列是双向队列，是必须有的队列；Condition 队列（条件队列）是单向队列，可有可无。一个 AQS 中可以有零个或多个 Condition 队列。

### 内部类 Node

AQS 同步控制器基于 Node 节点类，节点类主要包含其代表的线程，节点模式为共享还是独占，节点状态，指向前后节点的指针，若其在 Condition 队列中，还将使用指向 Condition 队列中后一个节点的指针 nextWaiter。节点状态在类中用 waitStatus 表示，对于不同类型的锁可以表示不同的特征，如资源数，锁状态等。

```java
    static final class Node {
        /** 节点在共享模式下等待的标记 */
        static final Node SHARED = new Node();
        /** 节点在独占模式下等待的标记 */
        static final Node EXCLUSIVE = null;

        // 等待状态的值为 0 表示当前节点在 sync 队列中，等待着获取锁
        /** 表示等待状态的值，为 1 表示当前节点已被取消调度，进入这个状态
         * 的节点不会再变化 */
        static final int CANCELLED =  1;
        /** 表示等待状态的值，为 -1 表示当前节点的后继节点线程被阻塞，正在
         * 等待当前节点唤醒。后继节点入队列的时候，会将前一个节点的状态
         * 更新为 SIGNAL */
        static final int SIGNAL    = -1;
        /**  表示等待状态的值，为 -2 表示当前节点等待在 condition 上，当其他
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
         * waitStatus 表示节点状态，有如下几个值（就是上面的那几个）：
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
        * 前驱节点
         */
        volatile Node prev;

        /**
        * 后继节点
         */
        volatile Node next;

        /**
         * 此节点代表的线程。
         */
        volatile Thread thread;

        /**
        * 如果节点在 Condition 队列中，nextWaiter 指向下一个节点
        * Condition 中是单向链表。
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

### 内部类 Condition

Condition 类仍然使用 Node 节点作为基础数据结构，每一个 Condition 队列的构建主要包含两个属性，分别作为队列的头结点和队列的尾节点：

```java
        /** Condition 队列的第一个节点 */
        private transient Node firstWaiter;
        /** Condition 队列的最后一个节点 */
        private transient Node lastWaiter;
```

**添加节点（线程进入 Condition 队列）**

将线程添加到 Condition 队列，分为响应中断，不响应中断和有时间限制的等待方法，分别在函数 await，awaitUninterruptibly，awaitNanos 等方法中实现。

在 await 方法（此方法响应中断）中，使用私有方法 addConditionWaiter 将线程包装成新的 Node 节点添加到 Condition 队列尾部，然后释放线程持有的锁。如果被唤醒了，继续执行后面的流程，即通过 acquireQueued 方法重新获取同步状态。

await 方法完整源码如下所示：

> 这里面使用到了 LockSupport.park() 停止线程，注意此方法不会释放锁资源。

> 如果节点之前在 Sync 队列中（节点必然在 Sync 队列中，而且是 head 节点，因为只有在 lock 范围内，才能调用 await 方法），将会释放资源，经过 fullyRelease-release-tryRelease-unparkSuccessor，唤醒后面的节点。所以需要新创建一个节点加入到 Condition 队列中，原来同步队列中的节点已经无效了。

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
            // LockSupport.park() 不会释放锁资源
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
        
上面的流程调用了很多其他的函数。首先是 addConditionWaiter，在此函数中，把当前线程添加到 Condition 队列，且没有直接使用原来的 Node 节点，而是创建新的节点容纳线程。也就是说这时候同步队列和 Condition 队列中分别有一个节点保存了该线程。
 
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
 ```
  
在 unlinkCancelledWaiters 方法中从 firstWaiter 开始遍历 Condition 队列，清除队列中状态不是 Condition 的节点。

unlinkCancelledWaiters 中代码流程（步骤）可以复用在很多“删除链表某些节点”的场景中。
    
```java
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
 
然后是 acquireQueue 函数，此函数用于在 Condition 队列中的节点被唤醒之后，重新竞争同步状态。

唤醒 Condition 中的节点实际上是把节点加入到同步 (Sync) 队列中，同时设置节点的状态。这些都在 signal 相关函数中实现，此时我们需要知道的是，当节点（线程）被从 Condition 中唤醒后，可以通过 predecessor() 方法获取到前驱节点。
 
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

**shouldParkAfterFailedAcquire**

shouldParkAfterFailedAcquire 函数用来判断获取失败后，当前线程是否需要进入休眠：

```java
    /**
     * 检查和更新未能成功 acquire 的节点状态。如果线程应该阻塞，返回 true。
     * 这是所有 acquire 循环的主要信号控制。需要 pred == node.prev。
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        // ws 存储前驱节点的状态
        if (ws == Node.SIGNAL)
            /**
             * pred 节点已经将状态设置为 SIGNAL，即 node 已告诉前驱节点自己正在
             * 等到唤醒。此时可以安心进入等待状态。
             */
            return true;
        if (ws > 0) {
            /**
             * 前驱节点已经被取消。跳过前驱节点一直往前找，直到找到一个非
             * CANCEL 的节点，将前驱节点设置为此节点。（中途经过的 CANCEL
             * 节点会被垃圾回收。）
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /**
             * 如果进行到这里 waitStatus 应该是 0 或者 PROPAGATE。说明我们
             * 需要一个信号，但是不要立即 park。在 park 前调用者需要重试。
             * 使用 CAS 的方式将 pred 的状态设置成 SIGNAL。（例如如果 pred
             * 刚刚 CANCEL 就不能设置成 SIGNAL。）
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        // 只有当前驱节点是 SIGNAL 才直接返回 true，否则只能返回 false，
        // 并重新尝试。
        return false;
    }
```

**cancelAcquire**

取消当前节点之后意味着当前节点就不存在了（将会被垃圾回收）。大致流程是先判断当前节点是否是头结点或尾节点，如果不是，针对普通的中间节点，把它从链表中删除，交给垃圾收集器回收。

```java
    /**
     * 取消正在进行的 acquire 尝试。
     * 使 node 不再关联任何线程，并将 node 的状态设置为 CANCELLED。
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // 如果节点不存在直接忽略
        if (node == null)
            return;

        // node 不再关联任何线程
        node.thread = null;

        // 跳过已经 cancel 的前驱节点，找到一个有效的前驱节点 pred
        // Skip cancelled predecessors
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        Node predNext = pred.next;

        // 这里可以使用无条件写代替 CAS。在这个原子步骤之后，其他节点可以
        // 跳过。在此之前，我们不受其它线程的干扰。
        node.waitStatus = Node.CANCELLED;

        // 如果当前节点是 tail，删除自身（更新 tail 为 pred，并使 predNext
        // 指向 null）。
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            // 如果 node 不是 tail 也不是 head 的后继节点，将 node 的前驱节点
            // 设置为 SIGNAL，然后将 node 前驱节点的 next 设置为 node 的
            // 后继节点。
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                // 如果 node 是 head 的后继节点，直接唤醒 node 的后继节点
                unparkSuccessor(node);
            }
            // 辅助垃圾回收
            node.next = node; // help GC
        }
    }
```

到这里就完成了 await 方法的所有流程。和 await 方法差不多的还有 awaitUninterruptibly、awaitNanos 等。

不响应中断的 awaitUninterruptibly 方法和 await 方法相比，区别是不即时响应中断。若经历了中断过程，在进入同步队列之后，再自行中断。
     
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
                // 时间到了，退出自旋，进入同步队列。
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

其实用“唤醒节点”来替代“删除节点”更合适。

signal 函数用来唤醒 first 节点，在此函数中实际上调用了 doSignal 函数执行唤醒操作。除此之外还用到了 AQS 类中的transferForSignal 方法和 enq 方法。

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
```

transferForSignal 方法尝试使用 enq 方法把节点添加到同步队列中，然后再把节点的状态设置为 SIGNAL。

在 enq 方法中使用自旋锁循环，直到进入同步队列为止，同样需要使用 CAS 的方式把节点添加到末尾。

```java
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

signalAll 方法和 doSignalAll 方法用来唤醒 Condition 中所有节点，流程与 signal 一样。

```java
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
        
```

除此之外，Condition 类中还提供了 isOwnedBy 方法判断 Condition 是否被指定同步器持有，hasWaiters 判断是否有线程在 Condition 上等待，getWaitQueueLength 获取条件队列上等待的节点个数，getWaitingThreads 获取条件队列上所有等待的线程。

### 类属性

变量 head 和 tail 分别记录同步队列的头结点和尾节点（由此看出同步队列才是必要的，Condition 队列非必要），state 记录同步状态。

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

### 成员函数

所有成员函数中，核心的方法分为 acquire 和 release 两类。acquire 相关函数用于在独占/共享模式下获取锁，release 相关函数用于在独占/共享模式下释放锁。

#### acquire

acquire 相关的实现根据共享/独占，是否相应中断，是否有时间限制等要求，主要有下列函数：

| 方法 | 作用 |
| - | - |
| acquire | 独占模式获取锁 |
| acquireShared | 共享模式获取锁 |
| acquireInterruptibly | 独占中断模式获取锁 |
| acquireSharedInterruptibly | 共享中断模式获取锁 |
| tryAcquireNanos | 限时独占模式获取锁 |
| tryAcquireSharedNanos | 限时共享模式获取锁 |
| doAcquireShared | 执行共享模式下获取锁 |
| doAcquireInterruptibly | 执行独占中断模式下获取锁 |
| doAcquireSharedInterruptibly | 执行共享中断模式下释放锁 |
| doAcquireNanos | 执行独占限时模式下获取锁 |
| doAcquireSharedNanos | 执行共享限时模式下获取锁 |

看起来好像很多，但其实大多数函数的流程是差不多的。

需要注意的是，无论是公平还是非公平模式，如果 tryAcquire 尝试失败，都会通过 addWaiter 把当前线程包装成节点添加到同步队列里，然后等待被唤醒。一般实现公平和非公平模式的方法是，看同步队列中是否还有节点在等待。

**acquire**

在 acquire 方法中，首先调用 tryAcquire 执行一次 acquire 操作，尝试获取资源，如果成功则操作结束，如果失败，调用 acquireQueued 方法将当前线程包装成节点加入到同步队列尾部。

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

**acquireShared** 

此方法中首先调用 tryAcquireShared 尝试获取共享锁，如果获取成功则结束所有操作，获取失败进入 **doAcquireShared** 方法中自旋，如果该节点是 head 节点的下一个节点，无限循环直到获取成功，否则进入 waiting 状态直到被唤醒（此时仍然在队列中，只是不再自旋获取锁，由前一个节点唤醒）。与独占模式的区别在于调用了 setHeadAndPropagate 将同步状态往后传递，即在有剩余资源的情况下，让其他线程也能继续获取资源。

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
                // waiting 状态，等待被 unpark 或 interrupt。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    // 安心进入休眠状态，只是设置中断标记为 true，以便后续完成中断
                    // 没有马上抛出中断
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

在这里调用了addWaiter 方法把当前线程封装成一个节点添加到同步队列里：
 
 ```java
    /**
     * 为当前线程和给定模式创建节点并添加到等待队列队列尾部，并返回当前线程
     * 所在节点。
     *
     * 如果 tail 不为 null，即等待队列已经存在，则以 CAS 的方式将当前线程节点
     * 加入到等待队列的末尾。否则，通过 enq 方法初始化一个等待队列，并返回当前节点。
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        // 创造新节点
        Node node = new Node(Thread.currentThread(), mode);
        // 尝试快速入队，失败时调用 enq 函数的方式入队
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            // 设置 tail，设置成功返回，设置失败进入 enq
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }
```
 
在这里还调用了 setHeadAndPropagate 设置新的 head，并继续唤醒后面的线程（如果后面的节点是共享类型的话）：

```java
    /**
     * 指定队列的 head，并检查后继节点是否在共享模式下等待，如果是且
     * （propagate > 0 或等待状态为 PROPAGATE），则传播。
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        // 记录原先的 head，并将 node 节点设置为新的 head。
        Node h = head;
        setHead(node);
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }
```

可以把 doReleaseShared 理解为唤醒后面的节点，而不仅仅是释放资源（释放资源其实也是唤醒后面的节点）。

**acquireInterruptibly** 

此方法以响应中断的方式获取锁，随时抛出中断异常。首先检查是否被设置了中断状态，然后调用 tryAcquire 尝试获取锁，如果获取失败，调用 **doAcquireInterruptibly** 进入自旋操作，直到获取到锁或者进入休眠状态或者被中断。

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

**acquireSharedInterruptibly** 

此方法以响应中断的方式获取共享锁。同样先检查是否有中断标记，然后尝试 tryAcquireShared 获取锁，否则进入 **doAcquireSharedInterruptibly** 自旋。

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

**tryAcquireNanos** 

此方法用于在限定时间内获取锁，超出时间则直接失败。

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

#### release
 
**release** 函数用于在独占模式下释放锁，调用 tryRelease 方法释放锁，成功则调用 unparkSuccessor 方法唤醒下一个线程

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
     * unparkSuccessor。）
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
         * unparkSuccessor 的其他用法不同，我们需要知道是否 CAS 的重置操作
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

**unparkSuccessor**

上面的 release 操作中调用了 unparkSuccessor 释放指定节点的后继节点：

```java
    /**
     * 唤醒指定节点的后继节点，如果其存在的话。
     * unpark - 唤醒
     * 成功获取到资源之后，调用这个方法唤醒 head 的下一个节点。由于当前
     * 节点已经释放掉资源，下一个等待的线程可以被唤醒继续获取资源。
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {

        int ws = node.waitStatus;
        // 如果当前节点没有被取消，更新 waitStatus 为 0。
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /**
         * 待唤醒的线程保存在后继节点中，通常是下一个节点。但是如果已经被
         * 取消或者显然为 null，则从 tail 向前遍历，以找到实际的未取消后继节点。
         */
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
    }
```
 
 * cancelAcquire：取消获取锁的尝试 

### 设计模式

AQS 中使用了模板方法设计模式，在自定义同步器的时候需要重写以下模板方法：

 * tryAcquire：尝试以独占模式获取锁
 * tryRelease：尝试以独占模式释放锁
 * tryAcquireShared：尝试以共享模式获取锁
 * tryReleaseShared：尝试以共享模式释放锁

以上方法在 AQS 类中默认抛出 UnsupportedOperationException 异常。

### 小结

* 前面说到 AbstractQueuedSynchronizer 依赖两种队列，显而易见其中最核心的就是同步队列。
* 每一个结点都是由前一个结点唤醒。
* 当结点发现前驱结点是 head 并且尝试获取成功，则会轮到该线程运行。 
* Condition 队列中的结点向同步队列中转移是通过 signal 操作完成的。 
* 当结点的状态为 SIGNAL 时，表示后面的结点需要运行。

*[JUC锁: 锁核心类AQS详解](https://www.pdai.tech/md/java/thread/java-thread-x-lock-AbstractQueuedSynchronizer.html) 中有 AQS 同步队列和 Condition 队列使用示例的详细图解，可供参考。*

### 参考

* [JUC源码分析—AQS](https://www.jianshu.com/p/a8d27ba5db49)
* [JUC锁: 锁核心类AQS详解](https://www.pdai.tech/md/java/thread/java-thread-x-lock-AbstractQueuedSynchronizer.html)
