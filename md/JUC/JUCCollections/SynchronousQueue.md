# SynchronousQueue

“没有容量”的阻塞队列，每个插入操作都要等待其他线程的删除操作，每个删除操作都要等待插入操作，实际相当于将数据从一个线程传递到另一个线程。包括公平和非公平两种模式。

“没有容量”并不是说队列中不保存任何元素/节点，实际上队列中依然有节点，节点内可能会有元素，也可能没有。其实此类的基本思想和 LinkedTransferQueue 类似，队列中只存在同一种类型的节点，要么是数据节点，要么是非数据节点（等待数据的节点），详见 [LinkedTransferQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/LinkedTransferQueue.md)。

公平模式通过队列（FIFO）实现，非公平模式通过栈（LIFO）实现。

此阻塞队列使用有自旋次数限制的 CAS 保障线程安全。

## 完整源码解析

[SynchronousQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/SynchronousQueue.java)

## 非公平模式

非公平模式的规则是后进先出（LIFO），通过链式栈实现。

### 内部类

属性 mode 表示此节点的类型（数据节点或非数据节点），其他详见 [LinkedTransferQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/JUC/JUCCollections/LinkedTransferQueue.md)。

```java
        /** TransferStacks 的节点类 */
        static final class SNode {
            volatile SNode next;        // next node in stack
            // 此节点匹配的节点
            volatile SNode match;       // the node matched to this
            // 此节点上等待的线程
            volatile Thread waiter;     // to control park/unpark
            Object item;                // data; or null for REQUESTs
            // 节点类型
            int mode;
            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.

            SNode(Object item) {
                this.item = item;
            }

            boolean casNext(SNode cmp, SNode val) {
                return cmp == next &&
                        UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * 尝试将节点 s 和此节点匹配。
             *
             * @param s the node to match
             * @return true if successfully matched to s
             */
            boolean tryMatch(SNode s) {
                // 此节点 m 还没有匹配者，将 s 作为其匹配着
                if (match == null &&
                        UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {
                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;
                        // 唤醒 m 中的线程，匹配完毕
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                // 返回 boolean，判断匹配到的线程是不是 s
                return match == s;
            }

            /**
             * 将 match 指向自己，表示取消了
             */
            void tryCancel() {
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            // 是否取消了
            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long matchOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }
```

### 类属性

重要属性就只有 head，与之对应的 CAS 操作为 casHead。

```java
        /** 栈的头节点（top）节点 */
        volatile SNode head;

        // head 是栈中最重要的属性，表示头结点，只有用 CAS 方式才能更新
        // 因为每一次操作在有多线程并发的时候，可能会处理同一个/不同的节点，
        // 随时可能发生不同步的情况，此时需要重新定位到栈顶（或栈底），
        // 而 head 就是每一次失败操作重新开始的地方（正是由于 head 的修改
        // 是原子操作，所以 head 可以作为并发条件下定位此队列的临界变量。）
        boolean casHead(SNode h, SNode nh) {
            return h == head &&
                    UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }
```

### 成员函数

显而易见，put 和 take 的核心实现还是 transfer 函数，通过自旋 + CAS 保障线程安全。

自旋对以下三种情况进行讨论：

1. 如果栈顶没有元素，或者栈顶元素和当前操作是同一模式（无法匹配），根据超时情况确定是否把新创造的节点压入栈顶。等到匹配成功，就删除节点。

以下两种情况基于栈顶有元素，且可以匹配：

2. 栈顶元素还没有匹配，把节点压入栈中，尝试与 head 匹配，匹配成功后再将两个节点弹出。

3. 栈顶元素正在匹配，帮助此节点进行匹配，然后从头开始循环。

