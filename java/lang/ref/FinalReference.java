/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package java.lang.ref;


/**
 * Final references, used to implement finalization
 * 需要格外注意的是，这个类的访问权限是默认的private，也就是说这个类不是给外部代码用的。
 */

class FinalReference<T> extends Reference<T> {

    public FinalReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

}
