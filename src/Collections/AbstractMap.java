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
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * 此类提供 Map 接口的基本实现，以最小化实现此接口所需的工作。
 *
 * 为了实现不可修改的映射，程序员只需要继承此类，并提供 entrySet 方法
 * 的实现，entrySet 方法返回 map 每一个映射的集合视图。通常，返回的
 * 集合在 AbstractSet 之上实现。这个集合不应该支持 add 或者 remove
 * 方法，它的迭代器不应该支持 remove 方法。
 *
 * 为了实现可修改的映射，程序员必须额外覆盖此类的 put 方法（否则会抛出
 * UnsupportedOperationException 异常），entrySet().iterator() 返回的
 * 迭代器必须额外实现它的 remove 方法。
 *
 * 按照 Map 接口规范的建议，程序员应该提供一个 void（无参数）和 map
 * 构造函数。
 *
 * 此类中每一个非抽象的方法文档中都描述了其具体实现。如果正在实现的
 * 映射允许更有效的实现，则可以覆盖它们。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see Map
 * @see Collection
 * @since 1.2
 */

public abstract class AbstractMap<K,V> implements Map<K,V> {
    /**
     * 唯一的构造函数。（用于子类的构造函数调用，特别是隐式的。）
     */
    protected AbstractMap() {
    }

