package org.olexec.compile;

import ch.qos.logback.core.net.SyslogOutputStream;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringSourceCompiler {
    private static Map<String, JavaFileObject> fileObjectMap = new ConcurrentHashMap<>();

    /** 使用 Pattern 预编译功能 */
    private static Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s*");

    public static byte[] compile(String source, DiagnosticCollector<JavaFileObject> compileCollector) {
        //System.out.println(source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileManager javaFileManager =
                new TmpJavaFileManager(compiler.getStandardFileManager(compileCollector, null, null));

        // 从源码字符串中匹配类名
        Matcher matcher = CLASS_PATTERN.matcher(source);
        String className;
        if (matcher.find()) {
            className = matcher.group(1);
            //System.out.println(className);
            //运行的className为Run

        } else {
            throw new IllegalArgumentException("No valid class");
        }

        // 把源码字符串构造成JavaFileObject，供编译使用
        JavaFileObject sourceJavaFileObject = new TmpJavaFileObject(className, source);
        //System.out.println(sourceJavaFileObject);

        Boolean result = compiler.getTask(null, javaFileManager, compileCollector,
                null, null, Arrays.asList(sourceJavaFileObject)).call();

        JavaFileObject bytesJavaFileObject = fileObjectMap.get(className);
        if (result && bytesJavaFileObject != null) {
            return ((TmpJavaFileObject) bytesJavaFileObject).getCompiledBytes();
        }
        return null;
    }

    /**
     * 管理JavaFileObject对象的工具
     */
    public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        protected TmpJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForInput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind) throws IOException {
            JavaFileObject javaFileObject = fileObjectMap.get(className);
            if (javaFileObject == null) {
                return super.getJavaFileForInput(location, className, kind);
            }
            return javaFileObject;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            JavaFileObject javaFileObject = new TmpJavaFileObject(className, kind);
            fileObjectMap.put(className, javaFileObject);
            return javaFileObject;
        }
    }

    /**
     * 用来封装表示源码与字节码的对象
     */
    public static class TmpJavaFileObject extends SimpleJavaFileObject {
        private String source;
        private ByteArrayOutputStream outputStream;

        /**
         * 构造用来存储源代码的JavaFileObject
         * 需要传入源码source，然后调用父类的构造方·法创建kind = Kind.SOURCE的JavaFileObject对象
         *  1、先初始化父类，由于该URI是通过类名来完成的，必须以.java结尾。
         * 2、如果是一个真实的路径，比如是file:///test/demo/Hello.java则不需要特别加.java
         * 3、这里加的String:///并不是一个真正的URL的schema, 只是为了区分来源
         */
        public TmpJavaFileObject(String name, String source) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        /**
         * 构造用来存储字节码的JavaFileObject
         * 需要传入kind，即我们想要构建一个存储什么类型文件的JavaFileObject
         */
        public TmpJavaFileObject(String name, Kind kind) {
            super(URI.create("String:///" + name + Kind.SOURCE.extension), kind);
            this.source = null;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            if (source == null) {
                throw new IllegalArgumentException("source == null");
            }
            return source;
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            outputStream = new ByteArrayOutputStream();
            return outputStream;
        }

        public byte[] getCompiledBytes() {
            return outputStream.toByteArray();
        }
    }
}
