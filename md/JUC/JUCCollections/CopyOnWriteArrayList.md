## CopyOnWriteArrayList

### 完整源码解析

[CopyOnWriteArrayList](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/JUCCollections/CopyOnWriteArrayList.java)

### 类属性

CopyOnWriteArrayList 类是 ArrayList 类的并发版本，类中仅有两个属性，一个是用于更改操作避免并发修改的 ReentrantLock 实例，一个是存储列表元素的对象数组。

```java
    /** 保护所有数据更改操作的锁 */
    final transient ReentrantLock lock = new ReentrantLock();

    /** 数组。只能通过 getArray/setArray 访问此数组（不是数组中的元素）。*/
    private transient volatile Object[] array;
```

### 成员函数

此类中的方法和 ArrayList 中的方法基本一致，包括 add，remove，set 等。

此类不需要扩容，因为每一步的修改操作都创造了一个新的数组。首先从原数组里复制一份新的数组，在新数组里执行写入操作。执行完成之后，将属性 array 指向新的数组，旧数组由垃圾回收器自行回收。

创建（复制）数组的操作有两种，一种是 Arrays.copyOf，调用后立刻无条件创建新的数组；一种是 System.arraycopy，将源数组复制到目标数组里。

**indexOf**

indexOf 操作相当于读操作，没有对内存写入，所以没有加锁。和 ArrayList 一样，查找第一次出现的索引从前往后扫描，查找最后一次出现的索引从后往前扫描。

```java
    /**
     * indexOf 的静态版本，允许重复调用而无需每次重新获取数组。查找范围为
     * index（包含）到 fence（不包含）。
     * 注意：没有加锁
     * @param o element to search for
     * @param elements the array
     * @param index first index to search
     * @param fence one past last index to search
     * @return index of element, or -1 if absent
     */
    private static int indexOf(Object o, Object[] elements,
                               int index, int fence) {
        // 允许 null 元素
        if (o == null) {
            for (int i = index; i < fence; i++)
                if (elements[i] == null)
                    return i;
        } else {
            for (int i = index; i < fence; i++)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }

    /**
     * lastIndexOf 的静态版本。从索引为 index 位置开始查找。
     * 没有加锁。
     * @param o element to search for
     * @param elements the array
     * @param index first index to search
     * @return index of element, or -1 if absent
     */
    private static int lastIndexOf(Object o, Object[] elements, int index) {
        if (o == null) {
            for (int i = index; i >= 0; i--)
                if (elements[i] == null)
                    return i;
        } else {
            for (int i = index; i >= 0; i--)
                if (o.equals(elements[i]))
                    return i;
        }
        return -1;
    }
```

**add**

add(E e) 方法用于添加一个元素到列表末尾。首先创建一个新的数组空间，将旧数组的元素复制进去，然后将指定元素添加到末尾，最后设置 array 指向新的数组。

add(int index, E element) 方法用于在指定索引处插入一个元素，指定索引处及之后的所有元素往后移动一位。首先创建能容纳所有元素的新数组，然后分两步将 index 之前、index 及之后的元素复制到其在新数组中的位置，最后插入新元素、设置新数组为 array。

```java
    /**
     * 将指定元素添加到列表末尾。
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     */
    public boolean add(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            // 同样创建新数组，并将元素添加到新数组尾部，然后将新数组设置成
            // 支撑数组
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 在列表中指定位置插入指定元素（插入后元素在 index 位置）。将 index
     * 位置及之后的元素往右移一位（索引加 1）。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                        ", Size: "+len);
            Object[] newElements;
            // 需要移动的元素
            int numMoved = len - index;
            // 如果不用移动任何元素，创造一个比当前数组容量大 1 的数组。
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + 1);
            else {
                // 创建新数组
                newElements = new Object[len + 1];
                // 将 elements 里的元素复制到 newElements 里面
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index, newElements, index + 1,
                        numMoved);
            }
            newElements[index] = element;
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }
```

**addIfAbsent**

如果指定元素不存在，则将其加入到列表末尾。

