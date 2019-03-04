/*
 * Copyright (c) 1994, 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */

package java.io;

/**
 * This abstract class is the superclass of all classes representing an input stream of bytes.
 *
 * Applications that need to define a subclass of InputStream must always provide a method that returns the next byte of input.
 *
 * @author  Arthur van Hoff
 * @see     java.io.BufferedInputStream
 * @see     java.io.ByteArrayInputStream
 * @see     java.io.DataInputStream
 * @see     java.io.FilterInputStream
 * @see     java.io.InputStream#read()
 * @see     java.io.OutputStream
 * @see     java.io.PushbackInputStream
 * @since   JDK1.0
 */
public abstract class InputStream implements Closeable {

    // MAX_SKIP_BUFFER_SIZE is used to determine the maximum buffer size to use when skipping.
    private static final int MAX_SKIP_BUFFER_SIZE = 2048;

    /**
     * Reads the next byte of data from the input stream. The value byte is returned as an int in the range 0 to 255. If no byte is available
     * because the end of the stream has been reached, the value -1 is returned.
     *
     * 该方法会一直阻塞，直到数据是可读的、数据流到了结尾或者抛出异常。
     * This method blocks until input data is available, the end of the stream is detected, or an exception is thrown.
     * A subclass must provide an implementation of this method.
     *
     * @return     the next byte of data, or -1 if the end of the stream is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    public abstract int read() throws IOException;

    /**
     * Reads some number of bytes from the input stream and stores them into the buffer array b.
     *
     * 函数的返回值是实际读取的字节数
     * The number of bytes actually read is returned as an integer.
     *
     * 该方法会一直阻塞，直到数据是可读的、数据流到了结尾或者抛出异常。
     * This method blocks until input data is available, end of file is detected, or an exception is thrown.
     *
     * 如果参数中的字节数组长度为0，则不会读取任何byte，直接返回0(一种非法参数的处理方式)。
     * If the length of b is zero, then no bytes are read and 0 is returned;
     *
     * 排除非法参数的情况，正常情况下至少会读取一字节。如果因为已经到达文件的尾部而无可读数据则返回-1。
     * otherwise, there is an attempt to read at least one byte. If no byte is available because the stream is at the end of the file,
     * the value -1 is returned; otherwise, at least one byte is read and stored into b.
     *
     * The first byte read is stored into element b[0], the next one into b[1], and so on. The number of bytes read is, at most, equal
     * to the length of b. Let k be the number of bytes actually read; these bytes will be stored in elements b[0] through b[k-1], leaving
     * elements b[k] through b[b.length-1] unaffected.
     *
     * The read(b) method for class InputStream has the same effect as: read(b, 0, b.length)
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or -1 if there is no more data because the end of the stream has been reached.
     * @exception  IOException  If the first byte cannot be read for any reason other than the end of the file, if the input stream has been closed,
     *                          or if some other I/O error occurs.
     * @exception  NullPointerException  if b is null.
     * @see        java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads up to len bytes of data from the input stream into an array of bytes.  An attempt is made to read as many as len bytes, but a
     * smaller number may be read.
     *
     * 函数的返回值是实际读取的字节数
     * The number of bytes actually read is returned as an integer.
     *
     * 该方法会一直阻塞，直到数据是可读的、数据流到了结尾或者抛出异常。
     * This method blocks until input data is available, end of file is detected, or an exception is thrown.
     *
     * If len is zero, then no bytes are read and 0 is returned;
     * otherwise, there is an attempt to read at least one byte. If no byte is available because the stream is at end of file, the value -1
     * is returned; otherwise, at least one byte is read and stored into b.
     *
     * The first byte read is stored into element b[off], the next one into b[off+1], and so on. The number of bytes read is, at most, equal
     * to len. Let k be the number of bytes actually read; these bytes will be stored in elements b[off] through b[off+k-1], leaving elements
     * b[off+k] through b[off+len-1] unaffected.
     *
     * In every case, elements b[0] through b[off] and elements b[off+len] through b[b.length-1] are unaffected.
     *
     * The read(b, off, len) method for class InputStream simply calls the method read() repeatedly. If the first such call results in an
     * IOException, that exception is returned from the call to the read(b, off, len) method. If any subsequent call to read() results in
     * a IOException, the exception is caught and treated as if it were end of file; the bytes read up to that point are stored into b and
     * the number of bytes read before the exception occurred is returned. The default implementation of this method blocks until the requested
     * amount of input data len has been read, end of file is detected, or an exception is thrown. Subclasses are encouraged to provide a more
     * efficient implementation of this method.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array b at which the data is written.
     * @param      len   the maximum number of bytes to read.
     * @return     the total number of bytes read into the buffer, or -1 if there is no more data because the end of the stream has been reached.
     * @exception  IOException If the first byte cannot be read for any reason other than end of file, or if the input stream has been closed, or
     *                         if some other I/O error occurs.
     * @exception  NullPointerException If b is null.
     * @exception  IndexOutOfBoundsException If off is negative, len is negative, or len is greater than b.length - off
     * @see        java.io.InputStream#read()
     */
    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        /**
         * Java IO面向流的根本原因是这里走的系统调用，这里一次只能读一个字节或者一行
         */
        int c = read();
        if (c == -1) {
            return -1;
        }
        b[off] = (byte)c;

