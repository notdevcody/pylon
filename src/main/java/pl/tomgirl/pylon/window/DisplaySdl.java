package pl.tomgirl.pylon.window;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.Drawable;
import pl.tomgirl.pylon.Pylon;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.sdl.*;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLStdinc.SDL_SetMemoryFunctions;
import static org.lwjgl.sdl.SDLStdinc.nSDL_GetMemoryFunctions;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;

@SuppressWarnings("unused")
public class DisplaySdl {
    private static final DisplaySdl INSTANCE = new DisplaySdl();

    private boolean videoInitialized;
    private SDL_Event event;
    private SDL_WindowEvent windowEvent;
    private final Drawable drawable = new SurfaceDrawable();

    private final Map<String, String> hints = new HashMap<>();
    private boolean highPixelDensity = false;

    private long handle = -1L;
    private GpuSurface surface;
    private String title = "";
    private ByteBuffer[] cachedIcons;

    private int width = 640;
    private int height = 480;
    private int framebufferWidth = 640;
    private int framebufferHeight = 480;
    private int windowedWidth;
    private int windowedHeight;

    private boolean resizable;
    private boolean windowResized = true;
    private boolean resizePending;
    private boolean minimized;
    private boolean fullscreen;
    private boolean fullscreenDeferred;
    private boolean borderlessFullscreen;
    private int windowedX;
    private int windowedY;
    private boolean focused;
    private boolean closeRequested;

    private boolean textInputRequested = true;
    private boolean textInputActive;
    private int textInputX = -1;
    private int textInputY;
    private int textInputWidth;
    private int textInputHeight;
    private int textInputCursor;

    public static DisplaySdl instance() {
        return INSTANCE;
    }

    public void setSurface(@NotNull GpuSurface surface) {
        if (isCreated()) {
            throw new IllegalStateException("Cannot replace the surface after display creation");
        }
        this.surface = surface;
    }

