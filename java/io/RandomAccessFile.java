/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 */

package java.io;

import java.nio.channels.FileChannel;
import sun.nio.ch.FileChannelImpl;
import sun.misc.IoTrace;


/**
 * Instances of this class support both reading and writing to a random access file. 
 *
 * RandomAccessFile的行为就像是一个在文件系统中的大型的字节数组。在这个数组中存在游标或索引对数组的读写行为进行指向，称为文件指针。
 * A random access file behaves like a large array of bytes stored in the file system. There is a kind of cursor, or index into the implied
 * array, called the file pointer;
 *
 * 读操作从文件指针指向的位置开始读取，并随读取行为向前移动文件指针
 * input operations read bytes starting at the file pointer and advance the file pointer past the bytes read.
 *
 * If the random access file is created in read/write mode, then output operations are also available;
 *
 * 写操作同样从文件指针指向的位置开始写入，并随写入行为向前移动文件指针。当文件指针已经移动到文件的末尾，文件对应的内存中的字节数组随之扩展。
 * output operations write bytes starting at the file pointer and advance the file pointer past the bytes written. Output operations that
 * write past the current end of the implied array cause the array to be extended.
 *
 * The file pointer can be read by the getFilePointer method and set by the seek method.
 * 
 * 当要求读取的位置已经超出文件末尾了，则会抛出EOFException，除此之外的其他原因导致无法读取，则抛出则会抛出EOFException之外的IOException。
 * It is generally true of all the reading routines in this class that if end-of-file is reached before the desired number of bytes has been
 * read, an EOFException (which is a kind of IOException) is thrown. If any byte cannot be read for any reason other than end-of-file, an
 * IOException other than EOFException is thrown. In particular, an IOException may be thrown if the stream has been closed.
 *
 * @author  unascribed
 * @since   JDK1.0
 */

public class RandomAccessFile implements DataOutput, DataInput, Closeable {

    private FileDescriptor fd;
    private FileChannel channel = null;
    private boolean rw;

    /* The path of the referenced file */
    private final String path;

    private Object closeLock = new Object();
    private volatile boolean closed = false;

    private static final int O_RDONLY = 1;
    private static final int O_RDWR =   2;
    private static final int O_SYNC =   4;
    private static final int O_DSYNC =  8;

    /**
     * Creates a random access file stream to read from, and optionally to write to, a file with the specified name. A new FileDescriptor
     * object is created to represent the connection to the file.
     *
     * The mode argument specifies the access mode with which the file is to be opened. The permitted values and their meanings are as
     * specified for the RandomAccessFile(File,String) constructor.
     *
     * 
     * If there is a security manager, its checkRead method is called with the name argument as its argument to see if read access to the
     * file is allowed. If the mode allows writing, the security manager's checkWrite method is also called with the name argument as its
     * argument to see if write access to the file is allowed.
     *
     * @param      name   the system-dependent filename
     * @param      mode   the access mode
     * @exception IllegalArgumentException  if the mode argument is not equal to one of "r", "rw", "rws", or "rwd"
     * @exception FileNotFoundException if the mode is "r" but the given string does not denote an existing regular file, or if the mode begins
     *                                  with "rw" but the given string does not denote an existing, writable regular file and a new regular file
     *                                  of that name cannot be created, or if some other error occurs while opening or creating the file
     * @exception  SecurityException if a security manager exists and its checkRead method denies read access to the file or the mode is "rw" and
     *                               the security manager's checkWrite method denies write access to the file
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkRead(java.lang.String)
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @revised 1.4
     * @spec JSR-51
     */
    public RandomAccessFile(String name, String mode) throws FileNotFoundException
    {
        this(name != null ? new File(name) : null, mode);
    }

