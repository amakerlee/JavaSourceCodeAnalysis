## ConcurrentSkipListMap

在 ConcurrentSkipListMap 之前，了解跳跃表的概念和基本操作是必要的，忽略跳跃表的实际结构直接阅读源码难度较大。

### 完整源码解析

[ConcurrentSkipListMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/ConcurrentSkipListMap.java)

### 跳跃表

跳跃表是一种可以用来代替平衡树的数据结构，实际上就是在链表之上添加多级索引，通过索引实现链表中的快速查找。例如有下面这样的基础链表：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap1.png" width=70% />

在链表之上构造两层索引，如下所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap2.png" width=70% />

在最初的链表中查找节点 20 需要从头遍历所有节点，在跳跃表中从最高层的头结点开始查找，路径如下图所示，只需要经过索引节点 1,5,17,20 即可：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap3.png" width=70% />

标准化的链表中每两个元素提取一个元素作为上一层的索引，在数据量大的时候，如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap4.png" width=70% />

这是典型的以空间换时间的数据结构。每一次需要考虑的元素减少一半，查找的时间复杂度基本相当于平衡二叉树。

在 ConcurrentSkipListMap 的具体实现中，跳跃表主要由三种类型的节点构成，他们之间的关系如下：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ConcurrentSkipListMap5.png" width=70% />

除此之外，ConcurrentSkipListMap 通过随机的方式决定是否增加层级。

### 内部类




### 类属性

跳跃表中有两个重要的属性，一个是 BASE_HEADER，是最底层存储实际数据节点的链表的头结点；另一个是 head，是索引层中最高层的头结点，跳跃表中任何有效节点都可以从该节点到达。

```java
    /**
     * 最底层（base-level）的头结点
     */
    private static final Object BASE_HEADER = new Object();

    /**
     * 最高层的头结点
     */
    private transient volatile HeadIndex<K,V> head;
```

### 成员函数



### 为什么 Redis 使用跳跃表而不是红黑树实现有序集合



### 引用

[死磕 java集合之ConcurrentSkipListMap源码分析——发现个bug](https://www.cnblogs.com/tong-yuan/p/ConcurrentSkipListMap.html)

[基于跳跃表的 ConcurrentSkipListMap 内部实现（Java 8）](https://www.cnblogs.com/yangming1996/p/8084819.html)

[JUC源码分析-集合篇（三）：ConcurrentSkipListMap和ConcurrentSkipListSet](https://www.jianshu.com/p/8a223af84fc4)

[拜托，面试别再问我跳表了！ ](https://mp.weixin.qq.com/s/wacN04NHN2Zm0mZIlftxaw)