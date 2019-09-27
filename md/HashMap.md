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

对于指定的参数 cap，返回比它大的最小的 2 的幂。

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




***
> HashMap 小结

HashMap 是常用集合类中最复杂，也是设计最巧妙的一种数据结构。其底层的数据结构中用到了数组，双向链表以及红黑树，

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