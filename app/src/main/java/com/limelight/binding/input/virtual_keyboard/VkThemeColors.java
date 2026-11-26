package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;

/**
 * Resolves the device's dynamic accent color (Android 12+ Material You / theme colorAccent)
 * with a default blue fallback for consistent minimalist custom UI styling.
 */
public class VkThemeColors {

    public static final int DEFAULT_ACCENT_BLUE = 0xFF2196F3;

    public static int getAccentColor(Context context) {
        if (context == null) {
            return DEFAULT_ACCENT_BLUE;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                int color = context.getColor(android.R.color.system_accent1_400);
                if (Color.alpha(color) > 0) {
                    return color;
                }
            } catch (Exception ignored) {
            }
        }
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            int color = tv.data;
            if (Color.alpha(color) > 0 && !isTooDark(color)) {
                return color;
            }
        }
        return DEFAULT_ACCENT_BLUE;
    }

    public static int getAccentPressedColor(Context context) {
        return darkenColor(getAccentColor(context), 0.82f);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static StateListDrawable createPrimaryButtonDrawable(Context context) {
        float radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, context.getResources().getDisplayMetrics());

        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(radius);
        pressed.setColor(getAccentPressedColor(context));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(radius);
        normal.setColor(getAccentColor(context));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    public static StateListDrawable createInputBackgroundDrawable(Context context) {
        float radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 6f, context.getResources().getDisplayMetrics());
        int stroke = Math.max(1, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1f, context.getResources().getDisplayMetrics())));

        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(radius);
        focused.setColor(0xFF14181F);
        focused.setStroke(stroke, getAccentColor(context));

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(radius);
        normal.setColor(0xFF14181F);
        normal.setStroke(stroke, 0xFF2A313D);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static boolean isTooDark(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum < 0.12;
    }

    private static int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max(0, Math.min(255, Math.round(Color.red(color) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(Color.green(color) * factor)));
        int b = Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)));
        return Color.argb(a, r, g, b);
    }
}
