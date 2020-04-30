package JUC;

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

import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.LockSupport;

public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    // 结果
    volatile Object result;
    // 依赖栈
    volatile Completion stack;

    final boolean internalComplete(Object r) { // CAS from null to r
        return UNSAFE.compareAndSwapObject(this, RESULT, null, r);
    }

    final boolean casStack(Completion cmp, Completion val) {
        return UNSAFE.compareAndSwapObject(this, STACK, cmp, val);
    }

    // 如果成功地把 c 压入栈中，返回 true
    final boolean tryPushStack(Completion c) {
        Completion h = stack;
        lazySetNext(c, h);
        return UNSAFE.compareAndSwapObject(this, STACK, h, c);
    }

    // 把 c 压入栈中，直到成功为止
    final void pushStack(Completion c) {
        do {} while (!tryPushStack(c));
    }

    /* ------------- Encoding and decoding outcomes -------------- */

    static final class AltResult { // See above
        final Throwable ex;        // null only for NIL
        AltResult(Throwable x) { this.ex = x; }
    }

    // null 结果
    static final AltResult NIL = new AltResult(null);

    // 设置结果诶 null，通常用于无返回值的任务
    final boolean completeNull() {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                NIL);
    }

    // 如果为 null，则设置为 NIL
    final Object encodeValue(T t) {
        return (t == null) ? NIL : t;
    }

    // 如果是正常结束，设置任务的执行结果
    final boolean completeValue(T t) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                (t == null) ? NIL : t);
    }

    /**
     * Returns the encoding of the given (non-null) exception as a
     * wrapped CompletionException unless it is one already.
     */
    static AltResult encodeThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x :
                new CompletionException(x));
    }

    /** Completes with an exceptional result, unless already completed. */
    final boolean completeThrowable(Throwable x) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeThrowable(x));
    }

    /**
     * Returns the encoding of the given (non-null) exception as a
     * wrapped CompletionException unless it is one already.  May
     * return the given Object r (which must have been the result of a
     * source future) if it is equivalent, i.e. if this is a simple
     * relay of an existing CompletionException.
     */
    static Object encodeThrowable(Throwable x, Object r) {
        if (!(x instanceof CompletionException))
            x = new CompletionException(x);
        else if (r instanceof AltResult && x == ((AltResult)r).ex)
            return r;
        return new AltResult(x);
    }

    /**
     * Completes with the given (non-null) exceptional result as a
     * wrapped CompletionException unless it is one already, unless
     * already completed.  May complete with the given Object r
     * (which must have been the result of a source future) if it is
     * equivalent, i.e. if this is a simple propagation of an
     * existing CompletionException.
     */
    final boolean completeThrowable(Throwable x, Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeThrowable(x, r));
    }

    /**
     * Returns the encoding of the given arguments: if the exception
     * is non-null, encodes as AltResult.  Otherwise uses the given
     * value, boxed as NIL if null.
     */
    Object encodeOutcome(T t, Throwable x) {
        return (x == null) ? (t == null) ? NIL : t : encodeThrowable(x);
    }

    /**
     * Returns the encoding of a copied outcome; if exceptional,
     * rewraps as a CompletionException, else returns argument.
     */
    static Object encodeRelay(Object r) {
        Throwable x;
        return (((r instanceof AltResult) &&
                (x = ((AltResult)r).ex) != null &&
                !(x instanceof CompletionException)) ?
                new AltResult(new CompletionException(x)) : r);
    }

    /**
     * Completes with r or a copy of r, unless already completed.
     * If exceptional, r is first coerced to a CompletionException.
     */
    final boolean completeRelay(Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeRelay(r));
    }

    /**
     * 返回结果
     */
    private static <T> T reportGet(Object r)
            throws InterruptedException, ExecutionException {
        if (r == null) // by convention below, null means interrupted
            throw new InterruptedException();
        if (r instanceof AltResult) {
            // 有异常
            Throwable x, cause;
            if ((x = ((AltResult)r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException)x;
            if ((x instanceof CompletionException) &&
                    (cause = x.getCause()) != null)
                x = cause;
            throw new ExecutionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /**
     * 在 join 中调用，获取异步的结果，如果有异常，直接抛出异常。
     */
    private static <T> T reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult)r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException)x;
            if (x instanceof CompletionException)
                throw (CompletionException)x;
            throw new CompletionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /* ------------- Async task preliminaries -------------- */

    /**
     * A marker interface identifying asynchronous tasks produced by
     * {@code async} methods. This may be useful for monitoring,
     * debugging, and tracking asynchronous activities.
     *
     * @since 1.8
     */
    public static interface AsynchronousCompletionTask {
    }

    private static final boolean useCommonPool =
            (ForkJoinPool.getCommonPoolParallelism() > 1);

    /**
     * Default executor -- ForkJoinPool.commonPool() unless it cannot
     * support parallelism.
     */
    private static final Executor asyncPool = useCommonPool ?
            ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    /** Fallback if ForkJoinPool.commonPool() cannot support parallelism */
    static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) { new Thread(r).start(); }
    }

    /**
     * Null-checks user executor argument, and translates uses of
     * commonPool to asyncPool in case parallelism disabled.
     */
    static Executor screenExecutor(Executor e) {
        if (!useCommonPool && e == ForkJoinPool.commonPool())
            return asyncPool;
        if (e == null) throw new NullPointerException();
        return e;
    }

    // Modes for Completion.tryFire. Signedness matters.
    static final int SYNC   =  0;
    static final int ASYNC  =  1;
    static final int NESTED = -1;

    /* ------------- Base Completion classes and operations -------------- */

    @SuppressWarnings("serial")
    abstract static class Completion extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        // 堆中的下一个任务
        volatile Completion next;      // Treiber stack link

        /**
         * 执行被触发的任务，返回需要传播的依赖任务
         *
         * @param mode SYNC, ASYNC, or NESTED
         */
        abstract CompletableFuture<?> tryFire(int mode);

        /** 任务是否可触发 */
        abstract boolean isLive();

        public final void run()                { tryFire(ASYNC); }
        public final boolean exec()            { tryFire(ASYNC); return true; }
        public final Void getRawResult()       { return null; }
        public final void setRawResult(Void v) {}
    }

    static void lazySetNext(Completion c, Completion next) {
        UNSAFE.putOrderedObject(c, NEXT, next);
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

    /** 遍历栈并清除死亡的 Completion（not alive） */
    final void cleanStack() {
        // q 从栈的头结点开始
        for (Completion p = null, q = stack; q != null;) {
            Completion s = q.next;
            // 分成以下 3 中情况讨论
            if (q.isLive()) {
                // 如果 q 还活着，什么都不做，p 和 q 分别往后移动一位
                p = q;
                q = s;
            }
            else if (p == null) {
                // 可能是第一次，可能是重新从 stack 开始的，总之是在栈头部
                casStack(q, s);
                q = stack;
            }
            else {
                // 移除 q
                p.next = s;
                // 如果 p（q 的前一个节点）还活着，从 s 开始
                if (p.isLive())
                    q = s;
                else {
                    // p 死了，重新从 stack 开始
                    p = null;
                    q = stack;
                }
            }
        }
    }

    /* ------------- One-input Completions -------------- */

    // 继承自 Completion 的抽象类，包含了任务的线程池信息、依赖任务和任务执行体
    @SuppressWarnings("serial")
    abstract static class UniCompletion<T,V> extends Completion {
        // 执行当前任务的线程池，没有为 null
        Executor executor;
        // 依赖的任务
        CompletableFuture<V> dep;
        // 当前任务的执行实体
        CompletableFuture<T> src;

        // 构造函数
        UniCompletion(Executor executor, CompletableFuture<V> dep,
                      CompletableFuture<T> src) {
            this.executor = executor; this.dep = dep; this.src = src;
        }

        /**
         * 如果当前任务可以执行，返回 true。使用 FJ tag 保证只有一个线程声明所有权。
         * 如果异步执行，则提交当前任务。后面会调用 tryFire 执行。
         */
        final boolean claim() {
            Executor e = executor;
            if (compareAndSetForkJoinTaskTag((short)0, (short)1)) {
                if (e == null)
                    return true;
                executor = null; // disable
                e.execute(this);
            }
            return false;
        }

        final boolean isLive() { return dep != null; }
    }

    // 把 c 压入栈中，表示 c 依赖当前的任务
    final void push(UniCompletion<?,?> c) {
        if (c != null) {
            while (result == null && !tryPushStack(c))
                lazySetNext(c, null); // clear on failure
        }
    }

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

    // thenApply 任务的封装
    @SuppressWarnings("serial")
    static final class UniApply<T,V> extends UniCompletion<T,V> {
        Function<? super T,? extends V> fn;
        UniApply(Executor executor, CompletableFuture<V> dep,
                 CompletableFuture<T> src,
                 Function<? super T,? extends V> fn) {
            super(executor, dep, src); this.fn = fn;
        }
        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d; CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniApply(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniApply(CompletableFuture<S> a,
                               Function<? super S,? extends T> f,
                               UniApply<S,T> c) {
        Object r; Throwable x;
        // 依赖的任务未完成，直接返回 false
        if (a == null || (r = a.result) == null || f == null)
            return false;
        // 检查当前任务是否完成，防止其他线程已经完成了当前任务
        tryComplete: if (result == null) {
            // 当前任务未完成
            if (r instanceof AltResult) {
                // 依赖的任务处理异常，设置异常结果
                if ((x = ((AltResult)r).ex) != null) {
                    completeThrowable(x, r);
                    // 跳出最外层 if，然后退出函数
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    // 不会进入这个 if
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                // 执行任务并设置结果
                completeValue(f.apply(s));
            } catch (Throwable ex) {
                // 执行任务异常
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> CompletableFuture<V> uniApplyStage(
            Executor e, Function<? super T,? extends V> f) {
        if (f == null) throw new NullPointerException();
        // 创造新的 CompletableFuture
        CompletableFuture<V> d =  new CompletableFuture<V>();
        // 线程池为空时，当前线程直接调用 uniApply
        // 线程池不为空时，包装成 UniApply 任务放入栈中，并调用 tryFire 进行处理
        if (e != null || !d.uniApply(this, f, null)) {
            UniApply<T,V> c = new UniApply<T,V>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

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
                    // 不会进入
                    return false;
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

    @SuppressWarnings("serial")
    static final class UniRun<T> extends UniCompletion<T,Void> {
        Runnable fn;
        UniRun(Executor executor, CompletableFuture<Void> dep,
               CompletableFuture<T> src, Runnable fn) {
            super(executor, dep, src); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d; CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniRun(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniRun(CompletableFuture<?> a, Runnable f, UniRun<?> c) {
        Object r; Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult)r).ex) != null)
                completeThrowable(x, r);
            else
                try {
                    if (c != null && !c.claim())
                        return false;
                    f.run();
                    completeNull();
                } catch (Throwable ex) {
                    completeThrowable(ex);
                }
        }
        return true;
    }

    private CompletableFuture<Void> uniRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (e != null || !d.uniRun(this, f, null)) {
            UniRun<T> c = new UniRun<T>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    //  whenComplete 任务的封装
    @SuppressWarnings("serial")
    static final class UniWhenComplete<T> extends UniCompletion<T,T> {
        BiConsumer<? super T, ? super Throwable> fn;
        UniWhenComplete(Executor executor, CompletableFuture<T> dep,
                        CompletableFuture<T> src,
                        BiConsumer<? super T, ? super Throwable> fn) {
            super(executor, dep, src); this.fn = fn;
        }
        // 钩子方法
        final CompletableFuture<T> tryFire(int mode) {
            // 保存此任务依赖的 CmpletableFuture
            CompletableFuture<T> d;
            // 当前任务执行的实体
            CompletableFuture<T> a;
            // 此 Completion 依赖的 CompletableFuture 为空，直接返回 null
            // 依赖的 CompletableFuture 不为 null，调用依赖的 CompletableFuture 的 uniWhenComplete 方法执行任务
            // 如果 uniWhenComplete 返回 true，就不会返回 null
            // 如果是异步模式，就不判断任务是否结束
            if ((d = dep) == null ||
                    !d.uniWhenComplete(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            // 钩子方法之后的处理
            return d.postFire(a, mode);
        }
    }

    // 执行 UniWhenComplete 类型的任务
    final boolean uniWhenComplete(CompletableFuture<T> a,
                                  BiConsumer<? super T,? super Throwable> f,
                                  UniWhenComplete<T> c) {
        Object r; T t; Throwable x = null;
        // 依赖的任务还没有完成，返回 false
        if (a == null || (r = a.result) == null || f == null)
            return false;
        // 依赖的任务完成了
        if (result == null) {
            // 如果当前任务还没有完成
            try {
                // 判断任务能否被执行
                // 所有的 c 都为 null，所以不用考虑这个条件
                if (c != null && !c.claim())
                    return false;
                // 执行结果是否异常
                if (r instanceof AltResult) {
                    x = ((AltResult)r).ex;
                    t = null;
                } else {
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                // 执行任务
                f.accept(t, x);
                if (x == null) {
                    internalComplete(r);
                    return true;
                }
            } catch (Throwable ex) {
                if (x == null)
                    x = ex;
            }
            // 设置异常结果
            completeThrowable(x, r);
        }
        return true;
    }

    // 在各种 “whenComplete” 中调用
    private CompletableFuture<T> uniWhenCompleteStage(
            Executor e, BiConsumer<? super T, ? super Throwable> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        // 指定线程池为空，调用 uniWhenComplete 检查依赖的任务是否完成，没有完成的话和下一种情况一样的处理
        // 指定线程池非空，构建 UniWhenComplete 任务并将该任务加入队列中，同时调用 tryFire 进行同步处理
        if (e != null || !d.uniWhenComplete(this, f, null)) {
            UniWhenComplete<T> c = new UniWhenComplete<T>(e, d, this, f);
            push(c);
            // 调用钩子方法，触发任务执行并处理相关依赖
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniHandle<T,V> extends UniCompletion<T,V> {
        BiFunction<? super T, Throwable, ? extends V> fn;
        UniHandle(Executor executor, CompletableFuture<V> dep,
                  CompletableFuture<T> src,
                  BiFunction<? super T, Throwable, ? extends V> fn) {
            super(executor, dep, src); this.fn = fn;
        }
        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d; CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniHandle(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }

    // 处理 UniHandle 类型的任务
    final <S> boolean uniHandle(CompletableFuture<S> a,
                                BiFunction<? super S, Throwable, ? extends T> f,
                                UniHandle<S,T> c) {
        Object r; S s; Throwable x;
        // 依赖的任务（也就是当前任务）未完成，返回 false
        if (a == null || (r = a.result) == null || f == null)
            return false;
        // 如果执行到这儿了，肯定会进入这个 if
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                // this 执行异常
                if (r instanceof AltResult) {
                    x = ((AltResult)r).ex;
                    s = null;
                } else {
                    x = null;
                    @SuppressWarnings("unchecked") S ss = (S) r;
                    s = ss;
                }
                // 将依赖的结果作为当前函数的输入参数，并执行函数，设置当前执行结果
                completeValue(f.apply(s, x));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    // UniHandler 类型任务的处理
    private <V> CompletableFuture<V> uniHandleStage(
            Executor e, BiFunction<? super T, Throwable, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        // 线程池为 null，直接调用 uniHandler 处理
        // 线程池不为 null，包装成 UniHandler 类型的任务，推入栈中
        if (e != null || !d.uniHandle(this, f, null)) {
            UniHandle<T,V> c = new UniHandle<T,V>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniExceptionally<T> extends UniCompletion<T,T> {
        Function<? super Throwable, ? extends T> fn;
        UniExceptionally(CompletableFuture<T> dep, CompletableFuture<T> src,
                         Function<? super Throwable, ? extends T> fn) {
            super(null, dep, src); this.fn = fn;
        }
        final CompletableFuture<T> tryFire(int mode) { // never ASYNC
            // assert mode != ASYNC;
            CompletableFuture<T> d; CompletableFuture<T> a;
            if ((d = dep) == null || !d.uniExceptionally(a = src, fn, this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniExceptionally(CompletableFuture<T> a,
                                   Function<? super Throwable, ? extends T> f,
                                   UniExceptionally<T> c) {
        Object r; Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (r instanceof AltResult && (x = ((AltResult)r).ex) != null) {
                    if (c != null && !c.claim())
                        return false;
                    completeValue(f.apply(x));
                } else
                    internalComplete(r);
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private CompletableFuture<T> uniExceptionallyStage(
            Function<Throwable, ? extends T> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<T> d = new CompletableFuture<T>();
        if (!d.uniExceptionally(this, f, null)) {
            UniExceptionally<T> c = new UniExceptionally<T>(d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniRelay<T> extends UniCompletion<T,T> { // for Compose
        UniRelay(CompletableFuture<T> dep, CompletableFuture<T> src) {
            super(null, dep, src);
        }
        final CompletableFuture<T> tryFire(int mode) {
            CompletableFuture<T> d; CompletableFuture<T> a;
            if ((d = dep) == null || !d.uniRelay(a = src))
                return null;
            src = null; dep = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniRelay(CompletableFuture<T> a) {
        Object r;
        if (a == null || (r = a.result) == null)
            return false;
        if (result == null) // no need to claim
            completeRelay(r);
        return true;
    }

    @SuppressWarnings("serial")
    static final class UniCompose<T,V> extends UniCompletion<T,V> {
        Function<? super T, ? extends CompletionStage<V>> fn;
        UniCompose(Executor executor, CompletableFuture<V> dep,
                   CompletableFuture<T> src,
                   Function<? super T, ? extends CompletionStage<V>> fn) {
            super(executor, dep, src); this.fn = fn;
        }
        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d; CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniCompose(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniCompose(
            CompletableFuture<S> a,
            Function<? super S, ? extends CompletionStage<T>> f,
            UniCompose<S,T> c) {
        Object r; Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete: if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult)r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                CompletableFuture<T> g = f.apply(s).toCompletableFuture();
                if (g.result == null || !uniRelay(g)) {
                    UniRelay<T> copy = new UniRelay<T>(this, g);
                    g.push(copy);
                    copy.tryFire(SYNC);
                    if (result == null)
                        return false;
                }
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> CompletableFuture<V> uniComposeStage(
            Executor e, Function<? super T, ? extends CompletionStage<V>> f) {
        if (f == null) throw new NullPointerException();
        Object r; Throwable x;
        // 没有线程池，且当前任务已经处理完成
        if (e == null && (r = result) != null) {
            if (r instanceof AltResult) {
                // 如果当前任务有异常，返回异常结果
                if ((x = ((AltResult)r).ex) != null) {
                    return new CompletableFuture<V>(encodeThrowable(x, r));
                }
                r = null;
            }
            try {
                @SuppressWarnings("unchecked") T t = (T) r;
                // 把当前任务的结果 t 作为 f 的输入，执行 f
                CompletableFuture<V> g = f.apply(t).toCompletableFuture();
                Object s = g.result;
                // 已经获取到 f 执行的结果了，返回
                if (s != null)
                    return new CompletableFuture<V>(encodeRelay(s));
                // 任务未完成，封装成 UniRelay，放入栈中
                CompletableFuture<V> d = new CompletableFuture<V>();
                UniRelay<V> copy = new UniRelay<V>(d, g);
                g.push(copy);
                copy.tryFire(SYNC);
                return d;
            } catch (Throwable ex) {
                return new CompletableFuture<V>(encodeThrowable(ex));
            }
        }
        // 有线程池，或者当前任务没有处理完
        // 包装成 UniCompose 任务，推入栈中
        CompletableFuture<V> d = new CompletableFuture<V>();
        UniCompose<T,V> c = new UniCompose<T,V>(e, d, this, f);
        push(c);
        c.tryFire(SYNC);
        return d;
    }

    /* ------------- Two-input Completions -------------- */

    /** 增加了一个任务 snd */
    @SuppressWarnings("serial")
    abstract static class BiCompletion<T,U,V> extends UniCompletion<T,V> {
        CompletableFuture<U> snd; // second source for action
        BiCompletion(Executor executor, CompletableFuture<V> dep,
                     CompletableFuture<T> src, CompletableFuture<U> snd) {
            super(executor, dep, src); this.snd = snd;
        }
    }

    /** A Completion delegating to a BiCompletion */
    @SuppressWarnings("serial")
    static final class CoCompletion extends Completion {
        BiCompletion<?,?,?> base;
        CoCompletion(BiCompletion<?,?,?> base) { this.base = base; }
        final CompletableFuture<?> tryFire(int mode) {
            BiCompletion<?,?,?> c; CompletableFuture<?> d;
            if ((c = base) == null || (d = c.tryFire(mode)) == null)
                return null;
            base = null; // detach
            return d;
        }
        final boolean isLive() {
            BiCompletion<?,?,?> c;
            return (c = base) != null && c.dep != null;
        }
    }

    /** Pushes completion to this and b unless both done. */
    final void bipush(CompletableFuture<?> b, BiCompletion<?,?,?> c) {
        if (c != null) {
            Object r;
            while ((r = result) == null && !tryPushStack(c))
                lazySetNext(c, null); // clear on failure
            if (b != null && b != this && b.result == null) {
                Completion q = (r != null) ? c : new CoCompletion(c);
                while (b.result == null && !b.tryPushStack(q))
                    lazySetNext(q, null); // clear on failure
            }
        }
    }

    /** Post-processing after successful BiCompletion tryFire. */
    final CompletableFuture<T> postFire(CompletableFuture<?> a,
                                        CompletableFuture<?> b, int mode) {
        if (b != null && b.stack != null) { // clean second source
            if (mode < 0 || b.result == null)
                b.cleanStack();
            else
                b.postComplete();
        }
        return postFire(a, mode);
    }

    @SuppressWarnings("serial")
    static final class BiApply<T,U,V> extends BiCompletion<T,U,V> {
        BiFunction<? super T,? super U,? extends V> fn;
        BiApply(Executor executor, CompletableFuture<V> dep,
                CompletableFuture<T> src, CompletableFuture<U> snd,
                BiFunction<? super T,? super U,? extends V> fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biApply(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final <R,S> boolean biApply(CompletableFuture<R> a,
                                CompletableFuture<S> b,
                                BiFunction<? super R,? super S,? extends T> f,
                                BiApply<R,S,T> c) {
        Object r, s; Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        tryComplete: if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult)r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult)s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                completeValue(f.apply(rr, ss));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U,V> CompletableFuture<V> biApplyStage(
            Executor e, CompletionStage<U> o,
            BiFunction<? super T,? super U,? extends V> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        if (e != null || !d.biApply(this, b, f, null)) {
            BiApply<T,U,V> c = new BiApply<T,U,V>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    // BiAccept 类型的任务
    @SuppressWarnings("serial")
    static final class BiAccept<T,U> extends BiCompletion<T,U,Void> {
        BiConsumer<? super T,? super U> fn;
        BiAccept(Executor executor, CompletableFuture<Void> dep,
                 CompletableFuture<T> src, CompletableFuture<U> snd,
                 BiConsumer<? super T,? super U> fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biAccept(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    // 处理 BiAccept 类型的任务
    final <R,S> boolean biAccept(CompletableFuture<R> a,
                                 CompletableFuture<S> b,
                                 BiConsumer<? super R,? super S> f,
                                 BiAccept<R,S> c) {
        Object r, s; Throwable x;
        // 判断其依赖的 a 和 b 是否执行完毕
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        tryComplete: if (result == null) {
            // 当前任务还没有执行
            if (r instanceof AltResult) {
                // a 有异常
                if ((x = ((AltResult)r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                // b 有异常
                if ((x = ((AltResult)s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                // 使用 a 和 b 的结果作为 f 的输入，运行 f
                f.accept(rr, ss);
                // 没有返回值，返回 null
                completeNull();
            } catch (Throwable ex) {
                // 任务执行异常，设置异常结果
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U> CompletableFuture<Void> biAcceptStage(
            Executor e, CompletionStage<U> o,
            BiConsumer<? super T,? super U> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        // 线程池为空时，当前线程直接调用 biAccept
        // 线程池不为空时，包装成 BiAccept 任务放入栈中，并调用 tryFire 进行处理
        if (e != null || !d.biAccept(this, b, f, null)) {
            BiAccept<T,U> c = new BiAccept<T,U>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class BiRun<T,U> extends BiCompletion<T,U,Void> {
        Runnable fn;
        BiRun(Executor executor, CompletableFuture<Void> dep,
              CompletableFuture<T> src,
              CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biRun(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final boolean biRun(CompletableFuture<?> a, CompletableFuture<?> b,
                        Runnable f, BiRun<?,?> c) {
        Object r, s; Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult)r).ex) != null)
                completeThrowable(x, r);
            else if (s instanceof AltResult && (x = ((AltResult)s).ex) != null)
                completeThrowable(x, s);
            else
                try {
                    if (c != null && !c.claim())
                        return false;
                    f.run();
                    completeNull();
                } catch (Throwable ex) {
                    completeThrowable(ex);
                }
        }
        return true;
    }

    private CompletableFuture<Void> biRunStage(Executor e, CompletionStage<?> o,
                                               Runnable f) {
        CompletableFuture<?> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        if (e != null || !d.biRun(this, b, f, null)) {
            BiRun<T,?> c = new BiRun<>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class BiRelay<T,U> extends BiCompletion<T,U,Void> { // for And
        BiRelay(CompletableFuture<Void> dep,
                CompletableFuture<T> src,
                CompletableFuture<U> snd) {
            super(null, dep, src, snd);
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null || !d.biRelay(a = src, b = snd))
                return null;
            src = null; snd = null; dep = null;
            return d.postFire(a, b, mode);
        }
    }

    boolean biRelay(CompletableFuture<?> a, CompletableFuture<?> b) {
        Object r, s; Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult)r).ex) != null)
                completeThrowable(x, r);
            else if (s instanceof AltResult && (x = ((AltResult)s).ex) != null)
                completeThrowable(x, s);
            else
                completeNull();
        }
        return true;
    }

    /** Recursively constructs a tree of completions. */
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

    /* ------------- Projected (Ored) BiCompletions -------------- */

    /** Pushes completion to this and b unless either done. */
    final void orpush(CompletableFuture<?> b, BiCompletion<?,?,?> c) {
        if (c != null) {
            while ((b == null || b.result == null) && result == null) {
                if (tryPushStack(c)) {
                    if (b != null && b != this && b.result == null) {
                        Completion q = new CoCompletion(c);
                        while (result == null && b.result == null &&
                                !b.tryPushStack(q))
                            lazySetNext(q, null); // clear on failure
                    }
                    break;
                }
                lazySetNext(c, null); // clear on failure
            }
        }
    }

    // OrApply 类型任务的封装
    @SuppressWarnings("serial")
    static final class OrApply<T,U extends T,V> extends BiCompletion<T,U,V> {
        Function<? super T,? extends V> fn;
        OrApply(Executor executor, CompletableFuture<V> dep,
                CompletableFuture<T> src,
                CompletableFuture<U> snd,
                Function<? super T,? extends V> fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<V> tryFire(int mode) {
            CompletableFuture<V> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orApply(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    // 执行 OrApply 类型的任务
    final <R,S extends R> boolean orApply(CompletableFuture<R> a,
                                          CompletableFuture<S> b,
                                          Function<? super R, ? extends T> f,
                                          OrApply<R,S,T> c) {
        Object r; Throwable x;
        // a 和 b 都没有执行完，都没有结果，返回 false（a 是 this，b 是 other）
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        tryComplete: if (result == null) {
            // 当前 OrApply 任务还没执行
            try {
                if (c != null && !c.claim())
                    return false;
                // r 是已经完成的那一个任务的结果，如果都完成了，那就是 a 的结果
                if (r instanceof AltResult) {
                    // 如果有异常，设置异常
                    if ((x = ((AltResult)r).ex) != null) {
                        completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                @SuppressWarnings("unchecked") R rr = (R) r;
                // 处理任务并设置结果
                completeValue(f.apply(rr));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    // applyToEither 中调用
    private <U extends T,V> CompletableFuture<V> orApplyStage(
            Executor e, CompletionStage<U> o,
            Function<? super T, ? extends V> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<V> d = new CompletableFuture<V>();
        // 线程池为空时，当前线程直接调用 orAccept
        // 线程池不为空时，包装成 OrAccept 任务放入栈中，并调用 tryFire 进行处理
        if (e != null || !d.orApply(this, b, f, null)) {
            OrApply<T,U,V> c = new OrApply<T,U,V>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    // OrAccept 类型的任务
    @SuppressWarnings("serial")
    static final class OrAccept<T,U extends T> extends BiCompletion<T,U,Void> {
        Consumer<? super T> fn;
        OrAccept(Executor executor, CompletableFuture<Void> dep,
                 CompletableFuture<T> src,
                 CompletableFuture<U> snd,
                 Consumer<? super T> fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orAccept(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    // 执行 OrAccept 类型的任务
    final <R,S extends R> boolean orAccept(CompletableFuture<R> a,
                                           CompletableFuture<S> b,
                                           Consumer<? super R> f,
                                           OrAccept<R,S> c) {
        Object r; Throwable x;
        // 如果 a 和 b 都还没有完成，返回 false
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        tryComplete: if (result == null) {
            // 当前任务还没有完成
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    // a 或者 b 完成了，但发生异常
                    if ((x = ((AltResult)r).ex) != null) {
                        // 设置异常
                        completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                @SuppressWarnings("unchecked") R rr = (R) r;
                // f 依赖 a 或者 b 的结果
                // 执行 f
                f.accept(rr);
                // 没有结果
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    // 处理 OrAccept 类型的任务
    private <U extends T> CompletableFuture<Void> orAcceptStage(
            Executor e, CompletionStage<U> o, Consumer<? super T> f) {
        CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        // 线程池为 null，当前线程直接调用 orAccept 处理
        // 线程池不为 null，任务包装成 OrAccept 类型，推入栈中
        if (e != null || !d.orAccept(this, b, f, null)) {
            OrAccept<T,U> c = new OrAccept<T,U>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    // OrRun 类型的任务
    @SuppressWarnings("serial")
    static final class OrRun<T,U> extends BiCompletion<T,U,Void> {
        Runnable fn;
        OrRun(Executor executor, CompletableFuture<Void> dep,
              CompletableFuture<T> src,
              CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd); this.fn = fn;
        }
        final CompletableFuture<Void> tryFire(int mode) {
            CompletableFuture<Void> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orRun(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null; src = null; snd = null; fn = null;
            return d.postFire(a, b, mode);
        }
    }

    // 执行 OrRun
    final boolean orRun(CompletableFuture<?> a, CompletableFuture<?> b,
                        Runnable f, OrRun<?,?> c) {
        Object r; Throwable x;
        // a 和 b 都没完成，返回 false
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                // 有异常
                if (r instanceof AltResult && (x = ((AltResult)r).ex) != null)
                    completeThrowable(x, r);
                else {
                    // 直接执行 f，没有依赖任何结果
                    f.run();
                    // 同时也不会返回任何结果
                    completeNull();
                }
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    // 运行 OrRun 类型的任务，
    private CompletableFuture<Void> orRunStage(Executor e, CompletionStage<?> o,
                                               Runnable f) {
        CompletableFuture<?> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        // 没有设置线程池，直接调用 orRun
        // 设置了线程池，包装成 OrRun 类型的任务推入栈中
        if (e != null || !d.orRun(this, b, f, null)) {
            OrRun<T,?> c = new OrRun<>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class OrRelay<T,U> extends BiCompletion<T,U,Object> { // for Or
        OrRelay(CompletableFuture<Object> dep, CompletableFuture<T> src,
                CompletableFuture<U> snd) {
            super(null, dep, src, snd);
        }
        final CompletableFuture<Object> tryFire(int mode) {
            CompletableFuture<Object> d;
            CompletableFuture<T> a;
            CompletableFuture<U> b;
            if ((d = dep) == null || !d.orRelay(a = src, b = snd))
                return null;
            src = null; snd = null; dep = null;
            return d.postFire(a, b, mode);
        }
    }

    final boolean orRelay(CompletableFuture<?> a, CompletableFuture<?> b) {
        Object r;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null))
            return false;
        if (result == null)
            completeRelay(r);
        return true;
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

    /* ------------- Zero-input Async forms -------------- */

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

    // 执行有返回值的任务，把任务封装成 AsyncSupply，放入线程池执行
    static <U> CompletableFuture<U> asyncSupplyStage(Executor e,
                                                     Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<U> d = new CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }

    // 没有返回值的任务，也继承自 ForkJoinTask，说明可以在 ForkJoinPool 中执行
    @SuppressWarnings("serial")
    static final class AsyncRun extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        // 当前任务执行结果
        CompletableFuture<Void> dep;
        // 任务执行体
        Runnable fn;
        AsyncRun(CompletableFuture<Void> dep, Runnable fn) {
            this.dep = dep; this.fn = fn;
        }

        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) {}
        public final boolean exec() { run(); return true; }

        public void run() {
            CompletableFuture<Void> d; Runnable f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null; fn = null;
                // 任务还未执行
                if (d.result == null) {
                    try {
                        // 执行任务，没有结果
                        f.run();
                        // 设置结果为 null
                        d.completeNull();
                    } catch (Throwable ex) {
                        // 捕获异常，设置异常信息到结果中
                        d.completeThrowable(ex);
                    }
                }
                // 传播任务完成的消息，执行所有依赖此任务的其他任务
                // 依赖任务存储在栈中
                d.postComplete();
            }
        }
    }

    // 执行无返回值的任务，把任务封装成 AsyncRun，然后交给线程池执行
    static CompletableFuture<Void> asyncRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        CompletableFuture<Void> d = new CompletableFuture<Void>();
        e.execute(new AsyncRun(d, f));
        return d;
    }

    /* ------------- Signallers -------------- */

    /**
     * 实现 ManageBlocker，用于获取结果
     */
    @SuppressWarnings("serial")
    static final class Signaller extends Completion
            implements ForkJoinPool.ManagedBlocker {
        // 等待时间
        long nanos;                    // wait time if timed
        // 等待截止时间
        final long deadline;           // non-zero if timed
        // 中断标记，大于 0 可中断，小于 0 已经中断
        volatile int interruptControl; // > 0: interruptible, < 0: interrupted
        // 创建 Signaller 的线程
        volatile Thread thread;

        Signaller(boolean interruptible, long nanos, long deadline) {
            this.thread = Thread.currentThread();
            this.interruptControl = interruptible ? 1 : 0;
            this.nanos = nanos;
            this.deadline = deadline;
        }
        final CompletableFuture<?> tryFire(int ignore) {
            Thread w;
            if ((w = thread) != null) {
                thread = null;
                LockSupport.unpark(w);
            }
            return null;
        }
        // block 方法用于将线程阻塞，isReleasable 返回 true 表示不需要阻塞
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
        final boolean isLive() { return thread != null; }
    }

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

    /* ------------- public methods -------------- */

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Creates a new complete CompletableFuture with given encoded result.
     */
    private CompletableFuture(Object r) {
        this.result = r;
    }

    /**
     * 工厂方法。
     * 在线程池中异步执行一个有返回值的任务，返回结果封装在 CompletableFuture 中。
     * 调用 supplier 执行此任务。
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(asyncPool, supplier);
    }

    /**
     * 在指定线程池中异步执行一个有返回值的任务，返回结果封装在 CompletableFuture 中。
     * 调用 supplier 执行此任务。
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @param <U> the function's return type
     * @return the new CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                       Executor executor) {
        return asyncSupplyStage(screenExecutor(executor), supplier);
    }

    /**
     * 在线程池中异步执行一个没有返回值的任务，返回结果封装在 CompletableFuture 中。
     * 调用 runnable 执行此任务。
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return asyncRunStage(asyncPool, runnable);
    }

    /**
     * 在指定线程池中异步执行一个没有返回值的任务，返回结果封装在 CompletableFuture 中。
     * 调用 runnable 执行此任务。
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable,
                                                   Executor executor) {
        return asyncRunStage(screenExecutor(executor), runnable);
    }

    /**
     * 返回一个已完成的 CompletableFuture，用 value 作为结果。
     * 调用 supplier 执行此任务。
     *
     * @param value the value
     * @param <U> the type of the value
     * @return the completed CompletableFuture
     */
    public static <U> CompletableFuture<U> completedFuture(U value) {
        return new CompletableFuture<U>((value == null) ? NIL : value);
    }

    /**
     * 如果已经完成，无论是正常完成还是异常还是取消，都返回 true。
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * 获取结果，如果任务未完成，则等待。
     * 如果发生了异常，则抛出异常。
     *
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        return reportGet((r = result) == null ? waitingGet(true) : r);
    }

    /**
     * 有超时限制地获取结果。
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException if this future completed exceptionally
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Object r;
        long nanos = unit.toNanos(timeout);
        return reportGet((r = result) == null ? timedGet(nanos) : r);
    }

    /**
     * 阻塞等待任务执行完成并获取执行结果。
     *
     * @return the result value
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T join() {
        Object r;
        return reportJoin((r = result) == null ? waitingGet(false) : r);
    }

    /**
     * 立即获取执行结果。
     * 如果任务还没有完成，直接返回默认值。
     *
     * @param valueIfAbsent the value to return if not completed
     * @return the result value, if completed, else the given valueIfAbsent
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if this future completed
     * exceptionally or a completion computation threw an exception
     */
    public T getNow(T valueIfAbsent) {
        Object r;
        return ((r = result) == null) ? valueIfAbsent : reportJoin(r);
    }

    /**
     * If not already completed, sets the value returned by {@link
     * #get()} and related methods to the given value.
     *
     * @param value the result value
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean complete(T value) {
        boolean triggered = completeValue(value);
        postComplete();
        return triggered;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered = internalComplete(new AltResult(ex));
        postComplete();
        return triggered;
    }

    // thenApply，把上个计算结果转换为当前任务的输入参数
    public <U> CompletableFuture<U> thenApply(
            Function<? super T,? extends U> fn) {
        return uniApplyStage(null, fn);
    }

    // 由 ForkJoinPool.commonPool 运行
    public <U> CompletableFuture<U> thenApplyAsync(
            Function<? super T,? extends U> fn) {
        return uniApplyStage(asyncPool, fn);
    }

    // 由指定线程池运行
    public <U> CompletableFuture<U> thenApplyAsync(
            Function<? super T,? extends U> fn, Executor executor) {
        return uniApplyStage(screenExecutor(executor), fn);
    }

    // thenAccept，把上个计算结果转换为当前任务的输入参数，
    // 和 thenApply 的区别是不会返回任何处理结果
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniAcceptStage(null, action);
    }

    // 由 ForkJoinPool.commonPool 运行
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return uniAcceptStage(asyncPool, action);
    }

    // 由指定线程池运行
    public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action,
                                                   Executor executor) {
        return uniAcceptStage(screenExecutor(executor), action);
    }

    public CompletableFuture<Void> thenRun(Runnable action) {
        return uniRunStage(null, action);
    }

    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return uniRunStage(asyncPool, action);
    }

    public CompletableFuture<Void> thenRunAsync(Runnable action,
                                                Executor executor) {
        return uniRunStage(screenExecutor(executor), action);
    }

    public <U,V> CompletableFuture<V> thenCombine(
            CompletionStage<? extends U> other,
            BiFunction<? super T,? super U,? extends V> fn) {
        return biApplyStage(null, other, fn);
    }

    public <U,V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T,? super U,? extends V> fn) {
        return biApplyStage(asyncPool, other, fn);
    }

    public <U,V> CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T,? super U,? extends V> fn, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, fn);
    }

    // thenAcceptBoth：依赖当前 CompletableFuture 和 other 的结果作为 action 的输入
    public <U> CompletableFuture<Void> thenAcceptBoth(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(null, other, action);
    }

    // 在 ForkJoinPool.commonPool 中运行
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(asyncPool, other, action);
    }

    // 在指定线程池中运行
    public <U> CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        return biAcceptStage(screenExecutor(executor), other, action);
    }

    // runAfterBoth：等待当前 CompletableFuture 任务和 other 完成后执行
    public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other,
                                                Runnable action) {
        return biRunStage(null, other, action);
    }

    // 在 ForkJoinPool.commonPool 中运行
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                     Runnable action) {
        return biRunStage(asyncPool, other, action);
    }

    // 在指定的线程池中运行
    public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                     Runnable action,
                                                     Executor executor) {
        return biRunStage(screenExecutor(executor), other, action);
    }

    // applyToEither：把两个结果中任意一个结果作为当前执行的输入参数，并且会返回执行结果
    public <U> CompletableFuture<U> applyToEither(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(null, other, fn);
    }

    // 在 ForkJoinPool.commonPool 中执行
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(asyncPool, other, fn);
    }

    // 在指定线程池中执行
    public <U> CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn,
            Executor executor) {
        return orApplyStage(screenExecutor(executor), other, fn);
    }

    // acceptEither：把两个结果中任意一个结果作为当前执行的输入参数，没有执行结果
    public CompletableFuture<Void> acceptEither(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(null, other, action);
    }

    // 在 ForkJoinPool.commonPool 中执行
    public CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(asyncPool, other, action);
    }

    // 在指定线程池中执行
    public CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action,
            Executor executor) {
        return orAcceptStage(screenExecutor(executor), other, action);
    }

    // acceptEither：在两个任务中任意一个执行完之后再执行，且没有执行结果
    public CompletableFuture<Void> runAfterEither(CompletionStage<?> other,
                                                  Runnable action) {
        return orRunStage(null, other, action);
    }

    // 在 ForkJoinPool.commonPool 中执行
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                       Runnable action) {
        return orRunStage(asyncPool, other, action);
    }

    // 在指定线程池中执行
    public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                       Runnable action,
                                                       Executor executor) {
        return orRunStage(screenExecutor(executor), other, action);
    }

    // thenCompose：连接两个 CompletableFuture，将当前 CompletableFuture 结果作为 fn 的参数
    public <U> CompletableFuture<U> thenCompose(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(null, fn);
    }

    // 在 ForkJoinPool.commonPool 中运行
    public <U> CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(asyncPool, fn);
    }

    // 在指定线程池中运行
    public <U> CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn,
            Executor executor) {
        return uniComposeStage(screenExecutor(executor), fn);
    }

    // 完成后同步执行 action，即在执行 CompletableFuture 的线程中执行
    public CompletableFuture<T> whenComplete(
            BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(null, action);
    }

    // 完成后在 asyncPool 中异步执行 action
    public CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(asyncPool, action);
    }

    // 完成后在指定线程池中执行 action
    public CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return uniWhenCompleteStage(screenExecutor(executor), action);
    }

    // handle：获取当前任务的结果，将其作为 fn 的输入，并保存执行结果
    public <U> CompletableFuture<U> handle(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(null, fn);
    }

    // 在 ForkJoinPool.commonPool 中执行任务
    public <U> CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(asyncPool, fn);
    }

    // 在指定线程池中执行任务
    public <U> CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return uniHandleStage(screenExecutor(executor), fn);
    }

    /**
     * 返回当前的 CompletableFuture.
     *
     * @return this CompletableFuture
     */
    public CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // not in interface CompletionStage

    /**
     * 异常后执行。
     *
     * @param fn the function to use to compute the value of the
     * returned CompletableFuture if this CompletableFuture completed
     * exceptionally
     * @return the new CompletableFuture
     */
    public CompletableFuture<T> exceptionally(
            Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(fn);
    }

    /* ------------- Arbitrary-arity constructions -------------- */

    /**
     * 等待所有任务执行完毕。
     *在继续往下执行之前，等待所有任务完成，用法：
     * CompletableFuture.allOf(c1, c2, c3).join();
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the
     * given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }

    /**
     * 等待某个任务执行完毕
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed with the
     * result or exception of any of the given CompletableFutures when
     * one completes
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
    public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
        return orTree(cfs, 0, cfs.length - 1);
    }

    /* ------------- Control and status methods -------------- */

    /**
     * If not already completed, completes this CompletableFuture with
     * a {@link CancellationException}. Dependent CompletableFutures
     * that have not already completed will also complete
     * exceptionally, with a {@link CompletionException} caused by
     * this {@code CancellationException}.
     *
     * @param mayInterruptIfRunning this value has no effect in this
     * implementation because interrupts are not used to control
     * processing.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
                internalComplete(new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }

    /**
     * Returns {@code true} if this CompletableFuture was cancelled
     * before it completed normally.
     *
     * @return {@code true} if this CompletableFuture was cancelled
     * before it completed normally
     */
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
                (((AltResult)r).ex instanceof CancellationException);
    }

    /**
     * Returns {@code true} if this CompletableFuture completed
     * exceptionally, in any way. Possible causes include
     * cancellation, explicit invocation of {@code
     * completeExceptionally}, and abrupt termination of a
     * CompletionStage action.
     *
     * @return {@code true} if this CompletableFuture completed
     * exceptionally
     */
    public boolean isCompletedExceptionally() {
        Object r;
        return ((r = result) instanceof AltResult) && r != NIL;
    }

    /**
     * Forcibly sets or resets the value subsequently returned by
     * method {@link #get()} and related methods, whether or not
     * already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param value the completion value
     */
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /**
     * Forcibly causes subsequent invocations of method {@link #get()}
     * and related methods to throw the given exception, whether or
     * not already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param ex the exception
     * @throws NullPointerException if the exception is null
     */
    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        result = new AltResult(ex);
        postComplete();
    }

    /**
     * Returns the estimated number of CompletableFutures whose
     * completions are awaiting completion of this CompletableFuture.
     * This method is designed for use in monitoring system state, not
     * for synchronization control.
     *
     * @return the number of dependent CompletableFutures
     */
    public int getNumberOfDependents() {
        int count = 0;
        for (Completion p = stack; p != null; p = p.next)
            ++count;
        return count;
    }

    /**
     * Returns a string identifying this CompletableFuture, as well as
     * its completion state.  The state, in brackets, contains the
     * String {@code "Completed Normally"} or the String {@code
     * "Completed Exceptionally"}, or the String {@code "Not
     * completed"} followed by the number of CompletableFutures
     * dependent upon its completion, if any.
     *
     * @return a string identifying this CompletableFuture, as well as its state
     */
    public String toString() {
        Object r = result;
        int count;
        return super.toString() +
                ((r == null) ?
                        (((count = getNumberOfDependents()) == 0) ?
                                "[Not completed]" :
                                "[Not completed, " + count + " dependents]") :
                        (((r instanceof AltResult) && ((AltResult)r).ex != null) ?
                                "[Completed exceptionally]" :
                                "[Completed normally]"));
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long STACK;
    private static final long NEXT;
    static {
        try {
            final sun.misc.Unsafe u;
            UNSAFE = u = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CompletableFuture.class;
            RESULT = u.objectFieldOffset(k.getDeclaredField("result"));
            STACK = u.objectFieldOffset(k.getDeclaredField("stack"));
            NEXT = u.objectFieldOffset
                    (Completion.class.getDeclaredField("next"));
        } catch (Exception x) {
            throw new Error(x);
        }
    }
}

