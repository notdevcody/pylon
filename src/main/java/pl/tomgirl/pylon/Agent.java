package pl.tomgirl.pylon;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class Agent {
    private Agent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (injectFabric()) {
            Pylon.LOG.log(Level.INFO, "Injected Fabric mod");
            return;
        }
        if (injectRelauncher(instrumentation)) {
            Pylon.LOG.log(Level.INFO, "Injected FML coremod");
            return;
        }

        try {
            prioritizeAgentJar(instrumentation);
        } catch (Exception e) {
            Pylon.LOG.log(Level.WARNING, "Couldn't move Pylon ahead of LWJGL 2 on the class path", e);
        }
        instrumentation.addTransformer(new pl.tomgirl.pylon.game.GameTransformer());

        if (ClassLoader.getSystemResource("net/minecraft/launchwrapper/LaunchClassLoader.class") != null) {
            preserveLaunchWrapperLwjglExclusion(instrumentation);
        }
    }

    private static void preserveLaunchWrapperLwjglExclusion(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytecode) {
                if (loader != null && loader.getClass().getName().equals("net.minecraft.launchwrapper.LaunchClassLoader")) {
                    instrumentation.removeTransformer(this);
                    try {
                        keepLwjglExcluded(loader);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        Pylon.LOG.log(Level.SEVERE, "Couldn't preserve LaunchWrapper's LWJGL exclusion", e);
                    }
                }
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void keepLwjglExcluded(ClassLoader loader) throws ReflectiveOperationException {
        String lwjglPrefix = "org.lwjgl.";
        Field field = loader.getClass().getDeclaredField("classLoaderExceptions");
        field.setAccessible(true);
        Set<String> exclusions = new HashSet<String>((Set<String>) field.get(loader)) {
            @Override
            public boolean remove(Object prefix) {
                return !lwjglPrefix.equals(prefix) && super.remove(prefix);
            }
        };
        exclusions.add(lwjglPrefix);
        field.set(loader, exclusions);
    }

    public static boolean injectFabric() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");

            File agentFile = new File(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            String addMods = System.getProperty("fabric.addMods", "");
            if (!"".equals(addMods)) {
                addMods += File.pathSeparator;
            }
            addMods += agentFile.getAbsolutePath();
            System.setProperty("fabric.addMods", addMods);

            return true;
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) {
                Pylon.LOG.log(Level.SEVERE, "Error injecting into Fabric");
            }
            return false;
        }
    }

    private static boolean injectRelauncher(Instrumentation instrumentation) {
        if (ClassLoader.getSystemResource(RelauncherTransformer.LIBRARY_MANAGER_CLASS + ".class") == null) {
            return false;
        }

        String coremods = System.getProperty(RelauncherTransformer.COREMODS_PROP, "");
        System.setProperty(RelauncherTransformer.COREMODS_PROP,
            (coremods.isEmpty() ? "" : coremods + ",") + RelauncherTransformer.COREMOD);
        instrumentation.addTransformer(new RelauncherTransformer(instrumentation));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void prioritizeAgentJar(Instrumentation instrumentation) throws Exception {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        Class<?> owner = URLClassLoader.class;
        try {
            Class<?> builtin = Class.forName("jdk.internal.loader.BuiltinClassLoader");
            if (builtin.isAssignableFrom(system.getClass())) {
                owner = builtin;
            }
        } catch (ClassNotFoundException ignored) {}

        Object ucp = field(instrumentation, owner, "ucp").get(system);
        List<Object> loaders = (List<Object>) field(instrumentation, ucp.getClass(), "loaders").get(ucp);
        Field base = field(instrumentation, Class.forName(ucp.getClass().getName() + "$Loader"), "base");
        String self = Agent.class.getProtectionDomain().getCodeSource().getLocation().toString();
        synchronized (ucp) {
            for (int i = 0; i < loaders.size(); i++) {
                if (String.valueOf(base.get(loaders.get(i))).contains(self)) {
                    loaders.add(0, loaders.remove(i));
                    return;
                }
            }
        }

        throw new IllegalStateException("Pylon isn't on the system classpath?");
    }

    private static Field field(Instrumentation instrumentation, Class<?> owner, String name) throws Exception {
        try {
            Method getModule = Class.class.getMethod("getModule");
            Object module = getModule.invoke(owner);
            Instrumentation.class.getMethod("redefineModule", module.getClass(), Set.class, Map.class, Map.class, Set.class, Map.class)
                .invoke(
                    instrumentation, module, Collections.emptySet(), Collections.emptyMap(),
                    Collections.singletonMap(owner.getPackage().getName(), Collections.singleton(getModule.invoke(Agent.class))),
                    Collections.emptySet(), Collections.emptyMap()
                );
        } catch (NoSuchMethodException ignored) {}

        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
