package pl.tomgirl.pylon;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.logging.Level;

public final class Agent {
    private Agent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (injectFabric()) {
            Pylon.LOG.log(Level.INFO, "Injected Fabric mod");
            return;
        }
        if (injectRelauncher()) {
            Pylon.LOG.log(Level.INFO, "Injected FML coremod");
            return;
        }

        instrumentation.addTransformer(createGameTransformer());
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

    private static boolean injectRelauncher() {
        if (ClassLoader.getSystemResource("cpw/mods/fml/relauncher/RelaunchLibraryManager.class") == null) {
            return false;
        }

        String coremods = System.getProperty("fml.coreMods.load", "");
        System.setProperty("fml.coreMods.load", (coremods.isEmpty() ? "" : coremods + ",") + "pl.tomgirl.pylonfml.CpwCoreMod");
        return true;
    }

    private static ClassFileTransformer createGameTransformer() {
        if ("1.8".equals(System.getProperty("java.specification.version"))) {
            try {
                ClassLoader system = ClassLoader.getSystemClassLoader();

                Field parent = ClassLoader.class.getDeclaredField("parent");
                parent.setAccessible(true);

                RuntimeClassLoader runtime = new RuntimeClassLoader(RuntimeClassLoader.location(), (ClassLoader) parent.get(system));
                parent.set(system, runtime);

                return (ClassFileTransformer) runtime.loadClass("pl.tomgirl.pylon.game.GameTransformer").newInstance();
            } catch (Exception e) {
                Pylon.LOG.log(Level.WARNING, "Couldn't install runtime class loader", e);
            }
        }
        return new pl.tomgirl.pylon.game.GameTransformer();
    }
}
