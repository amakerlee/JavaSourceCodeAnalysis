
/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.io.Serializable;

/**
 * Map 是一个有键值对映射的对象。一个 map 不能包含重复的 key；每一个
 * key 最多能映射一个 value。
 *
 * 这个接口替代了 Dictionary 类，Dictionary 类是抽象类而不是接口
 * （替代原因：接口总是优于抽象类）
 *
 * Map 接口提供了三个集合试图，分别是 keys 的 set 集合，values 的集合，
 * key-value 映射的 set 集合（注意 values 集合不是 set 类型，因为 value
 * 可能相同）。map 的顺序定义成 map 的集合视图的迭代器返回的顺序。一些
 * map 的实现，比如 TreeMap 类，对于顺序有特殊的规定；其他的 map 实现类，
 * 比如 HashMap 类，就没有特殊规定。
 *
 * 注意：如果把可变的对象作为 map 的 key，需要特别注意。当该对象作为 map
 * 的 key，且其值改变了，会影响 equals 的比较，从而导致 map 的行为未知。
 * 此项禁止的一个特殊例子是，不允许将 map 本身作为 map 的 key。尽管允许
 * 将 map 本身作为 map 的 value，但是要特别注意，equals 和 hashCode 方法
 * 在这样的 map 里面可能不能正常使用。
 *
 * 所有通用的 map 实现类都应该提供两个标准的构造函数：一个无参数且返回
 * 类型为 void 类型的构造函数，用来构造一个空的 map，另一个就是仅含有
 * 一个参数且参数类型为 Map 的构造函数，用来构造一个和指定集合有相同 key
 * 和 value 映射的 map。实际上，后一个构造函数允许用户复制任何的 map，
 * 新生成和给定 map 一样的 map。map 接口没有办法强制执行这一建议（因为
 * 接口不能包含构造函数），但是 JDK中所有通用的 map 的实现都遵守这一规范。
 *
 * 如果一个方法不被 map 支持，且这个操作会改变 map 的结构，而且其已在
 * map 接口中作了具体的定义，它将会抛出 UnsupportedOperationException
 * 异常。在这种情况下，这些方法可能会，但并不一定会抛出
 * UnsupportedOperationException 异常，如果调用不会产生任何影响的话。
 *
 * 一些 map 的实现在 key 和 value 上会有限制。比如，一些实现不允许 key 或者
 * value 为 null，一些在 key 的数据类型上也有限制。尝试插入非法的 key 或
 * value 会抛出未检查的异常，特别是 NullPointerException 或者
 * ClassCastException 异常。试图查询非法的 key 或者 value 会抛出异常，
 * 或者会返回 false，一些实现会禁止前一种行为，一些会禁止后一种。更一般地，
 * 对于非法的 key 或者 value 的操作，将非法的元素插入到 map 中，可能会成功，
 *
 * 在 Collections Framework 中定义了很多基于 equals 的方法。例如
 * containsKey 方法。具体的实现可以通过避免调用 equals 方法，例如通过比较
 * 两个 key 的 hash 值来判断。（如果两个对象的 hash 值不同，那么两个对象
 * 不可能相等。）更一般地，大量 Collections Framework 的接口都可以利用
 * Object 的方法。
 *
 * @August containsKey()方法调用的getEntry方法源码看出，在if的判定条件
 * 中，equals是作为最后一个判定条件出现的，也就是说如果if前面的判定条件
 * 为true，那么是不会调用equals()方法的。
 *
 * 此接口是 Java Collections Framework 的成员。
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Josh Bloch
 * @see HashMap
 * @see TreeMap
 * @see Hashtable
 * @see SortedMap
 * @see java.util.Collection
 * @see java.util.Set
 * @since 1.2
 */
public interface Map<K,V> {
    // Query Operations
    // 查询操作

    /**
     * 返回 key-value 映射的个数。如果超过 Integer.MAX_VALUE，那么返回
     * Integer.MAX_VALUE
     *
     * @return the number of key-value mappings in this map
     */
    int size();

    /**
     * 如果不包含任何 key-value 映射，返回 true
     *
     * @return true if this map contains no key-value mappings
     */
    boolean isEmpty();

