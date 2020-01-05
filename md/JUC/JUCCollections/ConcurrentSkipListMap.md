## ConcurrentSkipListMap

在 ConcurrentSkipListMap 之前，了解跳跃表的概念和基本操作是必要的，忽略跳跃表的实际结构直接阅读源码难度较大。

### 完整源码解析

[ConcurrentSkipListMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/ConcurrentSkipListMap.java)

### 跳跃表

跳跃表是一种可以用来代替平衡树的数据结构，实际上就是在链表之上添加多级索引，通过索引实现链表中的快速查找。如果有下面这样的基础链表：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap1.png" width=70% />

在链表之上构造两层索引，如下所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap2.png" width=70% />

在最初的链表中查找节点 20 需要从头遍历所有节点，在跳跃表中从最高层的头结点开始查找，路径如下图所示，只需要经过索引节点 1,5,17,20 即可：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap3.png" width=70% />

标准化的跳跃表中每两个元素提取一个元素作为上一层的索引，在数据量大的时候，如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap4.png" width=70% />

这是典型的以空间换时间的数据结构。每一次需要考虑的元素减少一半，查找的时间复杂度基本相当于平衡二叉树。

在 ConcurrentSkipListMap 的具体实现中，没有完全按照标准化的跳跃表构造，而是通过随机的方式决定是否增加层级。

### 内部类

ConcurrentSkipListMap 中的跳跃表主要由三种作为内部类的节点构成，他们之间的关系如下：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap5.png" width=70% />

**Node** 表示最底层链表中存储实际数据的节点，和最普通的单向链表节点一样，只包括三个属性：key、value 和 next。除此之外，有一系列 CAS 操作的方法，用于原子更新节点属性，这些方法是 ConcurrentSkipListMap 能够作为线程安全集合类的基础。

标记节点位于即将被删除节点的后面，所有 value 为自身的节点都是标记节点。

```java
    /**
     * 存储键值对数据的节点，按照顺序排序，单向连接，可能中间有一些标记
     * 节点。列表由一个可以作为 head.node 访问的虚拟节点作为头。 value 字段
     * 声明为 Object，因为作为头节点和标记节点（删除时将 value 指向自身）
     * 可以接受特殊的非 V 类型的对象。
     */
    static final class Node<K,V> {
        final K key;
        volatile Object value;
        volatile Node<K,V> next;

        /**
         * 创造一个普通节点
         */
        Node(K key, Object value, Node<K,V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        /**
         * 创造一个新的标记节点。标记节点和普通节点的区别在于标记节点的 value
         * 字段指向自己。标记节点也有 null 的 key，但这不能将标记节点和
         * 最底层的头结点（head.node）区分开，后者也有 null 的 key。
         */
        Node(Node<K,V> next) {
            this.key = null;
            this.value = this;
            this.next = next;
        }

        /**
         * CAS 方式改变 value
         */
        boolean casValue(Object cmp, Object val) {
            return UNSAFE.compareAndSwapObject(this, valueOffset, cmp, val);
        }

        /**
         * CAS 方式改变 next 的引用
         */
        boolean casNext(Node<K,V> cmp, Node<K,V> val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        /**
         * 如果节点是标记节点返回 true。此方法实际上并没有在标记节点的
         * 任何当前的代码检查中被调用，因为调用者已经读取了 value 字段，
         * 并且需要使用这个读操作，所以直接测试 value 是否指向 node。
         *
         * @return true if this node is a marker node
         */
        boolean isMarker() {
            return value == this;
        }

        /**
         * 如果此节点是最底层链表的头结点返回 true。
         * @return true if this node is header node
         */
        boolean isBaseHeader() {
            return value == BASE_HEADER;
        }

        /**
         * 如果 f 节点是当前节点的后继节点，在当前节点和 f 之间插入一个标记节点
         * @param f the assumed current successor of this node
         * @return true if successful
         */
        boolean appendMarker(Node<K,V> f) {
            return casNext(f, new Node<K,V>(f));
        }

        /**
         * 辅助删除，通过添加 marker 或者和前驱节点取消关联。遍历过程中若 value
         * 为 null 时调用。
         * @param b predecessor
         * @param f successor
         */
        void helpDelete(Node<K,V> b, Node<K,V> f) {
            // 如果 this 节点在 b 和 f 中间
            if (f == next && this == b.next) {
                // 如果 f 等于 null 或者 f 不是标记节点，在 n 和 f 中间插入一个标记节点
                if (f == null || f.value != f) // not already marked
                    casNext(f, new Node<K,V>(f));
                // f 不等于 null 且 f 是标记节点，将 b 的 next 指向 f 的 next，
                // 即删除 b 和 f.next 之间的两个节点（n 和 n 后面的标记节点）
                else
                    b.casNext(this, f.next);
            }
        }

        /**
         * 如果此节点中是一个有效的 key-value 对，返回其 value，否则返回 null。
         * @return this node's value if it isn't a marker or header or
         * is deleted, else null
         */
        V getValidValue() {
            Object v = value;
            // 此节点是标记节点或者是最底层的头结点
            if (v == this || v == BASE_HEADER)
                return null;
            @SuppressWarnings("unchecked") V vv = (V)v;
            return vv;
        }

        /**
         * 如果此节点中是一个有效的 key-value 对，创建并返回一个包含 key-value 的
         * SimpleImmutableEntry 对象（快照），否则返回 null。
         * @return new entry or null
         */
        AbstractMap.SimpleImmutableEntry<K,V> createSnapshot() {
            Object v = value;
            if (v == null || v == this || v == BASE_HEADER)
                return null;
            @SuppressWarnings("unchecked") V vv = (V)v;
            return new AbstractMap.SimpleImmutableEntry<K,V>(key, vv);
        }

        // UNSAFE mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long valueOffset;
        private static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                valueOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("value"));
                nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
```

