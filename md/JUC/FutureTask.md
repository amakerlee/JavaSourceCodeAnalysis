## FutureTask

异步执行有返回值的任务，可以获取到返回值。FutureTask 实现了 Runnable 和 Future 接口。

### Future 接口

FutureTask 的语义基本就是 Future 的语义。FutureTask 实现了 Future 中所有的方法。

```java
public interface Future<V> {

    /**
    * 尝试取消任务。
    */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
    * 如果在执行完之前任务被取消返回 true。
     */
    boolean isCancelled();

    /**
     * 如果任务执行计数返回 true。
     */
    boolean isDone();

    /**
    * 线程等待直到任务执行完成，并返回结果。
    */
    V get() throws InterruptedException, ExecutionException;

    /**
    * 有时间限制的 get 方法。
    */
    V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

### 类属性

state 状态有七种可能的值，从名字基本可以判断每一种状态代表什么。正常情况下状态变化的路径是 NEW -> COMPLETING -> NORMAL，NEW 是最普遍的状态。COMPLETING 状态表示任务已经执行完成或发生异常，但结果还没有保存到 outcome。

[JUC线程池: FutureTask详解](https://www.pdai.tech/md/java/thread/java-thread-x-juc-executor-FutureTask.html) 中有每种状态详细的描述和图解，本文中不再赘述。

除了 state 之外，callable 表示将要执行的任务，outcome 存储返回的结果，runner 表示执行任务的线程。调用 get 方法是如果 outcome 还没有结果，那么线程将会在 waiters 中等待，直到被唤醒。

```java
    /**
     * 此任务的状态存放在 state 中。
     *
     * 状态变化路径由以下四种:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state;
    private static final int NEW          = 0;
    private static final int COMPLETING   = 1;
    private static final int NORMAL       = 2;
    private static final int EXCEPTIONAL  = 3;
    private static final int CANCELLED    = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED  = 6;

    // 内部持有的 callable 任务，运行完毕后置为空
    private Callable<V> callable;
    // 从 get() 中返回的结果或抛出的异常
    private Object outcome;
    // 运行 Callable 的线程
    private volatile Thread runner;
    // Treiber 栈（无所并发栈）保存等待线程（等待获取结果的线程）
    private volatile WaitNode waiters;
```

### AbstractExecutorService.submit

线程池中调用 submit 方法执行 future 类型的任务。可以看到在 submit 方法中构造了一个 FutureTask，然后调用 execute 把 FutureTask 放进线程池中，返回 FutureTask。

```java
public Future<?> submit(Runnable task) {
    if (task == null) throw new NullPointerException();
    RunnableFuture<Void> ftask = newTaskFor(task, null);
    execute(ftask);
    return ftask;
}
public <T> Future<T> submit(Runnable task, T result) {
    if (task == null) throw new NullPointerException();
    RunnableFuture<T> ftask = newTaskFor(task, result);
    execute(ftask);
    return ftask;
}
public <T> Future<T> submit(Callable<T> task) {
    if (task == null) throw new NullPointerException();
    RunnableFuture<T> ftask = newTaskFor(task);
    execute(ftask);
    return ftask;
}
protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return new FutureTask<T>(runnable, value);
}
public FutureTask(Runnable runnable, V result) {
    this.callable = Executors.callable(runnable, result);
    this.state = NEW;       // ensure visibility of callable
}
```

### 成员函数

其实 Future 类型的任务和简单的 Runnable 类型的任务功能差不多，就是多了个 get 方法，可以获取结果。任务的执行都是依赖 run 方法完成。

#### 运行

**run**

获取当前状态，如果是 NEW，尝试将当前线程保存在 runner 中，然后就调用 callable 的 call 方法执行任务。

如果执行成功，通过 set 方法保存结果；如果出现异常，通过 setException 方法把异常保存下来。

```java
    // 运行
    public void run() {
        // 状态不是 NEW，说明任务已经执行过了，或者已经取消，直接返回。
        // 状态是 NEW，尝试把当前线程保存在 runner 中，表示当前线程即将执行任务。赋值失败直接返回
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;
        try {
            // 获取当前任务
            Callable<V> c = callable;
            // 任务不为空且状态为 NEW，开始运行
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    // 调用 Callable 的 call 方法运行（类似 Runnable 的 run）
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    // 设置异常
                    setException(ex);
                }
                // 如果成功运行，把结果保存下来
                if (ran)
                    set(result);
            }
        } finally {
            // 设置 runner 为 null
            runner = null;
            // 检查状态，如果状态是 INTERRUPTING 或者 INTERRUPTED
            // 处理中断逻辑
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }
```

**set**

先把状态改成 COMPLETING，然后保存结果，保存完了再把状态改成 NORMAL，表示顺利完成。最后调用 finishCompletion 唤醒在 waiters 中等待结果的线程。

```java
    /**
     * 保存结果。
     *
     * @param v the value
     */
    protected void set(V v) {
        // 把状态先改成 COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            // 保存结果
            outcome = v;
            // 再把状态改成 NORMAL
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }
```

**finishCompletion**

遍历队列，依次唤醒所有等待结果的线程。

```java
    /**
     * 唤醒所有的等待线程
     */
    private void finishCompletion() {
        for (WaitNode q; (q = waiters) != null;) {
            // q 保存 waiters，把 waiters 设置为 null
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                // 自旋遍历 waiters
                for (;;) {
                    Thread t = q.thread;
                    // 唤醒线程
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    // 继续往后
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
    }
```

**setException**

和保存结果的流程一样，只是状态最后变成了 EXCEPTIONAL。

```java
    /**
     * 处理异常。
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        // 把状态从 NEW 设置成 COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            // 设置状态为 EXCEPTIONAL
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }
```

**runAndReset**

执行完了就完了，不会保存结果，也不会把 NEW 状态改成其他值。

```java
    /**
     * 不会设置结果，不会获取 call 的结果。
     * 任务执行完之后把状态还是 NEW，任务可以多次运行。
     * runAndReset 的典型应用是在 ScheduledThreadPoolExecutor 中，周期性的执行任务。
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        // 判断是否是初始阶段
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            // 调用 call
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            runner = null;
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        // 是否运行成功而且状态是否还是 NEW（没有 cancel 和 exception）
        return ran && s == NEW;
    }
```

#### 获取结果

**get**

由外部线程调用，获取此 Future 执行的结果。如果还没有完成的话，调用 awaitDone 阻塞当前线程，否则调用 report 获取结果并返回。

```java
    /**
     * 获取结果。
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        // 如果未完成，调用 awaitDone 等待任务完成
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }
```

**awaitDone**

每一次自旋都包含以下步骤：

首先检查当前线程是否被中断。如果当前线程被中断了，移除当前线程所在的节点，然后再抛出异常。

然后根据状态判断此线程的下一步行动：

如果为结束状态（正常结束/异常/取消），置空等待节点的线程，并返回 Future 状态； 

如果为正在完成，说明此时 Future 还需要等待，且马上就结束了，调用 yeild 为正在执行的任务让出时间片； 

如果 state 为 NEW，先新建一个 WaitNode，然后 CAS 修改当前 waiters，把节点加入到 waiters 中，之后就不会进入到这个 if 块了； 

如果设置了超时等待且超时了，删除节点后返回，没超时的话阻塞线程，直到超时或者获取到结果；

如果没有设置超时等待，直接阻塞当前线程。

```java
    /**
     * 等待任务完成。如果超时或中断，则取消
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        // 截止时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            // 如果中断了，清除中断状态
            if (Thread.interrupted()) {
                // 移除等待的节点
                removeWaiter(q);
                // 抛出中断异常
                throw new InterruptedException();
            }

            int s = state;
            // 如果为结束状态（正常结束/异常/取消）
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            // 如果正在完成，为任务让出时间片
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            // 如果节点等于  null，创建一个新节点
            else if (q == null)
                q = new WaitNode();
            // 如果还没有进入队列， CAS 把节点插入到队列头部
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            // 有超时限制
            else if (timed) {
                // 计算等待时间
                nanos = deadline - System.nanoTime();
                // 已经超时了，删除节点，返回当前状态
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                // 还没超时，阻塞线程
                LockSupport.parkNanos(this, nanos);
            }
            // 其他情况，阻塞线程
            else
                LockSupport.park(this);
        }
    }
```

**removeWaiter**

把 node 节点的 thread 置为 null，然后从头开始遍历，删除链表中的无效节点。

```java
    /**
     * 删除节点。
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                // 从头遍历，pred 表示前一个节点，q 表示当前节点，s 表示下一个节点
                // 删除遍历过程中遇到的无效节点
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        // q 是有效节点，往后移动一位，继续内层循环
                        pred = q;
                    else if (pred != null) {
                        // q 是无效节点，但 q 的前一个不为 null，删除 q
                        pred.next = s;
                        if (pred.thread == null)
                            // 如果 pred 的线程为 null，说明 pred 也是无效节点，pred 被错过了
                            // 又从头开始遍历
                            continue retry;
                    }
                    // pred == null 且 q.thread == null，说明 q 是头结点且头结点无效，把头结点删了
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                // 遍历完了，跳出自旋
                break;
            }
        }
    }
```

**report**

正常执行的话返回结果，发生异常的话直接抛出异常。

```java
    /**
     * 返回结果或抛出异常。
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        // 获取结果
        Object x = outcome;
        // 如果是正常完成，直接返回结果
        if (s == NORMAL)
            return (V)x;
        // 如果取消或异常，抛出异常
        if (s >= CANCELLED)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }
```

#### 取消任务

通过中断线程的方式取消任务。如果不允许中断，则不能取消任务。

```java
    // 通过中断的方式取消异步任务
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 尝试把状态变成 INTERRUPTING 或 CANCELLED
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                        mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {
            // 如果可以在运行时中断（取消只能通过中断完成）
            if (mayInterruptIfRunning) {
                try {
                    // 中断线程
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    // 把状态改为中断
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            // 移除并唤醒所有等待线程
            finishCompletion();
        }
        return true;
    }
```

### 参考

* [JUC线程池: FutureTask详解](https://www.pdai.tech/md/java/thread/java-thread-x-juc-executor-FutureTask.html)