    /**
     * 如果包含指定的 key，返回 true
     *
     * @param key key whose presence in this map is to be tested
     * @return true if this map contains a mapping for the specified
     *         key
     * @throws ClassCastException if the key is of an inappropriate type for
     *         this map
     * @throws NullPointerException if the specified key is null and this map
     *         does not permit null keys
     */
    boolean containsKey(Object key);

    /**
     * 如果 map 中至少有一个 key 对应指定的 value，返回 true。在大多数
     * map 的实现中，这一操作都需要 map 大小的线性时间来完成
     *
     * @param value value whose presence in this map is to be tested
     * @return true if this map maps one or more keys to the
     *         specified value
     * @throws ClassCastException if the value is of an inappropriate type for
     *         this map
     * @throws NullPointerException if the specified value is null and this
     *         map does not permit null values
     */
    boolean containsValue(Object value);

    /**
     * 返回指定 key 对应的 value，如果不存在对应的映射，返回 null。
     *
     * 如果 map 允许 null，那么返回 null 并不意味着 map 不存在指定 key 的映射，
     * 也可能是该 key 对应的 value 为 null。containsKey 方法可以用于区分这
     * 两种情况。
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     * @throws ClassCastException if the key is of an inappropriate type for
     *         this map
     * (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified key is null and this map
     *         does not permit null keys
     * (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    V get(Object key);

    // Modification Operations
    // 修改操作

    /**
     * put 是将指定的 key 和指定的 value 联系起来的操作。如果之前包括一个
     * 对指定 key 的映射，那么旧的 value 将会被新的 value 替换。
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was
     *                no mapping for key. (A null return can also indicate that
     *                the map previously associated null with key, if the
     *                implementation supports null values.)
     * @throws UnsupportedOperationException if the <tt>put</tt> operation
     *         is not supported by this map
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     * @throws NullPointerException if the specified key or value is null
     *         and this map does not permit null keys or values
     * @throws IllegalArgumentException if some property of the specified key
     *         or value prevents it from being stored in this map
     */
    V put(K key, V value);

    /**
     * remove 方法用于从 map 中移除指定的 key 对应的映射，如果其存在的话。
     *
     * 返回和指定 key 关联的 value，如果不存在该映射则返回 null。
     *
     * 如果 map 允许 value 为 null，那么返回 null 并不一定表示 map 不包含该映射，
     * 有可能是指定 key 对应的 value 本来就为 null。
     *
     * 一旦此方法被调用，map 就不再包含指定 key 对应的映射了。
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was
     *                no mapping for key.
     * @throws UnsupportedOperationException if the <tt>remove</tt> operation
     *         is not supported by this map
     * @throws ClassCastException if the key is of an inappropriate type for
     *         this map
     * @throws NullPointerException if the specified key is null and this
     *         map does not permit null keys
     */
    V remove(Object key);


    // Bulk Operations
    // 批量操作

    /**
     * 将指定 map 的所有键值对复制到此 map 中。此方法等效于对于指定 map 的
     * 所有键值对调用此 map 的 put 方法。如果此操作进行过程中指定的 map 被
     * 修改，则操作的结果不确定。
     *
     * @param m mappings to be stored in this map
     * @throws UnsupportedOperationException if the <tt>putAll</tt> operation
     *         is not supported by this map
     * @throws ClassCastException if the class of a key or value in the
     *         specified map prevents it from being stored in this map
     * @throws NullPointerException if the specified map is null, or if
     *         this map does not permit null keys or values, and the
     *         specified map contains null keys or values
     * @throws IllegalArgumentException if some property of a key or value in
     *         the specified map prevents it from being stored in this map
     */
    void putAll(java.util.Map<? extends K, ? extends V> m);

    /**
     * 删除 map 中所有的映射。此方法调用后 map 为空。
     *
     * @throws UnsupportedOperationException if the <tt>clear</tt> operation
     *         is not supported by this map
     */
    void clear();


    // Views
    // 视图

