/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package JUC;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.concurrent.locks.Condition;

/**
 * 可重入互斥锁（可重入独占锁）具有与使用 synchronized 的隐式监视器锁
 * 相同的基本行为和语义，但具有更多扩展功能。
 *
 * ReentrantLock 由最后一次成功锁定但尚未解锁它的线程拥有。当锁不被其它
 * 线程持有的时候，调用 lock 的线程将返回，并成功获取锁。如果当前线程已经
 * 拥有锁，则该方法将立即返回。可以使用 isHeldByCurrentThread 方法和
 * getHoldCount 进行检查。
 *
 * 该类的构造函数接受一个可选的公平性参数。当设置 true 时，在竞争状态下，
 * 锁倾向于对最长等待的线程授予访问权。否则，此锁不保证任何特定的访问顺序。
 * 使用多个线程访问的公平锁的程序可能会显示较低的总体吞吐量（即更慢，通常
 * 比那些使用默认设置的要慢得多），但是在获取锁和保证不会饿死方面的时间
 * 差异更小。但是注意，锁的公平性并不保证线程调度的公平性。因此，使用
 * 公平锁的多个线程中的一个可能会连续多次获得它，而其他线程不会，在当前
 * 时刻也没有持有锁。
 * 注意未定时的 tryLock 方法不支持设置公平性。如果锁可用，即使有其他线程
 * 正在等待，它也会成功。
 *
 * 建议使用如下方式：
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
 *   // ...
 *
 *   public void m() {
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }}
 *
 * 除了实现 Lock 接口外，此类还定义了一系列 public 和 protected 方法来检查
 * 锁的状态。其中一些方法仅对监测和监视有用。
 *
 * 此锁支持最多同一个线程获取 2147483547（Integer.MAX_VALUE）个
 * 递归锁。视图超过此限制将会抛出 Error 异常。
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;

    /** 提供所有实现机制的同步器 */
    private final Sync sync;

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

        // 检查是否是当前线程持有锁
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // Methods relayed from outer class

        // 返回持有锁的线程
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        // 获取状态（重入计数）
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        // 是否有线程持有锁
        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

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

    /**
     * 创建 ReentrantLock 实例。
     * 相当于使用 ReentrantLock(false)，默认为非公平锁。
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * 使用给定的公平/非公平策略创造一个 ReentrantLock 实例
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

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
     * 如果在给定的等待时间内没有其他线程持有锁，且当前线程没有中断，
     * 则 acquire 锁。
     *
     * 如果锁不被其他线程持有，则 acquire 锁，并立即返回 true，设置持有计数
     * 为 1。当这个锁被设置成使用公平策略时，如果有其他线程正在等待锁，
     * 那么锁不会被 acquire 到。这与 tryLock 形成对比。如果想要一个定时的
     * tryLock，它允许对一个公平的锁进行操作，那么把定时的和不定时的
     * 结合在一起：
     * if (lock.tryLock() ||
     *     lock.tryLock(timeout, unit)) {
     *   ...
     * }}
     *
     * 如果当前线程已经持有锁，那么持有计数加 1，然后方法返回 true。
     *
     * 如果锁被其他线程持有，那么当前线程进入睡眠。直到发生以下三种情况之一：
     * 此锁被当前线程 acquire；或者其他线程中断了此线程；或者时间到期。
     *
     * 如果锁被当前线程 acquire，返回 true，持有计数设置为 1。
     *
     * 如果当前线程：
     * 在进入此方法时其中断状态已设置，或者在获取锁时被中断了，那么抛出
     * InterruptedException 异常，并清除当前线程的中断状态。
     *
     * 如果指定的等待时间到期了，返回 false。如果时间小于等于 0，此方法
     * 不会再等待。
     *
     * 在此实现中，由于这个方法是显式中断点，所以优先相应中断而不是正常的
     * 或者可重入的锁获取，也不是等待时间的流逝。
     *
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     * @return {@code true} if the lock was free and was acquired by the
     *         current thread, or the lock was already held by the current
     *         thread; and {@code false} if the waiting time elapsed before
     *         the lock could be acquired
     * @throws InterruptedException if the current thread is interrupted
     * @throws NullPointerException if the time unit is null
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
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

    /**
     * 返回与此 Lock 实例一起使用的 Condition 实例。
     *
     * 返回的 Condition 实例支持 Object 监视器同样的以下方法：wait，notify，
     * notifyAll。
     *
     * 调用 Condition 中的 await 或者 signal 方法时如果没有持有锁，那么将会
     * 抛出 IllegalMonitorStateException 异常。
     *
     * 当 Condition 的 await 方法被调用时，如果锁被释放，在返回之前，会再次
     * 获取锁，并将持有计数恢复到方法被调用时的值。
     *
     * 如果一个线程在等待时被中断，那么等待将会停止，抛出 InterruptedException
     * 异常，线程的中断状态将被清除。
     *
     * 等待的线程按 FIFO 的顺序唤醒。
     *
     * 从等待方法返回的线程的锁重新 acquire 的顺序与最初获取锁的线程相同
     * （在默认情况下未指定），但对于公平锁，优先使用那些等待时间最长的线程。
     *
     * </ul>
     *
     * @return the Condition object
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     *
     * 查询当前线程持有锁的次数（持有计数）。
     *
     * 持有计数的信息通常仅用于测试和调试目的：
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *   public void m() {
     *     assert lock.getHoldCount() == 0;
     *     lock.lock();
     *     try {
     *       // ... method body
     *     } finally {
     *       lock.unlock();
     *     }
     *   }
     * }}
     *
     * @return the number of holds on this lock by the current thread,
     *         or zero if this lock is not held by the current thread
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * 查看当前线程是否持有锁。
     *
     * 与用于内置监视器锁的 Thread.holdsLock 方法类似，此方法通常用于调试
     * 和测试。例如，只有在锁被持有时才应该调用，如下：
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert lock.isHeldByCurrentThread();
     *       // ... method body
     *   }
     * }}
     *
     * 它也可以用来可重入锁以不可重入的方式使用，例如：
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }}
     *
     * @return {@code true} if current thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 查询此锁是否由任何线程持有。此方法用于监视系统状态，而不是用于同步
     * 控制。
     *
     * @return {@code true} if any thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 如果此锁是公平策略，返回 true。
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 返回当前拥有该锁的线程，如果不拥有则返回 null。如果此方法被非持有者
     * 调用，返回值反应当前锁状态的最佳状态近似值。例如，所有者可能是 null，
     * 即使有现成试图获取锁，但是还没有这样做。此方法的目的是为了方便构造
     * 更多的监视器子类。
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 查询是否有线程正在等待 acquire 此锁。注意，因为取消可能随时发生，
     * 返回 true 并不保证任何其他线程将获得此锁。该方法主要用于监控系统状态。
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 查询给定的线程是否等待获取此锁。注意，因为取消可能随时发生，返回
     * true 并不保证任何其他线程将获得此锁。该方法主要用于监控系统状态。
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 返回等待获取此锁的线程数量的估计值。这个值只是一个估计值，因为当
     * 这个方法遍历内部数据结构的时候线程可能会动态变化。此方法用来监视
     * 系统状态，而不是用于同步控制。
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 返回一个包含可能正在等待获取此锁的线程的集合。由于在构造这个结果时，
     * 实际的线程集可能会动态变化，返回的集合只是最佳估计。返回的集合中的
     * 元素没有特定顺序。此方法的目的是为了方便构造更多的监视器子类。
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 查询是否有线程正在 condition 队列上等待。注意，由于时限到期或者中断
     * 可能随时发生，返回 true 并不保证未来的 signal 会唤醒任何线程。此方法
     * 主要用来监视系统状态。
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回与此锁关联的指定 condition 上等待的线程数量。注意，由于超时和中断
     * 可能随时发生，因此估计值仅用于实际等待者数量的上限。此方法用来监视
     * 系统状态，而不是用于同步控制。
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回一个集合，其中包含可能在与此锁关联的指定 condition 上等待的线程。
     * 由于在构造这个结果时，实际的线程集可能会动态变化，返回的集合只是
     * 最佳估计。返回的集合中的元素没有特定顺序。此方法的目的是为了方便
     * 构造更多的监视器子类。
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回标识此锁的字符串及其锁状态。
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                "[Unlocked]" :
                "[Locked by thread " + o.getName() + "]");
    }
}

