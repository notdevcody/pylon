package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.util.logging.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.LWJGLException;
import pl.tomgirl.pylon.Pylon;
import pl.tomgirl.pylon.window.GlSurface;
import pl.tomgirl.pylon.window.DisplaySdl;

@SuppressWarnings("unused")
public class Display {
    private static final DisplaySdl SDL = DisplaySdl.instance();

    @NotNull
    public static String getTitle() {
        return SDL.getTitle();
    }

    public static void setTitle(@NotNull String title) {
        SDL.setTitle(title);
    }

    @NotNull
    public static DisplayMode getDisplayMode() {
        return SDL.getDisplayMode();
    }

    public static void setDisplayMode(@NotNull DisplayMode mode) {
        SDL.setDisplayMode(mode);
    }

    public static int getX() {
        return SDL.getX();
    }

    public static int getY() {
        return SDL.getY();
    }

    public static int getWidth() {
        return SDL.getWidth();
    }

    public static int getHeight() {
        return SDL.getHeight();
    }

    @Nullable
    public static DisplayMode getDesktopDisplayMode() {
        return SDL.getDesktopDisplayMode();
    }

    public static int setIcon(@NotNull ByteBuffer[] icons) {
        return SDL.setIcon(icons);
    }

    public static void update() {
        SDL.update();
    }

    public static void processMessages() {
        SDL.processMessages();
    }

    public static void create() throws LWJGLException {
        create(new PixelFormat());
    }

    public static void create(@NotNull PixelFormat pixelFormat) throws LWJGLException {
        try {
            SDL.create(new GlSurface(pixelFormat));
        } catch (RuntimeException e) {
            Pylon.LOG.log(Level.SEVERE, "Failed to create display", e);
            throw new LWJGLException(e);
        }
    }

    public static boolean isFullscreen() {
        return SDL.isFullscreen();
    }

    public static void setFullscreen(boolean fullscreen) {
        SDL.setFullscreen(fullscreen);
    }

    @NotNull
    public static DisplayMode[] getAvailableDisplayModes() {
        return SDL.getAvailableDisplayModes();
    }

    public static void destroy() {
        SDL.destroy();
    }

    public static boolean isCreated() {
        return SDL.isCreated();
    }

    public static boolean isCurrent() {
        try {
            return SDL.getDrawable().isCurrent();
        } catch (LWJGLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isActive() {
        return SDL.isActive();
    }

    public static void setResizable(boolean isResizable) {
        SDL.setResizable(isResizable);
    }

    public static void sync(int fps) {
        Sync.sync(fps);
    }

    public static void setVSyncEnabled(boolean enabled) {
        SDL.setVSyncEnabled(enabled);
    }

    public static void setLocation(int x, int y) {
        SDL.setLocation(x, y);
    }

    public static boolean wasResized() {
        return SDL.wasResized();
    }

    public static boolean isVisible() {
        return SDL.isVisible();
    }

    public static void makeCurrent() {
        SDL.makeCurrent();
    }

    public static Drawable getDrawable() {
        return SDL.getDrawable();
    }

    public static boolean isCloseRequested() {
        return SDL.isCloseRequested();
    }

    public static void swapBuffers() {
        SDL.swapBuffers();
    }

    public static void setParent(java.awt.Canvas o) {}

    public static void setDisplayConfiguration(float gamma, float brightness, float contrast) {}
}