    public long getHandle() {
        return handle;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    SDL_Event getEvent() {
        if (event == null) {
            event = SDL_Event.calloc();
            windowEvent = event.window();
        }

        return event;
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public void setTextInputActive(boolean active) {
        if (handle > 0 && textInputActive) {
            MainThread.run(() -> checkSdlError(SDLKeyboard.SDL_ClearComposition(handle)));
        }
        textInputX = -1;
        textInputRequested = active;
        updateTextInputState();
    }

    public void setTextInputArea(int x, int y, int width, int height, int cursor, int guiWidth, int guiHeight) {
        if (handle <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return;
        }

        int areaX = Math.round((float) x * this.width / guiWidth);
        int areaY = Math.round((float) y * this.height / guiHeight);
        int areaWidth = Math.max(1, Math.round((float) width * this.width / guiWidth));
        int areaHeight = Math.max(1, Math.round((float) height * this.height / guiHeight));
        int areaCursor = Math.round((float) cursor * this.width / guiWidth);
        if (areaX == textInputX && areaY == textInputY && areaWidth == textInputWidth
            && areaHeight == textInputHeight && areaCursor == textInputCursor
        ) {
            return;
        }
        MainThread.run(() -> {
            try (MemoryStack stack = stackPush()) {
                SDL_Rect.Buffer area = SDL_Rect.calloc(1, stack)
                    .x(areaX).y(areaY).w(areaWidth).h(areaHeight);
                checkSdlError(SDLKeyboard.SDL_SetTextInputArea(handle, area, areaCursor));
            }
        });
        textInputX = areaX;
        textInputY = areaY;
        textInputWidth = areaWidth;
        textInputHeight = areaHeight;
        textInputCursor = areaCursor;
    }

    private void updateTextInputState() {
        if (handle <= 0 || textInputActive == textInputRequested) {
            return;
        }

        MainThread.run(() -> {
            if (textInputRequested) {
                checkSdlError(SDLKeyboard.SDL_StartTextInput(handle));
            } else {
                SDLKeyboard.SDL_StopTextInput(handle);
            }
        });
        textInputActive = textInputRequested;
        textInputX = -1;
    }

    @NotNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NotNull String title) {
        this.title = title;
        if (isCreated()) {
            MainThread.run(() -> SDL_SetWindowTitle(handle, title));
        }
    }

    @NotNull
    public DisplayMode getDisplayMode() {
        if (!isCreated() && fullscreenDeferred) {
            initializeVideo();
            SDL_DisplayMode mode = SDL_GetDesktopDisplayMode(currentDisplay());
            if (mode != null) {
                float density = highPixelDensity ? mode.pixel_density() : 1f;
                return new DisplayMode(Math.round(mode.w() * density), Math.round(mode.h() * density), 24, 60);
            }
        }
        return new DisplayMode(framebufferWidth, framebufferHeight, 24, 60);
    }

    public void setDisplayMode(@NotNull DisplayMode mode) {
        width = windowedWidth = mode.getWidth();
        height = windowedHeight = mode.getHeight();
        float density = isCreated() ? getPixelDensity() : 1f;
        framebufferWidth = Math.round(width * density);
        framebufferHeight = Math.round(height * density);
        windowResized = true;
        resizePending = true;
    }

    public int getWidth() {
        return framebufferWidth;
    }

    public int getHeight() {
        return framebufferHeight;
    }

    public int getWindowWidth() {
        return width;
    }

    public int getWindowHeight() {
        return height;
    }

    public float getPixelDensity() {
        return SDL_GetWindowPixelDensity(handle);
    }

    /// Sets [SDLVideo#SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN] for window creation.
    public void setHighPixelDensity(boolean highPixelDensity) {
        if (isCreated()) {
            throw new IllegalStateException("Window properties cannot be set after Display is created");
        }
        this.highPixelDensity = highPixelDensity;
    }

    public boolean isHighPixelDensity() {
        return highPixelDensity;
    }

    /// Sets [SDLHints] for window creation.
    public void setWindowHint(String hint, String value) {
        if (isCreated()) {
            throw new IllegalStateException("Window hints cannot be set after Display is created");
        }
        hints.put(hint, value);
    }

    @SuppressWarnings("resource")
    public DisplayMode getDesktopDisplayMode() {
        SDL_DisplayMode mode = SDL_GetDesktopDisplayMode(currentDisplay());
        if (mode == null) {
            DisplayMode best = null;
            for (DisplayMode displayMode : getAvailableDisplayModes()) {
                if (best == null || displayMode.getWidth() * displayMode.getHeight() > best.getWidth() * best.getHeight()) {
                    best = displayMode;
                }
            }
            return best;
        }
        return new DisplayMode(
            mode.w(), mode.h(),
            SDL_PixelFormatDetails.nbits_per_pixel(SDLPixels.nSDL_GetPixelFormatDetails(mode.format())),
            (int) mode.refresh_rate()
        );
    }

    public int setIcon(@NotNull ByteBuffer[] icons) {
        if (!Arrays.equals(cachedIcons, icons)) {
            cachedIcons = Arrays.stream(icons).map(buf -> {
                ByteBuffer copy = ByteBuffer.allocate(buf.remaining());
                copy.put(buf.duplicate());
                copy.flip();
                return copy;
            }).toArray(ByteBuffer[]::new);
        }

        if (!isCreated() || cachedIcons == null || cachedIcons.length == 0) {
            return 0;
        }

        ByteBuffer icon = cachedIcons[0];
        int size = iconSize(icon);
        for (int i = 1; i < cachedIcons.length; i++) {
            int currentSize = iconSize(cachedIcons[i]);
            if (currentSize > size) {
                icon = cachedIcons[i];
                size = currentSize;
            }
        }

        ByteBuffer pixels = MemoryUtil.memAlloc(icon.remaining());
        try {
            pixels.put(icon.duplicate()).flip();
            int iconSize = size;
            MainThread.run(() -> {
                try (SDL_Surface surface = SDLSurface.SDL_CreateSurfaceFrom(
                    iconSize, iconSize, SDLPixels.SDL_PIXELFORMAT_RGBA32, pixels, iconSize * 4
                )) {
                    checkSdlError(surface != null);
                    checkSdlError(SDL_SetWindowIcon(handle, surface));
                } catch (Exception e) {
                    Pylon.LOG.log(Level.SEVERE, "Failed to set window icon", e);
                }
            });
        } finally {
            MemoryUtil.memFree(pixels);
        }
        return 1;
    }

    private static int iconSize(ByteBuffer icon) {
        int pixels = icon.remaining() / 4;
        int size = (int) Math.sqrt(pixels);
        if (size == 0 || size * size != pixels || icon.remaining() % 4 != 0) {
            throw new IllegalArgumentException("Icon must be a square RGBA image");
        }
        return size;
    }

    public void update() {
        swapBuffers();
        processMessages();
    }

    public void processMessages() {
        if (!MainThread.DISPATCH && !SDL_IsMainThread()) {
            return;
        }

        windowResized = resizePending;
        resizePending = false;
        MainThread.run(this::pollEvents);
        Keyboard.poll();
        Mouse.poll();
    }

    private void pollEvents() {
        while (SDL_PollEvent(event)) {
            switch (event.type()) {
                case SDL_EVENT_QUIT:
                case SDL_EVENT_WINDOW_CLOSE_REQUESTED:
                    closeRequested = true;
                    break;
                case SDL_EVENT_WINDOW_FOCUS_GAINED:
                    focused = true;
                    break;
                case SDL_EVENT_WINDOW_FOCUS_LOST: {
                    focused = false;
                    long nanos = windowEvent.timestamp();
                    if (Keyboard.isCreated()) {
                        KeyboardSdl.instance().releaseAll(nanos);
                    }
                    if (Mouse.isCreated()) {
                        MouseSdl.instance().releaseAll(nanos);
                    }
                    break;
                }
                case SDL_EVENT_WINDOW_SHOWN:
                case SDL_EVENT_WINDOW_RESTORED:
                case SDL_EVENT_WINDOW_MAXIMIZED:
                    minimized = false;
                    break;
                case SDL_EVENT_WINDOW_HIDDEN:
                case SDL_EVENT_WINDOW_MINIMIZED:
                    minimized = true;
                    break;
                case SDL_EVENT_WINDOW_RESIZED:
                    resizeCallback(handle, windowEvent.data1(), windowEvent.data2());
                    break;
                case SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED:
                    onFramebufferResize(handle, windowEvent.data1(), windowEvent.data2());
                    break;
                case SDL_EVENT_WINDOW_DISPLAY_CHANGED:
                    if (borderlessFullscreen) {
                        enterBorderlessFullscreen();
                    }
                    break;
                case SDL_EVENT_WINDOW_ENTER_FULLSCREEN:
                    fullscreen = true;
                    windowResized = true;
                    break;
                case SDL_EVENT_WINDOW_LEAVE_FULLSCREEN:
                    fullscreen = false;
                    windowResized = true;
                    break;
                case SDL_EVENT_KEY_DOWN:
                case SDL_EVENT_KEY_UP:
                case SDL_EVENT_TEXT_INPUT:
                case SDL_EVENT_TEXT_EDITING:
                case SDL_EVENT_TEXT_EDITING_CANDIDATES:
                    if (Keyboard.isCreated()) {
                        KeyboardSdl.instance().processKeyboardEvent(event);
                    }
                    break;
                case SDL_EVENT_MOUSE_BUTTON_DOWN:
                case SDL_EVENT_MOUSE_BUTTON_UP:
                case SDL_EVENT_MOUSE_MOTION:
                case SDL_EVENT_MOUSE_WHEEL:
                case SDL_EVENT_WINDOW_MOUSE_ENTER:
                case SDL_EVENT_WINDOW_MOUSE_LEAVE:
                    if (Mouse.isCreated()) {
                        MouseSdl.instance().processMouseEvent(event);
                    }
                    break;
            }
        }
    }

    private static void checkSdlError(boolean success) {
        if (!success) {
            throw new IllegalStateException("SDL error encountered: " + SDL_GetError());
        }
    }

    public synchronized void initializeVideo() {
        if (videoInitialized) {
            return;
        }

        MainThread.run(this::initSdlVideo);
        videoInitialized = true;
    }

    private void initSdlVideo() {
        SDL_SetMemoryFunctions(
            MemoryUtil::nmemAllocChecked,
            MemoryUtil::nmemCallocChecked,
            MemoryUtil::nmemReallocChecked,
            MemoryUtil::nmemFree
        );

        checkSdlError(SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MAC_BACKGROUND_APP, "0"));
        checkSdlError(SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "1"));
        checkSdlError(SDLHints.SDL_SetHint("SDL_BORDERLESS_WINDOWED_STYLE", "0"));
        hints.forEach((k, v) -> checkSdlError(SDLHints.SDL_SetHint(k, v)));

