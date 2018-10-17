/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;

/**
 * A synchronizer that may be exclusively owned by a thread.
 * 这个类为创建锁和相关的同步器关联所有者信息提供了基础
 * This class provides a basis for creating locks and related synchronizers that may entail a notion of ownership.
 *
 * AOS 并不管理也不会使用这些信息（独占锁的线程），只提供了简单的 get/set 方法。子类可以基于自身的场景需求维护和使用这些信息。
 * The AbstractOwnableSynchronizer class itself does not manage or use this information.
 * However, subclasses and tools may use appropriately maintained values to help control and monitor access and provide diagnostics.
 *
 * @since 1.6
 * @author Doug Lea
 */
public abstract class AbstractOwnableSynchronizer implements java.io.Serializable {

    /** Use serial ID even though all fields transient. */
    private static final long serialVersionUID = 3737899427754241961L;

    /**
     * Empty constructor for use by subclasses.
     */
    protected AbstractOwnableSynchronizer() { }

    /**
     * The current owner of exclusive mode synchronization.
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * Sets the thread that currently owns exclusive access. A null argument indicates that no thread owns access.
     * This method does not otherwise impose any synchronization or volatile field accesses.
     */
    protected final void setExclusiveOwnerThread(Thread t) {
        exclusiveOwnerThread = t;
    }

    /**
     * Returns the thread last set by setExclusiveOwnerThread, or null if never set.
     * This method does not otherwise impose any synchronization or volatile field accesses.
     * @return the owner thread
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
