## TreeMap

### 继承结构及完整源码解析

[Map](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Map.java) | [SortedMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/SortedMap.java) | [NavigableMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/NavigableMap.java) | [AbstractMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractMap.java) | [TreeMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/TreeMap.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/TreeMap.png" width=70% />

### 类属性

```java
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

```

### 成员函数

TreeMap 基本数据结构为红黑树，与红黑树相关内容参照 [HashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/md/HashMap.md)。

**查找方法**

```java
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
```

### TreeMap 小结

* TreeMap 是基于红黑树和 NavigableMap 实现的映射。所有的键值对节点组织成一个红黑树结构。

* TreeMap 完全基于红黑树结构，而红黑树是二叉检索树，所以相比 HashMap 完全无序的结构，TreeMap 的成员函数和内部类中，包含大量和节点顺序相关的操作，例如查找和指定值最接近的上一个节点或下一个节点。

* 对查询，插入，删除操作而言，HashMap 效率远远高于 TreeMap。