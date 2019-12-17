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

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 一个 ExecutorService 可以调度命令在给定的延迟时间后运行，或定期执行。
 *
 * schedule 方法创建具有各种延迟的任务，并返回可以用于取消或检查执行的
 * 任务对象。scheduleAtFixedRate 和 scheduleWithFixedDelay 方法创建
 * 并执行定期运行的任务，直到其被取消为止。
 *
 * 使用 Executor.execute(Runnable) 和 ExecutorService.submit 提交的任务
 * 延迟为 0。在 schedule 方法中也允许零延迟和负延迟（不是周期延迟），并
 * 将其视为立即执行的请求。
 *
 * 所有的 schedule 方法都接受相对延迟和周期作为参数，而不是绝对的时间或
 * 日期。把一个 java.util.Date 类型的绝对的时间转换成需要的形式是一件简单
 * 的事。例如，在将来的某个时间调度，可以使用：
 * schedule(task, date.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)。
 *但是要注意，由于网络时间同步协议、时钟漂移或其它因素，相对延迟的时间
 * 不一定与当前 Date 相同。
 *
 * Executors 为这个 ScheduledExecutorService 的实现提供了方便的工厂方法。
 *
 * 使用案例
 *
 * 此 ScheduledExecutorService 在一个小时内每十秒执行一次：
 *
 * import static java.util.concurrent.TimeUnit.*;
 * class BeeperControl {
 *   private final ScheduledExecutorService scheduler =
 *     Executors.newScheduledThreadPool(1);
 *
 *   public void beepForAnHour() {
 *     final Runnable beeper = new Runnable() {
 *       public void run() { System.out.println("beep"); }
 *     };
 *     final ScheduledFuture<?> beeperHandle =
 *       scheduler.scheduleAtFixedRate(beeper, 10, 10, SECONDS);
 *     scheduler.schedule(new Runnable() {
 *       public void run() { beeperHandle.cancel(true); }
 *     }, 60 * 60, SECONDS);
 *   }
 * }}
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ScheduledExecutorService extends ExecutorService {

    /**
     * 创建并执行一个在给定延迟之后仅执行一次的动作。
     *
     * @param command the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of
     *         the task and whose {@code get()} method will return
     *         {@code null} upon completion
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if command is null
     */
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay, TimeUnit unit);

    /**
     * 创建并执行一个在给定延迟之后可用的 ScheduledFuture。
     *
     * @param callable the function to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @param <V> the type of the callable's result
     * @return a ScheduledFuture that can be used to extract result or cancel
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if callable is null
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay, TimeUnit unit);

    /**
     * 创建并执行一个周期性操作，该操作在给定的初始延迟之后启用，之后在给定的
     * 时间段内使用；即在 initialDelay 之后执行，然后是 initialDelay + period，
     * 然后是 initialDelay + 2 * period，以此类推。
     * 如果任务的执行遇到异常，则禁止后续执行。否则，任务将仅通过执行程序
     * 的取消或终止而终止。如果此任务的执行时间超过周期，则后续执行可能会
     * 延迟执行，但不会并发执行。
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of
     *         the task, and whose {@code get()} method will throw an
     *         exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if period less than or equal to zero
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit);

    /**
     * 创建并执行一个周期性操作，在给定延迟之后启用，并周期性执行，时间间隔
     * 设定为一个执行终止到下一个执行开始。如果任务的执行遇到异常，则禁止
     * 后续执行。否则，任务将仅通过执行程序的取消或终止而终止。
     *
     * @param command the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one
     * execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return a ScheduledFuture representing pending completion of
     *         the task, and whose {@code get()} method will throw an
     *         exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if command is null
     * @throws IllegalArgumentException if delay less than or equal to zero
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit);

}

