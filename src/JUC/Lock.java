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
import java.util.concurrent.locks.Condition;

/**
 * 与使用 synchronized 方法和 synchronized 代码块相比，Lock 的实现提供了
 * 更广泛的锁定操作。它们允许更灵活的结构，可能有更多的属性，支持多个
 * 关联的 Condition 对象。
 *
 * 锁是一种控制多个线程对共享资源访问的工具。通常锁提供对共享资源的独占
 * 访问：同一时间只有一个线程可以获得锁，对共享资源的所有访问都需要首先
 * 获取锁。但是，有些锁可能允许并发访问共享资源，比如 ReadWriteLock 锁。
 *
 * 使用 synchronized 方法或者 synchronized 代码块访问和对象关联的隐式
 * 监视器锁，但是所有的 acquire 和 release 都以 block-structure 的方式发生：
 * 多个锁 acquire 时必须以相反的顺序 release，所有的锁都必须以 acquire 对应
 * 的形式 release。
 *
 * 虽然 synchronized 方法和 synchronized 代码块的作用域机制使基于监视器锁
 * 变成的机制更加容易，并且帮助避免了许多涉及锁的常见编程错误，但是在
 * 某些情况下需要更灵活地使用锁。例如，一些并发访问的数据结构的遍历算法
 * 需要使用 hand-over-hand 或者链锁：先获取节点 A，然后是节点 B，然后释放
 * A，然后获取 C，然后释放 B，然后获取 D 等。Lock 接口的实现允许在不同范围内
 * 获取和释放一个锁，并允许以任意顺序获取和释放多个锁。
 *
 * 伴随着灵活性增加的是额外的责任。块结构锁的小事消除了使用 synchronized
 * 产生的锁的自动释放。在大多数情况下，应该使用如下习语：
 * Lock l = ...;
 * l.lock();
 * try {
 *   // access the resource protected by this lock
 * } finally {
 *   l.unlock();
 * }}
 *
 * 当锁定和解锁发生在不同的作用域时，必须注意保持持有锁时执行的所有代码
 * 都收到 try-finally 或 try-catch 的保护，以确保在必要时释放锁。
 *
 * Lock 实现相对于 synchronized 提供了额外的功能，它提供了 tryLock 用于
 * 非阻塞尝试获取锁，提供 lockInterruptibly 用于可中断模式获取锁，提供了
 * tryLock(long, TimeUnit) 用于有限等待时间内获取锁。
 *
 * Lock 类还提供与隐式监视器锁完全不同的语义和行为，比如保证顺序、不可
 * 重入使用或死锁检测。
 *
 * 注意，Lock 实例只是普通对象，也可以用作 synchronized 语句持有对象。
 * 获取实例的监视器锁与调用该实例的 lock 方法没有具体关系。为了避免混淆，
 * 建议不要以这种方式实现 Lock 实例。
 *
 * @see ReentrantLock
 * @see Condition
 * @see ReadWriteLock
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface Lock {

    /**
     * 获取锁。如果锁不可用一直等待。
     *
     * 如果锁不可用，则当前线程将出于线程调度的目的而禁用，并处于休眠状态，
     * 直到获取到锁为止。
     *
     * 实现提示：
     * Lock 的实现可能会检测锁的错误使用，比如彼此的调用会导致死锁，并且
     * 可能在这种情况下抛出未检查的异常。
     */
    void lock();

    /**
     * 获取锁除非当前线程被中断。获取锁的同时响应中断。
     *
     * 如果锁可用并能立即返回的话获取锁。
     *
     * 如果锁不可用，则当前线程将出于线程调度的目的而禁用，并处于休眠状态，
     * 直到发生以下两种情况之一：
     * 当前线程获取锁；或者其他线程中断当前线程，且 lock 的 acquire 支持
     * 中断。
     *
     * 如果当前线程在进入此方法前已经设置其中断状态；或者在 acquire 时被
     * 中断，而且 lock 的 acquire 支持中断，那么抛出 InterruptedException
     * 异常，并清除当前线程的中断状态。
     *
     * @throws InterruptedException if the current thread is
     *         interrupted while acquiring the lock (and interruption
     *         of lock acquisition is supported)
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * 调用时如果锁是空闲的，则尝试 acquire。获取失败直接返回。
     *
     * 如果锁可用，则获取锁，并立即返回 true。如果锁不可用，返回 false。
     *
     * 此方法一个典型的用法是：
     * Lock lock = ...;
     * if (lock.tryLock()) {
     *   try {
     *     // manipulate protected state
     *   } finally {
     *     lock.unlock();
     *   }
     * } else {
     *   // perform alternative actions
     * }}</pre>
     *
     * 这种用法保证了如果锁被 acquire，那么确保它之后会被释放，如果没有
     * acquire 则不会被释放。
     *
     * @return {@code true} if the lock was acquired and
     *         {@code false} otherwise
     */
    boolean tryLock();

    /**
     * 如果给定的等待时间内线程是空闲的，并且当前线程没有被中断，则
     * acquire 锁。
     *
     * 如果 lock 可用的话此方法返回 true。
     * 如果锁不可用，则当前线程将出于线程调度的目的而禁用，并处于休眠状态，
     * 直到发生以下三种情况之一：
     * 当前线程获取到锁；或者其它线程中断当前线程，且 lock 的 acquire 支持
     * 中断；或者时间到期。
     *
     * 如果获取到锁则返回 true。
     *
     * 如果当前线程在进入此方法前已经设置其中断状态；或者在 acquire 时被
     * 中断，而且 lock 的 acquire 支持中断，那么抛出 InterruptedException
     * 异常，并清除当前线程的中断状态。
     *
     * 如果指定的等待时间过期，则返回 false。
     * 如果时间小于等于 0，则该方法不会再等待。
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return {@code true} if the lock was acquired and {@code false}
     *         if the waiting time elapsed before the lock was acquired
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while acquiring the lock (and interruption of lock
     *         acquisition is supported)
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁。
     */
    void unlock();

    /**
     * 返回一个新的 Condition 实例，该实例绑定到此 Lock 实例。
     *
     * 在 condition 队列上等待之前，锁必须由当前线程持有。
     * 调用 Condition.await 将自动在 wait 之前释放锁，在 wait 返回之前再次
     * 获取锁。
     *
     * @return A new {@link Condition} instance for this {@code Lock} instance
     * @throws UnsupportedOperationException if this {@code Lock}
     *         implementation does not support conditions
     */
    Condition newCondition();
}

