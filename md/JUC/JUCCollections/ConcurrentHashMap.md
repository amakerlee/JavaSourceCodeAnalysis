## ConcurrentHashMap

ConcurrentHashMap 是在 HashMap 的基础上进行改进的线程安全 Map 类，在开始 ConcurrentHashMap 前，务必提前了解 HashMap 的原理和基本思想。

ConcurrentHashMap 和 Hashtable 对象的 key、value 值均不可为 null，而 HashMap 对象的 key、value 值均可为 null。

### 完整源码解析

[ConcurrentHashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/ConcurrentHashMap.java)

### 类属性

使用新的变量 sizeCtl 控制初始化和扩容，sizeCtl 可能存在下面四个值：

* -1，表示正在初始化

* -(1 + nThreads)，表示有 n 个线程正在扩容

* 0，默认值

* 大于 0，初始化或扩容完成后下一次的扩容的阈值

counterCells 数组保存了集合中元素的个数（和 baseCount 协同工作），设计成数组是为了分担多线程同时修改集合元素个数的压力。当某个线程新增一个元素时，只需要在数组中专属（其实是由产生的随机数决定的）的位置（或者 baseCount 上）加一就可以了。

```java
    /**
     * 保存节点的数组，数组的每个位置称为一个桶。在第一次插入节点的时候
     * 懒加载。数组长度总是 2 的幂。
     */
    transient volatile Node<K,V>[] table;

    /**
     * 需要用到的临时 nextTable；只有在扩容时才不为 null。
     */
    private transient volatile Node<K,V>[] nextTable;

    /**
     * 基础计数值，主要在没有竞争时使用，但也可作为 table 初始化竞争的回退。
     * 通过 CAS 方式更新。
     */
    private transient volatile long baseCount;

    /**
     * table 初始化和扩容控制。当为负值时， table 被初始化或扩容：-1 时初始化，
     * 或者为 -（1 + 活跃的扩容线程数）。否则，当 table 为 null 时，保留创建时
     * 使用的初始的 table 大小，默认为 0。初始化之后，保存下一个元素的 count
     * 值，根据该值调整表的大小。
     * 简言之，控制表的初始化和扩容操作。
     * -1 代表 table 正在初始化
     * -N 代表有 N - 1 个线程正在进行扩容操作
     * 初始化数组或扩容完成后，将 sizeCtl 的值设为 0.75 * n
     */
    private transient volatile int sizeCtl;

    /**
     * 节点转移时下一个需要转移的 table 索引。
     * 扩容时用到，初始时为 table.length，表示从索引 0 到 transferIndex 的
     * 节点还未转移。
     */
    private transient volatile int transferIndex;

    /**
     * 自旋锁（通过 CAS 锁定），用于扩容和创建反单元格。
     */
    private transient volatile int cellsBusy;

    /**
     * 保存 Table 中的元素个数。非空时，数组大小为 2 的幂。
     */
    private transient volatile CounterCell[] counterCells;
```

### 内部类

Node 节点是基础节点。TreeNode 继承自 Node，作为树结构的节点。TreeBin 表示一个树结构，TreeNode 作为 TreeBin 的属性而存在。ForwardingNode 在扩容时作为占位节点，表示当前节点已经被移动，此类中包括属性 nextTable，指向新创建的数组。

### 成员函数

#### 添加

函数的最外层是自旋操作，每一次自旋操作分为以下四种情况：

1. 数组没有初始化，使用 initTable 初始化数组。

2. i 位置没有元素，（使用 CAS 方式）直接插入。如果成功则跳出自旋。

3. 当前节点正在转移，线程进入 helpTransfer 帮助转移。

4. 桶内已经有元素了。

对于第四种情况，在执行处理之前，首先使用 synchronized 锁住这个桶。分为链式结构和树形结构两种情况，查找是否已经存在 key 对应的节点，如果存在，替换其 value，如果不存在，在桶内插入新的节点。

