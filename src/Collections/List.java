package Collections;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 一个有序集合（也成为了序列）。这一个接口的使用者可以精确地控制
 * 列表中每一个元素插入的位置。用户可以通过整数索引（列表中的位置）
 * 访问元素，并在列表中搜索元素。
 *
 * 与集合不同的是，列表通常允许重复元素。更一般的时，列表通常允许
 * 两个元素e1和e2存在e1.equals(e2)的关系。如果允许空元素存在的话，
 * 列表会允许多个空元素存在。通过在用户插入重复元素时抛出异常，可以
 * 防止插入重复元素，但是我们希望少用这一做法。
 *
 *
 * 比起Collection接口，List接口在 iterator, add, remove, equals, 和
 * hashCode方法的约束下，添加了更多规定。这里还包含了其它继承方法
 * 的声明。
 *
 * List接口提供了四种方法对列表元素进行位置（索引）的访问。
 * Lists (like Java arrays) are zero based.
 * 注意这些操作在某些实现（例如LinkedList类）中的执行时间会和索引值
 * 成比例。因此，在调用者不知道实现方式的情况下，通过迭代遍历列表中
 * 的元素比索引遍历更可取。
 *
 * List接口提供了一个特殊的迭代器，叫ListIterator。除了迭代器接口
 * 提供的常规操作外，它还允许插入，替换和双向访问。此集合中提供了
 * 一个方法来获取从列表中执行位置开始的列表迭代器。
 *
 * List接口提供了两个方法来搜索指定的对象。从性能的角度看，这些方法
 * 应该谨慎使用。在许多实现中，它们将执行代价高昂的线性搜索。
 *
 * List接口提供了两个方法来有效地插入和删除列表中任意点上的多个元
 * 素。
 *
 * 注意：虽然列表中允许将自己作为元素，但是要特别注意：equal和
 * hashCode方法在这样的列表中不再定义。
 *
 * 有些列表的实现可能对包含的元素有限制。例如，一些实现禁止空元素，
 * 一些对元素类型有限制。试图插入非法元素会抛出未确认的异常，特别
 * 是NullPointerException和ClassCastException。试图查询非法元素可能
 * 会抛出异常，或者会只返回false；有些实现会显示前者，有些是后者。
 * 更一般地说，在一个非法元素上尝试操作时，其结果可能是元素没有插入
 * list中，也可能插入成功，这取决于其list的实现。
 *
 * 此接口是Java Collections Framework的成员
 *
 * @param <E> the type of elements in this list
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see Collection
 * @see Set
 * @see ArrayList
 * @see LinkedList
 * @see Vector
 * @see Arrays#asList(Object[])
 * @see Collections#nCopies(int, Object)
 * @see Collections#EMPTY_LIST
 * @see AbstractList
 * @see AbstractSequentialList
 * @since 1.2
 */

public interface List<E> extends Collection<E> {
    // Query Operations
    //查询操作

    /**
     * 返回列表中元素个数，如果元素个数大于Integer.MAX_VALUE，则
     * 返回Integer.MAX_VALUE。
     *
     * @return the number of elements in this list
     */
    int size();

    /**
     * 如果列表为空返回true。
     *
     * @return <tt>true</tt> if this list contains no elements
     */
    boolean isEmpty();

    /**
     * 判断是否包含对象o
     *
     * @param o element whose presence in this list is to be tested
     * @return <tt>true</tt> if this list contains the specified element
     * @throws ClassCastException if the type of the specified element
     *         is incompatible with this list
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements
     * (<a href="Collection.html#optional-restrictions">optional</a>)
     */
    boolean contains(Object o);

    /**
     * 返回正确顺序的列表迭代器。
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    Iterator<E> iterator();

    /**
     * 返回包含列表所有元素正确顺序的数组（从第一个到最后一个元素）。
     *
     * 返回的数组是“安全”的，因为list没有对它的引用。（换句话说，即使
     * list的后台由数组存储，这个方法仍然会分配新的数组空间，并返回新
     * 的数组。）因此调用者可以随意修改返回的数组。
     *
     * @return an array containing all of the elements in this list in proper
     *         sequence
     * @see Arrays#asList(Object[])
     */
    Object[] toArray();

