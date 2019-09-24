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

最核心的插入和提取方法是 addFirst，addLast，pollFirst，pollLast，其他方法根据这些来定义。

```java
    /**
     * 在队列前插入指定元素。
     */
    public void addFirst(E e) {
        if (e == null)
            throw new NullPointerException();
        // 注意：
        // 将 head 减 1，如果 head 为 0 ，运算之后指向数组末尾，防止数组
        // 到头了边界溢出，如果到头了就从末尾再往前。
        // 由于数组长度为 2 的幂，减 1 之后，之前为 1 的位置之前的位置为 0，
        // 之后的位置全为 1，所以和 head - 1 进行与运算后不 改变 head 的值。
        // 如果 head 等于 0，减 1 之后为 -1，二进制表示每一位均为 1 ，进行
        // 与运算之后 head 指向数组末尾。
        elements[head = (head - 1) & (elements.length - 1)] = e;
        if (head == tail)
            doubleCapacity();
    }

    /**
     * 把指定元素添加到队列末尾。
     */
    public void addLast(E e) {
        if (e == null)
            throw new NullPointerException();
        // tail指向第一个没有元素的位置
        elements[tail] = e;
        // tail + 1 一旦大于 elements.length - 1，tail 马上变成 0
        if ( (tail = (tail + 1) & (elements.length - 1)) == head)
            doubleCapacity();
    }
    
    // 删除第一个元素。（将该元素设置为 null）
    // 元素为空返回 null。
    public E pollFirst() {
        int h = head;
        @SuppressWarnings("unchecked")
        E result = (E) elements[h];
        // Element is null if deque empty
        if (result == null)
            return null;
        elements[h] = null;     // Must null out slot
        head = (h + 1) & (elements.length - 1);
        return result;
    }

    // 删除最后一个元素。（将该元素设置为 null）
    // 元素为空返回 null。
    public E pollLast() {
        int t = (tail - 1) & (elements.length - 1);
        @SuppressWarnings("unchecked")
        E result = (E) elements[t];
        if (result == null)
            return null;
        elements[t] = null;
        tail = t;
        return result;
    }
```

双端队列中与插入删除有关的方法主要有：

| 方法 | 作用 |
| - | - |
| void addFirst | 在队列前插入指定元素 |
| void addLast |把指定元素添加到队列末尾 |
| boolean offerFirst | 指定元素插入到队列开头 |
| boolean offerLast | 指定元素添加到队列末尾 |
| E removeFirst | 删除第一个元素并返回该元素 |
| E removeLast | 删除最后一个元素并返回该元素 |
| E pollFirst | 删除第一个元素 |
| E pollLast | 删除最后一个元素 |
| E getFirst | 返回队列的第一个元素 |
| E getLast | 返回队列的最后一个元素 |
| E peekFirst | 返回队列的第一个元素 |
| E peekLast | 返回队列的最后一个元素 |

**delete 方法删除指定位置元素**

此方法中使用 front 记录指定位置之前的元素个数，back 记录指定位置之后的元素个数，比较这两个值确定移动前面的元素还是移动后面的元素效率较高。由于底层数据结构使用循环数组，分别讨论指定位置位于 head 之前或者之后，指定位置位于 tail 之前或者之后的不同情况，并使用 System.arraycopy函数执行复制操作。

```java
    /**
     * 删除指定位置的元素，根据需要调整 head 和 tail。这可能导致数组中
     * 的元素向后或向前移动。
     *
     * 这个方法被称为 delete 而不是 remove，是为了强调它的语义和
     * remove 不同。
     *
     * @return true if elements moved backwards
     */
    private boolean delete(int i) {
        checkInvariants();
        final Object[] elements = this.elements;
        final int mask = elements.length - 1;
        final int h = head;
        final int t = tail;

        // 索引 i 前面的元素个数
        final int front = (i - h) & mask;
        // 索引 i 后面的元素个数
        final int back  = (t - i) & mask;

        // (t - h) & mask 表示数组中已经插入的元素个数，如果此表达式成立则
        // 抛出 ConcurrentModificationException 异常
        // Invariant: head <= i < tail mod circularity
        if (front >= ((t - h) & mask))
            throw new ConcurrentModificationException();

        // Optimize for least element motion
        // 判断索引 i 位于队列的前半部分还是后半部分。从而决定移动的方向，
        // 保证需要移动的元素个数最少
        // 若 front 小于 back，将目标元素之前的元素往后移动
        if (front < back) {
            if (h <= i) {
                System.arraycopy(elements, h, elements, h + 1, front);
            } else { // Wrap around
                System.arraycopy(elements, 0, elements, 1, i);
                elements[0] = elements[mask];
                System.arraycopy(elements, h, elements, h + 1, mask - h);
            }
            elements[h] = null;
            head = (h + 1) & mask;
            return false;
        } else { // 若 front 大于等于 back，将目标元素之后的元素向前移动
            if (i < t) { // Copy the null tail as well
                System.arraycopy(elements, i + 1, elements, i, back);
                tail = t - 1;
            } else { // Wrap around
                System.arraycopy(elements, i + 1, elements, i, mask - i);
                elements[mask] = elements[0];
                System.arraycopy(elements, 1, elements, 0, t);
                tail = (t - 1) & mask;
            }
            return true;
        }
    }
```

***
> ArrayDeque 作为队列比 LinkedList 要好，作为栈比 Stack 要好?