    /**
     * 此方法返回包含 map 中所有 key 的 Set。此集合由 map 提供支持，所以
     * 任何对 map 的操作都会体现在集合里，同样对集合的操作也将体现在 map 里。
     * （和 ArrayList 中的 subList 方法一样，任何一方的改变都会在另一方体现
     * 出来。）如果在迭代过程中 map 被进行了结构性修改（迭代器本身的 remove
     * 方法不算），迭代的结果不确定。此集合支持删除元素，同时会从 map 中
     * 移除对应的映射，通过 Iterator.remove, Set.remove, removeAll,
     * retainAll, 和 clear 操作。此集合不支持 add 和 addAll 操作。
     *
     * @return a set view of the keys contained in this map
     */
    java.util.Set<K> keySet();

    /**
     * 返回一个包含 map 中所有 value 的 Collection 对象视图。任何对 map 的
     * 操作都会体现在集合里，同样对集合的操作也将体现在 map 里。如果在
     * 迭代过程中 map 被进行了结构性修改（迭代器本身的 remove方法不算），
     * 迭代的结果不确定。此集合支持删除元素，同时会从 map 中移除对应的
     * 映射，通过 Iterator.remove, Collection.remove, removeAll, retainAll,
     * 和 clear 操作。此集合不支持 add 和 addAll 操作。
     *
     * @return a collection view of the values contained in this map
     */
    Collection<V> values();

    /**
     * 此方法返回包含 map 中所有映射的 Set。此集合由 map 提供支持，所以
     * 任何对 map 的操作都会体现在集合里，同样对集合的操作也将体现在 map 里。
     * 如果在迭代过程中 map 被进行了结构性修改（迭代器本身的 remove 和
     * map.entry 里面的 setValue 方法不算），迭代的结果不确定。此集合支持
     * 删除元素，同时会从 map 中移除对应的映射，通过 Iterator.remove,
     * Set.remove, removeAll, retainAll, 和 clear 操作。此集合不支持 add 和
     * addAll 操作。
     *
     * @return a set view of the mappings contained in this map
     */
    Set<java.util.Map.Entry<K, V>> entrySet();

    /**
     * A map entry (key-value pair). Map.entrySet 方法返回 map 的一个集合
     * 视图。这些 Map.Entry 对象只有在迭代过程中才是有效的；通常在遍历过程
     * 中如果作为后台支持的 map 被修改了，那么 map entry 的行为不确定
     * （不包括 setValue 方法进行的修改）。
     *
     * @see java.util.Map#entrySet()
     * @since 1.2
     */
    interface Entry<K,V> {
        /**
         * 返回此 entry 对应的 key
         *
         * @return the key corresponding to this entry
         * @throws IllegalStateException implementations may, but are not
         *         required to, throw this exception if the entry has been
         *         removed from the backing map.
         */
        K getKey();

        /**
         * 返回此 entry 对应的 value。如果映射已经从后台 map 中删除（通过
         * 迭代器的 remove 方法），那么此操作的结果不确定。
         *
         * @return the value corresponding to this entry
         * @throws IllegalStateException implementations may, but are not
         *         required to, throw this exception if the entry has been
         *         removed from the backing map.
         */
        V getValue();

        /**
         * 用指定的 value 替换 entry 中的 value。（写入到 map 中）。如果映射
         * 已经从后台 map 中删除（通过迭代器的 remove 方法），那么此操作的
         * 结果不确定。
         *
         * @param value new value to be stored in this entry
         * @return old value corresponding to the entry
         * @throws UnsupportedOperationException if the <tt>put</tt> operation
         *         is not supported by the backing map
         * @throws ClassCastException if the class of the specified value
         *         prevents it from being stored in the backing map
         * @throws NullPointerException if the backing map does not permit
         *         null values, and the specified value is null
         * @throws IllegalArgumentException if some property of this value
         *         prevents it from being stored in the backing map
         * @throws IllegalStateException implementations may, but are not
         *         required to, throw this exception if the entry has been
         *         removed from the backing map.
         */
        V setValue(V value);

        /**
         * 比较此 entry 和指定对象是否相等，如果指定对象也是一个 entry 而且
         * 两个 entry 表示同样的映射，那么返回 true。
         *
         * @param o object to be compared for equality with this map entry
         * @return true if the specified object is equal to this map
         *         entry
         */
        boolean equals(Object o);

        /**
         * 返回此 entry 的 hash 值。entry 的 hash 值定义成 key 和 value 求异或
         * 的结果。
         *
         * @return the hash code value for this map entry
         * @see Object#hashCode()
         * @see Object#equals(Object)
         * @see #equals(Object)
         */
        int hashCode();

