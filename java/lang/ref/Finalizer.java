/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang.ref;

import java.security.PrivilegedAction;
import java.security.AccessController;

/**
 * Package-private; must be in same package as the Reference class
 * JVM在加载一个类是，会遍历当前类的所有方法包括父类的方法。只要有一个参数为空且返回void的非空finalize方法，就认为该类是Finalizer类
 */
final class Finalizer extends FinalReference {

    /*
     * A native method that invokes an arbitrary object's finalize method is required since the finalize method is protected
     */
    static native void invokeFinalizeMethod(Object o) throws Throwable;

    /**================================================都是全局的======================================================**/
    private static ReferenceQueue queue = new ReferenceQueue(); //构造器决定了无法传入别的ReferenceQueue对象
    private static Finalizer unfinalized = null; // 全局链表
    private static final Object lock = new Object();
    /**==============================================================================================================**/

    private Finalizer next = null, prev = null; //单个Finalizer对象在unfinalized list中的前后指向

    /**
     * 判断对象是否还在unfinalized list中。
     * Finalizer类的对象在创建时即已加入到unfinalized list中，执行完finalize方法后从list中移除
     * @return
     */
    private boolean hasBeenFinalized() {
        //从 unfinalized list中移除后，会将next和prev都指向自己。
        return (next == this);
    }

    /**
     * 将当前引用加入到全局的unfinalized list里，等待FinalizeThread执行finalize()
     */
    private void add() {
        synchronized (lock) {
            if (unfinalized != null) {
                this.next = unfinalized;
                unfinalized.prev = this;
            }

            //队首插入节点
            unfinalized = this;
        }
    }

    private void remove() {
        synchronized (lock) {
            if (unfinalized == this) { //本节点就在unfinalized list的队首
                if (this.next != null) { // unfinalized list还有后序节点
                    unfinalized = this.next;
                } else {
                    unfinalized = this.prev;
                }
            }

            if (this.next != null) {
                this.next.prev = this.prev;
            }

            if (this.prev != null) {
                this.prev.next = this.next;
            }

            // 从队列移除后，将next和prev都指向自己，标识方法已执行
            this.next = this;
            this.prev = this;

        }
    }

    private Finalizer(Object finalizee) {
        super(finalizee, queue);
        add();
    }

    /**============================================== Invoked by VM =================================================**/
    /**
     * 在JVM创建对象时，会检查该对象所属的类是否重写了Object.finalize()。
     * 若未重写，则直接创建对象；
     * 若已重写，则再创建Finalizer对象(又包了个壳)，该操作是在原对象初始化前后取决于JVM参数：RegisterFinalizerAsInit(默认值为：true)。
        * 若RegisterFinalizerAsInit设为false，则在初始化之前创建Finalizer对象；
        * 若RegisterFinalizerAsInit设为true，则在初始化之后创建Finalizer对象。
     */
    static void register(Object finalizee) {
        new Finalizer(finalizee);
    }
    /** =============================================================================================================**/

    private void runFinalizer() {
        synchronized (this) {
            if (hasBeenFinalized()) return;
            remove();
        }
        try {
            Object finalizee = this.get();
            if (finalizee != null && !(finalizee instanceof java.lang.Enum)) {
                invokeFinalizeMethod(finalizee);
                /* Clear stack slot containing this variable, to decrease
                   the chances of false retention with a conservative GC */
                finalizee = null;
            }
        } catch (Throwable x) { }
        super.clear();
    }

    /**
     * Create a privileged secondary finalizer thread in the system thread group for the given Runnable, and wait for it to complete.

     * This method is used by both runFinalization and runFinalizersOnExit.
     * The former method invokes all pending finalizers, while the latter invokes all uninvoked finalizers if on-exit finalization has been enabled.

     * These two methods could have been implemented by offloading their work to the regular finalizer thread and waiting for that thread to finish.
     * The advantage of creating a fresh thread, however, is that it insulates invokers of these methods from a stalled or deadlocked finalizer thread.
     */
    private static void forkSecondaryFinalizer(final Runnable proc) {
        AccessController.doPrivileged(
            new PrivilegedAction<Void>() {
                public Void run() {
                ThreadGroup tg = Thread.currentThread().getThreadGroup();
                for (ThreadGroup tgn = tg;
                     tgn != null;
                     tg = tgn, tgn = tg.getParent());
                Thread sft = new Thread(tg, proc, "Secondary finalizer");
                sft.start();
                try {
                    sft.join();
                } catch (InterruptedException x) {
                    /* Ignore */
                }
                return null;
                }});
    }

    /* Called by Runtime.runFinalization() */
    static void runFinalization() {
        forkSecondaryFinalizer(new Runnable() {
            private volatile boolean running;
            public void run() {
                if (running)
                    return;
                running = true;
                for (;;) {
                    Finalizer f = (Finalizer)queue.poll();
                    if (f == null) break;
                    f.runFinalizer();
                }
            }
        });
    }

    /* Invoked by java.lang.Shutdown */
    static void runAllFinalizers() {
        forkSecondaryFinalizer(new Runnable() {
            private volatile boolean running;
            public void run() {
                if (running)
                    return;
                running = true;
                for (;;) {
                    Finalizer f;
                    synchronized (lock) {
                        f = unfinalized;
                        if (f == null) break;
                        unfinalized = f.next;
                    }
                    f.runFinalizer();
                }}});
    }

    private static class FinalizerThread extends Thread {
        private volatile boolean running;
        FinalizerThread(ThreadGroup g) {
            super(g, "Finalizer");
        }
        public void run() {
            if (running)
                return;
            running = true;
            for (;;) {
                try {
                    //从ReferenceQueue中移除，然后执行该类重写的finalize方法。
                    Finalizer f = (Finalizer)queue.remove();
                    f.runFinalizer();
                } catch (InterruptedException x) {
                    continue;
                }
            }
        }
    }

    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg; tgn != null; tg = tgn, tgn = tg.getParent());
        Thread finalizer = new FinalizerThread(tg);

        // 同样是一个守护线程，但是优先级不是最高的。这意味着在CPU资源吃紧时，可能会无法抢占到资源执行。
        // 这也是为什么说不要依赖重写finalize方法完成一些关键操作，因为FinalizerThread可能无法及时执行finalize方法。
        finalizer.setPriority(Thread.MAX_PRIORITY - 2);
        finalizer.setDaemon(true);
        finalizer.start();
    }

}
