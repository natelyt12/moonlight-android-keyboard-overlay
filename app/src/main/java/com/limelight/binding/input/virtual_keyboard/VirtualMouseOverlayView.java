package com.limelight.binding.input.virtual_keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressLint("ViewConstructor")
public class VirtualMouseOverlayView extends View {

    public static class MouseButtonDef {
        final String label;
        final String subLabel;
        final byte buttonCode;
        final RectF rect = new RectF();

        MouseButtonDef(String label, String subLabel, byte buttonCode) {
            this.label = label;
            this.subLabel = subLabel;
            this.buttonCode = buttonCode;
        }
    }

    private final NvConnection conn;
    private boolean hapticEnabled = true;

    // Visual paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dragPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint closeIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();

    // Mouse buttons:
    // [SC.UP]   [pri] [sec]
    // [SC.DN]   [b1]  [mid] [b2]
    private final MouseButtonDef priBtn = new MouseButtonDef("PRI", "Left", MouseButtonPacket.BUTTON_LEFT);
    private final MouseButtonDef secBtn = new MouseButtonDef("SEC", "Right", MouseButtonPacket.BUTTON_RIGHT);
    private final MouseButtonDef b1Btn = new MouseButtonDef("B1", "Back", MouseButtonPacket.BUTTON_X1);
    private final MouseButtonDef midBtn = new MouseButtonDef("MID", "Middle", MouseButtonPacket.BUTTON_MIDDLE);
    private final MouseButtonDef b2Btn = new MouseButtonDef("B2", "Fwd", MouseButtonPacket.BUTTON_X2);

    private final List<MouseButtonDef> allButtons = new ArrayList<>();
    private final RectF dragBarRect = new RectF();
    private final RectF closeRect = new RectF();

    // 2 Scroll Buttons: Scroll Up & Scroll Down
    private final RectF scrollUpRect = new RectF();
    private final RectF scrollDownRect = new RectF();
    private int scrollUpPointerId = -1;
    private int scrollDownPointerId = -1;

    // Continuous scroll handler
    private final Handler scrollHandler = new Handler(Looper.getMainLooper());
    private Runnable activeScrollRunnable = null;

    // State tracking for mouse buttons
    private final Map<Integer, MouseButtonDef> activePointers = new HashMap<>();
    private final Set<MouseButtonDef> pressedButtons = new HashSet<>();

    // Dragging overlay window
    private int dragPointerId = -1;
    private float dragStartRawX, dragStartRawY;
    private float viewStartX, viewStartY;

    // Flat ~70% opaque palette, shared with the keyboard overlay.
    private static final int COLOR_OVERLAY_BG = 0xB312141A;
    private static final int COLOR_OVERLAY_BORDER = 0x2EFFFFFF;
    private static final int COLOR_KEY_BG = 0x59232931;
    private static final int COLOR_KEY_BORDER = 0x22FFFFFF;
    private static final int COLOR_KEY_PRESSED = 0xE600B4D8;
    private static final int COLOR_CLOSE_BG = 0x59D90429;

    public VirtualMouseOverlayView(Context context, NvConnection conn) {
        super(context);
        this.conn = conn;

        setFocusable(false);
        setClickable(true);

        allButtons.add(priBtn);
        allButtons.add(secBtn);
        allButtons.add(b1Btn);
        allButtons.add(midBtn);
        allButtons.add(b2Btn);

        initPaints();
    }

    public void setHapticEnabled(boolean enabled) {
        this.hapticEnabled = enabled;
    }