        int i = 1;
        try {
            for (; i < len ; i++) {
                c = read(); //单次只能读取一个字节，c即为读取的字节
                if (c == -1) {
                    break;
                }
                b[off + i] = (byte)c;
            }
        } catch (IOException ee) {
        }
        return i;
    }

    /**
     * Skips over and discards n bytes of data from this input stream.
     * 
     * 该方法会因为种种原因，结束时跳过的实际数量可能小于n，也可能是0。比如已经达到文件末尾不足n字节的位置只是其中的一种原因。
     * The skip method may, for a variety of reasons, end up skipping over some smaller number of bytes, possibly 0. This may result from
     * any of a number of conditions; reaching end of file before n bytes have been skipped is only one possibility. 
     * 
     * 返回值是实际跳过的字节数，如果传入的参数是负数则不跳过任何字节。
     * The actual number of bytes skipped is returned. If n is negative, no bytes are skipped.
     *
     * The skip method of this class creates a byte array and then repeatedly reads into it until n bytes have been read or the end of the
     * stream has been reached. Subclasses are encouraged to provide a more efficient implementation of this method.
     *
     * For instance, the implementation may depend on the ability to seek.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if the stream does not support seek, or if some other I/O error occurs.
     */
    public long skip(long n) throws IOException {

        long remaining = n;
        int nr;

        if (n <= 0) {
            return 0;
        }

        int size = (int)Math.min(MAX_SKIP_BUFFER_SIZE, remaining);
        byte[] skipBuffer = new byte[size];
        while (remaining > 0) {
            nr = read(skipBuffer, 0, (int)Math.min(size, remaining));
            if (nr < 0) {
                break;
            }
            remaining -= nr;
        }

        return n - remaining;
    }

    /**
     * 返回一个预估值，即输入流中在无阻塞的前提下还能读取多少字节
     * Returns an estimate of the number of bytes that can be read (or skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. 
     * 
     * The next invocation might be the same thread or another thread. A single read or skip of this many bytes will not block, but may
     * read or skip fewer bytes.
     *
     * 在有些实现中，确实会返回流中的总的字节数，但是有些实现中并不会。因此以该函数的返回值作为分配接收缓存大小的依据是不对的
     * Note that while some implementations of InputStream will return the total number of bytes in the stream, many will not. It is never
     * correct to use the return value of this method to allocate a buffer intended to hold all data in this stream.
     *
     * A subclass' implementation of this method may choose to throw an IOException if this input stream has been closed by invoking the
     * close() method.
     *
     * The available method for class InputStream always returns 0.
     *
     * This method should be overridden by subclasses. 那么为什么不定义为抽象方法？需要一个默认值？但是0这个值没有任何意义啊
     *
     * @return     an estimate of the number of bytes that can be read (or skipped over) from this input stream without blocking or 0 when
     *             it reaches the end of the input stream.
     * @exception  IOException if an I/O error occurs.
     */
    public int available() throws IOException {
        return 0;
    }

    /**
     * Closes this input stream and releases any system resources associated with the stream.
     *
     * The close method of InputStream does nothing.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    public void close() throws IOException {}

    /**
     * Marks the current position in this input stream. A subsequent call to the reset method repositions this stream at the last marked
     * position so that subsequent reads re-read the same bytes.
     *
     * The readlimit arguments tells this input stream to allow that many bytes to be read before the mark position gets invalidated.
     *
     * The general contract of mark is that, if the method markSupported returns true, the stream somehow remembers all the bytes read
     * after the call to mark and stands ready to supply those same bytes again if and whenever the method reset is called. However, the
     * stream is not required to remember any data at all if more than readlimit bytes are read from the stream before reset is called.
     *
     * Marking a closed stream should not have any effect on the stream.
     *
     * The mark method of InputStream does nothing.
     *
     * @param   readlimit   the maximum limit of bytes that can be read before the mark position becomes invalid.
     * @see     java.io.InputStream#reset()
     */
    public synchronized void mark(int readlimit) {}

    /**
     * Repositions this stream to the position at the time the mark method was last called on this input stream.
     *
     * The general contract of reset is:
     *
     * If the method markSupported returns true, then:
     *
     *     If the method mark has not been called since the stream was created, or the number of bytes read from the stream since mark was
     *     last called is larger than the argument to mark at that last call, then an IOException might be thrown.
     *
     *     If such an IOException is not thrown, then the stream is reset to a state such that all the bytes read since the most recent call
     *     to mark (or since the start of the file, if mark has not been called) will be resupplied to subsequent callers of the read method,
     *     followed by any bytes that otherwise would have been the next input data as of the time of the call to reset.
     *
     * If the method markSupported returns false, then:
     *
     *     The call to reset may throw an IOException.
     *
     *     If an IOException is not thrown, then the stream is reset to a fixed state that depends on the particular type of the input stream
     *     and how it was created. The bytes that will be supplied to subsequent callers of the read method depend on the particular type of
     *     the input stream.
     *
     * The method reset for class InputStream does nothing except throw an IOException.
     *
     * @exception  IOException  if this stream has not been marked or if the mark has been invalidated.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.IOException
     */
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Tests if this input stream supports the mark and reset methods. Whether or not mark and reset are supported is an invariant property
     * of a particular input stream instance. The markSupported method of InputStream returns false.
     *
     * @return  true if this stream instance supports the mark and reset methods; false otherwise.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    public boolean markSupported() {
        return false;
    }

}