**Index** 节点是在第一层之上的，普通的索引节点；**HeadIndex** 表示每一层索引节点链的头结点。

```java
    /**
     * 索引节点
     */
    static class Index<K,V> {
        final Node<K,V> node;
        final Index<K,V> down;
        volatile Index<K,V> right;

        /**
         * 构造函数
         */
        Index(Node<K,V> node, Index<K,V> down, Index<K,V> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }

        /**
         * CAS 方式设置 right
         */
        final boolean casRight(Index<K,V> cmp, Index<K,V> val) {
            return UNSAFE.compareAndSwapObject(this, rightOffset, cmp, val);
        }

        /**
         * 如果索引节点已经被删除返回 true
         * @return true if indexed node is known to be deleted
         */
        final boolean indexesDeletedNode() {
            return node.value == null;
        }

        /**
         * 将 newSucc 插入到当前节点和 succ 之间（相对于 right 而言的中间，
         * 只改变 right 的指向，不考虑 down）。
         *
         * @param succ the expected current successor
         * @param newSucc the new successor
         * @return true if successful
         */
        final boolean link(Index<K,V> succ, Index<K,V> newSucc) {
            Node<K,V> n = node;
            newSucc.right = succ;
            return n.value != null && casRight(succ, newSucc);
        }

        /**
         * 将 succ 从当前节点和 succ.next 之间删除（相对于 right 而言）。
         *
         * @param succ the expected current successor
         * @return true if successful
         */
        final boolean unlink(Index<K,V> succ) {
            return node.value != null && casRight(succ, succ.right);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long rightOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Index.class;
                rightOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("right"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }
    
    /**
     * 每一层索引的头结点
     */
    static final class HeadIndex<K,V> extends Index<K,V> {
        final int level;
        HeadIndex(Node<K,V> node, Index<K,V> down, Index<K,V> right, int level) {
            super(node, down, right);
            this.level = level;
        }
    }
```

### 类属性

跳跃表中有两个重要的属性，一个是 BASE_HEADER，是最底层存储实际数据节点的链表的头结点；另一个是 head，是索引层中最高层的头结点，跳跃表中任何有效节点都可以从该节点到达。

