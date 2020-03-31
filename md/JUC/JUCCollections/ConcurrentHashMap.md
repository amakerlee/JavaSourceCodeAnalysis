## ConcurrentHashMap

ConcurrentHashMap 是在 HashMap 的基础上进行改进的线程安全 Map 类，在开始 ConcurrentHashMap 前，务必提前了解 HashMap 的原理和基本思想。

ConcurrentHashMap 中使用 synchronized 进行锁定，主要用在 put 操作和扩容操作中，而且每一次锁住的只有当前的桶（数组的单个槽位）。

ConcurrentHashMap 和 Hashtable 对象的 key、value 值不可为 null，而 HashMap 对象的 key、value 值可为 null。

### 完整源码解析

[ConcurrentHashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/ConcurrentHashMap.java)

### 类属性

使用新的变量 sizeCtl 控制初始化和扩容，sizeCtl 可能存在下面四个值：

* -1，表示正在初始化

* -(1 + nThreads)，表示有 n 个线程正在扩容

* 0，默认值

* 大于 0，初始化或扩容完成后下一次的扩容的阈值

counterCells 数组保存了集合中元素的个数（和 baseCount 协同工作），设计成数组是为了分担多线程同时修改集合元素个数的压力。当某个线程新增一个元素时，只需要在数组中专属（其实是由产生的随机数决定的）的位置（或者 baseCount 上）加一就可以了。LongAdder 也采用了这种思想。

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
     * sizeCtl = -1 表示 table 正在初始化
     * sizeCtl = 0 默认值
     * sizeCtl > 0 下次扩容的阈值
     * sizeCtl = (resizeStamp << 16) + (1 + nThreads)，表示正在进行扩容，高位存储扩容邮戳，低位存储扩容线程数加 1；
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

Node 节点是基础节点。TreeNode 继承自 Node，表示树的节点。TreeBin 表示一个树结构，TreeNode 作为 TreeBin 的属性而存在。ForwardingNode 在扩容时作为占位节点，表示当前节点已经被移动，ForwardingNode 类中包括属性 nextTable，指向新创建的数组。

### 成员函数

#### 添加

函数的最外层是自旋操作，每一次自旋对以下四种情况分别进行处理：

1. 如果 table 数组还没有初始化，调用 initTable 初始化数组。

2. 如果 i 位置没有元素，（使用 CAS 方式）直接插入。如果成功则跳出自旋。

3. 当前节点正在转移，线程进入 helpTransfer 帮助转移。从 helpTransfer 出来之后再继续自旋。

4. 桶内已经有元素了。首先使用 synchronized 锁住这个桶，然后进行插入或更新操作。桶内结构分为链式结构和树形结构两种情况，查找是否已经存在 key 对应的节点，如果存在，替换其 value，如果不存在，在桶内插入新的节点。

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

2. 如果该节点正在扩容，当前线程进入 helpTransfer 帮助扩容。从 helpTransfer 出来之后再继续自旋。

3. 桶中有元素，（使用 synchronized 锁定之后）在桶内查找。synchronized 锁定的是整个桶，这一步骤里面的修改操作不需要 CAS。

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
            // 如果 sizeCtl 小于 0，表示已经在初始化了。
            if ((sc = sizeCtl) < 0)
                Thread.yield();
            // 否则 CAS 设置 sizeCtl 为 -1，表示当前线程正在初始化
            // CAS 是为了让同时到达此处的线程，只有一个能进入这个代码块执行
            // CAS 成功之后，之后其他的线程会被因为 if ((sc = sizeCtl) < 0) 让出时间片，
            // 让初始化线程尽快完成初始化工作。
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

**helpTransfer**

任何调用 helpTransfer 的线程都会进入 helpTransfer 协助扩容。

