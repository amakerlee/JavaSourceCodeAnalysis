# ArrayList

***
> 继承结构及完整源码

[Iterable](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Iterable.java) | [Collection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Collection.java) | [List](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/List.java) | [AbstractCollection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractCollection.java) | [AbstractList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractList.java) | [ArrayList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/ArrayList.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ArrayList.png" width=100% />

***
> 类属性

使用 Object 数组来存储 ArrayList 的元素，ArrayList 的容量是这个数组的长度。

transient: 为了安全起见不希望在网络操作（主要涉及序列化操作）中被传输，这些信息对应的变量就可以加上 transient 关键字。换句话说，这个字段的生命周期仅存于调用者内存中而不会写到磁盘里持久化。

```java
transient Object[] elementData;
```