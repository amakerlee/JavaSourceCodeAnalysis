## Stack

### 继承结构及完整源码解析

[Iterable](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Iterable.java) | [Collection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Collection.java) | [List](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/List.java) | [AbstractCollection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractCollection.java) | [AbstractList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractList.java) | [Vector](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Vector.java) | [Stack](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Stack.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/Stack.png" width=50% />

### ArrayList 与 Vector

**接口**

两个类都实现了 List 接口，且每个方法的实现几乎都是一样的。

**底层数据结构**

底层数据结构都是数组，所以在有索引的时候，查询和修改操作都很快，而增加和删除操作很慢。

**扩容策略**

大多数情况下 ArrayList 每次扩容为原来的 1.5 倍，而 Vector 扩容为原来的 2 倍。

**线程安全**

ArrayList 是线程不安全的，Vector 是线程安全的，但是保证线程安全的策略仅仅是在方法前加上 synchronized 修饰符，所以 Vector 的效率极低。
