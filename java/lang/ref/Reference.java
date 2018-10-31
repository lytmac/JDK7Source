/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang.ref;

import sun.misc.Cleaner;


/**
 * Abstract base class for reference objects.
 * This class defines the operations common to all reference objects.
 *
 * 引用对象是基于垃圾回收器的需求来实现的，所以不能直接定义类继承此类
 * Because reference objects are implemented in close cooperation with the garbage collector, this class may not be subclassed directly.
 *
 * @author Mark Reinhold
 * @since 1.2
 */

public abstract class Reference<T> {

    /* A Reference instance is in one of four possible internal states: 一个Reference对象的状态只会在以下四种内部状态中装换
     *
         * Active: Subject to special treatment by the garbage collector.
         *
         * 状态变换路径有两种：Active -> Pending / Active -> Inactive。具体取决于实例是否在创建时向队列注册(是否初始化了ReferenceQueue字段)
         * 引用的可达性变化，是否就是该对象已经没有强引用了？那么这时对象回收了吗？
         * Some time after the collector detects that the reachability of the referent has changed to the appropriate state,
         * it changes the instance's state to either Pending or Inactive, depending upon whether or not the instance was
         * registered with a queue when it was created. In the former case it also adds the instance to the pending-Reference list.
         *
         * 新创建的对象状态都是Active
         * Newly-created instances are Active.
         *
         * Pending: An element of the pending-Reference list, waiting to be enqueued by the Reference-handler thread. Unregistered instances are never in this state.
         *
         * 该Reference对象已经入ReferenceQueue了，意味着该Reference指向的对象已经被GC回收了
         * Enqueued: An element of the queue with which the instance was registered when it was created.
         * When an instance is removed from its ReferenceQueue, it is made Inactive. Unregistered instances are never in this state.
         *
         * Inactive: Nothing more to do.
         * Once an instance becomes Inactive its state will never change again.
     *
     * The state is encoded in the queue and next fields as follows:(Reference对象处于不同的状态，对应的queue和next的取值也会发生变化)
     *
         * Active:
         * 创建Reference对象时，对ReferenceQueue字段赋值了，则为传入的值；否则就是默认的ReferenceQueue.NULL
         * queue = ReferenceQueue with which instance is registered, or ReferenceQueue.NULL if it was not registered with a queue;
         * next = null.
         *
         * Pending:
         * queue = ReferenceQueue with which instance is registered;
         * 需要明确的是：queue -> ReferenceQueue; list -> pending
         * next = Following instance in queue, or this if at end of list.
         *
         * Enqueued:
         * queue = ReferenceQueue.ENQUEUED;
         * next = Following instance in queue, or this if at end of list.
         *
         * Inactive:
         * queue = ReferenceQueue.NULL;
         * next = this.
     *
     * With this scheme the collector need only examine the next field in order to determine whether a Reference instance requires special treatment: If
     * the next field is null then the instance is active; if it is non-null, then the collector should treat the instance normally.
     *
     * To ensure that concurrent collector can discover active Reference objects without interfering with application threads that may apply
     * the enqueue() method to those objects, collectors should link discovered objects through the discovered field.
     */

    private T referent;         /* Treated specially by GC */ //所指向的对象

    /*
     * 引用队列，在检测到适当的可到达性更改后，GC将已注册的引用对象添加到该队列中.(先经由pending list，由ReferenceHandler执行入队)
     */
    ReferenceQueue<? super T> queue;

    Reference next;

    transient private Reference<T> discovered;  /* used by VM */


    /* Object used to synchronize with the garbage collector.
     * 垃圾收集器必须先获取到锁之后才能进入收集周期
     * The collector must acquire this lock at the beginning of each collection cycle.
     * 任何持有此锁的代码都必须尽快完成，不分配新对象，并避免调用用户代码
     * It is therefore critical that any code holding this lock complete as quickly as possible, allocate no new objects, and avoid calling user code.
     */
    static private class Lock {
    }

    ;

    //GC线程与ReferenceHandler线程基于这个对象进行同步.
    // 当GC回收了某个Reference指向的对象，将Reference加入pending list，ReferenceHandler继续处理将其放至ReferenceQueue
    private static Lock lock = new Lock();


