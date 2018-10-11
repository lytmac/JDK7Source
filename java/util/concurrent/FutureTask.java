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

package java.util.concurrent;

import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.
 * This class provides a base implementation of Future, with methods to start and cancel a computation,
 * query to see if the computation is complete, and retrieve the result of the computation.
 *
 * The result can only be retrieved when the computation has completed; the get methods will block if the computation has not yet completed.
 * Once the computation has completed, the computation cannot be restarted or cancelled (unless the computation is invoked using runAndReset).
 *
 * A FutureTask can be used to wrap a Callable or Runnable object.
 * Because FutureTask implements Runnable, a FutureTask can be submitted to an Executor for execution.
 *
 * In addition to serving as a standalone class, this class provides protected functionality that may be useful when creating customized task classes.
 *
 * @param <V> The result type returned by this FutureTask's get methods
 * @author Doug Lea
 * @since 1.5
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this class that relied on AbstractQueuedSynchronizer,
     * mainly to avoid surprising users about retaining interrupt status during cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * 只有 set()、setException()、cancel() 三个方法可以改变 state。运行期间的瞬态值可以设为 COMPLETING 或 INTERRUPTING
     * The run state of this task, initially NEW.  The run state transitions to a terminal state only in methods set, setException, and cancel.
     * 
     * 只有在将设置运算结果的那一瞬间，状态才为COMPLETING。
     * During completion, state may take on transient values of COMPLETING (while outcome is being set) or INTERRUPTING (only while interrupting
     * the runner to satisfy a cancel(true)).
     * 
     * Transitions from these intermediate to final states use cheaper ordered/lazy writes because values are unique and cannot be further modified.
     * 
     * Possible state transitions: 可能出现的几种状态装换路线
     * 执行过程正常完成: NEW -> COMPLETING -> NORMAL
     * 执行过程出现异常: NEW -> COMPLETING -> EXCEPTIONAL
     * 执行过程中被取消: NEW -> CANCELLED
     * 执行过程中被中断: NEW -> INTERRUPTING -> INTERRUPTED
     */

    private volatile int state;
    private static final int NEW = 0;  //任务未执行
    private static final int COMPLETING = 1;  //任务进行(瞬时状态)
    private static final int NORMAL = 2;  //任务正常完成
    private static final int EXCEPTIONAL = 3;  //任务抛出异常
    private static final int CANCELLED = 4;  //任务被取消
    private static final int INTERRUPTING = 5;  //任务中断(瞬时状态)
    private static final int INTERRUPTED = 6;  //任务已中断

    /**
     * The underlying callable; nulled out after running
     */
    private Callable<V> callable;
    /**
     * The result to return or exception to throw from get()
     */
    private Object outcome; // non-volatile, protected by state reads/writes
    /**
     * The thread running the callable; CASed during run()
     */
    private volatile Thread runner;
    /**
     * Treiber stack of waiting threads
     */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL) return (V) x;
        if (s >= CANCELLED) throw new CancellationException();

        //在运行过程中抛出异常，run() 会调用 setException() 将异常赋值为 outcome. 对外应统一已抛出异常的形式给出。
        throw new ExecutionException((Throwable) x);
    }

    /**
     * Creates a FutureTask that will, upon running, execute the given Callable.
     *
     * @param callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a FutureTask that will, upon running, execute the given Runnable, and arrange that get will return the given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result   the result to return on successful completion. If you don't need a particular result, consider using constructions of the form: Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    public boolean isDone() {
        //任务从状态NEW转变为其他状态，都是发生在计算执行完，将结果赋值到outcome的时间点。
        return state != NEW;
    }

    /**
     * 取消任务，若取消任务成功则返回true，否则返回false。
     *
     * @param mayInterruptIfRunning: 是否允许取消正在执行却没有执行完毕的任务。
     * @return 是否成功取消任务
     * 1 - 任务完成执行，无论mayInterruptIfRunning为true还是false，肯定返回false
     * 2 - 任务还未执行，无论mayInterruptIfRunning为true还是false，肯定返回true
     * 3 - 任务正在执行，若mayInterruptIfRunning设置为true，则返回true，若mayInterruptIfRunning设置为false，则返回false。
     * 
     * 任何状态的变更都需要采用CAS. 别的线程可能会对当前线程进行中断或取消以改变状态。
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (state != NEW) //只有还未将计算结果提交至outcome的情况下才能取消任务
            return false;
        if (mayInterruptIfRunning) {
            if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, INTERRUPTING)) {
                //将任务状态改为中断中，修改成功后调用interrupt方法中断当前线程。
                return false;
            }

            //走到这里，该任务即可取消，唯一能做的就是给该线程中断标志位打标
            Thread t = runner;
            if (t != null)
                t.interrupt();
            UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED); // final state
        } else if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, CANCELLED)) {
            //不允许取消正在执行却还没有执行完毕的任务
            //状态必须是从 NEW -> CANCELLED CAS操作才能成功。所以只有还未执行的任务才可能返回true
            return false;
        }

        //任务取消，要唤醒所有等待该任务计算结果的挂起在等待队列里的线程
        finishCompletion();
        return true;
    }

    /**
     * @throws CancellationException
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }

    /**
     * @throws CancellationException
     */
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING && (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            //唯一的情况是：state == NEW
            throw new TimeoutException();
        return report(s);
    }

    /**
     * 正常完成或者被取消了，状态都会被视为已完成
     * Protected method invoked when this task transitions to state isDone (whether normally or via cancellation).
     * The default implementation does nothing. Subclasses may override this method to invoke completion callbacks or perform bookkeeping.
     *
     * 重写该方法，可以在该方法内部去检查该任务是否是被取消的
     * Note that you can query status inside the implementation of this method to determine whether this task has been cancelled.
     */
    protected void done() {
    }

    /**
     * Sets the result of this future to the given value unless this future has already been set or has been cancelled.
     * This method is invoked internally by the run method upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        //只有在尝试将运算结果放到 Future.outcome 时，才需要将 state 置位 COMPLETING。放置完成后，state 马上置位为 NORMAL
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state

            //清理等待队列的所有节点
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an ExecutionException with the given throwable as its cause, unless this future has already been set or has been cancelled.
     * This method is invoked internally by the run method upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }

    public void run() {
        if (state != NEW || !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread())) {
            return;
        }

        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call(); //调用call() 执行具体的任务
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then resets this future to initial state, failing to do so if the computation
     * encounters an exception or is cancelled.  This is designed for use with tasks that intrinsically execute more than once.
     *
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {
        if (state != NEW || !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
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
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber stack.
     * See other classes such as Phaser and SynchronousQueue for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;

        WaitNode() {
            thread = Thread.currentThread();
        }
    }

    /**
     * 一旦一个任务执行完成，那么所有等待该任务结果的线程都应该从等待队列中移除并唤醒。
     * Removes and signals all waiting threads, invokes done(), and nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null; ) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (; ; ) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null) break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null; // to reduce footprint
    }

    /**
     * 算法还是常规的处理等待的方式，尝试获取，未果则将当前线程加入等待队列后挂起，等待被唤醒。
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos) throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (; ; ) {
            /**
             * 这里依然是经典的唤醒后继续执行的问题！！！
             * 等待队列的线程被唤醒后将继续在这个循环体内执行，具体的位置就是 LockSupport.park()、LockSupport.parkNanos()方法，也就是循环开始的位置。
             * 唤醒操作只有cancel()、 set()、 setException() 这三个方法会执行。
             * cancel() 调用成功后，state == CANCELED， get()会检查并抛出异常CancellationException
             * set()、setException() 则会分别将运算结果和异常赋值给outcome。get()可以通过检查tate 是 NORMAL 还是 EXCEPTIONAL 来区分是否执行完成。
             */

            //因为是无限循环，所以每次进来前先校验线程是否被中断。运行这段代码的是调用 get()的等待运行结果的线程。
            if (Thread.interrupted()) {
                removeWaiter(q); //调用futureTask.get()的线程需要从队列中移除
                throw new InterruptedException();
            }

            int s = state;
            if (s > COMPLETING) { //不是0：NEW，也不是1：COMPLETING
                if (q != null)
                    q.thread = null;
                return s;
            } else if (s == COMPLETING) // cannot time out yet
                Thread.yield(); //为什么要调用yield()???

                //前两个条件都不满足，state == NEW。下面所有的操作都是基于该前提的。
            else if (q == null)
                q = new WaitNode();
            else if (!queued) //将标识该线程的WaitNode入队
                /**
                 * 注意这里只是将线程节点入队，但是并没有将线程挂起。入队后下一轮循环才会将线程挂起。
                 */
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset, q.next = waiters, q);
            else if (timed) { //在已经入队的情况下，校验是否配置了超时。未配置超时则直接挂起
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) { //已经超时了
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            } else
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (; ; ) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
