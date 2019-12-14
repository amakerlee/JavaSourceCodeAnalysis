### ThreadPoolExecutor

***
> 继承结构及完整源码解析

[Executor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/Executor.java) | [ExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ExecutorService.java) | [AbstractExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractExecutorService.java) | [ThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ThreadPoolExecutor.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ThreadPoolExecutor.png" width=70% />

***
> 线程池状态

ThreadPoolExecutor 线程池有 RUNNING, SHUTDOWN, STOP, TIDYING, TERMINATED 五种状态，状态之间的关系如下图所示（引用自[JUC源码分析-线程池篇（一）：ThreadPoolExecutor](https://www.jianshu.com/p/7be43712ef21)）：

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ThreadPoolExecutor2.png" width=70% />

RUNNING：正常运行状态，接受新的任务（如果没有达到拒绝策略的条件）

SHUTDOWN：不接收新的任务，但是会继续运行正在线程中执行的任务和在队列中等待的任务

STOP：不接收新任务，也不会运行队列任务，并且中断正在运行的任务

TIDYING：所有任务都已经终止，workerCount 为0，当池状态为TIDYING时将会运行terminated()方法

TERMINATED：完全终止

线程池状态保存在作为类属性的原子整型变量 ctl 的高 3 位 bits 中。

***
> 添加新任务

常用的线程池添加一个新任务时，主要有以下步骤：

1. 若当前线程数小于核心线程数，创建一个新的线程执行该任务。

2. 若当前线程数大于等于核心线程数，且任务队列未满，将任务放入任务队列，等待空闲线程执行。

3. 若当前线程数大于等于核心线程数，且任务队列已满

    3.1 若线程数小于最大线程数，创建一个新线程执行该任务
    
    3.2 若线程数等于最大线程数，执行拒绝策略

***
> 静态常量

CAPACITY 表示此线程池最大有效线程数为 (2^29)-1，而有效线程数存储在变量 ctl 的低 29 位 bits 中。COUNT_BITS 用于便捷地获取 ctl 的高 3 位和低 29 位。

```java
    private static final int COUNT_BITS = Integer.SIZE - 3;
    // 最大有效线程数为 (2^29)-1
    // CAPACITY 的低 29 位全部为 1，高 3 位为 0
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    //111 + 29 个 0
    private static final int RUNNING    = -1 << COUNT_BITS;
    // 000 + 29 个 0
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    // 001 + 29 个 0
    private static final int STOP       =  1 << COUNT_BITS;
    // 010 + 29 个 0
    private static final int TIDYING    =  2 << COUNT_BITS;
    // 011 + 29 个 0
    private static final int TERMINATED =  3 << COUNT_BITS;
```

***
> 类属性

最重要的类属性 ctl 是原子整型变量类型，保存了线程池的两个状态，高 3 位表示线程池的状态，低 29 位表示线程池有效线程数。（线程池框架中大量使用此种方式，将多个状态封装到一个字段中，典型如著名的 ForkJoinPool，或许是因为位运算计算效率比较高吧）。

workQueue 是用于保存待执行任务的阻塞队列。workers 是保存了所有工作线程的集合，在此线程池中，工作线程并不是 Thread，而是封装了 Thread 的 Worker 内部类。

构造一个 ThreadPoolExecutor 线程池主要用到类属性中以下**六个参数**：

* **corePoolSize**：核心线程数，表示通常情况下线程池中活跃的线程个数；

* **maximumPoolSize**：线程池中可容纳的最大线程数；
 
* **keepAliveTime**：此参数表示在线程数大于 corePoolSize 时，多出来的线程，空闲时间超过多少时会被销毁；
 
* **workQueue**：存放任务的阻塞队列；
 
* **threadFactory**：创建新线程的线程工厂；
 
* **handler**：线程池饱和（任务过多）时的拒绝策略

```java
    // ctl 整型变量共有 32 位，低 29 位保存有效线程数 workCount，使用
    // 高 3 位表示线程池运行状态 runState。
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));

    // 封装和解封 ctl 中的两个字段
    // 获取 runState
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    // 获取 workerCount
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    // 如果 workerCount 和 runState 分别是两个整数，将它们合并到一个变量里
    private static int ctlOf(int rs, int wc) { return rs | wc; }


    /**
     * 用于保存任务并且将任务交给工作线程的队列。不需要让 workQueue.poll 返回 null
     * 和队列为空划等号，仅仅依赖 workQueue.isEmpty 的结果来判断队列是否为
     * 空即可（例如在判断状态是否从 SHUTDOWN 转变到 TIDYING）。
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * 访问 worker 集合和相关 bookkeeping 持有的锁。虽然可以使用某种类型
     * 的并发集合，但一般使用锁更好。其中一个原因是，它序列化了
     * interruptIdleWorkers，从而避免了不必要的中断风暴，特别是在 shutdown 期间。
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * 包括线程池中所有工作线程的集合。只有在持有 mainLock 时才能访问。
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * 用来支持 awaitTermination 的 condition 队列
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * 最大池容量。只有在持有 mainLock 时才能访问。
     */
    private int largestPoolSize;

    /**
     * 已完成任务的计数器。仅在工作线程终止时更新。只有在持有 mainLock 时才能访问。
     */
    private long completedTaskCount;

    /*
     * 所有的用户控制参数都被声明为 volatile，因此正在进行的操作基于最新的值，
     * 不需要锁定，因为没有内部的不变量依赖于它们的同步改变。
     */

    /**
     * 创建新线程的工厂。所有的线程都是使用这个工厂创建的（通过
     * addWorker 方法）。所有的调用者必须为 addWorker 失败做好准备，
     * 这可能是因为系统或用户的策略限制了线程的数量。即使它不被看成一个
     * 错误，创建线程失败可能会导致新的任务被拒绝或者现有任务留在队列中。
     */
    private volatile ThreadFactory threadFactory;

    /**
     * 线程池饱和或 shutdown 时调用。
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * 等待工作的空闲线程的超时时间。超过 corePoolSize 或 allowCoreThreadTimeOut
     * 时，线程使用。否则，它们将永远等待执行新的任务。
     */
    private volatile long keepAliveTime;

    /**
     * 如果为 false（默认），核心线程即使空闲也保持活动状态。
     * 如果为 true，空闲的核心线程由 keepAliveTime 确定存活时间。
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * 除非设置了 allowCoreThreadTimeOut（在这种情况下，最小值为 0），
     * 否则核心线程池的大小即为线程池中保持活跃的线程数目（不允许超时）。
     */
    private volatile int corePoolSize;

    /**
     * 线程池最多容纳线程数。注意实际的最大值受到 CAPACITY 的限制。
     */
    private volatile int maximumPoolSize;
```

***
> 内部类 Worker

Worker 的属性包含一个线程，线程第一次执行的任务，以及历史任务计数器（用于统计）。由于继承了 AbstractQueuedSynchronizer 抽象类，其自身也可作为锁的实现。

```java
    /**
     * Worker 类主要维护执行任务的线程的中断控制状态，以及其他的
     * bookkeeping 功能。该类扩展了 AQS，以简化获取和释放任务执行时的锁。
     * 这可以防止一些试图唤醒正在等待任务工作线程的中断，而不是防止中断
     * 正在运行的任务。我们实现了一个简单的不可重入独占锁，而不是使用
     * ReentrantLock，因为我们不希望工作线程在调用诸如 setCorePoolSize
     * 之类的线程池控制方法时能重入获取锁。另外，为了在线程真正开始运行
     * 任务之前禁止中断，我们将锁状态初始化为一个负值，并在启动时清除它（在
     * runWorker 中）。
     */
    private final class Worker
            extends AbstractQueuedSynchronizer
            implements Runnable
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** 此 worker 运行的线程，如果创建失败为 null */
        final Thread thread;
        /** 初始执行的任务。可能为 null */
        Runnable firstTask;
        /** 每一个线程的任务计数器 */
        volatile long completedTasks;

        /**
         * 构造函数
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // 0 表示未锁定状态
        // 1 表示锁定状态

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        // 如果线程存在，则中断线程
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }
```

***
> 成员函数

**execute**

在将任务添加到线程池的执行流程中，依次执行以下步骤：

1. 首先检查线程数是否达到核心线程数，如果没有，调用 addWorker 尝试添加新的线程执行该任务，添加成功则返回。

2. 线程数超过核心线程数，尝试将任务加入任务队列中，并检查线程池状态是否是 RUNNING，线程池中是否有线程能够运行此任务。

3. 如果尝试添加任务队列失败，再次尝试添加线程执行此任务，此时如果添加线程失败直接执行拒绝策略。

注意此操作执行过程中多次检查线程池状态，因为此过程中线程池可能被 SHUTDOWN 或发生其它变化。

```java
    /**
     * 在未来的某个时间执行给定的任务。此任务会由一个新的线程或存在于线程池
     * 中的某个线程执行。
     *
     * 如果任务不能提交，要么是因为线程池已经被 shut down，要么是因为
     * 达到了最大容量，由当前的 RejectedExecutionHandler 执行拒绝策略。
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        /*
         * 按以下三个步骤执行：
         *
         * 1. 如果运行的线程数小于 corePoolSize，尝试创建一个新线程，将给定的
         * 任务作为其第一个执行的任务。调用 addWorker 会自动检查 runState
         * 和 workCount，从而通过返回 false 在不应该添加线程的时候发出错误警报。
         *
         * 2. 如果一个任务可以成功地进入队列，我们仍然需要检查是否应该添加
         * 一个新的线程（从任务入队列到入队完成可能有线程死掉，或者线程池
         * 被关闭）。重新检查线程池状态，如果有必要回滚入队操作。如果没有
         * 线程，则添加一个。
         *
         * 3. 如果任务不能入队，再次尝试增加一个新线程，如果添加失败，意味着
         * 池已关闭或已经饱和，此时执行任务拒绝策略。
         */
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        // 线程数超过 corePoolSize
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
    }
```

上述方法中多次调用了添加新线程的核心函数 addWorker。

在 addWorker 函数中，首先自旋操作，检查是否允许添加线程（是否进入 SHUTDOWN 流程和是否达到线程数限制边界）、检查是否成功修改线程池状态。如果满足添加线程的前提条件，且线程数成功增加，则创建一个新的 Worker，将其添加到线程池中。如果添加失败，调用 addWorkerFailed 回滚操作，移除创建失败的 Worker。

```java
    /**
     * 检查是否可以根据当前线程池的状态和给定的边界（核心线程数和最大线程数）
     * 添加新的 worker。如果允许添加，创建并启动一个新的 worker，运行
     * firstTask 作为其第一个任务。如果线程池停止或者即将被 shutdown，则
     * 此方法返回 false。如果线程工厂创建线程失败，也返回 false。如果线程
     * 创建失败，要么是由于线程工厂返回 null，要么是异常（特别是 Thread.start()
     * 的 OOM），将干净利落地回滚。
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            // 获取状态
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            /* 这一句话可以转化为
            if ((rs > SHUTDOWN) ||
                 (rs >= SHUTDOWN && firstTask == null) ||
                 (rs >= SHUTDOWN && workQueue.isEmpty()))
             若线程池状态大于 SHUTDOWN 或者
             （状态大于等于 SHUTDOWN 且 firstTask == null）或者
             （状态大于等于 SHUTDOWN 且 任务队列为空）
             则返回添加失败
             */
            if (rs >= SHUTDOWN &&
                    ! (rs == SHUTDOWN &&
                            firstTask == null &&
                            ! workQueue.isEmpty()))
                return false;

            // 自旋操作对线程数量自增
            for (;;) {
                int wc = workerCountOf(c);
                // 线程数量已经不小于 CAPACITY 或者根据 core 参数判断是否
                // 满足数量限制的要求
                // （core 为 true 时必须小于 corePoolSize；为 false 必须
                // 小于 maximumPoolSize）
                if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                // 使用 CAS 线程数自增，然后退出自旋操作（break 打破外部循环）
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                // 如果 runState 改变了，从外层循环重新开始（continue 继续外层循环）
                if (runStateOf(c) != rs)
                    continue retry;
                // else 继续内层循环
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        // 开始创建新的 Worker
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // 加锁之后再次检查线程池的状态
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int rs = runStateOf(ctl.get());

                    // 如果还没有 SHUTDOWN （即 RUNNING）或者正在
                    // SHUTDOWN 且 firstTask 为空，才可以添加 Worker
                    // 第二种情况没有运行任务，只是添加了线程而已
                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        // 如果线程已经开启了，抛出 IllegalThreadStateException 异常
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        // workers 类型为 HashSet，由于 HashSet 线程不安全，
                        // 所以需要加锁
                        workers.add(w);
                        int s = workers.size();
                        // 更新最大线程池大小 largestPoolSize
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        // 添加成功
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    // 添加成功，开启线程
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                // 开启失败，调用 addWorkerFailed 方法移除失败的 worker
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

addWorkerFailed 方法较简单，只需要从集合中删除创建失败的线程，然后将线程计数减 1 即可。由于 workers 是非线程安全的，所以需要加锁。

回滚操作完成后，调用了 tryTerminate 方法。此方法必须在任何可能导致终止的行为之后被调用，例如减少工作线程数，移除队列中的任务，或者是在工作线程运行完毕后处理工作线程退出逻辑的方法 processWorkerExit。

```java
    /**
     * 回滚创建 Worker 的操作。
     * - 从 workers 中删除该 worker
     * - 减小 worker 计数
     * - 再次检查是否终止，防止它的存在阻止了 termination
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 删除
            if (w != null)
                workers.remove(w);
            // 计数减一
            decrementWorkerCount();
            // 尝试终止
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }
```

**run**

Worker 执行的主要操作（继承自 Runnable，实现 run 方法）在 runWorker 函数中实现。首先获取 firstTask，并将 firstTask 置为 null，然后进入线程漫长的不断执行任务的循环中。如果 firstTask 不为 null，直接执行 firstTask 即可，否则不断从任务队列获取任务（getTask）。循环的终止条件为 firstTask 为 null，且无法从任务队列中获取到任务（没有待执行的任务了）。每一次循环都需要加锁（加自身的锁，每一个 Worker 都是一个锁），防止 shutdown 时停止了此线程。

```java
    /**
     * 主 worker 运行循环。重复从任务队列获取任务并执行，同时处理一些问题：
     *
     * 1. 我们可能是从第一个初始的任务开始的，在这种情况下，不需要获取
     * 第一个。否则，只要线程池状态是 RUNNING，则需要通过 getTask 获取
     * 任务。如果 getTask 返回 null，则工作线程将有线程池状态更改或配置参数
     * 而退出。
     *
     * 2. 在运行任何任务之前，锁被获取以防止任务执行过程中其他的线程池中断
     * 发生，然后确保除非线程池停止，否则线程不会有中断集。
     *
     * 3. 每一个任务运行之前都调用 beforeExecute，这可能会抛出异常，在
     * 这种情况下，不处理任何任务，让线程死亡（用 completedAbruptly 中止
     * 循环）
     *
     * 4. 假设 beforeExecute 正常完成，运行这个任务，收集它抛出的任何异常
     * 并发送给 afterExecute。我们分别处理 RuntimeException, Error 和任意
     * 可抛出的对象。因为我们不能在 Runnable.run 中重新跑出 Throwables，
     * 所以在抛出时将它们封装在 Error 中（到线程的 UncaughtExceptionHandler）
     * 任何抛出的异常也会导致线程死亡。
     *
     * 5. 在 task.run 完成后，调用 afterExecute，它也可能抛出一个异常，这会
     * 导致线程死亡。
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        // 获取当前线程
        Thread wt = Thread.currentThread();
        // 获取当前线程的 task（firstTask）
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 初始化时状态为 -1，此刻设置为 0，表示可以获取锁，并执行任务
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            // 当 task 不为 null 或者从 getTask 取出的任务不为 null 时
            // 不断从任务队列中获取任务来执行
            while (task != null || (task = getTask()) != null) {
                // 加锁，不是为了防止并发执行任务，为了在 shutdown 时不终止
                // 正在运行的 worker
                // worker 本身就是一个锁，那么每个 worker 就是不同的锁
                w.lock();
                // 如果线程被停止，确保需要设置中断位的线程设置了中断位
                // 如果没有，确保线程没有被中断。清除中断位时需要再次检查以
                // 以应对 shutdownNow。
                // 如果线程池状态已经至少是 STOP，则中断
                // Thread.interrupted 判断是否中断，并且将中断状态重置为未中断，
                // 所以 Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)
                // 的作用是当状态低于 STOP 时，确保不设置中断位。
                // 最后再次检查 !wt.isInterrupted() 判断是否应该中断
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        // 执行 Runnable
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    // task 置为 null
                    // 记录完成任务数加一
                    // 解锁
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            // while 执行完毕后设置 completedAbruptly 标志位为 false
            completedAbruptly = false;
        } finally {
            // 1. 将 worker 从数组 workers 里删除掉；
            // 2. 根据布尔值 allowCoreThreadTimeOut 来决定是否补充新的 Worker 进数组workers
            processWorkerExit(w, completedAbruptly);
        }
    }
```

***
> 为什么要使用线程池

1. 由线程池创建、调度、监控和销毁所有线程，控制线程数量，避免出现线程泄露，方便管理。

2. 重复利用已创建的线程执行任务，不需要持续不断地重复创建和销毁线程，降低资源消耗。

3. 直接从线程池空闲的线程中获取工作线程，立即执行任务，不需要每次都创建新的线程，从而提高程序的响应速度。

***
> Executors 和常用线程池



***
> 使用 ThreadPoolExecutor 而不是 Executors 创建线程池


***
> 参考
* [JUC源码分析-线程池篇（一）：ThreadPoolExecutor](https://www.jianshu.com/p/7be43712ef21)
* [ThreadPoolExecutor源码解析](https://www.jianshu.com/p/a977ab6704d7)
* [ThreadPoolExecutor源码剖析](https://blog.csdn.net/qq_30572275/article/details/80543921)
* [java多线程系列：ThreadPoolExecutor源码分析](https://www.cnblogs.com/fixzd/p/9253203.html)