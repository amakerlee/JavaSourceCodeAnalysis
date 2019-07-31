package Collections;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

/**
 * 本类提供Collection接口的基本实现，以最小化此接口所需工作。
 *
 * 要实现不可修改的collection，程序员只需要扩展该类并且为iterator和
 * size方法提供方法的实现。（iterator方法返回的迭代器必须实现
 * hasNext和next。）
 *
 * 要实现可修改的collection，程序员还必须重写add方法（否则会抛出
 * UnsupportedOperationException异常），iterator方法返回的迭代器
 * 必须实现remove方法。
 *
 * 程序员必须提供一个void类型的Collection构造函数
 *
 * 本类中每一个非抽象的方法的文档详细描述了其实现。如果正在被实现的
 * 集合允许更有效的实现，这些方法都必须被重写。
 *
 * 本类是Java Collections Framework的成员.
 *
 * @author  Josh Bloch
 * @author  Neal Gafter
 * @see Collection
 * @since 1.2
 */

public abstract class AbstractCollection<E> implements Collection<E> {
    /**
     * 唯一的构造函数.。（用于子类构造函数调用，通常是隐式的。）
     */
    protected AbstractCollection() {
    }

    // Query Operations
    // 查询操作

    /**
     * 返回一个包含集合中每个元素的迭代器
     *
     * @August 继承的父类是抽象类时，需要将抽象类中所有的抽象方法
     * 都实现。
     *
     * @return an iterator over the elements contained in this collection
     */
    public abstract Iterator<E> iterator();

    // 获取集合中元素个数
    public abstract int size();

    /**
     *
     * size()返回值为0时，集合为空，返回true。
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     *
     * 实现方法：遍历集合，依次检查每个元素是否与参数对象相等。
     *
     * @August 判断是否相等的方法为equals，不是等号。
     * @August 必须对null单独处理否则null.equals会报空指针异常
     *
     * @throws ClassCastException
     * @throws NullPointerException
     */
    public boolean contains(Object o) {
        Iterator<E> it = iterator();
        if (o==null) {
            while (it.hasNext())
                if (it.next()==null)
                    return true;
        } else {
            while (it.hasNext())
                if (o.equals(it.next()))
                    return true;
        }
        return false;
    }

    /**
     * 此实现返回一个数组，其中包含此集合的迭代器按序返回的所有元素，
     * 存储在数组的连续元素中，从索引0开始。返回数组的长度等于迭代器
     * 返回的元素的个数，即使这个集合的大小在迭代期间发生了变化，即
     * 集合允许在迭代期间并发修改时可能发生的那样。size方法仅作为优化
     * 提示调用；即使迭代器返回的元素个数不同，此方法也会返回正确的
     * 结果。
     *
     * 此方法等价于：
     *   {@code
     * List<E> list = new ArrayList<E>(size());
     * for (E e : this)
     *     list.add(e);
     * return list.toArray();
     * }
     */
    public Object[] toArray() {
        // 创建一个数组，大小为集合中元素的数量
        Object[] r = new Object[size()];
        // 通过迭代器遍历集合，将当前集合中的元素复制到数组中（复制
        // 引用）
        Iterator<E> it = iterator();
        for (int i = 0; i < r.length; i++) {
            // 集合中元素比预期的少，调用Arrays.copyOf()方法将数组的元
            // 素复制到新数组，新数组大小为i。

            // Arrays的copyOf()方法传回的数组是新的数组对象，改变传回数组中的
            // 元素值，不会影响原来的数组。copyOf()的第二个自变量指定要建立的
            // 新数组长度，如果新数组的长度超过原数组的长度，则保留数组默认值0
            if (! it.hasNext())
                return Arrays.copyOf(r, i);
            r[i] = it.next();
        }
        // 集合中元素比预期的多，调用finishToArray()方法生成新数组。
        // 集合中元素和预期一样，返回r。
        return it.hasNext() ? finishToArray(r, it) : r;
    }

