/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

/**
 * An object that creates new threads on demand.
 * Using thread factories removes hardwiring of calls to Thread(Runnable) new Thread, enabling applications to use special thread subclasses, priorities, etc.
 *
 * The simplest implementation of this interface is just:
     *
     *  class SimpleThreadFactory implements ThreadFactory {
     *      public Thread newThread(Runnable r) {
     *          return new Thread(r);
     *      }
     *  }
 *
 * The Executors.defaultThreadFactory method provides a more useful simple implementation, that sets the created thread context to known values before returning it.
 * @since 1.5
 * @author Doug Lea
 */
public interface ThreadFactory {

    /**
     * Constructs a new Thread.
     * Implementations may also initialize priority, name, daemon status, ThreadGroup, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or null if the request to create a thread is rejected
     */
    Thread newThread(Runnable r);
}
