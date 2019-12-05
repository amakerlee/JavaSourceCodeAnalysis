### ReentrantReadWriteLock

***
> 完整源码解析

[ReadWriteLock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ReadWriteLock.java) | [ReentrantReadWriteLock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ReentrantReadWriteLock.java)

***
> 概览

ReentrantReadWriteLock 是 ReadWriteLock 锁的具体实现，同样扩展 AQS 抽象类作为锁的同步器，支持公平同步器和非公平同步器，分别实现为 FairSync 和 NonfairSync。在此同步器的基础上，实现了两种类型的锁作为内部属性，分别是读锁（共享锁） ReadLock 和 写锁（独占锁，排他锁）WriteLock。

***
> 内部类 Sync

在同步器基类 Sync 中，将代表状态的整型变量值的 32 位比特位，分为高 16 位和低 16 位。状态值整型变量的高 16 位表示持有读锁的线程数，最大为 65535；低 16 位表示持有写锁的线程的重入次数，最大为 65535。

读锁和写锁均为可重入锁，写锁的重入次数可以用状态值的低 16 位表示，而高 16 位已经用来表示读锁的个数，每个读锁的重入次数还需要用类 HoldCounter 来存储。类 HoldCounter 中包含有两个变量，一个是线程 id，另一个即为重入的持有计数。已经获取到读锁的各线程中私有变量的存储使用 ThreadLocal，其 Map 中的 value 类型即为前面提到的 HoldCounter 类实例。

firstReader 变量表示获得读锁的第一个线程；firstReaderHoldCount 表示 firstReader 的持有计数；readHolds 为持有读锁重入计数的 ThreadLocalHoldCounter 类实例；cachedHoldCounter 为成功获取 readLock 的最后一个线程的持有计数。这些变量用来提高此同步器运行的效率。

```java
        // 最多支持 65535(1<<16 - 1) 个写锁和 65535 个读锁
        // int 值的低十六位表示写锁计数，高十六位表示持有读锁的线程数
        static final int SHARED_SHIFT   = 16;
        // 增加一个线程获取读锁，则持有数加 SHARED_UNIT，因为只有高十六位
        // 才表示读锁数量
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        // 锁的最大数量
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        // 写锁计数掩码（低十六位的二进制全部为 1）
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** Returns the number of shared holds represented in count  */
        // 返回当前持有读锁的线程数（高十六位表示读锁计数，所以向右移 16 位）
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /** Returns the number of exclusive holds represented in count  */
        // 返回写锁的重入次数（获取低十六位）
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

        /**
         * 持有读锁的线程计数器。记录单个线程持有的读锁数量。
         */
        static final class HoldCounter {
            int count = 0;
            // Use id, not reference, to avoid garbage retention
            // 当前线程的 id
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocal 的子类。
         */
        static final class ThreadLocalHoldCounter
                extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * 当前线程持有的可重入读锁的数量。
         * 仅在构造函数和 readObject 中初始化。
         * 当读线程的持有计数下降到 0 时删除。
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * 成功获取 readLock 的最后一个线程的持有计数。此变量在通常情况下
         * 节省了 ThreadLocal 的查找，因为下一个要 release 的线程是最后一个
         * acquire 的。这是 non-volatile 的，只作为一种启发使用，而且对于线程
         * 缓存来说非常好。
         *
         * 可以比存储读持有计数的线程活的更久，但是通过不保留线程的引用来
         * 避免垃圾保留。
         *
         * 通过良性的数据竞争访问；依赖于内存模型的 final 字段和非空保证。
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         * firstReader 是获得读锁的第一个线程。
         * firstReaderHoldCount 是 firstReader 的持有计数。
         *
         * 更准确地说，firstReader 是最后一次将共享计数从 0 更改为 1 的唯一
         * 线程，并且从那时起就没有释放读锁；如果没有这样的线程则为 null。
         */
        private transient Thread firstReader = null;
        private transient int firstReaderHoldCount;
```

写锁（独占锁）的获取和释放分别在 tryAcquire 和 tryRelease 中实现。