    /**
     * {@inheritDoc}
     * 此实现返回一个数组，其中包含该集合的迭代器以相同的顺序返回的所有
     * 元素。这些元素储存在数组的连续空间中，从索引0开始。如果迭代器返回的
     * 元素数量太大，不能完全存入指定数组，那么新分配一个长度等于迭代器返回
     * 元素数量的数组，将元素存入其中。若迭代过程中允许并发修改，那么迭代
     * 过程中集合大小可能发生变化。size方法仅作为优化提示调用；即使迭代器
     * 返回的元素个数不同，此方法也会返回正确的结果。
     *
     * 此方法等价于：
     *
     * {@code
     * List<E> list = new ArrayList<E>(size());
     * for (E e : this)
     *     list.add(e);
     * return list.toArray(a);
     * }
     *
     * @throws ArrayStoreException  {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        // Estimate size of array; be prepared to see more or fewer elements
        int size = size();
        // 如果数组a长度大于list长度，则r指向a，否则，创建一个具有指定组件
        // 类型和长度的新数组，r指向该新数组。
        T[] r = a.length >= size ? a :
                (T[])java.lang.reflect.Array
                        .newInstance(a.getClass().getComponentType(), size);
        Iterator<E> it = iterator();

        // 集合元素索引小于数组长度
        for (int i = 0; i < r.length; i++) {
            if (! it.hasNext()) { // fewer elements than expected
                // 此处涉及到r是否指向a和元素是否能全部存入a中，如果全部存在a
                // 中，当r指向a时直接返回a，r不指向a时将r中的元素复制到a再返
                // 回a。如果不能全部存在a中，那么返回新分配的数组空间。

                // 如果数组是a，则将数组剩下的空间设为空（存在数组a中）
                if (a == r) {
                    r[i] = null;
                }
                // 如果数组a的长度小于集合长度，将之前数组中的元素复制到新数组
                // 中，并返回（存在新分配的空间中）
                else if (a.length < i) {
                    return Arrays.copyOf(r, i);
                }
                //public static void arraycopy(Object src, int srcPos,
                // Object dest, int destPos, int length)（存在a中）
                else {
                    System.arraycopy(r, 0, a, 0, i);
                    if (a.length > i) {
                        a[i] = null;
                    }
                }
                return a;
            }
            r[i] = (T)it.next();
        }
        // 集合元素索引大于数组长度
        return it.hasNext() ? finishToArray(r, it) : r;
    }

    /**
     * 要分配数组的最大长度。
     * 一些虚拟机在数组中保留一些头信息。
     * 试图分配更大的数组可能会导致OutOfMemoryError:请求的数组大小超过
     * 虚拟机限制
     *
     * @August 在数组的对象头里有一个_length字段，记录数组长度，只需要去
     * 读_length字段就可以了。ArrayList中定义的最大长度为Integer最大值减8，
     * 这个8就是就是存了数组_length字段。
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 当迭代器返回比预期更多的元素时，重新分配toArray中使用的数组，并从
     * 迭代器中取出元素完成填充。
     *
     * @param r the array, replete with previously stored elements
     * @param it the in-progress iterator over this collection
     * @return array containing the elements in the given array, plus any
     *         further elements returned by the iterator, trimmed to size
     */
    @SuppressWarnings("unchecked")
    private static <T> T[] finishToArray(T[] r, Iterator<?> it) {
        // 注意迭代器是从上层方法传过来的，不是从头开始迭代
        int i = r.length;
        while (it.hasNext()) {
            int cap = r.length;
            // 达到数组最大容量，开始扩容
            if (i == cap) {
                int newCap = cap + (cap >> 1) + 1;
                // 扩容后长度newCap，如果newCap大于MAX_ARRAY_SIZE，那么
                // 设置数组长度为Integer.MAX_VALUE
                if (newCap - MAX_ARRAY_SIZE > 0)
                    newCap = hugeCapacity(cap + 1);
                r = Arrays.copyOf(r, newCap);
            }
            r[i++] = (T)it.next();
        }
        // trim if overallocated
        return (i == r.length) ? r : Arrays.copyOf(r, i);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError
                    ("Required array size too large");
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    // Modification Operations
    // 修改操作

    /**
     * {@inheritDoc}
     *
     * 未实现的add操作。抛出UnsupportedOperationException异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IllegalStateException         {@inheritDoc}
     */
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * 此实现遍历集合寻找指定的元素。如果找到则使用迭代器的remove方法从
     * 集合中删除元素。
     *
     * 注意如果这个集合包含指定元素但迭代器中没有实现remove方法，那么
     * 将抛出UnsupportedOperationException异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     */
    public boolean remove(Object o) {
        Iterator<E> it = iterator();
        // 如果要删除的元素为空，那么找到第一个null元素，删除并返回true
        if (o==null) {
            while (it.hasNext()) {
                if (it.next()==null) {
                    it.remove();
                    return true;
                }
            }
        }
        // 否则使用equals方法返回第一个匹配的元素，删除并返回true。
        else {
            while (it.hasNext()) {
                if (o.equals(it.next())) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }


    // Bulk Operations
    // 批量操作

    /**
     * {@inheritDoc}
     *
     * 遍历指定集合，检查是否所有元素都在本集合中，如果都在返回true，否则
     * 返回false。
     *
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
        for (Object e : c)
            if (!contains(e))
                return false;
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * 这个方法遍历指定集合，把指定集合迭代器返回的所有元素一次添加到本集
     * 合中。
     *
     * 注意如果add方法没有被重写，那么将会抛出
     * UnsupportedOperationException异常。（假设指定集合不为空）
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     * @throws IllegalStateException         {@inheritDoc}
     *
     * @see #add(Object)
     */
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c)
            if (add(e))
                modified = true;
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * 这个方法遍历本集合，依次检查迭代器中的每一个元素是否包含在指定集合
     * 里。如果包含在指定集合里，使用迭代器的remove方法把它从本集合中删除。
     *
     * 注意迭代器中没有实现remove方法，并且这个集合包含和指定集合相等的
     * 一个或多个元素，那么这个实现将会抛出UnsupportedOperationException
     * 异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        boolean modified = false;
        Iterator<?> it = iterator();
        while (it.hasNext()) {
            if (c.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * @August 求参数集合与当前集合的交集
     *
     * 这个方法遍历本集合，依次检查当前集合的迭代器返回的每个元素，是否
     * 包含在指定集合中。如果不包含（removeAll方法中是包含即删除），使用
     * 迭代器的remove方法从本集合中删除它。
     *
     * 注意，如果iterator方法返回的迭代器没有实现remove方法，并且这个集合
     * 包含指定集合中不存在的一个或多个元素，那么这个实现将抛出一个
     * UnsupportedOperationException异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        boolean modified = false;
        Iterator<E> it = iterator();
        while (it.hasNext()) {
            if (!c.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * 此实现遍历本集合，使用迭代器的remove方法删除每个元素。为了提高效率，
     * 大多数实现会重写这个操作。
     *
     * 注意，如果这个集合的迭代器方法返回的迭代器没有实现remove方法，并且
     * 这个集合是非空的，那么这个实现将抛出一个
     * UnsupportedOperationException异常。
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     */
    public void clear() {
        Iterator<E> it = iterator();
        while (it.hasNext()) {
            // remove 将会删除上次调用 next 时返回的元素。先调用next再调用
            // remove 才会删除元素。next 和 remove 方法具有依赖性，必须先用
            // next，再使用remove。如果先用remove方法会抛出
            // IllegalStateException异常。
            it.next();
            it.remove();
        }
    }


    //  String conversion

    /**
     * 返回此集合的字符串表示形式。字符串表示形式由迭代器按序返回的所有元素
     * 组成，这些元素用方括号 ("[]") 括起来。相邻元素用字符 ", " （逗号和空格）
     * 隔开。元素通过String.valueOf(Object)转换为字符串。
     *
     * @return a string representation of this collection
     */
    public String toString() {
        Iterator<E> it = iterator();
        // 若集合为空返回字符串"[]"
        if (! it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            E e = it.next();
            sb.append(e == this ? "(this Collection)" : e);
            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }

}