    /* List of References waiting to be enqueued.(等待进入ReferenceQueue的Reference对象)
     * 需要明确的是：GC只是将回收的对象的Reference引用加入到pending list，然后由ReferenceHandler将其移至ReferenceQueue
     * The collector adds References to this list, while the Reference-handler thread removes them.
     * This list is protected by the above lock object.
     */
    private static Reference pending = null; //这是全局链表pending list的头指针

    /*
     * High-priority thread to enqueue pending References
     */
    private static class ReferenceHandler extends Thread {

        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

        public void run() {
            for (; ; ) {

                Reference r;
                synchronized (lock) { //ReferenceHandler线程与GC线程基于这把锁的同步，完成了一个生产者消费者模型
                    if (pending != null) { //GC将该Reference指向的对象回收，并将该Reference对象放入pending

                        /**==========================遍历结束的标识是：rn == r == null===============================**/
                        r = pending;
                        Reference rn = r.next;
                        pending = (rn == r) ? null : rn;
                        r.next = r; //遍历结束时，每个已移除pending list的Reference.next == null，不过这是中间状态，很快又要压入ReferenceQueue队列
                        /**==========================================================================================**/

                    } else {
                        try {
                            //等待GC线程将其唤醒，这里要明确ReferenceHandler是一个全局的守护线程，在不作任何操作时应该挂起，避免浪费计算资源
                            lock.wait();
                        } catch (InterruptedException x) {
                        }
                        continue;
                    }
                }

                // Fast path for cleaners
                if (r instanceof Cleaner) {
                    ((Cleaner) r).clean();
                    continue;
                }

                //将该Reference压入ReferenceQueue，注意pending list是全局唯一的，而ReferenceQueue则是各个Reference对象私有的。
                ReferenceQueue q = r.queue;
                //如果创建Reference对象时未初始化ReferenceQueue字段(也就是所谓的未注册ReferenceQueue)，则无需入队，即默认无需GC通知机制。
                if (q != ReferenceQueue.NULL)
                    q.enqueue(r);
            }
        }
    }

    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg; tgn != null; tg = tgn, tgn = tg.getParent())
            ;

        //优先级最高的守护线程，且运行期间做无限循环操作，也就意味着这是一个JVM线程生命周期基本一致的线程。
        // 因为自Reference类被加载起，ReferenceHandler线程即被创建，pending list也被创建，这两者是全局的，参与所有该JVM创建的Reference的回收工作。
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /*
         * If there were a special system-only priority greater than MAX_PRIORITY, it would be used here
         * handler线程设置为守护线程，且具有最高优先级.
         */
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();
    }


    /* -- Referent accessor and setters -- */

    /**
     * Returns this reference object's referent.
     * If this reference object has been cleared, either by the program or by the garbage collector, then this method returns null.
     *
     * @return The object to which this reference refers, or null if this reference object has been cleared
     */
    public T get() {
        return this.referent;
    }

    /**
     * Clears this reference object. Invoking this method will not cause this object to be enqueued.
     *
     * This method is invoked only by Java code;
     * when the garbage collector clears references it does so directly, without invoking this method.
     */
    public void clear() {
        this.referent = null;
    }


    /* -- Queue operations -- */

    /**
     * Tells whether or not this reference object has been enqueued, either by the program or by the garbage collector.
     * If this reference object was not registered with a queue when it was created, then this method will always return false.
     *
     * @return true if and only if this reference object has been enqueued
     */
    public boolean isEnqueued() {
        /* In terms of the internal states, this predicate actually tests whether the instance is either Pending or Enqueued */
        synchronized (this) {
            return (this.queue != ReferenceQueue.NULL) && (this.next != null);
        }
    }

    /**
     * Adds this reference object to the queue with which it is registered, if any.
     *
     * This method is invoked only by Java code; when the garbage collector enqueues references it does so directly, without invoking this method.
     *
     * @return true if this reference object was successfully enqueued;
     *         false if it was already enqueued or if it was not registered with a queue when it was created
     */
    public boolean enqueue() {
        return this.queue.enqueue(this);
    }


    /* -- Constructors -- */

    Reference(T referent) {
        this(referent, null);
    }

    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }

}
