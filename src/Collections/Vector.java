package Collections;
/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Vector类实现了一个可增长的数组。和数组一样，它包含了可以直接使用
 * 整数索引访问的组件。但是，Vector的大小可以根据需要增长或收缩，以
 * 适应在创建之后的添加和删除操作。
 *
 * 每一个向量都希望通过一个 capacity 和一个 capacityIncrement 来优化
 * 存储管理。capacity 至少和向量的大小一样大，实际上会更大一点，因为
 * 随着新的组件被添加到向量中，向量的存储以块的形式增加，块的大小
 * 为 capacityIncrement。应用程序可以在添加大量组件之前增加向量的
 * 容量；这会减少空间再分配的次数。
 *
 * 该类的 iterator 方法和 listIterator 方法返回的迭代器都支持 fail-fast：
 * 如果在迭代器创建之后，除了其自身的 remove 和 add 方法之外，一旦
 * 向量任何时候被结构性修改，会抛出 ConcurrentModificationException
 * 异常。因此，在面对并发修改的时候，迭代器会干净利落地停止，而不是
 * 在未来某个时间承担任意的风险和出现未知的行为。
 *
 * 注意迭代器的 fail-fast 行为不能完全保证正确，因为通常来说，非同步的
 * 并发修改都不能做出任何严格的承诺。支持 fail-fast 的迭代器会尽最大
 * 努力抛出 ConcurrentModificationException 异常。因此，编写一个依赖
 * 此异常来判断其正确性的程序是错误的：迭代器的快速故障行为应该只
 * 用于检测 bug。
 *
 * 从 Java 1.2 开始，这个类被修改为实现 List 接口，使其成为了
 * Java Collections Framework 的成员。和新的集合类不同的时，Vector
 * 是同步的。如果不考虑线程安全，推荐使用 ArrayList。
 *
 * @August Vector方法都加上了synchroized语句，在多线程环境下效率
 * 不高，现在大多不再使用了。
 * ArrayList与Vector拥有相同的扩容机制
 * 不同点在于ArrayList没有设置增长率，默认扩容为1.5倍。Vector设置了
 * 增长率
 *
 * @author  Lee Boynton
 * @author  Jonathan Payne
 * @see java.util.Collection
 * @see LinkedList
 * @since   JDK1.0
 */
