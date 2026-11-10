package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

@SuppressLint("ViewConstructor")
public class VirtualKeyView extends View {

    public interface KeyActionListener {
        void onKeyDown(VirtualKey key);
        void onKeyUp(VirtualKey key);
        void onKeyTouchEvent(VirtualKeyView source, MotionEvent event);
        void onExplicitKeyDown(int keycode);
        void onExplicitKeyUp(int keycode);
        void onScroll(short scrollAmount);
    }

    private final VirtualKey key;
    private final KeyActionListener listener;

    private boolean isPressed = false;
    public boolean isSelected = false;
    private boolean hapticFeedbackEnabled = true;

    /**
     * Kept for API compatibility. Haptics use a predefined click effect whose duration is
     * controlled by the platform, so a custom duration is not honoured.
     */
    public void setVibrationDuration(int durationMs) {
    }

    private void triggerVibration() {
        if (!hapticFeedbackEnabled) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                android.os.Vibrator vibrator = (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK));
                    return;
                }
            } catch (Exception ignored) {}
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    // Joystick / Controller states
    private float knobX = 0f;
    private float knobY = 0f;
    private boolean isJoystickActive = false;
    private boolean upActive = false;
    private boolean downActive = false;
    private boolean leftActive = false;
    private boolean rightActive = false;

    // Scroll Controller states
    private int scrollPointerId = -1;
    private float currentKnobY = 0f;
    private long lastScrollHapticTime = 0L;
    private float scrollTickAccumulator = 0f;
    private boolean isScrollActive = false;
    private final android.os.Handler scrollHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private float getScrollSpeedFactor() {
        try {
            int speed = PreferenceManager.getDefaultSharedPreferences(getContext())
                    .getInt("seekbar_scroll_speed", 5);
            return Math.max(1, speed) * 1.5f;
        } catch (Exception e) {
            return 7.5f;
        }
    }

    private final Runnable continuousScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScrollActive && scrollPointerId != -1) {
                float centerY = bounds.centerY();
                float maxTravel = Math.max(1f, (bounds.height() - dpToPx(36f)) / 2f);
                float norm = (currentKnobY - centerY) / maxTravel;
                norm = Math.max(-1f, Math.min(1f, norm));

                float absNorm = Math.abs(norm);
                float deadzone = 0.08f;

                if (absNorm > deadzone) {
                    float deflection = (absNorm - deadzone) / (1f - deadzone);
                    // Smooth power curve (1.5): gentle line-by-line control at center, smoothly ramps to peak speed at edge
                    float curved = (float) Math.pow(deflection, 1.5);
                    float signedVelocity = Math.signum(norm) * curved;

                    float factor = getScrollSpeedFactor();
                    // Velocity is strictly governed by deflection: at max deflection with default speed (factor=7.5),
                    // sends ~120 ticks (1 full mouse wheel notch) every 20ms (~6000 ticks/sec).
                    float targetScroll = -signedVelocity * factor * 16f;
                    scrollTickAccumulator += targetScroll;
                    short scrollTick = (short) scrollTickAccumulator;

                    if (scrollTick != 0) {
                        scrollTickAccumulator -= scrollTick;
                        if (listener != null) {
                            listener.onScroll(scrollTick);
                        }
                    }

                    long now = SystemClock.uptimeMillis();
                    long hapticInterval = (long) (300 - (curved * 180));
                    if (hapticFeedbackEnabled && absNorm > 0.20f && (now - lastScrollHapticTime >= hapticInterval)) {
                        triggerVibration();
                        lastScrollHapticTime = now;
                    }
                } else {
                    scrollTickAccumulator = 0f;
                }
                scrollHandler.postDelayed(this, 20);
            }
        }
    };

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    // Shared paints / geometry reused every frame so onDraw() never allocates.
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dirPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmpRect = new RectF();

    private final RectF bounds = new RectF();
    private final Rect textBounds = new Rect();

    // Default colors
    private static final int COLOR_NORMAL_BASE = 0x181A1B;
    private static final int COLOR_PRESSED_BG = 0xD000B4D8;
    private static final int COLOR_NORMAL_STROKE = 0x55FFFFFF;
    private static final int COLOR_ACTIVE_STROKE = 0xFFFFFFFF;
    private static final int COLOR_SELECTED_STROKE = 0xFF00E5FF;

    // Feature colors & icons (Prototype design)
    private static final int COLOR_SLIDE_ICON = 0xFF3898EC;
    private static final int COLOR_HOLD_OUTSIDE_ICON = 0xFFA08E38;
    private static final int COLOR_HOLD_OUTSIDE_PRESSED_BG = 0xD9948232;

    private static final float MAX_FONT_SIZE_SP = 22f;

    public VirtualKeyView(Context context, VirtualKey key, KeyActionListener listener) {
        super(context);
        this.key = key;
        this.listener = listener;

        setFocusable(false);
        setClickable(true);

        bgPaint.setStyle(Paint.Style.FILL);

        // Thin 1px/1dp border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(1f));

        selectPaint.setStyle(Paint.Style.STROKE);
        selectPaint.setStrokeWidth(3f); // 3px border
        selectPaint.setColor(COLOR_SELECTED_STROKE);
        selectPaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0));

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTypeface(BOLD_TYPEFACE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        iconStrokePaint.setStyle(Paint.Style.STROKE);
        iconStrokePaint.setStrokeWidth(dpToPx(1.8f));
        iconStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        iconStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setColor(0x22FFFFFF);
        guidePaint.setStrokeWidth(dpToPx(1f));

        dirPaint.setTextAlign(Paint.Align.CENTER);
        dirPaint.setTypeface(BOLD_TYPEFACE);

        knobPaint.setStyle(Paint.Style.FILL);

        knobBorderPaint.setStyle(Paint.Style.STROKE);
        knobBorderPaint.setStrokeWidth(dpToPx(1.5f));

        dotPaint.setStyle(Paint.Style.FILL);

        handlePaint.setStyle(Paint.Style.FILL);

        gripPaint.setStyle(Paint.Style.STROKE);
        gripPaint.setStrokeWidth(dpToPx(1.5f));

        arrowPaint.setTextAlign(Paint.Align.CENTER);
        arrowPaint.setTypeface(BOLD_TYPEFACE);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(0x28FFFFFF);
        linePaint.setStrokeWidth(dpToPx(1.5f));

        blockLabelPaint.setColor(0xAA00E5FF);
        blockLabelPaint.setTextSize(dpToPx(11f));
        blockLabelPaint.setTypeface(BOLD_TYPEFACE);
        blockLabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public VirtualKey getKey() {
        return key;
    }

    public void setOpacity(int opacityPercent) {
        float factor = Math.max(0.1f, Math.min(1.0f, opacityPercent / 100f));
        setAlpha(factor);
    }

    public void setHapticEnabled(boolean enabled) {
        this.hapticFeedbackEnabled = enabled;
        setHapticFeedbackEnabled(enabled);
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        invalidate();
    }

    public boolean isInResizeHandle(float localX, float localY) {
        if (!isSelected) return false;
        float touchThreshold = Math.min(Math.min(getWidth(), getHeight()) * 0.5f, dpToPx(42f));
        return localX >= (getWidth() - touchThreshold) && localY >= (getHeight() - touchThreshold);
    }

    private final int[] tempLoc = new int[2];
    public final java.util.Set<Integer> activeSlidePointers = new java.util.HashSet<>();
    private final java.util.Set<Integer> pointerDownInside = new java.util.HashSet<>();

    public boolean containsScreenPoint(float screenX, float screenY) {
        if (getVisibility() != View.VISIBLE) return false;
        getLocationOnScreen(tempLoc);
        int left = tempLoc[0];
        int top = tempLoc[1];
        int right = left + getWidth();
        int bottom = top + getHeight();
        return screenX >= left && screenX <= right && screenY >= top && screenY <= bottom;
    }

    /** True when this key lets touches pass through to the stream (Absorb Touch disabled). */
    public boolean isPassThrough() {
        return key.type != VirtualKey.KeyType.ACTION
                && !key.isJoystick && !key.isScroll && !key.isBlockTouch && !key.absorbTouch;
    }

    /** Remembers that a pointer physically landed on this key (used for pass-through keys). */
    public void markPointerDownInside(int pointerId) {
        pointerDownInside.add(pointerId);
    }

    public boolean isPointerDownInside(int pointerId) {
        return pointerDownInside.contains(pointerId);
    }

    public void clearPointerDownInside(int pointerId) {
        pointerDownInside.remove(pointerId);
    }

    public void onSlidePointerEnter(int pointerId) {
        // Pass-through keys are only pressed when the finger originally landed on them.
        if (!key.isSlide && key.absorbTouch) return;
        activeSlidePointers.add(pointerId);

        if (!isPressed) {
            isPressed = true;
            triggerVibration();
            if (listener != null) {
                listener.onKeyDown(key);
            }
            invalidate();
        }
    }

    public void onSlidePointerExit(int pointerId) {
        activeSlidePointers.remove(pointerId);

        if (!key.holdOutside && activeSlidePointers.isEmpty() && isPressed) {
            isPressed = false;
            if (listener != null) {
                listener.onKeyUp(key);
            }
            invalidate();
        }
    }

    public void clearAllSlidePointers() {
        activeSlidePointers.clear();
        pointerDownInside.clear();
        if (isPressed) {
            isPressed = false;
            if (listener != null) {
                listener.onKeyUp(key);
            }
            invalidate();
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokePaint.getStrokeWidth();
        bounds.set(pad, pad, w - pad, h - pad);

        // Auto font size with capped maximum
        float autoSize = Math.min(w * 0.38f, h * 0.45f);
        if (key.label.length() > 3) {
            autoSize = Math.min(w * 0.22f, h * 0.32f);
        }
        float maxPx = spToPx(MAX_FONT_SIZE_SP);
        float finalSize = Math.min(maxPx, Math.max(12f, autoSize));
        textPaint.setTextSize(finalSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (key.isJoystick) {
            drawJoystick(canvas);
            return;
        }

        if (key.isScroll) {
            drawScrollController(canvas);
            return;
        }

        float cornerRadiusPx = dpToPx(key.cornerRadius);
        if (cornerRadiusPx > Math.min(bounds.width(), bounds.height()) / 2f) {
            cornerRadiusPx = Math.min(bounds.width(), bounds.height()) / 2f;
        }

        // Block Touch rendering
        if (key.isBlockTouch) {
            if (key.bgOpacity > 0) {
                int alpha = Math.round((key.bgOpacity / 100f) * 255);
                bgPaint.setColor(Color.argb(alpha, Color.red(COLOR_NORMAL_BASE), Color.green(COLOR_NORMAL_BASE), Color.blue(COLOR_NORMAL_BASE)));
                canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, bgPaint);
            }

            if (key.showBorder) {
                canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, strokePaint);
            }

            if (isSelected) {
                canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, selectPaint);
                drawResizeHandle(canvas);
                canvas.drawText("[Block]", bounds.centerX(), bounds.centerY() + dpToPx(4f), blockLabelPaint);
            }
            return;
        }

        // Background color & alpha
        if (isPressed) {
            if (key.holdOutside) {
                bgPaint.setColor(COLOR_HOLD_OUTSIDE_PRESSED_BG);
                strokePaint.setColor(COLOR_ACTIVE_STROKE);
            } else {
                bgPaint.setColor(COLOR_PRESSED_BG);
                strokePaint.setColor(COLOR_ACTIVE_STROKE);
            }
        } else {
            int alpha = Math.round((key.bgOpacity / 100f) * 255);
            bgPaint.setColor(Color.argb(alpha, Color.red(COLOR_NORMAL_BASE), Color.green(COLOR_NORMAL_BASE), Color.blue(COLOR_NORMAL_BASE)));
            strokePaint.setColor(COLOR_NORMAL_STROKE);
        }

        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, bgPaint);

        // Thin 1px border
        if (key.showBorder || isPressed) {
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, strokePaint);
        }

        // Selection highlight in editor
        if (isSelected) {
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, selectPaint);
            drawResizeHandle(canvas);
        }

        // Label text with custom text opacity
        String label = key.label;
        int textAlpha = Math.round((key.textOpacity / 100f) * 255);
        textPaint.setColor(Color.argb(textAlpha, 255, 255, 255));
        textPaint.getTextBounds(label, 0, label.length(), textBounds);
        float x = bounds.centerX();
        float y = bounds.centerY() + (textBounds.height() / 2f) - textBounds.bottom;
        canvas.drawText(label, x, y, textPaint);

        // Slide key indicator: top-left up-chevron (Cyan #3898EC, turns white on press)
        if (key.isSlide) {
            iconStrokePaint.setColor(isPressed ? 0xFFFFFFFF : COLOR_SLIDE_ICON);
            float chevronW = dpToPx(10f);
            float chevronH = dpToPx(4.5f);
            float padX = dpToPx(8f);
            float padY = dpToPx(8f);
            float cx = bounds.left + padX + (chevronW / 2f);
            float cy = bounds.top + padY + (chevronH / 2f);
            drawUpChevron(canvas, iconStrokePaint, cx, cy, chevronW, chevronH);
        }

        // Hold on Slide Out indicator: bottom-left down-double-chevron (Olive/Mustard #A08E38, turns white on press)
        if (key.holdOutside) {
            iconStrokePaint.setColor(isPressed ? 0xFFFFFFFF : COLOR_HOLD_OUTSIDE_ICON);
            float chevronW = dpToPx(9.5f);
            float chevronH = dpToPx(3.2f);
            float spacing = dpToPx(4f);
            float padX = dpToPx(8f);
            float padY = dpToPx(8f);
            float cx = bounds.left + padX + (chevronW / 2f);
            float bottomY = bounds.bottom - padY;
            drawDownDoubleChevron(canvas, iconStrokePaint, cx, bottomY, chevronW, chevronH, spacing);
        }
    }

    /** Bottom-right triangluar resize grip shown while the key is selected in the editor. */
    private void drawResizeHandle(Canvas canvas) {
        float handleW = dpToPx(13f);
        float handleH = dpToPx(13f);

        handlePaint.setColor(COLOR_SELECTED_STROKE);
        path.reset();
        path.moveTo(bounds.right, bounds.bottom - handleH);
        path.lineTo(bounds.right, bounds.bottom);
        path.lineTo(bounds.right - handleW, bounds.bottom);
        path.close();
        canvas.drawPath(path, handlePaint);

        gripPaint.setColor(0xFF141517);
        gripPaint.setStrokeWidth(dpToPx(1.5f));
        canvas.drawLine(bounds.right - dpToPx(8f), bounds.bottom - dpToPx(3f),
                bounds.right - dpToPx(3f), bounds.bottom - dpToPx(8f), gripPaint);
    }

    private void drawUpChevron(Canvas canvas, Paint paint, float cx, float cy, float w, float h) {
        float halfW = w / 2f;
        float halfH = h / 2f;
        path.reset();
        path.moveTo(cx - halfW, cy + halfH);
        path.lineTo(cx, cy - halfH);
        path.lineTo(cx + halfW, cy + halfH);
        canvas.drawPath(path, paint);
    }

    private void drawDownDoubleChevron(Canvas canvas, Paint paint, float cx, float bottomY, float w, float h, float spacing) {
        float halfW = w / 2f;
        path.reset();

        // Top down-chevron
        float topTipY = bottomY - spacing;
        path.moveTo(cx - halfW, topTipY - h);
        path.lineTo(cx, topTipY);
        path.lineTo(cx + halfW, topTipY - h);

        // Bottom down-chevron
        path.moveTo(cx - halfW, bottomY - h);
        path.lineTo(cx, bottomY);
        path.lineTo(cx + halfW, bottomY - h);

        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Absorb Touch disabled: let the touch fall through to the stream view so the
        // mouse/camera keeps moving while the button is still pressed. The press and
        // release are driven by VirtualKeyboardController's shared touch dispatcher.
        if (isPassThrough()) {
            return false;
        }

        if (listener != null) {
            listener.onKeyTouchEvent(this, event);
        }

        if (key.isBlockTouch) {
            // Block touch: absorbs and blocks touch completely, does NOT send any keyboard or mouse input
            return true;
        }

        if (key.isScroll) {
            float cy = bounds.centerY();
            float knobH = Math.max(dpToPx(18f), bounds.height() * 0.18f);
            float halfKnobH = knobH / 2f;
            float arrowPadding = dpToPx(20f);
            float minY = bounds.top + arrowPadding + halfKnobH;
            float maxY = bounds.bottom - arrowPadding - halfKnobH;
            if (minY > maxY) {
                minY = bounds.top;
                maxY = bounds.bottom;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrollPointerId = event.getPointerId(0);
                    isScrollActive = true;
                    float touchY = event.getY();
                    float clampedDownY = Math.max(minY, Math.min(maxY, touchY));
                    currentKnobY = clampedDownY;
                    scrollTickAccumulator = 0f;
                    lastScrollHapticTime = SystemClock.uptimeMillis();
                    triggerVibration();
                    scrollHandler.removeCallbacks(continuousScrollRunnable);
                    scrollHandler.post(continuousScrollRunnable);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    int pIdx = event.findPointerIndex(scrollPointerId);
                    if (pIdx >= 0) {
                        float myY = event.getY(pIdx);
                        float clampedMoveY = Math.max(minY, Math.min(maxY, myY));
                        currentKnobY = clampedMoveY;
                        invalidate();
                    }
                    return true;
                }

                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(event.getActionIndex()) == scrollPointerId) {
                        isScrollActive = false;
                        scrollPointerId = -1;
                        currentKnobY = cy;
                        scrollTickAccumulator = 0f;
                        scrollHandler.removeCallbacks(continuousScrollRunnable);
                        invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isScrollActive = false;
                    scrollPointerId = -1;
                    currentKnobY = cy;
                    scrollTickAccumulator = 0f;
                    scrollHandler.removeCallbacks(continuousScrollRunnable);
                    invalidate();
                    return true;
            }
            return true;
        }

        if (key.isJoystick) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - dpToPx(3f);
            float knobRadius = radius * 0.36f;
            float maxTravel = Math.max(1f, radius - knobRadius);
            float deadzone = radius * 0.18f;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    triggerVibration();
                    // Fallthrough to calculate coordinates
                case MotionEvent.ACTION_MOVE:
                    float touchX = event.getX();
                    float touchY = event.getY();
                    float dx = touchX - cx;
                    float dy = touchY - cy;
                    float dist = (float) Math.hypot(dx, dy);

                    if (dist > maxTravel) {
                        knobX = cx + (dx / dist) * maxTravel;
                        knobY = cy + (dy / dist) * maxTravel;
                    } else {
                        knobX = touchX;
                        knobY = touchY;
                    }
                    isJoystickActive = true;

                    boolean newUp = false;
                    boolean newDown = false;
                    boolean newLeft = false;
                    boolean newRight = false;

                    if (dist >= deadzone) {
                        float nx = dx / dist;
                        float ny = dy / dist;
                        // sin(22.5 deg) ~ 0.3826
                        if (nx > 0.38f) newRight = true;
                        if (nx < -0.38f) newLeft = true;
                        if (ny > 0.38f) newDown = true;
                        if (ny < -0.38f) newUp = true;
                    }

                    updateDirectionState(newUp, newDown, newLeft, newRight);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    knobX = cx;
                    knobY = cy;
                    isJoystickActive = false;
                    updateDirectionState(false, false, false, false);
                    invalidate();
                    return true;
            }
            return true;
        }

        if (key.isSlide) {
            // Slide mode with absorbTouch consumes touch; otherwise passes through to stream
            return key.absorbTouch;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                triggerVibration();
                isPressed = true;
                if (listener != null) {
                    listener.onKeyDown(key);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float y = event.getY();
                boolean inside = x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
                if (!inside && isPressed) {
                    if (!key.holdOutside) {
                        isPressed = false;
                        if (listener != null) {
                            listener.onKeyUp(key);
                        }
                        invalidate();
                    }
                } else if (inside && !isPressed) {
                    isPressed = true;
                    if (listener != null) {
                        listener.onKeyDown(key);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isPressed) {
                    isPressed = false;
                    if (listener != null) {
                        listener.onKeyUp(key);
                    }
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateDirectionState(boolean up, boolean down, boolean left, boolean right) {
        if (up != upActive) {
            upActive = up;
            if (listener != null) {
                if (up) listener.onExplicitKeyDown(key.codeUp);
                else listener.onExplicitKeyUp(key.codeUp);
            }
        }
        if (down != downActive) {
            downActive = down;
            if (listener != null) {
                if (down) listener.onExplicitKeyDown(key.codeDown);
                else listener.onExplicitKeyUp(key.codeDown);
            }
        }
        if (left != leftActive) {
            leftActive = left;
            if (listener != null) {
                if (left) listener.onExplicitKeyDown(key.codeLeft);
                else listener.onExplicitKeyUp(key.codeLeft);
            }
        }
        if (right != rightActive) {
            rightActive = right;
            if (listener != null) {
                if (right) listener.onExplicitKeyDown(key.codeRight);
                else listener.onExplicitKeyUp(key.codeRight);
            }
        }
    }

    private String getDirectionLabel(int keycode, String fallbackArrow) {
        String name = KeyEvent.keyCodeToString(keycode).replace("KEYCODE_", "");
        if (name.length() > 3) {
            return fallbackArrow;
        }
        return name;
    }

    private void drawJoystick(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dpToPx(3f);

        // 1. Draw circular base
        if (key.bgOpacity > 0) {
            int alpha = Math.round((key.bgOpacity / 100f) * 255);
            bgPaint.setColor(Color.argb(alpha, 0x18, 0x1A, 0x1B));
            canvas.drawCircle(cx, cy, radius, bgPaint);
        }

        if (key.showBorder) {
            strokePaint.setColor(COLOR_NORMAL_STROKE);
            strokePaint.setStrokeWidth(dpToPx(1.2f));
            canvas.drawCircle(cx, cy, radius, strokePaint);

            // Subtle crosshair / inner guide circles
            canvas.drawCircle(cx, cy, radius * 0.45f, guidePaint);
            canvas.drawLine(cx - radius * 0.85f, cy, cx + radius * 0.85f, cy, guidePaint);
            canvas.drawLine(cx, cy - radius * 0.85f, cx, cy + radius * 0.85f, guidePaint);
        }

        // 2. Directional Indicators/Labels (Up, Down, Left, Right)
        float dirTextSize = Math.max(dpToPx(9f), radius * 0.18f);
        dirPaint.setTextSize(dirTextSize);

        // Up label
        dirPaint.setColor(upActive ? COLOR_PRESSED_BG : 0xCCFFFFFF);
        canvas.drawText(getDirectionLabel(key.codeUp, "▲"), cx, cy - radius * 0.55f, dirPaint);

        // Down label
        dirPaint.setColor(downActive ? COLOR_PRESSED_BG : 0xCCFFFFFF);
        canvas.drawText(getDirectionLabel(key.codeDown, "▼"), cx, cy + radius * 0.70f, dirPaint);

        // Left label
        dirPaint.setColor(leftActive ? COLOR_PRESSED_BG : 0xCCFFFFFF);
        canvas.drawText(getDirectionLabel(key.codeLeft, "◀"), cx - radius * 0.65f, cy + dirTextSize * 0.35f, dirPaint);

        // Right label
        dirPaint.setColor(rightActive ? COLOR_PRESSED_BG : 0xCCFFFFFF);
        canvas.drawText(getDirectionLabel(key.codeRight, "▶"), cx + radius * 0.65f, cy + dirTextSize * 0.35f, dirPaint);

        // 3. Thumb Knob (moves with touch)
        float currentKnobX = isJoystickActive ? knobX : cx;
        float currentKnobY = isJoystickActive ? knobY : cy;
        float knobRadius = radius * 0.36f;

        knobPaint.setColor(isJoystickActive ? COLOR_PRESSED_BG : 0xCC2B2D31);
        canvas.drawCircle(currentKnobX, currentKnobY, knobRadius, knobPaint);

        knobBorderPaint.setStrokeWidth(dpToPx(1.5f));
        knobBorderPaint.setColor(isJoystickActive ? 0xFFFFFFFF : 0x66FFFFFF);
        canvas.drawCircle(currentKnobX, currentKnobY, knobRadius, knobBorderPaint);

        // Center dot on knob
        dotPaint.setColor(isJoystickActive ? 0xFFFFFFFF : 0x88FFFFFF);
        canvas.drawCircle(currentKnobX, currentKnobY, dpToPx(3f), dotPaint);

        // 4. If selected in Layout Editor: draw circular dashed selection ring and resize handle
        if (isSelected) {
            canvas.drawCircle(cx, cy, radius, selectPaint);
            drawResizeHandle(canvas);
        }
    }

    private void drawScrollController(Canvas canvas) {
        float cornerRadiusPx = dpToPx(key.cornerRadius);
        if (cornerRadiusPx > Math.min(bounds.width(), bounds.height()) / 2f) {
            cornerRadiusPx = Math.min(bounds.width(), bounds.height()) / 2f;
        }

        // 1. Base track background
        if (key.bgOpacity > 0) {
            int alpha = Math.round((key.bgOpacity / 100f) * 255);
            bgPaint.setColor(isScrollActive ?
                    Color.argb(Math.min(255, alpha + 40), 0x24, 0x2A, 0x32) :
                    Color.argb(alpha, Color.red(COLOR_NORMAL_BASE), Color.green(COLOR_NORMAL_BASE), Color.blue(COLOR_NORMAL_BASE)));
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, bgPaint);
        }

        // Border
        if (key.showBorder || isScrollActive) {
            strokePaint.setColor(isScrollActive ? COLOR_ACTIVE_STROKE : COLOR_NORMAL_STROKE);
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, strokePaint);
        }

        float cx = bounds.centerX();
        float cy = bounds.centerY();

        // 2. Up & Down direction indicators
        float arrowSize = Math.max(dpToPx(10f), Math.min(bounds.width() * 0.35f, dpToPx(14f)));
        arrowPaint.setTextSize(arrowSize);

        float topArrowY = bounds.top + arrowSize + dpToPx(4f);
        float botArrowY = bounds.bottom - dpToPx(6f);

        boolean isUpActive = isScrollActive && (currentKnobY < cy - dpToPx(8f));
        boolean isDownActive = isScrollActive && (currentKnobY > cy + dpToPx(8f));

        arrowPaint.setColor(isUpActive ? COLOR_PRESSED_BG : 0xAAFFFFFF);
        canvas.drawText("▲", cx, topArrowY, arrowPaint);

        arrowPaint.setColor(isDownActive ? COLOR_PRESSED_BG : 0xAAFFFFFF);
        canvas.drawText("▼", cx, botArrowY, arrowPaint);

        // 3. Central guide line
        float lineTop = topArrowY + dpToPx(5f);
        float lineBot = botArrowY - arrowSize - dpToPx(1f);
        if (lineBot > lineTop) {
            canvas.drawLine(cx, lineTop, cx, lineBot, linePaint);
        }

        // 4. Draggable Knob / Thumb
        float knobW = Math.max(dpToPx(16f), bounds.width() - dpToPx(6f));
        float knobH = Math.max(dpToPx(18f), bounds.height() * 0.18f);
        float halfKnobH = knobH / 2f;
        float knobY = isScrollActive ? currentKnobY : cy;

        if (lineBot > lineTop) {
            knobY = Math.max(lineTop + halfKnobH, Math.min(lineBot - halfKnobH, knobY));
        }

        RectF knobRect = tmpRect;
        knobRect.set(cx - (knobW / 2f), knobY - halfKnobH, cx + (knobW / 2f), knobY + halfKnobH);

        knobPaint.setColor(isScrollActive ? COLOR_PRESSED_BG : 0xDD2D3035);
        canvas.drawRoundRect(knobRect, dpToPx(4f), dpToPx(4f), knobPaint);

        knobBorderPaint.setStrokeWidth(dpToPx(1.2f));
        knobBorderPaint.setColor(isScrollActive ? 0xFFFFFFFF : 0x55FFFFFF);
        canvas.drawRoundRect(knobRect, dpToPx(4f), dpToPx(4f), knobBorderPaint);

        // Grip lines on knob
        gripPaint.setColor(isScrollActive ? 0xFFFFFFFF : 0x77FFFFFF);
        gripPaint.setStrokeWidth(dpToPx(1.2f));
        float gripW = knobW * 0.4f;
        float gY = knobRect.centerY();
        canvas.drawLine(cx - gripW, gY - dpToPx(3f), cx + gripW, gY - dpToPx(3f), gripPaint);
        canvas.drawLine(cx - gripW, gY, cx + gripW, gY, gripPaint);
        canvas.drawLine(cx - gripW, gY + dpToPx(3f), cx + gripW, gY + dpToPx(3f), gripPaint);

        // 5. If selected in Layout Editor: draw selection outline and resize handle
        if (isSelected) {
            canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, selectPaint);
            drawResizeHandle(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (scrollHandler != null) {
            scrollHandler.removeCallbacks(continuousScrollRunnable);
        }
    }
}
