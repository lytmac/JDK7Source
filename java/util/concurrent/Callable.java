/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

/**
 * A task that returns a result and may throw an exception. Implementors define a single method with no arguments called call.
 *
 * The Callable interface is similar to java.lang.Runnable, in that both are designed for classes whose instances are potentially executed by
 * another thread. A Runnable, however, does not return a result and cannot throw a checked exception.
 *
 * The Executors class contains utility methods to convert from other common forms to Callable classes.
 *
 * @see Executor
 * @since 1.5
 * @author Doug Lea
 * @param <V> the result type of method call
 */
public interface Callable<V> {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    V call() throws Exception;
}
