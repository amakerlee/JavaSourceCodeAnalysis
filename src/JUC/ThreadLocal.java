package JUC;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 此类提供线程的局部变量。这些变量和普通的对应变量不同，每个访问变量的
 * 线程（通过 get 或 set）都有自己独立初始化的变量副本。ThreadLocal 实例
 * 通常是希望将状态和线程关联起来的类中的私有静态字段。
 *
 * 例如，下面的类生成每个线程的唯一标识符。
 * 线程的 id 在第一次调用 ThreadId.get 时被赋值，并且在随后的调用中保持不变。
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     // 下一个可以被分配的 ID
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     // 返回当前线程的唯一 id，必要时分配
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 *
 * 只要线程是活动的且 ThreadLocal 实例是可访问的，每个线程持有对其线程
 * 局部变量副本的隐式引用；在一个线程消失之后，它的所有 ThreadLocal 实例
 * 副本都要进行垃圾回收（除非存在对这些副本的其它引用）。
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
public class ThreadLocal<T> {
    /**
     *
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * 下一个 hash 值。自动更新。从 0 开始。
     */
    private static AtomicInteger nextHashCode =
            new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * 返回下一个可用的 hash 值。
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * 为这个 thread-local 变量返回当前线程的初始 value。此方法在线程第一次
     * 使用 get 方法访问变量时调用，除非线程之前使用了 set 方法。
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new ThreadLocal.SuppliedThreadLocal<>(supplier);
    }

    /**
     * 构造函数
     * @see #withInitial(java.util.function.Supplier)
     */
    public ThreadLocal() {
    }

    /**
     * 返回当前线程的 tread-local 变量副本的值。如果当前线程没有该值，首先
     * 将其初始化为调用 initialValue 返回的值。
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        Thread t = Thread.currentThread();
        // 获取当前线程的 ThreadLocalMap 实例
        ThreadLocal.ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocal.ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        // map 为空则初始化
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    private T setInitialValue() {
        // value 默认为 null
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocal.ThreadLocalMap map = getMap(t);
        // map 不为 null 表示已经初始化，则调用 set 设置 value 即可
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }

    /**
     * 设置当前线程的 thread-local 变量副本为指定的 value。大多数子类不需要
     * 重写此方法，仅仅依靠 initialValue 来设置 value。
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        // 获取当前线程
        Thread t = Thread.currentThread();
        // 获取当前线程持有的 ThreadLocalMap
        ThreadLocal.ThreadLocalMap map = getMap(t);
        // 如果持有则设置 Map 中此 ThreadLocal 对应的 value
        if (map != null)
            map.set(this, value);
        // 否则创造一个新的 Map 并设置值
        else
            createMap(t, value);
    }

    /**
     * 删除当前线程的 value。
     *
     * @since 1.5
     */
    public void remove() {
        ThreadLocal.ThreadLocalMap m = getMap(Thread.currentThread());
        if (m != null)
            m.remove(this);
    }

    /**
     * 获取和指定线程关联的 ThreadLocalMap。
     *
     * @param  t the current thread
     * @return the map
     */
    ThreadLocal.ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * 创建和 ThreadLocal 关联的 map
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocal.ThreadLocalMap(this, firstValue);
    }

    /**
     * 工厂方法创建继承的线程局部变量的映射。设计为只能从线程的构造函数调用
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocal.ThreadLocalMap createInheritedMap(ThreadLocal.ThreadLocalMap parentMap) {
        return new ThreadLocal.ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * ThreadLocal 的扩展子类。
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap 是一个定制的 hash map，用于维护线程本地值。
     * 每个线程持有一个 ThreadLocalMap，用于保存前面提到的变量副本。
     */
    static class ThreadLocalMap {

        /**
         * entry 继承自 WeakReference，使用 ThreadLocal 作为 key。
         * key 为 null（entry.get() == null）表示 key 不再被引用，entry 可以从
         * table 里删除了。
         *
         * Entry 继承 WeakReference，使用弱引用，可以将 ThreadLocal 对象
         * 的生命周期和线程生命周期解绑，持有对ThreadLocal的弱引用，可以
         * 使 ThreadLocal 在没有其他强引用的时候被回收掉，这样可以避免因为
         * 线程得不到销毁导致 ThreadLocal 对象无法被回收。
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            // key 对应的 value
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * 初始容量，必须是 2 的幂。
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * 存放数据的数组，每个元素为 entry。
         * 必要时扩容。
         * table 的长度总是 2 的幂。
         */
        private ThreadLocal.ThreadLocalMap.Entry[] table;

        /**
         * table 中 entry 的数量
         * The number of entries in the table.
         */
        private int size = 0;

        /**
         * 扩容的阈值。
         */
        private int threshold; // Default to 0

        /**
         * 将扩容阈值设置为长度的 2/3
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * 计算下一个位置。table 可看成环形数组，当到达数组最后一个索引时，
         * 需要回到数组头部。
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * 计算前一个位置。table 可看成环形数组。
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * 构造一个初始时包含键值对 （firstKey，firstValue）的 map。
         * ThreadLocalMap 是延迟构造的，在至少有一个条目放入时才创建。
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            // 创建 table 数组
            table = new ThreadLocal.ThreadLocalMap.Entry[INITIAL_CAPACITY];
            // 根据 ThreadLocal 的 hash 值计算索引（用 table 的长度取模）
            // 取模用 & 代替 %，二进制计算效率较高，由此决定容量必须是 2 的幂
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            // 放入 table 桶中
            table[i] = new ThreadLocal.ThreadLocalMap.Entry(firstKey, firstValue);
            size = 1;
            // 设置阈值
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * 构造一个包含指定 map 中所有键值对的新 map
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocal.ThreadLocalMap parentMap) {
            // 创建新的 table，大小和 parentMap 中的完全一样
            ThreadLocal.ThreadLocalMap.Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new ThreadLocal.ThreadLocalMap.Entry[len];

            // 将 parentMap 里的键值对复制到新的 map 里面
            for (int j = 0; j < len; j++) {
                ThreadLocal.ThreadLocalMap.Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        ThreadLocal.ThreadLocalMap.Entry c = new ThreadLocal.ThreadLocalMap.Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        // 发生 hash 冲突，线性探查找到空的桶
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * 获取与 key 关联的 entry。此方法是快速查找：直接命中存在的 key，
         * 否则将会跳转到 getEntryAfterMiss。这是为了最大限度提高直接命中
         * 的性能设计的，部分原因是为了使这种方法线性化。
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private ThreadLocal.ThreadLocalMap.Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            ThreadLocal.ThreadLocalMap.Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * 当直接查找索引没有找到该 key 时的 getEntry 版本。
         *
         * @param  key the thread local object
         * @param  i the table index for key's hash code
         * @param  e the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private ThreadLocal.ThreadLocalMap.Entry getEntryAfterMiss(ThreadLocal<?> key, int i, ThreadLocal.ThreadLocalMap.Entry e) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                // 清理无效的 entry
                if (k == null)
                    expungeStaleEntry(i);
                // 线性探查向后查找
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        /**
         * 将指定 key 对应的 value 设置成指定 value。
         *
         * @param key the thread local object
         * @param value the value to be set
         */
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            // 计算所在位置的索引
            int i = key.threadLocalHashCode & (len-1);

            // 根据计算出来的索引找到在数组中的位置，如果已经被占用则使用
            // 线性探测往后查找
            for (ThreadLocal.ThreadLocalMap.Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {

                // 获取 entry 的 key
                ThreadLocal<?> k = e.get();

                // 如果 key 就是指定的 key，即找到了其所在 entry，设置 value 值后返回
                if (k == key) {
                    e.value = value;
                    return;
                }

                // 如果 k 为 null，说明被回收了，该位置可以使用，使用新的
                // key-value 替换
                // 此时可能还没有找到 key，key 可能存在数组后面的位置
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            tab[i] = new ThreadLocal.ThreadLocalMap.Entry(key, value);
            int sz = ++size;

            // cleanSomeSlots 清除 table[index] != null && table[index].get() == null
            // 的对象。这种 key 关联的对象已经被回收了。
            // 如果没有清除任何 entry，且使用量达到了阈值，则 rehash 扩容。
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * 删除指定 key 对应的 entry。
         */
        private void remove(ThreadLocal<?> key) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
            for (ThreadLocal.ThreadLocalMap.Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    // 调用 WeakReference 的 clear 方法清除
                    e.clear();
                    // 连续段内清除无效 entry
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * 替换无效的 entry。
         *
         * @param  key the key
         * @param  value the value to be associated with key
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key.
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            ThreadLocal.ThreadLocalMap.Entry e;

            // 由于使用的是线性探查，所以需要向后查找和向前查找，确保 entry
            // 放在最前面的空桶里，确保清除了所有的无效 entry


             // 根据转入的无效 entry 的位置（staleSlot），向前扫描一段连续的
             // entry（直到找到 tab[i] == null）
            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                // 如果是无效的，更新 slotToExpunge 记录此时的索引
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            // 从 staleSlot 开始向后扫描一段连续的 entry
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                // 如果找到了 key，把 key 对应的 value 设置成指定的 value。
                if (k == key) {
                    e.value = value;

                    // 把 stableSlot 无效的引用转移到索引 i 位置，然后将
                    // stableSlot 位置设置成有效的指定 key-value
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    // 如果向前查找没有找到无效的 entry，则更新 slotToExpunge
                    // 为当前值 i
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                // 如果向后扫描找到了空桶，且向前扫描没有找到无效的 entry，
                // 更新 slotToExpunge 为当前的 i
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
                // 如果向前扫描找到了无效的 entry，则 slotToExpunge 不会变
            }

            // 经过向前和向后查找之后，若 staleSlot 位置的 value 为空，表示
            // key 之前不存在，则直接新增一个 entry
            tab[staleSlot].value = null;
            tab[staleSlot] = new ThreadLocal.ThreadLocalMap.Entry(key, value);

            // slotToExpunge 不等于 staleSlot，说明有无效的 entry 需要清理
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * 通过 rehash 位于 staleSlot 和下一个空槽之间的任何可能碰撞的 entry，
         * 删除无效的 entry。还将删除尾 null 槽之前遇到的其他无效 entry。
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;

            // 删除 staleSlot 位置的无效 entry
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            ThreadLocal.ThreadLocalMap.Entry e;
            int i;
            // 从 staleSlot 开始向后扫描一段连续的 entry，遇到空槽停止
            for (i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();
                // 如果遇到的 key 为 null，表示无效 entry，进行清理
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                } else {
                    // 遇到的 key 不为 null，计算索引
                    int h = k.threadLocalHashCode & (len - 1);
                    // 计算出来的索引 h 与当前的索引 i 不一致，从计算出来的索引
                    // 开始，向后查找到第一个空槽，把当前 entry 移动到其正确的
                    // 位置。同时将 i 处置为 null。
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            // 下一个空槽的索引
            return i;
        }

        /**
         * 启发式扫描一些单元格，寻找无效的 entry。这是在添加新元素或删除
         * 另一个无效元素时调用的。它执行 log 级次数的扫描，以平衡
         * 无扫描（快速但保留垃圾）和与元素成比例的扫描次数，后者将会找到
         * 所有垃圾但是会导致一些插入花费 O(n) 时间。
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                ThreadLocal.ThreadLocalMap.Entry e = tab[i];
                if (e != null && e.get() == null) {
                    // 重置 n 为 len
                    n = len;
                    removed = true;
                    // 调用 expungeStaleEntry 删除无效的 entry
                    i = expungeStaleEntry(i);
                }
            } while ( (n >>>= 1) != 0);
            // 如果进行过删除无效 entry 的操作，返回 true
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            // 全部清理过后 size 减小了，所以判断如果 size 大于 len / 2 即扩容
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * table 的容量扩大为原来的两倍（仍然为 2 的幂）
         */
        private void resize() {
            ThreadLocal.ThreadLocalMap.Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            ThreadLocal.ThreadLocalMap.Entry[] newTab = new ThreadLocal.ThreadLocalMap.Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                ThreadLocal.ThreadLocalMap.Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    // 虽然做过清理，但扩容过程中数组动态变化，可能又存在 k == null
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        // 计算索引
                        int h = k.threadLocalHashCode & (newLen - 1);
                        // 使用线性探测查找空槽
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            // 设置新的阈值
            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * 清除 table 中所有的无效 entry。
         */
        private void expungeStaleEntries() {
            ThreadLocal.ThreadLocalMap.Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                ThreadLocal.ThreadLocalMap.Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