如果正在转移（节点为 ForwordingNode，nextTable 不为 null）且需要帮忙，则进入 transfer 方法帮助转移。

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
```

**transfer 转移节点**

将数组扩容成原来的两倍。第一个开始扩容的线程创造一个容量为原容量两倍的新数组。

每一个线程完成一段数组中节点的转移。用 stride 控制每个线程每一次需要处理的数组长度，用 transferIndex 记录已经处理过或者有线程正在处理的最小槽索引。

自旋完成扩容操作。

每一次自旋需要判断这一次处理的是哪一个位置，也就是 i 的位置。比 transferIndex 大的索引位置已经分配给之前的线程，当前线程从 transferIndex 位置开始处理，从后往前，处理的索引范围是 transferIndex - stride 到 transferIndex。

处理完了当前位置 i 之后，继续处理 --i。如果当前范围内的所有位置都已经处理完了，根据 transferIndex 从 table 又分出一块 stride 给当前线程处理。这一流程是在 while (advance) {...} 这段代码中完成的。

确定了应该处理哪一个位置之后，就可以执行转移操作了。

执行转移操作时主要有以下几种情况：

* 如果当前线程已经完成转移，sizeCtl 减一后直接返回。最后一个线程完成扩容，设置 finishing 为 true 表示扩容结束。线程设置好 table、sizeCtl 变量之后，扩容结束。

* 如果 i 位置节点为 null，将其设为 fwd，提醒其他线程该位已经处理过了。

* 如果 i 位置已经处理过了，继续往后处理其他位置。

* 处理 i 位置。同样地，处理之前使用 synchronized 上锁。

最后一种情况又可以根据桶中是链式结构还是树状结构分成两种情况。如果是链式结构，将原来的链表（树）分成两个链表（树），分别放在原位置和新位置上。具体实现上，使用了数组容量为 2 的幂这一点来简化操作（只判断标志位），使用了 lastRun 来提高效率；如果是树状结构，也分成两部分，放在不同的位置上。

```java
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
        // advance 指的是做完了一个位置的迁移工作，可以准备做下一个位置的了
        boolean advance = true;
        // 在完成之前重新扫描一遍数组，确认已经完成。
        boolean finishing = false; // to ensure sweep before committing nextTab
        // 自旋移动每个节点，从 transferIndex 开始移动 stride 个槽的节点到新的
        // table
        // i 表示当前处理的节点索引，bound 表示需要处理节点的索引边界
        for (int i = 0, bound = 0;;) {
            Node<K,V> f; int fh;
            while (advance) {
                int nextIndex, nextBound;
                // 首先执行 i = i - 1，如果 i 大于 bound，说明还在当前 stride 范围内
                // nextIndex、nextBound、transferIndex 等都不需要改变
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

## 计数

**addCount**

计算元素个数同时使用到了 counterCells 和 baseCount 两个变量。

addCount 分成两个部分，上半部分是更新计数，下半部分是根据需要扩容。

只有从未出现过并发冲突的时候，baseCount 才会使用到，也就是直接在 baseCount 上面更新计数。一旦出现了并发冲突，之后所有的操作基本都只针对 counterCells。（fullAddCount 中如果在扩容，也会用到 baseCount）

在 counterCells 还没有初始化或者将要操作的槽还没有初始化或者槽中出现了竞争的时候，调用 fullAddCount 完成更新计数。

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
        // 计算元素个数其实使用了 counterCells 和 baseCount 两个变量
        // 如果 counterCells 为 null，说明之前一直没有出现过冲突，直接将值累加到 baseCount 上
        // 否则尝试更新 counterCells[i] 的值，如果更新失败，可能涉及 counterCells 的扩容，调用 fullAddCount。
        // 只有从未出现过并发冲突的时候，baseCount 才会使用到，一旦出现了并发冲突，之后所有的操作基本都只针对 CounterCell。（fullAddCount 中如果在扩容，也会用到 baseCount）
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
        // 检查是否扩容。putVal 方法中默认需要检查。
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
                    // 5. transferIndex <= 0，不需要线程加入扩容了
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
```

**fullAddCount**

在 fullAddCount 中的自旋分成以下三种情况：

* counterCells 已经初始化。此种情况又可以根据以下条件分别讨论：
  
  - 如果 counterCells 中 h 位置的 CounterCell 还没有初始化，则获取 CELLSBUSY 锁，然后创建 CounterCell 对象并初始化，操作成功则跳出自旋。
  - 如果 counterCells 中 h 位置的 CounterCell 已经初始化了，尝试 CAS 更新计数，操作成功则跳出自旋。
  - 如果 counterCells 中 h 位置竞争太激烈且还没有达到 CPU 的上限，则先扩容，扩容后再继续自旋。

* counterCells 还没有初始化。首先获取 CELLSBUSY 锁，然后创建 CounterCell 数组，初始大小为 2。初始化成功之后就释放锁。

* counterCells 正在初始化中。尝试更新 baseCount，成功则跳出自旋。

```java
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
        // 自旋
        for (;;) {
            CounterCell[] as; CounterCell a; int n; long v;
            // counterCells 已经初始化了
            if ((as = counterCells) != null && (n = as.length) > 0) {
                // h 位置的 CounterCell 还没有初始化。
                if ((a = as[(n - 1) & h]) == null) {
                    // 锁空闲（0 表示空闲，1 表示已经被获取），没有正在扩容
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
                // wasUncontended 表示前一次 CAS 更新 cell 单元是否成功
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                // 数组中找到位置非 null，则 CAS 更新它的 value，然后跳出循环
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

**sumCount**

sumCount 计算集合中的元素个数，把 baseCount 和 counterCells 每个槽中所有的值加起来就是最终的结果。

```java
    // 计算所有 counterCells 的元素和（节点总数）
    // 不同的线程操作的是不同的位置，最后把所有位置的和求出来，就是此 Map 的节点数
    final long sumCount() {
        CounterCell[] as = counterCells; CounterCell a;
        long sum = baseCount;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }
```

### ConcurrentHashMap 使用注意

在超短的时间内多个线程分别频繁地添加和删除键值对，由于线程的调度和等待，无法保证添加/删除操作的先后顺序。这一点在编写程序的时候需要注意。

考虑下面的程序。两个线程添加删除同一个键值对，虽然显式地先添加后删除，但是并非每一次测试的结果都符合预期，即 Map 不包含指定键值对。

如果使用阻塞队列，那么按照顺序执行的概率将要大一些，因为多线程完全按照串行顺序一个一个执行。例如 LinkedBlockingQueue，所有的操作都是先加锁，完成后才释放，保证了所有流程按照线性顺序执行。而 ConcurrentHashMap 中的 CAS 通常在某个步骤的最后才执行，除了 CAS 之外其他情况都是异步操作。

测试程序的结果显示，N = 100, 1000 时，ConcurrentHashMap 得到的结果都几乎达不到 100%。

虽然这不是属于“线程安全”范畴内的问题，但是在程序编写的要求较严苛时，将其考虑在内是有必要的。

```java
public class test {
    public static void main(String[] args) {
        final int nThreads = 2;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
        final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
//        final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(10);
        Runnable insertTask = new Runnable() {
            @Override
            public void run() {
                map.put("test", "test");
//                queue.offer("test");
            }
        };
        Runnable deleteTask = new Runnable() {
            @Override
            public void run() {
                map.remove("test");
//                queue.poll();
            }
        };
        final int N = 1000;
        int numOfR = 0;
        for (int i = 0; i < N; i++) {
            executor.execute(insertTask);
            executor.execute(deleteTask);
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
            boolean ans = !map.containsKey("test");
//            boolean ans = queue.isEmpty();
//            queue.clear();
            System.out.println(ans);
            if(ans) {
                numOfR++;
            }
            int activeCount = executor.getActiveCount();
            if (activeCount != 0)
                throw new UnsupportedOperationException("线程执行时间太短");
        }
        executor.shutdown();
        final NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setMaximumFractionDigits(2);
        System.out.println("" + (double)numOfR / N * 100  + " %");
    }
}
```

### Thread 中的 join, sleep 和 yeild

* 非静态方法join()让一个线程等待另外一个线程完成才继续执行。如果线程 A 执行体中调用 B 线程的 join() 方法，则 A 线程将会被阻塞，直到 B 线程执行完为止，A 才能得以继续执行。

* sleep() 让当前正在执行的线程先暂停一定的时间，并进入阻塞状态。在其睡眠的时间段内，该线程由于不是处于就绪状态，因此不会得到执行的机会。

* 一个线程执行了 yield() 方法后，就会进入 Runnable（就绪状态），（不同于 sleep() 和 join() 方法，因为这两个方法是使线程进入阻塞状态），线程让步，不会释放锁。

### 为什么 ConcurrentHashMap 的 key 和 value 不允许为 null

1. HashMap 的 key 和 value 允许为 null 是设计上的失误。

2. ConcurrentHashMap 的 key 不可以为 null，之所以设计成这样是因为写这个集合的 Doug Lee 讨厌 null。

3. 严格来说， HashMap 和 ConcurrentHashMap 的 value 都应该不允许为 null，因为会产生二义性的问题。调用 get(key) 返回的值为 null 时，调用者无法判断是由于键值对不存在还是由于本身 key 对应的 value 就为 null。在 HashMap 中虽然存在二义性，但是调用者可以自己结合 containsKey 方法避免这一问题（在单线程的环境下）。而对于 ConcurrentHashMap，如果允许 value 为 null，在调用端是无法避免二义性的：如果有 A、B 两个线程，A 调用 get(key) 返回 null，接下来使用 containsKey(key) 判断键值对是否存在，如果此时线程 B 插入键值对 <key, null>，那么线程 A 永远无法判断之前出现了什么情况。

### 引用

* [JUC源码分析-集合篇（一）：ConcurrentHashMap](https://www.jianshu.com/p/0fb89aefac66)

* [ConcurrentHashMap 源码学习](https://suiyia.github.io/2019/10/16/ConcurrentHashMap-%E6%BA%90%E7%A0%81%E5%AD%A6%E4%B9%A0/)

* [ConcurrentHashMap源码分析(1.8)](https://www.cnblogs.com/zerotomax/p/8687425.html#go0)

* [JUC源码解析-ConcurrentHashMap1.8](https://blog.csdn.net/sinat_34976604/article/details/80971620)

* [并发编程——ConcurrentHashMap#addCount() 分析](https://www.jianshu.com/p/749d1b8db066)

* [Java多线程进阶（十七）—— J.U.C之atomic框架：LongAdder](https://segmentfault.com/a/1190000015865714)