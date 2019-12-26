## ConcurrentLinkedQueue

ConcurrentLinkedQueue 是非阻塞无界并发队列，主要利用 CAS 实现多线程环境下的并发安全，元素入队出队规则为 FIFO (first-in-first-out 先入先出) 。

### 完整源码解析

[ConcurrentLinkedQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/ConcurrentLinkedQueue.java)

### 类属性

此类中节点的组织形式为单向链表形式，类属性维护两个指向节点的引用，分别为 head 和 tail，表示头结点和尾节点。

为了提高效率，head 并非在每个时刻都指向队列绝对意义上的头结点，同样 tail 并非在每个时刻都指向队列绝对意义上的尾结点，因为并不是每次更新操作都会实时更新 head 和 tail（如果每次操作都实时更新，那并发环境下的更新将会无限趋近于同步状态下的更新，效率较低。实际上，在入队或出队操作中检查到 head/tail 和实际位置相差一个节点时，才会通过 CAS 更新它们的位置，并且当 CAS 失败时，当前线程并不会二次尝试）。但是它们遵循一定的规则，这些规则在下面的注释行中已经罗列出来。

```java
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
```

### 内部类 Node

节点类 Node 是保存元素值的封装类，除了包含基本的 item 变量和 next “指针”之外，还提供了在并发环境下的 CAS 原子操作 casItem、lazySetNext、casNext，它们构成了并发环境下完成入队出队操作的基础。

```java
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
    }
```

### 成员函数

**offer**

offer 函数用于入队操作。在此队列中 head 并不一定指向队列最头部元素（最头部元素并不一定是有效元素，因为其 item 可能为 null，是因为 item 可能已经被获取，但是还没有显式地删除头部节点），tail 也不一定指向最后一个元素。

为了在尾节点之后添加一个新的节点，从 tail 开始往后查找，此时有三种情况：

1.找到了尾节点（尾节点的 next 指向 null。Node 节点存在另外一种形式为标记节点，即 next 指向自身，这样的形式表示该节点已经彻底和链表没有任何关系。不直接将 next 置为 null 是为了将其和尾节点区分开。），CAS 方式将新的节点插入到尾节点之后，同时判断是否需要更新 tail；

2.当前节点为标记节点，那么重新从（尾节点/头结点）开始查找；

3.tail 节点变化，重新获取 tail 节点，然后往后查找。

```java
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
```

**poll**

对于出队操作，首先应该找到有效的头结点。尝试从 head 开始往后查找，找到即返回 item，并判断是否更新 head。否则使用类似 poll 的方式，对于 item 等于 null 产生的三种不同的情况，分别讨论。

```java
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
```


### 引用

* [JUC源码分析-集合篇（四）：ConcurrentLinkedQueue](https://www.jianshu.com/p/0c5a672b2ade)

* [Java并发编程笔记之ConcurrentLinkedQueue源码探究](https://www.cnblogs.com/huangjuncong/p/9196240.html)

* [ConcurrentLinkedQueue全解析](https://www.jianshu.com/p/ce6108e4b2c4)
