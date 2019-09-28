/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package Collections;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import sun.misc.SharedSecrets;

/**
 * 基于 Map 接口实现的 hash table。此实现提供了所有可选的 map 操作，
 * 且允许 value 和 key 为 null。（HashMap 大致相当于 Hashtable，只是
 * HashMap 不支持同步，并且允许为 null。）此类不保证映射的顺序，
 * 特别是，不保证映射的顺序永远不变。
 *
 * 此实现提供了常数时间内的基本操作（get 和 put），假定 hash 函数将
 * 所有元素正确分布在桶里。集合视图的迭代时间和 HashMap 实例
 * （桶的数量）加上它的大小（映射的数量）成比例。
 *
 * HashMap 的实例有两个参数影响其性能：初始大小和加载因子。容量是
 * hash table 中桶的数量，初始容量即为 hash table 创建时桶的数量。
 * 加载因子是衡量 hash table 在其容量自动增加之前可以达到多满的参数。
 * 当hash table 中 entry 的数量超过了初始容量和加载因子的乘积（初始容量
 * 和加载因子的乘积就是当前允许的最大容量），hash table 会执行 rehash
 * 操作（内部数据结构会重建），才能让桶的数量为原来的两倍。
 *
 * 通常将加载因子的值设为 0.75 是时间和空间成本的折衷。如果设置为更高
 * 的值虽然会减少空间成本，但是会增加查询成本（查询操作会影响大部分
 * HashMap 类中的操作，包括 get 和 set）。在设置初始容量的时候就应该
 * 考虑加载因子的值和 map 中 entry 的总数，以便最大限度地减少 rehash
 * 操作的次数。如果初始容量大于最大条目数除以加载因子，则不会发生
 * rehash 操作。
 *
 * 如果大量的映射存储在 HashMap 实例中，在创建的时候设置为足够大的
 * 容量相对于按需执行自动的 rehash 操作，能更有效地存储映射关系。
 * 值得注意的是，使用多个 hashCode() 相同的键肯定会降低 hash table 的
 * 性能。为了改善这一不足，当 key 可以通过 Comparable 比较的时候，此类
 * 会进行比较和排序来帮助解开联系。
 *
 * 此实现不是同步的。如果多个线程同时访问一个 HashMap，而且至少一个
 * 线程从结构上修改了 map，那它必须保持外部同步。（结构上的修改指的
 * 是添加或删除一个或多个映射的操作，仅仅改变 map 中某个 key 相关联
 * 的 value 不是结构上的修改。一般通过对自然封装该映射的对象进行同步
 * 操作来完成，如果不存在这样的对象，应该用 Collections.synchronizedMap
 * 方法来 “包装” 该映射。最好在创建时完成该操作，以防止对 map 意外的
 * 访问：
 * Map m = Collections.synchronizedMap(new HashMap(...));
 *
 * 此类所有的集合视图返回的迭代器都支持 fail-fast：如果在迭代器创建之后
 * map 被结构性修改，且不是通过迭代器自身的 remove 方法修改的，那么
 * 迭代器会抛出 ConcurrentModificationException 异常。因此，在并发修改
 * 的情况下，迭代器会快速干净地失败，而不是在未来的某个时间里出现
 * 未知的风险和行为。
 *
 * 注意，迭代器的快速失败行为不能得到保证，一般来说，存在非同步的
 * 并发修改时，不可能作出任何坚决的保证。快速失败迭代器尽最大努力
 * 抛出 ConcurrentModificationException。因此，编写依赖于此异常的
 * 程序的做法是错误的，正确做法是：迭代器的快速失败行为应该仅用于
 * 检测bug。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Doug Lea
 * @author  Josh Bloch
 * @author  Arthur van Hoff
 * @author  Neal Gafter
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.2
 */
