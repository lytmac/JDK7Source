/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;

/**
 * A ReadWriteLock maintains a pair of associated locks, one for read-only operations and one for writing.
 * 
 * The read lock may be held simultaneously by multiple reader threads, so long as there are no writers. 
 *
 * 写锁是独占的，这种内存同步效果也会涉及到读锁。也就是说成功获取读锁后会看到前一个释放写锁的操作的所有效果。
 * The write lock is exclusive. All ReadWriteLock implementations must guarantee that the memory synchronization effects of writeLock operations
 * as specified in the Lock interface also hold with respect to the associated readLock. That is, a thread successfully acquiring the read lock 
 * will see all updates made upon previous release of the write lock.
 *
 * A read-write lock allows for a greater level of concurrency in accessing shared data than that permitted by a mutual exclusion lock.
 * It exploits the fact that while only a single thread at a time a writer thread can modify the shared data, in many cases any number of threads
 * can concurrently read the data (hence reader threads).
 * In theory, the increase in concurrency permitted by the use of a read-write lock will lead to performance improvements over the use of a mutual
 * exclusion lock. In practice this increase in concurrency will only be fully realized on a multi-processor, and then only if the access patterns
 * for the shared data are suitable.
 *
 * 读写锁是否能提升比独占锁更高的性能表现，取决于数据读写频率、读写操作持续时长和读写操作同时竞争同一数据的线程数量的比较。
 * Whether or not a read-write lock will improve performance over the use of a mutual exclusion lock depends on the frequency that the data is
 * read compared to being modified, the duration of the read and write operations, and the contention for the data - that is, the number of threads
 * that will try to read or write the data at the same time.
 *
 * For example, a collection that is initially populated with data and thereafter infrequently modified, while being frequently searched (such as a
 * directory of some kind) is an ideal candidate for the use of a read-write lock.
 *
 * 如果写操作变得频繁，那么独占锁将占据更多的时间，并发性也将更低。而且如果读操作本身很短，那么读写锁操作本身带来的开销(相较于独占锁更复杂)就无法被忽视。
 * However, if updates become frequent then the data spends most of its time being exclusively locked and there is little, if any increase in
 * concurrency. Further, if the read operations are too short the overhead of the read-write lock implementation (which is inherently more complex
 * than a mutual exclusion lock) can dominate the execution cost, particularly as many read-write lock implementations still serialize all threads
 * through a small section of code. Ultimately, only profiling and measurement will establish whether the use of a read-write lock is suitable for
 * your application.
 *
 * Although the basic operation of a read-write lock is straight-forward, there are many policy decisions that an implementation must make, which
 * may affect the effectiveness of the read-write lock in a given application. Examples of these policies include:
 *
 * 如果在写操作释放锁后，同时有读操作和写操作在等待。那么究竟应该是读操作优先还是写操作优先？通常是写优先，因为写操作通常更短，也不那么频繁。当然按照先后顺序也是一种选择。
 * 1. Determining whether to grant the read lock or the write lock, when both readers and writers are waiting, at the time that a writer releases the
 * write lock. Writer preference is common, as writes are expected to be short and infrequent. Reader preference is less common as it can lead to
 * lengthy delays for a write if the readers are frequent and long-lived as expected. Fair, or in-order implementations are also possible.
 *
 * 当另一个读操作已经拿到读锁了并且有个写操作在等待。那么这个时候再有读操作请求，是否还需要加读锁？加读锁会持续延迟写操作，不加则可能有降低并发度的可能性。
 * 2. Determining whether readers that request the read lock while a reader is active and a writer is waiting, are granted the read lock. Preference
 * to the reader can delay the writer indefinitely, while preference to the writer can reduce the potential for concurrency.
 *
 * 读写锁是否是可重入的？一个持有写锁的线程是否可以直接获取读锁和写锁？
 * 3. Determining whether the locks are reentrant: can a thread with the write lock reacquire it? Can it acquire a read lock while holding the write
 * lock? Is the read lock itself reentrant?
 *
 * 写锁是否可以降级为读锁而仅仅是不允许其他线程执行写操作？读锁是否可以升级到写锁，优先于其他等待的读线程或写线程？
 * 4. Can the write lock be downgraded to a read lock without allowing an intervening writer? Can a read lock be upgraded to a write lock, in preference
 * to other waiting readers or writers?
 *
 * You should consider all of these things when evaluating the suitability of a given implementation for your application.
 *
 * @see ReentrantReadWriteLock
 * @see Lock
 * @see ReentrantLock
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ReadWriteLock {
    /**
     * Returns the lock used for reading.
     *
     * @return the lock used for reading.
     */
    Lock readLock();

    /**
     * Returns the lock used for writing.
     *
     * @return the lock used for writing.
     */
    Lock writeLock();
}