```java
    /**
     * 最底层（base-level）的头结点
     */
    private static final Object BASE_HEADER = new Object();

    /**
     * 最高层的头结点
     */
    private transient volatile HeadIndex<K,V> head;
```

### 成员函数

**添加**

添加操作是 ConcurrentSkipListMap 中最复杂的操作。整个过程主要分成三步，第一步在最底层的单向链表中插入数据节点；第二步根据产生的随机数，确定是否需要增加层级，并在每一层建立添加节点的索引（仅仅建立）；第三步将构建的索引添加到每一层的索引链表中。

详细解释请参考：[死磕 java集合之ConcurrentSkipListMap源码分析——发现个bug](https://www.cnblogs.com/tong-yuan/p/ConcurrentSkipListMap.html)

相关方法中包括两个核心部分，跳跃表数据结构的操作（插入、删除和查找）和多线程操作时的并发控制，和 1.8 中大多数非阻塞的并发集合一样，保障原子修改的基础还是一系列 CAS 操作。在 CAS 之上包装两层循环， CAS 失败（有其他线程同时进行操作时）时，重新回到最开始的地方，重新进行一次内层的循环。

```java
    /**
     * 执行插入操作的函数。
     *
     * @param key the key
     * @param value the value that must be associated with key
     * @param onlyIfAbsent if should not insert if already present
     * @return the old value, or null if newly inserted
     */
    private V doPut(K key, V value, boolean onlyIfAbsent) {
        Node<K,V> z;             // added node
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;

        // 第一部分：在最底层链表中插入节点
        outer: for (;;) {
            // findPredecessor 函数找到给定 key 的前继节点 b
            for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
                if (n != null) {
                    Object v; int c;
                    Node<K,V> f = n.next;
                    // 读不一致，跳出重试
                    if (n != b.next)               // inconsistent read
                        break;
                    // 如果 n 已经被删除了，帮助删除，并跳出重新开始
                    if ((v = n.value) == null) {   // n is deleted
                        n.helpDelete(b, f);
                        break;
                    }
                    // b 已经被删除了，跳出重试
                    if (b.value == null || v == n) // b is deleted
                        break;
                    // 当前 key 大于后一个节点的 key，继续往后查找
                    if ((c = cpr(cmp, key, n.key)) > 0) {
                        b = n;
                        n = f;
                        continue;
                    }
                    // 指定 key 已经存在
                    if (c == 0) {
                        // onlyIfAbsent 为 true 时直接返回当前值，不修改
                        // 否则尝试 CAS 修改 value
                        if (onlyIfAbsent || n.casValue(v, value)) {
                            @SuppressWarnings("unchecked") V vv = (V)v;
                            return vv;
                        }
                        // CAS 失败跳出内层循环，重新开始外层循环
                        break; // restart if lost race to replace value
                    }
                    // else c < 0; fall through
                }

                // 上面 if 所有条件都不满足（可以插入），或者直接 n == null（后面已经没有节点了）
                z = new Node<K,V>(key, value, n);
                // 把新节点添加 b 节点后面
                if (!b.casNext(n, z))
                    // 失败则重新开始查找
                    break;
                // 添加成功跳出
                break outer;
            }
        }

        // 更新跳表的索引
        int rnd = ThreadLocalRandom.nextSecondarySeed();
        // 生成的随机数为正偶数时才会更新（最高位和最低位不为 1）
        if ((rnd & 0x80000001) == 0) {

            // 第二部分：根据产生的随机数，确认是否需要增加一层索引
            // 不需要增加层数（level <= head.level）就只要建好底层到 level 层
            // 的纵向索引即可
            // 需要增加层数则还需要建新一层的 HeadIndex，并转移 head
            int level = 1, max;
            // 计算 level
            // 从低 2 位开始向左有多少个连续的 1
            while (((rnd >>>= 1) & 1) != 0)
                ++level;
            Index<K,V> idx = null;
            HeadIndex<K,V> h = head;
            // 计算出来的层级小于等于最高层的层级，不需要增加，需要更新
            if (level <= (max = h.level)) {
                for (int i = 1; i <= level; ++i)
                    // z 是刚刚新添加的节点，idx 是 down（注意所有的索引节点都有
                    // node 属性，保存它对应的数据节点）
                    idx = new Index<K,V>(z, idx, null);
            }
            // 否则增加一层
            else {
                // 在最高层数基础上增加一层
                level = max + 1;
                // 构建一个 level + 1 长度的 Index 数组
                @SuppressWarnings("unchecked")Index<K,V>[] idxs =
                        (Index<K,V>[])new Index<?,?>[level+1];
                // 从下到上构建值为 z 的索引节点，索引节点存在数组里面
                for (int i = 1; i <= level; ++i)
                    idxs[i] = idx = new Index<K,V>(z, idx, null);
                for (;;) {
                    h = head;
                    int oldLevel = h.level;
                    // 其他线程已经将层数增加了，跳出
                    if (level <= oldLevel) // lost race to add level
                        break;
                    HeadIndex<K,V> newh = h;
                    Node<K,V> oldbase = h.node;
                    // 创建新的 head，将 down 指向原来的 head，将 right 指向创建的索引列
                    // 单线程情况下，此循环只会执行一次
                    for (int j = oldLevel+1; j <= level; ++j)
                        newh = new HeadIndex<K,V>(oldbase, newh, idxs[j], j);
                    // 尝试 CAS 将 head 设置为新创建的 head
                    if (casHead(h, newh)) {
                        h = newh;
                        idx = idxs[level = oldLevel];
                        break;
                    }
                }
            }

            // 第三部分：将新建的纵向索引节点和其他索引节点通过右指针连在一起
            splice: for (int insertionLevel = level;;) {
                // 上面循环中初始的 level 是旧的最高层
                // j 是新的最高层的层级
                int j = h.level;
                // 从新的 head 开始
                // 如果增加了一层，此时的 t 是在老的最高层的目标索引，
                // 没有增加层数，此时的 t 是创造的纵向目标索引的最高层
                for (Index<K,V> q = h, r = q.right, t = idx;;) {
                    // 如果遍历到了最右边或者最下边，退出外层循环
                    if (q == null || t == null)
                        break splice;
                    // right 节点不为 null，可以往 right 查找
                    if (r != null) {
                        Node<K,V> n = r.node;
                        // 比较 r 的 node 的 key 和插入节点的 key
                        int c = cpr(cmp, key, n.key);
                        // 节点的 value 为 null，需要删除
                        if (n.value == null) {
                            // 删除，如果删除失败，说明其他线程有修改，重新来
                            if (!q.unlink(r))
                                break;
                            // 删除成功后还是取右节点
                            r = q.right;
                            continue;
                        }
                        // 还要往右边查找
                        if (c > 0) {
                            q = r;
                            r = r.right;
                            continue;
                        }
                    }

                    // j 最初是新最高层的层级，insertionLevel 最初是旧的最高层层级
                    // 实际上第一次不会进入这个 if
                    // 最高层的 HeadIndex 的 right 已经连接了目标索引，所以最高层
                    // 并不需要进入这个 if

                    // 一般情况下 j 和 insertionLevel 是同步的
                    if (j == insertionLevel) {
                        // 在 q 和 r 之间插入 t
                        if (!q.link(r, t))
                            break; // 如果失败了，退出内层循环重试
                        // 如果插入完成后，t 节点被删除，那么结束插入操作
                        if (t.node.value == null) {
                            findNode(key);
                            break splice;
                        }
                        // 到达最底层
                        if (--insertionLevel == 0)
                            break splice;
                    }

                    // j 先自减一，然后和两个 level 比较
                    if (--j >= insertionLevel && j < level)
                        t = t.down;
                    // 当前层级已经到最右边了，继续往下一层级
                    // q 向下移动一位
                    q = q.down;
                    r = q.right;
                }
            }
        }
        return null;
    }
    
    /**
     * 返回最底层节点中比给定 key 小的节点，如果没有返回底层的头结点。
     * @param key the key
     * @return a predecessor of key
     */
    private Node<K,V> findPredecessor(Object key, Comparator<? super K> cmp) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        for (;;) {
            // head：最高层的头结点
            for (Index<K,V> q = head, r = q.right, d;;) {
                // 右节点不为 null
                if (r != null) {
                    Node<K,V> n = r.node;
                    K k = n.key;
                    // 如果 r.Node 已经被删除
                    if (n.value == null) {
                        // 尝试 CAS 更新 q 的右节点为 r.right（删除 r）
                        if (!q.unlink(r))
                            // 更新失败跳出内层循环
                            break;
                        // 更新成功重新读取 q 的右节点
                        r = q.right;
                        // 重新开始循环，所以不会马上进入到下面的 if 判断
                        continue;
                    }
                    // 比较 key 和 k，如果 key 大于 k，继续向右循环
                    if (cpr(cmp, key, k) > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    }
                }
                // 右节点为 null，或者 key 小于右节点的 key，向下查找
                // 下节点为 null，返回当前 q 节点的 node
                if ((d = q.down) == null)
                    return q.node;
                // 下节点不为 null，继续往后查找
                q = d;
                r = d.right;
            }
        }
    }
    
    /**
     * 返回指定 key 对应的节点，没有返回 null，清除查找路径上遇到的失效节点。
     *
     * @param key the key
     * @return node holding key, or null if no such
     */
    private Node<K,V> findNode(Object key) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        Comparator<? super K> cmp = comparator;
        outer: for (;;) {
            // 找到指定 key 的前继节点
            for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
                Object v; int c;
                // 到头了
                if (n == null)
                    break outer;
                Node<K,V> f = n.next;
                // 有其他线程，重新循环
                if (n != b.next)
                    break;
                // 已经被标记删除，调用 helpDelete 删除
                if ((v = n.value) == null) {
                    n.helpDelete(b, f);
                    break;
                }
                // b 已经被删除
                if (b.value == null || v == n)
                    break;
                // 找到了
                if ((c = cpr(cmp, key, n.key)) == 0)
                    return n;
                if (c < 0)
                    break outer;
                b = n;
                n = f;
            }
        }
        return null;
    }
```

**删除**

删除操作包含删除底层数据节点和删除索引（可能出现层级下降）。

删除时首先将节点的 value 置为 null，然后添加标记节点，再执行删除。删除失败通过 findNode 中的 helpDelete 不断尝试删除。

详细解释请参考：[死磕 java集合之ConcurrentSkipListMap源码分析——发现个bug](https://www.cnblogs.com/tong-yuan/p/ConcurrentSkipListMap.html)

```java
    /**
     * 执行删除操作的主要函数。定位节点，value 置为 null，添加一个删除的标记，
     * 前驱节点取消连接，删除关联的索引节点，可能还会减少索引的层数。
     *
     * @param key the key
     * @param value if non-null, the value that must be
     * associated with key
     * @return the node, or null if not found
     */
    final V doRemove(Object key, Object value) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        outer: for (;;) {
            // 找到目标节点的前驱节点 b
            for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
                Object v; int c;
                // 链表中不存在目标节点，直接退出
                if (n == null)
                    break outer;
                // f 当前节点的后继节点
                Node<K,V> f = n.next;
                // 再次检查 n 不是 b.next，说明被其他线程修改过，重新开始
                if (n != b.next)                    // inconsistent read
                    break;
                // n 被标记为删除状态
                if ((v = n.value) == null) {        // n is deleted
                    // 辅助删除，然后跳出内层循环
                    n.helpDelete(b, f);
                    break;
                }
                // b 已经个被删除，这时候表示 n 是 marker 节点，b 是应该被
                // 删除的节点
                if (b.value == null || v == n)      // b is deleted
                    break;
                // 没找到元素，退出
                if ((c = cpr(cmp, key, n.key)) < 0)
                    break outer;
                // 继续往右找
                if (c > 0) {
                    b = n;
                    n = f;
                    continue;
                }
                // 进行到这里说明 c == 0，找到了要删除的节点 n
                // value 不等于 v，说明其他线程把 value 修改了
                if (value != null && !value.equals(v))
                    break outer;
                // 完成所有的检查，执行删除节点 n
                // CAS 将 n 的 value 设置为 null
                if (!n.casValue(v, null))
                    break;
                // 尝试在 n 节点后添加标记节点（失败直接进入 if）
                // 尝试将 n 的前驱节点 b 的 next 设置成 n 的下一个节点
                if (!n.appendMarker(f) || !b.casNext(n, f))
                    // 上面有其中一个失败，都会进入这个 if
                    // 调用 findNode 清除已删除的节点
                    findNode(key);
                else {
                    // 说明节点一定删除了，通过 findPredecessor 删除索引节点
                    findPredecessor(key, cmp);      // clean index
                    // 如果删除索引节点之后，最高层没有 right 了，则删除最高层
                    if (head.right == null)
                        tryReduceLevel();
                }
                // 返回删除的元素值
                @SuppressWarnings("unchecked") V vv = (V)v;
                return vv;
            }
        }
        return null;
    }
```

**查找**

查找元素的操作比较简单。

详细解释请参考：[死磕 java集合之ConcurrentSkipListMap源码分析——发现个bug](https://www.cnblogs.com/tong-yuan/p/ConcurrentSkipListMap.html)

```java
    /**
     * 执行 get 操作。几乎和 findNode 一样。
     *
     * @param key the key
     * @return the value, or null if absent
     */
    private V doGet(Object key) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        outer: for (;;) {
            // 找到 key 的前驱节点
            for (Node<K,V> b = findPredecessor(key, cmp), n = b.next;;) {
                Object v; int c;
                // 链表到头，跳出外层循环
                if (n == null)
                    break outer;
                Node<K,V> f = n.next;
                // 有其他线程，重新循环
                if (n != b.next)                // inconsistent read
                    break;
                // 被标记删除，删除，再重试
                if ((v = n.value) == null) {    // n is deleted
                    n.helpDelete(b, f);
                    break;
                }
                // b 已经被删除
                if (b.value == null || v == n)  // b is deleted
                    break;
                // 找到了，返回 value
                if ((c = cpr(cmp, key, n.key)) == 0) {
                    @SuppressWarnings("unchecked") V vv = (V)v;
                    return vv;
                }
                if (c < 0)
                    break outer;
                // 继续找
                b = n;
                n = f;
            }
        }
        return null;
    }
```

### 为什么 Redis 使用跳跃表而不是红黑树实现有序集合

有序集合需要支持的操作包括：插入元素、删除元素、查找元素、有序输出所有元素和查找区间内所有元素。

其中前四项红黑树都可以完成，时间复杂度和跳跃表差不多。但是针对最后一项，两者有一定的性能差距。在跳跃表中查找区间内元素，只需要查找区间两个端点的位置即可；在标准的红黑树中找到区间最小值之后，需要以中序遍历的顺序继续寻找其它不超过最大值的节点（没有指向父节点的指针，中序遍历并不容易实现）。

除此之外，跳跃表相对于红黑树来说，实现较简单，更新也更灵活。特别是在并发环境中，需要更新节点时，跳跃表更新的部分很少，需要锁住的部分也少，而红黑树的平衡过程牵涉到大量节点，锁的代价相对较高，性能也不如前者。

### 引用

[死磕 java集合之ConcurrentSkipListMap源码分析——发现个bug](https://www.cnblogs.com/tong-yuan/p/ConcurrentSkipListMap.html)

[基于跳跃表的 ConcurrentSkipListMap 内部实现（Java 8）](https://www.cnblogs.com/yangming1996/p/8084819.html)

[JUC源码分析-集合篇（三）：ConcurrentSkipListMap和ConcurrentSkipListSet](https://www.jianshu.com/p/8a223af84fc4)

[拜托，面试别再问我跳表了！ ](https://mp.weixin.qq.com/s/wacN04NHN2Zm0mZIlftxaw)
