## LinkedTransferQueue

LinkedTransferQueue 是单向链表结构的无界阻塞队列，通过 CAS 和 LockSupport 实现线程安全，按照 FIFO 的顺序排列。

### 完整源码解析

[LinkedTransferQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/LinkedTransferQueue.java)

### 类属性

LinkedTransferQueue 队列实现的基础是单向链表，且使用松弛度减少系统开销，所以维护两个节点 head、tail，分别表示实际的头结点和尾节点。

```java
    /** 头节点；在第一次 enqueue 操作之前为 null */
    transient volatile Node head;

    /** 尾节点；在第一次 append 操作之前为 null */
    private transient volatile Node tail;
```

### 内部类 Node

和 ConcurrentLinkedQueue 一样，节点类中包含作为实现 LinkedTransferQueue 类线程安全基础的一系列 CAS 操作，除此之外，还包含两个特有属性，分别是 isData，waiter。

* isData：表示此节点的属性。LinkedTransferQueue 队列中有两种节点，数据节点和非数据节点。
    * 非数据节点的 isData 属性为 false，数据节点为 true。当消费者线程执行 take 操作时，如果队列为空，当前线程会作为一个元素为 null 的节点放入队列中等待，直到等到生产者线程。
* waiter：消费者线程在节点中等待数据。

**注意：**

1. 队列中只可能有一种节点类型（数据节点或非数据节点）。

2. 下文中会多次提到“匹配”，“匹配成功”表示正在等待的消费者线程终于有了生产者。生产者线程执行 put 操作，将数据放入到队列头部等待数据的消费者节点中。此时生产者和消费者成功对应，之后再将等待的消费者节点从队列中删除。

3. 此类中用到自链接节点，意义和用法与 ConcurrentLinkedQueue 相同。

```java
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
```

### 成员函数

此类与 ConcurrentLinkedQueue 和 ConcurrentLinkedDeque 实现方式类似，都是使用 CAS，而非锁来保证线程安全。

一般的队列 put 和 take 分别在不同的方法中实现，而此类中所有的操作实际上使用的是同一个函数： xfer，可以这么做是因为 take 和 put 操作的流程基本一样，如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/LinkedTransferQueue.png" width=70% />

使用“匹配”的方式确定下一步操作是删除队列头部元素还是在队列尾部添加元素。每一次都从队列头部节点开始匹配。

put 时如果头结点（不一定是第一个节点，因为可能有其他线程同时进行操作）是非数据节点，就匹配成功，如果头结点是数据节点，生成一个新的数据节点添加到队列尾部。

take 时如果头结点是非数据节点，就匹配成功，如果是数据节点，生成一个新的费数据节点添加到队列尾部。

简而言之，无论是入队还是出队，都先跟头结点对比，如果二者模式不一样则匹配成功，模式一样则添加新节点。

xfer 主要分成两个部分，第一个是从 head 节点开始匹配，如果匹配成功就返回，匹配失败进入第二个部分，根据指定的模式确定是否等待，是否阻塞或限时阻塞：

* NOW：没有匹配到立即返回，不做任何操作。对应 poll, tryTransfer。

* ASYNC：异步，元素进入队列但当前线程不会阻塞。对应 add, offer, put, offer。

* SYNC：同步，元素进入队列后阻塞当前线程，等待被匹配。对应 take, transfer。

* TIMED：有超时，元素进入队列后等待一段时间，没有被匹配则退出。对应 poll, tryTransfer。

**xfer**

```java
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
```

### 参考

[死磕 java集合之LinkedTransferQueue源码分析](https://www.cnblogs.com/tong-yuan/p/LinkedTransferQueue.html)

[并发编程—— LinkedTransferQueue](https://www.cnblogs.com/stateis0/p/9062076.html)
