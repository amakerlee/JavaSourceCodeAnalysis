package Collections;

import java.util.*;

/**
 * 这个类提供了List接口的基本实现，以最小化实现由“随机访问”数据存储支持的
 * 接口所需工作。对于顺序访问数据（例如链表），应该优先使用
 * AbstractSequentialList。
 *
 * 想要实现一个不可修改列表，程序员只需要扩展这个类并实现get和size方法。
 *
 * 想要实现可修改列表，程序员必须额外重写set方法（否则会抛出
 * UnsupportedOperationException异常）。如果列表大小允许变化，程序员必须
 * 重写add和remove方法。
 *
 * 根据Collection接口规范中的建议，程序员通常应该提供一个void（无参数）
 * 构造函数。
 *
 * 与其它抽象集合的实现不同的是，程序员不必提供迭代器的实现；iterator和
 * list iterator在这一个类中已经实现，在这些“随机访问”方法之上：
 * {@link #get(int)},
 * {@link #set(int, Object) set(int, E)},
 * {@link #add(int, Object) add(int, E)} and
 * {@link #remove(int)}.
 *
 * 该类中每个非抽象的方法在类文档中都详细描述了其实现。如果需要一个更高
 * 性能的实现，可以重写这些方法。
 *
 * 这个类是Java Collections Framework的成员。
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @since 1.2
 */

