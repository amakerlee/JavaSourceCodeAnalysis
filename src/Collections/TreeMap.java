package Collections;
/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.*;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 一个基于红黑树和 NavigableMap 的实现。键值对根据 Comparable 的自然
 * 顺序或者创建时提供的 Comparator 比较器排序，这取决于创建时使用了
 * 哪一个构造函数。
 *
 * 此实现保证了 containsKey, get, put 和 remove 方法在 log(n) 的时间内完成。
 *
 * 注意这个实现不是同步的。如果多个线程同时访问一个 Map，并且至少有一个
 * 线程在结构上修改了 Map，那么它必须从外部同步。（结构上的修改指的是
 * 添加，删除一个或多个键值对，仅仅改变已经存在的 key 对应的值不算结构上
 * 的修改。）这通常是通过一些自然封装了映射的对象进行同步来实现的。
 *
 * 可以通过 Collections.synchronizedSortedMap 来实现，如下：
 * SortedMap m = Collections.synchronizedSortedMap(new TreeMap(...));
 *
 * 此类中所有创建集合视图的方法范湖的迭代器都支持 fast-fail：如果在迭代器
 * 创建之后 map 被结构上修改，除非是迭代器自身的 remove 方法，迭代器都会
 * 抛出 ConcurrentModificationException 异常。因此，面对并发修改的时候，
 * 迭代器会快速干净地失败，而不是在未来出现不确定的行为。
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
 * @author  Josh Bloch and Doug Lea
 * @see java.util.Map
 * @see HashMap
 * @see Hashtable
 * @see Comparable
 * @see Comparator
 * @see java.util.Collection
 * @since 1.2
 */

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.SortedMap;