        /**
         * 返回一个比较 entry 的比较器，按照 key 的自然顺序排序。
         *
         * 返回的比较器支持序列化，如果 key 为 null 会抛出 NullPointerException
         *
         * @param  <K> the {@link Comparable} type of then map keys
         * @param  <V> the type of the map values
         * @return a comparator that compares {@link java.util.Map.Entry} in natural order on key.
         * @see Comparable
         * @since 1.8
         */
        public static <K extends Comparable<? super K>, V> Comparator<java.util.Map.Entry<K,V>> comparingByKey() {
            return (Comparator<java.util.Map.Entry<K, V>> & Serializable)
                    (c1, c2) -> c1.getKey().compareTo(c2.getKey());
        }

        /**
         * 返回一个 entry 的比较器，按照 value 的自然顺序排序
         *
         * 返回的比较器支持序列化，如果 value 为 null 会抛出 NullPointerException
         *
         * @param <K> the type of the map keys
         * @param <V> the {@link Comparable} type of the map values
         * @return a comparator that compares {@link java.util.Map.Entry} in natural order on value.
         * @see Comparable
         * @since 1.8
         */
        public static <K, V extends Comparable<? super V>> Comparator<java.util.Map.Entry<K,V>> comparingByValue() {
            return (Comparator<java.util.Map.Entry<K, V>> & Serializable)
                    (c1, c2) -> c1.getValue().compareTo(c2.getValue());
        }

        /**
         * 返回一个 entry 的比较器，根据传入的比较器对 key 排序
         *
         * 如果传入的比较器支持序列化，那么返回的比较器也支持序列化
         *
         * @param  <K> the type of the map keys
         * @param  <V> the type of the map values
         * @param  cmp the key {@link Comparator}
         * @return a comparator that compares {@link java.util.Map.Entry} by the key.
         * @since 1.8
         */
        public static <K, V> Comparator<java.util.Map.Entry<K, V>> comparingByKey(Comparator<? super K> cmp) {
            Objects.requireNonNull(cmp);
            return (Comparator<java.util.Map.Entry<K, V>> & Serializable)
                    (c1, c2) -> cmp.compare(c1.getKey(), c2.getKey());
        }

        /**
         * 返回一个 entry 的比较器，根据传入的比较器对 value 排序
         *
         * 如果传入的比较器支持序列化，那么返回的比较器也支持序列化
         *
         * @param  <K> the type of the map keys
         * @param  <V> the type of the map values
         * @param  cmp the value {@link Comparator}
         * @return a comparator that compares {@link java.util.Map.Entry} by the value.
         * @since 1.8
         */
        public static <K, V> Comparator<java.util.Map.Entry<K, V>> comparingByValue(Comparator<? super V> cmp) {
            Objects.requireNonNull(cmp);
            return (Comparator<java.util.Map.Entry<K, V>> & Serializable)
                    (c1, c2) -> cmp.compare(c1.getValue(), c2.getValue());
        }
    }

    // Comparison and hashing
    // 比较和 hash 操作

    /**
     * 比较指定对象和此 map 是否相等。如果指定对象也是 map 且两个集合对应
     * 的键值对完全相等，返回 true。
     *
     * @param o object to be compared for equality with this map
     * @return true if the specified object is equal to this map
     */
    boolean equals(Object o);

    /**
     * 返回 map 的 hash 值。map 的 hash 值定义为 map 的 entrySet 视图里
     * 每一对映射的 hash 值的和。
     *
     * @return the hash code value for this map
     * @see java.util.Map.Entry#hashCode()
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    int hashCode();

    // Defaultable methods
    // 有默认实现的方法

    /**
     * 返回指定 key 对应的 value，如果不包含该 key 的映射，则返回默认的
     * defaultValue 参数值。
     *
     * @implSpec
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the default mapping of the key
     * @return the value to which the specified key is mapped, or
     * {@code defaultValue} if this map contains no mapping for the key
     * @throws ClassCastException if the key is of an inappropriate type for
     * this map
     * @throws NullPointerException if the specified key is null and this map
     * does not permit null keys
     * @since 1.8
     */
    default V getOrDefault(Object key, V defaultValue) {
        V v;
        return (((v = get(key)) != null) || containsKey(key))
                ? v
                : defaultValue;
    }

