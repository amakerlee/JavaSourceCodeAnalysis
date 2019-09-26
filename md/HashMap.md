### HashMap

***
> 继承结构及完整源码解析

[Map](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Map.java) | [AbstractMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractMap.java) | [HashMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/HashMap.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/HashMap.png" width=50% />

 ***
 > 类属性




***
> 成员函数

**扩容操作**



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

当重新调整HashMap大小的时候，确实存在条件竞争，因为如果两个线程都发现HashMap需要重新调整大小了，它们会同时试着调整大小。在调整大小的过程中，存储在链表中的元素的次序会反过来，因为移动到新的bucket位置的时候，HashMap并不会将元素放在链表的尾部，而是放在头部，这是为了避免尾部遍历(tail traversing)，原数组[j]位置上的桶移到了新数组[j+原数组长度]。如果条件竞争发生了，那么就死循环了。


***
> 参考：

[30张图带你彻底理解红黑树](https://www.jianshu.com/p/e136ec79235c)