```java
    /**
     * A version of addIfAbsent using the strong hint that given
     * recent snapshot does not contain e.
     * 和 remove(Object o, Object[] snapshot, int index) 类似的操作。
     */
    private boolean addIfAbsent(E e, Object[] snapshot) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] current = getArray();
            int len = current.length;
            if (snapshot != current) {
                // Optimize for lost race to another addXXX operation
                // 首先在快照的长度和最新数组的长度值较小的范围内查找
                int common = Math.min(snapshot.length, len);
                for (int i = 0; i < common; i++)
                    // 如果已经存在该元素，返回 false.
                    // 如果 current[i] == snapshot[i]，说明该位置没有被其他线程修改过，
                    // 同时该位置是不可能存在的，因为之前已经检查过了
                    if (current[i] != snapshot[i] && eq(e, current[i]))
                        return false;
                    // 如果在没查找的范围内找到了，也返回 false
                if (indexOf(e, current, common, len) >= 0)
                    return false;
            }
            // 复制
            Object[] newElements = Arrays.copyOf(current, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
```

**addAllAbsent**

将指定集合中所有在列表中不存在的元素加入到列表中，相当于求两个集合的并集。首先找到应该添加到列表中的元素，保存在临时数组里，调用 Arrays.copyOf 创建新的数组，调用 System.arraycopy 将元素复制到新数组里。最后设置 array 指向新数组。

```java
    /**
     * 将指定集合中所有不存在于列表中的元素加入到列表中（求并集），按
     * 指定集合迭代器返回的顺序加入。
     *
     * 如果加入列表的元素有重复，只加入一次。
     *
     * @param c collection containing elements to be added to this list
     * @return the number of elements added
     * @throws NullPointerException if the specified collection is null
     * @see #addIfAbsent(Object)
     */
    public int addAllAbsent(Collection<? extends E> c) {
        Object[] cs = c.toArray();
        if (cs.length == 0)
            return 0;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            int added = 0;
            // uniquify and compact elements in cs
            // 从集合中找出不存在于列表中的元素（还需要判断它和已处理过的
            // 元素不相等，为了删除重复的元素）
            // 将找出的元素保存在cs 数组前面
            for (int i = 0; i < cs.length; ++i) {
                Object e = cs[i];
                if (indexOf(e, elements, 0, len) < 0 &&
                        indexOf(e, cs, 0, added) < 0)
                    cs[added++] = e;
            }
            // 复制
            if (added > 0) {
                Object[] newElements = Arrays.copyOf(elements, len + added);
                System.arraycopy(cs, 0, newElements, len, added);
                setArray(newElements);
            }
            return added;
        } finally {
            lock.unlock();
        }
    }
```

**addAll**

在此列表中指定位置插入指定集合中所有元素。将当前位置和其之后的元素向右移动（索引增加）。

```java
    /**
     * 在此列表中指定位置插入指定集合中所有元素。将当前位置和其之后的元素
     * 向右移动（索引增加）。插入的顺序和集合迭代器返回的顺序一致。
     *
     * @param index index at which to insert the first element
     *        from the specified collection
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     * @see #add(int,Object)
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] cs = c.toArray();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            // 判断边界
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                        ", Size: "+len);
            if (cs.length == 0)
                return false;
            // 需要移动的元素个数
            int numMoved = len - index;
            Object[] newElements;
            if (numMoved == 0)
                // 创建可以容纳所有元素的数组，并将列表元素复制到该数组里
                newElements = Arrays.copyOf(elements, len + cs.length);
            else {
                // 创建可以容纳所有元素的数组，将分别将 index 之前，index 及
                // 之后的元素复制到对应的位子（中间空出 cs 的位置）
                newElements = new Object[len + cs.length];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index,
                        newElements, index + cs.length,
                        numMoved);
            }
            // 将指定集合元素复制到 index 及之后
            System.arraycopy(cs, 0, newElements, index, cs.length);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
```

**set**

将列表中指定位置的元素替换成指定元素。由于是写操作，执行操作前需上锁。

```java
    /**
     * 将列表中指定位置元素替换成指定的值。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        final ReentrantLock lock = this.lock;
        // 替换之前上锁
        lock.lock();
        try {
            Object[] elements = getArray();
            E oldValue = get(elements, index);

            if (oldValue != element) {
                int len = elements.length;
                // Arrays.copyOf 返回新数组，并指定新数组长度
                Object[] newElements = Arrays.copyOf(elements, len);
                newElements[index] = element;
                // 将新数组设定为存储元素的数组
                setArray(newElements);
            } else {
                // Not quite a no-op; ensures volatile write semantics
                setArray(elements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }
```

**remove**

remove(int index) 删除列表中指定位置的元素。将之后的元素往前移动一格。并返回删除的元素。

remove(Object o) 删除指定的元素。此时先调用 indexOf 找到其所在的位置，此时没有加锁，所以可能有其他线程已经对数组进行了修改。下一步执行修改操作。先加锁，比较旧数组（快照）和新数组，找到应该修改的位置，然后创造新数组，在新数组里进行修改。

