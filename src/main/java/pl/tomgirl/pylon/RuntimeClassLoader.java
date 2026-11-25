package pl.tomgirl.pylon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.function.BiFunction;

public final class RuntimeClassLoader extends URLClassLoader {
    public static final String[] PREFIXES = { "org.lwjgl.", "pl.tomgirl.pylon." };

    static {
        registerAsParallelCapable();
    }

    private volatile BiFunction<String, byte[], byte[]> lwjglTransformer;

    public RuntimeClassLoader(URL runtime, ClassLoader parent) {
        super(new URL[] { runtime }, parent);
    }

    public static URL location() {
        return RuntimeClassLoader.class.getProtectionDomain().getCodeSource().getLocation();
    }

    public static boolean isRuntimeClass(String name) {
        return name.startsWith(PREFIXES[0]) || name.startsWith(PREFIXES[1]);
    }

    public void setLwjglTransformer(BiFunction<String, byte[], byte[]> transformer) {
        lwjglTransformer = transformer;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!isRuntimeClass(name)) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException e) {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream in = resource.openStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
            }

            byte[] bytes = out.toByteArray();
            BiFunction<String, byte[], byte[]> transformer = lwjglTransformer;
            if (transformer != null && name.startsWith(PREFIXES[0])) {
                bytes = transformer.apply(name, bytes);
            }

            String pkg = name.substring(0, name.lastIndexOf('.'));
            if (getPackage(pkg) == null) {
                try {
                    definePackage(pkg, null, null, null, null, null, null, null);
                } catch (IllegalArgumentException ignored) {}
            }
            return defineClass(name, bytes, 0, bytes.length, new CodeSource(getURLs()[0], (CodeSigner[]) null));
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