```java
        /**
         * 栈内部 put 或者 take 操作的基础。
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            SNode s = null; // constructed/reused as needed
            // 如果传入的 e 为 null，说明是请求数据（消费者），e 不为 null，是
            // 存入数据（生产者）
            int mode = (e == null) ? REQUEST : DATA;

            // 自旋 + CAS
            // 注意，如果没有执行到 return 的地方，会无限循环
            for (;;) {
                SNode h = head;
                // 栈顶没有元素，或者栈顶元素跟当前操作是同一个模式，
                // 都是生产者或者都是消费者。
                if (h == null || h.mode == mode) {  // empty or same-mode
                    // 等待时间已经到期
                    if (timed && nanos <= 0) {      // can't wait
                        // h 不为 null 而且已经是取消状态
                        if (h != null && h.isCancelled())
                            // CAS 将头结点设置成下一个节点，继续循环
                            casHead(h, h.next);     // pop cancelled node
                        // 否则返回 null
                        else
                            return null;
                        // CAS 尝试入栈
                    } else if (casHead(h, s = snode(s, e, h, mode))) {
                        // 入栈成功（模式相同可以入栈）
                        // 调用 awaitFulfill 方法自旋+阻塞当前线程，等待被匹配
                        SNode m = awaitFulfill(s, timed, nanos);
                        // 如果 m 等于 s，说明取消了，把它清除，并返回 null
                        if (m == s) {               // wait was cancelled
                            clean(s);
                            return null;
                        }
                        // 运行到这里说明匹配到元素了，因为从 awaitFulfill 出来要么
                        // 就是取消了，要么就是匹配到了。
                        // 如果头结点不为 null 而且头结点的下一个节点是 s
                        // CAS 尝试将头结点设置成 s.next，即弹出栈顶的两个元素
                        if ((h = head) != null && h.next == s)
                            casHead(h, s.next);     // help s's fulfiller
                        // 根据当前节点的模式判断返回 m 还是 s 中的值
                        return (E) ((mode == REQUEST) ? m.item : s.item);
                    }
                    // 栈顶有元素而且模式不一样（可匹配）
                    // 判断头结点是否正在匹配中，如果没有，进入到此代码块中
                } else if (!isFulfilling(h.mode)) { // try to fulfill
                    // h 已经被取消了，将头结点设置为 h 的下一个节点
                    if (h.isCancelled())            // already cancelled
                        casHead(h, h.next);         // pop and retry
                    // 先让节点进入队列，将其设置为头结点，状态为正在匹配
                    else if (casHead(h, s=snode(s, e, h, FULFILLING|mode))) {
                        for (;;) { // loop until matched or waiters disappear
                            SNode m = s.next;       // m is s's match
                            // 已经被其他线程匹配掉了
                            // 将头结点设置为 null，到外部再重新循环
                            if (m == null) {        // all waiters are gone
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }
                            SNode mn = m.next;
                            // 如果 m 和 s 匹配成功，就弹出栈顶的两个元素 m 和 s
                            if (m.tryMatch(s)) {
                                casHead(s, mn);     // pop both s and m
                                return (E) ((mode == REQUEST) ? m.item : s.item);
                            } else                  // lost match
                            // 匹配失败说明其他线程已经匹配 m 了，协助清除它
                                s.casNext(m, mn);   // help unlink
                        }
                    }
                    // 头结点和当前操作模式不一样，且头结点正在匹配中
                    // 帮助匹配
                } else {                            // help a fulfiller
                    SNode m = h.next;               // m is h's match
                    // m 已经被其他线程匹配了
                    if (m == null)                  // waiter is gone
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;
                        // 协助匹配
                        if (m.tryMatch(h))          // help match
                            // 匹配成功，弹出栈顶的两个元素
                            casHead(h, mn);         // pop both h and m
                        else                        // lost match
                        // 匹配失败，说明 m 已经被其他线程匹配了，协助清除
                            h.casNext(m, mn);       // help unlink
                    }
                }
            }
        }
```

awaitFulFill 方法用于当前线程阻塞等待。

```java
        /**
         * 自旋/阻塞直到节点 s 被匹配。
         *
         * @param s the waiting node
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched node, or s if cancelled
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            // 到期时间
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            // 当前线程
            Thread w = Thread.currentThread();
            // 自旋次数
            int spins = (shouldSpin(s) ?
                    (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                // 当前线程被设置中断，尝试清除 s
                if (w.isInterrupted())
                    s.tryCancel();
                SNode m = s.match;
                // 已经匹配到了，返回匹配到的节点
                if (m != null)
                    return m;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    // 超时了，尝试清除 s
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }
                if (spins > 0)
                    // 还有自旋次数，自旋次数减一，然后进入下一次自旋
                    spins = shouldSpin(s) ? (spins-1) : 0;
                // 到这儿说明自旋次数没有了
                else if (s.waiter == null)
                    // s 中等待的线程为 null，设置为当前线程
                    s.waiter = w; // establish waiter so can park next iter
                else if (!timed)
                    // 不允许超时，直接阻塞
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    // 阻塞相应的时间
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * 如果节点是头结点或者是活跃的匹配者。
         */
        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }
```

clean 用于删除指定节点。

```java
        /**
         * 将 s 从栈中删除。
         */
        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread

            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             */

            // 后面的两步操作都遍历到 past 为止
            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // 找到第一个有效的 head
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // 将 p 节点的 next 设置成下一个有效节点
            while (p != null && p != past) {
                SNode n = p.next;
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }
```

## 公平模式

公平模式的规则是先进先出（FIFO），通过链式单向队列实现。

### 内部类