public class HashMap<K,V> extends java.util.AbstractMap<K,V>
        implements Map<K,V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;

    /*
     * Implementation notes.
     *
     * This map usually acts as a binned (bucketed) hash table, but
     * when bins get too large, they are transformed into bins of
     * TreeNodes, each structured similarly to those in
     * java.util.TreeMap. Most methods try to use normal bins, but
     * relay to TreeNode methods when applicable (simply by checking
     * instanceof a node).  Bins of TreeNodes may be traversed and
     * used like any others, but additionally support faster lookup
     * when overpopulated. However, since the vast majority of bins in
     * normal use are not overpopulated, checking for existence of
     * tree bins may be delayed in the course of table methods.
     *
     * Tree bins (i.e., bins whose elements are all TreeNodes) are
     * ordered primarily by hashCode, but in the case of ties, if two
     * elements are of the same "class C implements Comparable<C>",
     * type then their compareTo method is used for ordering. (We
     * conservatively check generic types via reflection to validate
     * this -- see method comparableClassFor).  The added complexity
     * of tree bins is worthwhile in providing worst-case O(log n)
     * operations when keys either have distinct hashes or are
     * orderable, Thus, performance degrades gracefully under
     * accidental or malicious usages in which hashCode() methods
     * return values that are poorly distributed, as well as those in
     * which many keys share a hashCode, so long as they are also
     * Comparable. (If neither of these apply, we may waste about a
     * factor of two in time and space compared to taking no
     * precautions. But the only known cases stem from poor user
     * programming practices that are already so slow that this makes
     * little difference.)
     *
     * Because TreeNodes are about twice the size of regular nodes, we
     * use them only when bins contain enough nodes to warrant use
     * (see TREEIFY_THRESHOLD). And when they become too small (due to
     * removal or resizing) they are converted back to plain bins.  In
     * usages with well-distributed user hashCodes, tree bins are
     * rarely used.  Ideally, under random hashCodes, the frequency of
     * nodes in bins follows a Poisson distribution
     * (http://en.wikipedia.org/wiki/Poisson_distribution) with a
     * parameter of about 0.5 on average for the default resizing
     * threshold of 0.75, although with a large variance because of
     * resizing granularity. Ignoring variance, the expected
     * occurrences of list size k are (exp(-0.5) * pow(0.5, k) /
     * factorial(k)). The first values are:
     *
     * 0:    0.60653066
     * 1:    0.30326533
     * 2:    0.07581633
     * 3:    0.01263606
     * 4:    0.00157952
     * 5:    0.00015795
     * 6:    0.00001316
     * 7:    0.00000094
     * 8:    0.00000006
     * more: less than 1 in ten million
     *
     * The root of a tree bin is normally its first node.  However,
     * sometimes (currently only upon Iterator.remove), the root might
     * be elsewhere, but can be recovered following parent links
     * (method TreeNode.root()).
     *
     * All applicable internal methods accept a hash code as an
     * argument (as normally supplied from a public method), allowing
     * them to call each other without recomputing user hashCodes.
     * Most internal methods also accept a "tab" argument, that is
     * normally the current table, but may be a new or old one when
     * resizing or converting.
     *
     * When bin lists are treeified, split, or untreeified, we keep
     * them in the same relative access/traversal order (i.e., field
     * Node.next) to better preserve locality, and to slightly
     * simplify handling of splits and traversals that invoke
     * iterator.remove. When using comparators on insertion, to keep a
     * total ordering (or as close as is required here) across
     * rebalancings, we compare classes and identityHashCodes as
     * tie-breakers.
     *
     * The use and transitions among plain vs tree modes is
     * complicated by the existence of subclass LinkedHashMap. See
     * below for hook methods defined to be invoked upon insertion,
     * removal and access that allow LinkedHashMap internals to
     * otherwise remain independent of these mechanics. (This also
     * requires that a map instance be passed to some utility methods
     * that may create new nodes.)
     *
     * The concurrent-programming-like SSA-based coding style helps
     * avoid aliasing errors amid all of the twisty pointer operations.
     */

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

    /* ---------------- Static utilities -------------- */

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

    /* ---------------- Fields -------------- */
    // 字段，变量

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

    /* ---------------- Public operations -------------- */
    // public 操作

    /**
     * 根据指定的初始容量和加载因子构造一个空的 HashMap。
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        // Float.isNaN()：此方法如果此对象所表示的值是NaN
        // （not a number），返回true，否则返回false。
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                    loadFactor);
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }

    /**
     * 根据指定的初始容量和默认的加载因子（0.75）构造一个空的 HashMap。
     *
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 根据默认的初始容量（16）和默认的加载因子（0.75）构造一个
     * 空的 HashMap。
     */
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
    }

    /**
     * 构造一个和指定 Map 具有相同映射的 HashMap。创建容量足够容纳
     * 指定 Map 中所有映射的 HashMap，加载因子为 0.75。
     *
     * @param   m the map whose mappings are to be placed in this map
     * @throws  NullPointerException if the specified map is null
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }

    /**
     * 实现 Map.putAll 和 Map 构造器。
     * 将指定 Map 的键值对插入到此 Map 中。
     *
     * @param m the map
     * @param evict false when initially constructing this map, else
     * true (relayed to method afterNodeInsertion).
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

    /**
     * 返回 map 中映射的个数。
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * 如果 map 中不包含任何映射，返回 true。
     *
     * @return true if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 返回指定 key 对应的 value，如果指定 key 不包含任何映射返回 null。
     *
     * 返回值为 null 并不一定是因为不包含指定 key 对应的映射，也有可能是
     * map 允许 value 值为 null。containsKey 方法可以用来区分这两种情况。
     *
     * @see #put(Object, Object)
     */
    public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    /**
     * 实现 Map.get 和相关方法。
     *
     * @param hash hash for key
     * @param key the key
     * @return the node, or null if none
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

    /**
     * 如果 map 包含指定 key 的映射则返回 true。
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return true if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(Object key) {
        return getNode(hash(key), key) != null;
    }

    /**
     * 将 map 中指定的 value 和指定的 key 相关联。如果 map 之前包括了对应
     * 指定 key 的映射，那么旧的 value 将被替换。
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was
     *                no mapping for key. (A null return can also indicate that the
     *                map previously associated null with key.)
     */
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * 实现 Map.put 和相关方法。
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent if true, don't change existing value
     * @param evict if false, the table is in creation mode.
     * @return previous value, or null if none
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

    /**
     * 把桶里的链式结构变成树结构。
     */
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

    /**
     * 将指定 map 的所有映射复制到此 map 中。这些映射将替代此 map 中
     * 已经存在的 key 对应的映射。
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * 删除此 map 中指定 key 对应的映射，如果其存在的话。
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was
     *                no mapping for key. (A null return can also indicate that the
     *                map previously associated null with key.)
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
            for (int i = 0; i < tab.length; ++i)
                tab[i] = null;
        }
    }

    /**
     * 如果 map 中有一个或多个 key 映射到指定 value，则返回 true
     *
     * @param value value whose presence in this map is to be tested
     * @return true if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        Node<K,V>[] tab; V v;
        if ((tab = table) != null && size > 0) {
            for (int i = 0; i < tab.length; ++i) {
                // 遍历当前桶内所有映射
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    if ((v = e.value) == value ||
                            (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回一个包含 map 中所有 key 的 Set 视图。Set 由 map 支撑，所以
     * map 中的任何改变都会影响此 set，反之亦然。如果对此 set 进行迭代
     * 的过程中 map 被改变了（迭代器自身的 remove 操作除外），迭代的
     * 结果不确定。此 set 支持元素删除，即从 map 中删除对应的映射，通过
     * Iterator.remove，Set.remove，removeAll，retainAll，和 clear 方法
     * 均可以实现。此 set 不支持 add 或者 addAll 方法。
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    final class KeySet extends AbstractSet<K> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<K> iterator()     { return new KeyIterator(); }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }
        public final Spliterator<K> spliterator() {
            return new KeySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super K> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e.key);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * 返回一个包含 map 中所有 value 的 Collection 视图。集合由 map 支撑，
     * 所以 map 中的任何改变都会影响此集合，反之亦然。如果对此集合进行
     * 迭代的过程中 map 被改变了（迭代器自身的 remove 操作除外），迭代的
     * 结果不确定。此集合支持元素删除，即从 map 中删除对应的映射，通过
     * Iterator.remove，Collection.remove，removeAll，retainAll，和
     * clear 方法均可以实现。此 Collection 不支持 add 或者 addAll 方法。
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<V> iterator()     { return new ValueIterator(); }
        public final boolean contains(Object o) { return containsValue(o); }
        public final Spliterator<V> spliterator() {
            return new ValueSpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super V> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e.value);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * 返回一个包含 map 中所有 entry 的 Set 视图。集合由 map 支撑，所以
     * map 中的任何改变都会影响此集合，反之亦然。如果对此集合进行迭代
     * 的过程中 map 被改变了（迭代器自身的 remove 操作除外），迭代的
     * 结果不确定。此集合支持元素删除，即从 map 中删除对应的映射，通过
     * Iterator.remove，Set.remove，removeAll，retainAll，和 clear 方法
     * 均可以实现。此 set 不支持 add 或者 addAll 方法。
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        // 是否包含指定对象
        public final boolean contains(Object o) {
            // 如果该对象不是 Map.Entry 对象，返回 false
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            // 从 map 中搜索指定 entry 的 key 对应的映射
            Node<K,V> candidate = getNode(hash(key), key);
            // 判断获取到的映射和指定对象是否相等
            return candidate != null && candidate.equals(e);
        }
        // 删除和指定对象匹配的 entry
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }
        public final Spliterator<Map.Entry<K,V>> spliterator() {
            return new EntrySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            Node<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K,V> e = tab[i]; e != null; e = e.next)
                        action.accept(e);
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    // Overrides of JDK8 Map extension methods
    // 重写 JDK8 的 Map 中方法

    // 返回 key 对应的 value，如果没有返回默认值 defaultValue
    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? defaultValue : e.value;
    }

    // 如果指定的键还没有和值相关联（或者被映射为 null），则将它的 value
    // 设置为指定的值并返回 null，否则返回当前值。
    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    // 删除和指定 key，指定 value 匹配的映射
    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    // 对于指定 key 匹配的键值对，用 newValue 代替 oldValue
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K,V> e; V v;
        // 获取指定 key 对应的映射，如果映射存在且其 value 等于指定的 oldValue
        // 那么将 oldValue 替换成 newValue
        if ((e = getNode(hash(key), key)) != null &&
                ((v = e.value) == oldValue || (v != null && v.equals(oldValue)))) {
            e.value = newValue;
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    // 和上一个方法不同的是，不需要匹配 value，只要找到指定 key 对应的映射，
    // 无条件替换成指定 value
    @Override
    public V replace(K key, V value) {
        Node<K,V> e;
        if ((e = getNode(hash(key), key)) != null) {
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
        return null;
    }

    /**
     * 如果指定的 key 没有和对应的 value（或者映射到 null），使用给定的
     * mapping function 计算它的 value，如果计算出来的 value 不为 null，则
     * 将其插入到 map 中。
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return value 新的 value
     */
    @Override
    public V computeIfAbsent(K key,
                             Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null)
            throw new NullPointerException();
        // key 的 hash 值
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        // 如果 size 大于阈值 threshold，或者 table 为 null，或者 table 的长度
        // 为 0，那么对 table 进行扩容
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 如果 key 对应的桶不为 null，那么在该桶内寻找指定 key 对应的节点
        if ((first = tab[i = (n - 1) & hash]) != null) {
            // 如果首节点是树节点，那么调用树节点的 getTreeNode 方法找到指定
            // key 对应的节点
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            // 否则肯定是链式结构
            else {
                Node<K,V> e = first; K k;
                // 遍历链式结构，一旦找到指定 key 对应的则跳出循环
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
            V oldValue;
            // 如果找到的 old 节点的 value 值不为 null，操作结束，返回 old 原本
            // 的 value 值
            if (old != null && (oldValue = old.value) != null) {
                afterNodeAccess(old);
                return oldValue;
            }
        }
        // 根据 mappingFunction 规则和 key 计算出 value
        V v = mappingFunction.apply(key);
        // 如果计算出来的 value 值为 null，则返回 null
        if (v == null) {
            return null;
        // 如果 old 节点不为 null，将新的 value 赋值给该节点，并返回新的 value 值
        } else if (old != null) {
            old.value = v;
            afterNodeAccess(old);
            return v;
        }
        // 如果 old 为 null，但是 t 不为空，说明此桶内为树结构，那么调用树节点
        // 的 putVal 方法
        else if (t != null)
            t.putTreeVal(this, tab, hash, key, v);
        // 否则此桶内为链式结构，创建一个链式节点，并将其插入首节点位置，
        // 插入完成后判断此桶内节点数是否超过阈值，如果超过则转化成树结构
        else {
            tab[i] = newNode(hash, key, v, first);
            if (binCount >= TREEIFY_THRESHOLD - 1)
                treeifyBin(tab, hash);
        }
        ++modCount;
        ++size;
        afterNodeInsertion(true);
        return v;
    }

    /**
     * 如果 map 中指定的 key 存在对应的 value 且不为 null，使用 function
     * 计算出新的 value。
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return value 新的 value 值
     */
    public V computeIfPresent(K key,
                              BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        Node<K,V> e; V oldValue;
        // 计算 key 的 hash 值
        int hash = hash(key);
        // 如果指定 key 对应的节点不为 null 且其 value 不为 null，继续以下操作，
        // 否则返回 null
        if ((e = getNode(hash, key)) != null &&
                (oldValue = e.value) != null) {
            V v = remappingFunction.apply(key, oldValue);
            // 如果计算出来的 value 值不为 null，则将其赋值给指定节点，否则将
            // 指定节点删除
            if (v != null) {
                e.value = v;
                afterNodeAccess(e);
                return v;
            }
            else
                removeNode(hash, key, null, false, true);
        }
        return null;
    }

    /**
     * 利用指定的 key 和它当前的 value（如果当前不存在映射则 value 为 null）
     * 计算对应的映射。
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return
     */
    @Override
    public V compute(K key,
                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        // 计算 key 的 hash 值
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        // 如果指定 key 对应的节点不为 null 且其 value 不为 null，继续以下操作，
        // 否则返回 null
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 如果 key 对应的桶不为 null，那么在该桶内寻找指定 key 对应的节点
        if ((first = tab[i = (n - 1) & hash]) != null) {
            // 如果首节点是树节点，那么调用树节点的 getTreeNode 方法找到指定
            // key 对应的节点
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            // 否则肯定是链式结构
            else {
                Node<K,V> e = first; K k;
                // 遍历链式结构，一旦找到指定 key 对应的则跳出循环
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        // 如果 old 节点为 null，那么 oldValue 也为 null，否则为 old 的 value 值
        V oldValue = (old == null) ? null : old.value;
        V v = remappingFunction.apply(key, oldValue);
        // 如果 old 不为 null
        if (old != null) {
            // 如果 v 不为 null，将计算出来的 value 值赋值给此节点的 value
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            // 如果计算出来的 v 为 null，则删除该节点
            else
                removeNode(hash, key, null, false, true);
        }
        // 如果 old 为 null，即原来不存在指定 key 对应的节点，但计算出来的 v
        // 不为 null，那么将新的节点插入到该桶里面
        else if (v != null) {
            // 如果是树结构，调用树结构的 putTreeVal 插入
            if (t != null)
                t.putTreeVal(this, tab, hash, key, v);
            // 如果是链式结构，将新的节点插入到该桶的首节点位置，然后判断
            // 是否要转化成树结构
            else {
                tab[i] = newNode(hash, key, v, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return v;
    }

    /**
     * 如果指定的 key 没有关联的 value 或者关联的 value 为 null，把它和
     * 给定的非 null 值关联起来。如果有关联的 value，用给定的 function 的
     * 返回值替换原来的 value，如果 function 的结果为 null，那么删除此映射。
     */
    @Override
    public V merge(K key, V value,
                   BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null)
            throw new NullPointerException();
        if (remappingFunction == null)
            throw new NullPointerException();
        // 计算出 key 的 hash 值
        int hash = hash(key);
        Node<K,V>[] tab; Node<K,V> first; int n, i;
        int binCount = 0;
        TreeNode<K,V> t = null;
        Node<K,V> old = null;
        // 如果 size 大于阈值，或者 table 为 null，或者 tab 的长度为 0，那么对
        // table 扩容
        if (size > threshold || (tab = table) == null ||
                (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 如果 key 对应的桶不为 null，那么在桶内查找指定的映射
        if ((first = tab[i = (n - 1) & hash]) != null) {
            // 如果节点为树节点，调用树节点的 getTreeNode 查找
            if (first instanceof TreeNode)
                old = (t = (TreeNode<K,V>)first).getTreeNode(hash, key);
            // 否则该桶内为链式结构，遍历链式结构的所有节点，知道找到节点
            else {
                Node<K,V> e = first; K k;
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                } while ((e = e.next) != null);
            }
        }
        // 如果存在该节点
        if (old != null) {
            V v;
            // 如果该节点原先的值不为 null，那么使用 remappingFunction 计算出
            // 新的 value，如果原先的 value 值为 null，那么将参数里指定的 value
            // 值赋值给新的 value
            if (old.value != null)
                v = remappingFunction.apply(old.value, value);
            else
                v = value;
            // 如果新的 value 不为 null，那么将该节点的 value 值赋值为新的 value，
            // 否则删除该节点
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            else
                removeNode(hash, key, null, false, true);
            return v;
        }
        // 如果不存在该节点，且参数里指定的 value 不为 null，创造新的节点。
        // 若 t 不为 null，即桶内数据结构为树结构，则调用树节点的 putTreeVal
        // 方法，桶内为链式结构时，将其插入到链表头部，并在插入完成后判断
        // 是否将其转化成树结构
        if (value != null) {
            if (t != null)
                t.putTreeVal(this, tab, hash, key, value);
            else {
                tab[i] = newNode(hash, key, value, first);
                if (binCount >= TREEIFY_THRESHOLD - 1)
                    treeifyBin(tab, hash);
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return value;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Node<K,V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next)
                    action.accept(e.key, e.value);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * 对于 map 中的每一个 entry，将 value 替换成 BiFunction 返回的值
     */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Node<K,V>[] tab;
        if (function == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    e.value = function.apply(e.key, e.value);
                }
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    /* ------------------------------------------------------------ */
    // Cloning and serialization
    // 克隆和序列化

    /**
     * 返回一个 HashMap 的浅克隆：key 和 value 对象本身不克隆。
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        HashMap<K,V> result;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // These methods are also used when serializing HashSets
    // 这些方法在序列化 HashSet 的时候也要使用
    final float loadFactor() { return loadFactor; }
    final int capacity() {
        return (table != null) ? table.length :
                (threshold > 0) ? threshold :
                        DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * 把 HashMap 实例的状态保存在流里面。（例如，对它序列化）
     *
     * @serialData The capacity of the HashMap (the length of the bucket
     *                  array) is emitted (int), followed by the size (an int, the
     *                  number of key-value mappings), followed by the key
     *                  (Object) and value (Object) for each key-value mapping.
     *                  The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        int buckets = capacity();
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }

    /**
     * 从流数据里重建 HashMap。（即反序列化它）
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        reinitialize();
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new InvalidObjectException("Illegal load factor: " +
                    loadFactor);
        s.readInt();                // Read and ignore number of buckets
        int mappings = s.readInt(); // Read number of mappings (size)
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " +
                    mappings);
        else if (mappings > 0) { // (if zero, use defaults)
            // Size the table using given load factor only if within
            // range of 0.25...4.0
            float lf = Math.min(Math.max(0.25f, loadFactor), 4.0f);
            float fc = (float)mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                    DEFAULT_INITIAL_CAPACITY :
                    (fc >= MAXIMUM_CAPACITY) ?
                            MAXIMUM_CAPACITY :
                            tableSizeFor((int)fc));
            float ft = (float)cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ?
                    (int)ft : Integer.MAX_VALUE);

            // Check Map.Entry[].class since it's the nearest public type to
            // what we're actually creating.
            SharedSecrets.getJavaOISAccess().checkArray(s, Map.Entry[].class, cap);
            @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] tab = (Node<K,V>[])new Node[cap];
            table = tab;

            // Read the keys and values, and put the mappings in the HashMap
            for (int i = 0; i < mappings; i++) {
                @SuppressWarnings("unchecked")
                K key = (K) s.readObject();
                @SuppressWarnings("unchecked")
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }

    /* ------------------------------------------------------------ */
    // iterators
    // 迭代器

    abstract class HashIterator {
        Node<K,V> next;        // next entry to return
        Node<K,V> current;     // current entry
        int expectedModCount;  // for fast-fail
        int index;             // current slot

        HashIterator() {
            expectedModCount = modCount;
            Node<K,V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) { // advance to first entry
                do {} while (index < t.length && (next = t[index++]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K,V> nextNode() {
            Node<K,V>[] t;
            Node<K,V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {} while (index < t.length && (next = t[index++]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K,V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            K key = p.key;
            removeNode(hash(key), key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class KeyIterator extends HashIterator
            implements Iterator<K> {
        public final K next() { return nextNode().key; }
    }

    final class ValueIterator extends HashIterator
            implements Iterator<V> {
        public final V next() { return nextNode().value; }
    }

    final class EntryIterator extends HashIterator
            implements Iterator<Map.Entry<K,V>> {
        public final Map.Entry<K,V> next() { return nextNode(); }
    }

    /* ------------------------------------------------------------ */
    // spliterators

    static class HashMapSpliterator<K,V> {
        final HashMap<K,V> map;
        Node<K,V> current;          // current node
        int index;                  // current index, modified on advance/split
        int fence;                  // one past last index
        int est;                    // size estimate
        int expectedModCount;       // for comodification checks

        HashMapSpliterator(HashMap<K,V> m, int origin,
                           int fence, int est,
                           int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K,V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                Node<K,V>[] tab = m.table;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<K> {
        KeySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new KeySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.key);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        K k = current.key;
                        current = current.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<V> {
        ValueSpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new ValueSpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p.value);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        V v = current.value;
                        current = current.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K,V>
            extends HashMapSpliterator<K,V>
            implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(HashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ? null :
                    new EntrySpliterator<>(map, lo, index = mid, est >>>= 1,
                            expectedModCount);
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K,V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            HashMap<K,V> m = map;
            Node<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else
                mc = expectedModCount;
            if (tab != null && tab.length >= hi &&
                    (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K,V> p = current;
                current = null;
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        action.accept(p);
                        p = p.next;
                    }
                } while (p != null || i < hi);
                if (m.modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            Node<K,V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Node<K,V> e = current;
                        current = current.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount)
                            throw new ConcurrentModificationException();
                        return true;
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT;
        }
    }

    /* ------------------------------------------------------------ */
    // LinkedHashMap support
    // LinkedHashMap 支持


    /*
     * 接下来的 protected 方法用来被 LinkedHashMap 覆盖，但不会用于其他
     * 子类。几乎所有其它的内部方法都是 protected 方法，但被定义成 final
     * 类型，所以可以被 LinkedHashMap，视图类和 HashSet 使用。
     */

    // 创建单个（非树结构）节点
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }

    // 用于将树节点转化成简单的节点
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }

    // 创建一个树节点
    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    // For treeifyBin
    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    /**
     * 重置为初始默认的状态，由 clone 和 readObject 调用
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }

    // Callbacks to allow LinkedHashMap post-actions
    void afterNodeAccess(Node<K,V> p) { }
    void afterNodeInsertion(boolean evict) { }
    void afterNodeRemoval(Node<K,V> p) { }

    // Called only from writeObject, to ensure compatible ordering.
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        Node<K,V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    // 树结构的桶

    /**
     * 树结构桶内的 entry。继承自 LinkedHashMap.Entry（此类继承自 Node），
     * 所以可能用来当成简单节点或链表节点的扩展。
     *
     * 红黑树（平衡二叉查找树）的性质：
     * 根节点是黑色
     * 每个叶节点（NIL节点，即空节点）是黑色的
     * 每个红色节点的两个子节点都是黑色。（从每个叶子到根的所有路径上不能
     * 有两个连续的红色节点）
     * 从任一节点到其每个叶子的所有路径都包含相同数目的黑色节点
     *
     * 红黑树通过“变色”和“旋转”来维护红黑树的规则，变色就是让黑的变成
     * 红的，红的变成黑的，旋转又分为“左旋转”和“右旋转”
     * 左旋转：逆时针旋转，使父节点被自己的右子节点取代，自己成为左子节点
     * 右旋转：顺时针旋转，使父节点被左子节点取代，自己成为右子节点
     *
     * 新插入的节点，在调整之前均为红色
     */
    static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
        // 父节点
        TreeNode<K,V> parent;  // red-black tree links
        // 左子节点
        TreeNode<K,V> left;
        // 右子节点
        TreeNode<K,V> right;
        // 前一个节点
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        // 是否是红色
        boolean red;
        TreeNode(int hash, K key, V val, Node<K,V> next) {
            super(hash, key, val, next);
        }

        /**
         * 返回根节点
         */
        final TreeNode<K,V> root() {
            // 不断检查节点的 parent 是否为 null
            for (TreeNode<K,V> r = this, p;;) {
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }

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

        /**
         * 树结构的查找函数
         */
        final TreeNode<K,V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k, null);
        }

        /**
         * 当 hash 值相等或者无法比较时，用此方法来比较两个对象。返回值要么
         * 大于 0，要么小于 0，不会等于 0
         * 比较两个对象的类名，如果 a 不等于 nul，b 不等于 null 且 a 和 b 的类名
         * 不相等，则返回类名（String）的比较结果，否则调用本地方法为两个
         * 对象生成hashCode 值，再进行比较，hashCode 相等的话返回 -1
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null ||
                    (d = a.getClass().getName().
                            compareTo(b.getClass().getName())) == 0)
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
                        -1 : 1);
            return d;
        }

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

        /* ------------------------------------------------------------ */
        // Red-black tree methods, all adapted from CLR

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
    }

}
