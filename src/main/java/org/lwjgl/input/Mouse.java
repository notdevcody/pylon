package org.lwjgl.input;

import java.util.HashMap;
import java.util.Map;

import pl.tomgirl.pylon.window.MouseSdl;
import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;

@SuppressWarnings("unused")
public class Mouse {
    public static final int EVENT_SIZE = 1 + 1 + 4 + 4 + 4 + 8;

    private static final int MAX_BUTTONS = Integer.SIZE;
    private static final int LWJGL2_WHEEL_STEP = 120;
    private static final MouseSdl SDL = MouseSdl.instance();
    private static final Map<String, Integer> BUTTON_INDICES = new HashMap<>(32);
    private static final MouseSdl.PollState pollState = new MouseSdl.PollState();

    private static boolean initialized;
    private static boolean created;
    private static boolean grabbed;
    private static boolean hasWheel;
    private static boolean clipMouseCoordinatesToWindow = !getPrivilegedBoolean("org.lwjgl.input.Mouse.allowNegativeMouseCoords");

    private static int buttonCount = -1;
    private static String[] buttonNames;
    private static Cursor currentCursor;

    private static double grabX;
    private static double grabY;

    private static int eventButton;
    private static boolean eventButtonState;
    private static double eventX;
    private static double eventY;
    private static double eventDx;
    private static double eventDy;
    private static double eventWheelDelta;
    private static double eventWheelDeltaPrecise;
    private static double lastRawEventX;
    private static double lastRawEventY;
    private static long eventNanos;

    // compatibility fields
    // shout out to a mod which sets these w/ reflection
    private static int x;
    private static int y;
    private static int event_x;
    private static int event_y;

    private Mouse() {}

    private static void checkCreated() {
        if (!created) {
            throw new IllegalStateException("Mouse must be created first");
        }
    }

    private static double clamp(double coordinate, int displaySize) {
        return Math.max(0, Math.min(coordinate, displaySize - 1));
    }

    private static void initialize() {
        Sys.initialize();
        buttonNames = new String[MAX_BUTTONS];
        for (int button = 0; button < MAX_BUTTONS; button++) {
            String name = "BUTTON" + button;
            buttonNames[button] = name;
            BUTTON_INDICES.put(name, button);
        }
        initialized = true;
    }

    private static void resetMouse() {
        pollState.dx = pollState.dy = pollState.wheelDelta = 0;
    }

    public static void create() throws LWJGLException {
        if (!Display.isCreated()) {
            throw new IllegalStateException("Display must be created.");
        }

        if (created) {
            return;
        }

        if (!initialized) {
            initialize();
        }

        SDL.createMouse();
        hasWheel = SDL.hasWheel();
        created = true;

        buttonCount = SDL.getButtonCount();
        setGrabbed(grabbed);
    }

    public static boolean isCreated() {
        return created;
    }

    public static void destroy() {
        if (!created) {
            return;
        }

        created = false;
        SDL.destroyMouse();
    }

    public static void poll() {
        checkCreated();

        SDL.pollMouse(pollState);

        if (clipMouseCoordinatesToWindow) {
            pollState.x = clamp(pollState.x, Display.getWidth());
            pollState.y = clamp(pollState.y, Display.getHeight());
        }
    }

    public static int getX() {
        return (int) pollState.x;
    }

    public static int getY() {
        return (int) pollState.y;
    }

    public static int getDX() {
        int result = (int) pollState.dx;
        pollState.dx -= result;
        return result;
    }

    public static int getDY() {
        int result = (int) pollState.dy;
        pollState.dy -= result;
        return result;
    }

    public static int getDWheel() {
        int result = (int) pollState.wheelDelta;
        pollState.wheelDelta -= result;
        return result * LWJGL2_WHEEL_STEP;
    }

    public static boolean isButtonDown(int button) {
        checkCreated();
        return button >= 0 && button < buttonCount && pollState.buttons[button];
    }

    public static int getButtonCount() {
        return buttonCount;
    }

    public static String getButtonName(int button) {
        return button >= 0 && button < buttonNames.length ? buttonNames[button] : null;
    }

    public static int getButtonIndex(String buttonName) {
        return BUTTON_INDICES.getOrDefault(buttonName, -1);
    }

    public static boolean next() {
        checkCreated();
        MouseSdl.Event event = SDL.nextMouseEvent();
        if (event == null) {
            return false;
        }

        eventButton = event.button;
        eventButtonState = event.state;

        double coord1 = event.x;
        double coord2 = event.y;
        if (isGrabbed()) {
            eventDx = coord1;
            eventDy = coord2;
            eventX += coord1;
            eventY += coord2;
        } else {
            eventDx = coord1 - lastRawEventX;
            eventDy = coord2 - lastRawEventY;
            eventX = coord1;
            eventY = coord2;
        }
        lastRawEventX = eventX;
        lastRawEventY = eventY;

        if (clipMouseCoordinatesToWindow) {
            eventX = clamp(eventX, Display.getWidth());
            eventY = clamp(eventY, Display.getHeight());
        }
        eventWheelDelta = event.wheelDelta;
        eventWheelDeltaPrecise = event.wheelDeltaPrecise;
        eventNanos = event.nanos;
        return true;
    }

    public static int getEventButton() {
        return eventButton;
    }

    public static boolean getEventButtonState() {
        return eventButtonState;
    }

    public static int getEventX() {
        return (int) eventX;
    }

    public static int getEventY() {
        return (int) eventY;
    }

    public static int getEventDX() {
        return (int) eventDx;
    }

    public static int getEventDY() {
        return (int) eventDy;
    }

    public static int getEventDWheel() {
        return (int) eventWheelDelta * LWJGL2_WHEEL_STEP;
    }

    public static double getEventDWheelPrecise() {
        return eventWheelDeltaPrecise;
    }

    public static long getEventNanoseconds() {
        return eventNanos;
    }

    public static boolean isGrabbed() {
        return grabbed;
    }

    public static void setGrabbed(boolean grab) {
        boolean wasGrabbed = grabbed;
        grabbed = grab;
        if (!created) {
            return;
        }

        if (grab && !wasGrabbed) {
            grabX = pollState.x;
            grabY = pollState.y;
        } else if (!grab && wasGrabbed) {
            SDL.setCursorPosition(grabX, grabY);
        }

        SDL.grabMouse(grab);
        poll();
        eventX = lastRawEventX = pollState.x;
        eventY = lastRawEventY = pollState.y;
        resetMouse();
    }

    public static void setCursorPosition(int newX, int newY) {
        checkCreated();

        pollState.x = eventX = lastRawEventX = newX;
        pollState.y = eventY = lastRawEventY = newY;
        if (!isGrabbed()) {
            SDL.setCursorPosition(pollState.x, pollState.y);
        } else {
            grabX = newX;
            grabY = newY;
        }
    }

    public static Cursor getNativeCursor() {
        return null;
    }

    public static Cursor setNativeCursor(Cursor cursor) throws LWJGLException {
        return cursor;
    }

    public static void updateCursor() {
    }

    public static boolean isClipMouseCoordinatesToWindow() {
        return clipMouseCoordinatesToWindow;
    }

    public static void setClipMouseCoordinatesToWindow(boolean clip) {
        clipMouseCoordinatesToWindow = clip;
    }

    public static boolean hasWheel() {
        return hasWheel;
    }

    public static boolean isInsideWindow() {
        return SDL.isInsideWindow();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean getPrivilegedBoolean(final String propertyName) {
        return Boolean.getBoolean(propertyName);
    }
}
