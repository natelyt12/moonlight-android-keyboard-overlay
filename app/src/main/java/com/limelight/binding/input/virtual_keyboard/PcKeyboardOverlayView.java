package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Floating 75% PC keyboard overlay.
 *
 * <p>The board is laid out on a fixed 16-unit horizontal grid: every row is exactly
 * {@link #ROW_UNITS} units wide and keys are positioned from their absolute unit offset.
 * This guarantees that all rows share the same left/right margin and that a 1u key is a
 * perfect square (1u wide == 1u tall), while long keys (Tab, Caps, Shift, Space, ...) simply
 * span more units.</p>
 */
@SuppressLint("ViewConstructor")
public class PcKeyboardOverlayView extends View {

    // ----------------------------------------------------------------------------------
    // Layout constants (dp)
    // ----------------------------------------------------------------------------------

    /** Horizontal units in every row. */
    private static final float ROW_UNITS = 16f;
    private static final int ROW_COUNT = 6;

    private static final float PAD_H_DP = 8f;
    /** Tight gap between keys, matching the mouse overlay. */
    private static final float GAP_DP = 3f;
    private static final float TOP_PAD_DP = 2f;
    /** Thin drag strip, same weight as the resize strip at the bottom. */
    private static final float DRAG_BAR_H_DP = 16f;
    private static final float KEY_TOP_GAP_DP = 2f;
    private static final float RESIZE_ZONE_DP = 16f;

    // ----------------------------------------------------------------------------------
    // Layout model
    // ----------------------------------------------------------------------------------

    private static final class KeyDef {
        final String label;
        /** Shifted legend drawn above the primary one (real-keycap style). May be null. */
        final String shiftLabel;
        final int keycode;
        final float unitWidth;
        final boolean modifier;
        final boolean spacer;
        /** Close/dismiss key: lives inside the grid, never sends a keycode. */
        final boolean close;

        /** Distance from the left edge of the board, in units. */
        float offset;

        final RectF rect = new RectF();

        KeyDef(String label, int keycode, float unitWidth) {
            this(label, null, keycode, unitWidth, false, false, false);
        }

        KeyDef(String label, String shiftLabel, int keycode, float unitWidth) {
            this(label, shiftLabel, keycode, unitWidth, false, false, false);
        }

        KeyDef(String label, int keycode, float unitWidth, boolean modifier) {
            this(label, null, keycode, unitWidth, modifier, false, false);
        }

        private KeyDef(String label, String shiftLabel, int keycode, float unitWidth,
                       boolean modifier, boolean spacer, boolean close) {
            this.label = label;
            this.shiftLabel = shiftLabel;
            this.keycode = keycode;
            this.unitWidth = unitWidth;
            this.modifier = modifier;
            this.spacer = spacer;
            this.close = close;
        }
    }

    private static KeyDef key(String label, int keycode) {
        return new KeyDef(label, keycode, 1f);
    }

    private static KeyDef key(String label, int keycode, float unitWidth) {
        return new KeyDef(label, keycode, unitWidth);
    }

    private static KeyDef key(String label, String shiftLabel, int keycode) {
        return new KeyDef(label, shiftLabel, keycode, 1f);
    }

    private static KeyDef key(String label, String shiftLabel, int keycode, float unitWidth) {
        return new KeyDef(label, shiftLabel, keycode, unitWidth);
    }

    private static KeyDef mod(String label, int keycode, float unitWidth) {
        return new KeyDef(label, keycode, unitWidth, true);
    }

    /** Invisible horizontal gap (used to group the F-row like a real keyboard). */
    private static KeyDef gap(float units) {
        return new KeyDef(null, null, 0, units, false, true, false);
    }

    /** Dismiss key rendered inside the key grid. */
    private static KeyDef closeKey() {
        return new KeyDef("\u2715", null, 0, 1f, false, false, true);
    }

    // ----------------------------------------------------------------------------------
    // State
    // ----------------------------------------------------------------------------------

    private final NvConnection conn;
    private final KeyboardTranslator translator;
    private boolean hapticEnabled = true;

    private final List<List<KeyDef>> rows = new ArrayList<>();

    private final Map<Integer, KeyDef> activePointers = new HashMap<>();
    private final Set<KeyDef> pressedKeys = new HashSet<>();
    private final Set<KeyDef> lockedModifiers = new HashSet<>();
    private boolean shiftActive = false;
    private boolean capsLockOn = false;

    // Window chrome
    private final RectF dragBarRect = new RectF();
    private final RectF resizeZoneRect = new RectF();

    private int dragPointerId = -1;
    private float dragStartRawX, dragStartRawY;
    private float viewStartX, viewStartY;

    private int resizePointerId = -1;
    private float resizeStartRawY;
    private int viewStartHeight;

    // ----------------------------------------------------------------------------------
    // Paints & palette
    // ----------------------------------------------------------------------------------

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Flat ~70% opaque palette, shared with the mouse overlay.
    private static final int COLOR_PANEL_BG = 0xB312141A;
    private static final int COLOR_PANEL_BORDER = 0x2EFFFFFF;

    private static final int COLOR_KEY_BG = 0x59232931;
    private static final int COLOR_KEY_BG_MOD = 0x4D1B1F27;
    private static final int COLOR_KEY_BORDER = 0x22FFFFFF;

    private static final int COLOR_KEY_PRESSED = 0xE600B4D8;
    private static final int COLOR_KEY_LOCKED = 0xE6FB8500;
    private static final int COLOR_KEY_CLOSE = 0x59D90429;
    private static final int COLOR_KEY_CLOSE_PRESSED = 0xE6D90429;

    private static final int COLOR_PRESSED_BORDER = 0xFFFFFFFF;
    private static final int COLOR_LOCKED_BORDER = 0xFFFFE0A3;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0x99FFFFFF;
    private static final int COLOR_ACCENT = 0xFF7BE9FF;

    // ----------------------------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------------------------

    public PcKeyboardOverlayView(Context context, NvConnection conn, KeyboardTranslator translator) {
        super(context);
        this.conn = conn;
        this.translator = translator;

        setFocusable(false);
        setClickable(true);

        initPaints();
        build75PercentLayout();
    }

    public void setHapticEnabled(boolean enabled) {
        this.hapticEnabled = enabled;
    }

    public void setVibrationDuration(int durationMs) {
        // Haptics use a fixed predefined click effect; kept for API compatibility.
    }

    private void triggerVibration() {
        if (!hapticEnabled) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void initPaints() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(COLOR_PANEL_BG);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.2f));
        borderPaint.setColor(COLOR_PANEL_BORDER);

        capPaint.setStyle(Paint.Style.FILL);

        capBorderPaint.setStyle(Paint.Style.STROKE);
        capBorderPaint.setStrokeWidth(dpToPx(1f));
        capBorderPaint.setColor(COLOR_KEY_BORDER);

        textPaint.setColor(COLOR_TEXT);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        subTextPaint.setColor(COLOR_TEXT_DIM);
        subTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setColor(0xFFFFB703);

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(0x4DFFFFFF);
    }

    // ----------------------------------------------------------------------------------
    // Layout definition
    // ----------------------------------------------------------------------------------

    /**
     * Builds a standard 75% ANSI board. Every row sums to exactly 16u:
     *
     * <pre>
     * Esc | F1-F4 | F5-F8 | F9-F12 | Del | X
     * ` 1..0 - =            Backsp        |   Home
     * Tab  Q W E R T Y U I O P  [ ]  \    |   PgUp
     * Caps  A S D F G H J K L   ; ' Enter |   PgDn
     * Shift  Z X C V B N M , . /  Shift  Up | End
     * Ctrl Win Alt  Space  Alt Menu Ctrl  L D R
     * </pre>
     */
    private void build75PercentLayout() {
        rows.clear();

        // Row 0 - function row: Esc | F1-F4 | F5-F8 | F9-F12 | Del | Close
        // (1 + 0.25 + 4 + 0.25 + 4 + 0.25 + 4 + 0.25 + 1 + 1 = 16u)
        rows.add(makeRow(
                key("Esc", KeyEvent.KEYCODE_ESCAPE),
                gap(0.25f),
                key("F1", KeyEvent.KEYCODE_F1), key("F2", KeyEvent.KEYCODE_F2),
                key("F3", KeyEvent.KEYCODE_F3), key("F4", KeyEvent.KEYCODE_F4),
                gap(0.25f),
                key("F5", KeyEvent.KEYCODE_F5), key("F6", KeyEvent.KEYCODE_F6),
                key("F7", KeyEvent.KEYCODE_F7), key("F8", KeyEvent.KEYCODE_F8),
                gap(0.25f),
                key("F9", KeyEvent.KEYCODE_F9), key("F10", KeyEvent.KEYCODE_F10),
                key("F11", KeyEvent.KEYCODE_F11), key("F12", KeyEvent.KEYCODE_F12),
                gap(0.25f),
                key("Del", KeyEvent.KEYCODE_FORWARD_DEL),
                closeKey()));

        // Row 1 - number row (13 + 2 + 1 = 16u)
        rows.add(makeRow(
                key("`", "~", KeyEvent.KEYCODE_GRAVE),
                key("1", "!", KeyEvent.KEYCODE_1), key("2", "@", KeyEvent.KEYCODE_2),
                key("3", "#", KeyEvent.KEYCODE_3), key("4", "$", KeyEvent.KEYCODE_4),
                key("5", "%", KeyEvent.KEYCODE_5), key("6", "^", KeyEvent.KEYCODE_6),
                key("7", "&", KeyEvent.KEYCODE_7), key("8", "*", KeyEvent.KEYCODE_8),
                key("9", "(", KeyEvent.KEYCODE_9), key("0", ")", KeyEvent.KEYCODE_0),
                key("-", "_", KeyEvent.KEYCODE_MINUS),
                key("=", "+", KeyEvent.KEYCODE_EQUALS),
                key("Back", KeyEvent.KEYCODE_DEL, 2f),
                key("Home", KeyEvent.KEYCODE_MOVE_HOME)));

        // Row 2 - QWERTY (1.5 + 10 + 1 + 1 + 1.5 + 1 = 16u)
        rows.add(makeRow(
                key("Tab", KeyEvent.KEYCODE_TAB, 1.5f),
                key("Q", KeyEvent.KEYCODE_Q), key("W", KeyEvent.KEYCODE_W),
                key("E", KeyEvent.KEYCODE_E), key("R", KeyEvent.KEYCODE_R),
                key("T", KeyEvent.KEYCODE_T), key("Y", KeyEvent.KEYCODE_Y),
                key("U", KeyEvent.KEYCODE_U), key("I", KeyEvent.KEYCODE_I),
                key("O", KeyEvent.KEYCODE_O), key("P", KeyEvent.KEYCODE_P),
                key("[", "{", KeyEvent.KEYCODE_LEFT_BRACKET),
                key("]", "}", KeyEvent.KEYCODE_RIGHT_BRACKET),
                key("\\", "|", KeyEvent.KEYCODE_BACKSLASH, 1.5f),
                key("PgUp", KeyEvent.KEYCODE_PAGE_UP)));

        // Row 3 - home row (1.75 + 9 + 1 + 1 + 2.25 + 1 = 16u)
        rows.add(makeRow(
                key("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 1.75f),
                key("A", KeyEvent.KEYCODE_A), key("S", KeyEvent.KEYCODE_S),
                key("D", KeyEvent.KEYCODE_D), key("F", KeyEvent.KEYCODE_F),
                key("G", KeyEvent.KEYCODE_G), key("H", KeyEvent.KEYCODE_H),
                key("J", KeyEvent.KEYCODE_J), key("K", KeyEvent.KEYCODE_K),
                key("L", KeyEvent.KEYCODE_L),
                key(";", ":", KeyEvent.KEYCODE_SEMICOLON),
                key("'", "\"", KeyEvent.KEYCODE_APOSTROPHE),
                key("Enter", KeyEvent.KEYCODE_ENTER, 2.25f),
                key("PgDn", KeyEvent.KEYCODE_PAGE_DOWN)));

        // Row 4 - bottom letter row + arrow cluster (2.25 + 7 + 3 + 1.75 + 1 + 1 = 16u)
        rows.add(makeRow(
                mod("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, 2.25f),
                key("Z", KeyEvent.KEYCODE_Z), key("X", KeyEvent.KEYCODE_X),
                key("C", KeyEvent.KEYCODE_C), key("V", KeyEvent.KEYCODE_V),
                key("B", KeyEvent.KEYCODE_B), key("N", KeyEvent.KEYCODE_N),
                key("M", KeyEvent.KEYCODE_M),
                key(",", "<", KeyEvent.KEYCODE_COMMA),
                key(".", ">", KeyEvent.KEYCODE_PERIOD),
                key("/", "?", KeyEvent.KEYCODE_SLASH),
                mod("Shift", KeyEvent.KEYCODE_SHIFT_RIGHT, 1.75f),
                key("\u25B2", KeyEvent.KEYCODE_DPAD_UP),
                key("End", KeyEvent.KEYCODE_MOVE_END)));

        // Row 5 - modifier row with inverted-T arrow cluster (13 + 3 = 16u)
        rows.add(makeRow(
                mod("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 1.25f),
                mod("Win", KeyEvent.KEYCODE_META_LEFT, 1.25f),
                mod("Alt", KeyEvent.KEYCODE_ALT_LEFT, 1.25f),
                key("Space", KeyEvent.KEYCODE_SPACE, 5.5f),
                mod("Alt", KeyEvent.KEYCODE_ALT_RIGHT, 1.25f),
                key("Menu", KeyEvent.KEYCODE_MENU, 1.25f),
                mod("Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 1.25f),
                key("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT),
                key("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN),
                key("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT)));
    }

    private List<KeyDef> makeRow(KeyDef... keys) {
        List<KeyDef> row = new ArrayList<>(keys.length);
        float offset = 0f;
        for (KeyDef k : keys) {
            k.offset = offset;
            offset += k.unitWidth;
            row.add(k);
        }
        return row;
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // ----------------------------------------------------------------------------------
    // Sizing
    // ----------------------------------------------------------------------------------

    /** Total vertical chrome (padding, drag bar, resize zone) in px. */
    private float chromeHeightPx() {
        return dpToPx(TOP_PAD_DP + DRAG_BAR_H_DP + KEY_TOP_GAP_DP + RESIZE_ZONE_DP);
    }

    /** Height required so that every 1u key stays perfectly square for the given width. */
    public int calculatePerfectHeight(int width) {
        float padH = dpToPx(PAD_H_DP);
        float gap = dpToPx(GAP_DP);
        float usableW = width - padH * 2f;
        if (usableW <= 0f) {
            return 0;
        }
        float pitch = (usableW + gap) / ROW_UNITS;
        float rowH = pitch - gap;
        if (rowH <= 1f) {
            return 0;
        }
        return Math.round(chromeHeightPx() + ROW_COUNT * rowH + (ROW_COUNT - 1) * gap);
    }

    /** Inverse of {@link #calculatePerfectHeight(int)} keeping the keys square. */
    private int calculateWidthForHeight(int height) {
        float padH = dpToPx(PAD_H_DP);
        float gap = dpToPx(GAP_DP);
        float rowsH = height - chromeHeightPx() - (ROW_COUNT - 1) * gap;
        float rowH = rowsH / ROW_COUNT;
        if (rowH < 1f) {
            rowH = 1f;
        }
        float pitch = rowH + gap;
        return Math.round(ROW_UNITS * pitch - gap + padH * 2f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int width, int height) {
        if (width <= 0 || height <= 0 || rows.isEmpty()) {
            return;
        }

        float padH = dpToPx(PAD_H_DP);
        float gap = dpToPx(GAP_DP);
        float topPad = dpToPx(TOP_PAD_DP);
        float dragBarH = dpToPx(DRAG_BAR_H_DP);

        float usableW = width - padH * 2f;
        if (usableW <= 0f) {
            return;
        }

        // Uniform unit pitch: a key spanning N units is N*pitch - gap wide and its left edge
        // lands exactly N*pitch from the board's left margin. This keeps every row aligned.
        float pitch = (usableW + gap) / ROW_UNITS;
        float rowH = pitch - gap;
        if (rowH <= 1f) {
            return;
        }

        // Window chrome: a thin drag strip on top and a thin resize strip at the bottom.
        dragBarRect.set(padH, topPad, width - padH, topPad + dragBarH);
        resizeZoneRect.set(padH, height - dpToPx(RESIZE_ZONE_DP), width - padH, height);

        float curY = topPad + dragBarH + dpToPx(KEY_TOP_GAP_DP);
        for (List<KeyDef> row : rows) {
            float top = curY;
            float bottom = curY + rowH;

            for (KeyDef k : row) {
                if (k.spacer) {
                    continue;
                }
                float left = padH + k.offset * pitch;
                float right = left + k.unitWidth * pitch - gap;
                k.rect.set(left, top, right, bottom);
            }

            curY += rowH + gap;
        }
    }

    // ----------------------------------------------------------------------------------
    // Drawing
    // ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float panelRadius = dpToPx(12f);

        // 1. Flat translucent panel (~70% opaque, matching the mouse overlay)
        RectF panel = new RectF(dpToPx(1f), dpToPx(1f), getWidth() - dpToPx(1f), getHeight() - dpToPx(1f));
        canvas.drawRoundRect(panel, panelRadius, panelRadius, bgPaint);
        canvas.drawRoundRect(panel, panelRadius, panelRadius, borderPaint);

        // 2. Thin drag strip with a small centered pill
        drawPill(canvas, dpToPx(40f), dpToPx(3f), dragBarRect.centerY());

        // 3. Keys
        for (List<KeyDef> row : rows) {
            for (KeyDef k : row) {
                if (!k.spacer) {
                    drawKey(canvas, k);
                }
            }
        }

        // 4. Thin resize strip
        drawPill(canvas, dpToPx(64f), dpToPx(3f), resizeZoneRect.centerY());
    }

    private void drawPill(Canvas canvas, float pillW, float pillH, float centerY) {
        float x = (getWidth() - pillW) / 2f;
        float y = centerY - pillH / 2f;
        canvas.drawRoundRect(new RectF(x, y, x + pillW, y + pillH),
                pillH / 2f, pillH / 2f, pillPaint);
    }

    private void drawKey(Canvas canvas, KeyDef k) {
        boolean pressed = pressedKeys.contains(k);
        boolean locked = lockedModifiers.contains(k)
                || (k.keycode == KeyEvent.KEYCODE_CAPS_LOCK && capsLockOn);

        int fill;
        int border;
        if (k.close) {
            fill = pressed ? COLOR_KEY_CLOSE_PRESSED : COLOR_KEY_CLOSE;
            border = pressed ? 0xFFFFFFFF : 0x44FFFFFF;
        } else if (locked) {
            fill = COLOR_KEY_LOCKED;
            border = COLOR_LOCKED_BORDER;
        } else if (pressed) {
            fill = COLOR_KEY_PRESSED;
            border = COLOR_PRESSED_BORDER;
        } else {
            fill = k.modifier ? COLOR_KEY_BG_MOD : COLOR_KEY_BG;
            border = COLOR_KEY_BORDER;
        }

        float radius = dpToPx(5f);
        capPaint.setColor(fill);
        canvas.drawRoundRect(k.rect, radius, radius, capPaint);

        capBorderPaint.setColor(border);
        capBorderPaint.setStrokeWidth(dpToPx((pressed || locked) ? 1.6f : 0.9f));
        canvas.drawRoundRect(k.rect, radius, radius, capBorderPaint);

        // Sticky / caps-lock status dot
        if (locked) {
            canvas.drawCircle(k.rect.right - dpToPx(6f), k.rect.top + dpToPx(6f),
                    dpToPx(2.4f), indicatorPaint);
        }

        drawLegends(canvas, k, shiftActive || capsLockOn, pressed || locked);
    }

    private void drawLegends(Canvas canvas, KeyDef k, boolean shifted, boolean active) {
        float w = k.rect.width();
        float h = k.rect.height();
        if (w <= 0f || h <= 0f) {
            return;
        }

        int textColor = COLOR_TEXT;
        float cx = k.rect.centerX();

        if (k.shiftLabel != null) {
            // Real-keycap style: shifted symbol on top, primary symbol below.
            float topSize = fitTextSize(subTextPaint, k.shiftLabel, w * 0.74f, h * 0.38f, h * 0.32f);
            subTextPaint.setTextSize(topSize);
            subTextPaint.setColor(active ? textColor : (shifted ? COLOR_ACCENT : COLOR_TEXT_DIM));

            float botSize = fitTextSize(textPaint, k.label, w * 0.74f, h * 0.42f, h * 0.40f);
            textPaint.setTextSize(botSize);
            textPaint.setColor(textColor);

            canvas.drawText(k.shiftLabel, cx, k.rect.top + h * 0.44f, subTextPaint);
            canvas.drawText(k.label, cx, k.rect.top + h * 0.89f, textPaint);
        } else {
            String label = k.label;
            // Letters follow the shift/caps state, like a soft keyboard.
            if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
                label = shifted ? label.toUpperCase() : label.toLowerCase();
            }

            float size = fitTextSize(textPaint, label, w * 0.8f, h * 0.56f, h * 0.46f);
            textPaint.setTextSize(size);
            textPaint.setColor(textColor);

            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = k.rect.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, baseline, textPaint);
        }
    }

    /** Shrinks {@code paint} until {@code text} fits inside the given box, returning the size. */
    private float fitTextSize(Paint paint, String text, float maxWidth, float maxHeight, float startSize) {
        float size = Math.max(1f, startSize);
        paint.setTextSize(size);

        float width = paint.measureText(text);
        if (width > maxWidth && width > 0f) {
            size *= maxWidth / width;
        }

        paint.setTextSize(size);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float height = fm.descent - fm.ascent;
        if (height > maxHeight && height > 0f) {
            size *= maxHeight / height;
        }

        return Math.max(1f, size);
    }

    // ----------------------------------------------------------------------------------
    // Input
    // ----------------------------------------------------------------------------------

    private KeyDef findKeyAt(float x, float y) {
        for (List<KeyDef> row : rows) {
            for (KeyDef k : row) {
                if (!k.spacer && k.rect.contains(x, y)) {
                    return k;
                }
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerId = event.getPointerId(actionIndex);
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);

                if (dragPointerId == -1 && resizePointerId == -1 && dragBarRect.contains(x, y)) {
                    dragPointerId = pointerId;
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    viewStartX = getX();
                    viewStartY = getY();
                    return true;
                }

                if (dragPointerId == -1 && resizePointerId == -1 && resizeZoneRect.contains(x, y)) {
                    resizePointerId = pointerId;
                    resizeStartRawY = event.getRawY();
                    viewStartHeight = getHeight();
                    return true;
                }

                KeyDef key = findKeyAt(x, y);
                if (key != null) {
                    activePointers.put(pointerId, key);
                    handleKeyDown(key);
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (dragPointerId != -1) {
                    int index = event.findPointerIndex(dragPointerId);
                    if (index >= 0) {
                        float dx = event.getRawX() - dragStartRawX;
                        float dy = event.getRawY() - dragStartRawY;
                        View parent = (View) getParent();
                        if (parent != null) {
                            float newX = Math.max(0, Math.min(parent.getWidth() - getWidth(), viewStartX + dx));
                            float newY = Math.max(0, Math.min(parent.getHeight() - getHeight(), viewStartY + dy));
                            setX(newX);
                            setY(newY);
                        }
                    }
                }

                if (resizePointerId != -1) {
                    int index = event.findPointerIndex(resizePointerId);
                    if (index >= 0) {
                        float dy = event.getRawY() - resizeStartRawY;
                        int minHeight = (int) dpToPx(140f);
                        int newHeight = Math.max(minHeight, (int) (viewStartHeight + dy));
                        int newWidth = calculateWidthForHeight(newHeight);

                        View parent = (View) getParent();
                        if (parent != null) {
                            int maxWidth = parent.getWidth();
                            if (newWidth > maxWidth) {
                                newWidth = maxWidth;
                                newHeight = calculatePerfectHeight(newWidth);
                            }
                            int maxHeight = parent.getHeight();
                            if (newHeight > maxHeight) {
                                newHeight = maxHeight;
                                newWidth = calculateWidthForHeight(newHeight);
                            }
                        }

                        ViewGroup.LayoutParams lp = getLayoutParams();
                        if (lp != null && (lp.width != newWidth || lp.height != newHeight)) {
                            lp.width = newWidth;
                            lp.height = newHeight;
                            setLayoutParams(lp);
                        }
                    }
                    return true;
                }

                int pointerCount = event.getPointerCount();
                boolean changed = false;
                float slop = dpToPx(2f);
                for (int p = 0; p < pointerCount; p++) {
                    int pointerId = event.getPointerId(p);
                    if (pointerId == dragPointerId || pointerId == resizePointerId) {
                        continue;
                    }

                    KeyDef held = activePointers.get(pointerId);
                    if (held == null) {
                        // Keys are only acquired on ACTION_DOWN - sliding never presses a key.
                        continue;
                    }

                    float x = event.getX(p);
                    float y = event.getY(p);
                    if (x < held.rect.left - slop || x > held.rect.right + slop
                            || y < held.rect.top - slop || y > held.rect.bottom + slop) {
                        // Sliding off a key releases it and never triggers its neighbours.
                        handleKeyUp(held);
                        activePointers.remove(pointerId);
                        changed = true;
                    }
                }
                if (changed) {
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(actionIndex);

                if (pointerId == dragPointerId) {
                    dragPointerId = -1;
                    return true;
                }
                if (pointerId == resizePointerId) {
                    resizePointerId = -1;
                    return true;
                }

                KeyDef key = activePointers.remove(pointerId);
                if (key != null) {
                    handleKeyUp(key);
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragPointerId = -1;
                resizePointerId = -1;

                for (KeyDef k : activePointers.values()) {
                    handleKeyUp(k);
                }
                activePointers.clear();
                invalidate();
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    // ----------------------------------------------------------------------------------
    // Key handling
    // ----------------------------------------------------------------------------------

    private void handleKeyDown(KeyDef key) {
        triggerVibration();

        // The dismiss key lives inside the grid and never reaches the host.
        if (key.close) {
            pressedKeys.add(key);
            invalidate();
            hideKeyboard();
            return;
        }

        if (key.keycode == KeyEvent.KEYCODE_CAPS_LOCK) {
            capsLockOn = !capsLockOn;
            pressedKeys.add(key);
            sendKeyTap(key.keycode);
            return;
        }

        // Modifiers behave as sticky toggles so shortcuts can be entered with one finger.
        if (key.modifier) {
            if (lockedModifiers.contains(key)) {
                lockedModifiers.remove(key);
                pressedKeys.remove(key);
                sendKeyPacket(key.keycode, false);
            } else {
                lockedModifiers.add(key);
                pressedKeys.add(key);
                sendKeyPacket(key.keycode, true);
            }
            updateShiftState();
            return;
        }

        if (pressedKeys.add(key)) {
            updateShiftState();
            sendKeyPacket(key.keycode, true);
        }
    }

    private void handleKeyUp(KeyDef key) {
        if (key.close) {
            pressedKeys.remove(key);
            return;
        }

        if (key.keycode == KeyEvent.KEYCODE_CAPS_LOCK) {
            pressedKeys.remove(key);
            return;
        }

        if (key.modifier && lockedModifiers.contains(key)) {
            // Sticky modifier stays held until it is tapped again.
            return;
        }

        if (pressedKeys.remove(key)) {
            sendKeyPacket(key.keycode, false);
            updateShiftState();
        }
    }

    private void updateShiftState() {
        boolean hasShift = false;

        for (KeyDef k : pressedKeys) {
            if (k.keycode == KeyEvent.KEYCODE_SHIFT_LEFT || k.keycode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                hasShift = true;
                break;
            }
        }
        if (!hasShift) {
            for (KeyDef k : lockedModifiers) {
                if (k.keycode == KeyEvent.KEYCODE_SHIFT_LEFT || k.keycode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                    hasShift = true;
                    break;
                }
            }
        }

        shiftActive = hasShift;
    }

    private byte computeModifierMask() {
        byte mod = 0;
        Set<KeyDef> allActive = new HashSet<>(pressedKeys);
        allActive.addAll(lockedModifiers);

        for (KeyDef k : allActive) {
            if (k.keycode == KeyEvent.KEYCODE_SHIFT_LEFT || k.keycode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                mod |= KeyboardPacket.MODIFIER_SHIFT;
            } else if (k.keycode == KeyEvent.KEYCODE_CTRL_LEFT || k.keycode == KeyEvent.KEYCODE_CTRL_RIGHT) {
                mod |= KeyboardPacket.MODIFIER_CTRL;
            } else if (k.keycode == KeyEvent.KEYCODE_ALT_LEFT || k.keycode == KeyEvent.KEYCODE_ALT_RIGHT) {
                mod |= KeyboardPacket.MODIFIER_ALT;
            } else if (k.keycode == KeyEvent.KEYCODE_META_LEFT || k.keycode == KeyEvent.KEYCODE_META_RIGHT) {
                mod |= KeyboardPacket.MODIFIER_META;
            }
        }
        return mod;
    }

    private void sendKeyTap(int keycode) {
        sendKeyPacket(keycode, true);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                sendKeyPacket(keycode, false);
            }
        }, 40);
    }

    private void sendKeyPacket(int keycode, boolean down) {
        if (conn == null || translator == null) {
            return;
        }
        short translated = translator.translate(keycode, -1);
        byte mod = computeModifierMask();
        byte action = down ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP;
        conn.sendKeyboardInput(translated, action, mod, (byte) 0);
    }

    // ----------------------------------------------------------------------------------
    // Visibility
    // ----------------------------------------------------------------------------------

    public void showKeyboard() {
        setVisibility(View.VISIBLE);
    }

    public void hideKeyboard() {
        // Never leave keys stuck on the host.
        for (KeyDef k : pressedKeys) {
            if (!k.modifier && !k.close && k.keycode != KeyEvent.KEYCODE_CAPS_LOCK) {
                sendKeyPacket(k.keycode, false);
            }
        }
        for (KeyDef k : lockedModifiers) {
            sendKeyPacket(k.keycode, false);
        }

        lockedModifiers.clear();
        pressedKeys.clear();
        activePointers.clear();
        shiftActive = false;
        dragPointerId = -1;
        resizePointerId = -1;
        invalidate();

        setVisibility(View.GONE);
    }

    public void toggleKeyboard() {
        if (getVisibility() == View.VISIBLE) {
            hideKeyboard();
        } else {
            showKeyboard();
        }
    }
}
