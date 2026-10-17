package pl.tomgirl.pylon.window;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.lwjgl.input.Keyboard;
import org.lwjgl.sdl.SDLScancode;

import static org.lwjgl.input.Keyboard.*;
import static org.lwjgl.sdl.SDLScancode.*;

public class Keymap {
    static final int UNMAPPED = -1;
    private static final int[] SDL_TO_LWJGL = createMappings();

    private Keymap() {}

    static int translate(int scancode) {
        return scancode >= 0 && scancode < SDL_TO_LWJGL.length
            ? SDL_TO_LWJGL[scancode]
            : UNMAPPED;
    }

    private static int[] createMappings() {
        int[] mappings = new int[SDL_SCANCODE_COUNT];
        Arrays.fill(mappings, UNMAPPED);

        mapMatchingNames(mappings);

        map(mappings, SDL_SCANCODE_UNKNOWN, KEY_NONE);
        map(mappings, SDL_SCANCODE_LEFTBRACKET, KEY_LBRACKET);
        map(mappings, SDL_SCANCODE_RIGHTBRACKET, KEY_RBRACKET);
        map(mappings, SDL_SCANCODE_BACKSPACE, KEY_BACK);
        map(mappings, SDL_SCANCODE_PAGEUP, KEY_PRIOR);
        map(mappings, SDL_SCANCODE_PAGEDOWN, KEY_NEXT);
        map(mappings, SDL_SCANCODE_CAPSLOCK, KEY_CAPITAL);
        map(mappings, SDL_SCANCODE_SCROLLLOCK, KEY_SCROLL);
        map(mappings, SDL_SCANCODE_NUMLOCKCLEAR, KEY_NUMLOCK);
        map(mappings, SDL_SCANCODE_PRINTSCREEN, KEY_PRINT_SCREEN);

        map(mappings, SDL_SCANCODE_KP_0, KEY_NUMPAD0);
        map(mappings, SDL_SCANCODE_KP_1, KEY_NUMPAD1);
        map(mappings, SDL_SCANCODE_KP_2, KEY_NUMPAD2);
        map(mappings, SDL_SCANCODE_KP_3, KEY_NUMPAD3);
        map(mappings, SDL_SCANCODE_KP_4, KEY_NUMPAD4);
        map(mappings, SDL_SCANCODE_KP_5, KEY_NUMPAD5);
        map(mappings, SDL_SCANCODE_KP_6, KEY_NUMPAD6);
        map(mappings, SDL_SCANCODE_KP_7, KEY_NUMPAD7);
        map(mappings, SDL_SCANCODE_KP_8, KEY_NUMPAD8);
        map(mappings, SDL_SCANCODE_KP_9, KEY_NUMPAD9);
        map(mappings, SDL_SCANCODE_KP_PERIOD, KEY_DECIMAL);
        map(mappings, SDL_SCANCODE_KP_DECIMAL, KEY_DECIMAL);
        map(mappings, SDL_SCANCODE_KP_DIVIDE, KEY_DIVIDE);
        map(mappings, SDL_SCANCODE_KP_MULTIPLY, KEY_MULTIPLY);
        map(mappings, SDL_SCANCODE_KP_MINUS, KEY_SUBTRACT);
        map(mappings, SDL_SCANCODE_KP_PLUS, KEY_ADD);
        map(mappings, SDL_SCANCODE_KP_ENTER, KEY_NUMPADENTER);
        map(mappings, SDL_SCANCODE_KP_EQUALS, KEY_NUMPADEQUALS);
        map(mappings, SDL_SCANCODE_KP_COMMA, KEY_NUMPADCOMMA);

        map(mappings, SDL_SCANCODE_LCTRL, KEY_LCONTROL);
        map(mappings, SDL_SCANCODE_LALT, KEY_LMENU);
        map(mappings, SDL_SCANCODE_LGUI, KEY_LMETA);
        map(mappings, SDL_SCANCODE_RCTRL, KEY_RCONTROL);
        map(mappings, SDL_SCANCODE_RALT, KEY_RMENU);
        map(mappings, SDL_SCANCODE_MODE, KEY_RMENU);
        map(mappings, SDL_SCANCODE_RGUI, KEY_RMETA);
        map(mappings, SDL_SCANCODE_APPLICATION, KEY_APPS);
        map(mappings, SDL_SCANCODE_MENU, KEY_APPS);

        map(mappings, SDL_SCANCODE_NONUSBACKSLASH, KEY_WORLD_1);
        map(mappings, SDL_SCANCODE_NONUSHASH, KEY_BACKSLASH);
        map(mappings, SDL_SCANCODE_INTERNATIONAL2, KEY_KANA);
        map(mappings, SDL_SCANCODE_INTERNATIONAL3, KEY_YEN);
        map(mappings, SDL_SCANCODE_INTERNATIONAL4, KEY_CONVERT);
        map(mappings, SDL_SCANCODE_INTERNATIONAL5, KEY_NOCONVERT);

        return mappings;
    }

    private static void mapMatchingNames(int[] mappings) {
        for (Field lwjglKey : Keyboard.class.getFields()) {
            if (!lwjglKey.getName().startsWith("KEY_") || lwjglKey.getType() != int.class) {
                continue;
            }

            try {
                Field sdlScancode = SDLScancode.class.getField("SDL_SCANCODE_" + lwjglKey.getName().substring(4));
                map(mappings, sdlScancode.getInt(null), lwjglKey.getInt(null));
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private static void map(int[] mappings, int scancode, int lwjglKey) {
        mappings[scancode] = lwjglKey;
    }
}