    /**
     * 返回包含列表所有元素正确顺序的数组（从第一个到最后一个元素）；
     * 返回数组的运行时类型和参数数组一致。如果list和其后台数组一致，
     * 则返回list后台数组，否则生成一个新的和参数雷子那个一样的数组，
     * 大小和list一致。（和toArray()不同，不一定生成新的数组。）
     *
     * 如果list后台数组由空闲空间（数组比list由更多元素），数组中的元
     * 素在列表结束后马上设为null。（只要调用者确定list中不包含null元
     * 素，这一放飞在求list长度上时有用的。
     *
     * 和toArray()方法类似，这一方法充当了数组和集合api的桥梁。进一
     * 步讲，这一方法在运行时输出数组类型上有着精确的控制，那么也许
     * 在一定的场景下，可以被用来减少内存损耗。
     *
     * 假设list中只存储了String类型元素。下面的代码能够被用作：将list
     * 中元素放入一个新生成的String[]数组中。
     * String[] y = x.toArray(new String[0]);
     *
     * 请注意，toArray(new Object [0]) 在功能上与 toArray() 相同。
     *
     * @August 如果数组a足够大，那么list中的元素直接存在a中，否则
     * 存储在新开辟的空间中。
     * @param a the array into which the elements of this list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of this list
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this list
     * @throws NullPointerException if the specified array is null
     */
    <T> T[] toArray(T[] a);


    // Modification Operations
    // 修改操作

    /**
     * 在list末尾添加元素（可选操作）
     *
     * 支持这一操作的List在插入何种类型的元素时会进行限制。特别是，
     * 一些list会拒绝加入null元素，其他的list会对加入的元素施加限制。
     * List类应该在它们的文档中清晰地表达它们对将添加元素的限制。
     *
     * @param e element to be appended to this list
     * @return true (as specified by {@link Collection#add})
     * @throws UnsupportedOperationException if the add operation
     *         is not supported by this list
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     *         prevents it from being added to this list
     */
    boolean add(E e);

    /**
     * 删除列表中首次出现的指定元素，如果不存在不进行任何操作。更一
     * 般地说，删除满足以下条件的索引值最小的元素：
     * (o==null?get(i)==null:o.equals(get(i)))，如果该元素存在的话。如果
     * 该元素存在的话返回true。
     *
     * @param o element to be removed from this list, if present
     * @return true if this list contained the specified element
     * @throws ClassCastException if the type of the specified element
     *         is incompatible with this list (optional)
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements (optional)
     * @throws UnsupportedOperationException if the remove operation
     *         is not supported by this list
     */
    boolean remove(Object o);


    // Bulk Modification Operations
    // 块修改操作

    /**
     * 如果list中包含指定Collection中的全部元素，则返回true。
     *
     * @param  c collection to be checked for containment in this list
     * @return true if this list contains all of the elements of the
     *         specified collection
     * @throws ClassCastException if the types of one or more elements
     *         in the specified collection are incompatible with this
     *         list (optional)
     * @throws NullPointerException if the specified collection contains one
     *         or more null elements and this list does not permit null
     *         elements (optional), or if the specified collection is null
     * @see #contains(Object)
     */
    boolean containsAll(Collection<?> c);

    /**
     * 将作为参数的Collection中的所有元素添加到list末尾。按照迭代器的
     * 返回顺序添加（可选操作）。若此操作在执行过程中参数集合被其他
     * 线程修改，会出现什么影响未定义。
     *
     * @param c collection containing elements to be added to this list
     * @return true if this list changed as a result of the call
     * @throws UnsupportedOperationException if the addAll operation
     *         is not supported by this list
     * @throws ClassCastException if the class of an element of the specified
     *         collection prevents it from being added to this list
     * @throws NullPointerException if the specified collection contains one
     *         or more null elements and this list does not permit null
     *         elements, or if the specified collection is null
     * @throws IllegalArgumentException if some property of an element of the
     *         specified collection prevents it from being added to this list
     * @see #add(Object)
     */
    boolean addAll(Collection<? extends E> c);

    /**
     * 将参数集合中的所有元素插入到指定位置（可选操作）。将原来位置
     * 及其之后的元素后移（索引相应增加）。按照迭代器的返回顺序添加。
     * 若此操作在执行过程中参数集合被其他线程修改，会出现什么影响未
     * 定义。
     *
     * @param index index at which to insert the first element from the
     *              specified collection
     * @param c collection containing elements to be added to this list
     * @return true if this list changed as a result of the call
     * @throws UnsupportedOperationException if the addAll operation
     *         is not supported by this list
     * @throws ClassCastException if the class of an element of the specified
     *         collection prevents it from being added to this list
     * @throws NullPointerException if the specified collection contains one
     *         or more null elements and this list does not permit null
     *         elements, or if the specified collection is null
     * @throws IllegalArgumentException if some property of an element of the
     *         specified collection prevents it from being added to this list
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    boolean addAll(int index, Collection<? extends E> c);

    /**
     * 删除list中和参数集合相同的元素
     *
     * @param c collection containing elements to be removed from this list
     * @return true if this list changed as a result of the call
     * @throws UnsupportedOperationException if the removeAll operation
     *         is not supported by this list
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     * @see #contains(Object)
     */
    boolean removeAll(Collection<?> c);

