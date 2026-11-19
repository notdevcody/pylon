package pl.tomgirl.pylon.fabric;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.FabricLoaderImpl;

public class PreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if (!launchesThroughApplet()) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    private static boolean launchesThroughApplet() {
        try {
            return FabricLoaderImpl.INSTANCE.getGameProvider().getEntrypoint().contains("Applet");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
