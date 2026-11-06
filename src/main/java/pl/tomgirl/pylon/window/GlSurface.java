package pl.tomgirl.pylon.window;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.sdl.SDLPlatform;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
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
    private boolean glLibraryLoaded;
    private GLCapabilities capabilities;

    public GlSurface(PixelFormat pixelFormat) {
        this.pixelFormat = pixelFormat;
    }

    @Override
    public long createWindow(String title, int width, int height, boolean resizable) {
        if (window != 0) {
            throw new IllegalStateException("Surface has already been created");
        }

        try {
            MainThread.run(() -> createSdlWindow(title, width, height, resizable));
            context = check(SDL_GL_CreateContext(window));
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
        String libraryName = Configuration.OPENGL_LIBRARY_NAME.get();
        if (libraryName != null) {
            check(SDL_GL_LoadLibrary(libraryName));
            glLibraryLoaded = true;
        }
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
        if (pixelFormat.getAlphaBits() == 0 && capabilities != null) {
            fillAlpha();
        }
        check(SDL_GL_SwapWindow(window));
    }

    private void fillAlpha() {
        int drawFramebuffer = capabilities.OpenGL30 ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer colorMask = stack.malloc(4);
            FloatBuffer clearColor = stack.mallocFloat(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);

            if (drawFramebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            }
            if (scissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(false, false, false, true);
            GL11.glClearColor(0f, 0f, 0f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
            GL11.glClearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            if (drawFramebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            }
        }
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
        if (glLibraryLoaded) {
            MainThread.run(SDLVideo::SDL_GL_UnloadLibrary);
            glLibraryLoaded = false;
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
