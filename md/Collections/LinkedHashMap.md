## LinkedHashMap

LinkedHashMap 继承自 HashMap，在 HashMap 的基础上，通过双向链表来维持节点有序。

在 HashMap 已经为 LinkedHashMap 预留了位置添加链表操作，即 afterNodeInsertion、afterNodeRemoval 等，在 HashMap 中这些函数为空函数，在 LinkedHashMap 中重写这些函数，实现链表的创建和维护。

LinkedHashMap 不保证线程安全。

### 完整源码解析

[LinkedHashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/LinkedHashMap.java)

### 内部类 Entry

继承自 HashMap 的 Node 节点类，多了 before、after 两个属性，分别指向双向链表的前一个节点和后一个节点。

```java
    /**
     * LinkedHashMap 的节点类。
     */
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after;
        Entry(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }
```

### 类属性

在 HashMap 的基础上多了三个属性，前两个比较简单，分别存储双向链表的头结点和尾节点，最后一个 accessOrder 是用来控制双向链表中节点的存储顺序而设置的。

双向链表中节点顺序有以下两种模式：

* 插入模式：节点的顺序按照插入的顺序排序，新插入的节点直接添加到链表末尾。注意，使用 put 修改已存在节点的值不属于插入操作。

* 访问模式：节点的顺序根据访问（get，put）的顺序而改变，任何访问操作都会把当前节点移动到链表末尾。

accessOrder 默认为插入模式（false），其值设置为 true 时变成访问模式。

```java
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
```

### 成员函数

创建新节点使用 newNode 函数，创建后还需要将节点添加到链表尾部。

**newNode**

```java
    // 创建 LinkedHashMap 节点
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        LinkedHashMap.Entry<K,V> p =
                new LinkedHashMap.Entry<K,V>(hash, key, value, e);
        // 将新节点 p 设置为新的尾节点
        linkNodeLast(p);
        return p;
    }
    
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
```

访问节点之后判断是否需要调整链表中节点顺序，如果是访问模式，将当前节点移动到链表末尾。

**afterNodeAccess**

```java
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
```

插入的后续操作。

**afterNodeInsertion**

```java
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
```

删除的后续操作。

**afterNodeRemoval**

```java
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
```

是否移除最老的节点。

**removeEldestEntry**

```java
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
```

### 总结

不难看出 LinkedHashMap 就只是在 HashMap 之上添加了一个 LinkedList 而已，并没有构造新的节点或结构，而且维护双向链表的代码也非常简单。

LinkedHashMap 中比较重要的是 accessOrder 属性，它定义了双向链表中节点的存储序列，可用于实现 LRU 等淘汰算法。