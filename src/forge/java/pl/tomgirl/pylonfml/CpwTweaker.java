package pl.tomgirl.pylonfml;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.IFMLCallHook;
import pl.tomgirl.pylon.RuntimeClassLoader;
import pl.tomgirl.pylon.forge.OldFmlCompat;

@SuppressWarnings({"unused", "unchecked"})
public final class CpwTweaker implements IFMLCallHook {
    private ClassLoader loader;

    @Override
    public void injectData(Map<String, Object> data) {
        loader = (ClassLoader) data.get("classLoader");
    }

    @Override
    public Void call() throws Exception {
        Class<?> type = loader.getClass();
        Field parent = field(type, "parent");
        RuntimeClassLoader runtime = new RuntimeClassLoader(RuntimeClassLoader.location(), (ClassLoader) parent.get(loader));
        parent.set(loader, runtime);

        final List<String> prefixes = Arrays.asList(RuntimeClassLoader.PREFIXES);
        Field exceptions = field(type, "classLoaderExceptions");
        Set<String> pinned = new HashSet<String>((Set<String>) exceptions.get(loader)) {
            @Override
            public boolean remove(Object prefix) {
                return !prefixes.contains(prefix) && super.remove(prefix);
            }
        };
        pinned.addAll(prefixes);
        exceptions.set(loader, pinned);

        OldFmlCompat.prepare(loader);
        ((List<String>)
            Class.forName("cpw.mods.fml.relauncher.RelaunchLibraryManager", false, loader)
                .getMethod("getLibraries")
                .invoke(null)
        ).add(new File(RuntimeClassLoader.location().toURI()).getName());

        Method transform = transformerMethod();
        boolean includesTransformedName = transform.getParameterCount() == 3;
        List<?> transformers = (List<?>) type.getMethod("getTransformers").invoke(loader);
        runtime.setLwjglTransformer((name, bytes) -> {
            Object[] arguments = includesTransformedName ? new Object[] {name, name, bytes} : new Object[] {name, bytes};
            for (Object transformer : transformers.toArray()) {
                try {
                    arguments[arguments.length - 1] = bytes;
                    byte[] result = (byte[]) transform.invoke(transformer, arguments);
                    if (result != null) bytes = result;
                } catch (Throwable ignored) {
                }
            }
            return bytes;
        });
        type.getMethod("registerTransformer", String.class).invoke(loader, CpwTransformer.class.getName());

        if (System.getProperty("os.name", "").startsWith("Mac")) {
            Class<?> display = runtime.loadClass("pl.tomgirl.pylon.window.DisplaySdl");
            display.getMethod("initializeVideo").invoke(display.getMethod("instance").invoke(null));
        }
        return null;
    }

    private static Method transformerMethod() throws NoSuchMethodException {
        try {
            return IClassTransformer.class.getMethod("transform", String.class, String.class, byte[].class);
        } catch (NoSuchMethodException e) {
            return IClassTransformer.class.getMethod("transform", String.class, byte[].class);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
