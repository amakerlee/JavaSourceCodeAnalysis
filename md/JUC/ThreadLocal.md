### ThreadLocal

***
> 完整源码解析

[ThreadLocalMap](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ThreadLocal.java) | [ThreadLocal](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ThreadLocal.java)

***
> 基本原理

通过 ThreadLocal 在指定的线程中存储数据。数据存储过后，只有该线程才能获取到数据，其他线程无法获取数据。

实现上述目的的 ThreadLocal 依赖的是 ThreadLocalMap。每个线程内部绑定一个 ThreadLocalMap，在此类的映射中，将 ThreadLocal 对象作为 key，将希望存入的值作为 value。

Thread，ThreadLocal 和 ThreadLocalMap 的关系如下图所示：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ThreadLocal.png" width=50% />

***
> 内部类 ThreadLocalMap

此 map 和 HashMap 一样使用 Entry 作为 key-value 的存储结构。和 HashMap 不同的是，Entry 继承自 WeakReference 弱引用，用于在线程被销毁的时候，对 value 进行垃圾回收。发生哈希碰撞时，HashMap 使用双向链表和红黑树组织桶内数据结构，而 ThreadLocalMap 使用线性探查解决。除此之外，ThreadLocalMap 会频繁检查 table 数组中失效的 Entry，并进行垃圾回收和重新整理。

类属性包括以下几项：

```java
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
```

rehash 的阈值设置为 table 数组长度的 2/3，且 table 数组长度保持为 2 的幂。

为了符合线性探查的要求，将 table 作为循环数组，利用以下两个函数方便计算下一个索引和上一个索引：

```java
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
```

**get 方法**

在 getEntry 方法中首先根据 key 的 hash 值计算所在位置，如果一次命中则返回，否则进入 getEntryAfterMiss 继续查找。

getEntryAfterMiss 方法使用线性探查从当前位置继续往后查找，直到找到该节点或遇到 null 桶为止。每一次的循环过程，都调用 expungeStaleEntry 清理一段范围内的无效 Entry。

expungeStaleEntry 方法从当前参数位置开始往后遍历，直到遇到 null 空槽为止。遍历过程中，清除无效 Entry（置为 null，虚拟机执行回收）。由于清理后多出了空槽，所以清理的同时，根据 hash 值重新计算此范围内节点的位置，并进行重置。重置过程保证了当中间节点被清除时，后续节点回到其正确的位置上，或者填补这一空白。

```java
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
```

**set 方法**

set 方法用于插入新的节点，或者更新节点的 value 值，同样首先查找对应的 key，然后新建或替换，然后清理范围内无效的节点并重置范围内节点位置。

```java
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
```

**rehash 操作**

```java
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
```

***
> 成员函数

**get 方法**

在线程执行过程中，调用某个 ThreadLocal 的 get 方法，即获取此线程和该 ThreadLocal 绑定的 value 值。

首先获取该线程的 ThreadLocalMap 变量，然后在获取到的 map 中，根据指定的作为 key 的 ThreadLocal，查找其对应的 value 并返回。如果获取到的为 null，则进行初始化（延迟初始化模式）。

```java
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
```

**set 方法**

调用 ThreadLocal 的 set 方法，将 value 和指定的线程绑定。在具体的实现方法中，和 get 类似，首先获取该线程的 ThreadLocalMap 对象，若该 map 存在则调用 map.set 方法设置 value，否则创建新的 map 作为 ThreadLocalMap。

```java
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
```

***
> 内存泄露问题

ThreadLocalMap 中采用 ThreadLocal 作为键值对的 key，如果 ThreadLocal 被回收则意味着此 ThreadLocal 对应的 value 再也不会被访问到，也成为无用的内存。由于 Map 被当前线程持有，不管 ThreadLocal 有没有被回收，value 都会一直存在于内存中，无意义的 value 在内存中累加，极有可能造成内存泄露的问题。

针对此问题，ThreadLocal 类中的 set、get 等方法均实现了回收无效的 Entry 节点的操作。如果检测到范围内节点的 key 为 null，则设置其 value 为 null，其 Entry 的引用为 null，那么在下一次垃圾回收之前，将会自动回收这些弱引用的节点对象。

强引用对象是指不会被回收的对象；软引用对象是指内部不足的时候回收的对象；弱引用对象是指存活到垃圾回收前的对象，此类对象在垃圾回收发生时会立刻进行回收。

***
> 参考：

[ThreadLocal源码分析](https://www.jianshu.com/p/80866ca6c424)
[ThreadLocal 原理](https://www.jianshu.com/p/0ba78fe61c40)