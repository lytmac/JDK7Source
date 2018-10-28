/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.util;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;


/**
 * Hash table based implementation of the Map interface, with weak keys.
 * An entry in a WeakHashMap will automatically be removed when its key is no longer in ordinary use. 
 *
 * 当WeakHashMap所持有的Entry在外部没有其他引用的情况下，WeakHashMap不会阻止GC回收该Entry。也就是说Entry只剩WeakHashMap引用时，是会被GC回收的。
 * More precisely, the presence of a mapping for a given key will not prevent the key from being discarded by the garbage collector, 
 * that is, made finalizable, finalized, and then reclaimed.
 *
 * 当一个Entry的key被回收时，该Entry将会被从WeakHashMap中移除。这就是WeakHashMap与其他Map实现体的不同的行为
 * When a key has been discarded its entry is effectively removed from the map, so this class behaves somewhat differently from other Map implementations.
 *
 * Both null values and the null key are supported. This class has performance characteristics similar to those of the HashMap
 * class, and has the same efficiency parameters of initial capacity and load factor.
 *
 * Like most collection classes, this class is not synchronized. A synchronized WeakHashMap may be constructed using the Collections.synchronizedMap method.
 *
 *
 * This class is intended primarily for use with key objects whose equals methods test for object identity using the == operator.
 *
 * 一旦一个Entry的key被回收，就不会再重建了。
 * Once such a key is discarded it can never be recreated, so it is impossible to do a lookup of that key in a WeakHashMap
 * at some later time and be surprised that its entry has been removed.
 *
 * This class will work perfectly well with key objects whose equals methods are not based upon object identity, such
 * as String instances. With such recreatable key objects, however, the automatic removal of WeakHashMap entries whose
 * keys have been discarded may prove to be confusing.
 *
 * The behavior of the WeakHashMap class depends in part upon the actions of the garbage collector, so several familiar
 * (though not required) Map invariants do not hold for this class.
 * 因为GC可能在任何时刻回收key，所以WeakHashMap会表现得好像一个未知的线程默默地删除了Entry.
 * Because the garbage collector may discard keys at any time, a WeakHashMap may behave as though an unknown thread is silently removing entries.
 * In particular, even if you synchronize on a WeakHashMap instance and invoke none of its mutator methods, it is possible for the size method to
 * return smaller values over time, for the isEmpty method to return false and then true, for the containsKey method to return true and later false
 * for a given key, for the get method to return a value for a given key but later return null, for the put method to return null and the remove
 * method to return false for a key that previously appeared to be in the map, and for successive examinations of the key set, the value collection,
 * and the entry set to yield successively smaller numbers of elements.
 *
 * WeakHashMap中的每个key都间接存储为弱引用的引用对象。因此只有在GC回收了key所引用的对象之后才会自动回收key
 * Each key object in a WeakHashMap is stored indirectly as the referent of a weak reference. Therefore a key will automatically be
 * removed only after the weak references to it, both inside and outside of the map, have been cleared by the garbage collector.
 *
 * Implementation note: The value objects in a WeakHashMap are held by ordinary strong references.
 * Thus care should be taken to ensure that value objects do not strongly refer to their own keys, either directly or indirectly,
 * since that will prevent the keys from being discarded. Note that a value object may refer indirectly to its key via the WeakHashMap itself;
 * that is, a value object may strongly refer to some other key object whose associated value object, in turn, strongly refers to the key of the first value
 * object. If the values in the map do not rely on the map holding strong references to them, one way to deal with this is to wrap values themselves within
 * WeakReferences before inserting, as in: m.put(key, new WeakReference(value)), and then unwrapping upon each get.
 *
 * The iterators returned by the iterator method of the collections returned by all of this class's "collection view methods" are fail-fast:
 * if the map is structurally modified at any time after the iterator is created, in any way except through the iterator's own remove method,
 * the iterator will throw a ConcurrentModificationException.
 * Thus, in the face of concurrent modification, the iterator fails quickly and cleanly, rather than risking arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * Note that the fail-fast behavior of an iterator cannot be guaranteed as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification. Fail-fast iterators throw ConcurrentModificationException on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this exception for its correctness:  the fail-fast behavior of iterators should be used only to detect bugs.
 *
 * This class is a member of the <a href="{@docRoot}/../technotes/guides/collections/index.html"> Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author      Doug Lea
 * @author      Josh Bloch
 * @author      Mark Reinhold
 * @since       1.2
 * @see         java.util.HashMap
 * @see         java.lang.ref.WeakReference
 */
