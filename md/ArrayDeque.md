### ArrayDeque

***
> 继承结构及完整源码解析

[Iterable](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Iterable.java) | [Collection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Collection.java) | [Queue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Queue.java) | [Deque](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Deque.java) | [AbstractCollection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractCollection.java) | [ArrayDeque](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/ArrayDeque.java)
 
<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ArrayDeque.png" width=50% />
 
 ***
 > 类属性
 
 此类是双端队列的实现类，底层数据结构是对象数组，数组的长度总是 2 的幂，只有当数组变成满的时候，会立刻调整大小。另外两个重要的类属性是 head 和 tail，分别表示队列头部索引和队列尾部索引。
 
 ```java
     /**
      * 队列的元素都存储在这个数组里。队列的容量就是这个数组的长度，
      * 其长度总是 2 的幂。数组永远不允许变成满的，除非是在 addX 除非
      * 是在 addX 方法中。当数组变成满的时候，它会立刻调整大小 （参阅
      * doubleCapacity），这样就避免了头和尾互相缠绕，使其相等。我们还
      * 保证所有不包含元素的数组单元格始终为 null。
      */
     transient Object[] elements; // 非私有成员，以简化嵌套类的访问。
 
     /**
      * 队列头部元素的索引（该元素将被 remove 或者 pop 删除）；如果
      * 队列为空，将会是等于 tail 的数。
      */
     transient int head;
 
     /**
      *队列尾部索引，将下一个元素添加到该索引的下一个位置（通过 addLast(E)，
      * add(E)，或者 push(E)）。
      */
     transient int tail;
 
     /**
      * 一个新创建的队列的最小容量。必须是 2 的幂。
      */
     private static final int MIN_INITIAL_CAPACITY = 8;
```

***
> 成员函数

**数组空间再分配方法**

calculateSize 函数用于计算比指定参数大且最为 2 的整数次方的最小的数
doubleCapacity 函数用于在队列满的时候将队列支撑数组扩展成原来的两倍，过程如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ArrayDequeDoubleCapacity.png" width=70% />

copyElements 函数用于将支撑数组中的元素按顺序复制到指定数组中。由于队列为循环队列，所以分为两种情况：head 在 tail 之前，和 head 在 tail 之后。

```java
    // 计算容量，大于 numElement 且为 2 的整数次方的最小的数
    // 比如，3 算出来是 8，9 算出来是 16，33 算出来是 64
    private static int calculateSize(int numElements) {
        int initialCapacity = MIN_INITIAL_CAPACITY;
        if (numElements >= initialCapacity) {
            // 假设初始容量为 1010010
            initialCapacity = numElements;
            initialCapacity |= (initialCapacity >>>  1);
            // 1111011
            initialCapacity |= (initialCapacity >>>  2);
            // 1111111
            initialCapacity |= (initialCapacity >>>  4);
            // 1111111
            initialCapacity |= (initialCapacity >>>  8);
            // 1111111
            initialCapacity |= (initialCapacity >>> 16);
            // 1111111
            initialCapacity++;
            // 10000000

            if (initialCapacity < 0)   // Too many elements, must back off
                initialCapacity >>>= 1;// Good luck allocating 2 ^ 30 elements
        }
        return initialCapacity;
    }
    
   /**
     * 将队列容量设置为当前的两倍，当队列满时调用，即 head 和 tail
     * 相遇的时候。
     */
    private void doubleCapacity() {
        // assert 如果表达式为 true 则继续执行，如果为 false 抛出
        // AssertionError，并终止执行
        assert head == tail;
        int p = head;
        int n = elements.length;
        // 数组长度减去 head 位置的索引，表示 head 位置右边的元素个数。
        int r = n - p;
        // 新的容量，等于原来容量的两倍
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");
        // 创建新数组
        Object[] a = new Object[newCapacity];
        // 把索引 p 之后的元素复制到新数组从索引 0 开始的位置
        System.arraycopy(elements, p, a, 0, r);
        // 把索引 0 到 p 的元素复制到新数组从索引 r 开始的位置，复制完成
        // 之后的顺序是正确的先后顺序
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
    }
    
    /**
     * 按顺序（从队列的第一个元素到最后一个元素） 将元素数组中的元素
     * 复制到指定的数组中。假设数组足够大，可以容纳队列中所有元素。
     *
     * @return its argument
     */
    private <T> T[] copyElements(T[] a) {
        // head 在 tail 之前，一次复制，否则分两次复制（同 doubleCapacity）
        if (head < tail) {
            System.arraycopy(elements, head, a, 0, size());
        } else if (head > tail) {
            int headPortionLen = elements.length - head;
            System.arraycopy(elements, head, a, 0, headPortionLen);
            System.arraycopy(elements, 0, a, headPortionLen, tail);
        }
        return a;
    }
```

**插入与删除的核心方法**

```java

```