在 tryAcquire 中，首先判断锁是否已经被获取。如果没有，继续判断是否应该被阻塞；如果已经被获取，判断是读锁被获取还是写锁被获取，判断是否是当前线程获取了锁。根据此设置拥有锁的线程和重入的持有计数。

在 tryRelease 中，步骤较简单，只需要判断持有锁的是否为当前写线程，以及持有计数是否降为 0。

```java
        // 获取独占锁，基本和 ReentrantLock 一样
        protected final boolean tryAcquire(int acquires) {
            /*
             * Walkthrough:
             * 1. If read count nonzero or write count nonzero
             *    and owner is a different thread, fail.
             * 2. If count would saturate, fail. (This can only
             *    happen if count is already nonzero.)
             * 3. Otherwise, this thread is eligible for lock if
             *    it is either a reentrant acquire or
             *    queue policy allows it. If so, update state
             *    and set owner.
             */
            Thread current = Thread.currentThread();
            int c = getState();
            int w = exclusiveCount(c);
            // 写锁或读锁已经被获取
            if (c != 0) {
                // (Note: if c != 0 and w == 0 then shared count != 0)
                // 写锁为 0 （即读锁已被获取）或者得到锁的不是当前线程，则获取失败
                // 就算是自己持有共享锁也不能获取独占锁，从这里可以看出不支持锁升级
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                // 写锁重入持有计数已达到限制，则获取失败
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // Reentrant acquire
                // 重入获取成功
                setState(c + acquires);
                return true;
            }
            // 这个锁是首次被获取
            // 当前线程应该阻塞，或者 CAS 设置状态失败，则获取锁失败
            // 1.如果是公平锁，那么writerShouldBlock只允许队列头的线程获取锁
            // 2.如果是非公平锁，不做限制，writerShouldBlock直接返回false
            if (writerShouldBlock() ||
                    !compareAndSetState(c, c + acquires))
                return false;
            // 否则获取成功
            setExclusiveOwnerThread(current);
            return true;
        }

        // 释放独占锁，基本和 ReentrantLock 一样
        protected final boolean tryRelease(int releases) {
            // 当前线程不是持有独占锁的线程，抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 持有计数减少
            int nextc = getState() - releases;
            boolean free = exclusiveCount(nextc) == 0;
            // 只有持有计数为 0 的时候才会完全释放
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }
```

读锁（共享锁）的获取和释放分别在 tryAcquireShared 和 tryReleaseShared 中实现。

比独占模式稍复杂的是，共享锁的获取和释放中额外使用了三个变量用于提高效率，分别是第一次获取读锁的 firstReader（其持有计数不使用 ThreadLocal 方式保存），最新一次获取读锁线程的 cachedHoldCounter 对象，以及用 ThreadLocal 方式保存的每个线程对应的 HoldCounter 对象。

释放读锁时，首先判断当前线程是否是 firstReader，然后判断当前线程的 HoldCounter 是否是最新存储的 cacheHoldCounter，如果都不是，则用 ThreadLocal 的方式获取，然后以 CAS 的方式更新状态，成功释放锁。

获取读锁时，首先判断是否有资格获取（读锁可以获取，读锁可以重入，除此之外，如果当前线程持有写锁，依然可以获取读锁），然后使用和释放锁类似的方法，逐步判断。如果第一次获取失败，进入 fullTryAcquireShared 自旋，尝试获取读锁。

