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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.*;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * 一个 ExecutorService 的实现类，使用线程池中的某一个线程执行提交的任务。
 * 通常使用 Executors 工厂方法配置。
 *
 * 线程池解决了两个问题：由于减少了每个人物的调用开销，在执行大量异步
 * 任务时提供了更好的性能，并且还提供了一种绑定和管理资源（包括执行任务
 * 集合时消耗的线程）的方法。每个 ThreadPoolExecutor 还维护一些基本的
 * 统计信息，例如完成的任务总数。
 *
 * 为了在广泛的上下文中有用，此类提供了许多可调整的参数和可扩展钩子。
 * 但是，程序员可以使用更方便的 Executors 工厂方法
 * newCachedThreadPool（无界的线程池，线程自动回收），
 * newFixedThreadPool（固定大小的线程池）和
 * newSingleThreadExecutor（单个后台线程），通过工厂方法的预先配置可以
 * 适应大多数常用的场景。否则，在手动配置和调优该类时，请使用以下指南：
 *
 * > 核心线程池大小（corePoolSize）和最大线程池大小（maximumPoolSize）
 *
 * 一个 ThreadPoolExecutor 会自动调整线程池大小（getPoolSize），根据设置
 * 的 corePoolSize 和 maximumPoolSize。
 *
 * 当使用 execute 方法提交一个新任务，并且运行的线程数小于 corePoolSize 时，
 * 将创建一个新线程来处理请求，即使其他工作线程处于空闲状态。如果有多于
 * corePoolSize 但小于 maximumPoolSize 的线程数在运行，仅当队列已满时
 * 才会创建新线程。通过将 corePoolSize 和 maximumPoolSize 设置成相同值，
 * 可以创建一个固定大小的线程池。通过将  maximumPoolSize 设置成一个基本
 * 无界的值，例如 Integer.MAX_VALUE，允许线程池容纳任意数量的并发任务。
 * 最典型的是，核心和最大线程数量仅在创建时设置，但也可以使用 setCorePoolSize
 * 和 setMaximumPoolSize 动态改变。
 *
 * > 根据需要构建（调用构造函数）
 *
 * 默认情况下，即使是核心线程也只是在新任务到达时才创建和启动，但是可以
 * 使用方法 prestartCoreThread 或者 prestartAllCoreThreads 动态覆盖它。
 * 如果使用非空队列构造线程池，则可能需要预启动线程。
 *
 * > 创建新线程
 *
 * 使用 ThreadFactory 创建新线程。如果没有特别指定，默认使用
 * Executors.defaultThreadFactory，它创建的线程都在同一个 ThreadGroup
 * 中，具有相同的 NORM_PRIORITY 优先级和非守护状态。通过提供不同的
 * ThreadFactory，可以选择线程名称，线程组，优先级，守护线程状态等。
 * 如果一个 ThreadFactory 通过 newThread 创建失败，executor 将继续执行，
 * 但是可能无法执行任何任务。线程应该拥有”modifyThread“（运行时许可）。
 * 如果工作线程或者使用线程池的其他线程不拥有此许可，服务可能会降级：
 * 配置更改可能不会即时生效，shutdown 操作可能仍然处于可能终止但尚未完成
 * 的状态。
 *
 * > 生存时间
 *
 * 如果当前线程池的线程数量超过 corePoolSize，超出的线程如果空闲时间超过
 * keepAliveTime，将会被终止。这提供了一种在线程池没有被积极使用时减少
 * 资源消耗的方法。如果以后线程池变得活跃，将会重新构造新的线程。此参数
 * 可以使用方法 setKeepAliveTime 方法动态更改。
 * 默认情况下，keep-alive 策略仅适用线程数量超过 corePoolSize 的情况。
 * 但是方法 allowCoreThreadTimeOut 也可以将这个超时策略应用到核心线程，
 * 只要 keepAliveTime 的值不为 0。
 *
 * > 队列
 *
 * 任何 BlockingQueue 都可以用来转化和保存提交的任务。此队列和线程池大小
 * 的交互使用：
 * 如果运行的线程数小于 corePoolSize，Executor 总是添加新线程而不是排队。
 * 如果运行的线程数等于或大于 corePoolSize，Executor 总是希望对请求进行
 * 排队，而不是添加新线程。
 * 如果一个请求不能排队，创造一个新线程，除非线程数超过 maximumPoolSize，
 * 在这种情况下，任务将被拒绝。
 *
 * 排队有三种基本策略：
 *
 * 直接传递。工作队列一个很好的默认选择是 SynchronousQueue，它将任务
 * 交给线程，而不需要持有它们。在这里，如果没有立即可用的线程来运行任务，
 * 则对任务进行排队的尝试将失败，因此将构造一个新线程。此策略在处理可能
 * 具有内部依赖的请求集时避免了锁定。直接移交通常需要无界限的
 * maximumPoolSizes 参数来避免拒绝新提交的任务。反过来，当任务持续到达
 * 的平均速度比它们被处理的速度还要快时，就可能出现无限的线程增长。
 * （导致 OOM）。
 *
 * 无界队列。使用无界队列（例如没有预定义容量的 LinkedBlockingQueue）将
 * 导致新任务在队列中等待，而所有的 corePoolSize 内的线程处于忙碌状态。
 * 因此，创建的线程数量不会超过 corePoolSize。（此时 maximumPoolSize 参数
 * 的设定不会对线程池产生任何影响。）当每个任务完全独立于其它任务的时候，
 * 这可能是合适的，任务不会影响其它任务的执行。虽然这种类型的队列在平滑
 * 短暂的请求突发方面很有用，但必须承认，当任务持续到达的平均速度比它们
 * 被处理的速度还要快时，可能会出现无界队列的无限增长（导致 OOM）。
 *
 * 有界队列。有界队列（例如 ArrayBlockingQueue）有助于防止在使用有限的
 * maximumPoolSizes 时耗尽资源，但可能更难调优和控制。队列大小和线程池
 * 大小的最大值可以互相交换：使用大的队列和小的线程池可以让 CPU 的使用，
 * 操作系统资源，上下文切换开销最小化，但是会降低吞吐量。如果任务频繁被
 * 阻塞，系统可能会为线程执行安排更多时间。使用小的队列需要更大的线程池，
 * 这将会使 CPU 更忙，但可能会遇到无法接受的调度开销，这也会降低吞吐量。
 *
 * > 拒绝策略
 * 使用方法 execute 提交的新任务将在 Executor 被 shutdown 或者线程数达到
 * 最大容量且工作队列达到最大容量时被拒绝。无论在哪种情况下，execute 方法
 * 都将调用
 * RejectedExecutionHandler.rejectedExecution(Runnable, ThreadPoolExecutor)。
 * 提供里四种预定义的处理程序策略：
 *
 * 在默认的 ThreadPoolExecutor.AbortPolicy 中，处理程序拒绝时抛出
 * RejectedExecutionException 异常。
 *
 * 在 ThreadPoolExecutor.CallerRunsPolicy 中，调用 execute 的线程本身运行
 * 任务，这提供了一个简单的反馈控制机制，可以降低新任务的提交速度。
 *
 * 在 ThreadPoolExecutor.DiscardPolicy 中，无法执行的任务将被删除。
 *
 * 在 ThreadPoolExecutor.DiscardOldestPolicy 中，如果 executor 没有被
 * shutdown，工作队列头部的任务会被删除，然后重试 execution。（可能会
 * 再次失败，导致重复执行。）
 *
 * 可以定义和使用其他类型的 RejectedExecutionHandler 类。这样做需要谨慎，
 * 特别是当策略设计为仅在特定容量或队列下工作时。
 *
 * > 钩子方法
 *
 * 此类提供了 protected 类型的可重写 beforeExecute 和 afterExecute 方法，
 * 在执行任务之前和之后调用。这些函数可以用来操作运行环境，例如，重新
 * 初始化 ThreadLocal，手机统计信息，或者添加日志条目。此外，可以重写
 * terminated 方法来执行任何 Executor 终止之后需要执行的特殊处理。
 *
 * 如果钩子或者毁掉方法抛出异常，内部工作线程可能会失败并突然终止。
 *
 * > 队列维护
 *
 * 方法 getQueue 允许访问工作队列，用于监控和调试。强烈反对将此方法用于
 * 任何其他目的。提供的两个方法 remove 和 purge 可用于在大量派对任务被
 * 取消时帮助回收内存空间。
 *
 * > 终结
 *
 * 程序中不再引用并且没有剩余线程的线程池将自动被 shutdown。如果希望确保
 * 即使用户忘记调用 shutdown 也能回收未引用的线程池，那么必须通过设置
 * 合适的 keep-alive 时间、设置核心线程数为 0、或者设置 allowCoreThreadTimeOut
 * 来安排未使用的线程最终死亡。
 *
 * 扩展示例。该类的大多数扩展都会重写一个或多个 protected 钩子函数。例如，
 * 下面的子类，添加了一个简单的暂停/恢复功能：
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * 主要的线程池控制状态 ctl 是一个原子型整数，它包括两个概念字段：
     * workCount，表示有效线程的数量
     * runState，表示线程池是否 running，shutdown 等
     *
     * 为了将它们打包成一个 int 型，我们将 workerCount 限制成 (2^29)-1
     * （大约 5 亿）个线程，而不是(2^31)-1 （大约 20 亿）个线程。如果将来
     * 出现问题，可以将变量变成 AtomicLong 类型，并调整下面的 shift/mask
     * 常量。如果不是一定需要，避免这样做，因为使用 int 可以使这段代码更快
     * 更简单。
     *
     * workerCount 是允许开始和不允许停止的 worker 数量。这个值可能与实际
     * 的活动线程数有短暂的不同，例如当 ThreadFactory 在被请求时没有创造
     * 线程，或者在退出的线程终止之前仍然记录在册。用户可见的线程池大小是
     * 当前的 worker 集合的大小。
     *
     * runState 提供整个生命周期的控制，有以下值：
     *
     *   RUNNING:  接受新的任务，处理等待队列中的任务
     *   SHUTDOWN: 不接受新的任务，但处理等待队列中的任务
     *   STOP:     不接受新的任务，不处理等待的任务，而且还要中断正在运行的任务
     *   TIDYING:  所有的任务都停止了，workCount 等于 0，过渡到 TIDYING
     *                      的线程将运行 terminated 钩子函数
     *   TERMINATED: terminated() 完成
     *
     * 这些值之间的数字顺序很重要，以便进行有序的比较。runState 随时单调
     * 增加，但不必触及每个状态。转换如下：
     * RUNNING -> SHUTDOWN
     *    调用 shutdown，或许在 finalize 隐式发生
     * (RUNNING or SHUTDOWN) -> STOP
     *    调用 shutdownNow
     * SHUTDOWN -> TIDYING
     *    队列和线程池都为空的时候
     * STOP -> TIDYING
     *    线程池为空的时候
     * TIDYING -> TERMINATED
     *    terminated 钩子函数执行完成的时候
     *
     * 当状态到达 TERMINATED 的时候，在 awaitTermination 中等待的函数会返回
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */

    // ctl 整型变量共有 32 位，低 29 位保存有效线程数 workCount，使用
    // 高 3 位表示线程池运行状态 runState。
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
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

    // Packing and unpacking ctl
    // 获取 runState
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    // 获取 workerCount
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    // 如果 workerCount 和 runState 分别是两个整数，将它们合并到一个变量里
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */

    // 线程池状态 c 小于 s 时返回 true
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    // 线程池状态 c 至少为 s 时返回 true
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    // 如果 c 小于 SHUTDOWN 说明是 RUNNING 状态
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * CAS 方式将 workerCount 加 1
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * CAS 方式将 workerCount 减 1
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * workCount 减 1
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

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

    /**
     * 默认拒绝策略。
     */
    private static final RejectedExecutionHandler defaultHandler =
            new AbortPolicy();

    /**
     * 针对 shutdown 和 shutdownNow 的运行权限许可。
     */
    private static final RuntimePermission shutdownPerm =
            new RuntimePermission("modifyThread");

    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

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
            // 设置此 AQS 的状态
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            // 创建新的线程，传入 this，也就是执行当前 Worker 的 run 函数
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

    /*
     * 设置控制状态的方法
     */

    /**
     * 将 runState 转变成给定参数，如果已经是给定参数则不进行转换。
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * 如果当前状态为（SHUTDOWN 且 线程池和队列为空）或者（STOP
     * 且线程池为空）,转换到 TERMINATED 状态，。如果有资格终止，但 workerCount
     * 不是零，中断空闲的线程，以确保 shutdown 的信号传播。此方法必须在
     * 任何可能导致终止的动作之后调用——以减少工作线程数量或在 shutdown
     * 期间从队列中删除任务。此方法是非私有的，ScheduledThreadPoolExecutor
     * 也可以访问。
     *
     * 如果线程池状态为 RUNNING 或 （TIDYING 或 TERMINATED）或
     * （SHUTDOWN 且任务队列不为空），不终止或执行任何操作，直接返回。
     *
     * tryTerminate 用于尝试终止线程池，在 shutdow、shutdownNow、remove
     * 中均是通过此方法来终止线程池。此方法必须在任何可能导致终止的行为
     * 之后被调用，例如减少工作线程数，移除队列中的任务，或者是在工作线程
     * 运行完毕后处理工作线程退出逻辑的方法 processWorkerExit。
     * 如果线程池可被终止（状态为 SHUTDOWN 并且等待队列和池任务都为空，
     * 或池状态为 STOP 且池任务为空），调用此方法转换线程池状态为 TERMINATED。
     * 如果线程池可以被终止，但是当前工作线程数大于 0，则调用
     * interruptIdleWorkers方法先中断一个空闲的工作线程，用来保证池
     * 关闭操作继续向下传递。
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            // 线程池状态为 RUNNING 或 （TIDYING 或 TERMINATED）或
            // （SHUTDOWN 且任务队列不为空），直接返回
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            // 如果工作线程数 workCount 不为 0，调用函数关闭一个空闲线程，然后返回
            // (只关闭一个的原因我猜是遍历所有的 worker 消耗太大。)
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            // 线程数为 0 且（状态为 STOP 或者（状态为 SHUTDOWN 且
            // 任务队列为空）），此处为线程池状态转化图中满足 SHUTDOWN
            // 或 STOP 转化到 TIDYING 的情况。
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 如果成功将状态转化成了 TIDYING，在调用 terminated 方法完成
                // 将 TIDYING 转化到 TERMINATED 的后续操作
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        // 最后将状态设为 TERMINATED 即可
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * 控制工作线程中断的方法。
     */

    /**
     * 如果有安全管理器，确保调用者具有 shutdown 线程的权限。如果通过了，
     * 还要确保调用者可以中断每一个工作线程。
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * 中断所有线程，即使线程仍然活跃。忽略 SecurityExceptions （防止一些
     * 线程没有被中断）
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 中断可能正在等到任务的线程（空闲线程），以便他们可以检查终止或
     * 配置更改。忽略 SecurityExceptions （防止一些线程没有被中断）
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // 如果线程没有被中断且能获取到锁（能获取到说明它很闲，因为在
                // 正常执行任务的线程都已经获取到锁了），
                // 则尝试中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                // 如果 onlyOne 为 true，仅中断一个线程
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * interruptIdleWorker 的普通版本
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * 为给定的 Runnable 调用拒绝执行程序（拒绝策略）。
     * 此函数是包访问权限，可以被 ScheduledThreadPoolExecutor 调用。
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * 用于 ScheduledThreadPoolExecutor 中检查状态，在 shutdown 期间让
     * 任务运行。
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * 将任务添加到新的列表，通常使用阻塞队列的 drainTo 方法。但是如果队列
     * 是 DelayQueue 或者任何其他类型的队列，以至于 poll 或者 drainTo 可能会
     * 删除失败，那么将逐个删除这些元素。
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

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

            // 自旋操作增加 state 中线程数量
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

        // 状态修改成功，可以开始创建新的 Worker 了
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
                    // 加锁之后再次检查线程池的状态，防止加锁过程中状态被修改
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

    /**
     * 为正在死亡的 worker 清理和登记。仅限工作线程调用。除非设置了 completedAbruptly，
     * 否则假定 workCount 已经被更改了。此方法从 worker 集合中移除线程，
     * 如果线程因任务异常而退出，或者运行的工作线程数小于 corePoolSize，
     * 或者队列非空但没有工作线程，则可能终止线程池或替换工作线程。
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果此变量为 true，需要将 workerCount 减一
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 移除当前线程
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();

        int c = ctl.get();
        // 线程池状态小于 STOP，没有终止
        if (runStateLessThan(c, STOP)) {
            // 如果原线程不是因为异常退出的，需要进入此 if 块判断
            // 当 worker 没有任务可执行而退出循环时，completedAbruptly 的值为 false
            if (!completedAbruptly) {
                // 如果 allowCoreThreadTimeOut 为 true，就算是核心线程，只要空闲，
                // 都要移除
                // min 获取当前核心线程数
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                // 等待队列不为空，且没有工作线程
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                // 工作线程数大于核心线程数，直接返回（删了就删了，没有影响）
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 原线程不应该被删除，应该添加新的线程替换它
            addWorker(null, false);
        }
    }

    /**
     * 获取等待队列中的任务。基于当前线程池的配置来决定执行任务阻塞或等待
     * 或返回 null。在以下四种情况下会引起 worker 退出，并返回 null：
     * 1. 工作线程数超过 maximumPoolSize。
     * 2. 线程池已停止。
     * 3. 线程池已经 shutdown，且等待队列为空。
     * 4. 工作线程等待任务超时。
     *
     * 并不仅仅是简单地从队列中拿到任务就结束了。
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            // 还是首先获取线程池状态
            int c = ctl.get();
            int rs = runStateOf(c);

            // 首先根据状态判断当前线程该不该存活
            // 状态为以下两种情况时会 workerCount 减 1，并返回 null
            // 1. 状态为 SHUTDOWN，且 workQueue 为空
            // （说明在 SHUTDOWN 状态线程池中的线程还是会继续取任务执行）
            // 2. 线程池状态为 STOP
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
                // 返回 null，runWorker 中当前线程会退出 while 循环，然后执行
                // processWorkerExit
            }

            int wc = workerCountOf(c);

            // 然后根据超时限制和核心线程数判断当前线程该不该存活
            // timed 用于判断是否需要超时控制。
            // 在 allowCoreThreadTimeOut 中设置过或者线程数超过核心线程数了
            // 就需要超时控制。
            // allowCoreThreadTimeOut 表示就算是核心线程也会超时
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            // 1. 线程数超过最大线程数的限制了（运行过程中修改过 maximumPoolSize）
            // 或者已经超时（timed && timedOut 为 true 表示需要进行超时控制
            // 且已经超时）
            // 2. 线程数 workerCount 大于 1 或者任务队列为空
            if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // workQueue.poll 表示如果在 keepAliveTime 时间内阻塞队列还是没有任务，则返回 null
                // timed 为 true 则调用有时间控制的 poll 方法进行超时控制，否则通过
                // take 方法获取
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                // 获取到任务，立即返回
                if (r != null)
                    return r;
                // 如果 r 等于 null，说明已经超时，设置 timedOut 为 true，在下次
                // 自旋时回收
                timedOut = true;
            } catch (InterruptedException retry) {
                // 发生中断，设置成没有超时，并继续执行
                timedOut = false;
            }
        }
    }

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
        // 获取当前线程（也就是 Worker）
        Thread wt = Thread.currentThread();
        // 获取当前 Worker 的 task（firstTask）
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
                // 如果线程池状态已经至少是 STOP，则中断当前线程。
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

    // 公共的构造函数和方法

    /**
     * 使用给定的初始参数和默认的线程工厂以及拒绝策略执行程序构造一个
     * ThreadPoolExecutor 实例。使用 Executors 工厂方法而不是这个通用函数
     * 可能会更方便。
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * 根据给定的参数和默认的拒绝策略构造新的 ThreadPoolExecutor。
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    /**
     * 根据给定的参数和默认的拒绝策略构造新的 ThreadPoolExecutor。
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     * 根据给定的参数构造新的 ThreadPoolExecutor。
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

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

    /**
     * 启动有序的 shutdown，在此过程中执行以前已经提交的任务，但不接受新
     * 的任务。如果已经 shutdown，调用将没有其它效果。
     *
     * 此方法不等待以前提交的任务完成执行。使用 awaitTermination 来完成。
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 检查有没有权限
            checkShutdownAccess();
            // 将状态转变为参数中指定的状态，此处为 SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 终止所有空闲线程
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * 尝试停止所有正在执行的任务，停止等待线程，并返回等待执行的任务列表。
     * 从此方法返回时，将从任务队列中删除这些任务。
     *
     * 此方法不会等待正在活跃执行的任务终止。使用 awaitTermination 来完成。
     *
     * 除了尽最大努力停止处理正在执行的任务之外，没有任何其他承诺。此实现
     * 通过 Thread.interrupt 来取消任务，所以没有响应中断的任务可能永远不会
     * 终止。
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 检查权限
            checkShutdownAccess();
            // 将状态变成 STOP
            advanceRunState(STOP);
            // 中断 Worker
            interruptWorkers();
            // 调用 drainQueue 将队列中未处理的任务移到 tasks 里
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    // 是否还是 running 状态
    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * 如果线程池在 shutdown 或 shutdownNow 结束后仍未完全终止，
     * 则返回 true
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    // 已经完成终止过程
    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    // 自旋等待 timeout 这么长的时间，如果完成 TERMINATED，返回 true，
    // 否则时间到期返回 false。
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 线程池不再被引用且没有任何线程时，调用 shutdown 清理它。
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> { shutdown(); return null; };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * 设置用来创建新线程的线程工厂
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * 返回用来创建新线程的 ThreadFactory
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * 设置线程池的拒绝策略
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * 返回线程池的拒绝策略
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * 设置核心线程数（参数 corePoolSize）。如果新的值比当前值要小，超过的
     * 部分在它们下次空闲的时候会被终止。如果更大，新的线程，会被开启。
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * 返回设置的核心线程数量参数 corePoolSize 的值
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 启动一个核心线程，让他等待任务。这将覆盖只有在执行任务时才启动核心
     * 线程的默认策略。如果所有的核心线程都已经启动，这个方法将返回 false。
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
                addWorker(null, true);
    }

    /**
     * 和 prestartCoreThread 相同，不同点是，即使 corePoolSize 设置为 0，
     * 也会至少启动一个线程。
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * 启动所有的核心线程，让它们等待执行任务。这将覆盖只有在执行新任务时
     * 才启动核心线程的默认策略。
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * 如果此线程池允许核心线程等待超时并且在 keepAlive 时间内没有任务到达
     * 时终止，则返回 true。如果新任务到达时该策略需要替换则替换。如果返回了 true，
     * 应用于非核心线程的 keep-alive 策略也适用于核心线程。如果为 false（默认值），
     * 核心线程将不会因为没有任务而终止。
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * 设置核心线程是否允许超时并是否在 alive 的时间内没有任务到达而终止的策略，
     * 以及该策略在新任务到达时是否需要替换。如果不终止，核心线程将不会
     * 因为缺少任务而终止。如果要终止，应用于非核心线程的 keep-alive 策略
     * 也适用于核心线程。为了避免持续的线程替换，keep-alive 的时间必须大于 0。
     * 通常在线程池线程被频繁使用之前调用此方法。
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * 设置允许的最大线程数（参数 maximumPoolSize）。这将覆盖构造函数中
     * 设置的该值。如果新值小于当前值，多出来的线程将在它们空闲的时候被终止。
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * 返回参数 maximumPoolSize 的值。
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * 设置线程在终止之前 keep-alive 的时间。
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * 返回线程的 keep-alive 时间，这是超过核心线程数的线程在终止之前保持
     * 空闲的时间。
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */
    // 用户层面的队列工具

    /**
     * 返回此线程池使用的任务队列。对任务队列的访问主要用于调试和监视。
     * 返回的队列可能仍处于活动状态。检索遍历任务队列并不会组织任务队列的执行。
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * 如果存在此任务，则将其从任务队列中删除。如果任务尚未开始运行，
     * 则永远不会运行了。
     *
     * 此方法可作为取消方案的一部分使用。在将任务放入任务队列之前，可能
     * 无法取消已经转换为其他表单的任务。例如，使用 submit 输入的任务可能
     * 被转换为维护 Future 状态的表单。但是，在这种情况下，可以使用方法
     * purge 删除那些被取消的 Future。
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * 尝试从工作队列中删除所有已被取消的 Future 任务。此方法可用于内存
     * 回收操作，而不会对功能产生其他影响。被取消的任务永远不会执行，但是
     * 可能会累积在工作队列中，直到工作线程主动删除它们。现在调用此方法
     * 来删除它们。但是，如果存在其他线程的干扰，此方法可能无法删除任务。
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            // 迭代器遍历删除 Future
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * 返回线程池中当前线程数。
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            // 直接调用任务队列的 size 方法
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回活跃的工作线程数量的估计值
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            // 如果锁被获取，说明它正在执行任务
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回线程池中曾经同时出现的线程数量最大值
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回计划执行过的任务和即将执行任务的总数。由于任务和线程状态可能
     * 在计算期间动态变化，所以返回的值只是一个近似值。
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 计算已经完成的任务总数
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            // 返回任务总数加上队列中待执行的任务数
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回已执行完成的任务的大致总数。因为任务和线程的状态在计算期间可能
     * 会动态变化，所以返回的值只是一个近似值，但是在连续调用期间不会减少。
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                        "Shutting down"));
        return super.toString() +
                "[" + rs +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }

    /* Extension hooks */
    // 钩子函数

    /**
     * 方法在给定线程执行给定的 Runnable 之前调用。此方法由线程 t 调用，该
     * 线程执行任务 r，并可用于重新初始化 ThreadLocal，或执行日志记录。
     *
     * 此实现什么也不做，但是可以在子类中定制。注意：为了正确嵌套多个覆盖，
     * 子类通常应该在方法末尾调用 super.beforeExecute。
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * 在完成给定的 Runnable 执行后调用的方法。此方法由执行任务的线程调用。
     * 如果非空，抛出的是未捕获的 RuntimeException 或 Error，将导致执行中止。
     *
     * 此实现什么也不做，但是可以在子类中定制。注意：为了正确嵌套多个覆盖，
     * 子类通常应该在方法末尾调用 super.afterExecute。
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * 当线程池被终止时调用的方法。
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */
    // 预定义的拒绝策略

    /**
     * 被拒绝任务的处理程序，直接在 execute 方法的调用线程中运行被拒绝的
     * 任务，除非线程池已经被 shutdown 了，在这种情况下被丢弃。
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * 创建 CallerRunsPolicy
         */
        public CallerRunsPolicy() { }

        /**
         * 在调用者的线程中执行任务 r，除非执行者被 shutdown，这种情况下
         * 任务被忽略。
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // 如果线程池没有被 shut down，则直接运行
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * 直接抛出 RejectedExecutionException 异常
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * 创建 AbortPolicy。
         */
        public AbortPolicy() { }

        /**
         * 总是抛出 RejectedExecutionException 异常。
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " +
                    e.toString());
        }
    }

    /**
     * 直接忽略，不会抛出任何异常
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * 创建 DiscardPolicy。
         */
        public DiscardPolicy() { }

        /**
         * 直接忽略。
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * 将任务队列中最老的未处理请求删除，然后 execute 任务。除非线程池被
     * shut down，这种情况下任务被丢弃。
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * 创建 DiscardOldestPolicy。
         */
        public DiscardOldestPolicy() { }

        /**
         * 获取并忽略线程池中下一个将执行的任务（任务队列中最老的任务），
         * 然后重新尝试执行任务 r。除非线程池关闭，在这种情况下，任务 r 将被
         * 丢弃。
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}