    // Query Operations
    // 查询操作

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * This implementation returns entrySet().size().
     */
    public int size() {
        return entrySet().size();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * This implementation returns size() == 0.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现迭代遍历 entrySet() 来搜索指定 value 所在的 entry。如果这样
     * 的 entry 存在的话，返回 true。如果没有找到这样的 entry，迭代停止，
     * 并返回 false。注意这一实现需要线性时间。
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean containsValue(Object value) {
        Iterator<Entry<K,V>> i = entrySet().iterator();
        if (value==null) {
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (e.getValue()==null)
                    return true;
            }
        } else {
            // 迭代遍历 entrySet
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (value.equals(e.getValue()))
                    return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现迭代遍历 entrySet() 来搜索指定 key 所在的 entry。如果这样
     * 的 entry 存在的话，返回 true。如果没有找到这样的 entry，迭代停止，
     * 并返回 false。注意这一实现需要线性时间。
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean containsKey(Object key) {
        Iterator<Map.Entry<K,V>> i = entrySet().iterator();
        if (key==null) {
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (e.getKey()==null)
                    return true;
            }
        } else {
            // 迭代遍历 entrySet
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (key.equals(e.getKey()))
                    return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现迭代遍历 entrySet() 来搜索指定 key 所在的 entry。如果这样
     * 的 entry 存在的话，返回 entry 的 value 值。如果没有找到这样的
     * entry，迭代停止，并返回 null。注意这一实现需要线性时间。
     *
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     */
    public V get(Object key) {
        Iterator<Entry<K,V>> i = entrySet().iterator();
        if (key==null) {
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (e.getKey()==null)
                    return e.getValue();
            }
        } else {
            // 迭代遍历 entrySet
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                if (key.equals(e.getKey()))
                    return e.getValue();
            }
        }
        return null;
    }


    // Modification Operations
    // 修改操作

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * This implementation always throws an UnsupportedOperationException.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现迭代遍历 entrySet 搜索指定 key 所在的 entry。如果找到满足
     * 条件的 entry，通过 getValue 方法获取它的 value 值，并使用迭代器的
     * remove 方法删除该 entry。如果没有找到满足条件的 entry，迭代停止
     * 并返回 null。注意这一实现需要线性时间。
     *
     * 如果 entrySet 的迭代不支持 remove 方法且此 map 包含指定 key 的映射，
     * 此实现会抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     */
    public V remove(Object key) {
        Iterator<Entry<K,V>> i = entrySet().iterator();
        Entry<K,V> correctEntry = null;
        // correctEntry 指向将要删除的 entry
        if (key==null) {
            while (correctEntry==null && i.hasNext()) {
                Entry<K,V> e = i.next();
                if (e.getKey()==null)
                    correctEntry = e;
            }
        } else {
            while (correctEntry==null && i.hasNext()) {
                Entry<K,V> e = i.next();
                if (key.equals(e.getKey()))
                    correctEntry = e;
            }
        }

        // 删除 entry 并返回其 value
        V oldValue = null;
        if (correctEntry !=null) {
            oldValue = correctEntry.getValue();
            i.remove();
        }
        return oldValue;
    }


    // Bulk Operations
    // 批量操作

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现对指定 map 的 entrySet 进行迭代，然后对每一个 entry 调用
     * map 的 put 操作。
     *
     * 如果此 map 不支持 put 方法且指定的 map 非空，此实现会抛出
     * UnsupportedOperationException 异常。
     * 此实现会抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        for (java.util.Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现调用 entrySet.clear 方法。
     *
     * 如果 entrySet 不支持 clear 操作此实现会抛出
     * UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     */
    public void clear() {
        entrySet().clear();
    }


    // Views
    // 视图

    /**
     * 第一次请求视图的时候初始化这些字段，用来容纳一个适当的视图实例。
     * 这个视图是无状态的，所以没有理由创建多个视图。
     *
     * 由于在访问这些字段的时候没有执行同步，所以希望使用这些字段的
     * java.util.Map 视图类没有非 final 字段（或者出了 outer-this 之外的字段）。
     *
     * 此实现只读取这些字段一次：
     * {@code
     * public Set<K> keySet() {
     *   Set<K> ks = keySet;  // single racy read
     *   if (ks == null) {
     *     ks = new KeySet();
     *     keySet = ks;
     *   }
     *   return ks;
     * }
     *}
     */
    transient Set<K>        keySet;
    transient Collection<V> values;

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现返回一个继承自 AbstractSet 的集合。子类的迭代方法返回一个
     * 在 map 的 entrySet 迭代器之上的“包装器对象”。size 方法委托给此
     * map 的 size 方法，contains 方法委托给此 map 的 containsKey 方法。
     *
     * 此集合在方法第一次调用的时候创建，并作为对所有后续调用的返回结果。
     * 因为不执行同步，所以对该方法的多个调用可能不会返回相同的 set。
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new AbstractSet<K>() {
                // AbstractSet 的迭代器
                public Iterator<K> iterator() {
                    return new Iterator<K>() {
                        private Iterator<Entry<K,V>> i = entrySet().iterator();

                        // 是否有下一个
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        // 向右移动一位，并返回 key
                        public K next() {
                            return i.next().getKey();
                        }

                        // 删除
                        public void remove() {
                            i.remove();
                        }
                    };
                }

                // 返回 Map 的大小
                public int size() {
                    return AbstractMap.this.size();
                }

                // 判断 Map 是否为空
                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                // 清除 Map 的所有元素
                public void clear() {
                    AbstractMap.this.clear();
                }

                // 是否包含指定 key
                public boolean contains(Object k) {
                    return AbstractMap.this.containsKey(k);
                }
            };
            keySet = ks;
        }
        return ks;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec
     * 此实现返回一个继承自 AbstractCollection 的集合。子类的迭代方法
     * 返回一个在 map 的 entrySet 迭代器之上的“包装器对象”。size 方法
     * 委托给此 map 的 size 方法，contains 方法委托给此 map 的
     * containsValue 方法。
     *
     * 此集合在方法第一次调用的时候创建，并作为对所有后续调用的返回结果。
     * 因为不执行同步，所以对该方法的多个调用可能不会返回相同的 set。
     */
    public Collection<V> values() {
        Collection<V> vals = values;
        if (vals == null) {
            vals = new AbstractCollection<V>() {
                // 集合的迭代器
                public Iterator<V> iterator() {
                    return new Iterator<V>() {
                        private Iterator<Entry<K,V>> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public V next() {
                            return i.next().getValue();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return AbstractMap.this.size();
                }

                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                public void clear() {
                    AbstractMap.this.clear();
                }

                public boolean contains(Object v) {
                    return AbstractMap.this.containsValue(v);
                }
            };
            values = vals;
        }
        return vals;
    }

    public abstract Set<Entry<K,V>> entrySet();


    // Comparison and hashing
    // 比较和 hash 操作

    /**
     * 比较指定对象和此 map 是否相等。如果指定对象也是 map 且两个 map
     * 的所有映射对应相等，则返回 true。
     *
     * @implSpec
     * 此实现首先检查指定对象是否是此 map 对象，如果是返回 true。然后
     * 检查指定对象是否是 map 且其的大小是否和此 map 相等，如果不是
     * 返回 false。如果是，接着遍历 map 的 entrySet 集合，检查指定的 map
     * 是否包含此 map 的每一个映射。如果没有返回 false。迭代结束之后，
     * 返回 true。
     *
     * @param o object to be compared for equality with this map
     * @return true if the specified object is equal to this map
     */
    public boolean equals(Object o) {
        // 检查指定对象是否是此 map
        if (o == this)
            return true;

        //检查指定对象是否是 map 对象，且其大小是否和此 map 的大小相等
        if (!(o instanceof Map))
            return false;
        Map<?,?> m = (Map<?,?>) o;
        if (m.size() != size())
            return false;

        // 遍历 entrySet 中所有映射
        try {
            Iterator<Entry<K,V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    // map 允许 value 为 null 的情况
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    /**
     * 返回此 map 的 hash 值。一个 map 的 hash 值定义为 entrySet 中每一个
     * entry 的 hash 值的和。
     *
     * @return the hash code value for this map
     * @see Map.Entry#hashCode()
     * @see Object#equals(Object)
     * @see Set#equals(Object)
     */
    public int hashCode() {
        int h = 0;
        Iterator<Entry<K,V>> i = entrySet().iterator();
        // 遍历 entrySet 中所有的 entry，将每一个 entry 的 hash 值相加
        while (i.hasNext())
            h += i.next().hashCode();
        return h;
    }

    /**
     * 返回此 map 的字符串表示方式。字符串表示方式包括按 entrySet
     * 迭代器返回顺序的 key-value 映射列表，用 “{}” 括起来。相邻的映射之间
     * 用 “, ” 分隔（逗号和空格）。每一个映射的格式为 “key=value”。key 和
     * value 使用 String.valueOf 方法转化成 string 格式。
     *
     * @return a string representation of this map
     */
    public String toString() {
        Iterator<Entry<K,V>> i = entrySet().iterator();
        // 不包含任何映射
        if (! i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<K,V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key   == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (! i.hasNext())
                return sb.append('}').toString();
            sb.append(',').append(' ');
        }
    }

    /**
     * 返回此 AbstractMap 实例的一个浅拷贝：key 和 value 对象本身不拷贝。
     *
     * @return a shallow copy of this map
     */
    protected Object clone() throws CloneNotSupportedException {
        AbstractMap<?,?> result = (AbstractMap<?,?>)super.clone();
        result.keySet = null;
        result.values = null;
        return result;
    }

    private static boolean eq(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    // SimpleEntry 和 SimpleImmutableEntry 是不同且不相关的类，尽管
    // 它们共享一些代码。由于不能在子类中添加或删除字段的 final-ness，
    // 所以它们不能共享表示形式，而且由于重复的代码量太小，所以不足以
    // 保证公共抽象类。


    /**
     * 包含一个 key 和 一个 value 的 Entry。可以使用 setValue 方法改变 value。
     * 此类简化了构建已定义 map 实现的过程。例如在 Map.entrySet().toArray
     * 方法中返回 SimpleEntry 实例的数组会很方便。
     *
     * @since 1.6
     */
    public static class SimpleEntry<K,V>
            implements Entry<K,V>, java.io.Serializable
    {
        private static final long serialVersionUID = -8499721149061103585L;

        private final K key;
        private V value;

        /**
         * 创建一个表示指定 key 到指定 value 的映射。
         *
         * @param key the key represented by this entry
         * @param value the value represented by this entry
         */
        public SimpleEntry(K key, V value) {
            this.key   = key;
            this.value = value;
        }

        /**
         * 创建一个和指定 entry 表示的映射相同的 entry。
         *
         * @param entry the entry to copy
         */
        public SimpleEntry(Entry<? extends K, ? extends V> entry) {
            this.key   = entry.getKey();
            this.value = entry.getValue();
        }

        /**
         * 返回 entry 的 key。
         *
         * @return the key corresponding to this entry
         */
        public K getKey() {
            return key;
        }

        /**
         * 返回 entry 的 value。
         *
         * @return the value corresponding to this entry
         */
        public V getValue() {
            return value;
        }

        /**
         * 用指定的 value 替换此 entry 的 value 值。
         *
         * @param value new value to be stored in this entry
         * @return the old value corresponding to the entry
         */
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        /**
         * 比较指定的对象和此 entry 是否相等。
         * 如果指定的对象也是一个 map entry 且两个 entry 表示相同的映射，
         * 则返回 true。
         *
         * @param o object to be compared for equality with this map entry
         * @return {@code true} if the specified object is equal to this map
         *         entry
         * @see    #hashCode
         */
        public boolean equals(Object o) {
            // 首先判断是否是 Map.Entry
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            // 然后判断 key 和 value 是否分别相等
            return eq(key, e.getKey()) && eq(value, e.getValue());
        }

        /**
         * 返回此 map entry 的 hash 值。 一个 map entry 的 hash 值定义成：
         *   (e.getKey()==null ? 0 : e.getKey().hashCode()) ^
         *   (e.getValue()==null ? 0 : e.getValue().hashCode())
         *
         * @return the hash code value for this map entry
         * @see    #equals
         */
        public int hashCode() {
            return (key   == null ? 0 :   key.hashCode()) ^
                    (value == null ? 0 : value.hashCode());
        }

        /**
         * 返回 map entry 的字符串表示。字符串格式为 “key=value”。
         *
         * @return a String representation of this map entry
         */
        public String toString() {
            return key + "=" + value;
        }

    }

    /**
     * 一个 key 和 value 不可变的 entry。此类不支持 setValue 方法。此类
     * 中的方法可以用来返回一个线程安全的 key-value 快照。
     *
     * @since 1.6
     */
    public static class SimpleImmutableEntry<K,V>
            implements Entry<K,V>, java.io.Serializable
    {
        private static final long serialVersionUID = 7138329143949025153L;

        private final K key;
        private final V value;

        /**
         * 创建一个表示指定 key 到指定 value 的映射。
         *
         * @param key the key represented by this entry
         * @param value the value represented by this entry
         */
        public SimpleImmutableEntry(K key, V value) {
            this.key   = key;
            this.value = value;
        }

        /**
         * 创建一个和指定 entry 表示的映射相同的 entry。
         *
         * @param entry the entry to copy
         */
        public SimpleImmutableEntry(Entry<? extends K, ? extends V> entry) {
            this.key   = entry.getKey();
            this.value = entry.getValue();
        }

        /**
         * 返回 entry 的 key。
         *
         * @return the key corresponding to this entry
         */
        public K getKey() {
            return key;
        }

        /**
         * 返回 entry 的 value。
         *
         * @return the value corresponding to this entry
         */
        public V getValue() {
            return value;
        }

        /**
         * 由于此类表示不可变的 map entry，所以一旦调用 setValue 方法
         * 直接抛出 UnsupportedOperationException 异常。
         *
         * @param value new value to be stored in this entry
         * @return (Does not return)
         * @throws UnsupportedOperationException always
         */
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        /**
         * 比较指定的对象和此 entry 是否相等。
         * 如果指定的对象也是一个 map entry 且两个 entry 表示相同的映射，
         * 则返回 true。
         *
         * @param o object to be compared for equality with this map entry
         * @return {@code true} if the specified object is equal to this map
         *         entry
         * @see    #hashCode
         */
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return eq(key, e.getKey()) && eq(value, e.getValue());
        }

        /**
         * 返回此 map entry 的 hash 值。 一个 map entry 的 hash 值定义成：
         *   (e.getKey()==null ? 0 : e.getKey().hashCode()) ^
         *   (e.getValue()==null ? 0 : e.getValue().hashCode())
         *
         * @return the hash code value for this map entry
         * @see    #equals
         */
        public int hashCode() {
            return (key   == null ? 0 :   key.hashCode()) ^
                    (value == null ? 0 : value.hashCode());
        }

        /**
         * 返回 map entry 的字符串表示。字符串格式为 “key=value”。
         *
         * @return a String representation of this map entry
         */
        public String toString() {
            return key + "=" + value;
        }

    }

}

