/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.List;
import java.util.Collection;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * An Executor that provides methods to manage termination and methods that can produce a Future for tracking progress of one or more asynchronous tasks.
 *
 * 关闭线程池，要考虑拒绝新任务，同时也要考虑如何处理已经提交的任务（还在等待队列里的任务和已经正在执行的任务）
 * shutdown() 会在停止前继续执行已经提交的任务；shutdownNow() 则会阻止执行等待队列里的任务，对于已经提交的任务也会尝试阻止（应该是发出中断请求），但是无法保证任务会马上终止。
 * An ExecutorService can be shut down, which will cause it to reject new tasks. Two different methods are provided for shutting down an ExecutorService.
 * The shutdown method will allow previously submitted tasks to execute before terminating,
 * while the shutdownNow method prevents waiting tasks from starting and attempts to stop currently executing tasks.
 *
 * Upon termination, an executor has no tasks actively executing, no tasks awaiting execution, and no new tasks can be submitted.
 * An unused ExecutorService should be shut down to allow reclamation of its resources.
 *
 * Method submit extends base method Executor.execute by creating and returning a Future that can be used to cancel execution and/or wait for completion.
 * 批量执行方法：invokeAny() 执行一个任务即可返回，其他任务取消；invokeAll() 则必须执行全部任务才能返回
 * Methods invokeAny and invokeAll perform the most commonly useful forms of bulk execution, executing a collection of tasks and then waiting for at least one, or all, to complete.
 *
 * Class ExecutorCompletionService can be used to write customized variants of these methods.
 *
 * The Executors class provides factory methods for the executor services provided in this package.
 *
 * Usage Examples
 *
 * Here is a sketch of a network service in which threads in a thread pool service incoming requests. It uses the preconfigured Executors.newFixedThreadPool factory method:
 *
     * class NetworkService implements Runnable {
     *      private final ServerSocket serverSocket;
     *      private final ExecutorService pool;
     *
     *      public NetworkService(int port, int poolSize) throws IOException {
     *          serverSocket = new ServerSocket(port);
     *          pool = Executors.newFixedThreadPool(poolSize);
     *      }
     *
     *      public void run() { // run the service
     *          try {
     *              for (;;) { pool.execute(new Handler(serverSocket.accept())); }
     *          } catch (IOException ex) {
     *              pool.shutdown();
     *          }
     *      }
     * }
     *
     * class Handler implements Runnable {
     *      private final Socket socket;
     *      Handler(Socket socket) { this.socket = socket; }
     *
     *      public void run() {
     *          // read and service request on socket
     *      }
     * }
 *
 * The following method shuts down an ExecutorService in two phases:
 * first by calling shutdown to reject incoming tasks, and then calling shutdownNow, if necessary, to cancel any lingering tasks.
 *
     * void shutdownAndAwaitTermination(ExecutorService pool) {
     *      pool.shutdown(); // 首先阻止新任务提交
     *      try {
     *          if (!pool.awaitTermination(60, TimeUnit.SECONDS)) { // 为还存在的任务等待60s,如果等待超时则取消已提交的任务
     *              pool.shutdownNow();
     *
     *              if (!pool.awaitTermination(60, TimeUnit.SECONDS)) // 继续等待60s,如果依然等待超时则表明还有任务在运行，线程池依然没有关闭
     *                  System.err.println("Pool did not terminate");
     *          }
     *      } catch (InterruptedException ie) {
     *          pool.shutdownNow(); // Re-Cancel if current thread also interrupted
     *          Thread.currentThread().interrupt();
     *      }
     * }
 *
 * Memory consistency effects: Actions in a thread prior to the submission of a Runnable or Callable task to an ExecutorService happen-before any actions taken by that task,
 * which in turn happen-before the result is retrieved via Future.get().
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ExecutorService extends Executor {

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * This method does not wait for previously submitted tasks to complete execution.  Use awaitTermination method to do that.
     *
     * @throws SecurityException if a security manager exists and shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify because it does not hold java.lang.RuntimePermission,
     *         or the security manager's checkAccess method denies access.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the processing of waiting tasks, and returns a list of the tasks that were awaiting execution.
     *
     * This method does not wait for actively executing tasks to terminate.  Use awaitTermination method to do that.
     *
     * 无法保证一定终止正在执行的任务，只能尽全力去做。取消正在执行的任务的典型的实现方式就是采用中断
     * There are no guarantees beyond best-effort attempts to stop processing actively executing tasks. For example,
     * typical implementations will cancel via Thread.interrupt, so any task that fails to respond to interrupts may never terminate.
     *
     * @return list of tasks that never commenced execution
     * @throws SecurityException if a security manager exists and shutting down this ExecutorService may manipulate
     *         threads that the caller is not permitted to modify because it does not hold java.lang.RuntimePermission,
     *         or the security manager's checkAccess method enies access.
     */
    List<Runnable> shutdownNow();

    /**
     *
     * @return true if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * 如果都没有调用 shutdown() 或者 shutdownNow() 关闭服务，isTerminated() 只会返回false。 只有在服务关闭的前提下，我们才会去讨论终止的问题
     * Note that isTerminated method never return true unless either shutdown or shutdownNow was called first.
     *
     * @return true if all tasks have completed following shut down
     */
    boolean isTerminated();

    /**
     * 调用 shutdown() 后继续调用awaitTermination()，该方法会一直阻塞直到超时或者所有已提交的任务都执行完成（也就是不再有需要执行的任务）。
     * Blocks until all tasks have completed execution after a shutdown request, or the timeout occurs, or the current thread is interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return <tt>true</tt> if this executor terminated and
     *         <tt>false</tt> if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;


    /**
     * Submits a value-returning task for execution and returns a
     * Future representing the pending results of the task. The
     * Future's <tt>get</tt> method will return the task's result upon
     * successful completion.
     *
     * <p>
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * <tt>result = exec.submit(aCallable).get();</tt>
     *
     * <p> Note: The {@link Executors} class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, {@link java.security.PrivilegedAction} to
     * {@link Callable} form so they can be submitted.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's <tt>get</tt> method will
     * return the given result upon successful completion.
     *
     * @param task the task to submit
     * @param result the result to return
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's <tt>get</tt> method will
     * return <tt>null</tt> upon <em>successful</em> completion.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    Future<?> submit(Runnable task);

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results when all complete.
     * {@link Future#isDone} is <tt>true</tt> for each
     * element of the returned list.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @return A list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list, each of which has completed.
     * @throws InterruptedException if interrupted while waiting, in
     *         which case unfinished tasks are cancelled.
     * @throws NullPointerException if tasks or any of its elements are <tt>null</tt>
     * @throws RejectedExecutionException if any task cannot be
     *         scheduled for execution
     */

    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException;

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results
     * when all complete or the timeout expires, whichever happens first.
     * {@link Future#isDone} is <tt>true</tt> for each
     * element of the returned list.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return a list of Futures representing the tasks, in the same
     *         sequential order as produced by the iterator for the
     *         given task list. If the operation did not time out,
     *         each task will have completed. If it did time out, some
     *         of these tasks will not have completed.
     * @throws InterruptedException if interrupted while waiting, in
     *         which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks, any of its elements, or
     *         unit are <tt>null</tt>
     * @throws RejectedExecutionException if any task cannot be scheduled
     *         for execution
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                  long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any element task
     *         subject to execution is <tt>null</tt>
     * @throws IllegalArgumentException if tasks is empty
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the result returned by one of the tasks.
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, or unit, or any element
     *         task subject to execution is <tt>null</tt>
     * @throws TimeoutException if the given timeout elapses before
     *         any task successfully completes
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     *         for execution
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                    long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
