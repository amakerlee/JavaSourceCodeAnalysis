## ScheduledThreadPoolExecutor

### 继承结构及完整源码解析

[Executor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/Executor.java) | [ExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ExecutorService.java) | [AbstractExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/AbstractExecutorService.java) | [ThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ThreadPoolExecutor.java) | [ScheduledExecutorService](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ScheduledExecutorService.java) | [ScheduledThreadPoolExecutor](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/ScheduledThreadPoolExecutor.java)

<img src="https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/images/ScheduledThreadPoolExecutor.png" width=70% />

### 内部类 ScheduledFutureTask

ScheduledFutureTask 实现了 Runnable、Future、Delayed 接口，任何 Runnable 接口提交到线程池的时候都会被包装成 ScheduledFutureTask。

```java
    // 可以延迟执行的异步运算任务
    private class ScheduledFutureTask<V>
            extends FutureTask<V> implements RunnableScheduledFuture<V> {

        // 为相同延时任务提供的顺序编号。
        // 两个任务有相同的延迟时间时，按照 FIFO 的顺序入队。
        private final long sequenceNumber;

        // 任务下一次可以执行的时间，纳秒级
        private long time;

        /**
         * 重复任务执行的周期，纳秒级
         * 正数表示 scheduleAtFixedRate，负数表示 scheduleWithFixedDelay
         */
        private final long period;

        // 重新入队的任务，通过 reExecutePeriodic 重新入队排序。
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * 在堆中的索引，以支持更快的取消操作
         */
        int heapIndex;

        /**
         * 构造函数，延迟任务
         */
        ScheduledFutureTask(Runnable r, V result, long ns) {
            super(r, result);
            this.time = ns;
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * 构造函数，定时任务
         */
        ScheduledFutureTask(Runnable r, V result, long ns, long period) {
            super(r, result);
            this.time = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * 构造函数
         */
        ScheduledFutureTask(Callable<V> callable, long ns) {
            super(callable);
            this.time = ns;
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        // 还需要等待多久
        public long getDelay(TimeUnit unit) {
            return unit.convert(time - now(), NANOSECONDS);
        }

        // 比较执行的顺序
        public int compareTo(Delayed other) {
            if (other == this) // compare zero if same object
                return 0;
            if (other instanceof ScheduledFutureTask) {
                ScheduledFutureTask<?> x = (ScheduledFutureTask<?>)other;
                long diff = time - x.time;
                if (diff < 0)
                    return -1;
                else if (diff > 0)
                    return 1;
                else if (sequenceNumber < x.sequenceNumber)
                    return -1;
                else
                    return 1;
            }
            long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
            return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
        }

        /**
         * 如果这是定时任务则返回 true。
         *
         * @return {@code true} if periodic
         */
        public boolean isPeriodic() {
            return period != 0;
        }

        /**
         * 设置下一次执行的时间
         */
        private void setNextRunTime() {
            long p = period;
            if (p > 0)
                time += p;
            else
                time = triggerTime(-p);
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && removeOnCancel && heapIndex >= 0)
                remove(this);
            return cancelled;
        }

        /**
         * 执行
         */
        public void run() {
            boolean periodic = isPeriodic();
            if (!canRunInCurrentRunState(periodic))
                cancel(false);
            else if (!periodic)
                ScheduledFutureTask.super.run();
            else if (ScheduledFutureTask.super.runAndReset()) {
                setNextRunTime();
                reExecutePeriodic(outerTask);
            }
        }
    }
```

### 重要方法

#### schedule

达到给定延迟时间后执行任务。

把 Runnable 包装成 ScheduledFutureTask 类型的任务，然后调用 delayedExecute 处理。

```java
    /**
     * 达到给定的延迟时间后，执行任务
     * ScheduledFutureTask 是 RunnableScheduledFuture 的实现类
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay,
                                       TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        // 创造
        RunnableScheduledFuture<?> t = decorateTask(command,
                new ScheduledFutureTask<Void>(command, null,
                        triggerTime(delay, unit)));
        delayedExecute(t);
        return t;
    }
```

**delayedExecute**

从 delayExecute 中可以看出任务被提交之后的执行流程如下：

* 检查线程池是否被 shutdown 了，如果线程池不再是 RUNNING 状态，直接执行拒绝策略。

* 直接将任务添加到阻塞队列中。

* 如果核心线程池还没有满，创建一个工作线程；如果核心线程数被设置为 0，也会创造一个工作线程，因为任务必须由线程来执行。

```java
    /**
     * 延迟执行。
     *
     * @param task the task
     */
    private void delayedExecute(RunnableScheduledFuture<?> task) {
        // 如果还是 RUNNING 状态
        if (isShutdown())
            reject(task);
        else {
            // 直接把任务加入到队列中
            super.getQueue().add(task);
            // 如果已经停止了，删除任务
            if (isShutdown() &&
                    !canRunInCurrentRunState(task.isPeriodic()) &&
                    remove(task))
                task.cancel(false);
            else
                // 添加一个工作线程
                ensurePrestart();
        }
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
```

#### scheduleAtFixedRate