```java
        /** Node class for TransferQueue. */
        static final class QNode {
            volatile QNode next;          // next node in queue
            volatile Object item;         // CAS'ed to or from null
            volatile Thread waiter;       // to control park/unpark
            // 节点类型，是数据节点还是请求节点
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            boolean casNext(QNode cmp, QNode val) {
                return next == cmp &&
                        UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            boolean casItem(Object cmp, Object val) {
                return item == cmp &&
                        UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * item 指向自己，表示已删除
             */
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            // 节点是否已经失效
            boolean isCancelled() {
                return item == this;
            }

            /**
             * 如果节点已经离开了队列。
             */
            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long itemOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                            (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }
```

### 类属性





```java
        /** 队列头 */
        transient volatile QNode head;
        /** 队列尾 */
        transient volatile QNode tail;
        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it was cancelled.
         */
        transient volatile QNode cleanMe;
```


### 成员函数


```java
        /**
         * put 或者 take 的核心操作
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.
             */

            QNode s = null; // constructed/reused as needed
            boolean isData = (e != null);

            // 自旋
            for (;;) {
                QNode t = tail;
                QNode h = head;
                // 未初始化
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin

                // 队列为空或者模式相同
                if (h == t || t.isData == isData) {
                    QNode tn = t.next;
                    // 重新读取 tail
                    if (t != tail)
                        continue;
                    // 还没有到达尾结点，tail 向后移动（tail 节点需要 CAS）
                    if (tn != null) {
                        advanceTail(t, tn);
                        continue;
                    }
                    if (timed && nanos <= 0)        // can't wait
                        return null;
                    // 创造新节点
                    if (s == null)
                        s = new QNode(e, isData);
                    // 添加节点
                    if (!t.casNext(null, s))
                        continue;

                    // 将尾结点设置成新添加的节点
                    advanceTail(t, s);
                    // 自旋/等待
                    Object x = awaitFulfill(s, e, timed, nanos);
                    // 等待结束，删除节点
                    if (x == s) {
                        clean(t, s);
                        return null;
                    }

                    // s 没有离开队列
                    if (!s.isOffList()) {
                        advanceHead(t, s);
                        if (x != null)
                            s.item = s;
                        s.waiter = null;
                    }
                    return (x != null) ? (E)x : e;
                } else {
                    // 模式不同，进入此程序段
                    // 从 head 节点开始查找
                    QNode m = h.next;               // node to fulfill
                    // 重新获取 head/tail
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read

                    Object x = m.item;
                    // 如果模式相同（其他线程已经进行了处理）
                    // 或者节点被取消
                    // 或者匹配失败（CAS 失败，说明有其他线程已经处理了）
                    if (isData == (x != null) ||
                            x == m ||
                            !m.casItem(x, e)) {
                        // 继续往后查找，head 往后移动
                        advanceHead(h, m);
                        continue;
                    }

                    // 匹配成功，head 出列
                    advanceHead(h, m);
                    // 唤醒匹配成功节点里的线程
                    LockSupport.unpark(m.waiter);
                    return (x != null) ? (E)x : e;
                }
            }
        }
```


```java
        /**
         * 自旋/阻塞直到节点被填满
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            int spins = ((head.next == s) ?
                    (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                if (w.isInterrupted())
                    s.tryCancel(e);
                Object x = s.item;
                if (x != e)
                    return x;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                if (spins > 0)
                    --spins;
                else if (s.waiter == null)
                    s.waiter = w;
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }
```


```java
        /**
         * 清除节点 s
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            /*
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             */
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;
                // 找到有效的 head 节点
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail
                // 队列为空，直接返回
                if (t == h)
                    return;
                QNode tn = t.next;
                // tail 被修改，重新获取
                if (t != tail)
                    continue;
                // 找到新的 tail 节点
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                // 如果要删除的节点不是 tail，直接修改 pred 的 next，然后返回
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                // s 是尾结点
                // 不能直接删除尾结点，此时可能有其它线程正在执行入队操作，
                // 而入队是放在队尾的，因此直接删除队尾，可能会导致其它线程
                // 入队操作不能正确的加入队列
               // cleanMe 表示需要删除节点的前驱
                QNode dp = cleanMe;
                // 有需要删除的节点
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    // 删除 d
                    if (d == null ||               // d is gone or
                            d == dp ||                 // d is off list or
                            !d.isCancelled() ||        // d not cancelled or
                            (d != t &&                 // d not tail and
                                    (dn = d.next) != null &&  //   has successor
                                    dn != d &&                //   that is on list
                                    dp.casNext(d, dn)))       // d unspliced
                        // 将 cleanMe 设置为 null
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                    // 将 cleanMe 设置为 pred
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }
```

## 参考

[Java 并发 --- 阻塞队列之SynchronousQueue源码分析](https://blog.csdn.net/u014634338/article/details/78419445)

[死磕 java集合之SynchronousQueue源码分析](https://www.cnblogs.com/tong-yuan/p/SynchronousQueue.html)

[JUC源码分析-集合篇（八）：SynchronousQueue](https://www.jianshu.com/p/c4855acb57ec)