```java
    /** put 和 putIfAbsent 的具体实现 */
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        // 不允许 key 或者 value 为 null
        if (key == null || value == null) throw new NullPointerException();
        // 计算 hash 值
        int hash = spread(key.hashCode());
        int binCount = 0;
        // 自旋
        for (Node<K,V>[] tab = table;;) {
            // f：找到的索引处的节点
            // n：table.length
            // i：新节点的索引
            // fh：f.hash
            Node<K,V> f; int n, i, fh;
            // 如果 table 为 null，先初始化
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            // i 位置为 null，直接插入
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                // CAS 方式插入节点，成功则跳出循环
                if (casTabAt(tab, i, null,
                        new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            // 当前节点处于 MOVED 状态，有其他线程正在进行节点转移操作
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            // 执行到这里说明找到的桶已经有元素了
            else {
                V oldVal = null;
                // 对 f 加锁
                synchronized (f) {
                    // 监测 i 位置是否还是 f，如果是 f 才能进行后续操作，否则继续循环
                    if (tabAt(tab, i) == f) {
                        // f.hash >= 0，说明是链式结构
                        // 对 f 开启的链表进行遍历
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                // 如果某个节点 e 的 hash 值与指定的 hash 值相等，则修改
                                // 这个节点的 value，然后跳出遍历循环
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                // 如果遍历到尾节点了还没有找到，则把当前的键值对插入
                                // 到链表尾部
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                            value, null);
                                    break;
                                }
                            }
                        }
                        // 如果节点是 TreeBin 类型的节点，说明该桶内是红黑树，
                        // 调用红黑树节点的 putTreeVal 方法进行插入操作。
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                    value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                // 插入链表操作中已经统计桶中节点个数，此处判断，如果超过阈值，
                // 调用 treeifyBin 将链表转化为红黑树
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        // 调用 addCount 更新元素数量
        addCount(1L, binCount);
        return null;
    }
```

#### 删除

与插入类似，每一次自旋操作分为以下情况：

1. 如果数组不存在或 i 位置不存在任何节点，直接退出自旋。

2. 如果该节点正在扩容，当前线程进入 helpTransfer 帮助扩容。

3. 桶中有元素，（使用 synchronized 锁定之后）在桶内查找。分为链式结构和树形结构。因为已经锁定，所以这一步骤里面的修改操作不需要使用 CAS 方式。

```java
    /**
     * 四个删除/替代方法的支撑函数：将指定 key 对应的 value 替换成指定的
     * value，或者删除节点
     */
    final V replaceNode(Object key, V value, Object cv) {
        int hash = spread(key.hashCode());
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            // 如果 table 不存在或者 i 位置不存在任何节点，直接跳出循环
            if (tab == null || (n = tab.length) == 0 ||
                    (f = tabAt(tab, i = (n - 1) & hash)) == null)
                break;
            // 如果该节点正在被其他线程转移（扩容），则此线程也帮助扩容
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                V oldVal = null;
                boolean validated = false;
                synchronized (f) {
                    // 确认，防止修改完成后其他线程继续修改
                    if (tabAt(tab, i) == f) {
                        // 当前为链表结构
                        if (fh >= 0) {
                            validated = true;
                            // 遍历链表
                            for (Node<K,V> e = f, pred = null;;) {
                                K ek;
                                // 如果找到指定 key 对应的映射
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    V ev = e.val;
                                    // 用于适应多个不同的函数调用 replaceNode 方法
                                    if (cv == null || cv == ev ||
                                            (ev != null && cv.equals(ev))) {
                                        oldVal = ev;
                                        // 如果 value 不为 null，则用指定 value 替换该节点的 value
                                        if (value != null)
                                            e.val = value;
                                        // value 等于 null 且 pred 不为 null，删除找到的节点
                                        else if (pred != null)
                                            pred.next = e.next;
                                        // value 等于 null 且 pred 为 null，删除找到的节点
                                        else
                                            setTabAt(tab, i, e.next);
                                    }
                                    break;
                                }
                                pred = e;
                                if ((e = e.next) == null)
                                    break;
                            }
                        }
                        // 如果 f 所在的桶内是树结构
                        else if (f instanceof TreeBin) {
                            validated = true;
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> r, p;
                            // 从树结构中找到指定节点
                            if ((r = t.root) != null &&
                                    (p = r.findTreeNode(hash, key, null)) != null) {
                                V pv = p.val;
                                if (cv == null || cv == pv ||
                                        (pv != null && cv.equals(pv))) {
                                    oldVal = pv;
                                    if (value != null)
                                        p.val = value;
                                    else if (t.removeTreeNode(p))
                                        setTabAt(tab, i, untreeify(t.first));
                                }
                            }
                        }
                    }
                }
                if (validated) {
                    if (oldVal != null) {
                        // 更新元素数量，数量减一
                        if (value == null)
                            addCount(-1L, -1);
                        return oldVal;
                    }
                    break;
                }
            }
        }
        return null;
    }
```