public class TreeMap<K,V>
        extends AbstractMap<K,V>
        implements NavigableMap<K,V>, Cloneable, java.io.Serializable
{
    /**
     * 确定 TreeMap 中元素顺序的比较器，如果使用元素的自然顺序排序，此变量
     * 为 null。
     *
     * @serial
     */
    private final Comparator<? super K> comparator;

    /**
     * root，根节点
     */
    private transient TreeMap.Entry<K,V> root;

    /**
     * 树中 entry 的数量。
     */
    private transient int size = 0;

    /**
     * 结构性修改的次数。
     */
    private transient int modCount = 0;

    /**
     * 构造函数
     */
    public TreeMap() {
        comparator = null;
    }

    /**
     * 构造函数
     */
    public TreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    /**
     * 构造函数
     *
     * @param  m the map whose mappings are to be placed in this map
     * @throws ClassCastException if the keys in m are not {@link Comparable},
     *         or are not mutually comparable
     * @throws NullPointerException if the specified map is null
     */
    public TreeMap(java.util.Map<? extends K, ? extends V> m) {
        comparator = null;
        putAll(m);
    }

    /**
     * 构造函数
     *
     * @param  m the sorted map whose mappings are to be placed in this map,
     *         and whose comparator is to be used to sort this map
     * @throws NullPointerException if the specified map is null
     */
    public TreeMap(SortedMap<K, ? extends V> m) {
        comparator = m.comparator();
        try {
            buildFromSorted(m.size(), m.entrySet().iterator(), null, null);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }
    }


    // Query Operations
    // 查询操作

    /**
     * 返回此 Map 中键值对的数量。
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * 如果包含指定 key 返回 true。
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the
     *         specified key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * 如果有一个或多个 key 映射到指定 value 则返回 true。在大多数实现中，
     * 此操作需要相当于 Map 大小的线性时间。
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if a mapping to {@code value} exists;
     *         {@code false} otherwise
     * @since 1.2
     */
    public boolean containsValue(Object value) {
        for (TreeMap.Entry<K,V> e = getFirstEntry(); e != null; e = successor(e))
            if (valEquals(value, e.value))
                return true;
        return false;
    }

    /**
     * 返回指定 key 对应的 value，如果不存在这样的映射则返回 null。
     *
     * 返回 null 并不代表不存在映射，有可能是 key 对应的 value 本身就为 null。
     * 可以用 containsKey 方法来区分这两种情况。
     *
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public V get(Object key) {
        TreeMap.Entry<K,V> p = getEntry(key);
        return (p==null ? null : p.value);
    }

    public Comparator<? super K> comparator() {
        return comparator;
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public K firstKey() {
        return key(getFirstEntry());
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public K lastKey() {
        return key(getLastEntry());
    }

    /**
     * 把指定 Map 中的所有映射复制到此 Map 中。如果此 Map 已经存在某个键值对，
     * 则替换掉原键值对。
     *
     * @param  map mappings to be stored in this map
     * @throws ClassCastException if the class of a key or value in
     *         the specified map prevents it from being stored in this map
     * @throws NullPointerException if the specified map is null or
     *         the specified map contains a null key and this map does not
     *         permit null keys
     */
    public void putAll(java.util.Map<? extends K, ? extends V> map) {
        int mapSize = map.size();
        if (size==0 && mapSize!=0 && map instanceof SortedMap) {
            Comparator<?> c = ((SortedMap<?,?>)map).comparator();
            if (c == comparator || (c != null && c.equals(comparator))) {
                ++modCount;
                try {
                    buildFromSorted(mapSize, map.entrySet().iterator(),
                            null, null);
                } catch (java.io.IOException cannotHappen) {
                } catch (ClassNotFoundException cannotHappen) {
                }
                return;
            }
        }
        super.putAll(map);
    }

    /**
     * 返回指定 key 对应的 entry，如果不存在则返回 null。
     *
     * @return this map's entry for the given key, or {@code null} if the map
     *         does not contain an entry for the key
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    final TreeMap.Entry<K,V> getEntry(Object key) {
        // Offload comparator-based version for sake of performance
        // 根据 Map 中指定的比较器获取对应的 entry。
        if (comparator != null)
            return getEntryUsingComparator(key);
        if (key == null)
            throw new NullPointerException();
        @SuppressWarnings("unchecked")
        // 根据二叉检索树的性质，向下搜索，直到找到为止。否则返回 null
        Comparable<? super K> k = (Comparable<? super K>) key;
        TreeMap.Entry<K,V> p = root;
        while (p != null) {
            int cmp = k.compareTo(p.key);
            if (cmp < 0)
                p = p.left;
            else if (cmp > 0)
                p = p.right;
            else
                return p;
        }
        return null;
    }

    /**
     * 根据 Map 中指定的比较器获取对应的 entry。和 getEntry 区分的是
     * 为了性能。（并非所有类似的操作都值得这样做。）
     */
    final TreeMap.Entry<K,V> getEntryUsingComparator(Object key) {
        @SuppressWarnings("unchecked")
        K k = (K) key;
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            TreeMap.Entry<K,V> p = root;
            while (p != null) {
                int cmp = cpr.compare(k, p.key);
                if (cmp < 0)
                    p = p.left;
                else if (cmp > 0)
                    p = p.right;
                else
                    return p;
            }
        }
        return null;
    }

    /**
     * 获取指定 key 对应的 entry；如果不存在这样的 entry，返回比指定
     * key 大的最小的 key 对应的 entry；如果还是不存在，则返回 null。
     */
    final TreeMap.Entry<K,V> getCeilingEntry(K key) {
        TreeMap.Entry<K,V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                // 存在左子树，继续向左子树查找，不存在时当前节点即为要找的节点
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else if (cmp > 0) {
                if (p.right != null) {
                    p = p.right;
                } else {
                    // 要查找的 key 比当前节点大，而当前节点不存在右子树，那么
                    // 比当前节点 key 大的最小的 key 在父节点或父节点之上。
                    TreeMap.Entry<K,V> parent = p.parent;
                    TreeMap.Entry<K,V> ch = p;
                    // ch 为其父节点的左子节点时，其父节点即为要找的节点。
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;
        }
        return null;
    }

    /**
     * 获取指定 key 对应的 entry；如果不存在这样的 entry，返回比指定
     * key 小的最大的 key 对应的 entry；如果还是不存在，则返回 null。
     */
    final TreeMap.Entry<K,V> getFloorEntry(K key) {
        TreeMap.Entry<K,V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                // 存在右子树，继续向右子树查找，不存在时当前节点即为要找的节点
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else if (cmp < 0) {
                if (p.left != null) {
                    p = p.left;
                } else {
                    // 要查找的 key 比当前节点小，而当前节点不存在左子树，那么
                    // 比当前节点 key 小的最大的 key 在父节点或父节点之上。
                    TreeMap.Entry<K,V> parent = p.parent;
                    TreeMap.Entry<K,V> ch = p;
                    // ch 为其父节点的右子节点时，其父节点即为要找的节点。
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;

        }
        return null;
    }

    /**
     * 获取比指定 key 大的最小的 key 对应的 entry；如果不存在这样的 entry，
     * 返回 null。
     * 实现方法和 getCeilingEntry 类似，不同点在于当前节点 key 和指定 key
     * 相等时，还要继续向右查找。
     */
    final TreeMap.Entry<K,V> getHigherEntry(K key) {
        TreeMap.Entry<K,V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else {
                if (p.right != null) {
                    p = p.right;
                } else {
                    TreeMap.Entry<K,V> parent = p.parent;
                    TreeMap.Entry<K,V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * 获取比指定 key 小的最大的 key 对应的 entry；如果不存在这样的 entry，
     * 返回 null。
     * 实现方法和 getFloorEntry 类似，不同点在于当前节点 key 和指定 key
     * 相等时，还要继续向左查找。
     */
    final TreeMap.Entry<K,V> getLowerEntry(K key) {
        TreeMap.Entry<K,V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else {
                if (p.left != null) {
                    p = p.left;
                } else {
                    TreeMap.Entry<K,V> parent = p.parent;
                    TreeMap.Entry<K,V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * 将指定的 key 和指定的 value 作为新的 entry 插入 Map 中。如果 Map
     * 中已经存在指定 key 的映射，那么将其对应的 value 体验成指定 value。
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public V put(K key, V value) {
        TreeMap.Entry<K,V> t = root;
        // 如果根节点为 null
        if (t == null) {
            // 对 key 是否为 null 进行检查 type
            compare(key, key); // type (and possibly null) check
            // 创建一个根节点，返回 null
            root = new TreeMap.Entry<>(key, value, null);
            size = 1;
            modCount++;
            return null;
        }
        int cmp;
        TreeMap.Entry<K,V> parent;
        // split comparator and comparable paths
        Comparator<? super K> cpr = comparator;
        // 如果存在比较器
        if (cpr != null) {
            do {
                // 记录要插入节点的父节点，在创建新节点时使用
                parent = t;
                // 比较指定 key 和当前节点 key 的大小，并不断向下搜索
                cmp = cpr.compare(key, t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                // 如果已经存在 key，则更新其对应的 value 即可
                else
                    return t.setValue(value);
            } while (t != null);
        }
        // 如果不存在比较器
        else {
            if (key == null)
                throw new NullPointerException();
            @SuppressWarnings("unchecked")
            Comparable<? super K> k = (Comparable<? super K>) key;
            do {
                parent = t;
                cmp = k.compareTo(t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    return t.setValue(value);
            } while (t != null);
        }
        // 如果原先不存在指定 key，那么创建一个新的节点，并将其父节点
        // 指针指向 parent
        TreeMap.Entry<K,V> e = new TreeMap.Entry<>(key, value, parent);
        if (cmp < 0)
            parent.left = e;
        else
            parent.right = e;
        // 插入新节点后，为了保持红黑树平衡进行调整
        fixAfterInsertion(e);
        size++;
        modCount++;
        return null;
    }

    /**
     * 删除指定 key 对应的映射，如果其存在的话。
     *
     * @param  key key for which mapping should be removed
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     * @throws ClassCastException if the specified key cannot be compared
     *         with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     */
    public V remove(Object key) {
        TreeMap.Entry<K,V> p = getEntry(key);
        if (p == null)
            return null;

        V oldValue = p.value;
        deleteEntry(p);
        return oldValue;
    }

    /**
     * 删除 Map 中所有的映射。
     * 此函数调用后 Map 为空。
     */
    public void clear() {
        modCount++;
        size = 0;
        root = null;
    }

    /**
     * 返回 TreeMap 实例的一个浅拷贝。（key 和 value 对象本身不会克隆）
     *
     * @return a shallow copy of this map
     */
    public Object clone() {
        TreeMap<?,?> clone;
        try {
            clone = (TreeMap<?,?>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }

        // Put clone into "virgin" state (except for comparator)
        clone.root = null;
        clone.size = 0;
        clone.modCount = 0;
        clone.entrySet = null;
        clone.navigableKeySet = null;
        clone.descendingMap = null;

        // Initialize clone with our mappings
        try {
            clone.buildFromSorted(size, entrySet().iterator(), null, null);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }

        return clone;
    }

    // NavigableMap API methods
    // 实现 NavigableMap 接口的方法

    /**
     * @since 1.6
     */
    public Map.Entry<K,V> firstEntry() {
        return exportEntry(getFirstEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K,V> lastEntry() {
        return exportEntry(getLastEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K,V> pollFirstEntry() {
        TreeMap.Entry<K,V> p = getFirstEntry();
        Map.Entry<K,V> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K,V> pollLastEntry() {
        TreeMap.Entry<K,V> p = getLastEntry();
        Map.Entry<K,V> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K,V> lowerEntry(K key) {
        return exportEntry(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public K lowerKey(K key) {
        return keyOrNull(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K,V> floorEntry(K key) {
        return exportEntry(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public K floorKey(K key) {
        return keyOrNull(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K,V> ceilingEntry(K key) {
        return exportEntry(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public K ceilingKey(K key) {
        return keyOrNull(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K,V> higherEntry(K key) {
        return exportEntry(getHigherEntry(key));
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @since 1.6
     */
    public K higherKey(K key) {
        return keyOrNull(getHigherEntry(key));
    }

    // Views
    // 视图

    /**
     * 这几个属性用来创建 entry set 视图的实例。视图与状态无关，所以
     * 没有理由创建多次。
     */
    private transient TreeMap.EntrySet entrySet;
    private transient TreeMap.KeySet<K> navigableKeySet;
    private transient NavigableMap<K,V> descendingMap;

    /**
     * 返回一个包含所有 key 的 Set 视图。
     *
     * 该 Set 的迭代器按升序返回 key。
     *
     * 该 Set 由 Map 支撑，所以 Map 的任何改变都会影响返回的 Set，反之
     * 亦然。在迭代过程中如果 Map 改变了（除非是调用迭代器自身的
     * remove 方法），迭代的结果不确定。该 Set 支持元素删除，同时将会
     * 从 Map 中删除相应的映射，通过 Iterator.remove, Set.remove,
     *  removeAll, retainAll, 和 clear 方法完成这一操作。不支持 add 或者
     *  addAll 操作。
     */
    public java.util.Set<K> keySet() {
        return navigableKeySet();
    }

    /**
     * @since 1.6
     */
    public java.util.NavigableSet<K> navigableKeySet() {
        TreeMap.KeySet<K> nks = navigableKeySet;
        // 调用 TreeMap 的 KeySet 方法返回键集合。
        return (nks != null) ? nks : (navigableKeySet = new TreeMap.KeySet<>(this));
    }

    /**
     * @since 1.6
     */
    public java.util.NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    /**
     * 返回一个包含所有 value 的 Collection 视图。
     *
     * 该 Set 的迭代器按 key 升序返回 key 对应的 value。
     *
     * 该 Collection 由 Map 支撑，所以 Map 的任何改变都会影响返回的集合，
     * 反之亦然。在迭代过程中如果 Map 改变了（除非是调用迭代器自身的
     * remove 方法），迭代的结果不确定。该 Cellection 支持元素删除，同时
     * 将会从 Map 中删除相应的映射，通过 Iterator.remove, Collection.remove,
     *  removeAll, retainAll, 和 clear 方法完成这一操作。不支持 add 或者
     *  addAll 操作。
     */
    public java.util.Collection<V> values() {
        // AbstractMap 中存在 values 属性，用于保存所有的 value。
        Collection<V> vs = values;
        if (vs == null) {
            // 调用 TreeMap 的 values 方法返回 value 的 Collection
            vs = new TreeMap.Values();
            values = vs;
        }
        return vs;
    }

    /**
     * 返回一个包含所有映射的 Set 视图。
     *
     * 该 Set 的迭代器按 key 的升序返回 entry。
     *
     * 该 Set 由 Map 支撑，所以 Map 的任何改变都会影响返回的 Set，反之
     * 亦然。在迭代过程中如果 Map 改变了（除非是调用迭代器自身的
     * remove 方法），迭代的结果不确定。该 Set 支持元素删除，同时将会
     * 从 Map 中删除相应的映射，通过 Iterator.remove, Set.remove,
     *  removeAll, retainAll, 和 clear 方法完成这一操作。不支持 add 或者
     *  addAll 操作。
     */
    public java.util.Set<Map.Entry<K,V>> entrySet() {
        TreeMap.EntrySet es = entrySet;
        // 同样调用 TreeMap 的 EntrySet 方法返回所有 entry 的集合视图。
        return (es != null) ? es : (entrySet = new TreeMap.EntrySet());
    }

    /**
     * @since 1.6
     */
    public NavigableMap<K, V> descendingMap() {
        NavigableMap<K, V> km = descendingMap;
        return (km != null) ? km :
                (descendingMap = new TreeMap.DescendingSubMap<>(this,
                        true, null, true,
                        true, null, true));
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} or {@code toKey} is
     *         null and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                                    K toKey,   boolean toInclusive) {
        return new TreeMap.AscendingSubMap<>(this,
                false, fromKey, fromInclusive,
                false, toKey,   toInclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code toKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K,V> headMap(K toKey, boolean inclusive) {
        return new TreeMap.AscendingSubMap<>(this,
                true,  null,  true,
                false, toKey, inclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K,V> tailMap(K fromKey, boolean inclusive) {
        return new TreeMap.AscendingSubMap<>(this,
                false, fromKey, inclusive,
                true,  null,    true);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} or {@code toKey} is
     *         null and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K,V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code toKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K,V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if {@code fromKey} is null
     *         and this map uses natural ordering, or its comparator
     *         does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K,V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    @Override
    // 将指定 key 的 value 替换成新的 value。指定 key 和 oldValue 都必须和
    // 需要操作的 entry 对应。成功返回 true，失败返回 false。
    public boolean replace(K key, V oldValue, V newValue) {
        TreeMap.Entry<K,V> p = getEntry(key);
        if (p!=null && Objects.equals(oldValue, p.value)) {
            p.value = newValue;
            return true;
        }
        return false;
    }

    @Override
    // 直接替换指定 key 对应的 value，不用考虑原先的 value。成功则返回
    // 原先的 value，失败返回 null。
    public V replace(K key, V value) {
        TreeMap.Entry<K,V> p = getEntry(key);
        if (p!=null) {
            V oldValue = p.value;
            p.value = value;
            return oldValue;
        }
        return null;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        int expectedModCount = modCount;
        for (TreeMap.Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            action.accept(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    // 根据给定的 function 计算新的 value 值并替换
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        int expectedModCount = modCount;

        for (TreeMap.Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            e.value = function.apply(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    // View class support
    // 视图支撑函数

    // 包含所有 value 的 Collection 集合类
    class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new TreeMap.ValueIterator(getFirstEntry());
        }

        public int size() {
            return TreeMap.this.size();
        }

        public boolean contains(Object o) {
            return TreeMap.this.containsValue(o);
        }

        public boolean remove(Object o) {
            for (TreeMap.Entry<K,V> e = getFirstEntry(); e != null; e = successor(e)) {
                if (valEquals(e.getValue(), o)) {
                    deleteEntry(e);
                    return true;
                }
            }
            return false;
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<V> spliterator() {
            return new TreeMap.ValueSpliterator<K,V>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    // 包含所有 entry 的 Set 集合类
    class EntrySet extends java.util.AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new TreeMap.EntryIterator(getFirstEntry());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object value = entry.getValue();
            TreeMap.Entry<K,V> p = getEntry(entry.getKey());
            return p != null && valEquals(p.getValue(), value);
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object value = entry.getValue();
            // 找到指定 key 对应的 entry
            TreeMap.Entry<K,V> p = getEntry(entry.getKey());
            if (p != null && valEquals(p.getValue(), value)) {
                // 删除 entry 并调整红黑树保持平衡
                deleteEntry(p);
                return true;
            }
            return false;
        }

        public int size() {
            return TreeMap.this.size();
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            return new TreeMap.EntrySpliterator<K,V>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    /*
     * 与 Values 和 EntrySet 不同，KeySet 类是静态的，它委托给一个
     * NavigableMap 来允许子映射使用。
     */

    Iterator<K> keyIterator() {
        return new TreeMap.KeyIterator(getFirstEntry());
    }

    Iterator<K> descendingKeyIterator() {
        return new TreeMap.DescendingKeyIterator(getLastEntry());
    }

    static final class KeySet<E> extends java.util.AbstractSet<E> implements java.util.NavigableSet<E> {
        private final NavigableMap<E, ?> m;
        KeySet(NavigableMap<E,?> map) { m = map; }

        public Iterator<E> iterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E,?>)m).keyIterator();
            else
                return ((TreeMap.NavigableSubMap<E,?>)m).keyIterator();
        }

        public Iterator<E> descendingIterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E,?>)m).descendingKeyIterator();
            else
                return ((TreeMap.NavigableSubMap<E,?>)m).descendingKeyIterator();
        }

        public int size() { return m.size(); }
        public boolean isEmpty() { return m.isEmpty(); }
        public boolean contains(Object o) { return m.containsKey(o); }
        public void clear() { m.clear(); }
        public E lower(E e) { return m.lowerKey(e); }
        public E floor(E e) { return m.floorKey(e); }
        public E ceiling(E e) { return m.ceilingKey(e); }
        public E higher(E e) { return m.higherKey(e); }
        public E first() { return m.firstKey(); }
        public E last() { return m.lastKey(); }
        public Comparator<? super E> comparator() { return m.comparator(); }
        public E pollFirst() {
            Map.Entry<E,?> e = m.pollFirstEntry();
            return (e == null) ? null : e.getKey();
        }
        public E pollLast() {
            Map.Entry<E,?> e = m.pollLastEntry();
            return (e == null) ? null : e.getKey();
        }
        public boolean remove(Object o) {
            int oldSize = size();
            m.remove(o);
            return size() != oldSize;
        }
        public java.util.NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                                E toElement, boolean toInclusive) {
            return new TreeMap.KeySet<>(m.subMap(fromElement, fromInclusive,
                    toElement,   toInclusive));
        }
        public java.util.NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new TreeMap.KeySet<>(m.headMap(toElement, inclusive));
        }
        public java.util.NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new TreeMap.KeySet<>(m.tailMap(fromElement, inclusive));
        }
        public java.util.SortedSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }
        public java.util.SortedSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }
        public java.util.SortedSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }
        public java.util.NavigableSet<E> descendingSet() {
            return new TreeMap.KeySet<>(m.descendingMap());
        }

        public Spliterator<E> spliterator() {
            return keySpliteratorFor(m);
        }
    }

    /**
     * TreeMap 迭代器的基本类
     */
    abstract class PrivateEntryIterator<T> implements Iterator<T> {
        TreeMap.Entry<K,V> next;
        TreeMap.Entry<K,V> lastReturned;
        int expectedModCount;

        PrivateEntryIterator(TreeMap.Entry<K,V> first) {
            expectedModCount = modCount;
            lastReturned = null;
            next = first;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final TreeMap.Entry<K,V> nextEntry() {
            TreeMap.Entry<K,V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = successor(e);
            lastReturned = e;
            return e;
        }

        final TreeMap.Entry<K,V> prevEntry() {
            TreeMap.Entry<K,V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = predecessor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            // deleted entries are replaced by their successors
            if (lastReturned.left != null && lastReturned.right != null)
                next = lastReturned;
            deleteEntry(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends TreeMap.PrivateEntryIterator<Map.Entry<K,V>> {
        EntryIterator(TreeMap.Entry<K,V> first) {
            super(first);
        }
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    final class ValueIterator extends TreeMap.PrivateEntryIterator<V> {
        ValueIterator(TreeMap.Entry<K,V> first) {
            super(first);
        }
        public V next() {
            return nextEntry().value;
        }
    }

    final class KeyIterator extends TreeMap.PrivateEntryIterator<K> {
        KeyIterator(TreeMap.Entry<K,V> first) {
            super(first);
        }
        public K next() {
            return nextEntry().key;
        }
    }

    final class DescendingKeyIterator extends TreeMap.PrivateEntryIterator<K> {
        DescendingKeyIterator(TreeMap.Entry<K,V> first) {
            super(first);
        }
        public K next() {
            return prevEntry().key;
        }
        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            deleteEntry(lastReturned);
            lastReturned = null;
            expectedModCount = modCount;
        }
    }

    // Little utilities

    /**
     * 使用正确的比较器比较两个对象
     */
    @SuppressWarnings("unchecked")
    final int compare(Object k1, Object k2) {
        return comparator==null ? ((Comparable<? super K>)k1).compareTo((K)k2)
                : comparator.compare((K)k1, (K)k2);
    }

    /**
     * 判断两个 value 是否相等，包括value 为 null 的情况。
     */
    static final boolean valEquals(Object o1, Object o2) {
        return (o1==null ? o2==null : o1.equals(o2));
    }

    /**
     * Return SimpleImmutableEntry for entry, or null if null
     */
    static <K,V> Map.Entry<K,V> exportEntry(TreeMap.Entry<K,V> e) {
        return (e == null) ? null :
                new java.util.AbstractMap.SimpleImmutableEntry<>(e);
    }

    /**
     * 返回 entry 的 key，如果为 null 则返回 null
     */
    static <K,V> K keyOrNull(TreeMap.Entry<K,V> e) {
        return (e == null) ? null : e.key;
    }

    /**
     * 返回指定 entry 的 key，如果 entry 为 null 抛出异常
     * @throws NoSuchElementException if the Entry is null
     */
    static <K> K key(TreeMap.Entry<K,?> e) {
        if (e==null)
            throw new NoSuchElementException();
        return e.key;
    }


    // SubMaps
    // 映射子集合

    /**
     * Dummy value serving as unmatchable fence key for unbounded
     * SubMapIterators
     */
    private static final Object UNBOUNDED = new Object();

    /**
     * @serial include
     */
    abstract static class NavigableSubMap<K,V> extends java.util.AbstractMap<K,V>
            implements NavigableMap<K,V>, java.io.Serializable {
        private static final long serialVersionUID = -2102997345730753016L;
        /**
         * The backing map.
         */
        final TreeMap<K,V> m;

        /**
         * Endpoints are represented as triples (fromStart, lo,
         * loInclusive) and (toEnd, hi, hiInclusive). If fromStart is
         * true, then the low (absolute) bound is the start of the
         * backing map, and the other values are ignored. Otherwise,
         * if loInclusive is true, lo is the inclusive bound, else lo
         * is the exclusive bound. Similarly for the upper bound.
         */
        final K lo, hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;

        NavigableSubMap(TreeMap<K,V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0)
                    throw new IllegalArgumentException("fromKey > toKey");
            } else {
                if (!fromStart) // type check
                    m.compare(lo, lo);
                if (!toEnd)
                    m.compare(hi, hi);
            }

            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
        }

        // internal utilities

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                if (c < 0 || (c == 0 && !loInclusive))
                    return true;
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                if (c > 0 || (c == 0 && !hiInclusive))
                    return true;
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0)
                    && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */

        final TreeMap.Entry<K,V> absLowest() {
            TreeMap.Entry<K,V> e =
                    (fromStart ?  m.getFirstEntry() :
                            (loInclusive ? m.getCeilingEntry(lo) :
                                    m.getHigherEntry(lo)));
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K,V> absHighest() {
            TreeMap.Entry<K,V> e =
                    (toEnd ?  m.getLastEntry() :
                            (hiInclusive ?  m.getFloorEntry(hi) :
                                    m.getLowerEntry(hi)));
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K,V> absCeiling(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K,V> e = m.getCeilingEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K,V> absHigher(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K,V> e = m.getHigherEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K,V> absFloor(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K,V> e = m.getFloorEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K,V> absLower(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K,V> e = m.getLowerEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /** Returns the absolute high fence for ascending traversal */
        final TreeMap.Entry<K,V> absHighFence() {
            return (toEnd ? null : (hiInclusive ?
                    m.getHigherEntry(hi) :
                    m.getCeilingEntry(hi)));
        }

        /** Return the absolute low fence for descending traversal  */
        final TreeMap.Entry<K,V> absLowFence() {
            return (fromStart ? null : (loInclusive ?
                    m.getLowerEntry(lo) :
                    m.getFloorEntry(lo)));
        }

        // Abstract methods defined in ascending vs descending classes
        // These relay to the appropriate absolute versions

        abstract TreeMap.Entry<K,V> subLowest();
        abstract TreeMap.Entry<K,V> subHighest();
        abstract TreeMap.Entry<K,V> subCeiling(K key);
        abstract TreeMap.Entry<K,V> subHigher(K key);
        abstract TreeMap.Entry<K,V> subFloor(K key);
        abstract TreeMap.Entry<K,V> subLower(K key);

        /** Returns ascending iterator from the perspective of this submap */
        abstract Iterator<K> keyIterator();

        abstract Spliterator<K> keySpliterator();

        /** Returns descending iterator from the perspective of this submap */
        abstract Iterator<K> descendingKeyIterator();

        // public methods

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : entrySet().isEmpty();
        }

        public int size() {
            return (fromStart && toEnd) ? m.size() : entrySet().size();
        }

        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }

        public final V put(K key, V value) {
            if (!inRange(key))
                throw new IllegalArgumentException("key out of range");
            return m.put(key, value);
        }

        public final V get(Object key) {
            return !inRange(key) ? null :  m.get(key);
        }

        public final V remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public final Map.Entry<K,V> ceilingEntry(K key) {
            return exportEntry(subCeiling(key));
        }

        public final K ceilingKey(K key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<K,V> higherEntry(K key) {
            return exportEntry(subHigher(key));
        }

        public final K higherKey(K key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<K,V> floorEntry(K key) {
            return exportEntry(subFloor(key));
        }

        public final K floorKey(K key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<K,V> lowerEntry(K key) {
            return exportEntry(subLower(key));
        }

        public final K lowerKey(K key) {
            return keyOrNull(subLower(key));
        }

        public final K firstKey() {
            return key(subLowest());
        }

        public final K lastKey() {
            return key(subHighest());
        }

        public final Map.Entry<K,V> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<K,V> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<K,V> pollFirstEntry() {
            TreeMap.Entry<K,V> e = subLowest();
            Map.Entry<K,V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        public final Map.Entry<K,V> pollLastEntry() {
            TreeMap.Entry<K,V> e = subHighest();
            Map.Entry<K,V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        // Views
        transient NavigableMap<K,V> descendingMapView;
        transient TreeMap.NavigableSubMap.EntrySetView entrySetView;
        transient TreeMap.KeySet<K> navigableKeySetView;

        public final java.util.NavigableSet<K> navigableKeySet() {
            TreeMap.KeySet<K> nksv = navigableKeySetView;
            return (nksv != null) ? nksv :
                    (navigableKeySetView = new TreeMap.KeySet<>(this));
        }

        public final java.util.Set<K> keySet() {
            return navigableKeySet();
        }

        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public final SortedMap<K,V> subMap(K fromKey, K toKey) {
            return subMap(fromKey, true, toKey, false);
        }

        public final SortedMap<K,V> headMap(K toKey) {
            return headMap(toKey, false);
        }

        public final SortedMap<K,V> tailMap(K fromKey) {
            return tailMap(fromKey, true);
        }

        // View classes

        abstract class EntrySetView extends AbstractSet<Entry<K,V>> {
            private transient int size = -1, sizeModCount;

            public int size() {
                if (fromStart && toEnd)
                    return m.size();
                if (size == -1 || sizeModCount != m.modCount) {
                    sizeModCount = m.modCount;
                    size = 0;
                    Iterator<?> i = iterator();
                    while (i.hasNext()) {
                        size++;
                        i.next();
                    }
                }
                return size;
            }

            public boolean isEmpty() {
                TreeMap.Entry<K,V> n = absLowest();
                return n == null || tooHigh(n.key);
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<?,?> node = m.getEntry(key);
                return node != null &&
                        valEquals(node.getValue(), entry.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<K,V> node = m.getEntry(key);
                if (node!=null && valEquals(node.getValue(),
                        entry.getValue())) {
                    m.deleteEntry(node);
                    return true;
                }
                return false;
            }
        }

        /**
         * Iterators for SubMaps
         */
        abstract class SubMapIterator<T> implements Iterator<T> {
            TreeMap.Entry<K,V> lastReturned;
            TreeMap.Entry<K,V> next;
            final Object fenceKey;
            int expectedModCount;

            SubMapIterator(TreeMap.Entry<K,V> first,
                           TreeMap.Entry<K,V> fence) {
                expectedModCount = m.modCount;
                lastReturned = null;
                next = first;
                fenceKey = fence == null ? UNBOUNDED : fence.key;
            }

            public final boolean hasNext() {
                return next != null && next.key != fenceKey;
            }

            final TreeMap.Entry<K,V> nextEntry() {
                TreeMap.Entry<K,V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = successor(e);
                lastReturned = e;
                return e;
            }

            final TreeMap.Entry<K,V> prevEntry() {
                TreeMap.Entry<K,V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = predecessor(e);
                lastReturned = e;
                return e;
            }

            final void removeAscending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                // deleted entries are replaced by their successors
                if (lastReturned.left != null && lastReturned.right != null)
                    next = lastReturned;
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

            final void removeDescending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

        }

        final class SubMapEntryIterator extends TreeMap.NavigableSubMap.SubMapIterator<Entry<K,V>> {
            SubMapEntryIterator(TreeMap.Entry<K,V> first,
                                TreeMap.Entry<K,V> fence) {
                super(first, fence);
            }
            public Map.Entry<K,V> next() {
                return nextEntry();
            }
            public void remove() {
                removeAscending();
            }
        }

        final class DescendingSubMapEntryIterator extends TreeMap.NavigableSubMap.SubMapIterator<Entry<K,V>> {
            DescendingSubMapEntryIterator(TreeMap.Entry<K,V> last,
                                          TreeMap.Entry<K,V> fence) {
                super(last, fence);
            }

            public Map.Entry<K,V> next() {
                return prevEntry();
            }
            public void remove() {
                removeDescending();
            }
        }

        // Implement minimal Spliterator as KeySpliterator backup
        final class SubMapKeyIterator extends TreeMap.NavigableSubMap.SubMapIterator<K>
                implements Spliterator<K> {
            SubMapKeyIterator(TreeMap.Entry<K,V> first,
                              TreeMap.Entry<K,V> fence) {
                super(first, fence);
            }
            public K next() {
                return nextEntry().key;
            }
            public void remove() {
                removeAscending();
            }
            public Spliterator<K> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED |
                        Spliterator.SORTED;
            }
            public final Comparator<? super K>  getComparator() {
                return TreeMap.NavigableSubMap.this.comparator();
            }
        }

        final class DescendingSubMapKeyIterator extends TreeMap.NavigableSubMap.SubMapIterator<K>
                implements Spliterator<K> {
            DescendingSubMapKeyIterator(TreeMap.Entry<K,V> last,
                                        TreeMap.Entry<K,V> fence) {
                super(last, fence);
            }
            public K next() {
                return prevEntry().key;
            }
            public void remove() {
                removeDescending();
            }
            public Spliterator<K> trySplit() {
                return null;
            }
            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }
            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }
            public long estimateSize() {
                return Long.MAX_VALUE;
            }
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED;
            }
        }
    }

    /**
     * @serial include
     */
    static final class AscendingSubMap<K,V> extends TreeMap.NavigableSubMap<K,V> {
        private static final long serialVersionUID = 912986545866124060L;

        AscendingSubMap(TreeMap<K,V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        public Comparator<? super K> comparator() {
            return m.comparator();
        }

        public NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                                        K toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new TreeMap.AscendingSubMap<>(m,
                    false, fromKey, fromInclusive,
                    false, toKey,   toInclusive);
        }

        public NavigableMap<K,V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new TreeMap.AscendingSubMap<>(m,
                    fromStart, lo,    loInclusive,
                    false,     toKey, inclusive);
        }

        public NavigableMap<K,V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new TreeMap.AscendingSubMap<>(m,
                    false, fromKey, inclusive,
                    toEnd, hi,      hiInclusive);
        }

        public NavigableMap<K,V> descendingMap() {
            NavigableMap<K,V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new TreeMap.DescendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Spliterator<K> keySpliterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        final class AscendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K,V>> iterator() {
                return new SubMapEntryIterator(absLowest(), absHighFence());
            }
        }

        public java.util.Set<Entry<K,V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new TreeMap.AscendingSubMap.AscendingEntrySetView());
        }

        TreeMap.Entry<K,V> subLowest()       { return absLowest(); }
        TreeMap.Entry<K,V> subHighest()      { return absHighest(); }
        TreeMap.Entry<K,V> subCeiling(K key) { return absCeiling(key); }
        TreeMap.Entry<K,V> subHigher(K key)  { return absHigher(key); }
        TreeMap.Entry<K,V> subFloor(K key)   { return absFloor(key); }
        TreeMap.Entry<K,V> subLower(K key)   { return absLower(key); }
    }

    /**
     * @serial include
     */
    static final class DescendingSubMap<K,V>  extends TreeMap.NavigableSubMap<K,V> {
        private static final long serialVersionUID = 912986545866120460L;
        DescendingSubMap(TreeMap<K,V> m,
                         boolean fromStart, K lo, boolean loInclusive,
                         boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        private final Comparator<? super K> reverseComparator =
                Collections.reverseOrder(m.comparator);

        public Comparator<? super K> comparator() {
            return reverseComparator;
        }

        public NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive,
                                        K toKey,   boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new TreeMap.DescendingSubMap<>(m,
                    false, toKey,   toInclusive,
                    false, fromKey, fromInclusive);
        }

        public NavigableMap<K,V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new TreeMap.DescendingSubMap<>(m,
                    false, toKey, inclusive,
                    toEnd, hi,    hiInclusive);
        }

        public NavigableMap<K,V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new TreeMap.DescendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive);
        }

        public NavigableMap<K,V> descendingMap() {
            NavigableMap<K,V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new TreeMap.AscendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd,     hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Spliterator<K> keySpliterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        final class DescendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K,V>> iterator() {
                return new DescendingSubMapEntryIterator(absHighest(), absLowFence());
            }
        }

        public java.util.Set<Entry<K,V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new TreeMap.DescendingSubMap.DescendingEntrySetView());
        }

        TreeMap.Entry<K,V> subLowest()       { return absHighest(); }
        TreeMap.Entry<K,V> subHighest()      { return absLowest(); }
        TreeMap.Entry<K,V> subCeiling(K key) { return absFloor(key); }
        TreeMap.Entry<K,V> subHigher(K key)  { return absLower(key); }
        TreeMap.Entry<K,V> subFloor(K key)   { return absCeiling(key); }
        TreeMap.Entry<K,V> subLower(K key)   { return absHigher(key); }
    }

    /**
     * This class exists solely for the sake of serialization
     * compatibility with previous releases of TreeMap that did not
     * support NavigableMap.  It translates an old-version SubMap into
     * a new-version AscendingSubMap. This class is never otherwise
     * used.
     *
     * @serial include
     */
    private class SubMap extends AbstractMap<K,V>
            implements SortedMap<K,V>, java.io.Serializable {
        private static final long serialVersionUID = -6520786458950516097L;
        private boolean fromStart = false, toEnd = false;
        private K fromKey, toKey;
        private Object readResolve() {
            return new TreeMap.AscendingSubMap<>(TreeMap.this,
                    fromStart, fromKey, true,
                    toEnd, toKey, false);
        }
        public Set<Entry<K,V>> entrySet() { throw new InternalError(); }
        public K lastKey() { throw new InternalError(); }
        public K firstKey() { throw new InternalError(); }
        public SortedMap<K,V> subMap(K fromKey, K toKey) { throw new InternalError(); }
        public SortedMap<K,V> headMap(K toKey) { throw new InternalError(); }
        public SortedMap<K,V> tailMap(K fromKey) { throw new InternalError(); }
        public Comparator<? super K> comparator() { throw new InternalError(); }
    }


    // Red-black mechanics
    // 红黑机制

    private static final boolean RED   = false;
    private static final boolean BLACK = true;

    /**
     * 树中节点类
     */

    static final class Entry<K,V> implements Map.Entry<K,V> {
        K key;
        V value;
        TreeMap.Entry<K,V> left;
        TreeMap.Entry<K,V> right;
        TreeMap.Entry<K,V> parent;
        boolean color = BLACK;

        /**
         * 构造函数
         */
        Entry(K key, V value, TreeMap.Entry<K,V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }

        /**
         * 返回 key
         *
         * @return the key
         */
        public K getKey() {
            return key;
        }

        /**
         * 返回 key 对应的 value
         *
         * @return the value associated with the key
         */
        public V getValue() {
            return value;
        }

        /**
         * 将此 entry 的 value 替换成新的 value
         *
         * @return the value associated with the key before this method was
         *         called
         */
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        // 判断是否相等
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;

            return valEquals(key,e.getKey()) && valEquals(value,e.getValue());
        }

        public int hashCode() {
            int keyHash = (key==null ? 0 : key.hashCode());
            int valueHash = (value==null ? 0 : value.hashCode());
            return keyHash ^ valueHash;
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    /**
     * 返回 TreeMap 中的第一个 Entry（根据 TreeMap 的排序方法）。如果
     * TreeMap 为空返回 null。
     */
    final TreeMap.Entry<K,V> getFirstEntry() {
        TreeMap.Entry<K,V> p = root;
        if (p != null)
            while (p.left != null)
                p = p.left;
        return p;
    }

    /**
     * 返回 TreeMap 中的最后一个 Entry（根据 TreeMap 的排序方法）。如果
     * TreeMap 为空返回 null。
     */
    final TreeMap.Entry<K,V> getLastEntry() {
        TreeMap.Entry<K,V> p = root;
        if (p != null)
            while (p.right != null)
                p = p.right;
        return p;
    }

    /**
     * 返回指定 Entry 的继任者（下一个节点），如果没有返回 null。
     */
    static <K,V> TreeMap.Entry<K,V> successor(TreeMap.Entry<K,V> t) {
        if (t == null)
            return null;
        else if (t.right != null) {
            TreeMap.Entry<K,V> p = t.right;
            while (p.left != null)
                p = p.left;
            return p;
        } else {
            TreeMap.Entry<K,V> p = t.parent;
            TreeMap.Entry<K,V> ch = t;
            // 不断向上查找，直到 p 节点为其父节点的左子节点为止。此时 p 的
            // 父节点为要查找的节点。
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * 返回指定 Entry 的前一个节点），如果没有返回 null。
     */
    static <K,V> TreeMap.Entry<K,V> predecessor(TreeMap.Entry<K,V> t) {
        if (t == null)
            return null;
        else if (t.left != null) {
            TreeMap.Entry<K,V> p = t.left;
            while (p.right != null)
                p = p.right;
            return p;
        } else {
            TreeMap.Entry<K,V> p = t.parent;
            TreeMap.Entry<K,V> ch = t;
            // 不断向上查找，直到 p 节点为其父节点的右子节点为止。此时 p 的
            // 父节点为要查找的节点。
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * 平衡操作
     */

    // 返回当前节点的颜色
    private static <K,V> boolean colorOf(TreeMap.Entry<K,V> p) {
        return (p == null ? BLACK : p.color);
    }

    // 返回当前节点的父节点
    private static <K,V> TreeMap.Entry<K,V> parentOf(TreeMap.Entry<K,V> p) {
        return (p == null ? null: p.parent);
    }

    // 设置当前节点的颜色
    private static <K,V> void setColor(TreeMap.Entry<K,V> p, boolean c) {
        if (p != null)
            p.color = c;
    }

    // 返回当前节点左子节点
    private static <K,V> TreeMap.Entry<K,V> leftOf(TreeMap.Entry<K,V> p) {
        return (p == null) ? null: p.left;
    }

    // 返回当前节点右子节点
    private static <K,V> TreeMap.Entry<K,V> rightOf(TreeMap.Entry<K,V> p) {
        return (p == null) ? null: p.right;
    }

    /** From CLR */
    // 对指定节点 p 左旋
    private void rotateLeft(TreeMap.Entry<K,V> p) {
        if (p != null) {
            TreeMap.Entry<K,V> r = p.right;
            p.right = r.left;
            if (r.left != null)
                r.left.parent = p;
            r.parent = p.parent;
            if (p.parent == null)
                root = r;
            else if (p.parent.left == p)
                p.parent.left = r;
            else
                p.parent.right = r;
            r.left = p;
            p.parent = r;
        }
    }

    /** From CLR */
    // 对节点 p 右旋
    private void rotateRight(TreeMap.Entry<K,V> p) {
        if (p != null) {
            TreeMap.Entry<K,V> l = p.left;
            p.left = l.right;
            if (l.right != null) l.right.parent = p;
            l.parent = p.parent;
            if (p.parent == null)
                root = l;
            else if (p.parent.right == p)
                p.parent.right = l;
            else p.parent.left = l;
            l.right = p;
            p.parent = l;
        }
    }

    /** From CLR */
    private void fixAfterInsertion(TreeMap.Entry<K,V> x) {
        x.color = RED;

        while (x != null && x != root && x.parent.color == RED) {
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                TreeMap.Entry<K,V> y = rightOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == rightOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateLeft(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateRight(parentOf(parentOf(x)));
                }
            } else {
                TreeMap.Entry<K,V> y = leftOf(parentOf(parentOf(x)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(x), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    x = parentOf(parentOf(x));
                } else {
                    if (x == leftOf(parentOf(x))) {
                        x = parentOf(x);
                        rotateRight(x);
                    }
                    setColor(parentOf(x), BLACK);
                    setColor(parentOf(parentOf(x)), RED);
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        root.color = BLACK;
    }

    /**
     * Delete node p, and then rebalance the tree.
     */
    private void deleteEntry(TreeMap.Entry<K,V> p) {
        modCount++;
        size--;

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if (p.left != null && p.right != null) {
            TreeMap.Entry<K,V> s = successor(p);
            p.key = s.key;
            p.value = s.value;
            p = s;
        } // p has 2 children

        // Start fixup at replacement node, if it exists.
        TreeMap.Entry<K,V> replacement = (p.left != null ? p.left : p.right);

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent;
            if (p.parent == null)
                root = replacement;
            else if (p == p.parent.left)
                p.parent.left  = replacement;
            else
                p.parent.right = replacement;

            // Null out links so they are OK to use by fixAfterDeletion.
            p.left = p.right = p.parent = null;

            // Fix replacement
            if (p.color == BLACK)
                fixAfterDeletion(replacement);
        } else if (p.parent == null) { // return if we are the only node.
            root = null;
        } else { //  No children. Use self as phantom replacement and unlink.
            if (p.color == BLACK)
                fixAfterDeletion(p);

            if (p.parent != null) {
                if (p == p.parent.left)
                    p.parent.left = null;
                else if (p == p.parent.right)
                    p.parent.right = null;
                p.parent = null;
            }
        }
    }

    /** From CLR */
    private void fixAfterDeletion(TreeMap.Entry<K,V> x) {
        while (x != root && colorOf(x) == BLACK) {
            if (x == leftOf(parentOf(x))) {
                TreeMap.Entry<K,V> sib = rightOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateLeft(parentOf(x));
                    sib = rightOf(parentOf(x));
                }

                if (colorOf(leftOf(sib))  == BLACK &&
                        colorOf(rightOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) {
                        setColor(leftOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateRight(sib);
                        sib = rightOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(rightOf(sib), BLACK);
                    rotateLeft(parentOf(x));
                    x = root;
                }
            } else { // symmetric
                TreeMap.Entry<K,V> sib = leftOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (colorOf(rightOf(sib)) == BLACK &&
                        colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }

        setColor(x, BLACK);
    }

    private static final long serialVersionUID = 919286545866124006L;

    /**
     * Save the state of the {@code TreeMap} instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <em>size</em> of the TreeMap (the number of key-value
     *             mappings) is emitted (int), followed by the key (Object)
     *             and value (Object) for each key-value mapping represented
     *             by the TreeMap. The key-value mappings are emitted in
     *             key-order (as determined by the TreeMap's Comparator,
     *             or by the keys' natural ordering if the TreeMap has no
     *             Comparator).
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // Write out the Comparator and any hidden stuff
        s.defaultWriteObject();

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (Iterator<Map.Entry<K,V>> i = entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<K,V> e = i.next();
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    /**
     * Reconstitute the {@code TreeMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(final java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in the Comparator and any hidden stuff
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        buildFromSorted(size, null, s, null);
    }

    /** Intended to be called only from TreeSet.readObject */
    void readTreeSet(int size, java.io.ObjectInputStream s, V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        buildFromSorted(size, null, s, defaultVal);
    }

    /** Intended to be called only from TreeSet.addAll */
    void addAllForTreeSet(SortedSet<? extends K> set, V defaultVal) {
        try {
            buildFromSorted(set.size(), set.iterator(), null, defaultVal);
        } catch (java.io.IOException cannotHappen) {
        } catch (ClassNotFoundException cannotHappen) {
        }
    }


    /**
     * Linear time tree building algorithm from sorted data.  Can accept keys
     * and/or values from iterator or stream. This leads to too many
     * parameters, but seems better than alternatives.  The four formats
     * that this method accepts are:
     *
     *    1) An iterator of Map.Entries.  (it != null, defaultVal == null).
     *    2) An iterator of keys.         (it != null, defaultVal != null).
     *    3) A stream of alternating serialized keys and values.
     *                                   (it == null, defaultVal == null).
     *    4) A stream of serialized keys. (it == null, defaultVal != null).
     *
     * It is assumed that the comparator of the TreeMap is already set prior
     * to calling this method.
     *
     * @param size the number of keys (or key-value pairs) to be read from
     *        the iterator or stream
     * @param it If non-null, new entries are created from entries
     *        or keys read from this iterator.
     * @param str If non-null, new entries are created from keys and
     *        possibly values read from this stream in serialized form.
     *        Exactly one of it and str should be non-null.
     * @param defaultVal if non-null, this default value is used for
     *        each value in the map.  If null, each value is read from
     *        iterator or stream, as described above.
     * @throws java.io.IOException propagated from stream reads. This cannot
     *         occur if str is null.
     * @throws ClassNotFoundException propagated from readObject.
     *         This cannot occur if str is null.
     */
    private void buildFromSorted(int size, Iterator<?> it,
                                 java.io.ObjectInputStream str,
                                 V defaultVal)
            throws  java.io.IOException, ClassNotFoundException {
        this.size = size;
        root = buildFromSorted(0, 0, size-1, computeRedLevel(size),
                it, str, defaultVal);
    }

    /**
     * Recursive "helper method" that does the real work of the
     * previous method.  Identically named parameters have
     * identical definitions.  Additional parameters are documented below.
     * It is assumed that the comparator and size fields of the TreeMap are
     * already set prior to calling this method.  (It ignores both fields.)
     *
     * @param level the current level of tree. Initial call should be 0.
     * @param lo the first element index of this subtree. Initial should be 0.
     * @param hi the last element index of this subtree.  Initial should be
     *        size-1.
     * @param redLevel the level at which nodes should be red.
     *        Must be equal to computeRedLevel for tree of this size.
     */
    @SuppressWarnings("unchecked")
    private final TreeMap.Entry<K,V> buildFromSorted(int level, int lo, int hi,
                                                               int redLevel,
                                                               Iterator<?> it,
                                                               java.io.ObjectInputStream str,
                                                               V defaultVal)
            throws  java.io.IOException, ClassNotFoundException {
        /*
         * Strategy: The root is the middlemost element. To get to it, we
         * have to first recursively construct the entire left subtree,
         * so as to grab all of its elements. We can then proceed with right
         * subtree.
         *
         * The lo and hi arguments are the minimum and maximum
         * indices to pull out of the iterator or stream for current subtree.
         * They are not actually indexed, we just proceed sequentially,
         * ensuring that items are extracted in corresponding order.
         */

        if (hi < lo) return null;

        int mid = (lo + hi) >>> 1;

        TreeMap.Entry<K,V> left  = null;
        if (lo < mid)
            left = buildFromSorted(level+1, lo, mid - 1, redLevel,
                    it, str, defaultVal);

        // extract key and/or value from iterator or stream
        K key;
        V value;
        if (it != null) {
            if (defaultVal==null) {
                Map.Entry<?,?> entry = (Map.Entry<?,?>)it.next();
                key = (K)entry.getKey();
                value = (V)entry.getValue();
            } else {
                key = (K)it.next();
                value = defaultVal;
            }
        } else { // use stream
            key = (K) str.readObject();
            value = (defaultVal != null ? defaultVal : (V) str.readObject());
        }

        TreeMap.Entry<K,V> middle =  new TreeMap.Entry<>(key, value, null);

        // color nodes in non-full bottommost level red
        if (level == redLevel)
            middle.color = RED;

        if (left != null) {
            middle.left = left;
            left.parent = middle;
        }

        if (mid < hi) {
            TreeMap.Entry<K,V> right = buildFromSorted(level+1, mid+1, hi, redLevel,
                    it, str, defaultVal);
            middle.right = right;
            right.parent = middle;
        }

        return middle;
    }

    /**
     * Find the level down to which to assign all nodes BLACK.  This is the
     * last `full' level of the complete binary tree produced by
     * buildTree. The remaining nodes are colored RED. (This makes a `nice'
     * set of color assignments wrt future insertions.) This level number is
     * computed by finding the number of splits needed to reach the zeroeth
     * node.  (The answer is ~lg(N), but in any case must be computed by same
     * quick O(lg(N)) loop.)
     */
    private static int computeRedLevel(int sz) {
        int level = 0;
        for (int m = sz - 1; m >= 0; m = m / 2 - 1)
            level++;
        return level;
    }

    /**
     * Currently, we support Spliterator-based versions only for the
     * full map, in either plain of descending form, otherwise relying
     * on defaults because size estimation for submaps would dominate
     * costs. The type tests needed to check these for key views are
     * not very nice but avoid disrupting existing class
     * structures. Callers must use plain default spliterators if this
     * returns null.
     */
    static <K> Spliterator<K> keySpliteratorFor(NavigableMap<K,?> m) {
        if (m instanceof TreeMap) {
            @SuppressWarnings("unchecked") TreeMap<K,Object> t =
                    (TreeMap<K,Object>) m;
            return t.keySpliterator();
        }
        if (m instanceof TreeMap.DescendingSubMap) {
            @SuppressWarnings("unchecked") TreeMap.DescendingSubMap<K,?> dm =
                    (TreeMap.DescendingSubMap<K,?>) m;
            TreeMap<K,?> tm = dm.m;
            if (dm == tm.descendingMap) {
                @SuppressWarnings("unchecked") TreeMap<K,Object> t =
                        (TreeMap<K,Object>) tm;
                return t.descendingKeySpliterator();
            }
        }
        @SuppressWarnings("unchecked") TreeMap.NavigableSubMap<K,?> sm =
                (TreeMap.NavigableSubMap<K,?>) m;
        return sm.keySpliterator();
    }

    final Spliterator<K> keySpliterator() {
        return new TreeMap.KeySpliterator<K,V>(this, null, null, 0, -1, 0);
    }

    final Spliterator<K> descendingKeySpliterator() {
        return new TreeMap.DescendingKeySpliterator<K,V>(this, null, null, 0, -2, 0);
    }

    /**
     * Base class for spliterators.  Iteration starts at a given
     * origin and continues up to but not including a given fence (or
     * null for end).  At top-level, for ascending cases, the first
     * split uses the root as left-fence/right-origin. From there,
     * right-hand splits replace the current fence with its left
     * child, also serving as origin for the split-off spliterator.
     * Left-hands are symmetric. Descending versions place the origin
     * at the end and invert ascending split rules.  This base class
     * is non-commital about directionality, or whether the top-level
     * spliterator covers the whole tree. This means that the actual
     * split mechanics are located in subclasses. Some of the subclass
     * trySplit methods are identical (except for return types), but
     * not nicely factorable.
     *
     * Currently, subclass versions exist only for the full map
     * (including descending keys via its descendingMap).  Others are
     * possible but currently not worthwhile because submaps require
     * O(n) computations to determine size, which substantially limits
     * potential speed-ups of using custom Spliterators versus default
     * mechanics.
     *
     * To boostrap initialization, external constructors use
     * negative size estimates: -1 for ascend, -2 for descend.
     */
    static class TreeMapSpliterator<K,V> {
        final TreeMap<K,V> tree;
        TreeMap.Entry<K,V> current; // traverser; initially first node in range
        TreeMap.Entry<K,V> fence;   // one past last, or null
        int side;                   // 0: top, -1: is a left split, +1: right
        int est;                    // size estimate (exact only for top-level)
        int expectedModCount;       // for CME checks

        TreeMapSpliterator(TreeMap<K,V> tree,
                           TreeMap.Entry<K,V> origin, TreeMap.Entry<K,V> fence,
                           int side, int est, int expectedModCount) {
            this.tree = tree;
            this.current = origin;
            this.fence = fence;
            this.side = side;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEstimate() { // force initialization
            int s; TreeMap<K,V> t;
            if ((s = est) < 0) {
                if ((t = tree) != null) {
                    current = (s == -1) ? t.getFirstEntry() : t.getLastEntry();
                    s = est = t.size;
                    expectedModCount = t.modCount;
                }
                else
                    s = est = 0;
            }
            return s;
        }

        public final long estimateSize() {
            return (long)getEstimate();
        }
    }

    static final class KeySpliterator<K,V>
            extends TreeMap.TreeMapSpliterator<K,V>
            implements Spliterator<K> {
        KeySpliterator(TreeMap<K,V> tree,
                       TreeMap.Entry<K,V> origin, TreeMap.Entry<K,V> fence,
                       int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public TreeMap.KeySpliterator<K,V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K,V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (d <  0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new TreeMap.KeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K,V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K,V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        public final Comparator<? super K>  getComparator() {
            return tree.comparator;
        }

    }

    static final class DescendingKeySpliterator<K,V>
            extends TreeMap.TreeMapSpliterator<K,V>
            implements Spliterator<K> {
        DescendingKeySpliterator(TreeMap<K,V> tree,
                                 TreeMap.Entry<K,V> origin, TreeMap.Entry<K,V> fence,
                                 int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public TreeMap.DescendingKeySpliterator<K,V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K,V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d <  0)              ? e.left :    // was left
                                            (d >  0 && f != null) ? f.right :   // was right
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) > 0) {       // e not already past s
                side = 1;
                return new TreeMap.DescendingKeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K,V> f = fence, e, p, pr;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.left) != null) {
                        while ((pr = p.right) != null)
                            p = pr;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.left)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K,V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = predecessor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.ORDERED;
        }
    }

    static final class ValueSpliterator<K,V>
            extends TreeMap.TreeMapSpliterator<K,V>
            implements Spliterator<V> {
        ValueSpliterator(TreeMap<K,V> tree,
                         TreeMap.Entry<K,V> origin, TreeMap.Entry<K,V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public TreeMap.ValueSpliterator<K,V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K,V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (d <  0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new TreeMap.ValueSpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K,V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.value);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            TreeMap.Entry<K,V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.value);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) | Spliterator.ORDERED;
        }
    }

    static final class EntrySpliterator<K,V>
            extends TreeMap.TreeMapSpliterator<K,V>
            implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(TreeMap<K,V> tree,
                         TreeMap.Entry<K,V> origin, TreeMap.Entry<K,V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public TreeMap.EntrySpliterator<K,V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K,V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0)              ? tree.root : // was top
                                    (d >  0)              ? e.right :   // was right
                                            (d <  0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new TreeMap.EntrySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K,V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    }
                    else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            TreeMap.Entry<K,V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        @Override
        public Comparator<Map.Entry<K, V>> getComparator() {
            // Adapt or create a key-based comparator
            if (tree.comparator != null) {
                return Map.Entry.comparingByKey(tree.comparator);
            }
            else {
                return (Comparator<Map.Entry<K, V>> & Serializable) (e1, e2) -> {
                    @SuppressWarnings("unchecked")
                    Comparable<? super K> k1 = (Comparable<? super K>) e1.getKey();
                    return k1.compareTo(e2.getKey());
                };
            }
        }
    }
}

