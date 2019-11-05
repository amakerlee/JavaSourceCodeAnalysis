package Collections;
/*
 * Copyright (c) 1995, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Enumeration;

/**
 * Dictionary 抽象类是所有存储键值对数据结构的抽象父类，例如 Hashtable。每个
 * 键值对都是一个对象。在任何一个 Dictionary 对象中，每一个 key 至多和一个
 * value 关联。给定一个 Dictionary 和一个 key，都能找到关联的 value。任何非空的
 * 对象都可以当成 key 或者 value。
 * 有一个默认的规则是，应该使用 equals 方法来确定两个 key 是否相同。
 * 注意：这个类已经过时了。新的实现应该基于 Map 接口，而不是扩展此类。
 *
 * @author  unascribed
 * @see     java.util.Map
 * @see     java.lang.Object#equals(java.lang.Object)
 * @see     java.lang.Object#hashCode()
 * @see     java.util.Hashtable
 * @since   JDK1.0
 */
public abstract
class Dictionary<K,V> {
    /**
     * 构造函数
     */
    public Dictionary() {
    }

    /**
     * 返回字典中键值对的数量
     *
     * @return  the number of keys in this dictionary.
     */
    abstract public int size();

    /**
     * 测试字典是否为空。当且仅当字典不包含任何键值对的时候此函数返回 true。
     *
     * @return  <code>true</code> if this dictionary maps no keys to values;
     *          <code>false</code> otherwise.
     */
    abstract public boolean isEmpty();

    /**
     * 返回字典中所有 key 的枚举。
     *
     * @return  an enumeration of the keys in this dictionary.
     * @see     java.util.Dictionary#elements()
     * @see     java.util.Enumeration
     */
    abstract public Enumeration<K> keys();

    /**
     * 返回字典中所有 value 的枚举。
     *
     * @return  an enumeration of the values in this dictionary.
     * @see     java.util.Dictionary#keys()
     * @see     java.util.Enumeration
     */
    abstract public Enumeration<V> elements();

    /**
     * 获取指定 key 对应的 value。如果字典中包含此映射则返回 value，否则返回 null。
     *
     * @return  the value to which the key is mapped in this dictionary;
     * @param   key   a key in this dictionary.
     *          <code>null</code> if the key is not mapped to any value in
     *          this dictionary.
     * @exception NullPointerException if the <tt>key</tt> is <tt>null</tt>.
     * @see     java.util.Dictionary#put(java.lang.Object, java.lang.Object)
     */
    abstract public V get(Object key);

    /**
     * 将指定 key 及其对应的 value 插入到字典中。 key 和 value 都不能为 null。
     *
     * 如果字典中已经包含了指定 key，返回指定 key 对应的 value，并将 value 更新成
     * 指定的 value。如果不包含此映射，则创建新的 entry 节点，添加到字典里。
     *
     * @param      key     the hashtable key.
     * @param      value   the value.
     * @return     the previous value to which the <code>key</code> was mapped
     *             in this dictionary, or <code>null</code> if the key did not
     *             have a previous mapping.
     * @exception  NullPointerException  if the <code>key</code> or
     *               <code>value</code> is <code>null</code>.
     * @see        java.lang.Object#equals(java.lang.Object)
     * @see        java.util.Dictionary#get(java.lang.Object)
     */
    abstract public V put(K key, V value);

    /**
     * 删除指定 key 对应的 entry。如果指定 key 不在字典中，不做任何操作。
     *
     * @param   key   the key that needs to be removed.
     * @return  the value to which the <code>key</code> had been mapped in this
     *          dictionary, or <code>null</code> if the key did not have a
     *          mapping.
     * @exception NullPointerException if <tt>key</tt> is <tt>null</tt>.
     */
    abstract public V remove(Object key);
}

