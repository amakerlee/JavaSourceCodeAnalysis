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

/**
 * 一个线程可以专有的同步器。此类为创建可能需要所有权概念的锁和相关的
 * 同步器提供了基础。AbstractOwnableSynchronizer 类自身并不管理或使用
 * 此信息。但是，子类和工具可以使用适当维护的值来帮助控制和监视访问并
 * 提供诊断。
 *
 * @since 1.6
 * @author Doug Lea
 */
public abstract class AbstractOwnableSynchronizer
        implements java.io.Serializable {

    /** Use serial ID even though all fields transient. */
    private static final long serialVersionUID = 3737899427754241961L;

    /**
     * 提供给子类的空构造函数。
     */
    protected AbstractOwnableSynchronizer() { }

    /**
     * 独占模式同步的当前持有者。
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * 设置当前拥有独占访问权限的线程。
     * null 参数表示没有线程拥有访问权限。
     * 否则此方法不会强加任何同步或 volatile 字段访问。
     * @param thread the owner thread
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * 返回由 setExclusiveOwnerThread 设置的最后一个线程，如果没有设置
     * 则为 null。否则此方法不会强加任何同步或 volatile 字段访问。
     * @return the owner thread
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}

