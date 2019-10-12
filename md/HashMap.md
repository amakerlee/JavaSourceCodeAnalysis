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

**remove 操作**

clear 函数用来删除 map 中的所有映射，实现思路比较简单，将所有桶中的元素设置为 null 即可，让虚拟机自动完成垃圾回收。

remove 和 removeNode 函数用来删除 map 中指定 key 对应的映射。首先找到指定 key 所在的桶，如果桶内有元素，指定映射可能存在此桶中，那么继续向下查找。由于桶内的数据结构可能为链式结构或红黑树结构，所以分成两种情况讨论。 对于链式结构，从链表头开始遍历搜索，直到找到指定映射为止，同时记录其前一个节点的引用，以便执行删除操作。对于红黑树结构，使用 getTreeNode 方法查找指定节点，使用 removeTreeNode 方法删除指定节点。

```java
    /**
     * 删除此 map 中指定 key 对应的映射，如果其存在的话。
     */
    public V remove(Object key) {
        Node<K,V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ?
                null : e.value;
    }

    /**
     * 实现 Map.remove 和相关方法。
     * 方法为 final，不可被覆盖
     *
     * @param hash key的hash值，该值是通过hash(key)获取到的
     * @param key 要删除的键值对的key
     * @param value 要删除的键值对的value，该值是否作为删除的条件取决
     *               于matchValue是否为true
     * @param matchValue 如果为true，则当key对应的键值对的值
     *                equals(value)为true时才删除；否则不关心value的值
     * @param movable 删除后是否移动节点，如果为false，则不移动
     * @return 返回被删除的节点对象，如果没有删除任何节点则返回null
     */
    final Node<K,V> removeNode(int hash, Object key, Object value,
                               boolean matchValue, boolean movable) {
        // 声明节点数组，当前节点，数组长度，索引值
        Node<K,V>[] tab; Node<K,V> p; int n, index;
        // 如果节点数组 tab 不为 null，tab 的长度大于 0，当前节点对象
        //（该节点为树的根节点或链表的首节点）不为 null，则从该节点遍历，
        // 找到和 key 匹配的对象。
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (p = tab[index = (n - 1) & hash]) != null) {
            Node<K,V> node = null, e; K k; V v;

            // 如果当前节点的 key 和指定 key 相等（引用相等或者值相等），
            // 那么当前节点就是要删除的节点
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                node = p;
            // 第一个节点没有匹配成功，检查是否有 next 节点
            // 如果有 next 节点，说明发生了 hash 碰撞，该节点上的数据结构
            // 可能为链式结构，可能为红黑树
            else if ((e = p.next) != null) {
                // 当前节点是树节点，那么调用红黑树中 getTreeNode 方法查找
                // 指定节点
                if (p instanceof TreeNode)
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                // 当前节点是链表节点，从头到尾遍历
                else {
                    do {
                        // hash 值相等，或者 key 指向同一个对象，或者 key 的值相等
                        // 即表示匹配成功
                        if (e.hash == hash &&
                                ((k = e.key) == key ||
                                        (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        // 把当前节点p指向e，这一步是让p存储的永远下一次循环
                        // 里e的父节点，如果下一次e匹配上了，那么p就是node的
                        // 父节点
                        p = e;
                    } while ((e = e.next) != null);
                }
            }

            // 如果 node 不为 null，说明 key 匹配成功
            // 如果不需要对比 value或者需要对比但 value 也相等
            // 那么就可以删除该 node 节点
            if (node != null && (!matchValue || (v = node.value) == value ||
                    (value != null && value.equals(v)))) {
                // 该节点是树节点，调用 TreeNode 的 removeTreeNode 方法删除
                if (node instanceof TreeNode)
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                // 如果找到的节点是首节点，将桶指向第二个节点即可
                else if (node == p)
                    tab[index] = node.next;
                // 找到的节点是链表的中间节点，由于 p 是 node 的父节点，直接
                // 将 p.next 指向 node.next 即可
                else
                    p.next = node.next;
                ++modCount;
                // size 减一
                --size;
                // 留给子类的操作，此类没有任何实现逻辑
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }

    /**
     * 删除此 map 中所有映射。
     * 此方法调用后 map 为空。
     */
    public void clear() {
        Node<K,V>[] tab;
        modCount++;
        if ((tab = table) != null && size > 0) {
            size = 0;
            // 将所有桶内元素设置为  null 即可，虚拟机自动完成垃圾回收
            for (int i = 0; i < tab.length; ++i)
                tab[i] = null;
        }
    }
    
```


**resize**

使用 resize 方法初始化 table 数组或者对 table 数组的大小加倍。

首先计算新的容量和新的阈值，计算方法分成以下几种情况：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/HashMap1.png" width=70% />

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
> TreeNode