public class WeakHashMap<K,V> extends AbstractMap<K,V> implements Map<K,V> {

    /**
     * The default initial capacity -- MUST be a power of two.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    Entry<K,V>[] table;

    /**
     * The number of key-value mappings contained in this weak hash map.
     */
    private int size;

    /**
     * The next size value at which to resize (capacity * load factor).
     */
    private int threshold;

    /**
     * The load factor for the hash table.
     */
    private final float loadFactor;

    /**
     * 整个WeakHashMap维护了一个全局的ReferenceQueue，接受GC的回收通知。
     * Reference queue for cleared WeakEntries
     */
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    /**
     * The number of times this WeakHashMap has been structurally modified.
     * Structural modifications are those that change the number of mappings in the map or otherwise modify its internal structure
     * (e.g., rehash).  This field is used to make iterators on Collection-views of the map fail-fast.
     *
     * @see ConcurrentModificationException
     */
    int modCount;

    /**
     * The default threshold of map capacity above which alternative hashing is used for String keys. Alternative hashing reduces the
     * incidence of collisions due to weak hash code calculation for String keys.
     *
     * This value may be overridden by defining the system property jdk.map.althashing.threshold. A property value of 1
     * forces alternative hashing to be used at all times whereas -1 value ensures that alternative hashing is never used.
     */
    static final int ALTERNATIVE_HASHING_THRESHOLD_DEFAULT = Integer.MAX_VALUE;

    /**
     * holds values which can't be initialized until after VM is booted.
     */
    private static class Holder {

        /**
         * Table capacity above which to switch to use alternative hashing.
         */
        static final int ALTERNATIVE_HASHING_THRESHOLD;

        static {
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                    "jdk.map.althashing.threshold"));

