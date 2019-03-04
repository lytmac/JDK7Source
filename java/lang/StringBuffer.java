/*
 * Copyright (c) 1994, 2008, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang;


/**
 * StringBuffer是线程安全的可变字符序列
 * A thread-safe, mutable sequence of characters.
 * A string buffer is like a String, but can be modified. At any point in time it contains some particular sequence of characters, but
 * the length and content of the sequence can be changed through certain method calls.
 *
 * StringBuffer的部分方法在必要的位置都是加了同步的(方法是synchronized修饰的)
 * String buffers are safe for use by multiple threads. The methods are synchronized where necessary so that all the operations on any
 * particular instance behave as if they occur in some serial order that is consistent with the order of the method calls made by each
 * of the individual threads involved.
 * 
 * The principal operations on a StringBuffer are the append and insert methods, which are overloaded so as to accept data of any type.
 * Each effectively converts a given datum to a string and then appends or inserts the characters of that string to the string buffer.
 * The append method always adds these characters at the end of the buffer; the insert method adds the characters at a specified point.
 * 
 * For example, if z refers to a string buffer object whose current contents are "start", then the method call z.append("le") would cause
 * the string buffer to contain "startle", whereas z.insert(4, "le") would alter the string buffer to contain "starlet".
 * 
 * In general, if sb refers to an instance of a StringBuffer, then sb.append(x) has the same effect as sb.insert(sb.length(), x).
 * 
 * Whenever an operation occurs involving a source sequence (such as appending or inserting from a source sequence) this class synchronizes
 * only on the string buffer performing the operation, not on the source.
 * 
 * Every string buffer has a capacity. As long as the length of the character sequence contained in the string buffer does not exceed the
 * capacity, it is not necessary to allocate a new internal buffer array. If the internal buffer overflows, it is automatically made larger.
 *
 * As of release JDK 5, this class has been supplemented with an equivalent class designed for use by a single thread, StringBuilder. The
 * StringBuilder class should generally be used in preference to this one, as it supports all of the same operations but it is faster, as
 * it performs no synchronization.
 *
 * @author      Arthur van Hoff
 * @see     java.lang.StringBuilder
 * @see     java.lang.String
 * @since   JDK1.0
 */
 public final class StringBuffer
    extends AbstractStringBuilder
    implements java.io.Serializable, CharSequence
{

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    static final long serialVersionUID = 3388685877147921107L;

    /**
     * Constructs a string buffer with no characters in it and an
     * initial capacity of 16 characters.
     */
    public StringBuffer() {
        super(16);
    }

    /**
     * Constructs a string buffer with no characters in it and
     * the specified initial capacity.
     *
     * @param      capacity  the initial capacity.
     * @exception  NegativeArraySizeException  if the capacity
     *               argument is less than 0.
     */
    public StringBuffer(int capacity) {
        super(capacity);
    }

    /**
     * Constructs a string buffer initialized to the contents of the
     * specified string. The initial capacity of the string buffer is
     * 16 plus the length of the string argument.
     *
     * @param   str   the initial contents of the buffer.
     * @exception NullPointerException if str is null
     */
    public StringBuffer(String str) {
        super(str.length() + 16);
        append(str);
    }

    /**
     * Constructs a string buffer that contains the same characters
     * as the specified CharSequence. The initial capacity of
     * the string buffer is 16 plus the length of the
     * CharSequence argument.
     * 
     * If the length of the specified CharSequence is
     * less than or equal to zero, then an empty buffer of capacity
     * 16 is returned.
     *
     * @param      seq   the sequence to copy.
     * @exception NullPointerException if seq is null
     * @since 1.5
     */
    public StringBuffer(CharSequence seq) {
        this(seq.length() + 16);
        append(seq);
    }

    public synchronized int length() {
        return count;
    }

    public synchronized int capacity() {
        return value.length;
    }


    public synchronized void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity > value.length) {
            expandCapacity(minimumCapacity);
        }
    }

    /**
     * @since      1.5
     */
    public synchronized void trimToSize() {
        super.trimToSize();
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see        #length()
     */
    public synchronized void setLength(int newLength) {
        super.setLength(newLength);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see        #length()
     */
    public synchronized char charAt(int index) {
        if ((index < 0) || (index >= count))
            throw new StringIndexOutOfBoundsException(index);
        return value[index];
    }

    /**
     * @since      1.5
     */
    public synchronized int codePointAt(int index) {
        return super.codePointAt(index);
    }

    /**
     * @since     1.5
     */
    public synchronized int codePointBefore(int index) {
        return super.codePointBefore(index);
    }

    /**
     * @since     1.5
     */
    public synchronized int codePointCount(int beginIndex, int endIndex) {
        return super.codePointCount(beginIndex, endIndex);
    }

    /**
     * @since     1.5
     */
    public synchronized int offsetByCodePoints(int index, int codePointOffset) {
        return super.offsetByCodePoints(index, codePointOffset);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized void getChars(int srcBegin, int srcEnd, char[] dst,
                                      int dstBegin)
    {
        super.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see        #length()
     */
    public synchronized void setCharAt(int index, char ch) {
        if ((index < 0) || (index >= count))
            throw new StringIndexOutOfBoundsException(index);
        value[index] = ch;
    }

    public synchronized StringBuffer append(Object obj) {
        super.append(String.valueOf(obj));
        return this;
    }

    public synchronized StringBuffer append(String str) {
        super.append(str);
        return this;
    }

    /**
     * Appends the specified StringBuffer to this sequence.
     * 
     * The characters of the StringBuffer argument are appended,
     * in order, to the contents of this StringBuffer, increasing the
     * length of this StringBuffer by the length of the argument.
     * If sb is null, then the four characters
     * "null" are appended to this StringBuffer.
     * 
     * Let n be the length of the old character sequence, the one
     * contained in the StringBuffer just prior to execution of the
     * append method. Then the character at index k in
     * the new character sequence is equal to the character at index k
     * in the old character sequence, if k is less than n;
     * otherwise, it is equal to the character at index k-n in the
     * argument sb.
     * 
     * This method synchronizes on this (the destination)
     * object but does not synchronize on the source (sb).
     *
     * @param   sb   the StringBuffer to append.
     * @return  a reference to this object.
     * @since 1.4
     */
    public synchronized StringBuffer append(StringBuffer sb) {
        super.append(sb);
        return this;
    }


    /**
     * Appends the specified CharSequence to this
     * sequence.
     * 
     * The characters of the CharSequence argument are appended,
     * in order, increasing the length of this sequence by the length of the
     * argument.
     *
     * The result of this method is exactly the same as if it were an
     * invocation of this.append(s, 0, s.length());
     *
     * This method synchronizes on this (the destination)
     * object but does not synchronize on the source (s).
     *
     * If s is null, then the four characters
     * "null" are appended.
     *
     * @param   s the CharSequence to append.
     * @return  a reference to this object.
     * @since 1.5
     */
    public StringBuffer append(CharSequence s) {
        // Note, synchronization achieved via other invocations
        if (s == null)
            s = "null";
        if (s instanceof String)
            return this.append((String)s);
        if (s instanceof StringBuffer)
            return this.append((StringBuffer)s);
        return this.append(s, 0, s.length());
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @since      1.5
     */
    public synchronized StringBuffer append(CharSequence s, int start, int end)
    {
        super.append(s, start, end);
        return this;
    }

    public synchronized StringBuffer append(char[] str) {
        super.append(str);
        return this;
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized StringBuffer append(char[] str, int offset, int len) {
        super.append(str, offset, len);
        return this;
    }

    public synchronized StringBuffer append(boolean b) {
        super.append(b);
        return this;
    }

    public synchronized StringBuffer append(char c) {
        super.append(c);
        return this;
    }

    public synchronized StringBuffer append(int i) {
        super.append(i);
        return this;
    }

    /**
     * @since 1.5
     */
    public synchronized StringBuffer appendCodePoint(int codePoint) {
        super.appendCodePoint(codePoint);
        return this;
    }

    public synchronized StringBuffer append(long lng) {
        super.append(lng);
        return this;
    }

    public synchronized StringBuffer append(float f) {
        super.append(f);
        return this;
    }

    public synchronized StringBuffer append(double d) {
        super.append(d);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized StringBuffer delete(int start, int end) {
        super.delete(start, end);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized StringBuffer deleteCharAt(int index) {
        super.deleteCharAt(index);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized StringBuffer replace(int start, int end, String str) {
        super.replace(start, end, str);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized String substring(int start) {
        return substring(start, count);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @since      1.4
     */
    public synchronized CharSequence subSequence(int start, int end) {
        return super.substring(start, end);
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized String substring(int start, int end) {
        return super.substring(start, end);
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     * @since      1.2
     */
    public synchronized StringBuffer insert(int index, char[] str, int offset,
                                            int len)
    {
        super.insert(index, str, offset, len);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized StringBuffer insert(int offset, Object obj) {
        super.insert(offset, String.valueOf(obj));
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized StringBuffer insert(int offset, String str) {
        super.insert(offset, str);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized StringBuffer insert(int offset, char[] str) {
        super.insert(offset, str);
        return this;
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @since      1.5
     */
    public StringBuffer insert(int dstOffset, CharSequence s) {
        // Note, synchronization achieved via other invocations
        if (s == null)
            s = "null";
        if (s instanceof String)
            return this.insert(dstOffset, (String)s);
        return this.insert(dstOffset, s, 0, s.length());
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @since      1.5
     */
    public synchronized StringBuffer insert(int dstOffset, CharSequence s,
                                            int start, int end)
    {
        super.insert(dstOffset, s, start, end);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public StringBuffer insert(int offset, boolean b) {
        return insert(offset, String.valueOf(b));
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public synchronized StringBuffer insert(int offset, char c) {
        super.insert(offset, c);
        return this;
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public StringBuffer insert(int offset, int i) {
        return insert(offset, String.valueOf(i));
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public StringBuffer insert(int offset, long l) {
        return insert(offset, String.valueOf(l));
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public StringBuffer insert(int offset, float f) {
        return insert(offset, String.valueOf(f));
    }

    /**
     * @throws StringIndexOutOfBoundsException {@inheritDoc}
     */
    public StringBuffer insert(int offset, double d) {
        return insert(offset, String.valueOf(d));
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since      1.4
     */
    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since      1.4
     */
    public synchronized int indexOf(String str, int fromIndex) {
        return String.indexOf(value, 0, count,
                              str.toCharArray(), 0, str.length(), fromIndex);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since      1.4
     */
    public int lastIndexOf(String str) {
        // Note, synchronization achieved via other invocations
        return lastIndexOf(str, count);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since      1.4
     */
    public synchronized int lastIndexOf(String str, int fromIndex) {
        return String.lastIndexOf(value, 0, count,
                              str.toCharArray(), 0, str.length(), fromIndex);
    }

    /**
     * @since   JDK1.0.2
     */
    public synchronized StringBuffer reverse() {
        super.reverse();
        return this;
    }

    public synchronized String toString() {
        return new String(value, 0, count);
    }

    /**
     * Serializable fields for StringBuffer.
     *
     * @serialField value  char[]
     *              The backing character array of this StringBuffer.
     * @serialField count int
     *              The number of characters in this StringBuffer.
     * @serialField shared  boolean
     *              A flag indicating whether the backing array is shared.
     *              The value is ignored upon deserialization.
     */
    private static final java.io.ObjectStreamField[] serialPersistentFields =
    {
        new java.io.ObjectStreamField("value", char[].class),
        new java.io.ObjectStreamField("count", Integer.TYPE),
        new java.io.ObjectStreamField("shared", Boolean.TYPE),
    };

    /**
     * readObject is called to restore the state of the StringBuffer from
     * a stream.
     */
    private synchronized void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        java.io.ObjectOutputStream.PutField fields = s.putFields();
        fields.put("value", value);
        fields.put("count", count);
        fields.put("shared", false);
        s.writeFields();
    }

    /**
     * readObject is called to restore the state of the StringBuffer from
     * a stream.
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        java.io.ObjectInputStream.GetField fields = s.readFields();
        value = (char[])fields.get("value", null);
        count = fields.get("count", 0);
    }
}