对于 HashMap 类，JDK1.8 相比于 JDK1.7 一个较大的改进是，当任意桶内节点超过指定数量时，桶内的链式结构会转化成红黑树结构。链表的时间代价是 O(n)，而红黑树的时间代价为 O(logn)。

值得一提的是，红黑树的节点类 TreeNode 继承自 LinkedHashMap.Entry（此类又继承自 Node），所以红黑树数据结构同时也是双向链表结构。在 TreeNode 的几乎所有操作中，都涉及到节点同时作为红黑树节点和双向链表节点的变化。

红黑树（平衡二叉查找树）的性质包括以下几点，正因为有了这些性质，红黑树才能成为时间代价为 O(logn) 的平衡二叉查找树结构：

    根节点是黑色；
    每个叶节点（NIL节点，即空节点）是黑色的；
    每个红色节点的两个子节点都是黑色。（从每个叶子到根的所有路径上不能有两个连续的红色节点）
    从任一节点到其每个叶子的所有路径都包含相同数目的黑色节点
    
红黑树通过“变色”和“旋转”来维护红黑树的规则，变色就是让黑的变成红的，红的变成黑的，旋转又分为“左旋转”和“右旋转”。左旋转是逆时针旋转，使父节点被自己的右子节点取代，自己成为左子节点；右旋转是顺时针旋转，使父节点被左子节点取代，自己成为右子节点。（红黑树是 HashMap 之前所有的集合类中，最复杂的一种数据结构）

**类属性**

```java
        // 父节点
        TreeNode<K,V> parent;  // red-black tree links
        // 左子节点
        TreeNode<K,V> left;
        // 右子节点
        TreeNode<K,V> right;
        // 前一个节点（双向链表属性）
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        // 是否是红色
        boolean red;
```

**getTreeNode（查找）**

红黑树是一棵黑色平衡的二叉查找树，所以红黑树的查找和二叉查找树的方法完全一样。如果当前结点 key 等于查找 key，那么该 key 为要查找的节点，返回该节点；如果当前结点 key 大于查找 key，那么要查找的节点只可能在右子树中，继续在右子树中查找；如果当前结点 key 小于查找 key，那么要查找的节点只可能在左子树中，继续在左子树中查找。

由于红黑树总保持黑色完美平衡，所以它的查找最坏时间复杂度为 O(2lgN)。

```java
        /**
         * 树结构的查找函数
         */
        final TreeNode<K,V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k, null);
        }
        
        /**
         * 从根节点 p 开始查找指定 hash 值和关键字 key 的结点
         * 当第一次使用比较器比较关键字时，参数 kc 储存了关键字 key 的比较器类别
         */
        final TreeNode<K,V> find(int h, Object k, Class<?> kc) {
            TreeNode<K,V> p = this;
            do {
                int ph, dir; K pk;
                TreeNode<K,V> pl = p.left, pr = p.right, q;
                // 给定哈希值小于当前节点的哈希值，进入左节点
                if ((ph = p.hash) > h)
                    p = pl;
                // 给定哈希值大于当前节点的哈希值，进入右节点
                else if (ph < h)
                    p = pr;
                // hash 值和 key 均相等，则找到该节点
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    return p;
                // 左子节点为空，进入右子节点
                else if (pl == null)
                    p = pr;
                // 右子节点为 null，进入左子节点
                else if (pr == null)
                    p = pl;
                // 如果不按哈希值排序，而是按照比较器排序，则通过比较器返回值
                // 决定进入左右结点
                else if ((kc != null ||
                        (kc = comparableClassFor(k)) != null) &&
                        (dir = compareComparables(kc, k, pk)) != 0)
                    p = (dir < 0) ? pl : pr;
                // 如果在右子节点中找到指定节点，直接返回
                else if ((q = pr.find(h, k, kc)) != null)
                    return q;
                // 否则进入左子节点
                else
                    p = pl;
            } while (p != null);
            return null;
        }
```

** putTreeVal（插入）**

首先必须注意，在红黑树的插入操作中，新插入的节点，在调整之前均为红色。

插入操作可以分解成两个部分，查找和自平衡。其中查找操作和 getTreeNode 方法的思路基本一致，使用循环从上往下查找，直到找到要插入的位置为止，这一步骤主要在 putTreeVal 函数中完成。找到后在该函数中创建一个新的 TreeNode 节点，并完成双链表的连接。为了让插入之后的树满足红黑树的性质，接着调用 balanceInsertion 函数完成插入新节点之后的自平衡。最后调用 moveRootToFront 确保 root 是直接保存在桶内的第一个节点。