            int threshold;
            try {
                threshold = (null != altThreshold)
                        ? Integer.parseInt(altThreshold)
                        : ALTERNATIVE_HASHING_THRESHOLD_DEFAULT;

                // disable alternative hashing if -1
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }

                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            ALTERNATIVE_HASHING_THRESHOLD = threshold;
        }
    }

    /**
     * If true then perform alternate hashing to reduce the incidence of collisions due to weak hash code calculation.
     */
    transient boolean useAltHashing;

    /**
     * A randomizing value associated with this instance that is applied to hash code of keys to make hash collisions harder to find.
     */
    transient final int hashSeed = sun.misc.Hashing.randomHashSeed(this);

    @SuppressWarnings("unchecked")
    private Entry<K,V>[] newTable(int n) {
        return (Entry<K,V>[]) new Entry[n];
    }

    /**
     * Constructs a new, empty WeakHashMap with the given initial capacity and the given load factor.
     *
     * @param  initialCapacity The initial capacity of the WeakHashMap
     * @param  loadFactor      The load factor of the WeakHashMap
     * @throws IllegalArgumentException if the initial capacity is negative, or if the load factor is nonpositive.
     */
    public WeakHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Initial Capacity: "+ initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load factor: "+ loadFactor);
        int capacity = 1;
        while (capacity < initialCapacity)
            capacity <<= 1;
        table = newTable(capacity);
        this.loadFactor = loadFactor;
        threshold = (int)(capacity * loadFactor);
        useAltHashing = sun.misc.VM.isBooted() && (capacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);
    }

    /**
     * Constructs a new, empty WeakHashMap with the given initial capacity and the default load factor (0.75).
     *
     * @param  initialCapacity The initial capacity of the WeakHashMap
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public WeakHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty WeakHashMap with the default initial capacity (16) and load factor (0.75).
     */
    public WeakHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new WeakHashMap with the same mappings as the specified map.
     * The WeakHashMap is created with the default load factor (0.75) and an initial capacity sufficient to hold the mappings in the specified map.
     *
     * @param   m the map whose mappings are to be placed in this map
     * @throws  NullPointerException if the specified map is null
     * @since   1.3
     */
    public WeakHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        putAll(m);
    }

    // internal utilities

    /**
     * Value representing null keys inside tables.
     */
    private static final Object NULL_KEY = new Object();

    /**
     * Use NULL_KEY for key if it is null.
     */
    private static Object maskNull(Object key) {
        return (key == null) ? NULL_KEY : key;
    }

    /**
     * Returns internal representation of null key back to caller as null.
     */
    static Object unmaskNull(Object key) {
        return (key == NULL_KEY) ? null : key;
    }

    /**
     * Checks for equality of non-null reference x and possibly-null y.  By
     * default uses Object.equals.
     */
    private static boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /**
     * Retrieve object hash code and applies a supplemental hash function to the result hash, which defends against poor quality hash functions.
     * This is critical because HashMap uses power-of-two length hash tables, that otherwise encounter collisions for hashCodes that do not differ in lower bits.
     */
    int hash(Object k) {

        int h;
        if (useAltHashing) {
            h = hashSeed;
            if (k instanceof String) {
                return sun.misc.Hashing.stringHash32((String) k);
            } else {
                h ^= k.hashCode();
            }
        } else  {
            h = k.hashCode();
        }

        // This function ensures that hashCodes that differ only by constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Returns index for hash code h.
     */
    private static int indexFor(int h, int length) {
        return h & (length-1);
    }

    /**
     * Expunges stale entries from the table.
     * GC回收的仅仅是Entry中由WeakReference指向的对象，而不是Entry本身，虽然GC线程向ReferenceQueue中存放的引用是Entry.
     * 自动清理Entry的工作还是要由WeakHashMap本身完成
     */
    private void expungeStaleEntries() {
        for (Object x; (x = queue.poll()) != null; ) { //从ReferenceQueue中移除元素，该元素即是已被GC回收的对象的引用。
            synchronized (queue) {
                @SuppressWarnings("unchecked")
                Entry<K,V> e = (Entry<K,V>) x;
                int i = indexFor(e.hash, table.length); //定位到已被回收Entry所在的桶位，这里需要借助Entry.hash来实现快速定位

                Entry<K,V> prev = table[i];
                Entry<K,V> p = prev;
                while (p != null) { //遍历i桶位链表
                    Entry<K,V> next = p.next;
                    if (p == e) { //找到了已经被GC回收的Entry
                        //这里判断的区别在于是否已经遍历到了链表的尾节点，这里要负责被回收节点的移除和新链表的重建
                        if (prev == e)
                            table[i] = next;
                        else
                            prev.next = next;
                        // Must not null out e.next; stale entries may be in use by a HashIterator
                        e.value = null; // Help GC
                        size--;
                        break;
                    }
                    prev = p;
                    p = next;
                }
            }
        }
    }

    /**
     * Returns the table after first expunging stale entries.
     */
    private Entry<K,V>[] getTable() {
        expungeStaleEntries();
        return table;
    }

    /**
     * Returns the number of key-value mappings in this map.
     * This result is a snapshot, and may not reflect unprocessed entries that will be removed before next attempted access
     * because they are no longer referenced.
     */
    public int size() {
        if (size == 0)
            return 0;
        expungeStaleEntries();
        return size;
    }

    /**
     * Returns true if this map contains no key-value mappings.
     * This result is a snapshot, and may not reflect unprocessed entries that will be removed before next attempted access
     * because they are no longer referenced.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
     *
     * More formally, if this map contains a mapping from a key k to a value v such that {(key==null ? k==null : key.equals(k))},
     * then this method returns v; otherwise it returns null.  (There can be at most one such mapping.)
     *
     * A return value of null does not necessarily indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to null.
     * The containsKey operation may be used to distinguish these two cases.
     *
     * @see #put(Object, Object)
     */
    public V get(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int index = indexFor(h, tab.length);
        Entry<K,V> e = tab[index];
        while (e != null) {
            if (e.hash == h && eq(k, e.get()))
                return e.value;
            e = e.next;
        }
        return null;
    }

    /**
     * Returns true if this map contains a mapping for the specified key.
     *
     * @param  key  The key whose presence in this map is to be tested
     * @return true if there is a mapping for key; false otherwise
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * Returns the entry associated with the specified key in this map.
     * Returns null if the map contains no mapping for this key.
     */
    Entry<K,V> getEntry(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int index = indexFor(h, tab.length);
        Entry<K,V> e = tab[index];
        while (e != null && !(e.hash == h && eq(k, e.get())))
            e = e.next;
        return e;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return the previous value associated with key, or null if there was no mapping for key.
     *         (A null return can also indicate that the map previously associated null with key.)
     */
    public V put(K key, V value) {
        Object k = maskNull(key); //校验key是不是NULL, 如果是NULL则需要转为new Object()
        int h = hash(k);
        Entry<K,V>[] tab = getTable(); //这一步要移除已过期的Entry
        int i = indexFor(h, tab.length);

        for (Entry<K,V> e = tab[i]; e != null; e = e.next) {
            if (h == e.hash && eq(k, e.get())) {
                V oldValue = e.value; //相同key覆盖已存在的值
                if (value != oldValue)
                    e.value = value;
                return oldValue;
            }
        }

        modCount++;
        Entry<K,V> e = tab[i];
        tab[i] = new Entry<>(k, value, queue, h, e);
        if (++size >= threshold)
            resize(tab.length * 2);
        return null;
    }

    /**
     * Rehashes the contents of this map into a new array with a larger capacity.
     * This method is called automatically when the number of keys in this map reaches its threshold.
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * @param newCapacity the new capacity, MUST be a power of two; must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value is irrelevant).
     */
    void resize(int newCapacity) {
        Entry<K,V>[] oldTable = getTable();
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry<K,V>[] newTable = newTable(newCapacity);
        boolean oldAltHashing = useAltHashing;
        useAltHashing |= sun.misc.VM.isBooted() && (newCapacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);
        boolean rehash = oldAltHashing ^ useAltHashing;
        transfer(oldTable, newTable, rehash);
        table = newTable;

        /*
         * If ignoring null elements and processing ref queue caused massive shrinkage, then restore old table.
         * This should be rare, but avoids unbounded expansion of garbage-filled tables.
         */
        if (size >= threshold / 2) {
            threshold = (int)(newCapacity * loadFactor);
        } else {
            expungeStaleEntries();
            transfer(newTable, oldTable, false);
            table = oldTable;
        }
    }

    /** Transfers all entries from src to dest tables */
    private void transfer(Entry<K,V>[] src, Entry<K,V>[] dest, boolean rehash) {
        for (int j = 0; j < src.length; ++j) {
            Entry<K,V> e = src[j];
            src[j] = null;
            while (e != null) {
                Entry<K,V> next = e.next;
                Object key = e.get();
                if (key == null) {
                    e.next = null;  // Help GC
                    e.value = null; //  "   "
                    size--;
                } else {
                    if (rehash) {
                        e.hash = hash(key);
                    }
                    int i = indexFor(e.hash, dest.length);
                    e.next = dest[i];
                    dest[i] = e;
                }
                e = next;
            }
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map.
     * @throws  NullPointerException if the specified map is null.
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself
         * to at most one extra resize.
         */
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * Removes the mapping for a key from this weak hash map if it is present.
     * More formally, if this map contains a mapping from key k to value v such that (key==null ?  k==null : key.equals(k)),
     * that mapping is removed. (The map can contain at most one such mapping.)
     *
     * Returns the value to which this map previously associated the key, or null if the map contained no mapping for the key.
     * A return value of null does not necessarily indicate that the map contained no mapping for the key; it's also possible
     * that the map explicitly mapped the key to null.
     *
     * The map will not contain a mapping for the specified key once the call returns.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was no mapping for key
     */
    public V remove(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && eq(k, e.get())) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return e.value;
            }
            prev = e;
            e = next;
        }

        return null;
    }

    /** Special version of remove needed by Entry set */
    boolean removeMapping(Object o) {
        if (!(o instanceof Map.Entry))
            return false;
        Entry<K,V>[] tab = getTable();
        Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
        Object k = maskNull(entry.getKey());
        int h = hash(k);
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && e.equals(entry)) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return true;
            }
            prev = e;
            e = next;
        }

        return false;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        // clear out ref queue. We don't need to expunge entries
        // since table is getting cleared.
        while (queue.poll() != null)
            ;

        modCount++;
        Arrays.fill(table, null);
        size = 0;

        // Allocation of array may have caused GC, which may have caused
        // additional entries to go stale.  Removing these entries from the
        // reference queue will make them eligible for reclamation.
        while (queue.poll() != null)
            ;
    }

    /**
     * Returns true if this map maps one or more keys to the specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return true if this map maps one or more keys to the specified value
     */
    public boolean containsValue(Object value) {
        if (value==null)
            return containsNullValue();

        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    /**
     * Special-case code for containsValue with null argument
     */
    private boolean containsNullValue() {
        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (e.value==null)
                    return true;
        return false;
    }

    /**
     * The entries in this hash table extend WeakReference, using its main ref field as the key.
     *
     * Entry<K, V> extends WeakReference<Object> 这样的继承关系而非组合关系的设计具有极为重要的意义。
     * 首先，WeakHashMap需要从ReferenceQueue中查看究竟是哪些key已经被GC回收了，而ReferenceQueue中只存放了WeakReference.
     * 其次，在调用expungeStaleEntries()去移除已被回收的Entry时，需要通过该Entry的hash值快速定位到其所在的桶位，如果只将key放入ReferenceQueue就无法获取hash.
     * 所以Entry必须要继承WeakReference,GC回收后放入ReferenceQueue的必须得是Entry，而不能仅仅是WeakReference。
     */
    private static class Entry<K,V> extends WeakReference<Object> implements Map.Entry<K,V> {
        V value;
        int hash;
        Entry<K,V> next;

        /**
         * Creates new entry.
         */
        Entry(Object key, V value, ReferenceQueue<Object> queue, int hash, Entry<K,V> next) {
            super(key, queue);
            this.value = value;
            this.hash  = hash;
            this.next  = next;
        }

        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) WeakHashMap.unmaskNull(get());
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            K k1 = getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                V v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        public int hashCode() {
            K k = getKey();
            V v = getValue();
            return ((k==null ? 0 : k.hashCode()) ^ (v==null ? 0 : v.hashCode()));
        }

        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    private abstract class HashIterator<T> implements Iterator<T> {
        private int index;
        private Entry<K,V> entry = null;
        private Entry<K,V> lastReturned = null;
        private int expectedModCount = modCount;

        /**
         * Strong reference needed to avoid disappearance of key between hasNext and next
         */
        private Object nextKey = null;

        /**
         * Strong reference needed to avoid disappearance of key between nextEntry() and any use of the entry
         */
        private Object currentKey = null;

        HashIterator() {
            index = isEmpty() ? 0 : table.length;
        }

        public boolean hasNext() {
            Entry<K,V>[] t = table;

            while (nextKey == null) {
                Entry<K,V> e = entry;
                int i = index;
                while (e == null && i > 0)
                    e = t[--i];
                entry = e;
                index = i;
                if (e == null) {
                    currentKey = null;
                    return false;
                }
                nextKey = e.get(); // hold on to key in strong ref
                if (nextKey == null)
                    entry = entry.next;
            }
            return true;
        }

        /** The common parts of next() across different types of iterators */
        protected Entry<K,V> nextEntry() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (nextKey == null && !hasNext())
                throw new NoSuchElementException();

            lastReturned = entry;
            entry = entry.next;
            currentKey = nextKey;
            nextKey = null;
            return lastReturned;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            WeakHashMap.this.remove(currentKey);
            expectedModCount = modCount;
            lastReturned = null;
            currentKey = null;
        }

    }

    private class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    private class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    private class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    // Views

    private transient Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Returns a Set view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
     * If the map is modified while an iteration over the set is in progress (except through the iterator's own remove operation),
     * the results of the iteration are undefined.
     * The set supports element removal, which removes the corresponding mapping from the map, via the Iterator.remove, Set.remove,
     * removeAll, retainAll, and clear operations.
     * It does not support the add or addAll operations.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            if (containsKey(o)) {
                WeakHashMap.this.remove(o);
                return true;
            }
            else
                return false;
        }

        public void clear() {
            WeakHashMap.this.clear();
        }
    }

    /**
     * Returns a Collection view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are reflected in the collection, and vice-versa.
     * If the map is modified while an iteration over the collection is in progress (except through the iterator's own remove operation),
     * the results of the iteration are undefined.
     * The collection supports element removal, which removes the corresponding mapping from the map, via the Iterator.remove,
     * Collection.remove, removeAll, retainAll and clear operations.
     * It does not support the add or addAll operations.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    private class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsValue(o);
        }

        public void clear() {
            WeakHashMap.this.clear();
        }
    }

    /**
     * Returns a Set view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
     * If the map is modified while an iteration over the set is in progress (except through the iterator's own remove operation, or through the
     * setValue operation on a map entry returned by the iterator) the results of the iteration are undefined.
     * The set supports element removal, which removes the corresponding mapping from the map, via the Iterator.remove, Set.remove, removeAll, retainAll and clear operations.
     * It does not support the add or addAll operations.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }

        public boolean remove(Object o) {
            return removeMapping(o);
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public void clear() {
            WeakHashMap.this.clear();
        }

        private List<Map.Entry<K,V>> deepCopy() {
            List<Map.Entry<K,V>> list = new ArrayList<>(size());
            for (Map.Entry<K,V> e : this)
                list.add(new AbstractMap.SimpleEntry<>(e));
            return list;
        }

        public Object[] toArray() {
            return deepCopy().toArray();
        }

        public <T> T[] toArray(T[] a) {
            return deepCopy().toArray(a);
        }
    }
}