```java
    /**
     * 删除列表中指定位置的元素。
     * 将之后的元素往前移动一格。并返回删除的元素。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            E oldValue = get(elements, index);
            int numMoved = len - index - 1;
            // 删除的元素是最后一个元素，截断即可
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, len - 1));
            else {
                // 否则将其后的元素向前移动一位
                Object[] newElements = new Object[len - 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index,
                        numMoved);
                setArray(newElements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从列表中删除第一次出现的指定元素，如果其存在的话。如果不包含该元素，
     * 不做出任何改变。
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     */
    public boolean remove(Object o) {
        Object[] snapshot = getArray();
        // 要删除元素的索引
        int index = indexOf(o, snapshot, 0, snapshot.length);
        return (index < 0) ? false : remove(o, snapshot, index);
    }

    /**
     * A version of remove(Object) using the strong hint that given
     * recent snapshot contains o at the given index.
     */
    private boolean remove(Object o, Object[] snapshot, int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] current = getArray();
            int len = current.length;
            // 如果快照已经不是当前的数组了
            if (snapshot != current) findIndex: {
                int prefix = Math.min(index, len);
                // 遍历找到该元素现在的位置
                for (int i = 0; i < prefix; i++) {
                    // 如果 current[i] == snapshot[i]，数组当前位置没有被改变
                    // 如果不相等，而且 o 等于 curr[i]，说明已经找到现在所在位置
                    if (current[i] != snapshot[i] && eq(o, current[i])) {
                        index = i;
                        break findIndex;
                    }
                }
                // 运行到这一步的 index 大于等于 len，说明数组缩短了
                // 如果在 len 范围内没找到，说明元素在列表中已经不存在了
                if (index >= len)
                    return false;
                // index < len
                // 当前 index 的元素就是要找的元素则跳出循环，否则从 index 开始，
                // 查找 index - len 范围内是否存在
                if (current[index] == o)
                    break findIndex;
                index = indexOf(o, current, index, len);
                if (index < 0)
                    return false;
            }
            // 找到其索引，执行复制操作
            Object[] newElements = new Object[len - 1];
            System.arraycopy(current, 0, newElements, 0, index);
            System.arraycopy(current, index + 1,
                    newElements, index,
                    len - index - 1);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除索引范围为 fromIndex（包含）到 toIndex（不包含）的元素。
     * 将右边的元素向左移动。如果 fromIndex 等于 toIndex，列表不会做出任何改变。
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     * @throws IndexOutOfBoundsException if fromIndex or toIndex out of range
     *         ({@code fromIndex < 0 || toIndex > size() || toIndex < fromIndex})
     */
    void removeRange(int fromIndex, int toIndex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;

            // 判断边界是否符合要求
            if (fromIndex < 0 || toIndex > len || toIndex < fromIndex)
                throw new IndexOutOfBoundsException();
            // 新的长度
            int newlen = len - (toIndex - fromIndex);
            int numMoved = len - toIndex;
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, newlen));
            else {
                Object[] newElements = new Object[newlen];
                System.arraycopy(elements, 0, newElements, 0, fromIndex);
                System.arraycopy(elements, toIndex, newElements,
                        fromIndex, numMoved);
                setArray(newElements);
            }
        } finally {
            lock.unlock();
        }
    }
```

### 总结

毋庸置疑，CopyOnWriteArrayList 是线程安全的。它使用了一种叫写时复制（COW）的方法，任何写操作都会创造一个新的数组，用于替换原数组。写操作需要加锁，避免多个线程同时执行写操作。读操作不需要加锁，所以读到的数据并不一定完全是最新的数据。

线程并发读数组有以下几种情况： 

1、如果写操作未完成，那么直接读取原数组的数据； 

2、如果写操作完成，但是引用还未指向新数组，那么也是读取原数组数据； 

3、如果写操作完成，并且引用已经指向了新的数组，那么直接从新数组中读取数据。

CopyOnWriteArrayList 主要用到读写分离和开辟新的空间这两套方案来解决并发冲突。

由于所有的写操作都需要创造新的数组，并且包含大量的数组元素复制操作，所以 CopyOnWriteArrayList 适合读多写少的操作。在数据量大时，尽量避免使用 CopyOnWriteArrayList 作为容器。

### 参考

* [CopyOnWriteArrayList是线程安全的](https://my.oschina.net/u/3847203/blog/2988423)

