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
import java.io.IOException;

/**
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Josh Bloch
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     HashMap
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.4
 */
public class LinkedHashMap<K,V>
        extends HashMap<K,V>
        implements Map<K,V>
{
    /**
     * LinkedHashMap 的节点类。
     */
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after;
        Entry(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }

    /**
     * 双向链表的头结点。
     */
    transient LinkedHashMap.Entry<K,V> head;

    /**
     * 双向链表的尾节点（最新插入的节点）。
     */
    transient LinkedHashMap.Entry<K,V> tail;

    /**
     * 节点存储的顺序：
     * 如果为 true 表示以访问（get、put）模式存储，最新访问的放在链表末尾
     * 如果为 false 表示以插入（put）模式存储，最近插入的放在链表末尾（注意此处的插入不包括修改）
     *
     * @serial
     */
    final boolean accessOrder;

    // 内部工具

    // 链表末尾添加节点
    private void linkNodeLast(LinkedHashMap.Entry<K,V> p) {
        LinkedHashMap.Entry<K,V> last = tail;
        tail = p;
        if (last == null)
            head = p;
        else {
            p.before = last;
            last.after = p;
        }
    }

    // 将 src 节点替换成 dst 节点
    private void transferLinks(LinkedHashMap.Entry<K,V> src,
                               LinkedHashMap.Entry<K,V> dst) {
        LinkedHashMap.Entry<K,V> b = dst.before = src.before;
        LinkedHashMap.Entry<K,V> a = dst.after = src.after;
        if (b == null)
            head = dst;
        else
            b.after = dst;
        if (a == null)
            tail = dst;
        else
            a.before = dst;
    }

    // overrides of HashMap hook methods

    void reinitialize() {
        super.reinitialize();
        head = tail = null;
    }

    // 创建 LinkedHashMap 节点
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        LinkedHashMap.Entry<K,V> p =
                new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        // 将新节点 p 设置为新的尾节点
        linkNodeLast(p);
        return p;
    }

    // 将节点转化为 LinkedHashMap 节点，回收原来的节点
    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        LinkedHashMap.Entry<K,V> t =
                new LinkedHashMap.Entry<K,V>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    // 创建树节点
    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        TreeNode<K,V> p = new TreeNode<K,V>(hash, key, value, next);
        linkNodeLast(p);
        return p;
    }

    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        TreeNode<K,V> t = new TreeNode<K,V>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    // 节点删除之后的操作
    void afterNodeRemoval(Node<K,V> e) { // unlink
        LinkedHashMap.Entry<K,V> p =
                (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        // 将前一个节点和后一个节点连接起来
        p.before = p.after = null;
        if (b == null)
            head = a;
        else
            b.after = a;
        if (a == null)
            tail = b;
        else
            a.before = b;
    }

    // 插入节点之后调整链表结构
    // 移除最老的节点
    void afterNodeInsertion(boolean evict) { // possibly remove eldest
        LinkedHashMap.Entry<K,V> first;
        // 默认的 removeEldestEntry 永远返回 false，实现 LRU 需要重写此函数
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    // 访问节点之后调整链表结构
    // 将访问的节点移动到链表末尾
    void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMap.Entry<K,V> last;
        // 如果 accessOrder 为 true 说明是访问模式，需要调整
        // 如果 accessOrder 为 false 说明是插入模式，不需要调整
        if (accessOrder && (last = tail) != e) {
            // p 当前节点
            // b 前一个节点
            // a 后一个节点
            LinkedHashMap.Entry<K,V> p =
                    (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            // 连接 a、b
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            // p 移到最后
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }

    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    /**
     * 构造函数
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    /**
     * 构造函数
     *
     * @param  initialCapacity the initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    /**
     * 构造函数
     */
    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    /**
     * 构造函数
     *
     * @param  m the map whose mappings are to be placed in this map
     * @throws NullPointerException if the specified map is null
     */
    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super();
        accessOrder = false;
        putMapEntries(m, false);
    }

    /**
     * 构造函数
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @param  accessOrder     the ordering mode - <tt>true</tt> for
     *         access-order, <tt>false</tt> for insertion-order
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public LinkedHashMap(int initialCapacity,
                         float loadFactor,
                         boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }


    /**
     * 是否包含指定 value
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }

    /**
     * 重写 HashMap 的 get 函数
     */
    public V get(Object key) {
        Node<K,V> e;
        // 不存在或者为 null
        if ((e = getNode(hash(key), key)) == null)
            return null;
        // 如果是访问模式，访问某个节点之后还需要调整链表节点顺序
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * 重写 getOrDefault 函数
     * {@inheritDoc}
     */
    public V getOrDefault(Object key, V defaultValue) {
        Node<K,V> e;
        if ((e = getNode(hash(key), key)) == null)
            return defaultValue;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * 重写 clear 函数
     * {@inheritDoc}
     */
    public void clear() {
        super.clear();
        head = tail = null;
    }

    /**
     * 此方法在 put 和 putAll 里调用，用于删除最老的节点。
     *
     * 案例：最大容量为 100 的 LinkedHashMap：
     *     private static final int MAX_ENTRIES = 100;
     *
     *     protected boolean removeEldestEntry(Map.Entry eldest) {
     *        return size() > MAX_ENTRIES;
     *     }
     *
     * @param    eldest The least recently inserted entry in the map, or if
     *           this is an access-ordered map, the least recently accessed
     *           entry.  This is the entry that will be removed it this
     *           method returns <tt>true</tt>.  If the map was empty prior
     *           to the <tt>put</tt> or <tt>putAll</tt> invocation resulting
     *           in this invocation, this will be the entry that was just
     *           inserted; in other words, if the map contains a single
     *           entry, the eldest entry is also the newest.
     * @return   <tt>true</tt> if the eldest entry should be removed
     *           from the map; <tt>false</tt> if it should be retained.
     */
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }
}

