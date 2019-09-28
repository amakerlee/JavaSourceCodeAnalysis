### HashMap

***
> 继承结构及完整源码解析

[Map](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Map.java) | [AbstractMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractMap.java) | [HashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/HashMap.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/HashMap.png" width=50% />

 ***
 > 静态常量

```java
    /**
     * 默认初始容量，必须是 2 的幂。
     * 此处为 16
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * 最大容量，如果任何构造函数中指定了一个更大的初始化容量，将会被
     *  MAXIMUM_CAPACITY 取代。
     *  此参数必须是 2 的幂，且小于等于 1 << 30。
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 默认的加载因子。
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * 将链表转化为红黑树的临界值。把一个元素添加到至少有
     * TREEIFY_THRESHOLD 个节点的桶里时，桶中的链表将被转化成
     * 树形结构。此变量最小为 8。
     */
    static final int TREEIFY_THRESHOLD = 8;

    /**
     * 在调整大小时，把树结构恢复成链表时的桶大小临界值。此变量应该小于
     * TREEIFY_THRESHOLD，最大为 6。
     */
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * 当 table 数组大于此容量是，桶才可能被转化成树形结构的。
     * （在桶里面节点数太多时会调整大小。）容量应该至少为
     * 4 * TREEIFY_THRESHOLD 来避免和树形结构化之间的冲突。
     */
    static final int MIN_TREEIFY_CAPACITY = 64;
```

***
> 类属性

table 用来作为 HashMap 中桶数组，数组的每个槽作为一个桶，用来容纳 hash 值相同的元素。HashMap 需要进行 resize 操作的阈值（threshold）计算方法为：容量乘负载因子（loadFactor * capacity）

```java
    /**
     * 表，第一次使用时初始化，根据需要调整大小。分配空间时，其长度总是
     * 2 的幂。（在某些操作中，允许长度为 0，以允许当前不需要的引导机制。）
     */
    transient Node<K,V>[] table;

    /**
     * 存储 entrySet。在 AbstractMap 字段中使用 keySet() 和 value()
     */
    transient Set<Map.Entry<K,V>> entrySet;

    /**
     * 此 map 中映射的数量
     */
    transient int size;

    transient int modCount;

    /**
     * 扩容的临界值（capacity * load factor）。超过这个值将扩容。
     * The next size value at which to resize (capacity * load factor).
     *
     * @serial
     */
    // 如果 table 数组没有分配空间，此字段会保存初始数组容量，或者用
    // 0 代表 DEFAULT_INITIAL_CAPACITY
    int threshold;

    /**
     * 加载因子。
     *
     * @serial
     */
    final float loadFactor;
```

***
> 内部类

Node 类是 HasMap 最简单的节点类型，用于桶内数据结构为链表时的情景。当桶内的数据结构转化成红黑树时，节点也转化成红黑树的节点类型 TreeNode。TreeNode 节点中将会涉及到红黑树的一系列相对复杂的操作，例如变色，旋转等。

```java
    /**
     * 基本的 hash 节点类型，用于大多数 entry。
     */
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        // 指向下一个节点
        Node<K,V> next;

        // 构造函数
        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        // key 的 hash 值和 value 的 hash 值的异或
        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        // 设置为指定 value
        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        // 判断指定对象和此 Node 是否相等
        public final boolean equals(Object o) {
            if (o == this)
                return true;
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                if (Objects.equals(key, e.getKey()) &&
                        Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }
```

***
> 成员函数

**hash()**

hash 函数重新计算对象的 hash 值，用于计算定位元素在数组中的位置，将 hashCode 的高 1 6位与 hashCode 进行异或运算，是为了在 table 的 length 较小的时候，让高位也参与运算，并且不会有太大的开销。

```java
    /**
     * 计算 key 的 hash 值，计算key.hashCode()，并将（XORs）的散列值
     * 由高向低扩展（将 key 的 hash 值的高 16 位和低 16 位XOR）。
     * 由于该表使用了2的幂掩码，因此仅在当前掩码之上以位为单位变化的
     * 散列集总是会发生冲突。（已知的例子包括在小表中保存连续整数的
     * 浮点 key。）因此，我们应用一个转换，将更高位的影响向下传播。
     * 位扩展的速度、实用性和质量之间存在权衡。因为许多常见的散列集
     * 已经合理分布（所以不会受益于传播），而且我们用树来处理桶里大型
     * 的碰撞，我们只是异或一些上位的 bits，以最便宜的方式来减少系统
     * 的损失，并将最高位 bits 的影响纳入考虑，否则由于表的范围，它们
     * 永远不会在计算索引中被使用。
     */
    static final int hash(Object key) {
        int h;
        // 首先取 key 的 hash 值，然后将 key 的高 16 位和低 16 位异或
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }
```

**比较**

在红黑树的数据结构中，hash 值决定了指定节点在当前节点的左子树中还是右子树中。当 hash 值相等的时候，需要约定一个统一的规范来确定节点的相对位置。

```java
    /* 当 put 一个新元素时，如果该元素键的 hash 值小于当前节点的 hash 值
     * 的时候，就会作为当前节点的左节点；hash 值大于当前节点 hash 值
     * 的时候作为当前节点的右节点。在 hash 值相同的时候，会先尝试看
     * 是否能够通过 Comparable 进行比较两个对象（当前节点的键对象和
     * 新元素的键对象），要想看看是否能基于 Comparable 进行比较的话，
     * 首先要看该元素键是否实现了 Comparable 接口，此时就需要用到
     * comparableClassFor 方法来获取该元素键的 Class，然后再通过
     * compareComparables 方法来比较两个对象的大小。
     *
     */

    /**
     * 返回 x 对象的类别，如果它实现了 Comparable<C> 接口的话，否则
     * 返回 null。
     */
    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c; Type[] ts, as; Type t; ParameterizedType p;
            if ((c = x.getClass()) == String.class) // bypass checks
                // 返回 String.class，因为 String 实现了 Comparable 接口。
                return c;
            // 如果 c 不是字符串类，获取 c 直接实现的接口（如果是泛型接口
            // 则附带泛型信息）
            if ((ts = c.getGenericInterfaces()) != null) {
                // 遍历接口数组
                // 检查 x对象的类是否实现了 Comparable<x 的 class>
                for (int i = 0; i < ts.length; ++i) {
                    // 如果当前接口 t 是个泛型接口
                    // 如果该泛型接口 t 的原始类型 p 是 Comparable 接口
                    // 如果该 Comparable 接口 p 只定义了一个泛型参数
                    // 如果这一个泛型参数的类型就是 c，那么返回 c
                    if (((t = ts[i]) instanceof ParameterizedType) &&
                            ((p = (ParameterizedType)t).getRawType() ==
                                    Comparable.class) &&
                            (as = p.getActualTypeArguments()) != null &&
                            as.length == 1 && as[0] == c) // type arg is c
                        return c;
                }
            }
        }
        return null;
    }

    /**
     * 如果 x 对象的类型和 kc （k 的筛选可比类）匹配，返回 k.compareTo(x)
     * 的比较结果。如果 x 为空，或者其所属的类不是 kc，返回 0。
     */
    @SuppressWarnings({"rawtypes","unchecked"}) // for cast to Comparable
    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 :
                ((Comparable)k).compareTo(x));
    }
```

**tableSizeFor**

对于指定的参数 cap，返回比它大的最小的 2 的幂。此函数与 ArrayDeque 中计算容量的 calculateSize 函数相同。

```java
    /**
     * 对于给定的目标容量，返回比它大的最小的 2 的幂。
     */
    static final int tableSizeFor(int cap) {
        // 减一是为了防止 cap 已经是 2 的幂了。如果 n 已经是 2 的幂，那么
        // 执行完成后返回的值将是 cap 的两倍
        int n = cap - 1;
        // 将最高位 1 右边全部变成 1。
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        // 如果最大容量大于 MAXIMUM_CAPACITY，返回 MAXIMUM_CAPACITY
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }
```

**putAll 操作**

将指定 Map 的所有键值对插入到此 Map 中。首先判断 table 是否已经初始化，如果已经初始化，判断是否需要扩容。然后将指定 Map 中所有键值对插入到当前 Map 中。

```java
    /**
     * 将指定 map 的所有映射复制到此 map 中。这些映射将替代此 map 中
     * 已经存在的 key 对应的映射。
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * 实现 Map.putAll 和 Map 构造器。
     * 将指定 Map 的键值对插入到此 Map 中。
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        // 判断容量
        // 如果指定 Map 不为空
        if (s > 0) {
            // 如果 table 没有初始化
            if (table == null) { // pre-size
                // 映射的总数除以加载因子即为初始容量
                float ft = ((float)s / loadFactor) + 1.0F;
                // 如果初始容量大于等于 MAXIMUM_CAPACITY，将初始容量
                // 设置为 MAXIMUM_CAPACITY
                int t = ((ft < (float)MAXIMUM_CAPACITY) ?
                        (int)ft : MAXIMUM_CAPACITY);
                // 如果容量大于临界值，根据容量初始化临界值
                if (t > threshold)
                    threshold = tableSizeFor(t);
            }
            // 如果 table 已经被初始化，且指定集合容量大于阈值
            else if (s > threshold)
                resize();
            // 将指定 Map 中所有键值对添加到 hashMap 中
            for (java.util.Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false, evict);
            }
        }
    }
```

**get 操作**

获取指定 key 对应的 value。在 getNode 方法中，通过 key 的 hash 值找到键值对所在的桶，判断桶内的数据结构为链表还是红黑树。如果桶内为链表，从头开始遍历，直到找到该节点为止；如果为红黑树，调用红黑树的 getTreeNode 方法查找（红黑树的节点定义和相关方法见下文）。

```java
    /**
     * 返回指定 key 对应的 value，如果指定 key 不包含任何映射返回 null。
     *
     * 返回值为 null 并不一定是因为不包含指定 key 对应的映射，也有可能是
     * map 允许 value 值为 null。containsKey 方法可以用来区分这两种情况。
     */
    public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    /**
     * 实现 Map.get 和相关方法。
     */
    final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        // 如果 table 不为 null，且 table 的长度大于 0，且对应的桶不为 null
        // 那么在桶中存在该键值对。
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (first = tab[(n - 1) & hash]) != null) {
            // 第一个节点即为指定 key 对应的节点
            if (first.hash == hash && // always check first node
                    ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            // 不是第一个节点则在桶内遍历
            if ((e = first.next) != null) {
                // 桶内为红黑树结构
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                // 桶内为链式结构
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```

**put 操作**

put 操作将指定的 key 和 value 作为节点添加到 Map 里面。putVal 函数中，首先判断 table 是否已经初始化，是否需要扩容，然后通过 key 的 hash 值找到要将新节点插入的桶。此时有两种情况需要讨论，一种是已经存在 key 对应的节点。此情景下将 value 设置成指定的 value，并返回旧的 value 值。另一种是 Map 中本身不存在指定 key 对应的节点，那么将新的节点插入到链表尾部或者红黑树适当位置即可（存在 hash 冲突时，桶内数据结构同样分为链表和红黑树两种情况）。

```java
    /**
     * 将 map 中指定的 value 和指定的 key 相关联。如果 map 之前包括了对应
     * 指定 key 的映射，那么旧的 value 将被替换。
     */
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * 实现 Map.put 和相关方法。
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        // 如果哈希表为空，或者哈希表的长度为 0，调用 resize() 创建一个
        // 哈希表，并用变量 n 记录哈希表长度
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 如果指定参数 hash 在表中没有对应的桶，即为没有碰撞，可以直接
        // 插入到 map 中
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            // 如果碰撞了，而且桶中的第一个节点（p.key == key）就匹配成功，
            // 将该节点记录下来
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            // 如果桶中的第一个节点没有匹配上，且桶内为红黑树结构，则调用
            // 红黑树对应的方法插入键值对
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            // 不是红黑树结构，肯定是链式结构
            else {
                // 遍历链式结构
                for (int binCount = 0; ; ++binCount) {
                    // 如果到了链表尾部
                    if ((e = p.next) == null) {
                        // 在链表尾部插入键值对
                        p.next = newNode(hash, key, value, null);
                        // 如果链的长度大于 TREEIFY_THRESHOLD（临界值），
                        // 则把链式结构变成红黑树
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    // 如果出现了重复的 key，跳出循环
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            // 如果 key 映射的节点不为 null，即之前就存在 key 对应的映射
            if (e != null) { // existing mapping for key
                // 记录节点的 oldValue
                V oldValue = e.value;
                // 如果 onlyIfAbsent 为 false 或者 oldValue 为 null
                if (!onlyIfAbsent || oldValue == null)
                    // 替换 value
                    e.value = value;
                // 访问后回调
                afterNodeAccess(e);
                // 返回节点的旧值
                return oldValue;
            }
        }
        ++modCount;
        // 判断是否需要扩容
        if (++size > threshold)
            resize();
        // 插入后回调
        afterNodeInsertion(evict);
        return null;
    }
```

**resize**

使用 resize 方法初始化 table 数组或者对 table 数组的大小加倍。

首先计算新的容量和新的阈值，计算方法分成以下几种情况：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/HashMap1.png" width=50% />

重新分配桶中元素的位置，分为三种情况：如果桶中有元素且不存在 hash 冲突，重新计算其位置即可；如果桶中数据结构为红黑树，调用红黑树的 split 操作重新分配；如果桶中数据结构为链式结构，那么将所有节点分为两类，一类保存在低位中（小于 oldCap 的位置中），一类保存在高位中（新的桶里）。

```java
    /**
     * 初始化 table size 或者对 table size 加倍。如果 table 为 null，对 table
     * 进行初始化。如果进行扩容操作，由于每次扩容都是翻倍，每个桶里的
     * 元素要么待在原来的索引里面，要么在新的 table 里偏移 2 的幂个位置。
     *
     * @return the table
     */
    final Node<K,V>[] resize() {
        // oldTable 保存原来的 table
        Node<K,V>[] oldTab = table;
        // oldCap 记录扩容前的长度
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        // oldThr 记录扩容前的阈值
        int oldThr = threshold;
        int newCap, newThr = 0;
        // 如果扩容器前的容量大于 0，说明老数组中已经存在元素
        if (oldCap > 0) {
            // 如果扩容前的容量大于 MAXIMUM_CAPACITY
            // 将阈值设置为 Integer.MAX_VALUE，无法进行扩容，返回
            // 原来的 table
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 首先将 newCap 变成 oldCap 的两倍。如果 newCap（oldCap 的两倍）
            // 小于容量限制（MAXIMUM_CAPACITY）且 oldCap 大于默认
            // 初始容量（DEFAULT_INITIAL_CAPACITY），则临界值变为原来
            // 的两倍
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                    oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        // 如果旧容量小于等于 0，说明老数组没有任何元素。
        // 旧的阈值大于 0，将新的容量设置为老数组的阈值。
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 如果旧容量小于等于 0, 且旧的阈值小于 0。运行到这里说明是调用的
        // 无参构造函数创建的该 map，并且第一次添加元素。
        // 新容量设置成默认初始容量，新的阈值设置成默认初始阈值
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        // 上面的条件中只有旧容量小于等于 0 且旧的阈值大于 0 时，才有
        // newThr 等于 0，此时 newCap 已经被赋值为 oldThr。
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }
        // 设置此 map 的阈值为计算出来的新的阈值 newThr
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        // 创建新的数组（对于第一次添加元素，这个数组就是第一个数组，对于
        // 存在 oldTab 的情况，这个数组就是需要扩容到的新数组）
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        // table 指向新数组
        table = newTab;
        // 如果 oldTab 不为 null，说明存在元素，需要将元素转移到新数组
        if (oldTab != null) {
            // 遍历 oldTab
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                // 如果当前位置有元素，那么需要转移该元素
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    // 如果元素的 next 属性为 null，说明不存在 hash 冲突
                    if (e.next == null)
                        // 把元素存储到新数组中，位置需要根据 hash 值和数组长度
                        // 取模：[hash 值 % 数组长度] = [hash 值 & （数组长度 - 1）]
                        // 用上述方式取模要求数组长度必须是 2 的 N 次方
                        newTab[e.hash & (newCap - 1)] = e;

                    // 如果 e 有下一个节点，判断其存储结构是链表结构还是红黑树结构
                    // 数组长度为 16，那么 hash 值为 1（1%16=1）的和 hash 值为
                    // 17（17%16=1）的两个元素都是会存储在数组的第 2 个位置上
                    //（对应数组下标为 1 ），当数组扩容为 32（1%32=1）时，hash
                    // 值为1的还应该存储在新数组的第二个位置上，但是 hash 值为
                    // 17（17%32=17）的就应该存储在新数组的第18个位置上了。
                    // 所以数组扩容后，所有元素都需要重新计算在新数组中的位置。

                    // 如果为红黑树结构
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    // 否则肯定为链式结构
                    else { // preserve order
                        // loHead 低位首节点，loTail 低位尾结点
                        Node<K,V> loHead = null, loTail = null;
                        // hiHead 高位首节点，hiTail 高位尾结点
                        Node<K,V> hiHead = null, hiTail = null;
                        // 以上的低位指的是新数组的 0 到 oldCap - 1、高位指的
                        // 是 oldCap 到 newCap - 1
                        Node<K,V> next;
                        // 对当前桶的所有节点进行遍历
                        do {
                            next = e.next;
                            // e 的 hash 值和 oldCap 求与操作，值为 0，说明 hash 值
                            // 小于老数组的长度
                            if ((e.hash & oldCap) == 0) {
                                // 链表为空，头结点指向该元素
                                if (loTail == null)
                                    loHead = e;
                                // 链表不为空，元素添加到链表尾部
                                else
                                    loTail.next = e;
                                // 尾结点设置为当前元素
                                loTail = e;
                            }
                            // 否则 hash 值大于老数组的长度，此时元素应该放置到
                            // 高位位置上
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        // 低位的元素组成的链表还是放在原来的位置
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        // 高位元素组成的链表放置的位置在原有位置上偏移了
                        // 老数组的长度个位置
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }
```

**链式结构转化为树结构**

table 没有达到转化成树结构的容量时，进行扩容操作。否则，先将链表中的节点转化成树节点，同时将链表转化成双向链表，然后调用树节点的 treeify 方法将双向链表进一步组织成红黑树结构。

```java
    final void treeifyBin(Node<K,V>[] tab, int hash) {
        int n, index; Node<K,V> e;
        // 如果元素数组为 null 或者数组长度小于树结构化的最小限制，
        // 则没有必要进行结构转换
        // 注意：当一个桶里集中了多个键值对映射，那是因为这些 key 的
        // hash 值和数组长度取模之后结果相同，而不是因为这些 key 的 hash
        // 值相同。
        // 因为 hash 值相同的概率不高，所以可以通过扩容的方式，来使这些
        // 键值对拆分到多个位置上。
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
            resize();
        // 如果待转化的桶不为 null，则将该桶内的映射转化成树形结构
        else if ((e = tab[index = (n - 1) & hash]) != null) {
            // 首尾节点
            TreeNode<K,V> hd = null, tl = null;
            // 先把节点转化成树节点，把单向链表转化成双向链表
            do {
                // 将该节点转化为树节点
                TreeNode<K,V> p = replacementTreeNode(e, null);
                // 如果尾结点为 null，说明还没有根节点
                if (tl == null)
                    hd = p;
                // 尾结点不为空
                else {
                    p.prev = tl;
                    tl.next = p;
                }
                // 当前节点设置成尾结点
                tl = p;
            } while ((e = e.next) != null);
            // 双向链表替换原来的单向链表，并转化成红黑树
            if ((tab[index] = hd) != null)
                hd.treeify(tab);
        }
    }
```






***
> HashMap 小结

HashMap 是常用集合类中最复杂，也是设计最巧妙的一种数据结构。其底层的数据结构中用到了数组，链表以及红黑树，

***
> JDK 1.7 和 JDK 1.8



***
> hashCode() 和 equals()



***
> 扩容和 rehash


***
> 为什么容量总是 2 的指数次方



***
> 哈希碰撞与解决方法

线性探查。。。


***
> 多线程环境下重新调整 HashMap 大小，发生条件竞争

当重新调整HashMap大小的时候，可能存在条件竞争，因为如果两个线程都发现HashMap需要重新调整大小了，它们会同时试着调整大小。在调整大小的过程中，存储在链表中的元素的次序会反过来，因为移动到新的bucket位置的时候，HashMap并不会将元素放在链表的尾部，而是放在头部，这是为了避免尾部遍历(tail traversing)，原数组[j]位置上的桶移到了新数组[j+原数组长度]。如果条件竞争发生了，那么就死循环了。


***
> 参考：

[30张图带你彻底理解红黑树](https://www.jianshu.com/p/e136ec79235c)

[Java集合：HashMap详解(JDK 1.8)【面试+工作】](http://www.sohu.com/a/254899015_100012573)

[Java8源码-HashMap](https://blog.csdn.net/panweiwei1994/article/details/77244920)

[JDK8：HashMap源码解析](https://blog.csdn.net/weixin_42340670)

[HashMap源码详解一篇就够](https://www.jianshu.com/p/4aa3bb16f36c)

[HashMap实现原理及源码分析](https://www.cnblogs.com/chengxiao/p/6059914.html)