定时任务。从上一个任务开始时计时，指定时间间隔过去后，如果上一个任务已经执行完毕，马上开始下一个任务，如果没有执行完毕，等上一个任务执行完后开启下一个任务。

```java
    /**
     * 定时执行。
     * 从上一个任务开始时计时，指定时间间隔过去后，如果上一个任务已经执行完毕，
     * 马上开始下一个任务，如果没有执行完毕，等上一个任务执行完后开启下一个任务。
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (period <= 0)
            throw new IllegalArgumentException();
        // 创建 task
        ScheduledFutureTask<Void> sft =
                new ScheduledFutureTask<Void>(command,
                        null,
                        triggerTime(initialDelay, unit),
                        unit.toNanos(period));
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);
        sft.outerTask = t;
        delayedExecute(t);
        return t;
    }
```

**ScheduledFutureTask.run**

执行完任务之后，计算下一次应该运行的时间，并把任务重新加入到队列中等待。

```java
        /**
         * 执行
         */
        public void run() {
            boolean periodic = isPeriodic();
            if (!canRunInCurrentRunState(periodic))
                cancel(false);
            // 不是定时任务，直接 run
            else if (!periodic)
                ScheduledFutureTask.super.run();
            // 调用 FutureTask 的 runAndReset，并设置下一次执行时间
            else if (ScheduledFutureTask.super.runAndReset()) {
                setNextRunTime();
                // 重新进入队列
                reExecutePeriodic(outerTask);
            }
        }
```

**setNextRunTime**

从这个函数中可以看到，下一次的时间为 time + period。

如果任务由于某些原因执行时间超过了 period，那么 time + period 时间甚至会早于当前时间。进入队列之后，将会排在队列的头部（至少应该是靠前位置），如果工作线程足够多的话，任务将会被马上执行。

需要注意的一点是，时间间隔是从任务开始执行的时间开始算起，而不是任务结束的时间。

```java
        /**
         * 设置下一次执行的时间
         */
        private void setNextRunTime() {
            long p = period;
            if (p > 0)
                time += p;
            else
                time = triggerTime(-p);
        }
```

#### scheduleWithFixedDelay

达到延迟之后开始定期执行任务。上一个任务执行结束后到下一个任务开始之间，时间间隔为指定参数 delay。和 scheduleAtFixedRate 基本一样。

```java
    /**
     * 达到延迟之后开始定期执行任务。
     * 上一个任务执行结束后到下一个任务开始之间，时间间隔为指定参数 delay。
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (delay <= 0)
            throw new IllegalArgumentException();
        ScheduledFutureTask<Void> sft =
                new ScheduledFutureTask<Void>(command,
                        null,
                        triggerTime(initialDelay, unit),
                        unit.toNanos(-delay));
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);
        sft.outerTask = t;
        delayedExecute(t);
        return t;
    }
```

### shutdown

根据参数判断是否保留队列中的任务。延迟任务默认保留。

```java
    /**
     * 取消由于 shutdown 不应该运行的全部任务
     */
    @Override void onShutdown() {
        BlockingQueue<Runnable> q = super.getQueue();
        // 线程池关闭后是否继续执行已存在的延迟任务。默认为 true。
        boolean keepDelayed =
                getExecuteExistingDelayedTasksAfterShutdownPolicy();
        // 线程池关闭后是否继续执行已存在的周期任务。
        boolean keepPeriodic =
                getContinueExistingPeriodicTasksAfterShutdownPolicy();
        // 池关闭后不保留任务
        if (!keepDelayed && !keepPeriodic) {
            // 取消队列中的所有任务
            for (Object e : q.toArray())
                if (e instanceof RunnableScheduledFuture<?>)
                    ((RunnableScheduledFuture<?>) e).cancel(false);
            q.clear();
        }
        else {
            // 遍历快照避免迭代器异常
            for (Object e : q.toArray()) {
                if (e instanceof RunnableScheduledFuture) {
                    RunnableScheduledFuture<?> t =
                            (RunnableScheduledFuture<?>)e;
                    // 是周期任务，根据 keepPeriodic 判断
                    // 是延迟任务，根据 keepDelay 判断
                    if ((t.isPeriodic() ? !keepPeriodic : !keepDelayed) ||
                            t.isCancelled()) { // also remove if already cancelled
                        if (q.remove(t))
                            t.cancel(false);
                    }
                }
            }
        }
        // 尝试终止
        tryTerminate();
    }
```

### 小结

* 构造函数调用了父类（ThreadPoolExecutor）的构造方法，可传入的参数只有核心线程数，线程工厂，拒绝策略。虽然默认最大线程数传入 Integer.MAX_VALUE，但实际 maximumPoolSize 参数对线程池没有任何影响，从 delayedExecute 的方法中也可以看出，限制线程数量的也只有核心线程数而已。（如果核心线程数为 0，也会创造一个线程执行任务。）

* Executors 中有以下两个方法可以用来便捷地构造 ScheduledThreadPoolExecutor：
  
  - newScheduledThreadPool: 可指定核心线程数的线程池。
  - newSingleThreadScheduledExecutor: 永远只有一个工作线程的线程池。

### 参考

[JUC源码分析-线程池篇（三）：ScheduledThreadPoolExecutor](https://www.jianshu.com/p/8c97953f2751)
