package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VirtualKeyboardController implements VirtualKeyView.KeyActionListener {

    private final NvConnection conn;
    private final KeyboardTranslator translator;
    private final FrameLayout parentLayout;
    private final Context context;

    private final List<VirtualKeyView> keyViews = new ArrayList<>();
    private final Set<Integer> activeModifiers = new HashSet<>();
    private boolean isHidden = false;
    private int opacity = 100;
    private boolean vibrationEnabled = true;
    private int vibrationDuration = 20;
    private PcKeyboardOverlayView pcKeyboardOverlay = null;
    private VirtualMouseOverlayView mouseOverlay = null;
    private InStreamMenuOverlayView inStreamMenuOverlay = null;

    private final Handler comboHandler = new Handler(Looper.getMainLooper());
    private final Map<String, List<Runnable>> pendingComboTasks = new HashMap<>();
    private final Map<String, List<Integer>> pressedComboKeys = new HashMap<>();

    private final Handler scrollActionHandler = new Handler(Looper.getMainLooper());
    private Runnable activeScrollActionRunnable = null;

    public VirtualKeyboardController(NvConnection conn, KeyboardTranslator translator,
                                     FrameLayout parentLayout, Context context) {
        this.conn = conn;
        this.translator = translator;
        this.parentLayout = parentLayout;
        this.context = context;
    }

    public void setOpacity(int opacityPercent) {
        this.opacity = opacityPercent;
        for (VirtualKeyView v : keyViews) {
            v.setOpacity(opacityPercent);
        }
    }

    public void setVibrationEnabled(boolean enabled) {
        this.vibrationEnabled = enabled;
        for (VirtualKeyView v : keyViews) {
            v.setHapticEnabled(enabled);
        }
        if (pcKeyboardOverlay != null) {
            pcKeyboardOverlay.setHapticEnabled(enabled);
        }
        if (mouseOverlay != null) {
            mouseOverlay.setHapticEnabled(enabled);
        }
    }

    public void setVibrationDuration(int durationMs) {
        this.vibrationDuration = durationMs;
        for (VirtualKeyView v : keyViews) {
            v.setVibrationDuration(durationMs);
        }
        if (pcKeyboardOverlay != null) {
            pcKeyboardOverlay.setVibrationDuration(durationMs);
        }
        if (mouseOverlay != null) {
            mouseOverlay.setVibrationDuration(durationMs);
        }
    }

    private boolean isSpecialFunctionKey(VirtualKey key) {
        if (key == null) return false;
        return key.type == VirtualKey.KeyType.ACTION;
    }

    private boolean isToggleVisibilityKey(VirtualKey key) {
        if (key == null) return false;
        return (key.type == VirtualKey.KeyType.ACTION && key.code == VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY)
                || VirtualKeyboardLayout.ACTION_TOGGLE_VISIBILITY.equals(key.id);
    }

    public void refreshLayout() {
        parentLayout.post(new Runnable() {
            @Override
            public void run() {
                int width = parentLayout.getWidth();
                int height = parentLayout.getHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }

                removeElements();

                List<VirtualKey> keys = VirtualKeyboardProfileManager.loadProfile(context);
                List<VirtualKey> orderedKeys = new ArrayList<>();
                for (VirtualKey k : keys) {
                    if (k.isBlockTouch) orderedKeys.add(k);
                }
                for (VirtualKey k : keys) {
                    if (!k.isBlockTouch) orderedKeys.add(k);
                }

                for (VirtualKey key : orderedKeys) {
                    VirtualKeyView view = new VirtualKeyView(context, key, VirtualKeyboardController.this);
                    view.setOpacity(opacity);
                    view.setHapticEnabled(vibrationEnabled);
                    view.setVibrationDuration(vibrationDuration);
                    keyViews.add(view);

                    int x = Math.round(key.relX * width);
                    int y = Math.round(key.relY * height);
                    int w = Math.round(key.relWidth * width);
                    int h = Math.round(key.relHeight * height);

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                    lp.setMargins(x, y, 0, 0);
                    parentLayout.addView(view, lp);

                    if (isHidden && !isSpecialFunctionKey(key)) {
                        view.setVisibility(View.GONE);
                    }
                }

                if (pcKeyboardOverlay != null) {
                    parentLayout.removeView(pcKeyboardOverlay);
                    pcKeyboardOverlay = null;
                }
                pcKeyboardOverlay = new PcKeyboardOverlayView(context, conn, translator);
                pcKeyboardOverlay.setHapticEnabled(vibrationEnabled);
                pcKeyboardOverlay.setVibrationDuration(vibrationDuration);
                pcKeyboardOverlay.setVisibility(View.GONE);

                int maxPcW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 840f, context.getResources().getDisplayMetrics());
                int pcKbW = Math.min((int) (width * 0.88f), maxPcW);
                int pcKbH = pcKeyboardOverlay.calculatePerfectHeight(pcKbW);
                FrameLayout.LayoutParams pcLp = new FrameLayout.LayoutParams(pcKbW, pcKbH);
                pcLp.leftMargin = (width - pcKbW) / 2;
                pcLp.topMargin = (height - pcKbH) / 2;
                parentLayout.addView(pcKeyboardOverlay, pcLp);

                if (mouseOverlay != null) {
                    parentLayout.removeView(mouseOverlay);
                    mouseOverlay = null;
                }
                mouseOverlay = new VirtualMouseOverlayView(context, conn);
                mouseOverlay.setHapticEnabled(vibrationEnabled);
                mouseOverlay.setVibrationDuration(vibrationDuration);
                mouseOverlay.setVisibility(View.GONE);

                int mouseW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 245f, context.getResources().getDisplayMetrics());
                int mouseH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 125f, context.getResources().getDisplayMetrics());
                mouseW = Math.min(mouseW, (int) (width * 0.45f));
                mouseH = Math.min(mouseH, (int) (height * 0.45f));
                FrameLayout.LayoutParams mouseLp = new FrameLayout.LayoutParams(mouseW, mouseH);
                mouseLp.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.getResources().getDisplayMetrics());
                mouseLp.topMargin = Math.max(0, height - mouseH - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60f, context.getResources().getDisplayMetrics()));
                parentLayout.addView(mouseOverlay, mouseLp);

                if (inStreamMenuOverlay != null) {
                    parentLayout.removeView(inStreamMenuOverlay);
                    inStreamMenuOverlay = null;
                }
                if (context instanceof com.limelight.Game) {
                    inStreamMenuOverlay = new InStreamMenuOverlayView(context, (com.limelight.Game) context, VirtualKeyboardController.this);
                    FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                    parentLayout.addView(inStreamMenuOverlay, menuLp);
                }
            }
        });
    }

    public void show() {
        for (VirtualKeyView v : keyViews) {
            if (isHidden && !isSpecialFunctionKey(v.getKey())) {
                v.setVisibility(View.GONE);
            } else {
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    public void hide() {
        if (pcKeyboardOverlay != null) {
            pcKeyboardOverlay.hideKeyboard();
        }
        if (mouseOverlay != null) {
            mouseOverlay.hideOverlay();
        }
        if (inStreamMenuOverlay != null) {
            inStreamMenuOverlay.closeMenu();
        }
        stopActionScroll();
        for (VirtualKeyView v : keyViews) {
            v.clearAllSlidePointers();
            v.setVisibility(View.GONE);
        }
    }

    public void removeElements() {
        stopActionScroll();
        if (pcKeyboardOverlay != null) {
            parentLayout.removeView(pcKeyboardOverlay);
            pcKeyboardOverlay = null;
        }
        if (mouseOverlay != null) {
            parentLayout.removeView(mouseOverlay);
            mouseOverlay = null;
        }
        if (inStreamMenuOverlay != null) {
            parentLayout.removeView(inStreamMenuOverlay);
            inStreamMenuOverlay = null;
        }
        for (VirtualKeyView v : keyViews) {
            v.clearAllSlidePointers();
            parentLayout.removeView(v);
        }
        keyViews.clear();
    }

    public void destroy() {
        stopActionScroll();
        removeElements();
        activeModifiers.clear();
    }

    private void stopActionScroll() {
        if (activeScrollActionRunnable != null) {
            scrollActionHandler.removeCallbacks(activeScrollActionRunnable);
            activeScrollActionRunnable = null;
        }
    }

    public int getOpacity() {
        return opacity;
    }

    public void toggleKeysVisibility(VirtualKey sourceKey) {
        isHidden = !isHidden;
        for (VirtualKeyView v : keyViews) {
            if (!isSpecialFunctionKey(v.getKey())) {
                v.setVisibility(isHidden ? View.GONE : View.VISIBLE);
            }
        }

        // Optional per-button behaviour: hiding the overlay switches the touch input
        // mode to direct touchscreen, showing it switches back to trackpad mode.
        if (sourceKey != null && sourceKey.switchInputModeOnToggle
                && context instanceof com.limelight.Game) {
            ((com.limelight.Game) context).setTouchscreenTrackpad(!isHidden);
        }
    }



    public void togglePcKeyboard() {
        if (pcKeyboardOverlay != null) {
            pcKeyboardOverlay.toggleKeyboard();
        }
    }

    public void togglePcMouse() {
        if (mouseOverlay != null) {
            mouseOverlay.toggleOverlay();
        }
    }

    public void toggleInStreamMenu() {
        if (inStreamMenuOverlay != null) {
            inStreamMenuOverlay.toggleMenu();
        }
    }

    public boolean isMenuOpen() {
        return inStreamMenuOverlay != null && inStreamMenuOverlay.isMenuOpen();
    }

    private byte getModifierMask() {
        byte mod = 0;
        if (activeModifiers.contains(KeyEvent.KEYCODE_SHIFT_LEFT) ||
                activeModifiers.contains(KeyEvent.KEYCODE_SHIFT_RIGHT)) {
            mod |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (activeModifiers.contains(KeyEvent.KEYCODE_CTRL_LEFT) ||
                activeModifiers.contains(KeyEvent.KEYCODE_CTRL_RIGHT)) {
            mod |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (activeModifiers.contains(KeyEvent.KEYCODE_ALT_LEFT) ||
                activeModifiers.contains(KeyEvent.KEYCODE_ALT_RIGHT)) {
            mod |= KeyboardPacket.MODIFIER_ALT;
        }
        return mod;
    }

    private void sendSingleKeyDown(int keyCode) {
        if (keyCode == 0) return;
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
                keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
                keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            activeModifiers.add(keyCode);
        }
        if (conn != null && translator != null) {
            short translated = translator.translate(keyCode, -1);
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, getModifierMask(), (byte) 0);
        }
    }

    private void sendSingleKeyUp(int keyCode) {
        if (keyCode == 0) return;
        if (conn != null && translator != null) {
            short translated = translator.translate(keyCode, -1);
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, getModifierMask(), (byte) 0);
        }
        activeModifiers.remove(keyCode);
    }

    private void handleComboKeyDown(final VirtualKey key) {
        final String keyId = key.id;
        cancelPendingComboTasks(keyId);

        final List<Integer> sequence = new ArrayList<>();
        sequence.add(key.code);
        if (key.extraCode1 != 0) sequence.add(key.extraCode1);
        if (key.extraCode2 != 0) sequence.add(key.extraCode2);
        if (key.extraCode3 != 0) sequence.add(key.extraCode3);

        final List<Integer> currentlyDown = new ArrayList<>();
        pressedComboKeys.put(keyId, currentlyDown);

        final List<Runnable> tasks = new ArrayList<>();
        pendingComboTasks.put(keyId, tasks);

        // First key down immediately
        sendSingleKeyDown(sequence.get(0));
        currentlyDown.add(sequence.get(0));

        // Subsequent keys with 20ms staggered delay so PC OS and games recognize combination
        for (int i = 1; i < sequence.size(); i++) {
            final int codeToPress = sequence.get(i);
            long delayMs = i * 20L;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    tasks.remove(this);
                    sendSingleKeyDown(codeToPress);
                    currentlyDown.add(codeToPress);
                }
            };
            tasks.add(r);
            comboHandler.postDelayed(r, delayMs);
        }
    }

    private void handleComboKeyUp(final VirtualKey key) {
        final String keyId = key.id;
        final List<Runnable> pending = pendingComboTasks.get(keyId);

        if (pending != null && !pending.isEmpty()) {
            int pendingCount = pending.size();
            long releaseStartDelay = (pendingCount * 20L) + 20L;

            Runnable releaseTask = new Runnable() {
                @Override
                public void run() {
                    releaseComboKeys(keyId);
                }
            };
            pending.add(releaseTask);
            comboHandler.postDelayed(releaseTask, releaseStartDelay);
        } else {
            releaseComboKeys(keyId);
        }
    }

    private void releaseComboKeys(final String keyId) {
        final List<Integer> currentlyDown = pressedComboKeys.remove(keyId);
        cancelPendingComboTasks(keyId);
        if (currentlyDown == null || currentlyDown.isEmpty()) return;

        // Release all combo keys at the same time
        for (int i = currentlyDown.size() - 1; i >= 0; i--) {
            sendSingleKeyUp(currentlyDown.get(i));
        }
    }

    private void cancelPendingComboTasks(String keyId) {
        List<Runnable> tasks = pendingComboTasks.remove(keyId);
        if (tasks != null) {
            for (Runnable r : tasks) {
                comboHandler.removeCallbacks(r);
            }
        }
    }

    @Override
    public void onKeyDown(VirtualKey key) {
        if (key.isBlockTouch) {
            return;
        }

        if (key.type == VirtualKey.KeyType.KEYBOARD) {
            if (key.extraCode1 == 0 && key.extraCode2 == 0 && key.extraCode3 == 0) {
                sendSingleKeyDown(key.code);
            } else {
                handleComboKeyDown(key);
            }
        } else if (key.type == VirtualKey.KeyType.MOUSE_BUTTON) {
            if (conn != null) {
                conn.sendMouseButtonDown((byte) key.code);
            }
        } else if (key.type == VirtualKey.KeyType.ACTION) {
            if (isToggleVisibilityKey(key)) {
                toggleKeysVisibility(key);
            } else if (key.code == VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD) {
                togglePcKeyboard();
            } else if (key.code == VirtualKey.ACTION_CODE_TOGGLE_MOUSE) {
                togglePcMouse();
            } else if (key.code == VirtualKey.ACTION_CODE_SHOW_ANDROID_KEYBOARD) {
                if (context instanceof com.limelight.Game) {
                    ((com.limelight.Game) context).toggleKeyboard();
                }
            } else if (key.code == VirtualKey.ACTION_CODE_SCROLL_UP) {
                if (conn != null) {
                    conn.sendMouseHighResScroll((short) 120);
                }
                stopActionScroll();
                activeScrollActionRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (conn != null) {
                            conn.sendMouseHighResScroll((short) 120);
                        }
                        scrollActionHandler.postDelayed(this, 80);
                    }
                };
                scrollActionHandler.postDelayed(activeScrollActionRunnable, 250);
            } else if (key.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                if (conn != null) {
                    conn.sendMouseHighResScroll((short) -120);
                }
                stopActionScroll();
                activeScrollActionRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (conn != null) {
                            conn.sendMouseHighResScroll((short) -120);
                        }
                        scrollActionHandler.postDelayed(this, 80);
                    }
                };
                scrollActionHandler.postDelayed(activeScrollActionRunnable, 250);
            } else if (key.code == VirtualKey.ACTION_CODE_TOGGLE_STREAM_MENU) {
                toggleInStreamMenu();
            }
        }
    }

    @Override
    public void onKeyUp(VirtualKey key) {
        if (key.isBlockTouch) {
            return;
        }

        if (key.type == VirtualKey.KeyType.KEYBOARD) {
            if (key.extraCode1 == 0 && key.extraCode2 == 0 && key.extraCode3 == 0) {
                sendSingleKeyUp(key.code);
            } else {
                handleComboKeyUp(key);
            }
        } else if (key.type == VirtualKey.KeyType.MOUSE_BUTTON) {
            if (conn != null) {
                conn.sendMouseButtonUp((byte) key.code);
            }
        } else if (key.type == VirtualKey.KeyType.ACTION) {
            if (key.code == VirtualKey.ACTION_CODE_SCROLL_UP || key.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                stopActionScroll();
            }
        }
    }

    @Override
    public void onKeyTouchEvent(VirtualKeyView source, MotionEvent event) {
        handleSlideTouchEvent(source, event);
    }

    @Override
    public void onExplicitKeyDown(int keycode) {
        if (conn != null && translator != null) {
            short translated = translator.translate(keycode, -1);
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, getModifierMask(), (byte) 0);
        }
    }

    @Override
    public void onExplicitKeyUp(int keycode) {
        if (conn != null && translator != null) {
            short translated = translator.translate(keycode, -1);
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, getModifierMask(), (byte) 0);
        }
    }

    @Override
    public void onScroll(short scrollAmount) {
        if (conn != null) {
            conn.sendMouseHighResScroll(scrollAmount);
        }
    }

    public boolean handleSlideTouchEvent(View sourceView, MotionEvent event) {
        if (isHidden || sourceView == null || event == null) {
            return false;
        }

        int[] sourceLoc = new int[2];
        sourceView.getLocationOnScreen(sourceLoc);

        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerCount = event.getPointerCount();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            for (VirtualKeyView v : keyViews) {
                if (isStreamDrivenKey(v.getKey())) {
                    v.clearAllSlidePointers();
                }
            }
            return false;
        }

        if (pcKeyboardOverlay != null && pcKeyboardOverlay.getVisibility() == View.VISIBLE) {
            int[] pcLoc = new int[2];
            pcKeyboardOverlay.getLocationOnScreen(pcLoc);
            for (int p = 0; p < pointerCount; p++) {
                float screenX = sourceLoc[0] + event.getX(p);
                float screenY = sourceLoc[1] + event.getY(p);
                if (screenX >= pcLoc[0] && screenX <= pcLoc[0] + pcKeyboardOverlay.getWidth() &&
                        screenY >= pcLoc[1] && screenY <= pcLoc[1] + pcKeyboardOverlay.getHeight()) {
                    return true;
                }
            }
        }
        if (mouseOverlay != null && mouseOverlay.getVisibility() == View.VISIBLE) {
            int[] mLoc = new int[2];
            mouseOverlay.getLocationOnScreen(mLoc);
            for (int p = 0; p < pointerCount; p++) {
                float screenX = sourceLoc[0] + event.getX(p);
                float screenY = sourceLoc[1] + event.getY(p);
                if (screenX >= mLoc[0] && screenX <= mLoc[0] + mouseOverlay.getWidth() &&
                        screenY >= mLoc[1] && screenY <= mLoc[1] + mouseOverlay.getHeight()) {
                    return true;
                }
            }
        }

        boolean shouldAbsorbTouch = false;

        for (int p = 0; p < pointerCount; p++) {
            int pointerId = event.getPointerId(p);
            float screenX = sourceLoc[0] + event.getX(p);
            float screenY = sourceLoc[1] + event.getY(p);

            boolean isPointerLeaving = (action == MotionEvent.ACTION_POINTER_UP && p == actionIndex);
            boolean isPointerArriving = (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                    && p == actionIndex;

            for (VirtualKeyView v : keyViews) {
                VirtualKey k = v.getKey();
                if (!isStreamDrivenKey(k)) continue;

                if (isPointerLeaving) {
                    v.clearPointerDownInside(pointerId);
                    v.onSlidePointerExit(pointerId);
                    continue;
                }

                if (isPointerArriving && v.containsScreenPoint(screenX, screenY)) {
                    v.markPointerDownInside(pointerId);
                }

                // Slide keys fire as soon as a finger enters them. Plain pass-through keys
                // (Absorb Touch disabled) only fire for fingers that landed on them first, so
                // dragging the camera across the screen never presses them by accident.
                boolean eligible = k.isSlide || v.isPointerDownInside(pointerId);

                if (eligible && v.containsScreenPoint(screenX, screenY)) {
                    v.onSlidePointerEnter(pointerId);
                    if (k.absorbTouch) {
                        shouldAbsorbTouch = true;
                    }
                } else {
                    if (!k.holdOutside) {
                        v.onSlidePointerExit(pointerId);
                    } else if (v.activeSlidePointers.contains(pointerId) && k.absorbTouch) {
                        shouldAbsorbTouch = true;
                    }
                }
            }
        }
        return shouldAbsorbTouch;
    }

    /**
     * Keys whose press/release is driven by {@link #handleSlideTouchEvent} rather than
     * by their own touch handler: slide keys plus any button with Absorb Touch disabled.
     */
    private boolean isStreamDrivenKey(VirtualKey k) {
        if (k == null || k.type == VirtualKey.KeyType.ACTION) return false;
        if (k.isJoystick || k.isScroll || k.isBlockTouch) return false;
        return k.isSlide || !k.absorbTouch;
    }
}
