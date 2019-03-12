/*
 * Copyright (c) 1994, 2011, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
package java.lang;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.Map;
import java.util.Vector;
import java.util.Hashtable;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.ClassFileTransformer;
import sun.misc.CompoundEnumeration;
import sun.misc.Resource;
import sun.misc.URLClassPath;
import sun.misc.VM;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.reflect.misc.ReflectUtil;
import sun.security.util.SecurityConstants;

/**
 * 类加载器负责定位Class文件，并试图生成构成类定义的数据，即Class对象。
 * A class loader is an object that is responsible for loading classes. The class ClassLoader is an abstract class. Given the binary name
 * of a class, a class loader should attempt to locate or generate data that constitutes a definition for the class. A typical strategy is
 * to transform the name into a file name and then read a "class file" of that name from a file system.
 *
 * 每个类的Class对象都包含一个对定义该类的classLoader的引用，可以通过成员函数getClassLoader()方法获取。
 * Every Class Class object contains a method Class#getClassLoader() reference to the ClassLoader that defined it.
 *
 * 数组类的Class对象不是由类加载器创建的，而是由JVM根据需要自动创建。数组类etClassLoader()返回的类加载器与其元素的类加载器一致，如果元素是基础类型，则该数组没有类加载器。
 * Class objects for array classes are not created by class loaders, but are created automatically as required by the Java runtime. The class
 * loader for an array class, as returned by Class#getClassLoader() is the same as the class loader for its element type; if the element type
 * is a primitive type, then the array class has no class loader.
 *
 * 应用程序需要实现ClassLoader的子类，以自定义JVM动态加载类的方式。
 * Applications implement subclasses of ClassLoader in order to extend the manner in which the Java virtual machine dynamically loads classes.
 *
 * 类加载器通常由安全管理器使用，用于指示安全域???
 * Class loaders may typically be used by security managers to indicate security domains.
 *
 * ClassLoader使用委托模型来搜索类和资源。每个ClassLoader对象都必须关联一个父类加载器，需要查找类或资源时，ClassLoader实例会在亲自查找类或资源前，将搜索委托给父类加载器。
 * The ClassLoader class uses a delegation model to search for classes and resources. Each instance of ClassLoader has an associated parent class
 * loader. When requested to find a class or resource, a ClassLoader instance will delegate the search for the class or resource to its parent
 * class loader before attempting to find the class or resource itself.
 *
 * JVM内建的ClassLoader(bootstrap class loader)没有父类，但是可以作为所有ClassLoader实例的父类
 * The virtual machine's built-in class loader, called the "bootstrap class loader", does not itself have a parent but may serve as the parent of
 * a ClassLoader instance.
 *
 * ClassLoader对象如果要支持并发地加载class文件，需要在该ClassLoader类初始化时调用静态方法registerAsParallelCapable()对自己进行注册。ClassLoader类默认是已注册有并发
 * 能力的，但是其子类仍需要进行注册。
 * Class loaders that support concurrent loading of classes are known as parallel capable class loaders and are required to register themselves at
 * their class initialization time by invoking the ClassLoader.registerAsParallelCapable method. Note that the ClassLoader class is registered as
 * parallel capable by default. However, its subclasses still need to register themselves if they are parallel capable.
 *
 * 在委托模型不是严格分层的环境中，ClassLoader需要具有并行能力，否则类加载会导致死锁，因为加载器锁在类加载过程的持续时间内保持（参见loadClass方法）。
 * In environments in which the delegation model is not strictly hierarchical, class loaders need to be parallel capable, otherwise class loading
 * can lead to deadlocks because the loader lock is held for the duration of the class loading process (see loadClass methods).
 *
 * Normally, the JVM loads classes from the local file system in a platform-dependent manner. For example, on UNIX systems, the JVM loads classes
 * from the directory defined by the CLASSPATH environment variable. However, some classes may not originate from a file; they may originate from
 * other sources, such as the network, or they could be constructed by an application.
 *
 * 成员方法defineClass()将字节数组转换成Class对象实例。通过类的Class对象，可以调用Class.newInstance()获取该类的对象实例。
 * The method defineClass(String, byte[], int, int) defineClass converts an array of bytes into an instance of class Class. Instances of this newly
 * defined class can be created using Class.newInstance.
 *
 * The methods and constructors of objects created by a class loader may reference other classes. To determine the class(es) referred to, the JVM
 * invokes the loadClass method of the class loader that originally created the class.
 *
 * For example, an application could create a network class loader to download class files from a server. Sample code might look like:
 *
     *   ClassLoader loader = new NetworkClassLoader(host, port);
     *   Object main = loader.loadClass("Main", true).newInstance();
 *
 * The network class loader subclass must define the methods findClass and loadClassData to load a class from the network. Once it has downloaded the
 * bytes that make up the class, it should use the method defineClass to create a class instance. A sample implementation is:
 *
     *     class NetworkClassLoader extends ClassLoader {
     *         String host;
     *         int port;
     *
     *         public Class findClass(String name) {
     *             byte[] b = loadClassData(name);
     *             return defineClass(name, b, 0, b.length);
     *         }
     *
     *         private byte[] loadClassData(String name) {
     *             // load the class data from the connection
     *         }
     *     }
 *
 * Binary names
 *
 * Any class name provided as a String parameter to methods in ClassLoader must be a binary name as defined by <<The Java Language Specification>>.
 *
 * Examples of valid class names include:
 * "java.lang.String"
 * "javax.swing.JSpinner$DefaultEditor"
 * "java.security.KeyStore$Builder$FileBuilder$1"
 * "java.net.URLClassLoader$3$1"
 *
 *
 * @see      #resolveClass(Class)
 * @since 1.0
 */
public abstract class ClassLoader {

    private static native void registerNatives();
    static {
        registerNatives();
    }

    // The parent class loader for delegation：如果要实现父类加载器委派模型(见loadClass())，这里要设置父类。而不是采用继承某个类加载器的方式。
    // Note: VM hardcoded the offset of this field, thus all new fields must be added *after* it.
    private final ClassLoader parent;

    /**
     * Encapsulates the set of parallel capable loader types.
     */
    private static class ParallelLoaders {
        private ParallelLoaders() {}

        // the set of parallel capable loader types
        private static final Set<Class<? extends ClassLoader>> loaderTypes =
                Collections.newSetFromMap(new WeakHashMap<Class<? extends ClassLoader>, Boolean>());

        static {
            synchronized (loaderTypes) { loaderTypes.add(ClassLoader.class); }
        }

        /**
         * Registers the given class loader type as parallel capabale.
         * Returns true is successfully registered; false if loader's super class is not registered.
         */
        static boolean register(Class<? extends ClassLoader> c) {
            synchronized (loaderTypes) {
                if (loaderTypes.contains(c.getSuperclass())) {
                    // register the class loader as parallel capable if and only if all of its super classes are.
                    // Note: given current classloading sequence, if  the immediate super class is parallel capable, all the super classes
                    // higher up must be too.
                    loaderTypes.add(c);
                    return true;
                } else {
                    return false;
                }
            }
        }

        /**
         * Returns true if the given class loader type is registered as parallel capable.
         */
        static boolean isRegistered(Class<? extends ClassLoader> c) {
            synchronized (loaderTypes) {
                return loaderTypes.contains(c);
            }
        }
    }

    // Maps class name to the corresponding lock object when the current class loader is parallel capable.
    // Note: VM also uses this field to decide if the current class loader is parallel capable and the appropriate lock object for class loading.
    private final ConcurrentHashMap<String, Object> parallelLockMap;

    // Hashtable that maps packages to certs
    private final Map <String, Certificate[]> package2certs;

    // Shared among all packages with unsigned classes
    private static final Certificate[] nocerts = new Certificate[0];