public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    /**
     * 唯一的构造函数。（由于是protect类型，所以用于子类构造函数的调用，
     * 通常是隐式的）
     */
    protected AbstractList() {
    }

    /**
     * 将指定元素添加到列表末尾（可选操作）
     *
     * 支持此操作的列表可能对添加到列表的元素设置限制。特别是，一些列表会
     * 拒绝添加 null 元素，其他的会对添加元素的类型施加限制。List 类应该在文档
     * 中清晰说明可以添加哪些元素。
     *
     * 这一实现会调用 add(size(), e) 方法。
     *
     * 如果 add(int, Object) 和 add(int, E) 方法都没有被重写，将会抛出
     * UnsupportedOperationException异常。
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws UnsupportedOperationException if the {@code add} operation
     *         is not supported by this list
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this list
     */
    public boolean add(E e) {
        add(size(), e);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    abstract public E get(int index);

    /**
     * {@inheritDoc}
     *
     * 这一实现总是抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     *这一实现总是抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * 这一视线总是抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }


    // Search Operations
    // 搜索操作

    /**
     * {@inheritDoc}
     *
     * 这个实现首先获得一个list iterator（通过listIterator()方法）。然后遍历
     * 列表，直到找到指定元素或者到达列表末尾。
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int indexOf(Object o) {
        ListIterator<E> it = listIterator();
        if (o==null) {
            while (it.hasNext())
                if (it.next()==null)
                    return it.previousIndex();
        } else {
            while (it.hasNext())
                if (o.equals(it.next()))
                    return it.previousIndex();
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     *
     * 这个实现首先获得一个list iterator（通过listIterator(size())方法）。然后
     * 遍历列表，直到找到指定元素或者到达列表的开头。
     *
     * @August 迭代器从后往前遍历
     *
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public int lastIndexOf(Object o) {
        ListIterator<E> it = listIterator(size());
        if (o==null) {
            while (it.hasPrevious())
                if (it.previous()==null)
                    return it.nextIndex();
        } else {
            while (it.hasPrevious())
                if (o.equals(it.previous()))
                    return it.nextIndex();
        }
        return -1;
    }


    // Bulk Operations
    // 批量操作

    /**
     * 删除列表中所有元素（可选操作）。调用此方法后列表为空。
     *
     * 这一实现会调用 removeRange(0, size()) 方法。
     *
     * 如果 remove(int index) 和 removeRange(int fromIndex, int toIndex) 都
     * 没有被重写，将会抛出 UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException if the {@code clear} operation
     *         is not supported by this list
     */
    public void clear() {
        removeRange(0, size());
    }

    /**
     * {@inheritDoc}
     *
     * 这一实现获取指定集合的迭代器并进行迭代，使用 add(int, E) 方法，将从
     * 迭代器获取的元素插入到列表的合适位置，每次插入一个。
     * 为了提高效率，许多实现都会重写这一方法。
     *
     * 如果 add 方法没有被重写，将会抛出 UnsupportedOperationException
     * 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);
        boolean modified = false;
        for (E e : c) {
            add(index++, e);
            modified = true;
        }
        return modified;
    }


    // Iterators
    // 迭代器

    /**
     * 按正确的顺序返回列表元素的迭代器。
     *
     * 这个实现返回 iterator 接口的一个简单实现，依赖于列表的 size(), get(int),
     * 和 remove(int) 方法。
     *
     * 如果列表的 remove(int) 方法没有被重写的话，迭代器会抛出
     * UnsupportedOperationException异常。
     *
     * 并发修改中,这一实现会抛出运行时异常,抛出异常的根据是modeCount域的
     * 值。
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * {@inheritDoc}
     *
     * 这一实现返回 listIterator。
     *
     * @see #listIterator(int)
     */
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    /**
     * {@inheritDoc}
     *
     * 这一实现返回一个简单的 ListIterator 接口的实现，它扩展了 iterator()
     * 方法返回的 Iterator 接口的实现。ListIterator 的实现依赖于列表的 size(),
     * get(int), 和 remove(int) 方法。
     *
     * 如果列表的 remove(int), set(int, E), 或者 add(int, E)方法没有被覆盖，
     * 这个实现返回的迭代器将会抛出 UnsupportedOperationException 异常。
     *
     * 并发修改中,这一实现会抛出运行时异常,抛出异常的根据是modeCount域的
     * 值。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public ListIterator<E> listIterator(final int index) {
        rangeCheckForAdd(index);
        return new ListItr(index);
    }

    // 私有内部类，实现 Iterator 接口
    private class Itr implements Iterator<E> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        int cursor = 0;

        /**
         * 最新调用 next 或 previous 方法的元素索引。如果该元素被删除则为-1。
         */
        int lastRet = -1;

        /**
         * 迭代器认为列表应该具有的 modCount 值，如果违背了这个期望，迭代器会
         * 检测到发生了并发修改。
         *
         * @August modCount 记录对象的修改次数。
         *                注意到 modCount 声明为 volatile，保证线程之间修改的可见性。
         *                fail-fast策略：如果在使用迭代器的过程中有其他线程修改了
         *                集合，那么将抛出ConcurrentModificationException
         */
        int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor != size();
        }

        // 索引超出界限抛出 IndexOutOfBoundsException 异常
        // cursor向后移动一位，修改 lastRet 的值。
        public E next() {
            checkForComodification();
            try {
                int i = cursor;
                E next = get(i);
                lastRet = i;
                cursor = i + 1;
                return next;
            } catch (IndexOutOfBoundsException e) {
                checkForComodification();
                throw new NoSuchElementException();
            }
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            // 调用AbstractList的remove方法删除lastRet代表的元素。
            try {
                AbstractList.this.remove(lastRet);
                if (lastRet < cursor)
                    cursor--;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }

        // modCount是否被修改，若不符合预期抛出异常。
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    // 私有内部类,实现 ListIterator 接口
    private class ListItr extends Itr implements ListIterator<E> {
        ListItr(int index) {
            cursor = index;
        }

        public boolean hasPrevious() {
            return cursor != 0;
        }

        public E previous() {
            checkForComodification();
            try {
                int i = cursor - 1;
                E previous = get(i);
                lastRet = cursor = i;
                return previous;
            } catch (IndexOutOfBoundsException e) {
                checkForComodification();
                throw new NoSuchElementException();
            }
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor-1;
        }

        public void set(E e) {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            // 调用 AbstractList 的 set 方法设置 lastRet 为指定元素。
            try {
                AbstractList.this.set(lastRet, e);
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }

        public void add(E e) {
            checkForComodification();

            // 调用 AbstractList 的 add 方法将指定元素添加到cursor位置。
            try {
                int i = cursor;
                AbstractList.this.add(i, e);
                lastRet = -1;
                cursor = i + 1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * 这个实现返回作为 AbstractList 子类的列表。子类在私有字段中存储列表中
     * 子列表的偏移量，子列表的大小（可以再生命周期中更改），和预期的
     * modCount值。子类中有两个变量，其中一个实现了 RandomAccess。如果
     * 列表实现了 RandomAccess，则返回的子列表将是实现了 RandomAccess 的
     * 子类实例。
     *
     * 子类的 set(int, E), get(int), add(int, E)}, remove(int)},
     * addAll(int, Collection)} and removeRange(int, int) 方法在检查索引边界并
     * 调整偏移量之后，都将委托列表相应的方法。addAll(Collection c)方法仅仅
     * 返回 addAll(size, c)。
     *
     * listIterator(int) 方法在原列表的迭代器上返回一个“包装器对象“，该对象是
     * 用原列表的相应方法创建的。iterator 方法只返回 listIterator() 方法， size()
     * 方法只返回子类的 size 字段。
     *
     * 所有方法首先检查原列表的实际 modCount 值是否等于期望值，如果不相等
     * 抛出 ConcurrentModificationException 异常
     *
     * @throws IndexOutOfBoundsException if an endpoint index value is out of range
     *         {@code (fromIndex < 0 || toIndex > size)}
     * @throws IllegalArgumentException if the endpoint indices are out of order
     *         {@code (fromIndex > toIndex)}
     */
    public List<E> subList(int fromIndex, int toIndex) {
        // 判断当前对象是否是 RandomAccess 的实例对象。
        return (this instanceof RandomAccess ?
                new RandomAccessSubList<>(this, fromIndex, toIndex) :
                new SubList<>(this, fromIndex, toIndex));
    }

    // Comparison and hashing
    // 比较和hash操作

    /**
     * @August equal函数的实现
     *
     * 比较指定的对象和该列表是否相等。如果指定的对象也是列表，两个列表大小
     * 相等，所有对应元素相等，那么返回 true (Two elements e1 and e2 are
     * equal if (e1==null ? e2==null : e1.equals(e2)).)。换句话说，如果两个列表
     * 包含相同元素且元素顺序相同，那么认为两个列表相等。
     *
     * 这个实现首先检查指定的对象是否是 this 列表，如果是返回 true；如果不是
     * 检查指定对象是否是列表对象，如果不是返回 false；然后遍历两个列表，
     * 比较对应元素是否相等，如果有任何一对不等则此函数返回 false（列表长度
     * 不等同样返回 false）；否则迭代完成后返回 true。
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    public boolean equals(Object o) {
        // 首先检查是否和自身相等
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        ListIterator<E> e1 = listIterator();
        ListIterator<?> e2 = ((List<?>) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            E o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1==null ? o2==null : o1.equals(o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    /**
     * 返回列表的 hash 值。
     *
     * 此实现使用的是hashCode方法中定义 list hash function 的代码。
     *
     * @return the hash code value for this list
     */
    public int hashCode() {
        int hashCode = 1;
        for (E e : this)
            hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
        return hashCode;
    }

    /**
     * 从该列表中删除索引位于 fromIndex 和 toIndex 之间的元素。将所有后续
     * 元素向左移动（减小索引）。这个调用删除 (toIndex - fromIndex) 个元素。
     * （如果 toIndex == fromIndex，此操作无效）。
     *
     * 这个方法由该列表和其子列表的 clear 操作调用。重写此方法，利用列表实现
     * 的内部机制，可以显著提升列表和子列表 clear 操作的性能。
     *
     * 这一实现获得位于 fromIndex 之前的列表迭代器，反复调用
     * ListIterator.next 和 ListIterator.remove，知道范围内所有元素被删除。
     * 注意：如果 ListIterator.remove 需要线性时间，那么这一实现需要二次
     * 时间。
     *
     * @param fromIndex index of first element to be removed
     * @param toIndex index after last element to be removed
     */
    protected void removeRange(int fromIndex, int toIndex) {
        ListIterator<E> it = listIterator(fromIndex);
        for (int i=0, n=toIndex-fromIndex; i<n; i++) {
            it.next();
            it.remove();
        }
    }

    /**
     * modCount 变量表示此列表在结构上被修改的次数。结构修改是指改变列表
     * 大小，或者以一种可能会产生错误结果的迭代扰乱列表内容。
     *
     * 这个字段在 iterator 和 listIterator 中使用。如果该字段的值发生意外改变，
     * 在 next, remove, previous, set, add 操作中， iterator （或者listIterator）
     * 会抛出ConcurrentModificationException异常。即提供了 fail-fast 行为，
     * 而不是迭代过程中发生并发修改的非确定性行为。
     *
     * 子类可以选择使用此字段。如果子类希望提供 fail-fast iterators
     * (and list iterators)，那么它只需要在 add(int, E) 和 remove(int)
     * （以及任何会导致列表结构性更改的方法）中增加这个字段。对add(int, E)
     * 或者 remove(int) 的单次调用必须只增加该字段一次，否则会抛出
     * ConcurrentModificationExceptions 异常。如果实现不希望提供 fail-fast
     * 行为，可以忽略该字段。
     */
    protected transient int modCount = 0;

    // 检查索引是否在数组范围内
    private void rangeCheckForAdd(int index) {
        if (index < 0 || index > size())
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size();
    }
}

class SubList<E> extends AbstractList<E> {
    private final AbstractList<E> l;
    private final int offset;
    private int size;

    // 构造函数，原列表为参数 list。
    // 只是指向原列表某一位置，子列表并没有申请新的内存空间。
    SubList(AbstractList<E> list, int fromIndex, int toIndex) {
        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > list.size())
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                    ") > toIndex(" + toIndex + ")");
        l = list;
        offset = fromIndex;
        size = toIndex - fromIndex;
        this.modCount = l.modCount;
    }

    public E set(int index, E element) {
        rangeCheck(index);
        checkForComodification();
        return l.set(index+offset, element);
    }

    public E get(int index) {
        rangeCheck(index);
        checkForComodification();
        return l.get(index+offset);
    }

    public int size() {
        checkForComodification();
        return size;
    }

    public void add(int index, E element) {
        rangeCheckForAdd(index);
        checkForComodification();
        l.add(index+offset, element);
        this.modCount = l.modCount;
        size++;
    }

    public E remove(int index) {
        rangeCheck(index);
        checkForComodification();
        E result = l.remove(index+offset);
        this.modCount = l.modCount;
        size--;
        return result;
    }

    protected void removeRange(int fromIndex, int toIndex) {
        checkForComodification();
        l.removeRange(fromIndex+offset, toIndex+offset);
        this.modCount = l.modCount;
        size -= (toIndex-fromIndex);
    }

    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        rangeCheckForAdd(index);
        int cSize = c.size();
        if (cSize==0)
            return false;

        checkForComodification();
        l.addAll(offset+index, c);
        this.modCount = l.modCount;
        size += cSize;
        return true;
    }

    public Iterator<E> iterator() {
        return listIterator();
    }

    public ListIterator<E> listIterator(final int index) {
        checkForComodification();
        rangeCheckForAdd(index);

        return new ListIterator<E>() {
            private final ListIterator<E> i = l.listIterator(index+offset);

            public boolean hasNext() {
                return nextIndex() < size;
            }

            public E next() {
                if (hasNext())
                    return i.next();
                else
                    throw new NoSuchElementException();
            }

            public boolean hasPrevious() {
                return previousIndex() >= 0;
            }

            public E previous() {
                if (hasPrevious())
                    return i.previous();
                else
                    throw new NoSuchElementException();
            }

            public int nextIndex() {
                return i.nextIndex() - offset;
            }

            public int previousIndex() {
                return i.previousIndex() - offset;
            }

            public void remove() {
                i.remove();
                // 注意：内部类中要在 this 前面加上外部类的类名。
                SubList.this.modCount = l.modCount;
                size--;
            }

            public void set(E e) {
                i.set(e);
            }

            public void add(E e) {
                i.add(e);
                SubList.this.modCount = l.modCount;
                size++;
            }
        };
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return new SubList<>(this, fromIndex, toIndex);
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void rangeCheckForAdd(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }

    private void checkForComodification() {
        if (this.modCount != l.modCount)
            throw new ConcurrentModificationException();
    }
}

// RandomAccess(since 1.4) 是一个标记接口，用于标明实现该接口的List支持快速随机
// 访问，主要目的是使算法能够在随机和顺序访问的list中表现的更加高效。
class RandomAccessSubList<E> extends SubList<E> implements RandomAccess {
    RandomAccessSubList(AbstractList<E> list, int fromIndex, int toIndex) {
        super(list, fromIndex, toIndex);
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return new RandomAccessSubList<>(this, fromIndex, toIndex);
    }
}
