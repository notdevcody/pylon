package org.lwjgl;

import org.lwjgl.system.Pointer;
import pl.tomgirl.pylon.Platform;

@SuppressWarnings("unused")
public class Sys {
    private static final long timerOffset = System.nanoTime();
    public static final String VERSION = Version.getVersion();

    private Sys() {
    }

    public static long getTimerResolution() {
        return 1000000000;
    }

    public static long getTime() {
        return (System.nanoTime() - timerOffset) & 0x7FFFFFFFFFFFFFFFL;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static void initialize() {
    }

    public static boolean is64Bit() {
        return Pointer.BITS64;
    }

    public static boolean openURL(String url) {
        Platform.CURRENT.open(url);
        return true;
    }
}
