package pl.tomgirl.pylonfml;

import java.io.File;
import java.lang.reflect.Field;
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

        final List<IClassTransformer> transformers = (List<IClassTransformer>) type.getMethod("getTransformers").invoke(loader);
        runtime.setLwjglTransformer((name, bytes) -> {
            for (IClassTransformer transformer : transformers.toArray(new IClassTransformer[0])) {
                try {
                    byte[] result = transformer.transform(name, name, bytes);
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

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