```java
        // 共享模式释放锁
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            // 当前为第一个获取读锁的线程
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
                // 如果只持有一次，则可以释放，将 firstReader 设为空，否则计数
                // 减 1
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                // 获取当前线程的计数器
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                // 从缓存或者 ThreadLocal 里获取到之后
                int count = rh.count;
                // 只持有 1 次，直接删除
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                // 持有计数减 1
                --rh.count;
            }
            // 更新完持有计数之后，自旋更新同步器状态，把读锁的数量减 1
            for (;;) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    // Releasing the read lock has no effect on readers,
                    // but it may allow waiting writers to proceed if
                    // both read and write locks are now free.
                    // 表示是否完全释放所有读锁
                    return nextc == 0;
            }
        }

        // 共享模式获取锁（获取读锁）
        protected final int tryAcquireShared(int unused) {
            /*
             * Walkthrough:
             * 1. If write lock held by another thread, fail.
             * 2. Otherwise, this thread is eligible for
             *    lock wrt state, so ask if it should block
             *    because of queue policy. If not, try
             *    to grant by CASing state and updating count.
             *    Note that step does not check for reentrant
             *    acquires, which is postponed to full version
             *    to avoid having to check hold count in
             *    the more typical non-reentrant case.
             * 3. If step 2 fails either because thread
             *    apparently not eligible or CAS fails or count
             *    saturated, chain to version with full retry loop.
             */
            Thread current = Thread.currentThread();
            int c = getState();
            // exclusiveCount(c) != 0 独占计数不等于 0，说明有线程持有写锁
            // 写锁可以继续获取读锁，非写锁不能获取读锁
            // 如果尝试获取的不是写锁，则返回 -1，表示获取失败
            if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                return -1;
            // 获取持有读锁的线程数量
            int r = sharedCount(c);
            // 1.获取读锁线程不应该阻塞
            // 2.没有达到最大数量
            // 3.CAS 更新成功
            // 可以尝试获取锁
            if (!readerShouldBlock() &&
                    r < MAX_COUNT &&
                    compareAndSetState(c, c + SHARED_UNIT)) {
                // 首次获取读锁，初始化 firstReader 和 firstReaderHoldCount
                // firstReader 不会放到 readHolds 里面，避免在 readHolds 里面查找
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    // 当前线程就是首次获取的线程，即首次获取线程重入，则持有
                    // 计数加 1
                    firstReaderHoldCount++;
                } else {
                    // 首先获取持有读锁的最后一个线程
                    HoldCounter rh = cachedHoldCounter;
                    // 如果当前线程不是持有锁的最后一个线程
                    if (rh == null || rh.tid != getThreadId(current))
                        // 缓存里没有，则从readHolds 里获取当前线程
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    // 更新重入计数
                    rh.count++;
                }
                // 返回 1 表示获取成功
                return 1;
            }
            // 一次获取读锁失败后，尝试循环获取
            return fullTryAcquireShared(current);
        }

        /**
         * 读锁 acquire 的完整版本，包含 CAS 失败或者 tryAcquireShared 中
         * 没有获取可重入读锁的情况
         * tryAcquireShared 是一次快速获取的情况，且里面用到的 CAS 只允许一个
         * 线程获取成功，但读锁是共享的，所以需要此函数来循环获取，直到成功。
         */
        final int fullTryAcquireShared(Thread current) {
            /*
             * This code is in part redundant with that in
             * tryAcquireShared but is simpler overall by not
             * complicating tryAcquireShared with interactions between
             * retries and lazily reading hold counts.
             */
            HoldCounter rh = null;
            for (;;) {
                int c = getState();
                // 如果有线程获取到了写锁
                if (exclusiveCount(c) != 0) {
                    // 判断获取到写锁的是不是当前线程，如果是则返回获取失败
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else 如果是当前线程获取到了写锁，不阻塞，否则会造成死锁。
                    // （从这里可以看到 ReentrantReadWriteLock 允许锁降级）
                } else if (readerShouldBlock()) {
                    // 进入这里说明虽然写锁没有被获取，但同步队列的头结点的后继
                    // 有一个竞争写锁的线程，所以这里应该让步，让写锁先获取。
                    // Make sure we're not acquiring read lock reentrantly
                    // 如果当前线程是 firstReader，一定是重入
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            // 如果当前线程不是缓存的最后一个线程，从 readHolds 里读取
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                // 移除这个线程
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        // 需要阻塞，而且还没有获取过锁，那么可以允许它获取失败。
                        // 注意：
                        // 已经获取了读锁的线程重入时，不能阻塞，阻塞会导致死锁。
                        if (rh.count == 0)
                            return -1;
                    }
                }
                // 读锁的数量达到限制
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // 此线程不应该被阻塞，且读锁的数量没有达到限制，那么可以获取读锁
                // 获取读锁成功，下面的处理和 tryAcquireShared 类似
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }
```

***
> 参考：

[ThreadLocal源码分析](https://www.jianshu.com/p/80866ca6c424)