其中插入新节点之后自平衡的情况如下图所示（引用自《[30张图带你彻底理解红黑树](https://www.jianshu.com/p/e136ec79235c)》）

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/HashMap_putTreeVal.png" width=100% />

其中 I 表示插入节点，P 表示插入节点的父节点，PP 表示插入节点的祖先节点，S 表示插入节点的叔叔节点。

```java
        /**
         * 树结构中的插入操作
         * 存在 hash 碰撞，且元素数量大于 8 的时候，以红黑树的结构存储元素
         * @param map 当前节点所在的 HashMap 对象
         * @param tab 当前 HashMap 对象的元素数组
         * @param h 指定 key 的 hash 值
         * @param k 指定 key
         * @param v 指定 key 要写入的 value
         * @return TreeNode 指定 key 匹配到的节点对象，针对这个对象修改 value
         */
        final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab,
                                       int h, K k, V v) {
            Class<?> kc = null;
            boolean searched = false;
            // 如果此节点的父节点不为 null 那么查找根节点，如果为 null 那么此
            // 节点即为根节点
            TreeNode<K,V> root = (parent != null) ? root() : this;
            // 从根节点开始遍历。终止条件在内部
            for (TreeNode<K,V> p = root;;) {
                // 声明方向 dir，当前节点的 hash 值，当前节点的键对象 pk
                int dir, ph; K pk;
                // 如果当前节点的 hash 值大于指定 key 的 hash 值，那么要添加的
                // 元素应该放置在当前节点左侧
                if ((ph = p.hash) > h)
                    dir = -1;
                // 如果当前节点的 hash 值小于指定 key 的 hash 值，那么要添加的
                // 元素应该放置在当前节点的右侧
                else if (ph < h)
                    dir = 1;
                // 如果找到和指定的 key 相等的节点，那么返回该节点，在外层方法
                // 会对 value 进行写入
                else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                    return p;

                // 到这一步说明当前节点的 hash 值和指定的 hash 值相等，但是 key
                // 的 equals 方法返回 false
                else if ((kc == null &&
                        (kc = comparableClassFor(k)) == null) ||
                        (dir = compareComparables(kc, k, pk)) == 0) {

                    // 走到这里说明：指定key没有实现comparable接口   或者实现了
                    // comparable接口并且和当前节点的键对象比较之后相等（仅限第一次循环）

                    // searched 标识是否已经遍历过当前节点的左右子节点
                    // 如果没有遍历过，那么递归进行遍历，看能否匹配到和指定 key
                    // 相等的键值对
                    // 找到了匹配的键值对就立刻返回，
                    if (!searched) {
                        TreeNode<K,V> q, ch;
                        searched = true;
                        // 沿着左右两侧进行遍历
                        if (((ch = p.left) != null &&
                                (q = ch.find(h, k, kc)) != null) ||
                                ((ch = p.right) != null &&
                                        (q = ch.find(h, k, kc)) != null))
                            return q;
                    }
                    // 遍历左右子树之后仍然没找到匹配的节点，再次比较当前节点 key
                    // 和指定 key 的大小
                    dir = tieBreakOrder(k, pk);
                }

                // xp 指向 当前节点
                TreeNode<K,V> xp = p;
                // dir 小于等于 0 时，插入到左边，那么看当前节点的左子节点是否
                // 为空，如果为空，就可以把要添加的元素作为当前节点的左子节点，
                // 如果不为空，还需要下一轮继续比较
                // dir 大于等于 0 时，插入到右边，那么看当前节点的右子节点是否
                // 为空，如果为空，就可以把要添加的元素作为当前节点的右子节点，
                // 如果不为空，还需要下一轮继续比较
                 // 注意：如果以上两条当中有一个子节点不为空，p已经指向了对应
                // 的不为空的子节点，开始下一轮的比较
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    // p 已经指向了其左子节点或者右子节点，且 p 为 null，而 xp 为
                    // 之前的 p
                    Node<K,V> xpn = xp.next;
                    // 创建一个新的树节点 x，x 的 next 指向 xp 的 next
                    TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);
                    // dir 小于等于 0 时 xp 的左指针指向新创建的节点，dir 大于 0
                    // 时 xp 的右指针指向新创建的节点
                    if (dir <= 0)
                        xp.left = x;
                    else
                        xp.right = x;
                    // xp 的 next 指向新创建的 x 节点，即链式结构中，x 插入到 xp
                    // 和它的下一个节点中间
                    xp.next = x;
                    // 新创建节点的 parent 和 prev 均设置为当前节点（left 和 right
                    // 指向 null，因为其为叶节点，没有子节点
                    x.parent = x.prev = xp;
                    if (xpn != null)
                        ((TreeNode<K,V>)xpn).prev = x;
                    // 调整树的结构使其满足红黑树的规则，以及新的 root 移动到链表
                    // 最前面
                    moveRootToFront(tab, balanceInsertion(root, x));
                    // 返回 null，意味着产生了一个新的节点
                    return null;
                }
            }
        }
        
        /**
         * 红黑树的插入平衡算法。当树结构中新插入了一个节点之后，要对树进行
         * 结构调整，以保证该树维持红黑树的特性
         * @param root 当前的根节点
         * @param x 为新插入的节点
         * @return 返回值为调整后的根节点
         */
        static <K,V> TreeNode<K,V> balanceInsertion(TreeNode<K,V> root,
                                                    TreeNode<K,V> x) {
            // 新插入的节点标记为红色
            x.red = true;
            // xp: 当前节点的父节点
            // xpp: 当前节点的爷爷节点
            // xppl: 当前节点的左叔叔节点
            // xppr: 当前节点的右叔叔节点
            for (TreeNode<K,V> xp, xpp, xppl, xppr;;) {
                // 如果父节点为 null，那么当前节点就是根节点，直接把当前节点
                // 标记为黑色，并返回当前节点
                if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                // 父节点为黑色，或者爷爷为 null，不需要进行调整，直接返回 root
                else if (!xp.red || (xpp = xp.parent) == null)
                    return root;

                // 进入到这里，父节点为红色，插入的节点也为红色，需要进行调整
                // 如果父节点是爷爷节点的左子节点
                if (xp == (xppl = xpp.left)) {
                    // 如果右叔叔节点不为 null 且为红色
                    if ((xppr = xpp.right) != null && xppr.red) {
                        // 右叔叔节点置为黑色
                        xppr.red = false;
                        // 父节点置为黑色
                        xp.red = false;
                        // 爷爷节点置为红色
                        xpp.red = true;
                        // 把爷爷节点当做处理的起始节点
                        x = xpp;
                    }
                    // 如果右叔叔节点为 null 或者为黑色
                    else {
                        // 如果当前节点是父节点的右孩子
                        if (x == xp.right) {
                            // 父节点左旋
                            root = rotateLeft(root, x = xp);
                            // 获取爷爷节点
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }

                        // 如果父节点不为 null
                        if (xp != null) {
                            // 将父节点置为黑色
                            xp.red = false;
                            // 如果爷爷节点不为 null
                            if (xpp != null) {
                                // 将爷爷节点置为红色，并且爷爷节点右旋
                                xpp.red = true;
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                }
                // 如果父节点是爷爷节点的右子节点
                else {
                    // 如果左叔叔节点不为 null 且为红色
                    if (xppl != null && xppl.red) {
                        // 左叔叔节点置为黑色，父节点置为黑色，爷爷节点置为红色
                        xppl.red = false;
                        xp.red = false;
                        xpp.red = true;
                        // 下一轮循环中，将爷爷节点作为处理的起始节点
                        x = xpp;
                    }
                    // 如果左叔叔节点为 null 或者是黑色
                    else {
                        // 如果当前节点是左子节点
                        if (x == xp.left) {
                            // 针对父节点做右旋操作
                            root = rotateRight(root, x = xp);
                            // 获取爷爷节点
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }

                        // 如果父节点不为 null
                        if (xp != null) {
                            // 父节点置为黑色
                            xp.red = false;
                            // 如果爷爷节点不为空
                            if (xpp != null) {
                                // 将爷爷节点置为红色，针对爷爷节点做左旋
                                xpp.red = true;
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }
```

**removeTreeNode（删除）**

```java
        /**
         * 删除节点
         * p 是待删除的节点，replacement 是删除 p 后需要来接替 p 位置的节点
         */
        final void removeTreeNode(HashMap<K,V> map, Node<K,V>[] tab,
                                  boolean movable) {
            // 链表处理开始
            int n;
            // table 为 null 或者 table 的长度为 0 直接返回
            if (tab == null || (n = tab.length) == 0)
                return;
            // 根据 hash 值计算出索引的位置
            int index = (n - 1) & hash;
            // 索引中第一个节点赋值给 first 和 root
            TreeNode<K,V> first = (TreeNode<K,V>)tab[index], root = first, rl;
            // succ 指向当前节点下一个节点，pred 指向当前节点的上一个节点
            TreeNode<K,V> succ = (TreeNode<K,V>)next, pred = prev;
            // 如果 pred 为 null，则将当前节点的下一个节点 next 设置为
            // 第一个节点
            if (pred == null)
                tab[index] = first = succ;
            // 否则将 pred 的 next 设置为当前节点的下一个节点
            else
                pred.next = succ;
            if (succ != null)
                succ.prev = pred;
            // 如果删除完之后 first 为 null，则代表该索引位置已经没有节点，
            // 则直接返回
            if (first == null)
                return;
            // 如果 root 的 parent 不为 null，说明 root 不是根节点，将 root
            // 赋值为根节点
            if (root.parent != null)
                root = root.root();
            // 通过 root 判断此红黑树是否太小，如果是则调用 untreeify 方法
            // 将其转化成链式结构并将所有节点转化成简单节点
            if (root == null
                    || (movable
                    && (root.right == null
                    || (rl = root.left) == null
                    || rl.left == null))) {
                tab[index] = first.untreeify(map);  // too small
                // 若转化成链式结构则不需要再进行下面的红黑树处理
                return;
            }
            // 链表处理到此结束

            // 红黑树处理开始
            // p 指向 this 节点，pl 指向 left 节点，pr 指向 right 节点
            // （p 为要删除的节点）
            TreeNode<K,V> p = this, pl = left, pr = right, replacement;
            // 如果其左子节点和右子节点都不为 null
            if (pl != null && pr != null) {
                // s 指向 p 的右子节点
                TreeNode<K,V> s = pr, sl;
                // s 一直向左查找，直到其指向最左的叶子结点
                while ((sl = s.left) != null) // find successor
                    s = sl;
                // 首先交换 p 和 s 的颜色（下面的步骤都是为了将 p 和 s 位置互换，
                // 先交换颜色）
                boolean c = s.red; s.red = p.red; p.red = c; // swap colors
                // sr 指向 s 的右子节点
                TreeNode<K,V> sr = s.right;
                // pp 指向 p 的父节点
                TreeNode<K,V> pp = p.parent;

                // 第一次调整开始
                // 如果 p 的右子节点 pr 为叶子结点，将 p 的父节点设置为 s，
                // s 的右子节点设置为 p
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                }
                else {
                    TreeNode<K,V> sp = s.parent;
                    // 如果 s 的父节点不为 null，将 p 的父节点设置为 s 的父节点 sp
                    if ((p.parent = sp) != null) {
                        // 如果 s 节点为 左子节点，则将 s 父节点的左子节点设置为 p 节点
                        if (s == sp.left)
                            sp.left = p;
                        // 否则将 s 父节点的右子节点设置为 p 节点
                        else
                            sp.right = p;
                    }
                    // s 的右子节点设置为 pr，pr 的父节点设置为 s
                    if ((s.right = pr) != null)
                        pr.parent = s;
                }

                // 第二次调整
                p.left = null;
                // 如果 s 的右子节点不为 null，将 p 的右子节点设置为 s 的右子节点
                if ((p.right = sr) != null)
                    sr.parent = p;
                // 如果 p 的左子节点不为 null，将 s 的左子节点设置为 p 的左子节点
                if ((s.left = pl) != null)
                    pl.parent = s;
                // 将 s 的父节点设置为 p 的父节点，若父节点为 null，则 s 节点为
                // 红黑树的根节点
                if ((s.parent = pp) == null)
                    root = s;
                // 如果 p 为其父节点 pp 的左子节点，将 pp 的左子节点设置为 s
                // 否则将 pp 的右子节点设置为 s
                else if (p == pp.left)
                    pp.left = s;
                else
                    pp.right = s;

                // 为什么 sr 是 replacement 的首选，p 为备选？
                // 从代码中可以看到 sr 第一次被赋值时，是在 s 节点进行了向左
                // 穷遍历结束后，因此此时 s 节点是没有左节点的，sr 即为 s 节
                // 点的右节点。而从上面的三次调整我们知道，p 节点已经跟 s
                // 节点进行了位置调换，所以此时 sr 其实是 p 节点的右节点，
                // 并且 p 节点没有左节点，因此要移除 p 节点，只需要将 p 节点
                // 的右节点 sr 覆盖掉 p 节点即可，因此 sr 是 replacement 的
                // 首选，如果 sr 为空，则代表 p 节点为叶子节点，此时将 p 节点
                // 清空即可。
                if (sr != null)
                    replacement = sr;
                else
                    replacement = p;
            }
            // 如果 p 的左子节点不为 null，右子节点为 null， replacement 设置
            // 为其左子节点
            else if (pl != null)
                replacement = pl;
            // 如果 p 的右子节点不为 null，左子节点为 null， replacement 设置
            // 为其右子节点
            else if (pr != null)
                replacement = pr;
            // 如果其左右子节点都为 null，replacement 直接设置为 p 节点
            else
                replacement = p;

            // 第三次调整
            // 如果 p 节点不是叶节点（只有当 pl == null 且 pr == null 时，即 p 是
            // 叶节点时，replacement 才会等于 p）
            if (replacement != p) {
                // 将 replacement 的父节点设置为 p 的父节点
                TreeNode<K,V> pp = replacement.parent = p.parent;
                // 如果 p 的父节点为 null，即 p 没有父节点，那么 p 为 root 节点
                if (pp == null)
                    root = replacement;
                // 如果 p 是其父节点的左子节点，则将父节点的左子节点替换成
                // replacement
                else if (p == pp.left)
                    pp.left = replacement;
                // 否则将其右子节点替换成 replacement
                else
                    pp.right = replacement;
                // p 节点的位置已经被完整替换成 replacement，将 p 节点清空
                // 以便 gc
                p.left = p.right = p.parent = null;
            }

            // 如果 p 节点不为红色则进行红黑树删除平衡调整
            // 如果 p 是红色则不会破坏红黑树的平衡不需要调整
            TreeNode<K,V> r = p.red ? root : balanceDeletion(root, replacement);

            // 如果 p 等于 replecement 即其左右子节点均为 null，即 p 本身为
            // 叶节点，则将节点 p 删除即可
            if (replacement == p) {  // detach
                TreeNode<K,V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    // 如果 p 为其父节点的左子节点，则将左子节点设为 null
                    if (p == pp.left)
                        pp.left = null;
                    // 如果 p 为其父节点的右子节点，则将右子节点设为 null
                    else if (p == pp.right)
                        pp.right = null;
                }
            }
            if (movable)
                moveRootToFront(tab, r);
        }
        
        /**
         * 红黑树的删除平衡算法。当树结构中删除了一个节点之后，要对树进行
         * 结构调整，以保证该树维持红黑树的特性
         * @param root 当前的根节点
         * @param x 为待继承删除节点的 replacement 节点
         * @return 返回值为调整后的根节点
         */
        static <K,V> TreeNode<K,V> balanceDeletion(TreeNode<K,V> root,
                                                   TreeNode<K,V> x) {
            for (TreeNode<K,V> xp, xpl, xpr;;) {
                // x 为 null 或者 x 是 root，直接返回
                if (x == null || x == root)
                    return root;
                // x 的父节点为 null，即 x 为根节点，将其染成黑色，然后返回
                else if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                // 如果 x 是红色，将其染成黑色，并返回根节点
                else if (x.red) {
                    x.red = false;
                    return root;
                }
                // 若 x 为其父节点的左子节点
                else if ((xpl = xp.left) == x) {
                    // 如果 x 的右兄弟节点不为 null 且为红色，将其兄弟节点染成
                    // 黑色，其父节点染成红色，然后对父节点 xp 左旋转，xp
                    // 赋值为 x 的父节点，xpr 赋值为 x 的右兄弟节点
                    if ((xpr = xp.right) != null && xpr.red) {
                        xpr.red = false;
                        xp.red = true;
                        root = rotateLeft(root, xp);
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    // 如果 xpr 为 null，则继续向上调整，将 x 的父节点作为新的
                    // x 继续循环
                    if (xpr == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) &&
                                (sl == null || !sl.red)) {
                            xpr.red = true;
                            x = xp;
                        }
                        else {
                            if (sr == null || !sr.red) {
                                if (sl != null)
                                    sl.red = false;
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                xpr = (xp = x.parent) == null ?
                                        null : xp.right;
                            }
                            if (xpr != null) {
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null)
                                    sr.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateLeft(root, xp);
                            }
                            x = root;
                        }
                    }
                }
                else { // symmetric
                    if (xpl != null && xpl.red) {
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null)
                        x = xp;
                    else {
                        TreeNode<K,V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) &&
                                (sr == null || !sr.red)) {
                            xpl.red = true;
                            x = xp;
                        }
                        else {
                            if (sl == null || !sl.red) {
                                if (sr != null)
                                    sr.red = false;
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ?
                                        null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null)
                                    sl.red = false;
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }
```

**rotateLeft（左旋）**

```java
        /**
         * 节点左旋
         * @param root 根节点
         * @param p 要左旋的节点
         * @return root 红黑树的根节点
         */
        static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root,
                                              TreeNode<K,V> p) {
            TreeNode<K,V> r, pp, rl;
            // 要左旋的节点不为 null 且其右子节点不为 null
            if (p != null && (r = p.right) != null) {
                // 右子节点的左子节点赋值给要旋转节点 p 的右子节点，并将 rl 指向
                // 该节点
                if ((rl = p.right = r.left) != null)
                    // rl 的父节点指向要旋转的节点 p
                    rl.parent = p;
                // 将原先 p 的右子节点 r 的 parent 设置成要旋转的节点 p 的
                // parent，即 p 的父节点变成了 r 的父节点 pp
                // 如果此时 pp 为 null，说明 r 已经是顶层的根节点了，应该设置为
                // root并且标为黑色
                if ((pp = r.parent = p.parent) == null)
                    (root = r).red = false;
                // 如果要旋转的节点 p 是 pp 的左子节点，那么将 pp 的左子节点赋值
                // 为 r，p 已经不再是 pp 的子节点了
                else if (pp.left == p)
                    pp.left = r;
                // 要旋转的节点 p 是 pp 的右子节点
                else
                    pp.right = r;
                // 以下两步执行完成后，p 正式变成 r 的左子节点
                r.left = p;
                p.parent = r;
            }
            // 返回根节点
            return root;
        }
```

**rotateRight（右旋）**

```java
        /**
         * 节点右旋
         * @param root 根节点
         * @param p 要右旋的节点
         * @return root 红黑树的根节点
         */
        static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root,
                                               TreeNode<K,V> p) {
            TreeNode<K,V> l, pp, lr;
            // 要右旋的节点 p 不为 null 且其左子节点 l 不为 null
            if (p != null && (l = p.left) != null) {
                // l 的右子节点设置为 p 的左子节点，并将 lr 指向该节点，如果 lr
                // 不为 null，那么 lr 的父节点设置为 p，此时 lr 和 l 再没有关系
                if ((lr = p.left = l.right) != null)
                    lr.parent = p;
                // 将 l 的父节点设置成 p 的父节点，此时 l 和 p 的父节点开始有了
                // 父子关系，p 的父节点 pp 变成了 l 的父节点
                // 如果此时 pp 为 null，说明 l 已经是顶层的根节点了，应该设置为
                // root并且标为黑色
                if ((pp = l.parent = p.parent) == null)
                    (root = l).red = false;
                // 如果p 是 pp 的右子节点，那么将 pp 的右子节点赋值为 l，p 已经
                // 不再是 pp 的子节点了
                else if (pp.right == p)
                    pp.right = l;
                // 要旋转的节点 p 是 pp 的左子节点
                else
                    pp.left = l;
                // 以下两步执行完成后，p 正式变成 r 的右子节点
                l.right = p;
                p.parent = l;
            }
            // 返回根节点
            return root;
        }
```

**balanceDeletion（删除平衡）**

```java

```


**treeify**

```java
        /**
         * 根据链表生成树结构。遍历链表获取节点，一个一个插入到红黑树中，
         * 插入一个节点后调用一次 balanceInsertion 检查红黑树性质是否需要
         * 修复。链表遍历完之后，调用 moveRootToFront 确保 root 节点是在
         * table 数组上
         */
        final void treeify(Node<K,V>[] tab) {
            // 根节点储存在 root 里
            TreeNode<K,V> root = null;
            // 对链式结构进行遍历
            for (TreeNode<K,V> x = this, next; x != null; x = next) {
                next = (TreeNode<K,V>)x.next;
                x.left = x.right = null;
                // 根节点的父节点为 null，根节点一定是黑色
                if (root == null) {
                    x.parent = null;
                    x.red = false;
                    root = x;
                }
                // 非根节点
                else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    for (TreeNode<K,V> p = root;;) {
                        int dir, ph;
                        K pk = p.key;
                        // 当前节点的 hash 值大于待插入节点的 hash 值
                        if ((ph = p.hash) > h)
                            dir = -1;
                        // 当前节点的 hash 值小于待插入节点的 hash 值
                        else if (ph < h)
                            dir = 1;
                        // comparableClassFor: 如果对象 k 的类实现了 Comparable<C>
                        // 接口，那么返回 k 的类型，否则返回 null
                        // compareComparables: 如果 pk 对象的类型和 kc 匹配，返回
                        // k.compareTo(pk) 的比较结果。如果 pk 为空，或者其所属的
                        // 类不是 kc，返回 0。
                        else if ((kc == null &&
                                (kc = comparableClassFor(k)) == null) ||
                                (dir = compareComparables(kc, k, pk)) == 0)
                            dir = tieBreakOrder(k, pk);

                        TreeNode<K,V> xp = p;
                        // 要插入的地方没有子节点，则进行插入，否则沿着要插入的
                        // 子树继续向下遍历
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            // 要插入节点 x 的父节点为上一个节点
                            x.parent = xp;
                            // 如果 dir 小于等于 0，即 x 的 hash 值小于等于 xp 的
                            // hash 值，赋值为左子节点
                            if (dir <= 0)
                                xp.left = x;
                            // 如果 dir 小于等于 0，即 x 的 hash 值小于等于 xp 的
                            // hash 值，赋值为右子节点
                            else
                                xp.right = x;
                            // 插入后修复红黑树的性质
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            // 确保当前的 root 是在 table 数组上
            moveRootToFront(tab, root);
        }
```

**untreeify**

```java
        /**
         * 树结构转化为单向链式结构，每一个树节点转化成简单节点
         */
        final Node<K,V> untreeify(HashMap<K,V> map) {
            // hd 是头部，tl 是尾部
            Node<K,V> hd = null, tl = null;
            for (Node<K,V> q = this; q != null; q = q.next) {
                // replacementNode 方法用于将树节点转化为简单节点，产生新的
                // 节点，原来的节点被虚拟机回收
                Node<K,V> p = map.replacementNode(q, null);
                // 第一个节点产生时，hd 指向它
                if (tl == null)
                    hd = p;
                // 将生成的新节点 p 添加到链表尾部
                else
                    tl.next = p;
                // 尾节点向后移动一位
                tl = p;
            }
            return hd;
        }
```

**moveRootToFront**

```java
        /**
         * 确保指定的 root 是桶中的第一个节点，即直接位于 table 上。
         * TreeNode 既是红黑树结构，也是双链表节后，作为红黑树结构时使用的
         * 属性是 left, right, parent，作为双链表结构时使用的是 prev, next
         * 换句话说，此方法的实现目标是保证树的根节点一定是双链表的首节点
         */
        static <K,V> void moveRootToFront(Node<K,V>[] tab, TreeNode<K,V> root) {
            int n;
            if (root != null && tab != null && (n = tab.length) > 0) {
                // 根据 root 的 hash 值定位它的索引 index
                int index = (n - 1) & root.hash;
                // 取出 table 中 index 位置的第一个节点
                TreeNode<K,V> first = (TreeNode<K,V>)tab[index];
                // 如果 root 不是第一个节点，那么将它放到第一个节点的位置
                if (root != first) {
                    // 定义 root 节点后的第一个节点
                    Node<K,V> rn;
                    // 把 index 处的元素替换为 root 根节点对象
                    tab[index] = root;
                    // 定义 root 节点的前一个节点
                    TreeNode<K,V> rp = root.prev;
                    // 如果后一个节点不为 null，那么后一个节点的 prev 设置为
                    // root 的前一个节点，即移除 root 节点
                    if ((rn = root.next) != null)
                        ((TreeNode<K,V>)rn).prev = rp;
                    // 如果 root 的前一个节点不为 null，那么前一个节点的 next
                    // 设置为 root 的后一个节点，同样是为了完成移除 root 操作
                    if (rp != null)
                        rp.next = rn;
                    // 如果 table 数组该索引上原来的元素不为 null，那么它的 prev
                    // 指向 root，即将 root 插入到 first 前面
                    if (first != null)
                        first.prev = root;
                    root.next = first;
                    root.prev = null;
                }
                // 检验TreeNode对象是否满足红黑树和双链表的特性
                assert checkInvariants(root);
            }
        }
```

**checkInvariants**

```java
        /**
         * 从 root 开始递归检查是否满足红黑树的性质，仅在检查 root 是否
         * 落在 table 上时调用
         */
        static <K,V> boolean checkInvariants(TreeNode<K,V> t) {
            TreeNode<K,V> tp = t.parent, tl = t.left, tr = t.right,
                    tb = t.prev, tn = (TreeNode<K,V>)t.next;
            if (tb != null && tb.next != t)
                return false;
            if (tn != null && tn.prev != t)
                return false;
            if (tp != null && t != tp.left && t != tp.right)
                return false;
            if (tl != null && (tl.parent != t || tl.hash > t.hash))
                return false;
            if (tr != null && (tr.parent != t || tr.hash < t.hash))
                return false;
            if (t.red && tl != null && tl.red && tr != null && tr.red)
                return false;
            if (tl != null && !checkInvariants(tl))
                return false;
            if (tr != null && !checkInvariants(tr))
                return false;
            return true;
        }
```

**split**

```java
        /**
         * Splits nodes in a tree bin into lower and upper tree bins,
         * or untreeifies if now too small. Called only from resize;
         * see above discussion about split bits and indices.
         *
         * @param map the map
         * @param tab the table for recording bin heads
         * @param index the index of the table being split
         * @param bit the bit of hash to split on
         */
        final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
            TreeNode<K,V> b = this;
            // Relink into lo and hi lists, preserving order
            TreeNode<K,V> loHead = null, loTail = null;
            TreeNode<K,V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;
            for (TreeNode<K,V> e = b, next; e != null; e = next) {
                next = (TreeNode<K,V>)e.next;
                e.next = null;
                if ((e.hash & bit) == 0) {
                    if ((e.prev = loTail) == null)
                        loHead = e;
                    else
                        loTail.next = e;
                    loTail = e;
                    ++lc;
                }
                else {
                    if ((e.prev = hiTail) == null)
                        hiHead = e;
                    else
                        hiTail.next = e;
                    hiTail = e;
                    ++hc;
                }
            }

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD)
                    tab[index] = loHead.untreeify(map);
                else {
                    tab[index] = loHead;
                    if (hiHead != null) // (else is already treeified)
                        loHead.treeify(tab);
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD)
                    tab[index + bit] = hiHead.untreeify(map);
                else {
                    tab[index + bit] = hiHead;
                    if (loHead != null)
                        hiHead.treeify(tab);
                }
            }
        }
```





***
> HashMap 小结

HashMap 是常用集合类中最复杂，也是设计很巧妙的一种数据结构。其底层的数据结构中用到了数组，链表以及红黑树，

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
> 为什么 HashMap 不是线程安全的

resize操作。。。

***
> 参考：

[30张图带你彻底理解红黑树](https://www.jianshu.com/p/e136ec79235c)

[Java集合：HashMap详解(JDK 1.8)【面试+工作】](http://www.sohu.com/a/254899015_100012573)

[Java8源码-HashMap](https://blog.csdn.net/panweiwei1994/article/details/77244920)

[JDK8：HashMap源码解析](https://blog.csdn.net/weixin_42340670)

[HashMap源码详解一篇就够](https://www.jianshu.com/p/4aa3bb16f36c)

[HashMap实现原理及源码分析](https://www.cnblogs.com/chengxiao/p/6059914.html)