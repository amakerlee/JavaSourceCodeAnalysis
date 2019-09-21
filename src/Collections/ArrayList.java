package Collections;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import sun.misc.SharedSecrets;

/**
 * List接口的大小可变数组的实现.  实现了所有可选列表操作，允许所有元素类型，
 * 包括null。除了实现List接口外，这个类还提供了方法用来操作内部存储列表的
 * 数组的大小。 这个类大致等同于Vector，除了此类是不同步的。
 * size, isEmpty, get, set, biterator和listIterator操作在常数时间内完成，add
 * 操作在分摊的常数时间内完成，即添加n个元素需要O(n)时间。除此之外，
 * 其他操作在线性时间内完成。该类的常数因子比实现LinkedList的常数因子要低。
 *
 * 每一个ArrayList实例都有一个容量。这个容量是用来储存元素的数组大小。它
 * 至少等于列表的大小。当元素被添加到ArrayList之后，它的容量自动增长。
 * 没有固定的增长策略，因为这不仅仅只是添加元素会带来分摊固定时间开销那样
 * 简单。
 *
 * 在添加大量元素之前，应用程序可以使用ensureCapacity操作增加ArrayList
 * 实例的容量。这可以减少递增式再分配的量。
 *
 * 这一实现并不是同步的。如果多个线程同时访问ArrayList实例，且至少有一个
 * 线程从结构上修改了ArrayList，那么必须从外部同步。（从结构上修改指的是
 * 添加，删除一个或多个元素，或者显式地改变底层数组的大小，仅仅设置元素
 * 的值不算从结构上修改。）这一般通过对自然封装该列表的对象进行同步操作
 * 来完成。
 *
 * 如果没有这样的对象则应该使用Collections.synchronizedList方法将该列表
 * “包装”起来。这最好在创建时完成，以防止意外对列表进行不同步的访问：
 * List list = Collections.synchronizedList(new ArrayList(…));
 *
 * 此类的iterator和listIterator方法返回的迭代器是快速失败的：在创建迭代器
 * 之后，除非通过迭代器自身的remove或add方法从结构上对列表进行修改，否则
 * 在任何时间以任何方式对列表进行修改，迭代器都会抛出
 * ConcurrentModificationException。因此，面对并发的修改，迭代器很快就会
 * 完全失败，而不是冒着在将来某个不确定时间发生任意不确定行为的风险。
 *
 * 注意，迭代器的 fast-fail 行为无法得到保证，因为一般来说，不可能对是否出现
 * 不同步并发修改做出任何硬性保证。fast-fail 迭代器会尽最大努力抛出
 * ConcurrentModificationException。因此，为提高这类迭代器的正确性而编写
 * 一个依赖于此异常的程序是错误的做法：迭代器的 fast-fail 行为应该仅用于
 * 检测bug。
 *
 * 此类是Java Collections Framework的成员。
 *
 * @August ArrayList 是变长集合类，基于定长数组实现。非线性安全类。
 *                ArrayList 的核心是扩容
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see     Collection
 * @see     List
 * @see     LinkedList
 * @see     Vector
 * @since   1.2
 */