#### 扩容

**initTable 初始化数组**

此处主要用到 sizeCtl 属性。如果 sizeCtl 小于 0，说明有线程已经在执行初始化了，让出初始化权，否则 CAS 设置 sizeCtl 为 -1，表示当前线程已经开始初始化了。

初始化完成之后把 sizeCtl 设置为当前容量的 0.75 倍，即下一次扩容的阈值。

```java
    /**
     * 初始化 table。
     * 如果 sizeCtl 小于 0，说明别的数组正在进行初始化，则让出初始化权（yeild）
     * 如果 sizeCtl 大于 0，初始化一个大小为 sizeCtl 的数组，等于 0 初始化一个
     * 默认大小（16）的数组。
     * 然后设置 sizeCtl 的值为数组长度的 3/4
     * Initializes table, using the size recorded in sizeCtl.
     */
    private final Node<K,V>[] initTable() {
        Node<K,V>[] tab; int sc;
        // 自旋
        while ((tab = table) == null || tab.length == 0) {
            // 如果 sizeCtl 小于 0，让出初始化权
            if ((sc = sizeCtl) < 0)
                Thread.yield();
            // 否则 CAS 设置 sizeCtl 为 -1，表示当前线程正在初始化
            // CAS 是为了让同时到达此处的线程，只有一个能进入这个代码块执行
            // CAS 成功之后，之后其他的线程会被阻塞在 if ((sc = sizeCtl) < 0) 里面，
            // 不会再继续 CAS 了
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null || tab.length == 0) {
                        // 如果 sc 大于 0，初始容量为 sc，否则初始容量为默认（16）
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        table = tab = nt;
                        // sc = 0.75 * n，即扩容阈值
                        sc = n - (n >>> 2);
                    }
                } finally {
                    // 初始化 sizeCtl 为 sc
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }
```

**transfer 转移节点**

其他线程进入 helpTransfer 协助扩容。如果正在转移（节点为 ForwordingNode，nextTable 不为 null），则进入 transfer 方法帮助转移。

将数组扩容成原来的两倍。第一个开始扩容的线程创造一个容量为原容量两倍的新数组。

每一个线程完成一段数组中节点的转移。

执行转移操作时主要进行以下判断：

* 如果当前线程已经完成转移，sizeCtl 减一后直接返回。最后一个线程完成扩容，设置好 table 等变量后，扩容结束。

* 如果 i 位置节点为 null，将其设为 fwd，提醒其他线程该位已经处理过了。

* 如果 i 位置已经处理过了，继续往后处理其他位置。

* 否则处理 i 位置，处理之前使用 synchronized 上锁。将原来的链表（树）分成两个链表（树），分别放在原位置和新位置上。具体实现上，使用了数组容量为 2 的幂这一点来简化操作。使用了 lastRun 来简化操作。

