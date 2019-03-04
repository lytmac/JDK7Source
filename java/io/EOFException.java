/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.io;

/**
 * Signals that an end of file or end of stream has been reached unexpectedly during input.
 *
 * This exception is mainly used by data input streams to signal end of stream.
 *
 * 有很多的其他输入操作并不是抛出一个异常来标识输入结束，而是在流的末尾返回一个特殊值。
 * Note that many other input operations return a special value on end of stream rather than throwing an exception.
 *
 * @author Frank Yellin
 * @see java.io.DataInputStream
 * @see java.io.IOException
 * @since JDK1.0
 */
public class EOFException extends IOException {
    private static final long serialVersionUID = 6433858223774886977L;

    /**
     * Constructs an EOFException with null as its error detail message.
     */
    public EOFException() {
        super();
    }

    /**
     * Constructs an EOFException with the specified detail message.
     * The string s may later be retrieved by the java.lang.Throwable.getMessage method of class java.lang.Throwable.
     *
     * @param s the detail message.
     */
    public EOFException(String s) {
        super(s);
    }
}
