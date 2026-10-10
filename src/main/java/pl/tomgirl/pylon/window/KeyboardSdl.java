package pl.tomgirl.pylon.window;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import pl.tomgirl.pylon.Pylon;
import org.lwjgl.input.Keyboard;
import org.lwjgl.sdl.*;

import static org.lwjgl.sdl.SDLEvents.*;

public class KeyboardSdl {
    private static final int TEXT_EVENT_KEY = -1;
    private static final int EVENT_QUEUE_SIZE = 100;

    private static final KeyboardSdl INSTANCE = new KeyboardSdl();
    private final byte[] keyDownBuffer = new byte[Keyboard.KEYBOARD_SIZE];
    private final BoundedQueue<Event> events = new BoundedQueue<>(EVENT_QUEUE_SIZE, Event::new);
    private final SDL_KeyboardEvent keyboardEvent = DisplaySdl.instance().getEvent().key();
    private final SDL_TextInputEvent textInputEvent = DisplaySdl.instance().getEvent().text();

    public void createKeyboard() {
        reset();
    }

    private void putKeyboardEvent(int keycode, byte state, int ch, long nanos, boolean repeat) {
        if (keycode == TEXT_EVENT_KEY) {
            Event last = events.peekLast();
            if (last != null && last.keycode > 0 && last.state != 0 && last.character == 0) {
                last.character = ch;
                return;
            }
            keycode = Keyboard.KEY_NONE;
        }

        Event event = events.claim();
        if (event != null) {
            event.set(keycode, state, ch, nanos, repeat);
        }
    }

    public void destroyKeyboard() {
        reset();
    }

    void releaseAll(long nanos) {
        for (int key = 1; key < keyDownBuffer.length; key++) {
            if (keyDownBuffer[key] != 0) {
                keyDownBuffer[key] = 0;
                putKeyboardEvent(key, (byte) 0, Keyboard.CHAR_NONE, nanos, false);
            }
        }
    }

    boolean isAnyKeyDown() {
        for (byte state : keyDownBuffer) {
            if (state != 0) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        Arrays.fill(keyDownBuffer, (byte) 0);
        events.clear();
    }

    public void pollKeyboard(ByteBuffer keyDownBuffer) {
        int oldPosition = keyDownBuffer.position();
        keyDownBuffer.put(this.keyDownBuffer);
        keyDownBuffer.position(oldPosition);
    }

    public Event nextKeyboardEvent() {
        return events.poll();
    }

    public int getNumKeyboardEvents(boolean includeRepeats) {
        if (includeRepeats) {
            return events.size();
        }

        int count = 0;
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (!event.repeat) {
                count++;
            }
        }
        return count;
    }

    public void processKeyboardEvent(SDL_Event event) {
        switch (event.type()) {
            case SDL_EVENT_KEY_DOWN:
            case SDL_EVENT_KEY_UP: {
                int key = translateKeyFromSDL(keyboardEvent.scancode());
                byte state = keyboardEvent.down() ? (byte) 1 : 0;
                if (key != Keyboard.KEY_NONE) {
                    this.keyDownBuffer[key] = state;
                }
                putKeyboardEvent(key, state, deferredCharacter(state), keyboardEvent.timestamp(), keyboardEvent.repeat());
                break;
            }
            case SDL_EVENT_TEXT_INPUT: {
                String text = textInputEvent.textString();
                if (text == null) text = "";
                text.chars().forEach(character ->
                    putKeyboardEvent(TEXT_EVENT_KEY, (byte) -1, character, textInputEvent.timestamp(), false)
                );
                break;
            }
        }
    }

    private int deferredCharacter(byte state) {
        if (state == 0 || !DisplaySdl.instance().isTextInputDeferred()
            || (keyboardEvent.mod() & (SDLKeycode.SDL_KMOD_CTRL | SDLKeycode.SDL_KMOD_ALT | SDLKeycode.SDL_KMOD_GUI)) != 0
        ) {
            return 0;
        }
        int character = SDLKeyboard.SDL_GetKeyFromScancode(keyboardEvent.scancode(), keyboardEvent.mod(), false);
        return character >= ' ' && character != 0x7F && Character.isValidCodePoint(character) ? character : 0;
    }

    private int translateKeyFromSDL(int key) {
        if (key < 0) key = SDLScancode.SDL_SCANCODE_UNKNOWN;
        int translated = Keymap.translate(key);
        if (translated != Keymap.UNMAPPED) {
            return translated;
        }
        Pylon.LOG.log(Level.WARNING, "Untranslated key: {0} ({1})", new Object[]{key, SDLKeyboard.SDL_GetScancodeName(key)});
        return Keyboard.KEY_NONE;
    }

    public static KeyboardSdl instance() {
        return INSTANCE;
    }

    public static final class Event {
        public int keycode;
        public byte state;
        public int character;
        public long nanos;
        public boolean repeat;

        private Event() {}

        private void set(int keycode, byte state, int character, long nanos, boolean repeat) {
            this.keycode = keycode;
            this.state = state;
            this.character = character;
            this.nanos = nanos;
            this.repeat = repeat;
        }
    }
}
