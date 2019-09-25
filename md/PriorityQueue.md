### PriorityQueue

***
> 继承结构及完整源码解析

[Iterable](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Iterable.java) | [Collection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Collection.java) | [Queue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Queue.java) | [AbstractCollection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractCollection.java)  | [AbstractQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractQueue.java) | [PriorityQueue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/PriorityQueue.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/PriorityQueue.png" width=50% />

 ***
 > 类属性
 
 PriorityQueue 类使用堆作为组织元素的数据结构，使用数组作为堆的底层存储结构。另外一个重要的类属性为比较器，如果指定了比较器，则使用指定的比较器排序，如果没有指定，默认使用升序排列（最小堆）。
 
 *堆：一种完全二叉树数据结构，分为最大堆和最小堆。在最大堆中，父节点的值比每一个子节点的值都要大。在最小堆中，父节点的值比每一个子节点的值都要小。*
 
 ```java
    // 默认初始容量
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * 优先级队列表现为一个平衡的二进制堆：queue[n] 的两个子队列分别为
     * queue[2*n+1] 和 queue[2*(n+1)]。如果队列的顺序由比较器决定，或者按
     * 元素的自然顺序排序。对于堆中的每个节点 n 和 n 的每个后代 d，有 n <= d。
     * 假定队列非空，堆中最小值的元素为 queue[0]。
     */
    transient Object[] queue; // non-private to simplify nested class access

    /**
     * 优先级队列中的元素个数。
     */
    private int size = 0;

    /**
     * 比较器。如果使用元素的自然顺序排序的话此比较器为 null。
     */
    private final Comparator<? super E> comparator;
```

***
> 成员函数

**扩容操作**

扩容操作和其他以数组为底层数据结构的线形数据结构基本一致，扩容的策略为：如果原容量小于 64，新容量变成 2 * oldCapacity + 2，否则，新容量变成 1.5 * oldCapacity，如果新容量比规定的最大容量还要大，那么将新容量设置为整型最大值。

```java
    /**
     * 增加数组容量
     */
    private void grow(int minCapacity) {
        int oldCapacity = queue.length;
        // 如果原容量小于 64，新容量变成 2 * oldCapacity + 2，否则，新容量
        // 变成 1.5 * oldCapacity
        int newCapacity = oldCapacity + ((oldCapacity < 64) ?
                (oldCapacity + 2) :
                (oldCapacity >> 1));
        // 如果新容量比规定的最大容量还要大，那么将新容量设置为整型最大值
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        queue = Arrays.copyOf(queue, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        // 指定容量大于规定的最大容量，将新容量设置为整型最大值，否则设置
        // 为规定的最大容量。
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }
```

**offer 和 poll**

offer 方法将指定元素添加到队列中，将指定元素添加到数组尾部，然后调用 siftUp 元素调整堆结构使其满足堆的性质。

poll 方法将堆顶元素从堆中弹出，然后调用 siftDown 元素向下调整堆结构使其满足堆的性质。

```java
    /**
     * 将指定元素插入到优先级队列中
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        modCount++;
        int i = size;
        // 扩容，确保队列容量不溢出
        if (i >= queue.length)
            grow(i + 1);
        size = i + 1;
        if (i == 0)
            queue[0] = e;
        else
            // 执行添加操作
            siftUp(i, e);
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0)
            return null;
        // s 指向最后一个元素，同时 size 减一
        int s = --size;
        modCount++;
        E result = (E) queue[0];
        E x = (E) queue[s];
        // 删除数组尾部元素
        queue[s] = null;
        // 在数组索引为 0 的位置插入 x（删除堆顶元素），然后向下调整堆结构
        if (s != 0)
            siftDown(0, x);
        // 返回堆顶元素
        return result;
    }
```

**removeAt**

从队列中删除索引为 i 的元素，然后向上或向下调整。

```java
    /**
     * 从队列中删除索引为 i 的元素。
     */
    @SuppressWarnings("unchecked")
    private E removeAt(int i) {
        // assert i >= 0 && i < size;
        modCount++;
        // s 为数组中最后一个元素的索引，同时 size 减一
        int s = --size;
        // 如果删除的是最后一个元素，直接将最后一个元素置为 null，并结束
        // 此方法
        if (s == i)
            queue[i] = null;
        else {
            E moved = (E) queue[s];
            queue[s] = null;
            // 将位置 i 的元素替换成 moved，即原来的最后一个元素，然后进行
            // 向下调整操作
            siftDown(i, moved);
            // 如果没有向下调整，说明可能它的位置在上面，接着向上调整
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }
```

**siftUp**

siftUp 和 siftDown 是堆中的核心方法。siftUp 方法用于在指定位置插入指定元素，然后向上提升 x （和其父节点进行比较）直到满足堆成立的条件。siftUp 分成有比较器和没有比较器两种情况讨论。

```java
    /**
     * 在 k 位置插入元素 x（从 k 位置开始调整堆，找到元素 x 的位置，忽略
     * 原先 k 位置的元素），通过向上提升 x 直到它大于等于它的父元素或者
     * 根元素，来保证满足堆成立的条件。
     *
     * 为了简化和加速强制转换和比较，Comparable（元素的默认比较器）
     * 和 Comparator（指定的比较器）被分成不同的方法，这两个方法基本
     * 等同。（siftDown 同理）
     *
     * 注意此方法为 private，仅限类内部调用
     */
    private void siftUp(int k, E x) {
        if (comparator != null)
            siftUpUsingComparator(k, x);
        else
            siftUpComparable(k, x);
    }

    // 对于默认比较器，插入元素时调整堆的方法
    @SuppressWarnings("unchecked")
    private void siftUpComparable(int k, E x) {
        // <? extends E>，集合中元素类型上限为 E，即只能是 E 或者 E 的子类
        // <? super E>，集合中元素类型下限为 E，即只能是 E 或者 E 的父类
        Comparable<? super E> key = (Comparable<? super E>) x;
        while (k > 0) {
            // 获取 k 的父节点
            int parent = (k - 1) >>> 1;
            // 获取父节点的元素
            Object e = queue[parent];
            // 如果 key 的值大于等于其父节点的值 e，那么不需要调整，结束操作
            // 最后建成小顶堆
            if (key.compareTo((E) e) >= 0)
                break;
            // 否则将父节点的值复制到 k 位置，然后 k 指向父节点的位置，继续比较
            queue[k] = e;
            k = parent;
        }
        // 找到指定元素的位置 k，将指定元素存在 k 位置
        queue[k] = key;
    }

    // 对于指定的比较器，插入元素时调整堆的方法
    @SuppressWarnings("unchecked")
    private void siftUpUsingComparator(int k, E x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = queue[parent];
            // 如果 x 大于其父节点的值 e，那么不用调整，跳出循环
            if (comparator.compare(x, (E) e) >= 0)
                break;
            queue[k] = e;
            k = parent;
        }
        queue[k] = x;
    }
```

**siftDown**

siftDown 方法用于在指定位置插入指定元素，然后向下调整 x （和其子节点进行比较）直到满足堆成立的条件。siftDown 分成有比较器和没有比较器两种情况讨论。

```java
    /**
     * 在 k 位置插入元素 x，通过向下调整 x 直到它小于等于它的子节点或者
     * 叶节点，来保证满足堆成立的条件。
     */
    private void siftDown(int k, E x) {
        if (comparator != null)
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }

    // 对于默认的比较器，插入元素时下沉元素的方法
    @SuppressWarnings("unchecked")
    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>)x;
        // half 为队列中间一半的位置，
        int half = size >>> 1;
        // k 小于中间位置索引值时循环，因为堆的叶节点索引大于等于中间
        // 位置索引
        while (k < half) {
            // 获取 k 的左子节点的索引
            int child = (k << 1) + 1;
            // 获取左子节点的值 c
            Object c = queue[child];
            int right = child + 1;
            // 如果存在右子节点，找出两个子节点的值较小的那一个
            if (right < size &&
                    ((Comparable<? super E>) c).compareTo((E) queue[right]) > 0)
                c = queue[child = right];
            // 如果 key 的值小于等于较小子节点的值， 结束循环
            if (key.compareTo((E) c) <= 0)
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = key;
    }

    @SuppressWarnings("unchecked")
    private void siftDownUsingComparator(int k, E x) {
        int half = size >>> 1;
        while (k < half) {
            // 左子节点设置为 child
            int child = (k << 1) + 1;
            Object c = queue[child];
            int right = child + 1;
            // 比较左子节点和右子节点
            if (right < size &&
                    comparator.compare((E) c, (E) queue[right]) > 0)
                c = queue[child = right];
            if (comparator.compare(x, (E) c) <= 0)
                break;
            queue[k] = c;
            k = child;
        }
        queue[k] = x;
    }
```

**heapify**

初始化时建堆操作

```java
    /**
     * 从最后一个元素的父节点位置开始建堆
     */
    @SuppressWarnings("unchecked")
    private void heapify() {
        for (int i = (size >>> 1) - 1; i >= 0; i--)
            siftDown(i, (E) queue[i]);
    }
```

> PriorityQueue 小结

1. 扩容机制和线性数据结构的机制基本相似。
2. 队列里不允许 null 元素。
3. 底层数据结构是以数组为基础的堆，插入删除操作依赖堆的调整函数 siftUp 和 siftDown，对于不同的比较器，堆的调整过程也不同。
