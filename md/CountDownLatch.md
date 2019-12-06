### CountDownLatch

主线程等待固定数量的子线程运行完成之后，继续运行后面的操作。

***
> 完整源码解析

[CountDownLatch](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/CountDownLatch.java)

***
> 内部类 Sync

同步控制器 Sync 类中实现获取共享锁的 tryAcquireShared 方法和释放共享锁的 tryReleaseShared 方法

tryAcquireShared 方法中仅仅判断当前状态（状态用于判断是否打开 latch）是否等于 0，当状态值降为 0 时，打开 latch，线程可以获取到锁，否则阻塞在 latch 之外。

在 tryReleaseShared 方法中自旋尝试 count 计数减 1，若减 1 之后的状态计数等于 0，则在调用此函数的 acquireSharedInterruptibly 中释放所有线程。

```java
        //调用此函数的方法是 acquireSharedInterruptibly
        // 在 acquireSharedInterruptibly 中，返回值小于 0 时进入 AQS 队列等待
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        
        // 释放共享锁
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (;;) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c-1;
                // 通过 CAS 设置同步状态
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
```

***
> 成员函数

此类的一个常用场景是，主线程等待固定数量的子线程运行完成之后，继续运行后面的操作。使用方法 await 阻塞主线程，直到所有的线程都完成调用 countDown 方法，将状态计数减到 0 之后，被阻塞的主线程才开始继续运行。

实现了同步器中共享模式获取锁和释放锁的函数之后，CountDownLatch 类中的 await 和 countDown 函数只需要简单的调用需要的函数即可。

```java
    /**
     * 使当前线程等待，直到 latch 的计数降到 0，等待过程中响应中断。
     *
     * 如果当前计数为 0，则此方法立即返回（获取成功）。
     *
     * 如果当前计数大于 0，则出于线程调度的目的，当前线程将被禁用，并休眠
     * 直到发生以下两种情况之一：
     * 由于调用 countDown 方法当前计数降到 0；或者其他线程中断此线程。
     *
     * 如果当前线程：
     * 在进入此方法前设置了中断状态；或者在等待时被中断，
     * 那么抛出 InterruptedException 异常，并清除当前线程的中断状态。
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    
    /**
     * latch 的计数递减，如果计数达到 0，则释放所有的等待线程。
     *
     * 如果当前计数大于 0，则递减。如果新的计数为 0，那么所有等待的线程都
     * 将重新启用，以便进行线程调度。
     *
     * 如果当前计数等于 0，则什么也不会发生。
     */
    public void countDown() {
        sync.releaseShared(1);
    }
```

***
> 总结

CountDownLatch 底层实现依赖于 AQS 共享锁的实现机制，首先初始化计数器 count，调用 countDown 方法时，计数器 count 减 1，当计数器 count 等于 0 时，会唤醒 AQS 等待队列中的线程。调用 await 方法，线程会被挂起，它会等待直到 count 值为 0 才继续执行，否则会加入到等待队列中，等待被唤醒。