public class Vector<E>
        extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    /**
     * 存储向量组件的数组缓冲区。向量的大小就是数组的长度，其大小至少
     * 应该所有的向量元素。
     *
     * 向量中最后一个元素后面的任何数组元素都为 null。
     *
     * @serial
     */
    protected Object[] elementData;

    /**
     * 在 Vector 对象中有效部件的数量，elementData[0] 到
     * elementData[elementCount - 1]是真实存在的部件。
     *
     * @serial
     */
    protected int elementCount;

    /**
     * 向量的容量在 size 大于 capacity 时增加的量。如果容量的增量小于或
     * 等于 0，向量的 capacity 就增加一倍。
     *
     * @serial
     */
    protected int capacityIncrement;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = -2767605614048989439L;

    /**
     * 根据指定的初始容量和容量增量构造空向量。
     *
     * @param   initialCapacity     the initial capacity of the vector
     * @param   capacityIncrement   the amount by which the capacity is
     *                              increased when the vector overflows
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    public Vector(int initialCapacity, int capacityIncrement) {
        super();
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+
                    initialCapacity);
        this.elementData = new Object[initialCapacity];
        this.capacityIncrement = capacityIncrement;
    }

    /**
     * 根据指定的初始容量构造空向量，容量增量为 0。
     *
     * @param   initialCapacity   the initial capacity of the vector
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    public Vector(int initialCapacity) {
        this(initialCapacity, 0);
    }

    /**
     * 构造初始容量为 10，标准容量增量为 0 的空向量。
     */
    public Vector() {
        this(10);
    }

    /**
     * 构造包含指定集合所有元素的向量，其存储的顺序为集合迭代器返回
     * 的顺序。
     *
     * @param c the collection whose elements are to be placed into this
     *       vector
     * @throws NullPointerException if the specified collection is null
     * @since   1.2
     */
    public Vector(java.util.Collection<? extends E> c) {
        elementData = c.toArray();
        elementCount = elementData.length;
        // c.toArray might (incorrectly) not return Object[] (see 6260652)
        if (elementData.getClass() != Object[].class)
            elementData = Arrays.copyOf(elementData, elementCount, Object[].class);
    }

    /**
     * 把向量的不见全部复制到指定数组里。向量中索引为 k 的元素将会
     * 复制到 anArray 中索引为 k 的位置。
     *
     * @param  anArray the array into which the components get copied
     * @throws NullPointerException if the given array is null
     * @throws IndexOutOfBoundsException if the specified array is not
     *         large enough to hold all the components of this vector
     * @throws ArrayStoreException if a component of this vector is not of
     *         a runtime type that can be stored in the specified array
     * @see #toArray(Object[])
     */
    public synchronized void copyInto(Object[] anArray) {
        System.arraycopy(elementData, 0, anArray, 0, elementCount);
    }

    /**
     * 把向量的容量修剪为当前的 size。如果向量的容量大于当前大小，那么
     * 通过替换掉内部用来存储元素的 elementData 来将容量更改为等于其
     * 当前大小。可以应用此操作最小化向量的存储空间。
     */
    public synchronized void trimToSize() {
        modCount++;
        int oldCapacity = elementData.length;
        // 有效部件的数量少于数组的大小，需要缩减数组。
        if (elementCount < oldCapacity) {
            elementData = Arrays.copyOf(elementData, elementCount);
        }
    }

    /**
     * 如果必要，增加向量的容量，确保它至少可以容纳最小参数指定的部件
     * 数量。
     *
     * 如果这个向量的当前容量小于 minCapacity，那么通过把 elementData
     * 替换成一个更大的数组来增加容量。新数组的大小是原数组的大小加上
     * capacityIncrement 的大小，除非 capacityIncrement 小于或等于 0，
     * 这种情况下新数组的容量是原来的两倍。如果扩容后的大小还是比
     * minCapacity 小，那么直接将容量变成 minCapacity 大小。
     *
     * @param minCapacity the desired minimum capacity
     */
    public synchronized void ensureCapacity(int minCapacity) {
        if (minCapacity > 0) {
            modCount++;
            ensureCapacityHelper(minCapacity);
        }
    }

    /**
     * 这一方法实现了 ensureCapacity 的非同步语义。该类中的同步方法
     * 可以在内部调用此方法，以确保容量，而不会产生额外同步的成本。
     *
     * @see #ensureCapacity(int)
     */
    private void ensureCapacityHelper(int minCapacity) {
        // overflow-conscious code
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }

    /**
     * 要分配的数组的最大大小。
     * 一些虚拟机在数组中保留一些头信息。
     * 尝试分配更大的数组可能会导致 OutOfMemoryError：请求的数组大
     * 小超过虚拟机限制。
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private void grow(int minCapacity) {
        // 计算新数组的空间大小 newCapacity
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
                capacityIncrement : oldCapacity);
        // 如果分配之后的空间依然小于指定的最小空间，直接将容量设定
        // 为 minCapacity。
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        // 如果分配之后的空间大小大于 MAX_ARRAY_SIZE，将容量设定
        // 为 minCapacity。
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        elementData = Arrays.copyOf(elementData, newCapacity);
    }

    // 将容量设定为 minCapacity
    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    /**
     * 设置向量的 size （不是 capacity）。如果新的 size 比当前的 size
     * 要大，将 null 元素添加到向量末尾。如果新的 size 比当前的 size
     * 要小，将 newSize 索引及之后的元素设置为 null。
     *
     * @param  newSize   the new size of this vector
     * @throws ArrayIndexOutOfBoundsException if the new size is negative
     */
    public synchronized void setSize(int newSize) {
        modCount++;
        if (newSize > elementCount) {
            ensureCapacityHelper(newSize);
        } else {
            for (int i = newSize ; i < elementCount ; i++) {
                elementData[i] = null;
            }
        }
        elementCount = newSize;
    }

    /**
     * 返回向量当前容量。
     *
     * @return  the current capacity (the length of its internal
     *          data array, kept in the field {@code elementData}
     *          of this vector)
     */
    public synchronized int capacity() {
        return elementData.length;
    }

    /**
     * 返回向量中部件的数量（size）。
     *
     * @return  the number of components in this vector
     */
    public synchronized int size() {
        return elementCount;
    }

    /**
     * 测试向量是否不包含任何部件（元素）。
     * Tests if this vector has no components.
     *
     * @return  {@code true} if and only if this vector has
     *          no components, that is, its size is zero;
     *          {@code false} otherwise.
     */
    public synchronized boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * 返回向量部件的枚举类。返回的枚举类包含向量中的所有元素。第一个
     * 元素是索引为 0 的元素，第二个是索引为 1 的元素，以此类推。
     *
     * @return  an enumeration of the components of this vector
     * @see     Iterator
     */
    public Enumeration<E> elements() {
        return new Enumeration<E>() {
            int count = 0;

            public boolean hasMoreElements() {
                return count < elementCount;
            }

            public E nextElement() {
                synchronized (this) {
                    if (count < elementCount) {
                        return elementData(count++);
                    }
                }
                throw new NoSuchElementException("Vector Enumeration");
            }
        };
    }

    /**
     * 如果向量包含指定元素返回 true。
     *
     * @param o element whose presence in this vector is to be tested
     * @return {@code true} if this vector contains the specified element
     */
    public boolean contains(Object o) {
        return indexOf(o, 0) >= 0;
    }

    /**
     * 返回向量中第一次出现指定元素的索引位置，如果不包含该元素返回 -1。
     *
     * @param o element to search for
     * @return the index of the first occurrence of the specified element in
     *         this vector, or -1 if this vector does not contain the element
     */
    public int indexOf(Object o) {
        return indexOf(o, 0);
    }

    /**
     * 从指定索引开始往后遍历，返回向量中第一次出现指定元素的索引
     * 位置，如果不包含该元素返回 -1。
     *
     * @param o element to search for
     * @param index index to start searching from
     * @return the index of the first occurrence of the element in
     *         this vector at position {@code index} or later in the vector;
     *         {@code -1} if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @see     Object#equals(Object)
     */
    public synchronized int indexOf(Object o, int index) {
        if (o == null) {
            for (int i = index ; i < elementCount ; i++)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = index ; i < elementCount ; i++)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 返回向量中最后一次出现指定元素的索引，不包含该元素返回 -1。
     *
     * @param o element to search for
     * @return the index of the last occurrence of the specified element in
     *         this vector, or -1 if this vector does not contain the element
     */
    public synchronized int lastIndexOf(Object o) {
        return lastIndexOf(o, elementCount-1);
    }

    /**
     * 从指定索引开始往前遍历，返回向量中第一次出现指定元素的索引
     * 位置（向量中最后一次出现的索引位置），如果不包含该元素返回 -1。
     *
     * @param o element to search for
     * @param index index to start searching backwards from
     * @return the index of the last occurrence of the element at position
     *         less than or equal to {@code index} in this vector;
     *         -1 if the element is not found.
     * @throws IndexOutOfBoundsException if the specified index is greater
     *         than or equal to the current size of this vector
     */
    public synchronized int lastIndexOf(Object o, int index) {
        if (index >= elementCount)
            throw new IndexOutOfBoundsException(index + " >= "+ elementCount);

        if (o == null) {
            for (int i = index; i >= 0; i--)
                if (elementData[i]==null)
                    return i;
        } else {
            for (int i = index; i >= 0; i--)
                if (o.equals(elementData[i]))
                    return i;
        }
        return -1;
    }

    /**
     * 返回指定索引位置的部件（元素）。
     *
     * 这个方法等价于 List 接口的 get 方法。
     *
     * @param      index   an index into this vector
     * @return     the component at the specified index
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     */
    public synchronized E elementAt(int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " + elementCount);
        }

        return elementData(index);
    }

    /**
     * 返回向量中的第一个元素（索引为 0 的元素）。
     *
     * @return     the first component of this vector
     * @throws NoSuchElementException if this vector has no components
     */
    public synchronized E firstElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData(0);
    }

    /**
     * 返回向量中的最后一个元素。
     *
     * @return  the last component of the vector, i.e., the component at index
     *          size() - 1.
     * @throws NoSuchElementException if this vector is empty
     */
    public synchronized E lastElement() {
        if (elementCount == 0) {
            throw new NoSuchElementException();
        }
        return elementData(elementCount - 1);
    }

    /**
     * 把向量中指定索引位置设定为指定元素。
     *
     * 指定索引必须大于等于 0，小于向量的当前 size。
     *
     * 此方法等价于 List 接口的 set 方法。注意 set 方法为了更符合数组的
     * 使用，参数的顺序不同。注意 set 方法返回了原来储存在该位置的元素。
     *
     * @param      obj     what the component is to be set to
     * @param      index   the specified index
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     */
    public synchronized void setElementAt(E obj, int index) {
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " +
                    elementCount);
        }
        elementData[index] = obj;
    }

    /**
     * 删除指定索引处的元素。向量中索引大于等于该指定索引的所有元素，
     * 向左移动一位，即索引减一。向量的大小也减一。
     *
     * 指定索引必须大于等于 0，小于向量的当前大小。
     *
     * 此方法等价于 List 接口的 remove 方法。注意 remove 返回了原来
     * 储存在该位置的元素，此方法没有。
     *
     * @param      index   the index of the object to remove
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     */
    public synchronized void removeElementAt(int index) {
        modCount++;
        if (index >= elementCount) {
            throw new ArrayIndexOutOfBoundsException(index + " >= " +
                    elementCount);
        }
        else if (index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        int j = elementCount - index - 1;
        if (j > 0) {
            // 将 elementData 数组里从索引为 index + 1 的元素开始，复制
            // 到 elementData 里索引为 index 的位置，复制元素的个数为 j 。
            System.arraycopy(elementData, index + 1, elementData, index, j);
        }
        elementCount--;
        elementData[elementCount] = null; /* to let gc do its work */
    }

    /**
     * 在指定索引处插入新元素。向量中每个大于等于当前索引的元素向后
     * 移动一位，即索引加一。
     *
     * 指定索引必须大于等于 0，小于等于向量当前大小。（如果指定索引
     * 等于向量的当前大小，新元素被添加到向量末尾。）
     *
     * 此方法等同于 List 接口的 add 方法。
     *
     *
     * @param      obj     the component to insert
     * @param      index   where to insert the new component
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     */
    public synchronized void insertElementAt(E obj, int index) {
        modCount++;
        if (index > elementCount) {
            throw new ArrayIndexOutOfBoundsException(index
                    + " > " + elementCount);
        }
        // 插入元素的一般步骤是，首先检查向量的容量是否足够大，
        // 然后使用 System.arrayCopy 将索引之后的元素右移，最后设置索引处
        // 的元素为指定元素。
        ensureCapacityHelper(elementCount + 1);
        System.arraycopy(elementData, index, elementData, index + 1, elementCount - index);
        elementData[index] = obj;
        elementCount++;
    }

    /**
     * 把指定元素添加到向量的末尾，并在向量的大小上加一。若向量的 size
     * 大于 capacity，那么 capacity 相应增加。此方法等同于 List 接口的
     * add 方法。
     *
     * @param   obj   the component to be added
     */
    public synchronized void addElement(E obj) {
        modCount++;
        ensureCapacityHelper(elementCount + 1);
        elementData[elementCount++] = obj;
    }

    /**
     * 删除向量中第一次出现（索引最小）的和指定元素匹配的元素。如果
     * 该元素存在，将所有索引大于等于该索引的元素向前移动一位，即
     * 索引减一。
     *
     * 此方法等同于 List 接口中的 remove 方法。
     *
     * @param   obj   the component to be removed
     * @return  {@code true} if the argument was a component of this
     *          vector; {@code false} otherwise.
     */
    public synchronized boolean removeElement(Object obj) {
        modCount++;
        int i = indexOf(obj);
        if (i >= 0) {
            removeElementAt(i);
            return true;
        }
        return false;
    }

    /**
     * 删除向量中所有元素，并将向量的大小设为 0。
     *
     * <p>This method is identical in functionality to the {@link #clear}
     * method (which is part of the {@link java.util.List} interface).
     */
    public synchronized void removeAllElements() {
        modCount++;
        // Let gc do its work
        for (int i = 0; i < elementCount; i++)
            elementData[i] = null;

        elementCount = 0;
    }

    /**
     * 返回向量的克隆。此克隆会保留指向新的内部数组的引用，而不是指向
     * 原数组的引用。
     *
     * @return  a clone of this vector
     */
    public synchronized Object clone() {
        try {
            @SuppressWarnings("unchecked")
            Vector<E> v = (Vector<E>) super.clone();
            v.elementData = Arrays.copyOf(elementData, elementCount);
            v.modCount = 0;
            return v;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    /**
     * 按顺序返回一个包含所有元素的数组。
     *
     * @since 1.2
     */
    public synchronized Object[] toArray() {
        return Arrays.copyOf(elementData, elementCount);
    }

    /**
     * 按顺序返回一个包含向量中所有元素的数组；返回的数组即为参数指定
     * 的数组。如果向量元素能全部保存在该数组中，那么返回该数组。否则，
     * 开辟一个新的数组空间用来保存向量中的元素，并返回该数组。
     *
     * 如果指定的数组还有多余的空间（即数组长度大于向量元素个数），
     * 数组中的剩余元素设置为 null。
     *
     * @param a the array into which the elements of the Vector are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the Vector
     * @throws ArrayStoreException if the runtime type of a is not a supertype
     * of the runtime type of every element in this Vector
     * @throws NullPointerException if the given array is null
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T[] toArray(T[] a) {
        if (a.length < elementCount)
            return (T[]) Arrays.copyOf(elementData, elementCount, a.getClass());

        System.arraycopy(elementData, 0, a, 0, elementCount);

        if (a.length > elementCount)
            a[elementCount] = null;

        return a;
    }

    // Positional Access Operations
    // 位置访问操作

    // 返回指定索引位置的元素
    @SuppressWarnings("unchecked")
    E elementData(int index) {
        return (E) elementData[index];
    }

    /**
     * 返回向量中指定位置的元素。
     *
     * @param index index of the element to return
     * @return object at the specified index
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *            ({@code index < 0 || index >= size()})
     * @since 1.2
     */
    public synchronized E get(int index) {
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);

        return elementData(index);
    }

    /**
     * 用指定元素替换向量中指定位置的元素，并返回原来的元素。
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     * @since 1.2
     */
    public synchronized E set(int index, E element) {
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);

        E oldValue = elementData(index);
        elementData[index] = element;
        return oldValue;
    }

    /**
     * 将指定的元素添加到向量末尾。
     *
     * @param e element to be appended to this Vector
     * @return {@code true} (as specified by {@link java.util.Collection#add})
     * @since 1.2
     */
    public synchronized boolean add(E e) {
        modCount++;
        ensureCapacityHelper(elementCount + 1);
        elementData[elementCount++] = e;
        return true;
    }

    /**
     * 删除向量中和指定元素匹配的第一个元素。如果向量不包含该元素，
     * 不做出任何改变。
     *
     * @param o element to be removed from this Vector, if present
     * @return true if the Vector contained the specified element
     * @since 1.2
     */
    public boolean remove(Object o) {
        return removeElement(o);
    }

    /**
     * 在向量中指定位置插入指定元素。将当前位置和之后的元素向右移动
     * 一位（索引加一）。
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     * @since 1.2
     */
    public void add(int index, E element) {
        insertElementAt(element, index);
    }

    /**
     * 删除指定位置处的元素。将之后的元素向左移动一位（索引减一）。
     * 并返回从向量中删除的元素。
     *
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     * @param index the index of the element to be removed
     * @return element that was removed
     * @since 1.2
     */
    public synchronized E remove(int index) {
        modCount++;
        if (index >= elementCount)
            throw new ArrayIndexOutOfBoundsException(index);
        E oldValue = elementData(index);

        // 删除该位置的元素的方法：使用 System.arraycopy 直接将之后的
        // 元素往前移动一位。
        int numMoved = elementCount - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                    numMoved);
        elementData[--elementCount] = null; // Let gc do its work

        return oldValue;
    }

    /**
     * 删除向量的所有元素。此方法调用后向量为空（除非抛出异常）。
     *
     * @since 1.2
     */
    public void clear() {
        removeAllElements();
    }

    // Bulk Operations
    // 批量操作

    /**
     * 如果向量包含指定集合中的所有元素，返回 true。
     *
     * @param   c a collection whose elements will be tested for containment
     *          in this Vector
     * @return true if this Vector contains all of the elements in the
     *         specified collection
     * @throws NullPointerException if the specified collection is null
     */
    public synchronized boolean containsAll(Collection<?> c) {
        return super.containsAll(c);
    }

    /**
     * 把指定集合中的所有元素添加到向量的末尾，以指定集合迭代器返回
     * 的顺序。如果操作进行过程中，指定集合被修改，则此操作的行为
     * 未定义。
     *
     * @param c elements to be inserted into this Vector
     * @return {@code true} if this Vector changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean addAll(Collection<? extends E> c) {
        modCount++;
        Object[] a = c.toArray();
        int numNew = a.length;
        // 先将向量扩容到足够大，然后把指定集合的数组复制到向量的数组里。
        ensureCapacityHelper(elementCount + numNew);
        System.arraycopy(a, 0, elementData, elementCount, numNew);
        elementCount += numNew;
        return numNew != 0;
    }

    /**
     * 删除向量中和指定集合相同的所有元素。
     *
     * @param c a collection of elements to be removed from the Vector
     * @return true if this Vector changed as a result of the call
     * @throws ClassCastException if the types of one or more elements
     *         in this vector are incompatible with the specified
     *         collection (optional)
     * @throws NullPointerException if this vector contains one or more null
     *         elements and the specified collection does not support null
     *         elements (optional),
     *         or if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    /**
     * 保留向量中和指定元素相同的部分。换句话说，删除向量中和指定
     * 集合元素不同的部分。
     *
     * @August 求交集
     *
     * @param c a collection of elements to be retained in this Vector
     *          (all other elements are removed)
     * @return true if this Vector changed as a result of the call
     * @throws ClassCastException if the types of one or more elements
     *         in this vector are incompatible with the specified
     *         collection (optional)
     * @throws NullPointerException if this vector contains one or more null
     *         elements and the specified collection does not support null
     *         elements (optional), or if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    /**
     * 把指定集合的所有元素插入到向量指定的位置。把当前位置和其之后
     * 的元素向后移动（索引减小）。插入的顺序为指定集合迭代器返回的
     * 顺序。
     *
     * @param index index at which to insert the first element from the
     *              specified collection
     * @param c elements to be inserted into this Vector
     * @return {@code true} if this Vector changed as a result of the call
     * @throws ArrayIndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     * @throws NullPointerException if the specified collection is null
     * @since 1.2
     */
    public synchronized boolean addAll(int index, Collection<? extends E> c) {
        modCount++;

        // 判断索引是否在范围内
        if (index < 0 || index > elementCount)
            throw new ArrayIndexOutOfBoundsException(index);

        // 将指定集合转化成数组便于插入
        Object[] a = c.toArray();
        int numNew = a.length;
        // 确保向量容量足够大
        ensureCapacityHelper(elementCount + numNew);

        // 将向量的数组中的元素向后移动，然后插入
        int numMoved = elementCount - index;
        if (numMoved > 0)
            System.arraycopy(elementData, index, elementData, index + numNew,
                    numMoved);

        System.arraycopy(a, 0, elementData, index, numNew);
        elementCount += numNew;
        return numNew != 0;
    }

    /**
     * 比较指定对象和此向量是否相等。如果指定对象也是 List，两个集合
     * 大小相同，且对应的元素均相等，那么返回 true。换句话说，如果两个
     * 集合对应元素对应相等，那么说两个集合相等。
     *
     * @param o the Object to be compared for equality with this Vector
     * @return true if the specified Object is equal to this Vector
     */
    public synchronized boolean equals(Object o) {
        return super.equals(o);
    }


    /**
     * 返回集合的 hash 值
     */
    public synchronized int hashCode() {
        return super.hashCode();
    }

    /**
     * 返回集合的字符串表示，包括集合所有元素的字符串表示。
     */
    public synchronized String toString() {
        return super.toString();
    }

    /**
     * 返回列表从 fromIndex（包含）到 toIndex（不包含）的视图。（如果
     * fromIndex 等于 toIndex，那么返回的列表为空。）返回的列表由此
     * 列表支撑，所以返回列表的任何改变将会影响此列表，反之亦然。返回
     * 的列表支持所有此列表支持的操作。
     *
     * 如果支撑列表在任何情况下进行了结构上的修改，那么返回列表的语义
     * 未知。（结构性的修改指的时改变列表大小。
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this List
     * @throws IndexOutOfBoundsException if an endpoint index value is out of range
     *         {@code (fromIndex < 0 || toIndex > size)}
     * @throws IllegalArgumentException if the endpoint indices are out of order
     *         {@code (fromIndex > toIndex)}
     */
    public synchronized List<E> subList(int fromIndex, int toIndex) {
        return Collections.synchronizedList(super.subList(fromIndex, toIndex),
                this);
    }

    /**
     * 删除 fromIndex（包含）到 toIndex（不包含）范围内的所有元素。
     * 将之后的元素向左移动（减小索引），如果 toIndex == fromIndex，
     * 此操作没有任何影响。
     */
    protected synchronized void removeRange(int fromIndex, int toIndex) {
        modCount++;
        int numMoved = elementCount - toIndex;
        System.arraycopy(elementData, toIndex, elementData, fromIndex,
                numMoved);

        // Let gc do its work
        int newElementCount = elementCount - (toIndex-fromIndex);
        while (elementCount != newElementCount)
            elementData[--elementCount] = null;
    }

    /**
     * 从流中加载向量实例。（即反序列化向量）。这一方法确保域的持久化。
     *
     * @param in the stream
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the stream contains data
     *         of a non-existing class
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField gfields = in.readFields();
        int count = gfields.get("elementCount", 0);
        Object[] data = (Object[])gfields.get("elementData", null);
        if (count < 0 || data == null || count > data.length) {
            throw new StreamCorruptedException("Inconsistent vector internals");
        }
        elementCount = count;
        elementData = data.clone();
    }

    /**
     * 把向量实例的状态加载到流里面（即序列化）。
     * 此方法确保流数据的持久化。
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException {
        final java.io.ObjectOutputStream.PutField fields = s.putFields();
        final Object[] data;
        synchronized (this) {
            fields.put("capacityIncrement", capacityIncrement);
            fields.put("elementCount", elementCount);
            data = elementData.clone();
        }
        fields.put("elementData", data);
        s.writeFields();
    }

    /**
     * 返回列表的迭代器（以正确的顺序），从列表指定位置开始。
     * 返回的迭代器支持 fast-fail。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized ListIterator<E> listIterator(int index) {
        if (index < 0 || index > elementCount)
            throw new IndexOutOfBoundsException("Index: "+index);
        return new ListItr(index);
    }

    /**
     * 返回列表的迭代器（以正确的顺序），从第一个元素开始。
     * 返回的迭代器支持 fast-fail。
     *
     * @see #listIterator(int)
     */
    public synchronized ListIterator<E> listIterator() {
        return new ListItr(0);
    }

    /**
     * 返回迭代器（以正确的顺序），从列表指定位置开始。
     * 返回的迭代器支持 fast-fail。
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public synchronized Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * AbstractList.Itr 的优化版本
     */
    private class Itr implements Iterator<E> {
        int cursor;       // index of next element to return
        int lastRet = -1; // index of last element returned; -1 if no such
        int expectedModCount = modCount;

        // 是否存在下一个元素
        public boolean hasNext() {
            // Racy but within spec, since modifications are checked
            // within or after synchronization in next/previous
            return cursor != elementCount;
        }

        // 返回 cursor指向的元素，并将游标向前移动一位。
        public E next() {
            synchronized (Vector.this) {
                checkForComodification();
                int i = cursor;
                if (i >= elementCount)
                    throw new NoSuchElementException();
                cursor = i + 1;
                return elementData(lastRet = i);
            }
        }

        // 删除 lastRet 指向的元素。即 lastRet 不等于 -1 才能删除
        public void remove() {
            if (lastRet == -1)
                throw new IllegalStateException();
            synchronized (Vector.this) {
                checkForComodification();
                Vector.this.remove(lastRet);
                expectedModCount = modCount;
            }
            cursor = lastRet;
            lastRet = -1;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            synchronized (Vector.this) {
                final int size = elementCount;
                int i = cursor;
                if (i >= size) {
                    return;
                }
                @SuppressWarnings("unchecked")
                final E[] elementData = (E[]) Vector.this.elementData;
                if (i >= elementData.length) {
                    throw new ConcurrentModificationException();
                }
                while (i != size && modCount == expectedModCount) {
                    action.accept(elementData[i++]);
                }
                // update once at end of iteration to reduce heap write traffic
                cursor = i;
                lastRet = i - 1;
                checkForComodification();
            }
        }

        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * AbstractList.ListItr 的优化版本
     */
    final class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            super();
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        // 返回 cursor - 1 指向的元素并向往回移动一位
        public E previous() {
            synchronized (Vector.this) {
                checkForComodification();
                int i = cursor - 1;
                if (i < 0)
                    throw new NoSuchElementException();
                cursor = i;
                return elementData(lastRet = i);
            }
        }

        // 把 lastRet 处的元素设置为指定元素
        public void set(E e) {
            if (lastRet == -1)
                throw new IllegalStateException();
            synchronized (Vector.this) {
                checkForComodification();
                Vector.this.set(lastRet, e);
            }
        }

        // 在 cursor 处添加指定元素
        public void add(E e) {
            int i = cursor;
            synchronized (Vector.this) {
                checkForComodification();
                Vector.this.add(i, e);
                expectedModCount = modCount;
            }
            cursor = i + 1;
            lastRet = -1;
        }
    }

    @Override
    public synchronized void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final int expectedModCount = modCount;
        @SuppressWarnings("unchecked")
        final E[] elementData = (E[]) this.elementData;
        final int elementCount = this.elementCount;
        for (int i=0; modCount == expectedModCount && i < elementCount; i++) {
            action.accept(elementData[i]);
        }
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    // 删除满足条件的元素
    public synchronized boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        // figure out which elements are to be removed
        // any exception thrown from the filter predicate at this stage
        // will leave the collection unmodified
        int removeCount = 0;
        final int size = elementCount;
        final BitSet removeSet = new BitSet(size);
        final int expectedModCount = modCount;
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

        // shift surviving elements left over the spaces left by removed elements
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
            elementCount = newSize;
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            modCount++;
        }

        return anyToRemove;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final int expectedModCount = modCount;
        final int size = elementCount;
        for (int i=0; modCount == expectedModCount && i < size; i++) {
            elementData[i] = operator.apply((E) elementData[i]);
        }
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }

    @SuppressWarnings("unchecked")
    @Override
    // 按比较器的规则排序
    public synchronized void sort(Comparator<? super E> c) {
        final int expectedModCount = modCount;
        Arrays.sort((E[]) elementData, 0, elementCount, c);
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
        modCount++;
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#ORDERED}.
     * Overriding implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new VectorSpliterator<>(this, null, 0, -1, 0);
    }

    /** Similar to ArrayList Spliterator */
    static final class VectorSpliterator<E> implements Spliterator<E> {
        private final Vector<E> list;
        private Object[] array;
        private int index; // current index, modified on advance/split
        private int fence; // -1 until used; then one past last index
        private int expectedModCount; // initialized when fence set

        /** Create new spliterator covering the given  range */
        VectorSpliterator(Vector<E> list, Object[] array, int origin, int fence,
                          int expectedModCount) {
            this.list = list;
            this.array = array;
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() { // initialize on first use
            int hi;
            if ((hi = fence) < 0) {
                synchronized(list) {
                    array = list.elementData;
                    expectedModCount = list.modCount;
                    hi = fence = list.elementCount;
                }
            }
            return hi;
        }

        public Spliterator<E> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                    new VectorSpliterator<E>(list, array, lo, index = mid,
                            expectedModCount);
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super E> action) {
            int i;
            if (action == null)
                throw new NullPointerException();
            if (getFence() > (i = index)) {
                index = i + 1;
                action.accept((E)array[i]);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi; // hoist accesses and checks from loop
            Vector<E> lst; Object[] a;
            if (action == null)
                throw new NullPointerException();
            if ((lst = list) != null) {
                if ((hi = fence) < 0) {
                    synchronized(lst) {
                        expectedModCount = lst.modCount;
                        a = array = lst.elementData;
                        hi = fence = lst.elementCount;
                    }
                }
                else
                    a = array;
                if (a != null && (i = index) >= 0 && (index = hi) <= a.length) {
                    while (i < hi)
                        action.accept((E) a[i++]);
                    if (lst.modCount == expectedModCount)
                        return;
                }
            }
            throw new ConcurrentModificationException();
        }

        public long estimateSize() {
            return (long) (getFence() - index);
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }
}