/**
 * RandomAccess接口：
 * 标记性接口，用来快速随机存取，实现了该接口之后，使用普通的for循环来
 * 遍历，性能更高，例如ArrayList。而没有实现该接口的话，使用Iterator来
 * 迭代，这样性能更高，例如linkedList。所以这个标记性只是为了让我们知道
 * 用什么样的方式去获取数据性能更好。
 * Cloneable接口：
 * 实现了该接口，就可以使用Object.Clone()方法了，列表能被克隆。
 * Serializable接口：
 * 实现该序列化接口，表明该类可以被序列化，能够从类变成节流传输，然后还能
 * 从字节流变成原来的类。
 */
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 8683452581122892189L;

    /**
     * 默认初始容量为 10。
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * 空实例共享此空数组。
     * @August 指定容量为 0 时使用
     */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /**
     * 默认容量大小的空实例共享此空数组。和 EMPTY_ELEMENTDATA 区分开是
     * 为了知道当第一个元素被添加时需要扩容多少。
     * @August 没有指定容量时使用
     */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    /**
     * 这一个数组用来存储 ArrayList 元素。ArrayList 的容量是这个数组的长度。
     * 添加第一个元素的时候任何满足
     * elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA
     * 的空 ArrayList 会将容量扩展到 DEFAULT_CAPACITY。
     *
     * @August transient: 为了安全起见不希望在网络操作（主要涉及序列化操作）
     * 中被传输，这些信息对应的变量就可以加上 transient 关键字。换句话说，这
     * 个字段的生命周期仅存于调用者内存中而不会写到磁盘里持久化。
     */
    // 设置成非私有变量是为了方便内部嵌套类访问
    transient Object[] elementData;

    /**
     * The size of the ArrayList (the number of elements it contains).
     * ArrayList 的大小（包含的元素数量）
     * @serial
     */
    private int size;

    /**
     * 使用指定的初始化容量构造一个空列表。
     *
     * @param  initialCapacity  the initial capacity of the list
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    // 有参数的构造函数
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            // 创建指定容量的数组
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            // 使用有指定初始化值的共享空实例
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                    initialCapacity);
        }
    }

    /**
     * Constructs an empty list with an initial capacity of ten.
     */
    // 没有参数的构造函数，插入元素时扩容
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    /**
     * 构造一个包含指定集合所有元素的列表，并按照集合迭代器返回元素的顺序
     * 构造。
     *
     * @param c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     */
    public ArrayList(Collection<? extends E> c) {
        elementData = c.toArray();
        if ((size = elementData.length) != 0) {
            // c.toArray 可能不会正确地返回 Object 数组，所以需要判断。
            if (elementData.getClass() != Object[].class)
                elementData = Arrays.copyOf(elementData, size, Object[].class);
        } else {
            // 如果elementDate为空，依然指向空实例。
            this.elementData = EMPTY_ELEMENTDATA;
        }
    }

    /**
     * 调整 ArrayList 实例的容量为列表的当前大小。程序可以用这个方法最小化
     * ArrayList 实例占用的空间。
     */
    public void trimToSize() {
        modCount++;
        if (size < elementData.length) {
            elementData = (size == 0)
                    ? EMPTY_ELEMENTDATA
                    // Arrays的copyOf()方法传回的数组是新的数组对象，改变传回
                    // 数组中的元素值，不会影响原来的数组。copyOf()的第二个
                    // 自变量指定要建立的新数组长度，如果新数组的长度超过原数组的
                    // 长度，则保留数组默认值
                    : Arrays.copyOf(elementData, size);
        }
    }

    /**
     * 如果有需要，增加 ArrayList 实例的容量，来确保它可以容纳至少指定
     * 最小容量的元素。
     *
     * @param   minCapacity   the desired minimum capacity
     */
    public void ensureCapacity(int minCapacity) {
        int minExpand = (elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA)
                // any size if not default element table
                ? 0
                // larger than default for default empty table. It's already
                // supposed to be at default size.
                : DEFAULT_CAPACITY;

        if (minCapacity > minExpand) {
            ensureExplicitCapacity(minCapacity);
        }
    }

    // 一系列和扩容相关的函数均为 private，不允许对象访问
    // 注意：size表示列表中元素个数，elementData.length 表示从来存储元素的
    // 数组的大小，size 和 elementData.length 并不一定相等。

    // 计算最小容量
    private static int calculateCapacity(Object[] elementData, int minCapacity) {
        // 如果初始化的时候没有指定初始容量
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        return minCapacity;
    }

    // 扩容的入口方法
    private void ensureCapacityInternal(int minCapacity) {
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }

    // 扩容的入口方法
    private void ensureExplicitCapacity(int minCapacity) {
        modCount++;

        // 需要的容量超过实际容量
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }

    /**
     * 能分配的最大数组的大小。
     * 一些虚拟机会保留一些头消息，占用部分空间。
     * 尝试分配比这个值更大的空间可能会抛出 OutOfMemoryError 错误：请求的
     * 数组大小超过了虚拟机的限制。
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 增大容量确保可以容纳指定最小容量的元素。
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;
        // 新的容量是原来的 1.5 倍
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        // 如果 newCapacity 不足以容纳 minCapacity，那么直接扩容到 minCapacity
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        // 如果 newCapacity 比最大容量还大，调用 hugeCapacity 函数进行判断
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // elementData 最终指向 newCapacity 大小的新数组空间
        elementData = Arrays.copyOf(elementData, newCapacity);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    /**
     * 返回列表中元素的数量
     *
     * @return the number of elements in this list
     */
    public int size() {
        return size;
    }

    /**
     * 如果列表中没有元素返回 true。
     *
     * @return true if this list contains no elements
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 如果列表包含指定元素返回 true。
     * 更正式地，当且仅当列表包含一个元素 e 且满足
     * (o==null ? e==null : o.equals(e)) 时返回true。
     *
     * @param o element whose presence in this list is to be tested
     * @return true if this list contains the specified element
     */
    public boolean contains(Object o) {
        // 调用 indexOf 找到索引，根据索引判断是否存在
        return indexOf(o) >= 0;
    }

    /**
     * 返回列表中第一次出现指定元素的索引位置，如果列表不包含该元素返回 -1。
     * 更正式地说，如果满足
     * (o==null ? get(i)==null : o.equals(get(i))) 则返回最小的索引值 i，否则认为
     * 索引不存在则返回-1。
     *
     * @August 从前往后找，找到即返回
     */
    public int indexOf(Object o) {
        if (o == null) {
            for (int i = 0; i < size; i++)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = 0; i < size; i++)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 返回列表中最后一次出现指定元素的索引位置，如果列表不包含该元素返回 -1。
     * 更正式地说，如果满足
     * (o==null ? get(i)==null : o.equals(get(i))) 则返回最大的索引值 i，否则认为
     * 索引不存在则返回-1。
     *
     * @August 从后往前找，找到即返回
     */
    public int lastIndexOf(Object o) {
        if (o == null) {
            for (int i = size-1; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = size-1; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 返回 ArrayList 实例的拷贝。
     *
     * @return a clone of this ArrayList instance
     */
    public Object clone() {
        try {
            // super.clone 是 Object 对象的浅拷贝。
            // 浅层拷贝就是创建一个新的实例，在内存中开辟新的地址生成obj2，
            // 但是obj2的子对象却没有被拷贝obj1的子对象，而是拷贝的obj1子对象
            // 的引用。
            ArrayList<?> v = (ArrayList<?>) super.clone();
            // elementData 是新的数组对象，由于浅拷贝没有拷贝子对象，所以要
            // 自己调用 Arrays.copyOf 拷贝。
            v.elementData = Arrays.copyOf(elementData, size);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * 以适当的顺序（从第一个元素到最后一个元素）返回包含列表所有元素的数组。
     *
     * 返回的数组是安全的，因为原列表中不包含它的引用。（换句话说，返回的
     * 数组是新分配的内存空间）。调用者可以任意修改返回的数组。
     *
     * @return an array containing all of the elements in this list in
     *         proper sequence
     */
    public Object[] toArray() {
        return Arrays.copyOf(elementData, size);
    }

    /**
     * 以适当的顺序（从第一个元素到最后一个元素）返回包含列表所有元素的数组。
     * 返回数组的运行时类型是指定数组的类型。如果指定数组能完全容纳列表所有
     * 元素，那么将所有元素存入指定数组内。否则，在内存中分配足以容纳所有
     * 元素的新的数组空间。
     *
     * 如果指定的数组还有多余的空间（即指定数组比列表有更多的元素），在数组
     * 中紧跟集合末尾的元素被设置为 null。
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            // 指定数组长度小于size，返回一个以列表元素填充的新数组。
            return (T[]) Arrays.copyOf(elementData, size, a.getClass());
        System.arraycopy(elementData, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    // Positional Access Operations
    // 位置访问相关操作

    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

    /**
     * 返回列表中指定位置的元素
     *
     * @param  index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        // 边界检查
        rangeCheck(index);

        return elementData(index);
    }

    /**
     * 用指定元素替换列表中某一位置的元素。返回值是该位置的旧值。
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E set(int index, E element) {
        rangeCheck(index);

        E oldValue = elementData(index);
        elementData[index] = element;
        return oldValue;
    }

    /**
     * 在列表末尾添加元素
     *
     * @param e element to be appended to this list
     * @return true (as specified by {@link Collection#add})
     */
    public boolean add(E e) {
        // 确保容量充足
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }

    /**
     * 在列表指定位置添加元素。把该位置及之后的所有元素向后移动（索引加一）。
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public void add(int index, E element) {
        rangeCheckForAdd(index);

        ensureCapacityInternal(size + 1);  // Increments modCount!!
        System.arraycopy(elementData, index, elementData, index + 1,
                size - index);
        elementData[index] = element;
        size++;
    }

    /**
     * 删除列表指定位置的元素。
     * 把后续元素向左移动（索引减一）。
     *
     * @param index the index of the element to be removed
     * @return the element that was removed from the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        rangeCheck(index);

        modCount++;
        E oldValue = elementData(index);

        // 计算要移动的元素个数
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                    numMoved);
        elementData[--size] = null; // clear to let GC do its work

        return oldValue;
    }

    /**
     * 删除列表中第一次出现的指定元素，如果它存在的话。如果该元素不存在，
     * 不作任何变化。更正式地说，删除满足条件
     * (o==null ? get(i)==null : o.equals(get(i))) 的索引值最小的元素。如果操作
     * 成功则返回 true。
     *
     * @param o element to be removed from this list, if present
     * @return true if this list contained the specified element
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }

    /**
     * 私有的删除方法，填过了边界检查且不返回删除元素的值。
     */
    private void fastRemove(int index) {
        modCount++;
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                    numMoved);
        elementData[--size] = null; // clear to let GC do its work
    }

    /**
     * 删除列表中的所有元素。此方法调用后列表为空。
     */
    public void clear() {
        modCount++;

        // clear to let GC do its work
        for (int i = 0; i < size; i++)
            elementData[i] = null;

        size = 0;
    }

    /**
     * 把指定集合的所有元素，按照迭代器指定集合迭代器返回的顺序，添加到列表
     * 末尾。如果指定集合在操作过程中被修改，则这个操作的行为是不确定的。
     *
     * @August 结构性修改操作的一般步骤是，检查边界，检查列表容量（扩容），
     *                最后添加或修改。
     *
     * @param c collection containing elements to be added to this list
     * @return true if this list changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacityInternal(size + numNew);  // Increments modCount
        System.arraycopy(a, 0, elementData, size, numNew);
        size += numNew;
        return numNew != 0;
    }

    /**
     * 从指定位置开始，将指定集合的所有元素插入列表中。把当前位置及之后的
     * 元素向右移动（增加索引）。新增加的元素将按照指定集合迭代器的返回顺序
     * 出现在列表中。
     *
     * @param index index at which to insert the first element from the
     *              specified collection
     * @param c collection containing elements to be added to this list
     * @return true if this list changed as a result of the call
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws NullPointerException if the specified collection is null
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);

        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacityInternal(size + numNew);  // Increments modCount

        int numMoved = size - index;
        // 如果索引之后还有元素，先将这些元素向右移动，为集合 c 留出位置。
        if (numMoved > 0)
            System.arraycopy(elementData, index, elementData, index + numNew,
                    numMoved);
        // 将集合 c 中元素复制到指定索引的位置
        System.arraycopy(a, 0, elementData, index, numNew);
        size += numNew;
        return numNew != 0;
    }

    /**
     * 删除列表中从 fromIndex（包含），到 toIndex（不包含）索引之间的元素。
     * 将所有后续元素向左移动（减小索引）。（如果 toIndex == fromIndex，此
     * 操作无影响。）
     *
     * @throws IndexOutOfBoundsException if {@code fromIndex} or
     *         {@code toIndex} is out of range
     *         ({@code fromIndex < 0 ||
     *          fromIndex >= size() ||
     *          toIndex > size() ||
     *          toIndex < fromIndex})
     */
    protected void removeRange(int fromIndex, int toIndex) {
        modCount++;
        int numMoved = size - toIndex;
        // 将 toIndex 之后的元素左移，直接覆盖要删除的元素
        System.arraycopy(elementData, toIndex, elementData, fromIndex,
                numMoved);

        // 将末尾空出来的位置设置为 null。
        int newSize = size - (toIndex-fromIndex);
        for (int i = newSize; i < size; i++) {
            elementData[i] = null;
        }
        size = newSize;
    }

    /**
     * 检查给定的索引是否在范围内。如果不在，抛出适当的运行时异常。这个方法
     * 不会检查索引是否是负数：这个方法总是在访问数组之前使用，如果索引为
     * 负数，访问数组时会抛出 ArrayIndexOutOfBoundsException 异常。
     */
    private void rangeCheck(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * add 操作和 addAll 操作的 rangeCheck 版本。
     */
    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * 构造一个 IndexOutOfBoundsException 详细消息。
     * 在错误处理代码许多可能的构建中，这种“描述”对服务器和客户端虚拟机都
     * 表现最好。
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    /**
     * 移除列表中和指定集合相同的元素。
     *
     * @param c collection containing elements to be removed from this list
     * @return {@code true} if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see Collection#contains(Object)
     */
    public boolean removeAll(Collection<?> c) {
        // 使用 Objects 工具类检查集合 c 是否指向 null
        Objects.requireNonNull(c);
        // 根据第二个参数判断是删除还是保留
        return batchRemove(c, false);
    }

    /**
     * 保留列表中和指定集合相同的元素（求交集）。换句话说，移除列表中有的而
     * 指定集合中没有的那些元素。
     *
     * @param c collection containing elements to be retained in this list
     * @return {@code true} if this list changed as a result of the call
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null element (optional),
     *         or if the specified collection is null
     * @see Collection#contains(Object)
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return batchRemove(c, true);
    }

    // complement 为 true，保留集合中存在的元素；complement 为 false 时删除。
    private boolean batchRemove(Collection<?> c, boolean complement) {
        // 对于一个final变量，如果是基本数据类型的变量，则其数值一旦在初始化
        // 之后便不能更改；如果是引用类型的变量，则在对其初始化之后便不能
        // 再让其指向另一个对象。
        final Object[] elementData = this.elementData;
        int r = 0, w = 0;
        boolean modified = false;
        try {
            for (; r < size; r++)
                // complement 为 false：若 c 不包含 elementData[r]，保留该
                // elementData[r]。
                // complement 为 true：若 c 包含 elementData[r]，保留该
                // elementData[r]。
                if (c.contains(elementData[r]) == complement)
                    // 将右边的元素左移，相当于保留该元素，删除左边的元素。
                    elementData[w++] = elementData[r];
        } finally {
            if (r != size) {
                System.arraycopy(elementData, r,
                        elementData, w,
                        size - r);
                w += size - r;
            }
            if (w != size) {
                // 索引 w 之后的元素设为 null
                for (int i = w; i < size; i++)
                    elementData[i] = null;
                modCount += size - w;
                size = w;
                modified = true;
            }
        }
        return modified;
    }

    /**
     * 保留 ArrayList 实例的状态到 stream 里（也就是序列化它）。
     *
     * @serialData The length of the array backing the ArrayList
     *             instance is emitted (int), followed by all of its elements
     *             (each an Object) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException{
        // Write out element count, and any hidden stuff
        // 写出元素计数和所有隐藏的东西。在写之前保存此刻的 modCount。
        int expectedModCount = modCount;
        s.defaultWriteObject();

        // Write out size as capacity for behavioural compatibility with clone()
        s.writeInt(size);

        // 以正确的顺序写出所有元素，遍历 elementData。
        for (int i=0; i<size; i++) {
            s.writeObject(elementData[i]);
        }

        // 写入过程中被其他线程修改，抛出并发异常
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * 从 stream 流里复原 ArrayList 实例（也就是说反序列化它）。
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        elementData = EMPTY_ELEMENTDATA;

        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in capacity
        // 读入容量
        s.readInt(); // ignored

        if (size > 0) {
            // 就像 clone() 一样，存储数组是基于 size 而不是 capacity
            int capacity = calculateCapacity(elementData, size);
            SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, capacity);
            ensureCapacityInternal(size);

            Object[] a = elementData;
            // 以正确的顺序读入所有元素
            for (int i=0; i<size; i++) {
                a[i] = s.readObject();
            }
        }
    }

    /**
     * 从列表的指定位置开始，返回列表中元素的列表迭代器（按正确顺序）。
     * 指定的索引表示第一次调用 next 方法返回的第一个元素。第一次调用
     * previous 方法将返回指定索引减 1 代表的元素。
     *
     * 返回的迭代器是支持 fast-fail 的。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("Index: "+index);
        return new ListItr(index);
    }

    /**
     * 返回列表中元素的列表迭代器（按正确顺序）。
     *
     * 返回的迭代器是支持 fast-fail 的。
     *
     * @see #listIterator(int)
     */
    public ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    /**
     * 按正确顺序返回列表中元素的迭代器。
     *
     * 返回的迭代器是支持 fast-fail 的。
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * AbstractList.Itr的优化版本
     */
    private class Itr implements Iterator<E> {
        int cursor;       // 将要返回的下一个元素的索引
        int lastRet = -1;     // 上一个返回元素的索引，如果没有则为 -1
        int expectedModCount = modCount;

        Itr() {}

        // 是否有下个元素
        public boolean hasNext() {
            return cursor != size;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            // 是否并发修改一致
            checkForComodification();
            int i = cursor;
            if (i >= size)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            // cursor 向后移动一位，返回移动前指向的元素，并将 lastRet 设置为 i
            cursor = i + 1;
            return (E) elementData[lastRet = i];
        }

        // 删除元素
        public void remove() {
            // 根据 lastRet 的值，保证 remove 方法必须在 next 之后使用
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                // 注意此处删除的是 lastRet，而不是 cursor
                ArrayList.this.remove(lastRet);
                cursor = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> consumer) {
            Objects.requireNonNull(consumer);
            final int size = ArrayList.this.size;
            int i = cursor;
            if (i >= size) {
                return;
            }
            final Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length) {
                throw new ConcurrentModificationException();
            }
            while (i != size && modCount == expectedModCount) {
                consumer.accept((E) elementData[i++]);
            }
            // update once at end of iteration to reduce heap write traffic
            cursor = i;
            lastRet = i - 1;
            checkForComodification();
        }

        // 检查并发修改异常
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * AbstractList.ListItr的优化版本
     */
    private class ListItr extends Itr implements ListIterator<E> {
        // 从指定索引开始的构造方法
        ListItr(int index) {
            super();
            cursor = index;
        }

        // 是否有上一个元素
        public boolean hasPrevious() {
            return cursor != 0;
        }

        // 返回下一个元素的索引
        public int nextIndex() {
            return cursor;
        }

        // 返回上一个元素的索引
        public int previousIndex() {
            return cursor - 1;
        }

        @SuppressWarnings("unchecked")
        // 返回上一个元素
        public E previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            // cursor 向前移动一位，指向当前元素，并且返回当前元素的值
            cursor = i;
            return (E) elementData[lastRet = i];
        }

        // 修改 lastRet 指向的元素的值
        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.set(lastRet, e);
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        // 添加新元素
        public void add(E e) {
            checkForComodification();

            try {
                // 在 cursor 指向索引的位置前面添加新元素，然后 cursor 向后移动
                // 一位
                int i = cursor;
                ArrayList.this.add(i, e);
                cursor = i + 1;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * 返回 fromIndex（包含该位置）到 toIndex（不包含该位置）之间此列表的
     * 视图 。（如果 fromIndex 等于 toIndex，返回的列表为空）返回的列表是
     * 依赖于此列表的，所以返回列表的非结构化更改会反映到此列表中，反之亦然。
     * 返回的列表支持列表所有操作。
     *
     * 此方法消除了对显式范围方法的需要（通常还需要对数组排序）。任何需要
     * 对整个列表部分的操作都可以传递 subList，而不是对整个列表进行操作
     * （提高性能）。例如，下面的语句从列表中删除范围内的元素：
     * list.subList(from, to).clear();
     * 可以为 indexOf(Object) 和 lastIndexOf(Object) 构造类似的语句。
     * Collection 中的所有算法都可以应用 subList 的思想。
     *
     * 如果原列表通过子列表以外的方式进行了结构性修改，那么通过这个方法返回
     * 的列表就变得不确定。（结构化的修改是指改变了这个列表的大小，或者其他
     * 扰乱列表的方式，使得正在进行的迭代可能产生不正确的结果）
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        // 检查索引是否超出边界范围
        subListRangeCheck(fromIndex, toIndex, size);
        return new SubList(this, 0, fromIndex, toIndex);
    }

    // 返回子列表边界检查
    static void subListRangeCheck(int fromIndex, int toIndex, int size) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > size)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                    ") > toIndex(" + toIndex + ")");
    }

    private class SubList extends AbstractList<E> implements RandomAccess {
        // 父列表
        private final AbstractList<E> parent;
        // 相对于父列表的偏移值
        private final int parentOffset;
        // 相对子列表自己的偏移值
        private final int offset;
        // 子列表的大小
        int size;

        // 有参数的构造函数
        SubList(AbstractList<E> parent,
                int offset, int fromIndex, int toIndex) {
            this.parent = parent;
            this.parentOffset = fromIndex;
            this.offset = offset + fromIndex;
            this.size = toIndex - fromIndex;
            this.modCount = ArrayList.this.modCount;
        }

        // 修改索引 index 位置的值， 返回修改前的值
        // 注意：子列表和原列表保存同一段元素，修改子列表元素值，父列表里面
        // 也改变了
        public E set(int index, E e) {
            // 检查索引
            rangeCheck(index);
            // 检查并发
            checkForComodification();
            E oldValue = ArrayList.this.elementData(offset + index);
            ArrayList.this.elementData[offset + index] = e;
            return oldValue;
        }

        // 获取某一索引位置的值
        public E get(int index) {
            rangeCheck(index);
            checkForComodification();
            return ArrayList.this.elementData(offset + index);
        }

        // 返回子列表大小
        public int size() {
            checkForComodification();
            return this.size;
        }

        // 以下操作的实现均调用父列表相应的函数，由于父列表和子列表共用一个
        // elementData 数组，所以父列表同样受到影响
        // 添加元素操作
        // 参数 index 是相对于子列表而言
        public void add(int index, E e) {
            rangeCheckForAdd(index);
            checkForComodification();
            // 在实现中，调用父列表的 add 方法，所以传入的参数 index 作相应修改
            parent.add(parentOffset + index, e);
            this.modCount = parent.modCount;
            this.size++;
        }

        // 感觉索引移除元素，并返回移除的元素。父列表和子列表都会受影响
        public E remove(int index) {
            rangeCheck(index);
            checkForComodification();
            E result = parent.remove(parentOffset + index);
            this.modCount = parent.modCount;
            this.size--;
            return result;
        }

        // 移除范围内的元素
        protected void removeRange(int fromIndex, int toIndex) {
            checkForComodification();
            parent.removeRange(parentOffset + fromIndex,
                    parentOffset + toIndex);
            this.modCount = parent.modCount;
            this.size -= toIndex - fromIndex;
        }

        // 添加指定集合的所有元素到子列表的末尾
        public boolean addAll(Collection<? extends E> c) {
            return addAll(this.size, c);
        }

        // 添加指定集合的所有元素到子列表的末尾
        public boolean addAll(int index, Collection<? extends E> c) {
            rangeCheckForAdd(index);
            int cSize = c.size();
            if (cSize==0)
                return false;

            checkForComodification();
            parent.addAll(parentOffset + index, c);
            this.modCount = parent.modCount;
            this.size += cSize;
            return true;
        }

        public Iterator<E> iterator() {
            return listIterator();
        }

        public ListIterator<E> listIterator(final int index) {
            checkForComodification();
            rangeCheckForAdd(index);
            final int offset = this.offset;

            return new ListIterator<E>() {
                int cursor = index;
                int lastRet = -1;
                int expectedModCount = ArrayList.this.modCount;

                public boolean hasNext() {
                    return cursor != SubList.this.size;
                }

                @SuppressWarnings("unchecked")
                public E next() {
                    checkForComodification();
                    int i = cursor;
                    if (i >= SubList.this.size)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i + 1;
                    return (E) elementData[offset + (lastRet = i)];
                }

                public boolean hasPrevious() {
                    return cursor != 0;
                }

                @SuppressWarnings("unchecked")
                public E previous() {
                    checkForComodification();
                    int i = cursor - 1;
                    if (i < 0)
                        throw new NoSuchElementException();
                    Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length)
                        throw new ConcurrentModificationException();
                    cursor = i;
                    return (E) elementData[offset + (lastRet = i)];
                }

                @SuppressWarnings("unchecked")
                public void forEachRemaining(Consumer<? super E> consumer) {
                    Objects.requireNonNull(consumer);
                    final int size = SubList.this.size;
                    int i = cursor;
                    if (i >= size) {
                        return;
                    }
                    final Object[] elementData = ArrayList.this.elementData;
                    if (offset + i >= elementData.length) {
                        throw new ConcurrentModificationException();
                    }
                    while (i != size && modCount == expectedModCount) {
                        consumer.accept((E) elementData[offset + (i++)]);
                    }
                    // update once at end of iteration to reduce heap write traffic
                    lastRet = cursor = i;
                    checkForComodification();
                }

                public int nextIndex() {
                    return cursor;
                }

                public int previousIndex() {
                    return cursor - 1;
                }

                public void remove() {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        SubList.this.remove(lastRet);
                        cursor = lastRet;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void set(E e) {
                    if (lastRet < 0)
                        throw new IllegalStateException();
                    checkForComodification();

                    try {
                        ArrayList.this.set(offset + lastRet, e);
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                public void add(E e) {
                    checkForComodification();

                    try {
                        int i = cursor;
                        SubList.this.add(i, e);
                        cursor = i + 1;
                        lastRet = -1;
                        expectedModCount = ArrayList.this.modCount;
                    } catch (IndexOutOfBoundsException ex) {
                        throw new ConcurrentModificationException();
                    }
                }

                final void checkForComodification() {
                    if (expectedModCount != ArrayList.this.modCount)
                        throw new ConcurrentModificationException();
                }
            };
        }

        // SubList 的 subList 方法，其中传入的 offset 参数是 SubList 的 offset
        // 属性的值
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size);
            return new SubList(this, offset, fromIndex, toIndex);
        }

        private void rangeCheck(int index) {
            if (index < 0 || index >= this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private void rangeCheckForAdd(int index) {
            if (index < 0 || index > this.size)
                throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        private String outOfBoundsMsg(int index) {
            return "Index: "+index+", Size: "+this.size;
        }

        private void checkForComodification() {
            if (ArrayList.this.modCount != this.modCount)
                throw new ConcurrentModificationException();
        }

        // 返回分裂迭代器
        public Spliterator<E> spliterator() {
            checkForComodification();
            return new ArrayListSpliterator<E>(ArrayList.this, offset,
                    offset + this.size, this.modCount);
        }
    }

    @Override
    // Java 8 Lambda 表达式遍历列表元素的方式
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final int expectedModCount = modCount;
        @SuppressWarnings("unchecked")
        final E[] elementData = (E[]) this.elementData;
        final int size = this.size;
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            action.accept(elementData[i]);
        }
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    /**
     * 在此列表中的元素上创建一个支持 late-binding 和 fail-fast 的 Spliterator。
     *
     * @August 可分割迭代器（splitable iterator），增强并行处理能力。用来
     * 多线程并行迭代的迭代器。主要作用是把集合分成几段，每个线程执行一段。
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new ArrayListSpliterator<E>(this, 0, -1, 0);
    }

    /** 基于索引的，二分的，懒加载器 */
    static final class ArrayListSpliterator<E> implements Spliterator<E> {

        /**
         * 如果 ArrayList 是不可变的，或者在结构上是不可变的（没有添加，删除
         * 等操作），我们可以用 ArrayList.spliterator 实现它们的 spliterator。
         * 相反，我们在遍历过程中检测尽可能多的干扰，同时又不会牺牲太多性能。
         * If ArrayLists were immutable, or structurally immutable (no
         * adds, removes, etc), we could implement their spliterators
         * with Arrays.spliterator. Instead we detect as much
         * interference during traversal as practical without
         * sacrificing much performance. We rely primarily on
         * modCounts. These are not guaranteed to detect concurrency
         * violations, and are sometimes overly conservative about
         * within-thread interference, but detect enough problems to
         * be worthwhile in practice. To carry this out, we (1) lazily
         * initialize fence and expectedModCount until the latest
         * point that we need to commit to the state we are checking
         * against; thus improving precision.  (This doesn't apply to
         * SubLists, that create spliterators with current non-lazy
         * values).  (2) We perform only a single
         * ConcurrentModificationException check at the end of forEach
         * (the most performance-sensitive method). When using forEach
         * (as opposed to iterators), we can normally only detect
         * interference after actions, not before. Further
         * CME-triggering checks apply to all other possible
         * violations of assumptions for example null or too-small
         * elementData array given its size(), that could only have
         * occurred due to interference.  This allows the inner loop
         * of forEach to run without any further checks, and
         * simplifies lambda-resolution. While this does entail a
         * number of checks, note that in the common case of
         * list.stream().forEach(a), no checks or other computation
         * occur anywhere other than inside forEach itself.  The other
         * less-often-used methods cannot take advantage of most of
         * these streamlinings.
         */

        // 用于存放 ArrayList 对象
        private final ArrayList<E> list;
        // 当前索引（包含），advance/split 操作时会被修改
        private int index;
        // 结束位置（不包含），-1 表示到最后一个元素
        private int fence;
        // 用于存放 list 的 modCount
        private int expectedModCount; // initialized when fence set

        /** Create new spliterator covering the given  range */
        // 构造函数
        ArrayListSpliterator(ArrayList<E> list, int origin, int fence,
                             int expectedModCount) {
            this.list = list; // OK if null unless traversed
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        // 获取结束位置（首次使用需要对 fence 赋值）
        private int getFence() { // initialize fence to size on first use
            int hi; // (a specialized variant appears in method forEach)
            ArrayList<E> lst;
            // 第一次初始化时 fence 才会小于 0
            if ((hi = fence) < 0) {
                // list 为 null 时，fence = 0
                if ((lst = list) == null)
                    hi = fence = 0;
                // 否则，fence 等于 list 的长度
                else {
                    expectedModCount = lst.modCount;
                    hi = fence = lst.size;
                }
            }
            return hi;
        }

        // 分割 list，返回一个新分割出的 spliterator 实例
        public ArrayListSpliterator<E> trySplit() {
            // hi 为当前结束位置
            // lo 为起始位置
            // mid 为中间位置
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            // lo >= mid 表示不能再分割，返回 null
            // lo < mid 可分割，切割（lo, mid) 出去，同时更新 index = mid
            return (lo >= mid) ? null : // divide range in half unless too small
                    new ArrayListSpliterator<E>(list, lo, index = mid,
                            expectedModCount);
        }

        // 返回 true 表示可能还有元素未处理
        // 返回 false 表示没有剩余的元素了
        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            // hi 为当前的结束位置
            // i 为起始位置
            int hi = getFence(), i = index;
            // 还有剩余的元素没有处理
            if (i < hi) {
                index = i + 1;
                @SuppressWarnings("unchecked") E e = (E)list.elementData[i];
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        // 顺序遍历处理所有剩下的元素
        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi, mc; // hoist accesses and checks from loop
            ArrayList<E> lst; Object[] a;
            if (action == null)
                throw new NullPointerException();
            if ((lst = list) != null && (a = lst.elementData) != null) {
                if ((hi = fence) < 0) {
                    mc = lst.modCount;
                    hi = lst.size;
                }
                else
                    mc = expectedModCount;
                if ((i = index) >= 0 && (index = hi) <= a.length) {
                    for (; i < hi; ++i) {
                        @SuppressWarnings("unchecked") E e = (E) a[i];
                        action.accept(e);
                    }
                    if (lst.modCount == mc)
                        return;
                }
            }
            throw new ConcurrentModificationException();
        }

        // 估算大小
        public long estimateSize() {
            return (long) (getFence() - index);
        }

        // 返回特征值
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    @Override
    // 过滤器删除元素
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        // 找出要删除的元素，抛出的任何异常都会使集合不发生变化
        int removeCount = 0;
        // 一个 BitSet 类常见一种特殊类型的数组来保存位值。表示当前 index 的数
        // 是否存在
        final BitSet removeSet = new BitSet(size);
        final int expectedModCount = modCount;
        final int size = this.size;
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            @SuppressWarnings("unchecked")
            final E element = (E) elementData[i];
            if (filter.test(element)) {
                removeSet.set(i);
                removeCount++;
            }
        }
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }

        // 删除元素的实现：向左移动存活的元素。
        final boolean anyToRemove = removeCount > 0;
        if (anyToRemove) {
            final int newSize = size - removeCount;
            for (int i=0, j=0; (i < size) && (j < newSize); i++, j++) {
                i = removeSet.nextClearBit(i);
                elementData[j] = elementData[i];
            }
            for (int k=newSize; k < size; k++) {
                elementData[k] = null;  // Let gc do its work
            }
            this.size = newSize;
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            modCount++;
        }

        return anyToRemove;
    }

    @Override
    @SuppressWarnings("unchecked")
    // 根据操作符替换列表中所有元素
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final int expectedModCount = modCount;
        final int size = this.size;
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            elementData[i] = operator.apply((E) elementData[i]);
        }
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }

    @Override
    @SuppressWarnings("unchecked")
    // 根据 Comparator 排序
    public void sort(Comparator<? super E> c) {
        final int expectedModCount = modCount;
        Arrays.sort((E[]) elementData, 0, size, c);
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }
}