    /**
     * 对 map 中每一个 entry 执行 action 定义的操作，知道所有 entry 执行完毕
     * 或者出现异常。除非 map 的实现类有规定，否则执行的顺序为 entrySet 中
     * entry 的顺序。执行中的异常抛给方法的调用者。
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * for (Map.Entry<K, V> entry : map.entrySet())
     *     action.accept(entry.getKey(), entry.getValue());
     * }
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param action The action to be performed for each entry
     * @throws NullPointerException if the specified action is null
     * @throws ConcurrentModificationException if an entry is found to be
     * removed during iteration
     * @since 1.8
     */
    default void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        // 获取每一个 entry，然后取出 key 和 value，传送给 action
        for (java.util.Map.Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch(IllegalStateException ise) {
                // this usually means the entry is no longer in the map.
                throw new ConcurrentModificationException(ise);
            }
            action.accept(k, v);
        }
    }

    /**
     * 对于 map 中的每一个 entry，将 value 替换成 BiFunction 返回的值。直到
     * 所有 entry 执行完毕或者出现异常为止。如果执行过程中出现异常，将其
     * 抛给调用者。
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * for (Map.Entry<K, V> entry : map.entrySet())
     *     entry.setValue(function.apply(entry.getKey(), entry.getValue()));
     * }
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param function the function to apply to each entry
     * @throws UnsupportedOperationException if the {@code set} operation
     * is not supported by this map's entry set iterator.
     * @throws ClassCastException if the class of a replacement value
     * prevents it from being stored in this map
     * @throws NullPointerException if the specified function is null, or the
     * specified replacement value is null, and this map does not permit null
     * values
     * @throws ClassCastException if a replacement value is of an inappropriate
     *         type for this map
     * @throws NullPointerException if function or a replacement value is null,
     *         and this map does not permit null keys or values
     * @throws IllegalArgumentException if some property of a replacement value
     *         prevents it from being stored in this map
     * @throws ConcurrentModificationException if an entry is found to be
     * removed during iteration
     * @since 1.8
     */
    default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        for (java.util.Map.Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch(IllegalStateException ise) {
                // this usually means the entry is no longer in the map.
                throw new ConcurrentModificationException(ise);
            }

            // ise thrown from function is not a cme.
            v = function.apply(k, v);

            try {
                entry.setValue(v);
            } catch(IllegalStateException ise) {
                // this usually means the entry is no longer in the map.
                throw new ConcurrentModificationException(ise);
            }
        }
    }

    /**
     * 如果指定的键还没有和值相关联（或者被映射为 null），则将它的 value
     * 设置为指定的值并返回 null，否则返回当前值。
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * V v = map.get(key);
     * if (v == null)
     *     v = map.put(key, value);
     * return v;
     * }
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with the key,
     *         if the implementation supports null values.)
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     * @throws ClassCastException if the key or value is of an inappropriate
     *         type for this map
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     * @throws IllegalArgumentException if some property of the specified key
     *         or value prevents it from being stored in this map
     * @since 1.8
     */
    default V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
        }

        return v;
    }

    /**
     * 如果指定的 key 和 value 在 map 中是一个 entry，那么删除这个 entry
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
     *     map.remove(key);
     *     return true;
     * } else
     *     return false;
     * }
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return {@code true} if the value was removed
     * @throws UnsupportedOperationException if the {@code remove} operation
     *         is not supported by this map
     * @throws ClassCastException if the key or value is of an inappropriate
     *         type for this map
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     * @since 1.8
     */
    default boolean remove(Object key, Object value) {
        Object curValue = get(key);
        // 如果 curValue 不等于 value 或者 （currValue 等于 null 且不包含该 key)
        // 则返回 false
        if (!Objects.equals(curValue, value) ||
                (curValue == null && !containsKey(key))) {
            return false;
        }
        remove(key);
        return true;
    }

    /**
     * 如果指定的 key 和 value 在 map 中是一个 entry，那么替换这个 entry 的 value
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
     *     map.put(key, newValue);
     *     return true;
     * } else
     *     return false;
     * }
     *
     * 此默认实现对于不支持 null 值但 oldValue 为 null 的映射不抛出
     * NullPointerException 异常，除非 newValue 也为 null
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return {@code true} if the value was replaced
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     * @throws ClassCastException if the class of a specified key or value
     *         prevents it from being stored in this map
     * @throws NullPointerException if a specified key or newValue is null,
     *         and this map does not permit null keys or values
     * @throws NullPointerException if oldValue is null and this map does not
     *         permit null values
     * @throws IllegalArgumentException if some property of a specified key
     *         or value prevents it from being stored in this map
     * @since 1.8
     */
    default boolean replace(K key, V oldValue, V newValue) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, oldValue) ||
                (curValue == null && !containsKey(key))) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    /**
     * 如果指定的 key 在 map 中有 value，则用指定 value 参数替换原来的 value。
     *
     * @implSpec
     * 此默认实现等价于以下代码:
     * {@code
     * if (map.containsKey(key)) {
     *     return map.put(key, value);
     * } else
     *     return null;
     * }
     *
     * 此默认实现不保证同步性和原子性。任何想要保证原子性的实现都必须重写
     * 此方法。
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@code null} if there was no mapping for the key.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with the key,
     *         if the implementation supports null values.)
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     * @throws NullPointerException if the specified key or value is null,
     *         and this map does not permit null keys or values
     * @throws IllegalArgumentException if some property of the specified key
     *         or value prevents it from being stored in this map
     * @since 1.8
     */
    default V replace(K key, V value) {
        V curValue;
        if (((curValue = get(key)) != null) || containsKey(key)) {
            curValue = put(key, value);
        }
        return curValue;
    }

    /**
     * If the specified key is not already associated with a value (or is mapped
     * to {@code null}), attempts to compute its value using the given mapping
     * function and enters it into this map unless {@code null}.
     *
     * <p>If the function returns {@code null} no mapping is recorded. If
     * the function itself throws an (unchecked) exception, the
     * exception is rethrown, and no mapping is recorded.  The most
     * common usage is to construct a new object serving as an initial
     * mapped value or memoized result, as in:
     *
     * <pre> {@code
     * map.computeIfAbsent(key, k -> new Value(f(k)));
     * }</pre>
     *
     * <p>Or to implement a multi-value map, {@code Map<K,Collection<V>>},
     * supporting multiple values per key:
     *
     * <pre> {@code
     * map.computeIfAbsent(key, k -> new HashSet<V>()).add(v);
     * }</pre>
     *
     *
     * @implSpec
     * The default implementation is equivalent to the following steps for this
     * {@code map}, then returning the current value or {@code null} if now
     * absent:
     *
     * <pre> {@code
     * if (map.get(key) == null) {
     *     V newValue = mappingFunction.apply(key);
     *     if (newValue != null)
     *         map.put(key, newValue);
     * }
     * }</pre>
     *
     * <p>The default implementation makes no guarantees about synchronization
     * or atomicity properties of this method. Any implementation providing
     * atomicity guarantees must override this method and document its
     * concurrency properties. In particular, all implementations of
     * subinterface {@link java.util.concurrent.ConcurrentMap} must document
     * whether the function is applied once atomically only if the value is not
     * present.
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with
     *         the specified key, or null if the computed value is null
     * @throws NullPointerException if the specified key is null and
     *         this map does not support null keys, or the mappingFunction
     *         is null
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @since 1.8
     */
    default V computeIfAbsent(K key,
                              Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return v;
    }

    /**
     * If the value for the specified key is present and non-null, attempts to
     * compute a new mapping given the key and its current mapped value.
     *
     * <p>If the function returns {@code null}, the mapping is removed.  If the
     * function itself throws an (unchecked) exception, the exception is
     * rethrown, and the current mapping is left unchanged.
     *
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if now absent:
     *
     * <pre> {@code
     * if (map.get(key) != null) {
     *     V oldValue = map.get(key);
     *     V newValue = remappingFunction.apply(key, oldValue);
     *     if (newValue != null)
     *         map.put(key, newValue);
     *     else
     *         map.remove(key);
     * }
     * }</pre>
     *
     * <p>The default implementation makes no guarantees about synchronization
     * or atomicity properties of this method. Any implementation providing
     * atomicity guarantees must override this method and document its
     * concurrency properties. In particular, all implementations of
     * subinterface {@link java.util.concurrent.ConcurrentMap} must document
     * whether the function is applied once atomically only if the value is not
     * present.
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key is null and
     *         this map does not support null keys, or the
     *         remappingFunction is null
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @since 1.8
     */
    default V computeIfPresent(K key,
                               BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        if ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                put(key, newValue);
                return newValue;
            } else {
                remove(key);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Attempts to compute a mapping for the specified key and its current
     * mapped value (or {@code null} if there is no current mapping). For
     * example, to either create or append a {@code String} msg to a value
     * mapping:
     *
     * <pre> {@code
     * map.compute(key, (k, v) -> (v == null) ? msg : v.concat(msg))}</pre>
     * (Method {@link #merge merge()} is often simpler to use for such purposes.)
     *
     * <p>If the function returns {@code null}, the mapping is removed (or
     * remains absent if initially absent).  If the function itself throws an
     * (unchecked) exception, the exception is rethrown, and the current mapping
     * is left unchanged.
     *
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if absent:
     *
     * <pre> {@code
     * V oldValue = map.get(key);
     * V newValue = remappingFunction.apply(key, oldValue);
     * if (oldValue != null ) {
     *    if (newValue != null)
     *       map.put(key, newValue);
     *    else
     *       map.remove(key);
     * } else {
     *    if (newValue != null)
     *       map.put(key, newValue);
     *    else
     *       return null;
     * }
     * }</pre>
     *
     * <p>The default implementation makes no guarantees about synchronization
     * or atomicity properties of this method. Any implementation providing
     * atomicity guarantees must override this method and document its
     * concurrency properties. In particular, all implementations of
     * subinterface {@link java.util.concurrent.ConcurrentMap} must document
     * whether the function is applied once atomically only if the value is not
     * present.
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the specified key is null and
     *         this map does not support null keys, or the
     *         remappingFunction is null
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @since 1.8
     */
    default V compute(K key,
                      BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);

        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            // delete mapping
            if (oldValue != null || containsKey(key)) {
                // something to remove
                remove(key);
                return null;
            } else {
                // nothing to do. Leave things as they were.
                return null;
            }
        } else {
            // add or replace old mapping
            put(key, newValue);
            return newValue;
        }
    }

    /**
     * If the specified key is not already associated with a value or is
     * associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is {@code null}. This
     * method may be of use when combining multiple mapped values for a key.
     * For example, to either create or append a {@code String msg} to a
     * value mapping:
     *
     * <pre> {@code
     * map.merge(key, msg, String::concat)
     * }</pre>
     *
     * <p>If the function returns {@code null} the mapping is removed.  If the
     * function itself throws an (unchecked) exception, the exception is
     * rethrown, and the current mapping is left unchanged.
     *
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if absent:
     *
     * <pre> {@code
     * V oldValue = map.get(key);
     * V newValue = (oldValue == null) ? value :
     *              remappingFunction.apply(oldValue, value);
     * if (newValue == null)
     *     map.remove(key);
     * else
     *     map.put(key, newValue);
     * }</pre>
     *
     * <p>The default implementation makes no guarantees about synchronization
     * or atomicity properties of this method. Any implementation providing
     * atomicity guarantees must override this method and document its
     * concurrency properties. In particular, all implementations of
     * subinterface {@link java.util.concurrent.ConcurrentMap} must document
     * whether the function is applied once atomically only if the value is not
     * present.
     *
     * @param key key with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value
     *        associated with the key or, if no existing value or a null value
     *        is associated with the key, to be associated with the key
     * @param remappingFunction the function to recompute a value if present
     * @return the new value associated with the specified key, or null if no
     *         value is associated with the key
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws ClassCastException if the class of the specified key or value
     *         prevents it from being stored in this map
     *         (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified key is null and this map
     *         does not support null keys or the value or remappingFunction is
     *         null
     * @since 1.8
     */
    default V merge(K key, V value,
                    BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        V newValue = (oldValue == null) ? value :
                remappingFunction.apply(oldValue, value);
        if(newValue == null) {
            remove(key);
        } else {
            put(key, newValue);
        }
        return newValue;
    }
}