    /**
     * 删除list中和参数集合不相同的元素（可选操作）
     *
     * @param c collection containing elements to be retained in this list
     * @return true if this list changed as a result of the call
     * @throws UnsupportedOperationException if the retainAll operation
     *         is not supported by this list
     * @throws ClassCastException if the class of an element of this list
     *         is incompatible with the specified collection (optional)
     * @throws NullPointerException if this list contains a null element and the
     *         specified collection does not permit null elements (optional),
     *         or if the specified collection is null
     * @see #remove(Object)
     * @see #contains(Object)
     */
    boolean retainAll(Collection<?> c);

    /**
     * 用函数接口的返回结果替代原list的值
     *
     * 如果list的迭代器不支持set操作，在替换第一个元素的时候会抛出
     * UnsupportedOperationException异常
     *
     * @param operator the operator to apply to each element
     * @throws UnsupportedOperationException if this list is unmodifiable.
     *         Implementations may throw this exception if an element
     *         cannot be replaced or if, in general, modification is not
     *         supported
     * @throws NullPointerException if the specified operator is null or
     *         if the operator result is a null value and this list does
     *         not permit null elements (optional)
     * @since 1.8
     */
    default void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        final ListIterator<E> li = this.listIterator();
        while (li.hasNext()) {
            li.set(operator.apply(li.next()));
        }
    }

    /**
     * 根据给定的Comparator对list中的元素排序
     *
     * 列表中的所有元素都必须是可比较的。（即对于列表中任意两个元素
     * e1和e2，使用c.compare(e1, e2)不能抛出ClassCastException异常
     *
     * 如果指定的comparator为空，那么list中所有元素必须实现Comparable
     * 接口，然后元素会自然排序。
     *
     * list必须能进行修改，对于扩容或缩减则不是必须的
     *
     * @implSpec
     * 这一方法默认的实现是：获取包含list所有元素的数组，对数组排序，
     * 并通过遍历重置该列表相应位置的每个元素。（这避免了对链表排序
     * 的性能消耗）
     *
     * @implNote
     * 该方法的实现采用稳定，自适应，可迭代的归并排序，当数组元素部分
     * 有序的时候其执行时间远远低于n*log(n)，若输入元素顺序随机，则时
     * 间代价和传统的归并排序基本一致。如果输入的书序基本有序，本方法
     * 大概需要n次比较。临时存储空间最小需要需要常量级别，此时输入的
     * 数组基本有序，最大为输入数组的1/2。
     *
     * 输入数组是升序还是降序对方法执行的影响都一样。对于同一个输入
     * 数组的不同部分，可以利用升序和降序的优势。这很适合两个到多个
     * 有序数组的合并：对数组进行简单的连接，并对结果数组进行排序。
     *
     * @param c the {@code Comparator} used to compare list elements.
     *          A {@code null} value indicates that the elements'
     *          {@linkplain Comparable natural ordering} should be used
     * @throws ClassCastException if the list contains elements that are not
     *         mutually comparable using the specified comparator
     * @throws UnsupportedOperationException if the list's list-iterator does
     *         not support the {@code set} operation
     * @throws IllegalArgumentException (optional)
     *         if the comparator is found to violate the {@link Comparator}
     *         contract
     * @since 1.8
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default void sort(Comparator<? super E> c) {
        // 将list转化成数组
        Object[] a = this.toArray();
        // 根据比较器对数组排序
        Arrays.sort(a, (Comparator) c);
        // 获取迭代器，并按顺序重置排序过后的元素列表
        ListIterator<E> i = this.listIterator();
        for (Object e : a) {
            i.next();
            i.set((E) e);
        }
    }

    /**
     * 删除列表中所有元素（可选操作）。方法返回之后列表为空。
     *
     * @throws UnsupportedOperationException if the clear operation
     *         is not supported by this list
     */
    void clear();


    // Comparison and hashing
    // 比较和hash操作

    /**
     * 比较参数对象和list的等价性。当满足o的类型为list，二者的size相等，
     * list中对应元素相等（当 (e1==null ? e2==null : e1.equals(e2)) 时称两
     * 元素相等）时，返回true。换句话说，如果两个列表中元素对应相等，
     * 那么两个列表相等。这一定义确保接口的不同实现里equals函数都能
     * 正常工作。
     *
     * @param o the object to be compared for equality with this list
     * @return true if the specified object is equal to this list
     */
    boolean equals(Object o);

    /**
     * 返回list的hash值。hash值得计算方法如下：
     * {@code
     *     int hashCode = 1;
     *     for (E e : list)
     *         hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
     * }
     * 这一定义确保了list1.equals(list2)时，list1和list2的hash值一致。
     *
     * @return the hash code value for this list
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    int hashCode();


    // Positional Access Operations
    // 位置访问操作

    /**
     * 返回list指定位置的元素。
     *
     * @param index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index < 0 || index >= size())
     */
    E get(int index);

    /**
     * 把列表中索引为index的元素替换为element。
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws UnsupportedOperationException if the set operation
     *         is not supported by this list
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list
     * @throws NullPointerException if the specified element is null and
     *         this list does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this list
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index < 0 || index >= size())
     */
    E set(int index, E element);

    /**
     * 把元素element插入到索引为index的位置，原index元素和其后面的
     * 元素依次向后移动。
     *
     * @param index index at which the specified element is to be inserted
     * @param element element to be inserted
     * @throws UnsupportedOperationException if the add operation
     *         is not supported by this list
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this list
     * @throws NullPointerException if the specified element is null and
     *         this list does not permit null elements
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this list
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index < 0 || index >= size())
     */
    void add(int index, E element);

    /**
     * 删除index位置的元素，将其后的所有元素依次向前移动。返回值为被
     * 删除的元素。
     *
     * @param index the index of the element to be removed
     * @return the element previously at the specified position
     * @throws UnsupportedOperationException if the remove operation
     *         is not supported by this list
     * @throws IndexOutOfBoundsException if the index is out of range
     *         (index < 0 || index >= size())
     */
    E remove(int index);


    // Search Operations
    // 查找操作

    /**
     * 返回列表中元素o第一次出现位置的索引。若不存在返回-1。
     *
     * @param o element to search for
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     * @throws ClassCastException if the type of the specified element
     *         is incompatible with this list (optional)
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements (optional)
     */
    int indexOf(Object o);

    /**
     * 返回列表中元素o最后一次出现位置的索引。若不存在返回-1。
     *
     * @param o element to search for
     * @return the index of the last occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     * @throws ClassCastException if the type of the specified element
     *         is incompatible with this list (optional)
     * @throws NullPointerException if the specified element is null and this
     *         list does not permit null elements (optional)
     */
    int lastIndexOf(Object o);


    // List Iterators
    // list迭代器

    /**
     * 返回按序迭代器
     *
     * @return a list iterator over the elements in this list (in proper
     *         sequence)
     */
    ListIterator<E> listIterator();

    /**
     * 返回从指定位置开始的按序迭代器。
     *
     * @param index index of the first element to be returned from the
     *        list iterator (by a call to {@link ListIterator#next next})
     * @return a list iterator over the elements in this list (in proper
     *         sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     */
    ListIterator<E> listIterator(int index);

    // View
    // 视图

    /**
     * 返回list中指定范围 [fromIndex, toIndex] 内的元素。（如果
     * formIndex和toIndex相等，则返回的list为空。返回的list由原list作
     * 为支撑，所以返回list中的非结构性更改都讲反映在此列表中，反之
     * 亦然。返回list支持原list支持的所有list相关操作。
     *
     * 此方法消除了显式范围内操作（数组通常存在的那种操作）的需要。
     * 任何需要列表的操作都可以通过传递子列表视图而不是整个列表来作为
     * 操作的范围。例如，下面的语句从列表中删除了一段范围内的元素：
     * list.subList(from, to).clear()
     * indexOf方法和lastIndexOf方法同理。在Collection类中所有的算法
     * 都可用在subList中。
     *
     * 如果后备list发生了结构更改，而这种更改并不是来自于返回的子list，
     * 那么此方法返回的list的的语义未可知。（结构性更改指的是诸如改变
     * 列表大小，或者以其他方式扰乱它，以至于正在进行的迭代返回错误
     * 结果。）
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @param toIndex high endpoint (exclusive) of the subList
     * @return a view of the specified range within this list
     * @throws IndexOutOfBoundsException for an illegal endpoint index value
     *         (fromIndex < 0 || toIndex > size || fromIndex > toIndex)
     */
    List<E> subList(int fromIndex, int toIndex);

    /**
     * 基于list中的元素创建一个Spliterator
     *
     * Spliterator有两个属性值：SIZED 和 ORDERED，接口的实现如果
     * 添加了其他属性值，则应该在文档中说明。
     *
     * @implSpec
     * 默认实现创建了一个基于Iterator，但后期进行了部分元素绑定的
     * 迭代器。(因为并行分隔迭代器，同一时刻几个元素都会进行遍历，
     * 所以称之为绑定,而原迭代器是顺序遍历元素)
     *
     * @implNote
     * 创建的并行分隔迭代器添加了新的属性值: SUBSIZED
     *
     * @return a {@code Spliterator} over the elements in this list
     * @since 1.8
     */
    @Override
    default Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.ORDERED);
    }
}
