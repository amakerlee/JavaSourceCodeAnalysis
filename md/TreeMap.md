### TreeMap

***
> 继承结构及完整源码解析

[Map](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Map.java) | [SortedMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/SortedMap.java) | [NavigableMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/NavigableMap.java) | [AbstractMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractMap.java) | [TreeMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/TreeMap.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/TreeMap.png" width=70% />

***
> 类属性

```java

```

***
> 成员函数

**add 方法和扩容方法**

```java

```

***
> TreeMap 小结

1. 由于 ArrayList 中的元素存储在数组里，即 ArrayList 的位置访问操作为数组的位置访问操作，所以 ArrayList 查找效率较高，但是插入删除效率低，因为插入删除操作会移动数组指定位置的前方或后方大量的元素。
2. ArrayList 插入删除等基本方法均用到 Arrays.copy() 或 System.arraycopy 函数进行批量数组元素的复制。
3. ArrayList 每次增加元素的时候，都需要调用 ensureCapacity 方法确保足够的容量。在能够实现确定元素数量的情况下首选 ArrayList，否则使用 LinkedList。