```java
    /**
     * 如果 resize 操作正在进行，帮助转移节点 f。
     */
    final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab; int sc;
        // 如果 tab 不为 null，传进来的节点是 ForwardingNode，且 ForwardingNode
        // 的下一个 tab 不为 null
        if (tab != null && (f instanceof ForwardingNode) &&
                (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length);
            while (nextTab == nextTable && table == tab &&
                    (sc = sizeCtl) < 0) {
                // 不需要帮助转移，跳出循环
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                        sc == rs + MAX_RESIZERS || transferIndex <= 0)
                    break;
                // CAS 更新帮助转移的线程数（+1）
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
            return nextTab;
        }
        return table;
    }
    
    /**
     * 移动和/或复制桶里的节点到新的 table 里。
     */
    private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
        int n = tab.length, stride;
        // 计算步长，表示一个线程处理的数组长度，用来控制对 CPU 的使用，
        // stride = tab.length/(NCPU*8)，最小为 16
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
            stride = MIN_TRANSFER_STRIDE; // subdivide range
        // 如果指定的 nextTab 为空（第一个线程开始扩容），初始化 nextTable
        // 其他线程进来帮忙时，不再创建新的 table。
        if (nextTab == null) {            // initiating
            try {
                // 创建一个相当于当前 table 两倍容量的数组，作为新的 table
                @SuppressWarnings("unchecked")
                Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // try to cope with OOME
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            transferIndex = n;
        }
        int nextn = nextTab.length;
        // fwd 是标志节点。当一个节点为空或者被转移之后，就设置为 fwd 节点
        // 表示这个桶已经处理过了
        ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
        // 是否继续向前查找
        boolean advance = true;
        // 在完成之前重新扫描一遍数组，确认已经完成。
        boolean finishing = false; // to ensure sweep before committing nextTab
        // 自旋移动每个节点，从 transferIndex 开始移动 stride 个节点到新的
        // table
        // i 表示当前处理的节点索引，bound 表示需要处理节点的索引边界
        for (int i = 0, bound = 0;;) {
            Node<K,V> f; int fh;
            while (advance) {
                int nextIndex, nextBound;
                // 已经完成，不再往前遍历了
                // bound 是所有线程处理区间的最低点
                if (--i >= bound || finishing)
                    advance = false;
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                // 更新 transferIndex
                // 当前线程处理的桶区间为（nextBound, nextIndex）
                else if (U.compareAndSwapInt
                        (this, TRANSFERINDEX, nextIndex,
                                nextBound = (nextIndex > stride ?
                                        nextIndex - stride : 0))) {
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
            if (i < 0 || i >= n || i + n >= nextn) {
                int sc;
                // 已经完成转移，设置 table 为新的 table，更新 sizeCtl 为扩容后的
                // 0.75 倍（原容量的 1.5 倍）并返回
                if (finishing) {
                    nextTable = null;
                    table = nextTab;
                    sizeCtl = (n << 1) - (n >>> 1);
                    return;
                }
                // 当前线程 return 之后可能还有其他线程正在转移
                // 每个线程完成扩容操作后对 sizeCtl 减一
                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                    // sc 初值为 （rs << RESIZE_STAMP_SHIFT) + 2）
                    // 如果还有其他线程正在操作，直接返回，不改变 finishing，否则改变
                    // i, finishing 和 advance，再检查一遍 table
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                        return;
                    finishing = advance = true;
                    i = n; // recheck before commit
                }
            }
            // 如果 i 位置节点为 null，将其设为 fwd，提醒其他线程该位已经处理过了
            else if ((f = tabAt(tab, i)) == null)
                advance = casTabAt(tab, i, null, fwd);
            // 该位置已经处理过了，继续往下
            else if ((fh = f.hash) == MOVED)
                advance = true; // already processed
            else {
                // 处理当前拿到的节点，此处要上锁
                synchronized (f) {
                    // 确认 i 位置仍然是 f，防止其他线程拿到锁进入修改
                    if (tabAt(tab, i) == f) {
                        // ln 保留在原位置，hn 应该移到i + n 位置
                        Node<K,V> ln, hn;
                        // 如果当前为链表节点
                        if (fh >= 0) {
                            // n 为原 table 长度，且为 2 的幂，任何数与 n 进行 & 操作后
                            // 只可能是 0 或者 n。
                            // 根据这个把链表节点分成两类，为 0 说明原来的索引小于 n，
                            // 则位置保持不变，为 n 说明已经超过了原来的 n，新的位置
                            // 应该是 n + i（n 的某一位为 1，如果需要移动，该 bit 位也
                            // 必定为 1，不然将会待在原桶，位置不变）
                            int runBit = fh & n;
                            Node<K,V> lastRun = f;

                            for (Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                // runBit 一直在变化
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            // 上面的循环执行完之后，lastRun 及其之后的元素在同一组。
                            // 且 runBit 就是 last 的标识
                            // 如果 runBit 等于 0，则 lastRun 及之后的元素都在原位置
                            // 否则，lastRun 及之后的元素都在新的位置
                            if (runBit == 0) {
                                ln = lastRun;
                                hn = null;
                            }
                            else {
                                hn = lastRun;
                                ln = null;
                            }
                            // 把 f 链表分成两个链表。
                            for (Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash; K pk = p.key; V pv = p.val;
                                // 原位置
                                if ((ph & n) == 0)
                                    ln = new Node<K,V>(ph, pk, pv, ln);
                                // i + n 位置
                                else
                                    hn = new Node<K,V>(ph, pk, pv, hn);
                            }
                            // 上述循环完成转移之后桶内的顺序并不一定是原来的顺序了
                            // 原因就是 lastRun

                            // 在 nextTab 的 i 位置插入一个链表
                            setTabAt(nextTab, i, ln);
                            // nextTab 的 i + n 位置插入一个链表
                            setTabAt(nextTab, i + n, hn);
                            // table 的 i 位置插入 fwd 节点，表示已经处理过了
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
                        // 当前为树节点
                        else if (f instanceof TreeBin) {
                            // f 转为根节点
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            // 低位节点
                            TreeNode<K,V> lo = null, loTail = null;
                            // 高位节点
                            TreeNode<K,V> hi = null, hiTail = null;
                            int lc = 0, hc = 0;
                            // 从首个节点向后遍历
                            for (Node<K,V> e = t.first; e != null; e = e.next) {
                                int h = e.hash;
                                // 构建新的树节点
                                TreeNode<K,V> p = new TreeNode<K,V>
                                        (h, e.key, e.val, null, null);
                                // 应该放在原位置
                                if ((h & n) == 0) {
                                    if ((p.prev = loTail) == null)
                                        lo = p;
                                    else
                                        loTail.next = p;
                                    loTail = p;
                                    ++lc;
                                }
                                // 应该放在 n + i 位置
                                else {
                                    if ((p.prev = hiTail) == null)
                                        hi = p;
                                    else
                                        hiTail.next = p;
                                    hiTail = p;
                                    ++hc;
                                }
                            }
                            // 扩容后不再需要 tree 结构，转变为链表结构
                            // 创建 TreeBin 时，其构造函数会把双向链表结构转化成树结构
                            ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                                    (hc != 0) ? new TreeBin<K,V>(lo) : t;
                            hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                                    (lc != 0) ? new TreeBin<K,V>(hi) : t;
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
                    }
                }
            }
        }
    }
```