    /**
     * Creates a random access file stream to read from, and optionally to write to, the file specified by the File argument. A new FileDescriptor
     * object is created to represent this file connection.
     *
     * The mode argument specifies the access mode in which the file is to be opened. The permitted values and their meanings are:
     *
     * Value    Meaning
     * r        Open for reading only. Invoking any of the write methods of the resulting object will cause an IOException to be thrown.
     * rw       Open for reading and writing. If the file does not already exist then an attempt will be made to create it.
     * rws      Open for reading and writing, as with "rw", and also require that every update to the file's content or metadata be written
     *          synchronously to the underlying storage device.
     * rwd      Open for reading and writing, as with "rw", and also require that every update to the file's content be written synchronously
     *          to the underlying storage device.
     *
     * The "rws" and "rwd" modes work much like the java.nio.channels.FileChannel#force(boolean) method of the java.nio.channels.FileChannel
     * class, passing arguments of true and false, respectively, except that they always apply to every I/O operation and are therefore often
     * more efficient. If the file resides on a local storage device then when an invocation of a method of this class returns it is guaranteed
     * that all changes made to the file by that invocation will have been written to that device. This is useful for ensuring that critical
     * information is not lost in the event of a system crash. If the file does not reside on a local device then no such guarantee is made.
     *
     * The "rwd" mode can be used to reduce the number of I/O operations performed. Using "rwd" only requires updates to the file's content to
     * be written to storage; using "rws" requires updates to both the file's content and its metadata to be written, which generally requires
     * at least one more low-level I/O operation.
     *
     * If there is a security manager, its checkRead method is called with the pathname of the file argument as its argument to see if read access
     * to the file is allowed. If the mode allows writing, the security manager's checkWrite method is also called with the path argument to see
     * if write access to the file is allowed.
     *
     * @param      file   the file object
     * @param      mode   the access mode, as described above
     * @exception  IllegalArgumentException  if the mode argument is not equal to one of "r", "rw", "rws", or "rwd"
     * @exception FileNotFoundException if the mode is "r" but the given file object does not denote an existing regular file, or if the mode begins
     *                                  with "rw" but the given file object does not denote an existing, writable regular file and a new regular file
     *                                  of that name cannot be created, or if some other error occurs while opening or creating the file
     * @exception  SecurityException if a security manager exists and its checkRead method denies read access to the file or the mode is "rw" and the
     *                               security manager's checkWrite method denies write access to the file
     * @see        java.lang.SecurityManager#checkRead(java.lang.String)
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @see        java.nio.channels.FileChannel#force(boolean)
     * @revised 1.4
     * @spec JSR-51
     */
    public RandomAccessFile(File file, String mode)
        throws FileNotFoundException
    {
        String name = (file != null ? file.getPath() : null);
        int imode = -1;
        if (mode.equals("r"))
            imode = O_RDONLY;
        else if (mode.startsWith("rw")) {
            imode = O_RDWR;
            rw = true;
            if (mode.length() > 2) {
                if (mode.equals("rws"))
                    imode |= O_SYNC;
                else if (mode.equals("rwd"))
                    imode |= O_DSYNC;
                else
                    imode = -1;
            }
        }
        if (imode < 0)
            throw new IllegalArgumentException("Illegal mode \"" + mode
                                               + "\" must be one of "
                                               + "\"r\", \"rw\", \"rws\","
                                               + " or \"rwd\"");
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkRead(name);
            if (rw) {
                security.checkWrite(name);
            }
        }
        if (name == null) {
            throw new NullPointerException();
        }
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }
        fd = new FileDescriptor();
        fd.incrementAndGetUseCount();
        this.path = name;
        open(name, imode);
    }

    /**
     * Returns the opaque file descriptor object associated with this stream.
     *
     * @return     the file descriptor object associated with this stream.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FileDescriptor
     */
    public final FileDescriptor getFD() throws IOException {
        if (fd != null) return fd;
        throw new IOException();
    }

    /**
     * Returns the unique java.nio.channels.FileChannel FileChannel object associated with this file.
     *
     * The java.nio.channels.FileChannel#position() position of the returned channel will always be equal to this object's file-pointer offset
     * as returned by the getFilePointer method. Changing this object's file-pointer offset, whether explicitly or by reading or writing bytes,
     * will change the position of the channel, and vice versa. Changing the file's length via this object will change the length seen via the
     * file channel, and vice versa.
     *
     * @return  the file channel associated with this file
     *
     * @since 1.4
     * @spec JSR-51
     */
    public final FileChannel getChannel() {
        synchronized (this) {
            if (channel == null) {
                channel = FileChannelImpl.open(fd, path, true, rw, this);

                /*
                 * FileDescriptor could be shared by FileInputStream or
                 * FileOutputStream.
                 * Ensure that FD is GC'ed only when all the streams/channels
                 * are done using it.
                 * Increment fd's use count. Invoking the channel's close()
                 * method will result in decrementing the use count set for
                 * the channel.
                 */
                fd.incrementAndGetUseCount();
            }
            return channel;
        }
    }

    /**
     * Opens a file and returns the file descriptor. The file is opened in read-write mode if the O_RDWR bit in mode is true, else the file
     * is opened as read-only. If the name refers to a directory, an IOException is thrown.
     *
     * @param name the name of the file
     * @param mode the mode flags, a combination of the O_ constants
     *             defined above
     */
    private native void open(String name, int mode)
        throws FileNotFoundException;

    // 'Read' primitives

    /**
     * Reads a byte of data from this file. The byte is returned as an integer in the range 0 to 255 (0x00-0x0ff). This method blocks if no
     * input is yet available.
     * 
     * Although RandomAccessFile is not a subclass of InputStream, this method behaves in exactly the same way as the InputStream#read() method
     * of InputStream.
     *
     * @return     the next byte of data, or -1 if the end of the file has been reached.
     * @exception  IOException  if an I/O error occurs. Not thrown if end-of-file has been reached.
     */
    public int read() throws IOException {
        Object traceContext = IoTrace.fileReadBegin(path);
        int b = 0;
        try {
            b = read0();
        } finally {
            IoTrace.fileReadEnd(traceContext, b == -1 ? 0 : 1);
        }
        return b;
    }

    private native int read0() throws IOException;

    /**
     * Reads a sub array as a sequence of bytes.
     * @param b the buffer into which the data is read.
     * @param off the start offset of the data.
     * @param len the number of bytes to read.
     * @exception IOException If an I/O error has occurred.
     */
    private int readBytes(byte b[], int off, int len) throws IOException {
        Object traceContext = IoTrace.fileReadBegin(path);
        int bytesRead = 0;
        try {
            bytesRead = readBytes0(b, off, len);
        } finally {
            IoTrace.fileReadEnd(traceContext, bytesRead == -1 ? 0 : bytesRead);
        }
        return bytesRead;
    }

    private native int readBytes0(byte b[], int off, int len) throws IOException;

    /**
     * Reads up to len bytes of data from this file into an
     * array of bytes. This method blocks until at least one byte of input
     * is available.
     * 
     * Although RandomAccessFile is not a subclass of
     * InputStream, this method behaves in exactly the
     * same way as the InputStream#read(byte[], int, int)} method of
     * InputStream.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in array b
     *                   at which the data is written.
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             -1 if there is no more data because the end of
     *             the file has been reached.
     * @exception  IOException If the first byte cannot be read for any reason
     * other than end of file, or if the random access file has been closed, or if
     * some other I/O error occurs.
     * @exception  NullPointerException If b is null.
     * @exception  IndexOutOfBoundsException If off is negative,
     * len is negative, or len is greater than
     * b.length - off
     */
    public int read(byte b[], int off, int len) throws IOException {
        return readBytes(b, off, len);
    }

    /**
     * Reads up to b.length bytes of data from this file
     * into an array of bytes. This method blocks until at least one byte
     * of input is available.
     * 
     * Although RandomAccessFile is not a subclass of
     * InputStream, this method behaves in exactly the
     * same way as the InputStream#read(byte[])} method of
     * InputStream.
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or
     *             -1 if there is no more data because the end of
     *             this file has been reached.
     * @exception  IOException If the first byte cannot be read for any reason
     * other than end of file, or if the random access file has been closed, or if
     * some other I/O error occurs.
     * @exception  NullPointerException If b is null.
     */
    public int read(byte b[]) throws IOException {
        return readBytes(b, 0, b.length);
    }

    /**
     * Reads b.length bytes from this file into the byte
     * array, starting at the current file pointer. This method reads
     * repeatedly from the file until the requested number of bytes are
     * read. This method blocks until the requested number of bytes are
     * read, the end of the stream is detected, or an exception is thrown.
     *
     * @param      b   the buffer into which the data is read.
     * @exception  EOFException  if this file reaches the end before reading
     *               all the bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * Reads exactly len bytes from this file into the byte
     * array, starting at the current file pointer. This method reads
     * repeatedly from the file until the requested number of bytes are
     * read. This method blocks until the requested number of bytes are
     * read, the end of the stream is detected, or an exception is thrown.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset of the data.
     * @param      len   the number of bytes to read.
     * @exception  EOFException  if this file reaches the end before reading
     *               all the bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final void readFully(byte b[], int off, int len) throws IOException {
        int n = 0;
        do {
            int count = this.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        } while (n < len);
    }

    /**
     * Attempts to skip over n bytes of input discarding the
     * skipped bytes.
     * 
     *
     * This method may skip over some smaller number of bytes, possibly zero.
     * This may result from any of a number of conditions; reaching end of
     * file before n bytes have been skipped is only one
     * possibility. This method never throws an EOFException.
     * The actual number of bytes skipped is returned.  If n
     * is negative, no bytes are skipped.
     *
     * @param      n   the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if an I/O error occurs.
     */
    public int skipBytes(int n) throws IOException {
        long pos;
        long len;
        long newpos;

        if (n <= 0) {
            return 0;
        }
        pos = getFilePointer();
        len = length();
        newpos = pos + n;
        if (newpos > len) {
            newpos = len;
        }
        seek(newpos);

        /* return the actual number of bytes skipped */
        return (int) (newpos - pos);
    }

    // 'Write' primitives

    /**
     * Writes the specified byte to this file. The write starts at
     * the current file pointer.
     *
     * @param      b   the byte to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(int b) throws IOException {
        Object traceContext = IoTrace.fileWriteBegin(path);
        int bytesWritten = 0;
        try {
            write0(b);
            bytesWritten = 1;
        } finally {
            IoTrace.fileWriteEnd(traceContext, bytesWritten);
        }
    }

    private native void write0(int b) throws IOException;

    /**
     * Writes a sub array as a sequence of bytes.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception IOException If an I/O error has occurred.
     */
    private void writeBytes(byte b[], int off, int len) throws IOException {
        Object traceContext = IoTrace.fileWriteBegin(path);
        int bytesWritten = 0;
        try {
            writeBytes0(b, off, len);
            bytesWritten = len;
        } finally {
            IoTrace.fileWriteEnd(traceContext, bytesWritten);
        }
    }

    private native void writeBytes0(byte b[], int off, int len) throws IOException;

    /**
     * Writes b.length bytes from the specified byte array
     * to this file, starting at the current file pointer.
     *
     * @param      b   the data.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(byte b[]) throws IOException {
        writeBytes(b, 0, b.length);
    }

    /**
     * Writes len bytes from the specified byte array
     * starting at offset off to this file.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(byte b[], int off, int len) throws IOException {
        writeBytes(b, off, len);
    }

    // 'Random access' stuff

    /**
     * Returns the current offset in this file.
     *
     * @return     the offset from the beginning of the file, in bytes,
     *             at which the next read or write occurs.
     * @exception  IOException  if an I/O error occurs.
     */
    public native long getFilePointer() throws IOException;

    /**
     * Sets the file-pointer offset, measured from the beginning of this
     * file, at which the next read or write occurs.  The offset may be
     * set beyond the end of the file. Setting the offset beyond the end
     * of the file does not change the file length.  The file length will
     * change only by writing after the offset has been set beyond the end
     * of the file.
     *
     * @param      pos   the offset position, measured in bytes from the
     *                   beginning of the file, at which to set the file
     *                   pointer.
     * @exception  IOException  if pos is less than
     *                          0 or if an I/O error occurs.
     */
    public native void seek(long pos) throws IOException;

    /**
     * Returns the length of this file.
     *
     * @return     the length of this file, measured in bytes.
     * @exception  IOException  if an I/O error occurs.
     */
    public native long length() throws IOException;

    /**
     * Sets the length of this file.
     *
     *  If the present length of the file as returned by the
     * length method is greater than the newLength
     * argument then the file will be truncated.  In this case, if the file
     * offset as returned by the getFilePointer method is greater
     * than newLength then after this method returns the offset
     * will be equal to newLength.
     *
     *  If the present length of the file as returned by the
     * length method is smaller than the newLength
     * argument then the file will be extended.  In this case, the contents of
     * the extended portion of the file are not defined.
     *
     * @param      newLength    The desired length of the file
     * @exception  IOException  If an I/O error occurs
     * @since      1.2
     */
    public native void setLength(long newLength) throws IOException;

    /**
     * Closes this random access file stream and releases any system
     * resources associated with the stream. A closed random access
     * file cannot perform input or output operations and cannot be
     * reopened.
     *
     *  If this file has an associated channel then the channel is closed
     * as well.
     *
     * @exception  IOException  if an I/O error occurs.
     *
     * @revised 1.4
     * @spec JSR-51
     */
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        if (channel != null) {
            /*
             * Decrement FD use count associated with the channel. The FD use
             * count is incremented whenever a new channel is obtained from
             * this stream.
             */
            fd.decrementAndGetUseCount();
            channel.close();
        }

        /*
         * Decrement FD use count associated with this stream.
         * The count got incremented by FileDescriptor during its construction.
         */
        fd.decrementAndGetUseCount();
        close0();
    }

    //
    //  Some "reading/writing Java data types" methods stolen from
    //  DataInputStream and DataOutputStream.
    //

    /**
     * Reads a boolean from this file. This method reads a
     * single byte from the file, starting at the current file pointer.
     * A value of 0 represents
     * false. Any other value represents true.
     * This method blocks until the byte is read, the end of the stream
     * is detected, or an exception is thrown.
     *
     * @return     the boolean value read.
     * @exception  EOFException  if this file has reached the end.
     * @exception  IOException   if an I/O error occurs.
     */
    public final boolean readBoolean() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return (ch != 0);
    }

    /**
     * Reads a signed eight-bit value from this file. This method reads a
     * byte from the file, starting from the current file pointer.
     * If the byte read is b, where
     * 0&nbsp;&lt;=&nbsp;b&nbsp;&lt;=&nbsp;255,
     * then the result is:
     * <blockquote><pre>
     *     (byte)(b)
     * </pre></blockquote>
     * 
     * This method blocks until the byte is read, the end of the stream
     * is detected, or an exception is thrown.
     *
     * @return     the next byte of this file as a signed eight-bit
     *             byte.
     * @exception  EOFException  if this file has reached the end.
     * @exception  IOException   if an I/O error occurs.
     */
    public final byte readByte() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return (byte)(ch);
    }

    /**
     * Reads an unsigned eight-bit number from this file. This method reads
     * a byte from this file, starting at the current file pointer,
     * and returns that byte.
     * 
     * This method blocks until the byte is read, the end of the stream
     * is detected, or an exception is thrown.
     *
     * @return     the next byte of this file, interpreted as an unsigned
     *             eight-bit number.
     * @exception  EOFException  if this file has reached the end.
     * @exception  IOException   if an I/O error occurs.
     */
    public final int readUnsignedByte() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    /**
     * Reads a signed 16-bit number from this file. The method reads two
     * bytes from this file, starting at the current file pointer.
     * If the two bytes read, in order, are
     * b1 and b2, where each of the two values is
     * between 0 and 255, inclusive, then the
     * result is equal to:
     * <blockquote><pre>
     *     (short)((b1 &lt;&lt; 8) | b2)
     * </pre></blockquote>
     * 
     * This method blocks until the two bytes are read, the end of the
     * stream is detected, or an exception is thrown.
     *
     * @return     the next two bytes of this file, interpreted as a signed
     *             16-bit number.
     * @exception  EOFException  if this file reaches the end before reading
     *               two bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final short readShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (short)((ch1 << 8) + (ch2 << 0));
    }

    /**
     * Reads an unsigned 16-bit number from this file. This method reads
     * two bytes from the file, starting at the current file pointer.
     * If the bytes read, in order, are
     * b1 and b2, where
     * 0&nbsp;&lt;=&nbsp;b1, b2&nbsp;&lt;=&nbsp;255,
     * then the result is equal to:
     * <blockquote><pre>
     *     (b1 &lt;&lt; 8) | b2
     * </pre></blockquote>
     * 
     * This method blocks until the two bytes are read, the end of the
     * stream is detected, or an exception is thrown.
     *
     * @return     the next two bytes of this file, interpreted as an unsigned
     *             16-bit integer.
     * @exception  EOFException  if this file reaches the end before reading
     *               two bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final int readUnsignedShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 8) + (ch2 << 0);
    }

    /**
     * Reads a character from this file. This method reads two
     * bytes from the file, starting at the current file pointer.
     * If the bytes read, in order, are
     * b1 and b2, where
     * 0&nbsp;&lt;=&nbsp;b1,&nbsp;b2&nbsp;&lt;=&nbsp;255,
     * then the result is equal to:
     * <blockquote><pre>
     *     (char)((b1 &lt;&lt; 8) | b2)
     * </pre></blockquote>
     * 
     * This method blocks until the two bytes are read, the end of the
     * stream is detected, or an exception is thrown.
     *
     * @return     the next two bytes of this file, interpreted as a
     *                  char.
     * @exception  EOFException  if this file reaches the end before reading
     *               two bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final char readChar() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char)((ch1 << 8) + (ch2 << 0));
    }

    /**
     * Reads a signed 32-bit integer from this file. This method reads 4
     * bytes from the file, starting at the current file pointer.
     * If the bytes read, in order, are b1,
     * b2, b3, and b4, where
     * 0&nbsp;&lt;=&nbsp;b1, b2, b3, b4&nbsp;&lt;=&nbsp;255,
     * then the result is equal to:
     * <blockquote><pre>
     *     (b1 &lt;&lt; 24) | (b2 &lt;&lt; 16) + (b3 &lt;&lt; 8) + b4
     * </pre></blockquote>
     * 
     * This method blocks until the four bytes are read, the end of the
     * stream is detected, or an exception is thrown.
     *
     * @return     the next four bytes of this file, interpreted as an int.
     * @exception  EOFException  if this file reaches the end before reading four bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final int readInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    /**
     * Reads a signed 64-bit integer from this file. This method reads eight bytes from the file, starting at the current file pointer.
     * If the bytes read, in order, are b1, b2, b3, b4, b5, b6, b7, and b8, where:
     *
     *     0 &lt;= b1, b2, b3, b4, b5, b6, b7, b8 &lt;=255,
     *
     * then the result is equal to:
     *     ((long)b1 &lt;&lt; 56) + ((long)b2 &lt;&lt; 48)
     *     + ((long)b3 &lt;&lt; 40) + ((long)b4 &lt;&lt; 32)
     *     + ((long)b5 &lt;&lt; 24) + ((long)b6 &lt;&lt; 16)
     *     + ((long)b7 &lt;&lt; 8) + b8
     *
     * This method blocks until the eight bytes are read, the end of the stream is detected, or an exception is thrown.
     *
     * @return     the next eight bytes of this file, interpreted as a long.
     * @exception  EOFException  if this file reaches the end before reading eight bytes.
     * @exception  IOException   if an I/O error occurs.
     */
    public final long readLong() throws IOException {
        return ((long)(readInt()) << 32) + (readInt() & 0xFFFFFFFFL);
    }

    /**
     * Reads a float from this file. This method reads an int value, starting at the current file pointer, as if by the readInt method
     * and then converts that int to a float using the intBitsToFloat method in class Float.
     * 
     * This method blocks until the four bytes are read, the end of the stream is detected, or an exception is thrown.
     *
     * @return     the next four bytes of this file, interpreted as a float.
     * @exception  EOFException  if this file reaches the end before reading four bytes.
     * @exception  IOException   if an I/O error occurs.
     * @see        java.io.RandomAccessFile#readInt()
     * @see        java.lang.Float#intBitsToFloat(int)
     */
    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads a double from this file. This method reads a long value, starting at the current file pointer, as if by the readLong method
     * and then converts that long to a double using the longBitsToDouble method in class Double.
     * 
     * This method blocks until the eight bytes are read, the end of the stream is detected, or an exception is thrown.
     *
     * @return     the next eight bytes of this file, interpreted as a double.
     * @exception  EOFException  if this file reaches the end before reading eight bytes.
     * @exception  IOException   if an I/O error occurs.
     * @see        java.io.RandomAccessFile#readLong()
     * @see        java.lang.Double#longBitsToDouble(long)
     */
    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads the next line of text from this file.  This method successively reads bytes from the file, starting at the current file pointer,
     * until it reaches a line terminator or the end of the file. Each byte is converted into a character by taking the byte's value for the
     * lower eight bits of the character and setting the high eight bits of the character to zero.  This method does not, therefore, support
     * the full Unicode character set.
     *
     * A line of text is terminated by a carriage-return character ('&#92;r'), a newline character ('&#92;n'), a carriage-return character
     * immediately followed by a newline character, or the end of the file.  Line-terminating characters are discarded and are not included
     * as part of the string returned.
     *
     * This method blocks until a newline character is read, a carriage return and the byte following it are read (to see if it is a newline),
     * the end of the file is reached, or an exception is thrown.
     *
     * @return     the next line of text from this file, or null if end of file is encountered before even one byte is read.
     * @exception  IOException  if an I/O error occurs.
     */

    public final String readLine() throws IOException {
        StringBuffer input = new StringBuffer();
        int c = -1;
        boolean eol = false;

        while (!eol) {
            switch (c = read()) {
            case -1:
            case '\n':
                eol = true;
                break;
            case '\r':
                eol = true;
                long cur = getFilePointer();
                if ((read()) != '\n') {
                    seek(cur);
                }
                break;
            default:
                input.append((char)c);
                break;
            }
        }

        if ((c == -1) && (input.length() == 0)) {
            return null;
        }
        return input.toString();
    }

    /**
     * Reads in a string from this file. The string has been encoded using a modified UTF-8 format.
     * 
     * The first two bytes are read, starting from the current file pointer, as if by readUnsignedShort. This value gives the number of
     * following bytes that are in the encoded string, not the length of the resulting string. The following bytes are then interpreted
     * as bytes encoding characters in the modified UTF-8 format and are converted into characters.
     * 
     * This method blocks until all the bytes are read, the end of the stream is detected, or an exception is thrown.
     *
     * @return     a Unicode string.
     * @exception  EOFException            if this file reaches the end before reading all the bytes.
     * @exception  IOException             if an I/O error occurs.
     * @exception  UTFDataFormatException  if the bytes do not represent valid modified UTF-8 encoding of a Unicode string.
     * @see        java.io.RandomAccessFile#readUnsignedShort()
     */
    public final String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    /**
     * Writes a boolean to the file as a one-byte value. The value true is written out as the value (byte)1; the value false is written out
     * as the value (byte)0. The write starts at the current position of the file pointer.
     *
     * @param      v   a boolean value to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeBoolean(boolean v) throws IOException {
        write(v ? 1 : 0);
        //written++;
    }

    /**
     * Writes a byte to the file as a one-byte value. The write starts at the current position of the file pointer.
     *
     * @param      v   a byte value to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeByte(int v) throws IOException {
        write(v);
        //written++;
    }

    /**
     * Writes a short to the file as two bytes, high byte first. The write starts at the current position of the file pointer.
     *
     * @param      v   a short to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeShort(int v) throws IOException {
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
        //written += 2;
    }

    /**
     * Writes a char to the file as a two-byte value, high byte first. The write starts at the current position of the file pointer.
     *
     * @param      v   a char value to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeChar(int v) throws IOException {
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
        //written += 2;
    }

    /**
     * Writes an int to the file as four bytes, high byte first. The write starts at the current position of the file pointer.
     *
     * @param      v   an int to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeInt(int v) throws IOException {
        write((v >>> 24) & 0xFF);
        write((v >>> 16) & 0xFF);
        write((v >>>  8) & 0xFF);
        write((v >>>  0) & 0xFF);
        //written += 4;
    }

    /**
     * Writes a long to the file as eight bytes, high byte first. The write starts at the current position of the file pointer.
     *
     * @param      v   a long to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeLong(long v) throws IOException {
        write((int)(v >>> 56) & 0xFF);
        write((int)(v >>> 48) & 0xFF);
        write((int)(v >>> 40) & 0xFF);
        write((int)(v >>> 32) & 0xFF);
        write((int)(v >>> 24) & 0xFF);
        write((int)(v >>> 16) & 0xFF);
        write((int)(v >>>  8) & 0xFF);
        write((int)(v >>>  0) & 0xFF);
        //written += 8;
    }

    /**
     * Converts the float argument to an int using the
     * floatToIntBits method in class Float,
     * and then writes that int value to the file as a
     * four-byte quantity, high byte first. The write starts at the
     * current position of the file pointer.
     *
     * @param      v   a float value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.lang.Float#floatToIntBits(float)
     */
    public final void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    /**
     * Converts the double argument to a long using the doubleToLongBits method in class Double, and then writes that long value to the file as an
     * eight-byte quantity, high byte first. The write starts at the current position of the file pointer.
     *
     * @param      v   a double value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.lang.Double#doubleToLongBits(double)
     */
    public final void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /**
     * Writes the string to the file as a sequence of bytes. Each character in the string is written out, in sequence, by discarding its high eight
     * bits. The write starts at the current position of the file pointer.
     *
     * @param      s   a string of bytes to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeBytes(String s) throws IOException {
        int len = s.length();
        byte[] b = new byte[len];
        s.getBytes(0, len, b, 0);
        writeBytes(b, 0, len);
    }

    /**
     * Writes a string to the file as a sequence of characters. Each character is written to the data output stream as if by the writeChar method.
     * The write starts at the current position of the file pointer.
     *
     * @param      s   a String value to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.RandomAccessFile#writeChar(int)
     */
    public final void writeChars(String s) throws IOException {
        int clen = s.length();
        int blen = 2*clen;
        byte[] b = new byte[blen];
        char[] c = new char[clen];
        s.getChars(0, clen, c, 0);
        for (int i = 0, j = 0; i < clen; i++) {
            b[j++] = (byte)(c[i] >>> 8);
            b[j++] = (byte)(c[i] >>> 0);
        }
        writeBytes(b, 0, blen);
    }

    /**
     * Writes a string to the file using modified UTF-8 encoding in a machine-independent manner.
     * 
     * First, two bytes are written to the file, starting at the current file pointer, as if by the writeShort method giving the number of bytes
     * to follow. This value is the number of bytes actually written out, not the length of the string. Following the length, each character of
     * the string is output, in sequence, using the modified UTF-8 encoding for each character.
     *
     * @param      str   a string to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public final void writeUTF(String str) throws IOException {
        DataOutputStream.writeUTF(str, this);
    }

    private static native void initIDs();

    private native void close0() throws IOException;

    static {
        initIDs();
    }
}