        checkSdlError(SDL_SetAppMetadata("Minecraft", null, "com.mojang.minecraft"));
        checkSdlError(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_URL_STRING, "https://minecraft.net"));
        checkSdlError(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_CREATOR_STRING, "Mojang AB"));
        checkSdlError(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_COPYRIGHT_STRING, "Minecraft EULA: https://minecraft.net/eula"));
        checkSdlError(SDL_SetAppMetadataProperty(SDL_PROP_APP_METADATA_TYPE_STRING, "game"));

        if (!SDL_Init(SDL_INIT_VIDEO)) {
            throw new IllegalStateException("Unable to initialize SDL: " + SDL_GetError());
        }
    }

    public void create(@NotNull GpuSurface fallbackSurface) throws LWJGLException {
        if (isCreated()) {
            throw new IllegalStateException("Display has already been created");
        }

        initializeVideo();

        GpuSurface surface = this.surface != null ? this.surface : fallbackSurface;
        this.surface = surface;
        windowedWidth = width;
        windowedHeight = height;
        try {
            handle = surface.createWindow(title, width, height, resizable);
        } catch (RuntimeException | Error throwable) {
            surface.destroy();
            this.surface = null;
            handle = -1L;
            throw throwable;
        }

        setFullscreen(fullscreenDeferred);
        resizePending = true;

        Mouse.create();
        Keyboard.create();
        MainThread.run(() -> {
            checkSdlError(SDL_ShowWindow(handle));
            checkSdlError(SDL_RaiseWindow(handle));
            SDL_SyncWindow(handle);
            try (MemoryStack ms = stackPush()) {
                IntBuffer width = ms.mallocInt(1);
                IntBuffer height = ms.mallocInt(1);
                checkSdlError(SDL_GetWindowSizeInPixels(handle, width, height));
                framebufferWidth = Math.max(1, width.get(0));
                framebufferHeight = Math.max(1, height.get(0));
            }
            if (SDL_IsMainThread()) {
                SDL_PumpEvents();
            }
            focused = (SDL_GetWindowFlags(handle) & SDL_WINDOW_INPUT_FOCUS) != 0;
        });
        updateTextInputState();
        if (cachedIcons != null) {
            setIcon(cachedIcons);
        }
    }

    private void onFramebufferResize(long window, int framebufferWidth, int framebufferHeight) {
        if (window != handle) {
            return;
        }

        if (framebufferWidth == 0 || framebufferHeight == 0) {
            minimized = true;
            return;
        }

        minimized = false;
        if (this.framebufferWidth != framebufferWidth || this.framebufferHeight != framebufferHeight) {
            this.framebufferWidth = framebufferWidth;
            this.framebufferHeight = framebufferHeight;
            windowResized = true;
        }
    }

    public boolean isFullscreen() {
        return this.fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        if (!isCreated()) {
            fullscreenDeferred = fullscreen;
            return;
        }

        try {
            if (fullscreen) {
                if (currentDisplay() == 0) {
                    Pylon.LOG.log(Level.WARNING, "Failed to find display");
                    return;
                }
                if (!this.fullscreen) {
                    windowedWidth = width;
                    windowedHeight = height;
                }
            } else {
                width = windowedWidth;
                height = windowedHeight;
            }
            MainThread.run(() -> {
                if (!fullscreen || !enterBorderlessFullscreen()) {
                    leaveBorderlessFullscreen();
                    SDL_SetWindowFullscreen(handle, fullscreen);
                    SDL_SetWindowSize(handle, windowedWidth, windowedHeight);
                }
            });
            windowResized = true;
            resizePending = true;
        } catch (Throwable t) {
            Pylon.LOG.log(Level.WARNING, "Failed to set fullscreen: ", t);
        }
    }

    private boolean enterBorderlessFullscreen() {
        if (!"Windows".equals(SDLPlatform.SDL_GetPlatform())) {
            return false;
        }
        try (MemoryStack ms = stackPush()) {
            SDL_Rect bounds = SDL_Rect.malloc(ms);
            if (!SDL_GetDisplayBounds(currentDisplay(), bounds)) {
                return false;
            }
            if (!borderlessFullscreen) {
                IntBuffer x = ms.mallocInt(1);
                IntBuffer y = ms.mallocInt(1);
                SDL_RestoreWindow(handle);
                SDL_SyncWindow(handle);
                SDL_GetWindowPosition(handle, x, y);
                windowedX = x.get(0);
                windowedY = y.get(0);
            }
            SDL_SetWindowBordered(handle, false);
            SDL_SetWindowSize(handle, bounds.w() + 1, bounds.h());
            SDL_SetWindowPosition(handle, bounds.x(), bounds.y());
        }
        borderlessFullscreen = true;
        fullscreen = true;
        return true;
    }

    private void leaveBorderlessFullscreen() {
        if (borderlessFullscreen) {
            borderlessFullscreen = false;
            fullscreen = false;
            SDL_SetWindowBordered(handle, true);
            SDL_SetWindowPosition(handle, windowedX, windowedY);
        }
    }

    private int currentDisplay() {
        int display = isCreated() ? SDL_GetDisplayForWindow(handle) : 0;
        return display != 0 ? display : SDL_GetPrimaryDisplay();
    }

    @NotNull
    public DisplayMode[] getAvailableDisplayModes() {
        int currDisplay = currentDisplay();
        if (currDisplay == 0) {
            return new DisplayMode[0];
        }

        PointerBuffer buf = SDL_GetFullscreenDisplayModes(currDisplay);
        if (buf == null) {
            throw new IllegalStateException("No display modes found");
        }

        DisplayMode[] modes = new DisplayMode[buf.limit()];
        for (int i = 0; i < modes.length; i++) {
            long mode = buf.get(i);
            modes[i] = new DisplayMode(
                SDL_DisplayMode.nw(mode), SDL_DisplayMode.nh(mode),
                SDL_PixelFormatDetails.nbits_per_pixel(SDLPixels.nSDL_GetPixelFormatDetails(SDL_DisplayMode.nformat(mode))),
                (int) SDL_DisplayMode.nrefresh_rate(mode)
            );
        }
        return modes;
    }

    public void destroy() {
        Keyboard.destroy();
        Mouse.destroy();
        if (handle > 0) {
            setTextInputActive(false);
        }
        if (surface != null) {
            surface.destroy();
            surface = null;
        }
        handle = -1L;
        textInputX = -1;
        MainThread.run(() -> {
            if (SDL_WasInit(SDL_INIT_VIDEO) != 0) {
                SDL_QuitSubSystem(SDL_INIT_VIDEO);
            }
            SDL_Quit();
        });
        if (event != null) {
            event.free();
            event = null;
            windowEvent = null;
        }
        videoInitialized = false;
        try (MemoryStack stack = stackPush()) {
            PointerBuffer funcs = stack.mallocPointer(4);

            nSDL_GetMemoryFunctions(
                memAddress(funcs, 0),
                memAddress(funcs, 1),
                memAddress(funcs, 2),
                memAddress(funcs, 3)
            );

            for (int i = 0; i < 4; i++) {
                Callback.free(funcs.get(i));
            }
        }
    }

    public boolean isCreated() {
        return handle > 0;
    }

    public boolean isActive() {
        return focused;
    }

    public void setResizable(boolean isResizable) {
        resizable = isResizable;
        if (isCreated()) {
            MainThread.run(() -> SDL_SetWindowResizable(handle, resizable));
        }
    }

    public void setVSyncEnabled(boolean enabled) {
        if (surface != null) {
            surface.setVSyncEnabled(enabled);
        }
    }

    public boolean wasResized() {
        return windowResized;
    }

    public boolean isVisible() {
        return !minimized;
    }

    private void resizeCallback(long window, int width, int height) {
        if (window == handle) {
            windowResized = true;
            this.width = width;
            this.height = height;
        }
    }

    public void makeCurrent() {
        requireSurface().makeCurrent();
    }

    public void swapBuffers() {
        requireSurface().swapBuffers();
    }

    private GpuSurface requireSurface() {
        if (surface == null) {
            throw new IllegalStateException("Display has not been created");
        }
        return surface;
    }

    private final class SurfaceDrawable implements Drawable {
        @Override
        public boolean isCurrent() {
            return false;
        }

        @Override
        public void makeCurrent() {
            DisplaySdl.this.makeCurrent();
        }

        @Override
        public void releaseContext() {
            requireSurface().releaseCurrent();
        }

        @Override
        public void destroy() {
        }
    }
}
