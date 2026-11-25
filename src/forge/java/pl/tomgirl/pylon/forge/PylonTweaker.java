package pl.tomgirl.pylon.forge;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import pl.tomgirl.pylon.RuntimeClassLoader;

@SuppressWarnings("unused")
public final class PylonTweaker implements ITweaker {
    private LaunchClassLoader classLoader;

    @Override
    public void acceptOptions(List<String> arguments, File gameDirectory, File assetsDirectory, String profile) {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        this.classLoader = classLoader;
        try {
            Field parent = classLoader.getClass().getDeclaredField("parent");
            parent.setAccessible(true);
            ClassLoader launcherParent = (ClassLoader) parent.get(classLoader);
            RuntimeClassLoader runtimeLoader = new RuntimeClassLoader(RuntimeClassLoader.location(), launcherParent);
            parent.set(classLoader, runtimeLoader);
            if (System.getProperty("os.name", "").startsWith("Mac")) {
                Class<?> display = runtimeLoader.loadClass("pl.tomgirl.pylon.window.DisplaySdl");
                Object instance = display.getMethod("instance").invoke(null);
                display.getMethod("initializeVideo").invoke(instance);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to install the runtime class loader", e);
        }

        classLoader.addClassLoaderExclusion("pl.tomgirl.pylon.");
        classLoader.addTransformerExclusion("pl.tomgirl.pylonfml.");
        classLoader.registerTransformer(PylonTransformer.class.getName());
    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        classLoader.addClassLoaderExclusion("org.lwjgl.");
        OldFmlCompat.prepare(classLoader);
        return new String[0];
    }
}
