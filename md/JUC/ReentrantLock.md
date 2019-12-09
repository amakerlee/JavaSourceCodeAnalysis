### ReentrantLock

***
> 完整源码解析

[Lock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/Lock.java) | [ReentrantLock](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ReentrantLock.java)

***
> 内部类

ReentrantLock 锁中定义抽象内部类 **Sync**，继承自 AbstractQueuedSynchronizer，作为公平/非公平同步控制器实现的基础。类中定义 nonfairTryAcquire 方法用于非公平锁的 tryAcquire 实现，定义 tryRelease 方法用于非公平锁的 tryRelease 实现。

**nonfairTryAcquire** 方法中，如果没有线程持有锁则设置当前线程持有锁，如果当前线程持有锁，则设置重入状态，否则获取失败。

**tryRelease** 方法中，如果当前线程没有持有锁，抛出 IllegalMonitorStateException 异常，否则减小重入计数或者直接释放锁。

```java
    /**
     * 此锁同步控制的基础。下层子类分为公平版本和非公平版本。使用 AQS
     * 状态表示锁的持有数量。
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 用于 Lock.lock 实现。子类化的主要原因是允许非公平版本的快速实现。
         */
        abstract void lock();

        /**
         * 非公平锁的 tryAcquire 实现。
         * 非公平的 tryLock。tryAcquire 在子类中实现，但是同时需要 trylock
         * 方法中的非公平尝试。
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                // 如果没有线程持有锁，使用 CAS 的方式设置状态，然后设置当前
                // 线程持有锁
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 如果当前线程已经持有锁，则设置重入状态。
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        // 非公平锁中 tryRelease 的实现
        protected final boolean tryRelease(int releases) {
            // 计算释放后的 state 值
            int c = getState() - releases;
            // 如果当前线程没有持有锁，则抛出 IllegalMonitorStateException 异常
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                // state 计数为 0，重入锁已经全部释放，可以唤醒下一个线程。
                free = true;
                // 设置锁的持有线程为 null
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }
    }
```

非公平同步器 NonfairSync 继承自 Sync，在 tryAcquire 实现中，直接调用父类中的 nonfairTryAcquire 函数。

```java
    /**
     * 非公平锁 Sync 的实现
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 实现 lock 函数。
         */
        final void lock() {
            // 通过 CAS 方式将状态值从 0 设置成 1（CANCELLED），如果设置
            // 成功则调用 setExclusiveOwnerThread 将当前线程设置为持有锁。
            // 否则调用 acquire 获取锁。
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }

        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }
```

公平锁同步器 FairSync 同样继承自 Sync。公平版本的 tryAcquire 实现中，首先检查是否有前驱节点，没有前驱节点时才尝试获取锁。如果是重入模式，则相应修改重入状态。否则获取失败。

```java
    /**
     * 公平锁 Sync 的实现
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        // 实现 lock 方法。直接调用 AQS 中的 acquire。
        final void lock() {
            acquire(1);
        }

        /**
         * tryAcquire 的公平锁版本。
         */
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            // 获取锁的状态 state
            int c = getState();
            // 没有线程持有锁
            if (c == 0) {
                // 判断当前线程是否有前驱节点，如果没有前驱节点，使用 CAS 的
                // 方式修改状态，修改为 acquires
                if (!hasQueuedPredecessors() &&
                        compareAndSetState(0, acquires)) {
                    // 如果修改成功，设置锁的持有线程为当前线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 当前线程已经持有锁，以重入的方式获取锁
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                // 更新 state 状态为重入计数
                setState(nextc);
                return true;
            }
            return false;
        }
    }
```

***
> 类属性

ReentrantLock 完全基于继承自 AQS 的公平/非公平 Sync 控制器来实现锁的获取和释放，所以此类的属性中只有提供所有获取/释放锁机制的 Sync 同步器。

```java
    /** 提供所有实现机制的同步器 */
    private final Sync sync;
```

***
> 成员函数

重入锁 ReentrantLock 中核心方法包括 lock，lockInterruptibly，tryLock，unlock 等，均基于同步器实现。

```java
    /**
     * acquire 锁。
     *
     * 如果锁没有被其他线程持有则 acquire 锁，并立即返回，设置锁的持有数
     * 为 1。
     *
     * 如果当前线程已经持有锁，那么持有计数加 1，然后立刻返回。
     *
     * 如果锁被其他线程持有，那么当前线程进入睡眠。直到锁被 acquire，此时
     * 锁持有的计数被设置成 1。
     */
    public void lock() {
        sync.lock();
    }
    
    /**
     * acquire 锁，除非当前线程被中断。
     *
     * 如果锁不被其他线程持有，则获取锁并立即返回，将持有计数设置为 1。
     *
     * 如果当前线程已经持有此锁，那么持有计数加 1，然后立即返回。
     *
     * 如果锁被其他线程持有，那么当前线程进入睡眠。直到发生以下两种情况之一：
     * 此锁被当前线程 acquire；或者其他线程中断了此线程。
     *
     * 如果锁被当前线程 acquire，持有计数设置为 1。
     *
     * 如果当前线程：
     * 在进入此方法时其中断状态已设置，或者在获取锁时被中断了，那么抛出
     * InterruptedException 异常，并清除当前线程的中断状态。
     *
     * 在此实现中，由于这个方法是显式中断点，所以优先相应中断而不是正常的
     * 或者可重入的锁获取。
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }
    
    /**
     * 如果在调用时锁没有被其他线程持有，则 acquire 锁。
     *
     * 如果锁不被其他线程持有，则 acquire 锁，并立即返回 true，设置锁的
     * 持有计数为 1。当这个锁被设置成公平锁策略时，如果锁可用，调用 tryLock
     * 会立即获得锁，不管其它线程是否正在等待锁。这种行为在某些情况下是
     * 有用的，即使它破坏了公平性。如果想为这个锁设置公平性，使用
     * tryLock(0, TimeUnit.SECONDS)即可（他会检测中断）。
     *
     * 如果当前线程已经持有该锁，那么持有计数将增加 1，然后此方法返回 true。
     *
     * 如果锁被其他线程持有，此方法立即返回 false。
     *
     * @return {@code true} if the lock was free and was acquired by the
     *         current thread, or the lock was already held by the current
     *         thread; and {@code false} otherwise
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }
    
    /**
     * 尝试释放锁。
     *
     * 如果当前线程是这个锁的持有者，那么持有计数将递减。如果持有计数现在
     * 为 0，则释放锁。如果当前线程不是这个锁的持有者，则抛出
     * IllegalMonitorStateException 异常。
     *
     * @throws IllegalMonitorStateException if the current thread does not
     *         hold this lock
     */
    public void unlock() {
        sync.release(1);
    }
```

***
> ReentrantLock 总结

ReentrantLock 是 Lock 接口的可重入锁实现，完全基于 AQS 抽象类。由于是独占锁，用户只需继承 AQS，实现自身同步器中 tryAcquire 和 tryRelease 方法。

当前持有锁的线程重入一次，状态值加 1，当状态值降到 0 时，才能释放锁。