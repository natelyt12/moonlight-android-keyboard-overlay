package com.limelight.binding.input.virtual_keyboard;

public class VirtualKey {
    public enum KeyType {
        KEYBOARD,
        MOUSE_BUTTON,
        ACTION
    }

    // Action codes for KeyType.ACTION
    public static final int ACTION_CODE_TOGGLE_VISIBILITY = 1;
    public static final int ACTION_CODE_TOGGLE_PC_KEYBOARD = 2;
    public static final int ACTION_CODE_SHOW_ANDROID_KEYBOARD = 3;
    public static final int ACTION_CODE_TOGGLE_MOUSE = 4;
    public static final int ACTION_CODE_SCROLL_UP = 5;
    public static final int ACTION_CODE_SCROLL_DOWN = 6;
    public static final int ACTION_CODE_TOGGLE_STREAM_MENU = 7;


    public final String id;
    public String label;
    public KeyType type;
    public int code; // KeyEvent.KEYCODE_* or MouseButtonPacket.BUTTON_*

    // Relative coordinates (0.0f - 1.0f of parent container)
    public float relX;
    public float relY;
    public float relWidth;
    public float relHeight;

    // Custom styling
    public float cornerRadius = 0f;  // in dp (default 0px)
    public int textOpacity = 100;     // 0 - 100%
    public int bgOpacity = 45;        // 0 - 100%
    public boolean showBorder = true;
    public boolean isSlide = false;        // Slide mode: triggers on slide-in touch
    public boolean holdOutside = false;    // Hold on slide out: keep pressed outside key boundary
    public boolean absorbTouch = true;     // Absorb touch: blocks stream mouse/camera movement (default enabled)
    public boolean isBlockTouch = false;   // Block touch: absorbs all touches, no inputs

    // Toggle-overlay action keys only: when the overlay is toggled off, switch the
    // touch input mode to direct touchscreen; switch back to trackpad when shown.
    public boolean switchInputModeOnToggle = false;

    // Virtual Controller / Joystick (4-way directional + diagonal mapping)
    public boolean isJoystick = false;
    public int codeUp = android.view.KeyEvent.KEYCODE_W;
    public int codeDown = android.view.KeyEvent.KEYCODE_S;
    public int codeLeft = android.view.KeyEvent.KEYCODE_A;
    public int codeRight = android.view.KeyEvent.KEYCODE_D;

    // Scroll Controller (Vertical mouse wheel scroll strip)
    public boolean isScroll = false;

    // Key Combo / Multiple Key Bindings (e.g. Ctrl + V, Alt + F4)
    public int extraCode1 = 0; // 0 = None
    public int extraCode2 = 0; // 0 = None
    public int extraCode3 = 0; // 0 = None

    public VirtualKey(String id, String label, KeyType type, int code,
                      float relX, float relY, float relWidth, float relHeight) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.code = code;
        this.relX = relX;
        this.relY = relY;
        this.relWidth = relWidth;
        this.relHeight = relHeight;
    }
}
