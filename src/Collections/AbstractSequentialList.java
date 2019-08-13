package Collections;

import java.util.*;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;

/**
 * 这个类提供了一个 List 接口的框架实现，以最小化实现由“顺序访问”
 * 数据存储（例如链表）支持的此接口所需工作。对于随机访问数据结构
 * （例如数组），应该优先使用 AbstractList。
 *
 *
 * 这个类与 AbstractList 类相反，它实现了列表迭代器顶部的随机访问
 * 方法 (get(int index), set(int index, E element),
 * add(int index, E element) and remove(int index))。
 *
 * 要实现此列表接口，程序员只需要扩展这个类并为 listIterator 和 size
 * 方法提供实现。对于不可修改列表，程序员只需要实现列表 iterator 的
 * hasNext, next, hasPrevious, previous and index 方法。
 *
 * 对于可修改的列表，程序员应该实现列表 iterator 的 set 方法。对于
 * 可变大小的列表，程序员应该额外实现列表 iterator 的 remove 和 add
 * 方法。
 *
 * 按照 Collection 接口规范中的建议，程序员通常应该提供一个
 * void（无参数）构造函数。
 *
 * 此类是 Java Collections Framework 的成员。
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see java.util.Collection
 * @see List
 * @see java.util.AbstractList
 * @see AbstractCollection
 * @since 1.2
 */

// Sequential 相继的，按次序的
public abstract class AbstractSequentialList<E> extends AbstractList<E> {
    /**
     * 唯一的构造函数。（用于子类构造函数调用，通常是隐式的）
     */
    protected AbstractSequentialList() {
    }

    /**
     * 返回列表中指定位置的元素。
     *
     * 此实现首先用 listIterator(index) 得到从指定索引位置开始的迭代器，
     * 然后使用 ListIterator.next 方法得到该元素并返回。
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        try {
            return listIterator(index).next();
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }

    /**
     * 用指定元素替换列表中指定位置的元素（可选操作）。
     *
     * 此实现首先用 listIterator(index) 得到从指定索引位置开始的迭代器，
     * 然后使用 ListIterator.next 方法得到该元素，使用 ListIterator.set
     * 设置该位置的新值并返回旧值。
     *
     * 注意如果列表迭代器没有实现 set 方法，会抛出
     * UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E set(int index, E element) {
        try {
            ListIterator<E> e = listIterator(index);
            E oldVal = e.next();
            e.set(element);
            return oldVal;
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }

    /**
     * 在指定位置插入指定元素（可选操作）。把当前位置及其之后的元素
     * 向右移动一位（索引加一）。
     *
     * 此实现首先用 listIterator(index) 得到从指定索引位置开始的迭代器，
     * 然后使用 ListIterator.add 方法插入指定元素。
     *
     * 注意如果列表迭代器没有实现 add 方法会抛出
     * UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public void add(int index, E element) {
        try {
            listIterator(index).add(element);
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }

    /**
     * 移除列表指定位置的元素（可选操作）。把所有后续元素向左移动一位
     * （索引减一）。返回从列表中移除的元素。
     *
     * 此实现首先用 listIterator(index) 得到从指定索引位置开始的迭代器，
     * 然后使用 ListIterator.remove 方法删除指定元素。
     *
     * 注意如果列表迭代器没有实现 remove 方法会抛出
     * UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public E remove(int index) {
        try {
            ListIterator<E> e = listIterator(index);
            E outCast = e.next();
            e.remove();
            return outCast;
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }


    // Bulk Operations
    // 批量操作

    /**
     * 将指定集合中的所有元素插入到列表中的指定位置（可选操作）。把
     * 当前位置及之后的元素向右移动（增加索引）。插入的顺序为指定
     * 集合迭代器返回的顺序。如果此操作进行的过程中指定集合被修改，
     * 那么此操作的行为未知。（注意如果执行集合是 this 列表，且不为空，
     * 就会发生这种情况。）
     *
     * 此实现首先得到指定集合的迭代器，以及使用 listIterator(index)
     * 得到列表从指定索引开始的列表迭代器。然后对指定集合进行迭代，
     * 使用 ListIterator.next（跳过刚插入的元素） 和 ListIterator.add
     * 将元素依次插入到列表集合中。
     *
     * 如果返回的列表迭代器没有实现 add 方法，抛出
     * UnsupportedOperationException 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IndexOutOfBoundsException     {@inheritDoc}
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        try {
            boolean modified = false;
            ListIterator<E> e1 = listIterator(index);
            Iterator<? extends E> e2 = c.iterator();
            while (e2.hasNext()) {
                e1.add(e2.next());
                modified = true;
            }
            return modified;
        } catch (NoSuchElementException exc) {
            throw new IndexOutOfBoundsException("Index: "+index);
        }
    }


    // Iterators
    // 迭代器

    /**
     * 返回列表迭代器（按正确的顺序）
     *
     * @return an iterator over the elements in this list (in proper sequence)
     */
    public Iterator<E> iterator() {
        return listIterator();
    }

    /**
     * 返回列表迭代器（按正确的顺序）
     *
     * @param  index index of first element to be returned from the list
     *         iterator (by a call to the next method)
     * @return a list iterator over the elements in this list (in proper
     *         sequence)
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public abstract ListIterator<E> listIterator(int index);
}

