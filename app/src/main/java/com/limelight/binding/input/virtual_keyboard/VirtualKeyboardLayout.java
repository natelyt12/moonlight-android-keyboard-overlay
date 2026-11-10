package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class VirtualKeyboardLayout {

    public static final String ACTION_TOGGLE_VISIBILITY = "action_toggle_visibility";

    public static List<VirtualKey> createDefaultLayout() {
        return createDefaultLayout(null);
    }

    public static List<VirtualKey> createDefaultLayout(Context context) {
        return new ArrayList<>();
    }
}
