# LinkedList

***
> 继承结构及完整源码解析

[Iterable](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Iterable.java) | [Collection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Collection.java) | [List](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/List.java) | [Queue](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Queue.java) | [Deque](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/Deque.java) | [AbstractCollection](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractCollection.java) | [AbstractList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractList.java) | [AbstractSequentialList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/AbstractSequentialList.java) | [LinkedList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/Collections/LinkedList.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/LinkedList.png" width=50% />

***
> 类属性

由于 LinkedList 链表由节点连接而成，所以类属性中，不需要数组作为支撑，也不需要“列表容量”等属性。设置 first 指向链表中第一个节点，设置 last 指向链表最后一个节点。

```java
    /**
     * 指向第一个节点的指针
     */
    transient Node<E> first;

    /**
     * 指向最后一个节点的指针
     */
    transient Node<E> last;
```

列表节点类的定义如下：

属性 item 用来存储元素的值，next 指向下一个节点，prev 指向上一个节点

```java
    // 节点类的定义
    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
```

***
> 成员函数

**支撑双向链表和双向队列基本操作的一系列私有方法**

LinkedList 同时实现了 List 接口和 Deque 接口，所以将两个接口的方法中都需要用到的操作分离出来，作为类的私有方法，具体如下所示：

| 私有方法 | 参数 | 作用 |
| - | - | - |
| linkFirst | 节点中元素的值 e | 在表头添加节点 |
| linkLast | 节点中元素的值 e | 在表尾添加节点 |
| linkBefore | 节点中元素的值 e，指定节点 succ | 在指定节点之前插入新节点 |
| unlinkFirst | 指定节点 f | 移除指定结点及其之前的所有节点，并返回指定节点中元素的值 |
| unlinkLast | 指定节点 f | 移除指定结点及其之后的所有节点，并返回指定节点的值 |
| unlink | 指定节点 f | 删除指定节点 |
| node | 指定索引 index | 返回指定索引处的节点引用 |

```java
    /**
     * 在表头添加元素。
     */
    private void linkFirst(E e) {
        final Node<E> f = first;
        // newNode 的 prev 属性指向 null，next 属性指向 f 即 first
        final Node<E> newNode = new Node<>(null, e, f);
        // 列表头结点 first 属性指向新的 newNode
        first = newNode;
        if (f == null)
            last = newNode;
        else
            f.prev = newNode;
        size++;
        modCount++;
    }

    /**
     * 在表尾添加元素
     */
    void linkLast(E e) {
        final Node<E> l = last;
        // newNode 的 prev 属性指向 l 即 last，next 属性指向 null
        final Node<E> newNode = new Node<>(l, e, null);
        // 列表尾结点 last 属性指向新的 newNode
        last = newNode;
        if (l == null)
            first = newNode;
        else
            l.next = newNode;
        size++;
        modCount++;
    }

    /**
     * 在指定的非空节点 succ 之前插入节点
     */
    void linkBefore(E e, Node<E> succ) {
        // assert succ != null;
        final Node<E> pred = succ.prev;
        // newNode 的 prev 属性指向 pred 即 succ 的前一个节点，next 属性
        // 指向 succ
        final Node<E> newNode = new Node<>(pred, e, succ);
        // succ 的 prev 属性指向待插入节点
        succ.prev = newNode;
        if (pred == null)
            first = newNode;
        else
            pred.next = newNode;
        size++;
        modCount++;
    }

    /**
     * 移除结点 f 及其之前的所有节点，并返回 f 的值
     */
    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        final E element = f.item;
        final Node<E> next = f.next;
        f.item = null;
        f.next = null; // help GC
        // first 指向 next， 即移除 f 及之前的节点
        first = next;
        if (next == null)
            last = null;
        else
            next.prev = null;
        size--;
        modCount++;
        return element;
    }

    /**
     * 移除结点 f 及其之后的所有节点，并返回 f 的值
     */
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        final E element = l.item;
        final Node<E> prev = l.prev;
        l.item = null;
        l.prev = null; // help GC
        // last 指向 prev， 即移除 f 及之后的节点
        last = prev;
        if (prev == null)
            first = null;
        else
            prev.next = null;
        size--;
        modCount++;
        return element;
    }

    /**
     * 删除节点 x
     */
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;
        final Node<E> next = x.next;
        final Node<E> prev = x.prev;

        // 判断 x 是否是头结点，否则 prev 指向 next
        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
            x.prev = null;
        }

        // 判断 x 是否是尾结点，否则 next 指向 prev
        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
            x.next = null;
        }

        x.item = null;
        size--;
        modCount++;
        return element;
    }
    
    /**
     * 返回指定索引处的节点
     */
    Node<E> node(int index) {
        // assert isElementIndex(index);

        // 注意：这里只能分成两种情况，不能用二分法。因为已知节点只有
        // first 和 last，即初始只能得到这两个节点。
        // 索引小于 size / 2，说明在前半部分，从 first 开始向后遍历
        if (index < (size >> 1)) {
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {
            // 否则在后半部分，从 last 开始向前遍历。
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }
```

**双向链表的位置访问操作**

包括返回列表中指定位置的元素的 get 方法、把列表中指定位置的元素值设置为特定的对象，并返回原来旧值的 set 方法，在列表中指定位置插入特定元素的 add 方法，移除列表中指定位置元素的 remove 方法。而每一个方法都调用了获取指定索引处节点的 node 方法：

```java
    /**
     * 返回指定索引处的节点
     */
    Node<E> node(int index) {
        // assert isElementIndex(index);

        // 注意：这里只能分成两种情况，不能用二分法。因为已知节点只有
        // first 和 last，即初始只能得到这两个节点。
        // 索引小于 size / 2，说明在前半部分，从 first 开始向后遍历
        if (index < (size >> 1)) {
            Node<E> x = first;
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else {
            // 否则在后半部分，从 last 开始向前遍历。
            Node<E> x = last;
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }
```

**双向链表的查询操作**

查询相关操作中，indexOf 从 first 节点开始往后遍历，直到找到列表中第一次出现的指定节点，并返回该节点的索引；lastIndexOf 正好相反，从 last 节点开始往前遍历，直到找到列表中最后一次出现（第一个找到）的指定节点，并返回该节点的索引。

**双向链表和双向队列相关操作及对应**

| 基本方法 | 双向队列方法 | 作用 |
| - | - | - |
| getFirst | peekFirst | 返回列表的第一个元素 |
| getLast | peekLast | 返回列表的最后一个元素 |
| removeFirst | pollFirst/pop | 移除并返回列表的第一个元素 |
| removeLast | pollLast | 移除并返回列表的最后一个元素 |
| addFirst | offerFirst/push | 在列表头添加指定元素 |
| addLast | offerLast | 在列表尾添加指定元素 |
| add |  | 在列表尾部添加元素（等同于 addLast） |
| remove(Object o) | removeFirstOccurrence | 删除列表中第一次出现的指定元素 |

**addAll 方法**
```java
    /**
     * 将参数集合中的所有元素插入到指定位置。将原来位置及其之后的
     * 元素后移（索引相应增加）。添加的顺序为参数集合迭代器返回的
     * 顺序。
     * 注意：链表的所有操作都要考虑到，当前节点是否为空，以及当前
     *           节点是否是头结点或者尾结点
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        checkPositionIndex(index);

        Object[] a = c.toArray();
        int numNew = a.length;
        if (numNew == 0)
            return false;

        // 1. 判断 index 是否是最后一个元素，这将会决定 succ 是否为空
        // pred 指向 index 索引的前一个元素，succ 指向 index 索引元素
        Node<E> pred, succ;
        if (index == size) {
            succ = null;
            pred = last;
        } else {
            succ = node(index);
            pred = succ.prev;
        }

        // 2. 元素依次插入到列表中，插入第一个元素时判断 pred 是否为空，
        // 即 index 是否是第一个元素，如果是第一个元素，需要设置 first
        // 指向当前第一个元素。
        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E) o;
            // 待插入的新元素为 newNode，它的 prev 指向 pred， next
            // 指向 null
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)
                first = newNode;
            else
                pred.next = newNode;
            // pred 变成新插入的节点，即按顺序往后插入
            pred = newNode;
        }

        // 插入完成后，判断 succ 是否为空，如果为空需要设置 last 指向 pred
        if (succ == null) {
            last = pred;
        } else {
            pred.next = succ;
            succ.prev = pred;
        }

        size += numNew;
        modCount++;
        return true;
    }
```

***
> LinkedList 小结

1. LinkedList 是基于双向链表实现的，除了实现了基本的链表操作外，还实现了栈、队列和双向队列的所有方法。
2. 不存在容量不足等问题。
3. 位置访问操作实现思路为从前往后遍历，或者从后往前遍历，直到找到指定节点为止。
4. LinkedList 是基于节点实现的，插入删除效率比较高，查找效率低。

***
> ArrayList 和 LinkedList 比较

**底层数据结构**

ArrayList 的底层数据结构是数组，对列表的任何操作实际上都是对数组的操作，元素存储在数组的每一个槽中；LinkedList 的底层数据类型是自定义的节点类（Node），元素的值保存在节点类的 item 中，而节点类中的 next 和 prev 属性（指向其他节点的“指针”）将所有节点联系在一起。

**扩容**

ArrayList 扩容时重新分配一个更长的数组，将原数组中所有元素复制过来，并回收原数组所占用的内存空间；LinkedList 的每一次插入删除操作都会为新的节点分配内存空间或者收回旧的节点所占用内存空间，不需要专门扩容。

**位置访问操作**

ArrayList 的底层数据结构为数组，所有的位置访问操作都是数组的随机访问操作，时间复杂度为 O(1)；
LinkedList 类中保存了第一个节点和最后一个节点的引用，而节点之间通过“指针”产生联系，且内存空间不一定连续，位置访问操作必须从第一个节点或者最后一个节点开始遍历，直到找到指定节点为止，时间复杂度为 O(n)。

**插入删除操作**

ArrayList 如果在容量足够的时候，将插入位置及之后的元素向右移动一位，然后执行插入操作，在容量不够的时候，先执行扩容操作，再插入。删除时将删除位置之后的所有元素向左移动一位。
LinkedList 插入时只需要创建新的节点并修改前后指针的指向即可。删除时只需要修改前后指针的指向即可，并将要删除的节点及节点中的元素和指针指向 null，虚拟机会自动回收内存空间。

**空间花费**

ArrayList 的空间浪费主要体现在在 list 列表的结尾预留一定的容量空间，而 LinkedList 的空间花费则体现在它的每一个节点都需要消耗相当的空间（存储“指针”）。

**其他**

对于随机访问 get 和 set， ArrayList 优于 LinkedList， 因为 ArrayList 中的数组支持随机访问。

对于新增和删除操作 add 和 remove， LinedList 比较占优势， 因为 ArrayList 要移动数组中的大量数据。

> ArrayList 和 LinkedList 时间消耗

对 ArrayList 和 LinkedList 分别进行 n 次添加，删除，查询操作，实验结果如下所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/LinkedListAnalysis.png" width=50% />

实验结果与上述分析完全吻合。LinkedList 在查询时的性能，远远不如 ArrayList。而对于插入而言，如果是在指定节点处插入，那么 LinkedList 性能较好，如果是在指定索引处插入，LinkedList 首先要遍历链表找到指定索引处的节点，所以这种情况下的插入性能并不一定优于 ArrayList。值得注意的是，在 n 较大时，LinkedList 重复执行在列表尾部添加元素这一操作时，时间消耗超过了 ArrayList，可能是因为 LinkedList 频繁的 new 操作，在一定程度上影响了其添加新节点的性能。