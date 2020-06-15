## CompletableFuture

CompletableFuture 中最常用的功能是回调，后文列举了和回调有关的一系列方法。

完整源码请参考 [CompletableFuture.java](https://github.com/Augustvic/JavaSourceCodeAnalysis/blob/master/src/JUC/CompletableFuture.java)

### 一次执行过程

以下面的程序为例：

```java
public class Test {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CompletableFuture cf = CompletableFuture.supplyAsync(() -> {
            try {
                long last = System.currentTimeMillis();
                while (true) {
                    long curr = System.currentTimeMillis();
                    if (curr - last < 10000) {
                        Thread.sleep(1000);
                    } else {
                        System.out.println(Thread.currentThread().getName() + ": wake up");
                        last = curr;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + "[supplyAsync]");
            return "hello ";
        },executorService).thenAccept(s -> {
            try {
                System.out.println(Thread.currentThread().getName() + "[thenAccept]: " + s + "world");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        while (true) {
            if (cf.isDone()) {
                System.out.println(Thread.currentThread().getName() + ": " + "CompletedFuture...isDown");
                break;
            }
        }
    }
}
```

程序分为两步，第一步是调用 CompletableFuture 的静态方法 supplyAsync 执行一次异步任务，此任务会返回一个 CompletableFuture 类型的对象。在这个对象里包含一个任务和该任务对应的结果，由于是异步执行，在调用该方法的线程得到结果时，任务可能还没有执行完。

第二步是调用获取到的 CompletableFuture 对象的 thenAccept 方法。此方法的作用是创造一个新的 CompletableFuture，接受 CompletableFuture 对象的结果，并作为参数传递到 Consumer 中并消费。消费完成后返回 CompletableFuture 对象（thenAccept 方法不保存 Consumer 的结果，所以 CompletableFuture 里面结果为空）。

把第二步创建的 CompletableFuture 记为 f2，第一步的记为 f1，f2 的执行依赖 f1 的结果。在执行 thenAccept 的时候，如果 f1 还没有执行完毕，将会把 f2 封装成一个特定的节点，添加到 f1 的依赖栈中，表示 f2 依赖 f1，即 f1 执行完之后才能执行 f2。

上面的例子中只有两步操作，所以 CompletableFuture 的依赖链很短。理论上 CompletableFuture 的依赖链的长度没有限制，因为它是纯粹的单向链表结构。

下面对该例子进行调试：

首先从静态方法 supplyAsync 进入 CompletableFuture：

```java
    /**
     * 在指定线程池中异步执行一个有返回值的任务，返回结果封装在 CompletableFuture 中。
     * 调用 supplier 执行此任务。
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                       Executor executor) {
        return asyncSupplyStage(screenExecutor(executor), supplier);
    }
    
    // 执行有返回值的任务，把任务封装成 AsyncSupply，放入线程池执行
    static <U> CompletableFuture<U> asyncSupplyStage(Executor e,
                                                     Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }
```

这里首先创造了一个 CompletableFuture 对象，然后创造了一个 AsyncSupply 类型的任务，任务中封装了刚刚创造的 CompletableFuture 对象（用于保存结果）和即将要执行的操作。接着把 AsyncSupply 任务放入线程池执行。

如果这个例子后面没有调用 thenAccept 方法的话，不难看出其实到这里就是一个简单的 Future 返回异步结果的过程。从类的签名中可以看到，CompletableFuture 实现了 Future 接口，所以确实，CompletableFuture 也可以当成 Future 类型。

来看 AsyncSupply 类：

```java
    // 有返回值的任务，继承自 ForkJoinTask，说明可以在 ForkJoinPool 中执行
    @SuppressWarnings("serial")
    static final class AsyncSupply<T> extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        // 当前任务的执行结果
        CompletableFuture<T> dep;
        // 任务执行体
        Supplier<T> fn;
        AsyncSupply(CompletableFuture<T> dep, Supplier<T> fn) {
            this.dep = dep; this.fn = fn;
        }

        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) {}
        public final boolean exec() { run(); return true; }

        public void run() {
            CompletableFuture<T> d; Supplier<T> f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null; fn = null;
                // 如果任务还未执行
                // d 是任务的结果，f 才是任务
                if (d.result == null) {
                    try {
                        // 执行任务并设置任务的执行结果
                        d.completeValue(f.get());
                    } catch (Throwable ex) {
                        // 捕获异常并设置异常结果
                        d.completeThrowable(ex);
                    }
                }
                // 传播任务完成的消息，执行所有依赖此任务的其他任务（栈中的其他任务）
                d.postComplete();
            }
        }
    }
```

CompletableFuture 默认 ForkJoinPool 线程池，所以 AsyncSupply 继承了 ForkJoinTask 类。类中有两个属性，分别为保存了当前任务执行结果的 CompletableFuture 对象和任务执行体 Supplier 对象。

在 run 方法中执行任务并设置执行结果，完成之后，需要传播任务完成的消息给所有依赖此任务的其他任务（即栈中的其它任务），这一操作在 postComplete 中完成。

在我们的例子里面，AsyncSupply 任务将会无限循环，即线程池会一直在 f.get() 里面执行。

我们回到 main 线程中，在返回的 CompletableFuture 中调用 thenAccept 方法将会进入以下流程：

```java
    // thenAccept，把上个计算结果转换为当前任务的输入参数，
    // 和 thenApply 的区别是不会返回任何处理结果
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniAcceptStage(null, action);
    }
    
    // thenAccept 中处理任务
    private CompletableFuture<Void> uniAcceptStage(Executor e,
                                                   Consumer<? super T> f) {
        if (f == null) throw new NullPointerException();
        // f 是用户新传入的任务，需要创建新的 CompletableFuture
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        // 线程池为空时，当前线程直接调用 uniAccept
        // uniAccept 中传入的 this 表示 d 依赖当前的 this
        // 线程池不为空时，包装成 UniAccept 任务放入栈中，表示 c 依赖 this
        // 调用 tryFire 尝试运行任务 c
        if (e != null || !d.uniAccept(this, f, null)) {
            UniAccept<T> c = new UniAccept<T>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }
```

传入的线程池为空，将会直接调用 uniAccept 方法，注意这里的 d 是新创建的 CompletableFuture 对象，之后返回的也是这个对象。如果传入的线程池不为空或者调用  uniAccept 失败了（失败的原因下面会说到），将会异步执行这个 Comsumer 任务。

如果要异步执行的话，把 Comsumer 包装成 UniAccept 任务放入 this 的栈中（放入 this 的栈中表示 UniAccept 任务依赖 this），然后尝试异步执行该任务。

uniAccept 的参数中传入了 this，this 是第一个步骤返回的 CompletableFuture，所以不需要担心这两个步骤的依赖关系无法建立，到这里为止两个 CompletableFuture 仍然是能够关联起来的。

进入 uniAccept 方法中：

```java
    // Accept 处理
    final <S> boolean uniAccept(CompletableFuture<S> a,
                                Consumer<? super S> f, UniAccept<S> c) {
        Object r; Throwable x;
        // 如果依赖的任务还没有完成，返回 false
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete: if (result == null) {
            // 当前任务还没完成
            if (r instanceof AltResult) {
                // 依赖的任务发生异常，设置异常结果
                if ((x = ((AltResult)r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                // 如果指定了线程池，将会进入 claim 执行 Completion类型任务
                // c 的作用是判断是否指定了线程池，如果没有指定线程池或者已经是在指定线程池的线程中运行了，c 为 null
                // claim 的作用是把任务从当前线程（ForkJoinPool）派发到指定线程池                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                // 执行当前任务，不设置结果
                f.accept(s);
                // 结果为空
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }
```

uniAccept 方法中首先检查此任务依赖的 CompletableFuture 有没有结果，如果没有，直接返回 false 表示此任务执行失败。失败的原因显而易见：其依赖的任务都还没执行完，那么此任务是不可能执行的。

如果其依赖的任务已经执行完了，能获取到结果（CompletableFuture）了，那么执行此任务。

回到上面的 uniAcceptStage 中，由于第一个步骤的任务是无限循环任务，所以 uniAccept 的尝试是肯定会失败的，uniAccept 将返回 false。

那么接下来就会创建 UniAccept 任务，把它 push 到第一个 CompletableFuture 的依赖栈中，最后调用 tryFire 尝试执行。

```java
    // Accept 类型的任务的封装
    @SuppressWarnings("serial")
    static final class UniAccept<T> extends UniCompletion<T,Void> {
        // fn 是当前 UniAccept 需要执行的任务
        // dep 是当前 UniAccept 任务执行过后存放结果的地方
        // src 是当前 UniAccept 依赖的东西（CompletableFuture）
        Consumer<? super T> fn;
        UniAccept(Executor executor, CompletableFuture<Void> dep,
                  CompletableFuture<T> src, Consumer<? super T> fn) {
            super(executor, dep, src); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d; CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniAccept(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }
```

这里再次尝试调用 uniAccept（和上面调用 uniAccept 完全一样），如果失败了，就不继续后面的流程了，直接返回空。（由于任务已经被添加到依赖栈里了，它会在之后被唤醒的。）

回到最初的 AsyncSupply 任务中，当它执行完毕后，将会继续执行依赖它的所有任务。和唤醒依赖任务有关的两个方法是 postComplete 和 postFire：

```java
    /**
     * 传进来的 a 是当前任务对应的 CompletableFuture 依赖的东西
     * 执行 cleanStack 或者 postComplete
     */
    final CompletableFuture<T> postFire(CompletableFuture<?> a, int mode) {
        // 被依赖的任务存在，且 stack 不为空，先处理它
        if (a != null && a.stack != null) {
            // 如果是嵌套模式(mode = -1), 或者任务的结果为空，直接清空栈
            if (mode < 0 || a.result == null)
                a.cleanStack();
            else
                // 调用 postComplete 方法
                a.postComplete();
        }
        // 再处理当前任务
        if (result != null && stack != null) {
            // 嵌套模式，直接返回自身
            if (mode < 0)
                return this;
            else
                // 调用 postComplete 方法
                postComplete();
        }
        return null;
    }
    
    /**
     * 任务出栈，触发所有可到达的任务（即所有依赖此任务结果的任务）。
     */
    final void postComplete() {
        // 当前 CompletableFuture
        CompletableFuture<?> f = this; Completion h;
        // 如果 f 的栈为 null，说明没有依赖 f 的任务
        // 使 f 重新指向 this，继续后面的节点
        while ((h = f.stack) != null ||
                (f != this && (h = (f = this).stack) != null)) {
            // 如果 f 的 stack 不为 null，说明有依赖 f 的任务，进入循环
            CompletableFuture<?> d; Completion t;
            // 从头遍历 stack，并更新头元素
            // 处理被删除的头结点 h
            if (f.casStack(h, t = h.next)) {
                if (t != null) {
                    if (f != this) {
                        // 如果 f 不指向当前 CompletableFuture 了，把它的头结点压入到
                        // 当前 CompletableFuture 中，使树形结构变成链表结构，避免过深的递归
                        pushStack(h);
                        // 继续下一个节点
                        continue;
                    }
                    // 解除 h 与栈的联系，因为 h 已经执行完了
                    h.next = null;
                }
                // 如果 t 等于 null，说明当前 f 的 stack 已经处理完了
                // 调用头结点的 tryFire 方法，该方法可看作 Completion 的钩子方法，执行完逻辑后，会向后传播的
                f = (d = h.tryFire(NESTED)) == null ? this : d;
            }
        }
    }
```

postFire 的作用是调用 cleanStack 和 postComplete，执行传播任务的核心流程在 postComplete 中。 [【JUC源码解析】CompletableFuture](https://www.cnblogs.com/aniao/p/aniao_cf.html) 一文中有执行过程的图解。

从当前的 CompletableFuture 开始，依次执行依赖栈中所有的 Completion，执行完一个之后，把它从链表（栈）中删除，直到执行完所有的 Completion 任务。

> 如果没有指定线程池，这一过程中所有的任务都将在一个线程中执行，这样做效率较低。

> 注意 claim 的注释部分。在第一次阅读 CompletableFuture 源码的时候，笔者并没有注意到这个方法，认为它没什么用，而且有的参考资料上也特别注释不会进入这一个 if 分支。但是在后面再次测试的时候，笔者发现如果指定了线程池，任务将会在指定线程池中执行，而在方法中的其它代码并没有这一逻辑。在经过调试之后发现，将任务派发到指定线程池正是 claim 方法中完成的。这也给笔者提了个醒，在学习过程中，一定不能轻易地相信其他资料和忽略任何细节，要不断检验才能最终得到正确的结果。

### 获取结果

> 以 get() 和 get(long timeout, TimeUnit unit) 为例

**get()**

get 方法获取结果，主要的逻辑在 waitingGet 中

```java
    /**
     * 获取结果，如果任务未完成，则等待。
     * 如果发生了异常，则抛出异常。
     */
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        return reportGet((r = result) == null ? waitingGet(true) : r);
    }
```

waitingGet 方法中，进行自旋。每一次自旋分为以下几种情况：

* 自旋次数耗尽时，重置 spins，如果是多核处理器则重置为 256，单核重置为 0；
* 自旋次数为正数，随机减 1，并继续自旋，不做其它操作；
* 如果是第一次循环，创建一个 Signaller；
* 把这一个获取结果的任务加入到依赖栈中；
* 如果线程中断了，返回 null；
* 当 spins 的值刚好为 0，而且还没有结果时，进入 ForkJoinPool 的 managedBlock。

```java
    /**
     * 在等待之后返回结果，如果中断了返回 null
     * 不会阻塞
     */
    private Object waitingGet(boolean interruptible) {
        Signaller q = null;
        boolean queued = false;
        int spins = -1;
        Object r;
        // 循环获取结果
        while ((r = result) == null) {
            if (spins < 0)
                // 旋转次数耗尽，重置旋转次数，如果有多核，则 spins 为 256，单核为 0
                spins = (Runtime.getRuntime().availableProcessors() > 1) ?
                        1 << 8 : 0;
            else if (spins > 0) {
                // 还有旋转次数，随机减少
                if (ThreadLocalRandom.nextSecondarySeed() >= 0)
                    --spins;
            }
            else if (q == null)
                // 第一次进入循环，创建一个 Signaller
                q = new Signaller(interruptible, 0L, 0L);
            else if (!queued)
                // 加入依赖栈
                queued = tryPushStack(q);
            else if (interruptible && q.interruptControl < 0) {
                // 中断
                q.thread = null;
                cleanStack();
                return null;
            }
            else if (q.thread != null && result == null) {
                // spins 耗尽的时候（spins == 0），还没有结果，进入 managedBlock
                // 由于创造 q 的时候，deadline 为 0L，将会休眠
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    // 发生异常，下一次循环时中断线程
                    q.interruptControl = -1;
                }
            }
        }
        // 清理 q
        if (q != null) {
            q.thread = null;
            if (q.interruptControl < 0) {
                if (interruptible)
                    r = null;
                else
                    Thread.currentThread().interrupt();
            }
        }
        // 消息往后传递
        postComplete();
        return r;
    }
```

Signaller 实现了 ForkJoinPool.ManagedBlocker 接口，其中包括以下属性：

```java
        // 等待时间
        long nanos;                    // wait time if timed
        // 等待截止时间
        final long deadline;           // non-zero if timed
        // 中断标记，大于 0 可中断，小于 0 已经中断
        volatile int interruptControl; // > 0: interruptible, < 0: interrupted
        // 创建 Signaller 的线程
        volatile Thread thread;
```

此类中的核心方法为 block 和 isReleasable，block 方法用于将线程阻塞，isReleasable 返回 true 表示不需要阻塞。

```java
        public boolean isReleasable() {
            if (thread == null)
                return true;
            // 如果已经中断，设置中断标记，就不阻塞了
            if (Thread.interrupted()) {
                int i = interruptControl;
                interruptControl = -1;
                if (i > 0)
                    return true;
            }
            // 如果已经超时了，也不阻塞
            if (deadline != 0L &&
                    (nanos <= 0L || (nanos = deadline - System.nanoTime()) <= 0L)) {
                thread = null;
                return true;
            }
            // 需要阻塞
            return false;
        }
        
        public boolean block() {
            if (isReleasable())
                return true;
            else if (deadline == 0L)
                // deadline 为 0 表示只有中断才释放
                // 休眠
                LockSupport.park(this);
            else if (nanos > 0L)
                // 有时间限制地休眠
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }
```

可以看出，多核处理器在自旋 256 次之后，如果没有获取到结果，线程将会进入休眠，单核处理器自旋第 3 次就会进入休眠。

**get(long timeout, TimeUnit unit)**

有超时限制的 get 调用的是 timedGet 方法，里面没有用到自旋，直接就是休眠指定时间。

```java
    /**
     * 如果超时了，抛出 TimeoutException 异常
     * 在等待之后返回结果，如果中断返回 null
     */
    private Object timedGet(long nanos) throws TimeoutException {
        if (Thread.interrupted())
            return null;
        // 直接抛出异常
        if (nanos <= 0L)
            throw new TimeoutException();
        // 截止时间
        long d = System.nanoTime() + nanos;
        Signaller q = new Signaller(true, nanos, d == 0L ? 1L : d); // avoid 0
        boolean queued = false;
        Object r;
        // 这里没有 spins，用了 timeout 代替 spins
        while ((r = result) == null) {
            if (!queued)
                // 加入依赖栈
                queued = tryPushStack(q);
            else if (q.interruptControl < 0 || q.nanos <= 0L) {
                // 超时了
                // 先清理，再抛出异常
                q.thread = null;
                cleanStack();
                if (q.interruptControl < 0)
                    return null;
                throw new TimeoutException();
            }
            else if (q.thread != null && result == null) {
                // 还没超时，也还没有结果
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        if (q.interruptControl < 0)
            r = null;
        q.thread = null;
        postComplete();
        return r;
    }
```

### 多任务协同

allOf 表示等待所有任务完成之后再继续往后，详细解释清参考 [【JUC源码解析】CompletableFuture](https://www.cnblogs.com/aniao/p/aniao_cf.html)。

```java
    /**
     * 等待所有任务执行完毕。
     *在继续往下执行之前，等待所有任务完成，用法：
     * CompletableFuture.allOf(c1, c2, c3).join();
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }
    
    // 构造一个任务树，当节点的所有子节点任务完成，表示当前节点任务完成
    static CompletableFuture<Void> andTree(CompletableFuture<?>[] cfs,
                                           int lo, int hi) {
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (lo > hi) // empty
            d.result = NIL;
        else {
            CompletableFuture<?> a, b;
            // 二分
            int mid = (lo + hi) >>> 1;
            // 如果 lo == mid，说明左边只有一个节点，直接取 lo
            // 如果 lo 不等于 mid，继续递归
            //
            if ((a = (lo == mid ? cfs[lo] :
                    andTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid+1) ? cfs[hi] :
                            andTree(cfs, mid+1, hi)))  == null)
                throw new NullPointerException();
            if (!d.biRelay(a, b)) {
                BiRelay<?,?> c = new BiRelay<>(d, a, b);
                a.bipush(b, c);
                c.tryFire(SYNC);
            }
        }
        return d;
    }
```

orOf 表示任何一个任务完成之后就继续往后，详细解释清参考 [【JUC源码解析】CompletableFuture](https://www.cnblogs.com/aniao/p/aniao_cf.html)。

```java
    /**
     * 等待某个任务执行完毕。
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        return orTree(cfs, 0, cfs.length - 1);
    }
    
    // 递归构建二叉树
    static CompletableFuture<Object> orTree(CompletableFuture<?>[] cfs,
                                            int lo, int hi) {
        CompletableFuture<Object> d = new CompletableFuture<Object>();
        if (lo <= hi) {
            CompletableFuture<?> a, b;
            // 二分
            int mid = (lo + hi) >>> 1;
            // 和 andTree 一样
            if ((a = (lo == mid ? cfs[lo] :
                    orTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid+1) ? cfs[hi] :
                            orTree(cfs, mid+1, hi)))  == null)
                throw new NullPointerException();
            if (!d.orRelay(a, b)) {
                OrRelay<?,?> c = new OrRelay<>(d, a, b);
                a.orpush(b, c);
                c.tryFire(SYNC);
            }
        }
        return d;
    }
```

### 常用接口

**工厂方法**

用于提交一个任务并创建一个 CompletableFuture 实例：

| 方法名 | 作用 |
| - | - |
| supplyAsync(Supplier<U> supplier) | 异步执行有返回值的任务，使用内置 ForkJoinPool |
| supplyAsync(Supplier<U> supplier, Executor executor) | 异步执行有返回值的任务，使用指定线程池 |
| runAsync(Runnable runnable) | 异步执行没有返回值的任务，使用内置 ForkJoinPool |
| runAsync(Runnable runnable, Executor executor) | 异步执行没有返回值的任务，使用指定线程池 |
| completedFuture(U value) | 返回一个值为 value 的 CompletableFuture 对象 |

**回调**

回调方法是都是某个 CompletableFuture 对象里的方法，都会创造一个新的 CompletableFuture 对象并返回。下面表格中的“方法”不是具体的方法名，而是某一类方法。

某一类方法通常包括三个具体的方法：同步执行、ForkJoinPool 异步、指定线程池异步。

| 方法 | 作用 |
| - | - |
| thenApply | 将上个计算结果作为当前任务的输入参数，并保存当前任务的结果 |
| thenAccept | 将上个计算结果作为当前任务的输入参数，但不会保存当前任务的结果 |
| thenRun | 上个计算任务完成后执行当前任务，不会使用上个任务的结果，也不会保存当前任务的结果 |
| thenCombine | 将上个计算结果和指定计算结果作为当前任务的输入参数，并保存当前任务的结果 |
| thenAcceptBoth | 将上个计算结果和指定计算结果作为当前任务的输入参数，但不会保存当前任务的结果 |
| runAfterBoth | 上个计算任务和指定计算任务完成后执行当前任务，不会使用上个任务的结果，也不会保存当前任务的结果 |
| applyToEither | 将上个计算结果和指定计算结果的任意一个作为当前任务的输入参数，并保存当前任务的结果 |
| acceptEither | 将上个计算结果和指定计算结果的任意一个作为当前任务的输入参数，但不会保存当前任务的结果 |
| runAfterEither | 上个计算任务或者指定计算任务完成后执行当前任务，不会使用上个任务的结果，也不会保存当前任务的结果 |
| thenCompose | 和 thenApply 的区别是参数里有 CompletableFuture |
| whenComplete | 和 thenApply 的区别是参数不一样 |
| handle | 和 thenApply 的区别是参数不一样 |

### 使用案例

> 案例源自 [实践：使用了CompletableFuture之后，程序性能提升了三倍](https://zhuanlan.zhihu.com/p/109225713)，并进行了简化

考虑如下情景，用户的信息分别存储在不同的数据库/服务器中，对于如下的用户类，需要分别到三个服务器中查找 name、college、address 的信息：

```java
    // 实体
    static class User {
        private Integer id;
        private String name;
        private String college;
        private String address;
        public Integer getId() {
            return id;
        }
        public void setId(Integer id) {
            this.id = id;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getCollege() {
            return college;
        }
        public void setCollege(String college) {
            this.college = college;
        }
        public String getAddress() {
            return address;
        }
        public void setAddress(String address) {
            this.address = address;
        }
    }
```

用 QueryUtils 来模拟数据库的操作，假设单个属性的查找需要 1 秒钟，串行查找所有的属性需要 3 秒钟：

```java
    // 模拟数据库操作
    static class QueryUtils {
        public String queryName(Integer id) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "query name success.";
        }
        public String queryCollege(Integer id) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "query college success.";
        }
        public String queryAddress(Integer id) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "query address success.";
        }
        public String queryAll(Integer id) {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "query success.";
        }
    }
    
    static QueryUtils queryUtils = new QueryUtils();
```

接下来开始使用 CompetableFuture 定义数据库查询的流程。

首先定义一个 Supplier 类型的任务，在其中调用数据库接口：

```java
    class QuerySuppiler implements Supplier<String> {
        private Integer id;
        private String type;
        private QueryUtils queryUtils;
        public QuerySuppiler(Integer id, String type, QueryUtils queryUtils) {
            this.id = id;
            this.type = type;
            this.queryUtils=queryUtils;
        }
        @Override
        public String get() {
            if("name".equals(type)){
                return queryUtils.queryName(id);
            }else if ("college".equals(type)){
                return queryUtils.queryCollege(id);
            }else if ("address".equals(type)){
                return queryUtils.queryAddress(id);
            }
            return null;
        }
    }
```

定义 coverUser 方法，处理单个用户，具体操作为获取 name、college、address 属性的值，并设置到 user 对象中。

supplyAsync 方法是并行执行，在 getUser 所在语句的时候，才需要阻塞。

```java
    public User coverUser(User user) {
        QuerySuppiler querySuppiler1 = new QuerySuppiler(user.getId(), "name", queryUtils);
        CompletableFuture<String> getName = CompletableFuture.supplyAsync(querySuppiler1);
        getName.thenAccept((String name) -> { user.setName(name); });
        QuerySuppiler querySuppiler2 = new QuerySuppiler(user.getId(), "college", queryUtils);
        CompletableFuture<String> getCollege = CompletableFuture.supplyAsync(querySuppiler2);
        getCollege.thenAccept((String college) -> { user.setCollege(college); });
        QuerySuppiler querySuppiler3 = new QuerySuppiler(user.getId(), "address", queryUtils);
        CompletableFuture<String> getAddress = CompletableFuture.supplyAsync(querySuppiler3);
        getAddress.thenAccept((String address) -> { user.setAddress(address); });
        CompletableFuture<Void> getUser = CompletableFuture.allOf(getName, getCollege, getAddress);
        getUser.join();
        return user;
    }
```

在 main 函数中进行测试，分别对“单线程完全串行”和“使用CompletableFuture”两种方式进行测试。结果显示，串行方式耗时大约 33 秒，使用 CompletableFuture 耗时大于 11 秒。

```java
    public static void main(String[] args) {
        Test test = new Test();
        List<User> userList= new ArrayList<>();
        long begin= System.currentTimeMillis();
        for(int i = 0; i <= 10; i++){
            User user = new User();
            user.setId(i);
            queryUtils.queryAll(i);
            userList.add(user);
        }
        long end=System.currentTimeMillis();
        System.out.println("串行：" + (end - begin));

        userList.clear();
        begin= System.currentTimeMillis();
        for(int i = 0; i <= 10; i++){
            User user = new User();
            user.setId(i);
            user = test.coverUser(user);
            userList.add(user);
        }
        end=System.currentTimeMillis();
        System.out.println("并行：" + (end - begin));
    }
```

上面的案例展现了 CompletableFuture 并行执行任务的能力，当然并行执行任务还有很多其它的工具，如 Executor、CountDownLatch、Lock 等。

在实际使用的时候，CompletableFuture 更多地用于回调，如 Dubbo 的异步调用和过滤器链回调就使用了 CompletableFuture。

### 参考

* [实践：使用了CompletableFuture之后，程序性能提升了三倍](https://zhuanlan.zhihu.com/p/109225713)
* [java并发编程(7)：CompletableFuture异步框架源码详解及实例](https://www.jianshu.com/p/c4c30d0ad7bb)