**addCount 增加计数**

计算元素个数同时使用到了 counterCells 和 baseCount 两个变量。增加元素个数时，有可能将新的计数加入到 baseCount，有可能加入到 counterCells。counterCells 数组保存每个线程添加的计数，每个线程操作的数组位置不一样。

所以计算元素个数时，实际上是把 baseCount 和 counterCells 里所有位置的值相加。

```java
    /**
     * map 中节点个数的增加或减少。如果 table 太小，而且还没有开始扩容，则开始
     * 扩容。如果已经开始扩容，调用此方法的线程帮助扩容。在扩容之后重新检查
     * 占用情况，看看是否还需要扩容，因为可能又添加了新的内容。
     * 根据参数 check 决定是否检查扩容（其实每次添加节点都会检查）
     *
     * @param x the count to add
     * @param check if < 0, don't check resize, if <= 1 only check if uncontended
     */
    private final void addCount(long x, int check) {
        CounterCell[] as; long b, s;
        // 如果 counterCells 不为 null，直接进入 if 块，使用 counterCells；
        // 否则尝试 CAS 修改 baseCount，如果成功了，就不管 counterCells 了，
        // 失败了也进入 if 块
        // 由此可以看出，计算元素个数其实同时使用到了 counterCells 和 baseCount 两个变量
        // 说明，有 counterCells 的时候优先使用 counterCells。
        if ((as = counterCells) != null ||
                !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
            CounterCell a; long v; int m;
            boolean uncontended = true;
            // 如果 counterCells 不为 null，其长度不为 0，线程通过寻址找到 as 数组中
            // 属于它的 CounterCell 却为 null
            // 尝试赋值，赋值失败（说明出现并发）执行 fullAddCount 方法
            // ThreadLocalRandom 是一个线程私有的随机数生成器，每个线程的 probe
            // 都是不同的，可以认为每个线程的 probe 就是它在 CounterCell 数组中
            // 的 hash code
            if (as == null || (m = as.length - 1) < 0 ||
                    (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
                    !(uncontended =
                            U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
                // 在有竞争的时候使用 fullAddCount 计算更新元素数
                fullAddCount(x, uncontended);
                return;
            }
            if (check <= 1)
                return;
            // sumCount 计算元素总数
            s = sumCount();
        }
        // 检查扩容。putVal 方法中默认需要检查。
        if (check >= 0) {
            Node<K,V>[] tab, nt; int n, sc;
            // 如果 map 中的节点数达到 sizeCtl（达到扩容阈值），需要扩容。如果
            // table 不为 null 且 table 的长度小于最大值限制，则可以扩容。
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                    (n = tab.length) < MAXIMUM_CAPACITY) {
                // table 长度的标识
                int rs = resizeStamp(n);
                // 要么在初始化，要么正在扩容
                if (sc < 0) {
                    // 满足以下条件之一直接退出循环（通过检验变量是否变化）
                    // 1. sc 的低 16 位不等于标识，说明 sizeCtl 变化了
                    // 2. sc == 标识符加 1（扩容结束了，不再有线程进行扩容）（默认
                    // 第一个线程设置 sc ==rs 左移 16 位 + 2，当第一个线程结束扩容了，
                    // 就会将 sc 减一。这个时候，sc 就等于 rs + 1）
                    // 3. sc == 标识符 + 65535（帮助线程已经达到最大）
                    // 4. nextTable == null，扩容结束
                    // 5. transferIndex <= 0，转移状态变化了
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                            sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                            transferIndex <= 0)
                        break;
                    // sizeCtl 加一，表示帮助扩容的线程加一，然后进行扩容。
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        transfer(tab, nt);
                }
                // 没有在扩容，将 sizeCtl 更新，赋值为标识符左移 16 位（此时为负数）
                // 然后加 2，表示已经有一个线程开始扩容了，然后进行扩容。
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                        (rs << RESIZE_STAMP_SHIFT) + 2))
                    transfer(tab, null);
                // 更新 s
                s = sumCount();
            }
        }
    }
    
    // 初始化 CounterCells 和更新计数
    private final void fullAddCount(long x, boolean wasUncontended) {
        int h;
        // 为 0 表示该线程的 ThreadLocalRandom 还没有初始化
        if ((h = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();      // force initialization
            h = ThreadLocalRandom.getProbe();
            // 非竞争
            wasUncontended = true;
        }
        // 冲突标志
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            CounterCell[] as; CounterCell a; int n; long v;
            // counterCells 已经初始化了
            if ((as = counterCells) != null && (n = as.length) > 0) {
                // h 位置的 CounterCell 还没有初始化。
                if ((a = as[(n - 1) & h]) == null) {
                    // 锁空闲（0 表示空闲，1 表示已经被获取）
                    if (cellsBusy == 0) {            // Try to attach new Cell
                        // 创建新的 CounterCell，将 x 保存在此 CounterCell 中
                        CounterCell r = new CounterCell(x); // Optimistic create
                        // 获取锁（CAS 将 CELLSBUSY 的值变成 1）
                        if (cellsBusy == 0 &&
                                U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                            boolean created = false;
                            // 尝试将创建的 CounterCell 放入 counterCells 数组中，如果
                            // 成功将 created 的值变为 true。
                            try {               // Recheck under lock
                                CounterCell[] rs; int m, j;
                                if ((rs = counterCells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                // 释放锁
                                cellsBusy = 0;
                            }
                            // 操作成功跳出循环
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    // 没成功
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                // 数组中找到为位置非 null，则 CAS 更新它的 value，然后跳出循环
                else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
                    break;
                // 上面更新失败到这里检查 counterCells 数组是否已经扩容，是否达到上限
                // 并且再寻址一次（重新循环一次会重新获取 probe）
                else if (counterCells != as || n >= NCPU)
                    collide = false;            // At max size or stale
                // 如果进入这个 else if 块再让线程循环一次，重新寻址一次，如果还有
                // 冲突说明数组太小竞争太激烈，需要扩容
                else if (!collide)
                    collide = true;
                // 扩容前，先获取锁
                else if (cellsBusy == 0 &&
                        U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                    try {
                        // 扩容前的检查，因为数组可能已经被其他线程扩容了
                        if (counterCells == as) {// Expand table unless stale
                            CounterCell[] rs = new CounterCell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            counterCells = rs;
                        }
                    } finally {
                        // 释放锁
                        cellsBusy = 0;
                    }
                    // 重新循环
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = ThreadLocalRandom.advanceProbe(h);
            }
            // counterCells 还没有初始化，获取锁 CELLSBUSY
            else if (cellsBusy == 0 && counterCells == as &&
                    U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                // 标志是否完成初始化
                boolean init = false;
                try {                           // Initialize table
                    if (counterCells == as) {
                        // 初始大小为 2
                        CounterCell[] rs = new CounterCell[2];
                        // 创建 CounterCell 对象
                        rs[h & 1] = new CounterCell(x);
                        counterCells = rs;
                        // 初始化成功
                        init = true;
                    }
                } finally {
                    // 释放锁
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            // 获取锁失败或者 counterCells 被其他线程扩容，尝试 CAS 更新 baseCount，
            // 成功则跳出循环
            else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
                break;                          // Fall back on using base
        }
    }
```