    public void setVibrationDuration(int durationMs) {
        // Maintained for backward compatibility, haptic now uses predefined click
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
            } catch (Exception ignored) {}
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void initPaints() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(COLOR_OVERLAY_BG);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.2f));
        borderPaint.setColor(COLOR_OVERLAY_BORDER);

        dragPillPaint.setStyle(Paint.Style.FILL);
        dragPillPaint.setColor(0x40FFFFFF);

        closeIconPaint.setStyle(Paint.Style.STROKE);
        closeIconPaint.setStrokeWidth(dpToPx(1.6f));
        closeIconPaint.setStrokeCap(Paint.Cap.ROUND);

        closePaint.setStyle(Paint.Style.FILL);
        closePaint.setColor(COLOR_CLOSE_BG);

        keyBgPaint.setStyle(Paint.Style.FILL);

        keyBorderPaint.setStyle(Paint.Style.STROKE);
        keyBorderPaint.setStrokeWidth(dpToPx(0.8f));

        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        subTextPaint.setColor(0x88FFFFFF);
        subTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        subTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) dpToPx(240f);
        int h = (int) dpToPx(135f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutOverlay(w, h);
    }

    private void layoutOverlay(int width, int height) {
        if (width <= 0 || height <= 0) return;

        float padH = dpToPx(7f);
        float padV = dpToPx(4f);
        float gap = dpToPx(3f);
        float dragBarH = dpToPx(16f);

        dragBarRect.set(padH, padV, width - padH, padV + dragBarH);

        // Close button in the top-right of the thin drag strip
        float closeW = dpToPx(26f);
        float closeH = dpToPx(14f);
        float closeTop = padV + (dragBarH - closeH) / 2f;
        closeRect.set(width - padH - closeW, closeTop, width - padH, closeTop + closeH);
        dragBarRect.right = Math.max(dragBarRect.left, closeRect.left - dpToPx(4f));

        float usableH = height - dragBarH - (padV * 2);
        float r1Height = (usableH - gap) * 0.52f;
        float r2Height = (usableH - gap) * 0.48f;

        float r1Y = padV + dragBarH;
        float r2Y = r1Y + r1Height + gap;

        // Left column: 2 distinct Scroll Buttons [▲ Scroll Up] and [▼ Scroll Down]
        float scrollW = dpToPx(40f);
        scrollUpRect.set(padH, r1Y, padH + scrollW, r1Y + r1Height);
        scrollDownRect.set(padH, r2Y, padH + scrollW, r2Y + r2Height);

        // Right side: 2 rows of buttons
        float rightStartX = padH + scrollW + gap;
        float rightW = width - padH - rightStartX;

        // Row 1: [PRI] [SEC] (2 buttons, 50% each)
        float r1BtnW = (rightW - gap) / 2f;
        priBtn.rect.set(rightStartX, r1Y, rightStartX + r1BtnW, r1Y + r1Height);
        secBtn.rect.set(rightStartX + r1BtnW + gap, r1Y, rightStartX + rightW, r1Y + r1Height);

        // Row 2: [B1] [MID] [B2] (3 buttons, 33.3% each)
        float r2BtnW = (rightW - (gap * 2)) / 3f;
        b1Btn.rect.set(rightStartX, r2Y, rightStartX + r2BtnW, r2Y + r2Height);
        midBtn.rect.set(rightStartX + r2BtnW + gap, r2Y, rightStartX + (r2BtnW * 2) + gap, r2Y + r2Height);
        b2Btn.rect.set(rightStartX + (r2BtnW * 2) + (gap * 2), r2Y, rightStartX + rightW, r2Y + r2Height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cornerRadius = dpToPx(12f);
        float keyCornerRadius = dpToPx(5f);

        // 1. Flat translucent container (~70% opaque, matching the keyboard overlay)
        RectF outerRect = new RectF(dpToPx(1f), dpToPx(1f), getWidth() - dpToPx(1f), getHeight() - dpToPx(1f));
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, bgPaint);
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, borderPaint);

        // 2. Thin drag strip with a small centered pill
        float pillW = dpToPx(38f);
        float pillH = dpToPx(3f);
        float pillX = (getWidth() - pillW) / 2f;
        float pillY = dragBarRect.centerY() - pillH / 2f;
        canvas.drawRoundRect(new RectF(pillX, pillY, pillX + pillW, pillY + pillH), pillH / 2f, pillH / 2f, dragPillPaint);

        // Close button
        canvas.drawRoundRect(closeRect, dpToPx(4f), dpToPx(4f), closePaint);
        closeIconPaint.setColor(0xFFFF8A80);
        // Draw the glyph inside a centered square, otherwise the wide button rect stretches it.
        float iconHalf = Math.min(closeRect.width(), closeRect.height()) * 0.21f;
        float iconCx = closeRect.centerX();
        float iconCy = closeRect.centerY();
        canvas.drawLine(iconCx - iconHalf, iconCy - iconHalf,
                iconCx + iconHalf, iconCy + iconHalf, closeIconPaint);
        canvas.drawLine(iconCx + iconHalf, iconCy - iconHalf,
                iconCx - iconHalf, iconCy + iconHalf, closeIconPaint);

        // 3. Draw 2 Scroll Buttons on the left
        drawScrollButton(canvas, scrollUpRect, "▲", "Up", scrollUpPointerId != -1, keyCornerRadius);
        drawScrollButton(canvas, scrollDownRect, "▼", "Dn", scrollDownPointerId != -1, keyCornerRadius);

        // 4. Draw Mouse Buttons on the right
        for (MouseButtonDef btn : allButtons) {
            drawButton(canvas, btn, keyCornerRadius);
        }
    }

    private void drawScrollButton(Canvas canvas, RectF rect, String arrow, String sub, boolean isPressed, float cornerRadius) {
        if (isPressed) {
            keyBgPaint.setColor(COLOR_KEY_PRESSED);
            keyBorderPaint.setColor(0xFFFFFFFF);
        } else {
            keyBgPaint.setColor(COLOR_KEY_BG);
            keyBorderPaint.setColor(COLOR_KEY_BORDER);
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyBgPaint);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyBorderPaint);

        float centerY = rect.centerY() - dpToPx(4f);
        textPaint.setTextSize(dpToPx(13f));
        canvas.drawText(arrow, rect.centerX(), centerY + dpToPx(4f), textPaint);

        subTextPaint.setTextSize(dpToPx(9.5f));
        canvas.drawText(sub, rect.centerX(), centerY + dpToPx(14f), subTextPaint);
    }

    private void drawButton(Canvas canvas, MouseButtonDef btn, float cornerRadius) {
        boolean isPressed = pressedButtons.contains(btn);

        if (isPressed) {
            keyBgPaint.setColor(COLOR_KEY_PRESSED);
            keyBorderPaint.setColor(0xFFFFFFFF);
        } else {
            keyBgPaint.setColor(COLOR_KEY_BG);
            keyBorderPaint.setColor(COLOR_KEY_BORDER);
        }

        canvas.drawRoundRect(btn.rect, cornerRadius, cornerRadius, keyBgPaint);
        canvas.drawRoundRect(btn.rect, cornerRadius, cornerRadius, keyBorderPaint);

        float w = btn.rect.width();
        float h = btn.rect.height();
        float fontSize = Math.min(dpToPx(14f), Math.min(w * 0.35f, h * 0.40f));
        textPaint.setTextSize(fontSize);

        float centerY = btn.rect.centerY();
        if (btn.subLabel != null) {
            centerY -= dpToPx(4.5f);
        }

        textPaint.getTextBounds(btn.label, 0, btn.label.length(), textBounds);
        float textY = centerY + (textBounds.height() / 2f) - textBounds.bottom;
        canvas.drawText(btn.label, btn.rect.centerX(), textY, textPaint);

        if (btn.subLabel != null) {
            subTextPaint.setTextSize(fontSize * 0.65f);
            canvas.drawText(btn.subLabel, btn.rect.centerX(), centerY + (fontSize * 0.85f) + dpToPx(2f), subTextPaint);
        }
    }

    private MouseButtonDef findButtonAt(float x, float y) {
        for (MouseButtonDef btn : allButtons) {
            if (btn.rect.contains(x, y)) {
                return btn;
            }
        }
        return null;
    }

    private void startScrollLoop(final short scrollAmount) {
        stopScrollLoop();
        if (conn != null) {
            conn.sendMouseHighResScroll(scrollAmount);
        }
        activeScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (conn != null) {
                    conn.sendMouseHighResScroll(scrollAmount);
                }
                scrollHandler.postDelayed(this, 80);
            }
        };
        scrollHandler.postDelayed(activeScrollRunnable, 250);
    }

    private void stopScrollLoop() {
        if (activeScrollRunnable != null) {
            scrollHandler.removeCallbacks(activeScrollRunnable);
            activeScrollRunnable = null;
        }
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

                // 1. Close button
                if (closeRect.contains(x, y)) {
                    hideOverlay();
                    return true;
                }

                // 2. Drag bar
                if (dragPointerId == -1 && dragBarRect.contains(x, y)) {
                    dragPointerId = pointerId;
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    viewStartX = getX();
                    viewStartY = getY();
                    return true;
                }

                // 3. Scroll Up button
                if (scrollUpPointerId == -1 && scrollUpRect.contains(x, y)) {
                    scrollUpPointerId = pointerId;
                    triggerVibration();
                    startScrollLoop((short) 120);
                    invalidate();
                    return true;
                }

                // 4. Scroll Down button
                if (scrollDownPointerId == -1 && scrollDownRect.contains(x, y)) {
                    scrollDownPointerId = pointerId;
                    triggerVibration();
                    startScrollLoop((short) -120);
                    invalidate();
                    return true;
                }

                // 5. Mouse buttons
                MouseButtonDef btn = findButtonAt(x, y);
                if (btn != null) {
                    activePointers.put(pointerId, btn);
                    handleButtonDown(btn);
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                // Dragging overlay window
                if (dragPointerId != -1) {
                    int pIdx = event.findPointerIndex(dragPointerId);
                    if (pIdx >= 0) {
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

                // Handle button slide / release
                int pointerCount = event.getPointerCount();
                boolean changed = false;
                for (int p = 0; p < pointerCount; p++) {
                    int pointerId = event.getPointerId(p);
                    if (pointerId == dragPointerId || pointerId == scrollUpPointerId || pointerId == scrollDownPointerId) continue;

                    float x = event.getX(p);
                    float y = event.getY(p);

                    MouseButtonDef oldBtn = activePointers.get(pointerId);
                    MouseButtonDef newBtn = findButtonAt(x, y);

                    if (oldBtn != newBtn) {
                        if (oldBtn != null) {
                            handleButtonUp(oldBtn);
                        }
                        if (newBtn != null) {
                            handleButtonDown(newBtn);
                            activePointers.put(pointerId, newBtn);
                        } else {
                            activePointers.remove(pointerId);
                        }
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
                if (pointerId == scrollUpPointerId) {
                    scrollUpPointerId = -1;
                    stopScrollLoop();
                    invalidate();
                    return true;
                }
                if (pointerId == scrollDownPointerId) {
                    scrollDownPointerId = -1;
                    stopScrollLoop();
                    invalidate();
                    return true;
                }
                MouseButtonDef btn = activePointers.remove(pointerId);
                if (btn != null) {
                    handleButtonUp(btn);
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragPointerId = -1;
                scrollUpPointerId = -1;
                scrollDownPointerId = -1;
                stopScrollLoop();
                for (MouseButtonDef btn : activePointers.values()) {
                    handleButtonUp(btn);
                }
                activePointers.clear();
                invalidate();
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    private void handleButtonDown(MouseButtonDef btn) {
        if (pressedButtons.add(btn)) {
            triggerVibration();
            if (conn != null) {
                conn.sendMouseButtonDown(btn.buttonCode);
            }
        }
    }

    private void handleButtonUp(MouseButtonDef btn) {
        if (pressedButtons.remove(btn)) {
            if (conn != null) {
                conn.sendMouseButtonUp(btn.buttonCode);
            }
        }
    }

    public void toggleOverlay() {
        if (getVisibility() == View.VISIBLE) {
            hideOverlay();
        } else {
            showOverlay();
        }
    }

    public void showOverlay() {
        setVisibility(View.VISIBLE);
        bringToFront();
    }

    public void hideOverlay() {
        for (MouseButtonDef btn : new HashSet<>(pressedButtons)) {
            handleButtonUp(btn);
        }
        activePointers.clear();
        dragPointerId = -1;
        scrollUpPointerId = -1;
        scrollDownPointerId = -1;
        stopScrollLoop();
        setVisibility(View.GONE);
    }
}
