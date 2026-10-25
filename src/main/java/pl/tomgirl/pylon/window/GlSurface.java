package pl.tomgirl.pylon.window;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.sdl.SDLPlatform;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryUtil.memFree;

public class GlSurface implements GpuSurface {
    private final PixelFormat pixelFormat;
    private long window;
    private long context;
    private boolean glInitialized;
    private GLCapabilities capabilities;

    public GlSurface(PixelFormat pixelFormat) {
        this.pixelFormat = pixelFormat;
    }

    @Override
    public long createWindow(String title, int width, int height, boolean resizable) {
        if (window != 0) {
            throw new IllegalStateException("Surface has already been created");
        }

        MainThread.run(() -> createSdlWindow(title, width, height, resizable));

        try {
            context = check(SDL_GL_CreateContext(window));
            check(SDL_GL_LoadLibrary((ByteBuffer) null));
            Configuration.OPENGL_EXPLICIT_INIT.set(true);
            GL.create(SDLVideo::SDL_GL_GetProcAddress);
            glInitialized = true;
            capabilities = GL.createCapabilities(MemoryUtil::memCallocPointer);
            return window;
        } catch (RuntimeException | Error throwable) {
            destroy();
            throw throwable;
        }
    }

    private void createSdlWindow(String title, int width, int height, boolean resizable) {
        int properties = (int) check(SDL_CreateProperties());
        try {
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_X_NUMBER, SDL_WINDOWPOS_CENTERED));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_Y_NUMBER, SDL_WINDOWPOS_CENTERED));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, width));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, height));
            check(SDL_SetStringProperty(properties, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_OPENGL_BOOLEAN, true));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, resizable));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN, DisplaySdl.instance().isHighPixelDensity()));

            configureContext();
            window = check(SDL_CreateWindowWithProperties(properties));
        } finally {
            SDL_DestroyProperties(properties);
        }
    }

    private void configureContext() {
        if (!"macOS".equals(SDLPlatform.SDL_GetPlatform())) {
            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3));
            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 2));
            check(SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_COMPATIBILITY));
        }
        check(SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1));
        check(SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, pixelFormat.getDepthBits()));
        check(SDL_GL_SetAttribute(SDL_GL_STENCIL_SIZE, pixelFormat.getStencilBits()));
        check(SDL_GL_SetAttribute(SDL_GL_STEREO, pixelFormat.isStereo() ? 1 : 0));
    }

    @Override
    public void makeCurrent() {
        if ("macOS".equals(SDLPlatform.SDL_GetPlatform())
            && SDL_GL_GetCurrentContext() == context
            && SDL_GL_GetCurrentWindow() == window
            && GL11.glGetString(GL11.GL_VERSION) == null
        ) {
            check(SDL_GL_MakeCurrent(window, 0));
        }
        check(SDL_GL_MakeCurrent(window, context));
        GL.setCapabilities(capabilities);
    }

    @Override
    public void releaseCurrent() {
        check(SDL_GL_MakeCurrent(window, 0));
        GL.setCapabilities(null);
    }

    @Override
    public void swapBuffers() {
        check(SDL_GL_SwapWindow(window));
    }

    @Override
    public void setVSyncEnabled(boolean enabled) {
        if (context != 0) {
            check(SDL_GL_SetSwapInterval(enabled ? 1 : 0));
        }
    }

    @Override
    public void destroy() {
        if (capabilities != null) {
            memFree(capabilities.getAddressBuffer());
            GL.setCapabilities(null);
            capabilities = null;
        }
        if (glInitialized) {
            GL.destroy();
            glInitialized = false;
        }
        if (context != 0) {
            SDL_GL_DestroyContext(context);
            context = 0;
        }
        if (window != 0) {
            MainThread.run(() -> SDL_DestroyWindow(window));
            window = 0;
        }
    }

    private static void check(boolean success) {
        if (!success) {
            throw new IllegalStateException("SDL error encountered: " + SDL_GetError());
        }
    }

    private static long check(long pointer) {
        if (pointer == 0) {
            throw new IllegalStateException("SDL error encountered: " + SDL_GetError());
        }
        return pointer;
    }
}