### 其他

#### Thread 中的 join, sleep 和 yeild

* 非静态方法join()让一个线程等待另外一个线程完成才继续执行。如果线程 A 执行体中调用 B 线程的 join() 方法，则 A 线程将会被阻塞，直到 B 线程执行完为止，A 才能得以继续执行。

* sleep() 让当前正在执行的线程先暂停一定的时间，并进入阻塞状态。在其睡眠的时间段内，该线程由于不是处于就绪状态，因此不会得到执行的机会。

* 一个线程执行了 yield() 方法后，就会进入 Runnable（就绪状态），（不同于 sleep() 和 join() 方法，因为这两个方法是使线程进入阻塞状态），线程让步，不会释放锁。

### 引用

* [JUC源码分析-集合篇（一）：ConcurrentHashMap](https://www.jianshu.com/p/0fb89aefac66)

* [ConcurrentHashMap 源码学习](https://suiyia.github.io/2019/10/16/ConcurrentHashMap-%E6%BA%90%E7%A0%81%E5%AD%A6%E4%B9%A0/)

* [ConcurrentHashMap源码分析(1.8)](https://www.cnblogs.com/zerotomax/p/8687425.html#go0)

* [JUC源码解析-ConcurrentHashMap1.8](https://blog.csdn.net/sinat_34976604/article/details/80971620)

* [并发编程——ConcurrentHashMap#addCount() 分析](https://www.jianshu.com/p/749d1b8db066)