    // The classes loaded by this class loader. The only purpose of this table is to keep the classes from being GC'ed until the loader is GC'ed.
    private final Vector<Class<?>> classes = new Vector<>();

    // The "default" domain. Set as the default ProtectionDomain on newly created classes.
    private final ProtectionDomain defaultDomain = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null, this, null);

    // The initiating protection domains for all classes loaded by this loader
    private final Set<ProtectionDomain> domains;

    // Invoked by the VM to record every loaded class with this loader.
    void addClass(Class c) {
        classes.addElement(c);
    }

    // The packages defined in this class loader. Each package name is mapped to its corresponding Package object.
    // @GuardedBy("itself")
    private final HashMap<String, Package> packages = new HashMap<>();

    private static Void checkCreateClassLoader() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        return null;
    }

    private ClassLoader(Void unused, ClassLoader parent) {
        this.parent = parent;
        if (ParallelLoaders.isRegistered(this.getClass())) {
            parallelLockMap = new ConcurrentHashMap<>();
            package2certs = new ConcurrentHashMap<>();
            domains =
                Collections.synchronizedSet(new HashSet<ProtectionDomain>());
            assertionLock = new Object();
        } else {
            // no finer-grained lock; lock on the classloader instance
            parallelLockMap = null;
            package2certs = new Hashtable<>();
            domains = new HashSet<>();
            assertionLock = this;
        }
    }

    /**
     * Creates a new class loader using the specified parent class loader for delegation.
     *
     * If there is a security manager, its SecurityManager.checkCreateClassLoader() method is invoked. This may result in a security exception.
     *
     * @param  parent The parent class loader
     *
     * @throws  SecurityException If a security manager exists and its checkCreateClassLoader method doesn't allow creation of a new class loader.
     *
     * @since  1.2
     */
    protected ClassLoader(ClassLoader parent) {
        this(checkCreateClassLoader(), parent);
    }

    /**
     * Creates a new class loader using the ClassLoader returned by the method getSystemClassLoader() as the parent class loader.
     *
     * If there is a security manager, its SecurityManager#checkCreateClassLoader() method is invoked. This may result in a security exception.
     *
     * @throws  SecurityException If a security manager exists and its checkCreateClassLoader method doesn't allow creation of a new class loader.
     */
    protected ClassLoader() {
        this(checkCreateClassLoader(), getSystemClassLoader());
    }

    // -- Class --

    /**
     * Loads the class with the specified binary name. This method searches for classes in the same manner as the loadClass(String, boolean) method.
     * It is invoked by the Java virtual machine to resolve class references. Invoking this method is equivalent to invoking loadClass(name, false).
     *
     * @param  name The binary name of the class
     *
     * @return  The resulting Class object
     *
     * @throws  ClassNotFoundException If the class was not found
     */
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /**
     * Loads the class with the specified binary name. The default implementation of this method searches for classes in the following order:
     *
     *   1. Invoke findLoadedClass(String) method to check if the class has already been loaded.
     *
     *   2. Invoke the loadClass(String) method on the parent class loader. If the parent is null the class loader built-in to the virtual machine
     *   is used, instead.
     *
     *   3. Invoke the findClass(String) method to find the class.
     *
     * If the class was found using the above steps, and the resolve flag is true, this method will then invoke the resolveClass(Class) method on
     * the resulting Class object.
     *
     * Subclasses of ClassLoader are encouraged to override findClass(String), rather than this method.
     *
     * Unless overridden, this method synchronizes on the result of getClassLoadingLock() method during the entire class loading process.
     *
     * @param  name The binary name of the class
     *
     * @param  resolve If true then resolve the class
     *
     * @return  The resulting Class object
     *
     * @throws  ClassNotFoundException If the class could not be found
     */
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) { //这里要做同步
            // First, check if the class has already been loaded
            Class c = findLoadedClass(name);
            if (c == null) { //该类还未加载
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {
                        c = parent.loadClass(name, false); //委托给父类加载器进行类加载
                    } else {
                        //如果没有明确定义父类加载器，则通过JVM内建的bootstrap class loader进行类加载
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order to find the class.
                    long t1 = System.nanoTime();
                    c = findClass(name); //抽象方法，交给子类去实现该方法，以对默认的加载行为进行扩展。

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /**
     * Returns the lock object for class loading operations. For backward compatibility, the default implementation of this method behaves
     * as follows. If this ClassLoader object is registered as parallel capable, the method returns a dedicated object associated with the
     * specified class name. Otherwise, the method returns this ClassLoader object.
     *
     * @param  className The name of the to-be-loaded class
     *
     * @return the lock for class loading operations
     *
     * @throws NullPointerException If registered as parallel capable and className is null
     *
     * @see #loadClass(String, boolean)
     *
     * @since  1.7
     */
    protected Object getClassLoadingLock(String className) {
        Object lock = this;
        if (parallelLockMap != null) {
            Object newLock = new Object();
            lock = parallelLockMap.putIfAbsent(className, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }
        return lock;
    }

    // This method is invoked by the virtual machine to load a class.
    private Class loadClassInternal(String name)
        throws ClassNotFoundException
    {
        // For backward compatibility, explicitly lock on 'this' when the current class loader is not parallel capable.
        if (parallelLockMap == null) {
            synchronized (this) {
                 return loadClass(name);
            }
        } else {
            return loadClass(name);
        }
    }

    // Invoked by the VM after loading class with this loader.
    private void checkPackageAccess(Class cls, ProtectionDomain pd) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (ReflectUtil.isNonPublicProxyClass(cls)) {
                for (Class intf: cls.getInterfaces()) {
                    checkPackageAccess(intf, pd);
                }
                return;
            }

            final String name = cls.getName();
            final int i = name.lastIndexOf('.');
            if (i != -1) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        sm.checkPackageAccess(name.substring(0, i));
                        return null;
                    }
                }, new AccessControlContext(new ProtectionDomain[] {pd}));
            }
        }
        domains.add(pd);
    }

    /**
     * Finds the class with the specified binary name. This method should be overridden by class loader implementations that follow the
     * delegation model for loading classes, and will be invoked by the loadClass method after checking the parent class loader for the
     * requested class. The default implementation throws a ClassNotFoundException.
     *
     * @param  name The binary name of the class
     *
     * @return  The resulting Class object
     *
     * @throws  ClassNotFoundException If the class could not be found
     *
     * @since  1.2
     */
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    /**
     * Converts an array of bytes into an instance of class Class. Before the Class can be used it must be resolved. This method is deprecated
     * in favor of the version that takes a binary name as its first argument, and is more secure.
     *
     * @param  b The bytes that make up the class data. The bytes in positions off through off+len-1 should have the format of a valid class file
     *          as defined by <<The Java Virtual Machine Specification>>.
     *
     * @param  off The start offset in b of the class data
     *
     * @param  len The length of the class data
     *
     * @return  The Class object that was created from the specified class data
     *
     * @throws  ClassFormatError If the data did not contain a valid class
     *
     * @throws  IndexOutOfBoundsException If either off or len is negative, or if off+len is greater than b.length.
     *
     * @throws  SecurityException If an attempt is made to add this class to a package that contains classes that were signed by a different set
     *          of certificates than this class, or if an attempt is made to define a class in a package with a fully-qualified name that starts
     *          with "java".
     *
     * @see  #loadClass(String, boolean)
     * @see  #resolveClass(Class)
     *
     * @deprecated  Replaced by #defineClass(String, byte[], int, int)
     * defineClass(String, byte[], int, int)}
     */
    @Deprecated
    //final修饰表明该方法不能再被重写了。因为没有别的计算方式将字节码转换成Class对象了。
    protected final Class<?> defineClass(byte[] b, int off, int len) throws ClassFormatError {
        return defineClass(null, b, off, len, null);
    }

    /**
     * Converts an array of bytes into an instance of class Class. Before the Class can be used it must be resolved.
     *
     * This method assigns a default java.security.ProtectionDomain to the newly defined class. The ProtectionDomain is effectively granted the
     * same set of permissions returned when java.security.Policy.getPolicy().getPermissions(new CodeSource(null, null)) is invoked. The default
     * domain is created on the first invocation of defineClass(String, byte[], int, int) defineClass, and re-used on subsequent invocations.
     *
     * To assign a specific ProtectionDomain to the class, use the defineClass method that takes a ProtectionDomain as one of its arguments.
     *
     * @param  name The expected binary name of the class, or null if not known
     *
     * @param  b The bytes that make up the class data. The bytes in positions off through off+len-1 should have the format of a valid class
     *           file as defined by <<The Java Virtual Machine Specification>>.
     *
     * @param  off The start offset in b of the class data
     *
     * @param  len The length of the class data
     *
     * @return  The Class object that was created from the specified class data.
     *
     * @throws  ClassFormatError If the data did not contain a valid class
     *
     * @throws  IndexOutOfBoundsException If either off or len is negative, or if off+len is greater than b.length.
     *
     * @throws  SecurityException If an attempt is made to add this class to a package that contains classes that were signed by a different
     *                            set of certificates than this class (which is unsigned), or if name begins with "java".
     *
     * @see  #loadClass(String, boolean)
     * @see  #resolveClass(Class)
     * @see  java.security.CodeSource
     * @see  java.security.SecureClassLoader
     *
     * @since  1.1
     */
    protected final Class<?> defineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
        return defineClass(name, b, off, len, null);
    }

    /* Determine protection domain, and check that:
        - not define java.* class,
        - signer of this class matches signers for the rest of the classes in package.
    */
    private ProtectionDomain preDefineClass(String name, ProtectionDomain pd) {
        if (!checkName(name))
            throw new NoClassDefFoundError("IllegalName: " + name);

        if ((name != null) && name.startsWith("java.")) {
            throw new SecurityException
                ("Prohibited package name: " +
                 name.substring(0, name.lastIndexOf('.')));
        }
        if (pd == null) {
            pd = defaultDomain;
        }

        if (name != null) checkCerts(name, pd.getCodeSource());

        return pd;
    }

    private String defineClassSourceLocation(ProtectionDomain pd)
    {
        CodeSource cs = pd.getCodeSource();
        String source = null;
        if (cs != null && cs.getLocation() != null) {
            source = cs.getLocation().toString();
        }
        return source;
    }

    private Class defineTransformedClass(String name, byte[] b, int off, int len,
                                         ProtectionDomain pd,
                                         ClassFormatError cfe, String source)
      throws ClassFormatError
    {
        // Class format error - try to transform the bytecode and define the class again
        ClassFileTransformer[] transformers =
            ClassFileTransformer.getTransformers();
        Class c = null;

        if (transformers != null) {
            for (ClassFileTransformer transformer : transformers) {
                try {
                    // Transform byte code using transformer
                    byte[] tb = transformer.transform(b, off, len);
                    c = defineClass1(name, tb, 0, tb.length,
                                     pd, source);
                    break;
                } catch (ClassFormatError cfe2)     {
                    // If ClassFormatError occurs, try next transformer
                }
            }
        }

        // Rethrow original ClassFormatError if unable to transform
        // bytecode to well-formed
        //
        if (c == null)
            throw cfe;

        return c;
    }

    private void postDefineClass(Class c, ProtectionDomain pd)
    {
        if (pd.getCodeSource() != null) {
            Certificate certs[] = pd.getCodeSource().getCertificates();
            if (certs != null)
                setSigners(c, certs);
        }
    }

    /**
     * Converts an array of bytes into an instance of class Class,
     * with an optional ProtectionDomain.  If the domain is
     * null, then a default domain will be assigned to the class as
     * specified in the documentation for #defineClass(String, byte[],
     * int, int)}.  Before the class can be used it must be resolved.
     *
     * The first class defined in a package determines the exact set of
     * certificates that all subsequent classes defined in that package must
     * contain.  The set of certificates for a class is obtained from the
     * java.security.CodeSource CodeSource} within the
     * ProtectionDomain of the class.  Any classes added to that
     * package must contain the same set of certificates or a
     * SecurityException will be thrown.  Note that if
     * name is null, this check is not performed.
     * You should always pass in the binary name of the
     * class you are defining as well as the bytes.  This ensures that the
     * class you are defining is indeed the class you think it is.
     *
     * The specified name cannot begin with "java.", since
     * all classes in the "java.* packages can only be defined by the
     * bootstrap class loader.  If name is not null, it
     * must be equal to the binary name of the class
     * specified by the byte array "b", otherwise a 
     * NoClassDefFoundError} will be thrown.  </p>
     *
     * @param  name
     *         The expected binary name of the class, or
     *         null if not known
     *
     * @param  b
     *         The bytes that make up the class data. The bytes in positions
     *         off through off+len-1 should have the format
     *         of a valid class file as defined by
     *         <cite>The Java&trade; Virtual Machine Specification</cite>.
     *
     * @param  off
     *         The start offset in b of the class data
     *
     * @param  len
     *         The length of the class data
     *
     * @param  protectionDomain
     *         The ProtectionDomain of the class
     *
     * @return  The Class object created from the data,
     *          and optional ProtectionDomain.
     *
     * @throws  ClassFormatError
     *          If the data did not contain a valid class
     *
     * @throws  NoClassDefFoundError
     *          If name is not equal to the binary
     *          name of the class specified by b
     *
     * @throws  IndexOutOfBoundsException
     *          If either off or len is negative, or if
     *          off+len is greater than b.length.
     *
     * @throws  SecurityException
     *          If an attempt is made to add this class to a package that
     *          contains classes that were signed by a different set of
     *          certificates than this class, or if name begins with
     *          "java.".
     */
    protected final Class<?> defineClass(String name, byte[] b, int off, int len,
                                         ProtectionDomain protectionDomain)
        throws ClassFormatError
    {
        protectionDomain = preDefineClass(name, protectionDomain);

        Class c = null;
        String source = defineClassSourceLocation(protectionDomain);

        try {
            c = defineClass1(name, b, off, len, protectionDomain, source);
        } catch (ClassFormatError cfe) {
            c = defineTransformedClass(name, b, off, len, protectionDomain, cfe,
                                       source);
        }

        postDefineClass(c, protectionDomain);
        return c;
    }

    /**
     * Converts a java.nio.ByteBuffer ByteBuffer}
     * into an instance of class Class,
     * with an optional ProtectionDomain.  If the domain is
     * null, then a default domain will be assigned to the class as
     * specified in the documentation for #defineClass(String, byte[],
     * int, int)}.  Before the class can be used it must be resolved.
     *
     * <p>The rules about the first class defined in a package determining the
     * set of certificates for the package, and the restrictions on class names
     * are identical to those specified in the documentation for 
     * #defineClass(String, byte[], int, int, ProtectionDomain)}.
     *
     * An invocation of this method of the form
     * <i>cl</i>.defineClass(<i>name</i>,
     * <i>bBuffer</i>, <i>pd</i>) yields exactly the same
     * result as the statements
     *
     * <blockquote>
     * ...<br>
     * byte[] temp = new byte[<i>bBuffer</i>.
     * java.nio.ByteBuffer#remaining remaining}()];<br>
     *     <i>bBuffer</i>.java.nio.ByteBuffer#get(byte[])
     * get}(temp);<br>
     *     return #defineClass(String, byte[], int, int, ProtectionDomain)
     * <i>cl</i>.defineClass}(<i>name</i>, temp, 0,
     * temp.length, <i>pd</i>);<br>
     * </blockquote>
     *
     * @param  name
     *         The expected binary name. of the class, or
     *         null if not known
     *
     * @param  b
     *         The bytes that make up the class data. The bytes from positions
     *         b.position() through b.position() + b.limit() -1
     *          should have the format of a valid class file as defined by
     *         <cite>The Java&trade; Virtual Machine Specification</cite>.
     *
     * @param  protectionDomain
     *         The ProtectionDomain of the class, or null.
     *
     * @return  The Class object created from the data,
     *          and optional ProtectionDomain.
     *
     * @throws  ClassFormatError
     *          If the data did not contain a valid class.
     *
     * @throws  NoClassDefFoundError
     *          If name is not equal to the binary
     *          name of the class specified by b
     *
     * @throws  SecurityException
     *          If an attempt is made to add this class to a package that
     *          contains classes that were signed by a different set of
     *          certificates than this class, or if name begins with
     *          "java.".
     *
     * @see      #defineClass(String, byte[], int, int, ProtectionDomain)
     *
     * @since  1.5
     */
    protected final Class<?> defineClass(String name, java.nio.ByteBuffer b,
                                         ProtectionDomain protectionDomain)
        throws ClassFormatError
    {
        int len = b.remaining();

        // Use byte[] if not a direct ByteBufer:
        if (!b.isDirect()) {
            if (b.hasArray()) {
                return defineClass(name, b.array(),
                                   b.position() + b.arrayOffset(), len,
                                   protectionDomain);
            } else {
                // no array, or read-only array
                byte[] tb = new byte[len];
                b.get(tb);  // get bytes out of byte buffer.
                return defineClass(name, tb, 0, len, protectionDomain);
            }
        }

        protectionDomain = preDefineClass(name, protectionDomain);

        Class c = null;
        String source = defineClassSourceLocation(protectionDomain);

        try {
            c = defineClass2(name, b, b.position(), len, protectionDomain,
                             source);
        } catch (ClassFormatError cfe) {
            byte[] tb = new byte[len];
            b.get(tb);  // get bytes out of byte buffer.
            c = defineTransformedClass(name, tb, 0, len, protectionDomain, cfe,
                                       source);
        }

        postDefineClass(c, protectionDomain);
        return c;
    }

    private native Class defineClass0(String name, byte[] b, int off, int len,
                                      ProtectionDomain pd);

    private native Class defineClass1(String name, byte[] b, int off, int len,
                                      ProtectionDomain pd, String source);

    private native Class defineClass2(String name, java.nio.ByteBuffer b,
                                      int off, int len, ProtectionDomain pd,
                                      String source);

    // true if the name is null or has the potential to be a valid binary name
    private boolean checkName(String name) {
        if ((name == null) || (name.length() == 0))
            return true;
        if ((name.indexOf('/') != -1)
            || (!VM.allowArraySyntax() && (name.charAt(0) == '[')))
            return false;
        return true;
    }

    private void checkCerts(String name, CodeSource cs) {
        int i = name.lastIndexOf('.');
        String pname = (i == -1) ? "" : name.substring(0, i);

        Certificate[] certs = null;
        if (cs != null) {
            certs = cs.getCertificates();
        }
        Certificate[] pcerts = null;
        if (parallelLockMap == null) {
            synchronized (this) {
                pcerts = package2certs.get(pname);
                if (pcerts == null) {
                    package2certs.put(pname, (certs == null? nocerts:certs));
                }
            }
        } else {
            pcerts = ((ConcurrentHashMap<String, Certificate[]>)package2certs).
                putIfAbsent(pname, (certs == null? nocerts:certs));
        }
        if (pcerts != null && !compareCerts(pcerts, certs)) {
            throw new SecurityException("class \""+ name +
                 "\"'s signer information does not match signer information of other classes in the same package");
        }
    }

    /**
     * check to make sure the certs for the new class (certs) are the same as
     * the certs for the first class inserted in the package (pcerts)
     */
    private boolean compareCerts(Certificate[] pcerts,
                                 Certificate[] certs)
    {
        // certs can be null, indicating no certs.
        if ((certs == null) || (certs.length == 0)) {
            return pcerts.length == 0;
        }

        // the length must be the same at this point
        if (certs.length != pcerts.length)
            return false;

        // go through and make sure all the certs in one array
        // are in the other and vice-versa.
        boolean match;
        for (int i = 0; i < certs.length; i++) {
            match = false;
            for (int j = 0; j < pcerts.length; j++) {
                if (certs[i].equals(pcerts[j])) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }

        // now do the same for pcerts
        for (int i = 0; i < pcerts.length; i++) {
            match = false;
            for (int j = 0; j < certs.length; j++) {
                if (pcerts[i].equals(certs[j])) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }

        return true;
    }

    /**
     * Links the specified class.  This (misleadingly named) method may be
     * used by a class loader to link a class.  If the class c has
     * already been linked, then this method simply returns. Otherwise, the
     * class is linked as described in the "Execution" chapter of
     * <cite>The Java&trade; Language Specification</cite>.
     * </p>
     *
     * @param  c
     *         The class to link
     *
     * @throws  NullPointerException
     *          If c is null.
     *
     * @see  #defineClass(String, byte[], int, int)
     */
    protected final void resolveClass(Class<?> c) {
        resolveClass0(c);
    }

    private native void resolveClass0(Class c);

    /**
     * Finds a class with the specified binary name, loading it if necessary.
     *
     * This method loads the class through the system class loader (see getSystemClassLoader()). The Class object returned might have more than
     * one ClassLoader associated with it. Subclasses of ClassLoader need not usually invoke this method, because most class loaders need to
     * override just findClass(String).
     *
     * @param  name The binary name of the class
     *
     * @return  The Class object for the specified name
     *
     * @throws  ClassNotFoundException If the class could not be found
     *
     * @see  #ClassLoader(ClassLoader)
     * @see  #getParent()
     */
    protected final Class<?> findSystemClass(String name) throws ClassNotFoundException {
        ClassLoader system = getSystemClassLoader();
        if (system == null) {
            if (!checkName(name))
                throw new ClassNotFoundException(name);
            Class cls = findBootstrapClass(name);
            if (cls == null) {
                throw new ClassNotFoundException(name);
            }
            return cls;
        }
        return system.loadClass(name);
    }

    /**
     * Returns a class loaded by the bootstrap class loader;
     * or return null if not found.
     */
    private Class findBootstrapClassOrNull(String name)
    {
        if (!checkName(name)) return null;

        return findBootstrapClass(name);
    }

    // return null if not found
    private native Class findBootstrapClass(String name);

    /**
     * Returns the class with the given binary name if this
     * loader has been recorded by the Java virtual machine as an initiating
     * loader of a class with that binary name.  Otherwise
     * null is returned.  </p>
     *
     * @param  name
     *         The binary name of the class
     *
     * @return  The Class object, or null if the class has
     *          not been loaded
     *
     * @since  1.1
     */
    protected final Class<?> findLoadedClass(String name) {
        if (!checkName(name))
            return null;
        return findLoadedClass0(name);
    }

    private native final Class findLoadedClass0(String name);

    /**
     * Sets the signers of a class.  This should be invoked after defining a
     * class.  </p>
     *
     * @param  c
     *         The Class object
     *
     * @param  signers
     *         The signers for the class
     *
     * @since  1.1
     */
    protected final void setSigners(Class<?> c, Object[] signers) {
        c.setSigners(signers);
    }


    // -- Resource --

    /**
     * Finds the resource with the given name.  A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.
     *
     * The name of a resource is a '/'-separated path name that
     * identifies the resource.
     *
     * This method will first search the parent class loader for the
     * resource; if the parent is null the path of the class loader
     * built-in to the virtual machine is searched.  That failing, this method
     * will invoke #findResource(String)} to find the resource.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  A URL object for reading the resource, or
     *          null if the resource could not be found or the invoker
     *          doesn't have adequate  privileges to get the resource.
     *
     * @since  1.1
     */
    public URL getResource(String name) {
        URL url;
        if (parent != null) {
            url = parent.getResource(name);
        } else {
            url = getBootstrapResource(name);
        }
        if (url == null) {
            url = findResource(name);
        }
        return url;
    }

    /**
     * Finds all the resources with the given name. A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.
     *
     * <p>The name of a resource is a /-separated path name that
     * identifies the resource.
     *
     * The search order is described in the documentation for 
     * #getResource(String)}.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An enumeration of java.net.URL URL} objects for
     *          the resource.  If no resources could  be found, the enumeration
     *          will be empty.  Resources that the class loader doesn't have
     *          access to will not be in the enumeration.
     *
     * @throws  IOException
     *          If I/O errors occur
     *
     * @see  #findResources(String)
     *
     * @since  1.2
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration[] tmp = new Enumeration[2];
        if (parent != null) {
            tmp[0] = parent.getResources(name);
        } else {
            tmp[0] = getBootstrapResources(name);
        }
        tmp[1] = findResources(name);

        return new CompoundEnumeration<>(tmp);
    }

    /**
     * Finds the resource with the given name. Class loader implementations
     * should override this method to specify where to find resources.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  A URL object for reading the resource, or
     *          null if the resource could not be found
     *
     * @since  1.2
     */
    protected URL findResource(String name) {
        return null;
    }

    /**
     * Returns an enumeration of java.net.URL URL} objects
     * representing all the resources with the given name. Class loader
     * implementations should override this method to specify where to load
     * resources from.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An enumeration of java.net.URL URL} objects for
     *          the resources
     *
     * @throws  IOException
     *          If I/O errors occur
     *
     * @since  1.2
     */
    protected Enumeration<URL> findResources(String name) throws IOException {
        return java.util.Collections.emptyEnumeration();
    }

    /**
     * Registers the caller as parallel capable.</p>
     * The registration succeeds if and only if all of the following
     * conditions are met: <br>
     * 1. no instance of the caller has been created</p>
     * 2. all of the super classes (except class Object) of the caller are
     * registered as parallel capable</p>
     * Note that once a class loader is registered as parallel capable, there
     * is no way to change it back. </p>
     *
     * @return  true if the caller is successfully registered as
     *          parallel capable and false if otherwise.
     *
     * @since   1.7
     */
    @CallerSensitive
    protected static boolean registerAsParallelCapable() {
        Class<? extends ClassLoader> callerClass =
            Reflection.getCallerClass().asSubclass(ClassLoader.class);
        return ParallelLoaders.register(callerClass);
    }

    /**
     * Find a resource of the specified name from the search path used to load
     * classes.  This method locates the resource through the system class
     * loader (see #getSystemClassLoader()}).  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  A java.net.URL URL} object for reading the
     *          resource, or null if the resource could not be found
     *
     * @since  1.1
     */
    public static URL getSystemResource(String name) {
        ClassLoader system = getSystemClassLoader();
        if (system == null) {
            return getBootstrapResource(name);
        }
        return system.getResource(name);
    }

    /**
     * Finds all resources of the specified name from the search path used to
     * load classes.  The resources thus found are returned as an
     * java.util.Enumeration Enumeration} of 
     * java.net.URL URL} objects.
     *
     * The search order is described in the documentation for 
     * #getSystemResource(String)}.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An enumeration of resource java.net.URL URL}
     *          objects
     *
     * @throws  IOException
     *          If I/O errors occur

     * @since  1.2
     */
    public static Enumeration<URL> getSystemResources(String name)
        throws IOException
    {
        ClassLoader system = getSystemClassLoader();
        if (system == null) {
            return getBootstrapResources(name);
        }
        return system.getResources(name);
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static URL getBootstrapResource(String name) {
        URLClassPath ucp = getBootstrapClassPath();
        Resource res = ucp.getResource(name);
        return res != null ? res.getURL() : null;
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static Enumeration<URL> getBootstrapResources(String name)
        throws IOException
    {
        final Enumeration<Resource> e =
            getBootstrapClassPath().getResources(name);
        return new Enumeration<URL> () {
            public URL nextElement() {
                return e.nextElement().getURL();
            }
            public boolean hasMoreElements() {
                return e.hasMoreElements();
            }
        };
    }

    // Returns the URLClassPath that is used for finding system resources.
    static URLClassPath getBootstrapClassPath() {
        return sun.misc.Launcher.getBootstrapClassPath();
    }


    /**
     * Returns an input stream for reading the specified resource.
     *
     * The search order is described in the documentation for 
     * #getResource(String)}.  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An input stream for reading the resource, or null
     *          if the resource could not be found
     *
     * @since  1.1
     */
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Open for reading, a resource of the specified name from the search path
     * used to load classes.  This method locates the resource through the
     * system class loader (see #getSystemClassLoader()}).  </p>
     *
     * @param  name
     *         The resource name
     *
     * @return  An input stream for reading the resource, or null
     *          if the resource could not be found
     *
     * @since  1.1
     */
    public static InputStream getSystemResourceAsStream(String name) {
        URL url = getSystemResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }


    // -- Hierarchy --

    /**
     * Returns the parent class loader for delegation. Some implementations may
     * use null to represent the bootstrap class loader. This method
     * will return null in such implementations if this class loader's
     * parent is the bootstrap class loader.
     *
     * If a security manager is present, and the invoker's class loader is
     * not null and is not an ancestor of this class loader, then this
     * method invokes the security manager's 
     * SecurityManager#checkPermission(java.security.Permission)
     * checkPermission} method with a 
     * RuntimePermission#RuntimePermission(String)
     * RuntimePermission("getClassLoader")} permission to verify
     * access to the parent class loader is permitted.  If not, a
     * SecurityException will be thrown.  </p>
     *
     * @return  The parent ClassLoader
     *
     * @throws  SecurityException
     *          If a security manager exists and its checkPermission
     *          method doesn't allow access to this class loader's parent class
     *          loader.
     *
     * @since  1.2
     */
    @CallerSensitive
    public final ClassLoader getParent() {
        if (parent == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkClassLoaderPermission(parent, Reflection.getCallerClass());
        }
        return parent;
    }

    /**
     * Returns the system class loader for delegation.  This is the default
     * delegation parent for new ClassLoader instances, and is
     * typically the class loader used to start the application.
     *
     * This method is first invoked early in the runtime's startup
     * sequence, at which point it creates the system class loader and sets it
     * as the context class loader of the invoking Thread.
     *
     * The default system class loader is an implementation-dependent
     * instance of this class.
     *
     * If the system property "java.system.class.loader" is defined
     * when this method is first invoked then the value of that property is
     * taken to be the name of a class that will be returned as the system
     * class loader.  The class is loaded using the default system class loader
     * and must define a public constructor that takes a single parameter of
     * type ClassLoader which is used as the delegation parent.  An
     * instance is then created using this constructor with the default system
     * class loader as the parameter.  The resulting class loader is defined
     * to be the system class loader.
     *
     * If a security manager is present, and the invoker's class loader is
     * not null and the invoker's class loader is not the same as or
     * an ancestor of the system class loader, then this method invokes the
     * security manager's 
     * SecurityManager#checkPermission(java.security.Permission)
     * checkPermission} method with a 
     * RuntimePermission#RuntimePermission(String)
     * RuntimePermission("getClassLoader")} permission to verify
     * access to the system class loader.  If not, a
     * SecurityException will be thrown.  </p>
     *
     * @return  The system ClassLoader for delegation, or
     *          null if none
     *
     * @throws  SecurityException
     *          If a security manager exists and its checkPermission
     *          method doesn't allow access to the system class loader.
     *
     * @throws  IllegalStateException
     *          If invoked recursively during the construction of the class
     *          loader specified by the "java.system.class.loader"
     *          property.
     *
     * @throws  Error
     *          If the system property "java.system.class.loader"
     *          is defined but the named class could not be loaded, the
     *          provider class does not define the required constructor, or an
     *          exception is thrown by that constructor when it is invoked. The
     *          underlying cause of the error can be retrieved via the
     *          Throwable#getCause()} method.
     *
     * @revised  1.4
     */
    @CallerSensitive
    public static ClassLoader getSystemClassLoader() {
        initSystemClassLoader();
        if (scl == null) {
            return null;
        }
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkClassLoaderPermission(scl, Reflection.getCallerClass());
        }
        return scl;
    }

    private static synchronized void initSystemClassLoader() {
        if (!sclSet) {
            if (scl != null)
                throw new IllegalStateException("recursive invocation");
            sun.misc.Launcher l = sun.misc.Launcher.getLauncher();
            if (l != null) {
                Throwable oops = null;
                scl = l.getClassLoader();
                try {
                    scl = AccessController.doPrivileged(
                        new SystemClassLoaderAction(scl));
                } catch (PrivilegedActionException pae) {
                    oops = pae.getCause();
                    if (oops instanceof InvocationTargetException) {
                        oops = oops.getCause();
                    }
                }
                if (oops != null) {
                    if (oops instanceof Error) {
                        throw (Error) oops;
                    } else {
                        // wrap the exception
                        throw new Error(oops);
                    }
                }
            }
            sclSet = true;
        }
    }

    // Returns true if the specified class loader can be found in this class
    // loader's delegation chain.
    boolean isAncestor(ClassLoader cl) {
        ClassLoader acl = this;
        do {
            acl = acl.parent;
            if (cl == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }

    // Tests if class loader access requires "getClassLoader" permission
    // check.  A class loader 'from' can access class loader 'to' if
    // class loader 'from' is same as class loader 'to' or an ancestor
    // of 'to'.  The class loader in a system domain can access
    // any class loader.
    private static boolean needsClassLoaderPermissionCheck(ClassLoader from,
                                                           ClassLoader to)
    {
        if (from == to)
            return false;

        if (from == null)
            return false;

        return !to.isAncestor(from);
    }

    // Returns the class's class loader, or null if none.
    static ClassLoader getClassLoader(Class<?> caller) {
        // This can be null if the VM is requesting it
        if (caller == null) {
            return null;
        }
        // Circumvent security check since this is package-private
        return caller.getClassLoader0();
    }

    static void checkClassLoaderPermission(ClassLoader cl, Class<?> caller) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            // caller can be null if the VM is requesting it
            ClassLoader ccl = getClassLoader(caller);
            if (needsClassLoaderPermissionCheck(ccl, cl)) {
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
            }
        }
    }

    // The class loader for the system
    // @GuardedBy("ClassLoader.class")
    private static ClassLoader scl;

    // Set to true once the system class loader has been set
    // @GuardedBy("ClassLoader.class")
    private static boolean sclSet;


    // -- Package --

    /**
     * Defines a package by name in this ClassLoader.  This allows
     * class loaders to define the packages for their classes. Packages must
     * be created before the class is defined, and package names must be
     * unique within a class loader and cannot be redefined or changed once
     * created.  </p>
     *
     * @param  name
     *         The package name
     *
     * @param  specTitle
     *         The specification title
     *
     * @param  specVersion
     *         The specification version
     *
     * @param  specVendor
     *         The specification vendor
     *
     * @param  implTitle
     *         The implementation title
     *
     * @param  implVersion
     *         The implementation version
     *
     * @param  implVendor
     *         The implementation vendor
     *
     * @param  sealBase
     *         If not null, then this package is sealed with
     *         respect to the given code source java.net.URL
     *         URL}  object.  Otherwise, the package is not sealed.
     *
     * @return  The newly defined Package object
     *
     * @throws  IllegalArgumentException
     *          If package name duplicates an existing package either in this
     *          class loader or one of its ancestors
     *
     * @since  1.2
     */
    protected Package definePackage(String name, String specTitle,
                                    String specVersion, String specVendor,
                                    String implTitle, String implVersion,
                                    String implVendor, URL sealBase)
        throws IllegalArgumentException
    {
        synchronized (packages) {
            Package pkg = getPackage(name);
            if (pkg != null) {
                throw new IllegalArgumentException(name);
            }
            pkg = new Package(name, specTitle, specVersion, specVendor,
                              implTitle, implVersion, implVendor,
                              sealBase, this);
            packages.put(name, pkg);
            return pkg;
        }
    }

    /**
     * Returns a Package that has been defined by this class loader
     * or any of its ancestors.  </p>
     *
     * @param  name
     *         The package name
     *
     * @return  The Package corresponding to the given name, or
     *          null if not found
     *
     * @since  1.2
     */
    protected Package getPackage(String name) {
        Package pkg;
        synchronized (packages) {
            pkg = packages.get(name);
        }
        if (pkg == null) {
            if (parent != null) {
                pkg = parent.getPackage(name);
            } else {
                pkg = Package.getSystemPackage(name);
            }
            if (pkg != null) {
                synchronized (packages) {
                    Package pkg2 = packages.get(name);
                    if (pkg2 == null) {
                        packages.put(name, pkg);
                    } else {
                        pkg = pkg2;
                    }
                }
            }
        }
        return pkg;
    }

    /**
     * Returns all of the Packages defined by this class loader and
     * its ancestors.  </p>
     *
     * @return  The array of Package objects defined by this
     *          ClassLoader
     *
     * @since  1.2
     */
    protected Package[] getPackages() {
        Map<String, Package> map;
        synchronized (packages) {
            map = new HashMap<>(packages);
        }
        Package[] pkgs;
        if (parent != null) {
            pkgs = parent.getPackages();
        } else {
            pkgs = Package.getSystemPackages();
        }
        if (pkgs != null) {
            for (int i = 0; i < pkgs.length; i++) {
                String pkgName = pkgs[i].getName();
                if (map.get(pkgName) == null) {
                    map.put(pkgName, pkgs[i]);
                }
            }
        }
        return map.values().toArray(new Package[map.size()]);
    }


    // -- Native library access --

    /**
     * Returns the absolute path name of a native library.  The VM invokes this
     * method to locate the native libraries that belong to classes loaded with
     * this class loader. If this method returns null, the VM
     * searches the library along the path specified as the
     * "java.library.path" property.  </p>
     *
     * @param  libname
     *         The library name
     *
     * @return  The absolute path of the native library
     *
     * @see  System#loadLibrary(String)
     * @see  System#mapLibraryName(String)
     *
     * @since  1.2
     */
    protected String findLibrary(String libname) {
        return null;
    }

    /**
     * The inner class NativeLibrary denotes a loaded native library instance.
     * Every classloader contains a vector of loaded native libraries in the
     * private field nativeLibraries.  The native libraries loaded
     * into the system are entered into the systemNativeLibraries
     * vector.
     *
     * Every native library requires a particular version of JNI. This is
     * denoted by the private jniVersion field.  This field is set by
     * the VM when it loads the library, and used by the VM to pass the correct
     * version of JNI to the native methods.  </p>
     *
     * @see      ClassLoader
     * @since    1.2
     */
    static class NativeLibrary {
        // opaque handle to native library, used in native code.
        long handle;
        // the version of JNI environment the native library requires.
        private int jniVersion;
        // the class from which the library is loaded, also indicates
        // the loader this native library belongs.
        private Class fromClass;
        // the canonicalized name of the native library.
        String name;

        native void load(String name);
        native long find(String name);
        native void unload();

        public NativeLibrary(Class fromClass, String name) {
            this.name = name;
            this.fromClass = fromClass;
        }

        protected void finalize() {
            synchronized (loadedLibraryNames) {
                if (fromClass.getClassLoader() != null && handle != 0) {
                    /* remove the native library name */
                    int size = loadedLibraryNames.size();
                    for (int i = 0; i < size; i++) {
                        if (name.equals(loadedLibraryNames.elementAt(i))) {
                            loadedLibraryNames.removeElementAt(i);
                            break;
                        }
                    }
                    /* unload the library. */
                    ClassLoader.nativeLibraryContext.push(this);
                    try {
                        unload();
                    } finally {
                        ClassLoader.nativeLibraryContext.pop();
                    }
                }
            }
        }
        // Invoked in the VM to determine the context class in
        // JNI_Load/JNI_Unload
        static Class getFromClass() {
            return ClassLoader.nativeLibraryContext.peek().fromClass;
        }
    }

    // All native library names we've loaded.
    private static Vector<String> loadedLibraryNames = new Vector<>();

    // Native libraries belonging to system classes.
    private static Vector<NativeLibrary> systemNativeLibraries
        = new Vector<>();

    // Native libraries associated with the class loader.
    private Vector<NativeLibrary> nativeLibraries = new Vector<>();

    // native libraries being loaded/unloaded.
    private static Stack<NativeLibrary> nativeLibraryContext = new Stack<>();

    // The paths searched for libraries
    private static String usr_paths[];
    private static String sys_paths[];

    private static String[] initializePath(String propname) {
        String ldpath = System.getProperty(propname, "");
        String ps = File.pathSeparator;
        int ldlen = ldpath.length();
        int i, j, n;
        // Count the separators in the path
        i = ldpath.indexOf(ps);
        n = 0;
        while (i >= 0) {
            n++;
            i = ldpath.indexOf(ps, i + 1);
        }

        // allocate the array of paths - n :'s = n + 1 path elements
        String[] paths = new String[n + 1];

        // Fill the array with paths from the ldpath
        n = i = 0;
        j = ldpath.indexOf(ps);
        while (j >= 0) {
            if (j - i > 0) {
                paths[n++] = ldpath.substring(i, j);
            } else if (j - i == 0) {
                paths[n++] = ".";
            }
            i = j + 1;
            j = ldpath.indexOf(ps, i);
        }
        paths[n] = ldpath.substring(i, ldlen);
        return paths;
    }

    // Invoked in the java.lang.Runtime class to implement load and loadLibrary.
    static void loadLibrary(Class fromClass, String name,
                            boolean isAbsolute) {
        ClassLoader loader =
            (fromClass == null) ? null : fromClass.getClassLoader();
        if (sys_paths == null) {
            usr_paths = initializePath("java.library.path");
            sys_paths = initializePath("sun.boot.library.path");
        }
        if (isAbsolute) {
            if (loadLibrary0(fromClass, new File(name))) {
                return;
            }
            throw new UnsatisfiedLinkError("Can't load library: " + name);
        }
        if (loader != null) {
            String libfilename = loader.findLibrary(name);
            if (libfilename != null) {
                File libfile = new File(libfilename);
                if (!libfile.isAbsolute()) {
                    throw new UnsatisfiedLinkError(
    "ClassLoader.findLibrary failed to return an absolute path: " + libfilename);
                }
                if (loadLibrary0(fromClass, libfile)) {
                    return;
                }
                throw new UnsatisfiedLinkError("Can't load " + libfilename);
            }
        }
        for (int i = 0 ; i < sys_paths.length ; i++) {
            File libfile = new File(sys_paths[i], System.mapLibraryName(name));
            if (loadLibrary0(fromClass, libfile)) {
                return;
            }
        }
        if (loader != null) {
            for (int i = 0 ; i < usr_paths.length ; i++) {
                File libfile = new File(usr_paths[i],
                                        System.mapLibraryName(name));
                if (loadLibrary0(fromClass, libfile)) {
                    return;
                }
            }
        }
        // Oops, it failed
        throw new UnsatisfiedLinkError("no " + name + " in java.library.path");
    }

    private static boolean loadLibrary0(Class fromClass, final File file) {
        if (loadLibrary1(fromClass, file)) {
            return true;
        }
        final File libfile = ClassLoaderHelper.mapAlternativeName(file);
        if (libfile != null && loadLibrary1(fromClass, libfile)) {
            return true;
        }
        return false;
    }

    private static boolean loadLibrary1(Class fromClass, final File file) {
        boolean exists = AccessController.doPrivileged(
            new PrivilegedAction<Object>() {
                public Object run() {
                    return file.exists() ? Boolean.TRUE : null;
                }})
            != null;
        if (!exists) {
            return false;
        }
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (IOException e) {
            return false;
        }
        ClassLoader loader =
            (fromClass == null) ? null : fromClass.getClassLoader();
        Vector<NativeLibrary> libs =
            loader != null ? loader.nativeLibraries : systemNativeLibraries;
        synchronized (libs) {
            int size = libs.size();
            for (int i = 0; i < size; i++) {
                NativeLibrary lib = libs.elementAt(i);
                if (name.equals(lib.name)) {
                    return true;
                }
            }

            synchronized (loadedLibraryNames) {
                if (loadedLibraryNames.contains(name)) {
                    throw new UnsatisfiedLinkError
                        ("Native Library " +
                         name +
                         " already loaded in another classloader");
                }
                /* If the library is being loaded (must be by the same thread,
                 * because Runtime.load and Runtime.loadLibrary are
                 * synchronous). The reason is can occur is that the JNI_OnLoad
                 * function can cause another loadLibrary invocation.
                 *
                 * Thus we can use a static stack to hold the list of libraries
                 * we are loading.
                 *
                 * If there is a pending load operation for the library, we
                 * immediately return success; otherwise, we raise
                 * UnsatisfiedLinkError.
                 */
                int n = nativeLibraryContext.size();
                for (int i = 0; i < n; i++) {
                    NativeLibrary lib = nativeLibraryContext.elementAt(i);
                    if (name.equals(lib.name)) {
                        if (loader == lib.fromClass.getClassLoader()) {
                            return true;
                        } else {
                            throw new UnsatisfiedLinkError
                                ("Native Library " +
                                 name +
                                 " is being loaded in another classloader");
                        }
                    }
                }
                NativeLibrary lib = new NativeLibrary(fromClass, name);
                nativeLibraryContext.push(lib);
                try {
                    lib.load(name);
                } finally {
                    nativeLibraryContext.pop();
                }
                if (lib.handle != 0) {
                    loadedLibraryNames.addElement(name);
                    libs.addElement(lib);
                    return true;
                }
                return false;
            }
        }
    }

    // Invoked in the VM class linking code.
    static long findNative(ClassLoader loader, String name) {
        Vector<NativeLibrary> libs =
            loader != null ? loader.nativeLibraries : systemNativeLibraries;
        synchronized (libs) {
            int size = libs.size();
            for (int i = 0; i < size; i++) {
                NativeLibrary lib = libs.elementAt(i);
                long entry = lib.find(name);
                if (entry != 0)
                    return entry;
            }
        }
        return 0;
    }


    // -- Assertion management --

    final Object assertionLock;

    // The default toggle for assertion checking.
    // @GuardedBy("assertionLock")
    private boolean defaultAssertionStatus = false;

    // Maps String packageName to Boolean package default assertion status Note
    // that the default package is placed under a null map key.  If this field
    // is null then we are delegating assertion status queries to the VM, i.e.,
    // none of this ClassLoader's assertion status modification methods have
    // been invoked.
    // @GuardedBy("assertionLock")
    private Map<String, Boolean> packageAssertionStatus = null;

    // Maps String fullyQualifiedClassName to Boolean assertionStatus If this
    // field is null then we are delegating assertion status queries to the VM,
    // i.e., none of this ClassLoader's assertion status modification methods
    // have been invoked.
    // @GuardedBy("assertionLock")
    Map<String, Boolean> classAssertionStatus = null;

    /**
     * Sets the default assertion status for this class loader.  This setting
     * determines whether classes loaded by this class loader and initialized
     * in the future will have assertions enabled or disabled by default.
     * This setting may be overridden on a per-package or per-class basis by
     * invoking #setPackageAssertionStatus(String, boolean)} or 
     * #setClassAssertionStatus(String, boolean)}.  </p>
     *
     * @param  enabled
     *         true if classes loaded by this class loader will
     *         henceforth have assertions enabled by default, false
     *         if they will have assertions disabled by default.
     *
     * @since  1.4
     */
    public void setDefaultAssertionStatus(boolean enabled) {
        synchronized (assertionLock) {
            if (classAssertionStatus == null)
                initializeJavaAssertionMaps();

            defaultAssertionStatus = enabled;
        }
    }

    /**
     * Sets the package default assertion status for the named package.  The
     * package default assertion status determines the assertion status for
     * classes initialized in the future that belong to the named package or
     * any of its "subpackages".
     *
     * A subpackage of a package named p is any package whose name begins
     * with "p.".  For example, javax.swing.text is a
     * subpackage of javax.swing, and both java.util and
     * java.lang.reflect are subpackages of java.
     *
     * In the event that multiple package defaults apply to a given class,
     * the package default pertaining to the most specific package takes
     * precedence over the others.  For example, if javax.lang and
     * javax.lang.reflect both have package defaults associated with
     * them, the latter package default applies to classes in
     * javax.lang.reflect.
     *
     * Package defaults take precedence over the class loader's default
     * assertion status, and may be overridden on a per-class basis by invoking
     * #setClassAssertionStatus(String, boolean)}.  </p>
     *
     * @param  packageName
     *         The name of the package whose package default assertion status
     *         is to be set. A null value indicates the unnamed
     *         package that is "current"
     *         (see section 7.4.2 of
     *         <cite>The Java&trade; Language Specification</cite>.)
     *
     * @param  enabled
     *         true if classes loaded by this classloader and
     *         belonging to the named package or any of its subpackages will
     *         have assertions enabled by default, false if they will
     *         have assertions disabled by default.
     *
     * @since  1.4
     */
    public void setPackageAssertionStatus(String packageName,
                                          boolean enabled) {
        synchronized (assertionLock) {
            if (packageAssertionStatus == null)
                initializeJavaAssertionMaps();

            packageAssertionStatus.put(packageName, enabled);
        }
    }

    /**
     * Sets the desired assertion status for the named top-level class in this
     * class loader and any nested classes contained therein.  This setting
     * takes precedence over the class loader's default assertion status, and
     * over any applicable per-package default.  This method has no effect if
     * the named class has already been initialized.  (Once a class is
     * initialized, its assertion status cannot change.)
     *
     * If the named class is not a top-level class, this invocation will
     * have no effect on the actual assertion status of any class. </p>
     *
     * @param  className
     *         The fully qualified class name of the top-level class whose
     *         assertion status is to be set.
     *
     * @param  enabled
     *         true if the named class is to have assertions
     *         enabled when (and if) it is initialized, false if the
     *         class is to have assertions disabled.
     *
     * @since  1.4
     */
    public void setClassAssertionStatus(String className, boolean enabled) {
        synchronized (assertionLock) {
            if (classAssertionStatus == null)
                initializeJavaAssertionMaps();

            classAssertionStatus.put(className, enabled);
        }
    }

    /**
     * Sets the default assertion status for this class loader to
     * false and discards any package defaults or class assertion
     * status settings associated with the class loader.  This method is
     * provided so that class loaders can be made to ignore any command line or
     * persistent assertion status settings and "start with a clean slate."
     * </p>
     *
     * @since  1.4
     */
    public void clearAssertionStatus() {
        /*
         * Whether or not "Java assertion maps" are initialized, set
         * them to empty maps, effectively ignoring any present settings.
         */
        synchronized (assertionLock) {
            classAssertionStatus = new HashMap<>();
            packageAssertionStatus = new HashMap<>();
            defaultAssertionStatus = false;
        }
    }

    /**
     * Returns the assertion status that would be assigned to the specified
     * class if it were to be initialized at the time this method is invoked.
     * If the named class has had its assertion status set, the most recent
     * setting will be returned; otherwise, if any package default assertion
     * status pertains to this class, the most recent setting for the most
     * specific pertinent package default assertion status is returned;
     * otherwise, this class loader's default assertion status is returned.
     * </p>
     *
     * @param  className
     *         The fully qualified class name of the class whose desired
     *         assertion status is being queried.
     *
     * @return  The desired assertion status of the specified class.
     *
     * @see  #setClassAssertionStatus(String, boolean)
     * @see  #setPackageAssertionStatus(String, boolean)
     * @see  #setDefaultAssertionStatus(boolean)
     *
     * @since  1.4
     */
    boolean desiredAssertionStatus(String className) {
        synchronized (assertionLock) {
            // assert classAssertionStatus   != null;
            // assert packageAssertionStatus != null;

            // Check for a class entry
            Boolean result = classAssertionStatus.get(className);
            if (result != null)
                return result.booleanValue();

            // Check for most specific package entry
            int dotIndex = className.lastIndexOf(".");
            if (dotIndex < 0) { // default package
                result = packageAssertionStatus.get(null);
                if (result != null)
                    return result.booleanValue();
            }
            while(dotIndex > 0) {
                className = className.substring(0, dotIndex);
                result = packageAssertionStatus.get(className);
                if (result != null)
                    return result.booleanValue();
                dotIndex = className.lastIndexOf(".", dotIndex-1);
            }

            // Return the classloader default
            return defaultAssertionStatus;
        }
    }

    // Set up the assertions with information provided by the VM.
    // Note: Should only be called inside a synchronized block
    private void initializeJavaAssertionMaps() {
        // assert Thread.holdsLock(assertionLock);

        classAssertionStatus = new HashMap<>();
        packageAssertionStatus = new HashMap<>();
        AssertionStatusDirectives directives = retrieveDirectives();

        for(int i = 0; i < directives.classes.length; i++)
            classAssertionStatus.put(directives.classes[i],
                                     directives.classEnabled[i]);

        for(int i = 0; i < directives.packages.length; i++)
            packageAssertionStatus.put(directives.packages[i],
                                       directives.packageEnabled[i]);

        defaultAssertionStatus = directives.deflt;
    }

    // Retrieves the assertion directives from the VM.
    private static native AssertionStatusDirectives retrieveDirectives();
}


class SystemClassLoaderAction
    implements PrivilegedExceptionAction<ClassLoader> {
    private ClassLoader parent;

    SystemClassLoaderAction(ClassLoader parent) {
        this.parent = parent;
    }

    public ClassLoader run() throws Exception {
        String cls = System.getProperty("java.system.class.loader");
        if (cls == null) {
            return parent;
        }

        Constructor ctor = Class.forName(cls, true, parent)
            .getDeclaredConstructor(new Class[] { ClassLoader.class });
        ClassLoader sys = (ClassLoader) ctor.newInstance(
            new Object[] { parent });
        Thread.currentThread().setContextClassLoader(sys);
        return sys;
    }
}
