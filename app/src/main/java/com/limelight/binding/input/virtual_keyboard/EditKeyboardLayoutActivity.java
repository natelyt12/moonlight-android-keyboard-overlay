package com.limelight.binding.input.virtual_keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EditKeyboardLayoutActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final float SNAP_THRESHOLD_DP = 6f;

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float getEffectiveSnapStepX() {
        int cW = keysContainer != null ? keysContainer.getWidth() : 0;
        return cW > 0 ? (1f / (float) cW) : 0.001f;
    }

    private float getEffectiveSnapStepY() {
        int cH = keysContainer != null ? keysContainer.getHeight() : 0;
        return cH > 0 ? (1f / (float) cH) : 0.001f;
    }

    private float[] snapPositionToEdges(VirtualKey movingKey, float rawRelX, float rawRelY, int cW, int cH) {
        if (!snapEnabled || cW <= 0 || cH <= 0) {
            clearAlignmentGuides();
            return new float[]{rawRelX, rawRelY};
        }
        float threshX = dpToPx(SNAP_THRESHOLD_DP) / (float) cW;
        float threshY = dpToPx(SNAP_THRESHOLD_DP) / (float) cH;

        float w = movingKey.relWidth;
        float h = movingKey.relHeight;

        float bestDx = threshX + 1f;
        float snappedX = rawRelX;
        float bestDy = threshY + 1f;
        float snappedY = rawRelY;

        // Check X candidates: screen center + other buttons' left, right, centerX
        float curLeft = rawRelX;
        float curRight = rawRelX + w;
        float curCenterX = rawRelX + (w / 2f);

        float distScreenCx = Math.abs(curCenterX - 0.5f);
        if (distScreenCx <= threshX && distScreenCx < bestDx) {
            bestDx = distScreenCx;
            snappedX = 0.5f - (w / 2f);
        }

        // Check Y candidates: screen center + other buttons' top, bottom, centerY
        float curTop = rawRelY;
        float curBottom = rawRelY + h;
        float curCenterY = rawRelY + (h / 2f);

        float distScreenCy = Math.abs(curCenterY - 0.5f);
        if (distScreenCy <= threshY && distScreenCy < bestDy) {
            bestDy = distScreenCy;
            snappedY = 0.5f - (h / 2f);
        }

        for (VirtualKey other : currentKeys) {
            if (other == movingKey) continue;
            float oLeft = other.relX;
            float oRight = other.relX + other.relWidth;
            float oCx = other.relX + (other.relWidth / 2f);

            float[] xPairs = {
                    Math.abs(curLeft - oLeft), oLeft,
                    Math.abs(curLeft - oRight), oRight,
                    Math.abs(curRight - oLeft), oLeft - w,
                    Math.abs(curRight - oRight), oRight - w,
                    Math.abs(curCenterX - oCx), oCx - (w / 2f)
            };
            for (int i = 0; i < xPairs.length; i += 2) {
                if (xPairs[i] <= threshX && xPairs[i] < bestDx) {
                    bestDx = xPairs[i];
                    snappedX = xPairs[i + 1];
                }
            }

            float oTop = other.relY;
            float oBottom = other.relY + other.relHeight;
            float oCy = other.relY + (other.relHeight / 2f);

            float[] yPairs = {
                    Math.abs(curTop - oTop), oTop,
                    Math.abs(curTop - oBottom), oBottom,
                    Math.abs(curBottom - oTop), oTop - h,
                    Math.abs(curBottom - oBottom), oBottom - h,
                    Math.abs(curCenterY - oCy), oCy - (h / 2f)
            };
            for (int i = 0; i < yPairs.length; i += 2) {
                if (yPairs[i] <= threshY && yPairs[i] < bestDy) {
                    bestDy = yPairs[i];
                    snappedY = yPairs[i + 1];
                }
            }
        }

        snappedX = Math.max(0f, Math.min(1f - w, snappedX));
        snappedY = Math.max(0f, Math.min(1f - h, snappedY));
        updateAlignmentGuidesForRect(movingKey, snappedX, snappedY, w, h, cW, cH);
        return new float[]{snappedX, snappedY};
    }

    private float[] snapSizeToEdges(VirtualKey resizingKey, float rawRelW, float rawRelH, int cW, int cH) {
        if (!snapEnabled || cW <= 0 || cH <= 0) {
            clearAlignmentGuides();
            return new float[]{rawRelW, rawRelH};
        }
        float threshX = dpToPx(SNAP_THRESHOLD_DP) / (float) cW;
        float threshY = dpToPx(SNAP_THRESHOLD_DP) / (float) cH;

        float curRight = resizingKey.relX + rawRelW;
        float curBottom = resizingKey.relY + rawRelH;

        float bestDx = threshX + 1f;
        float snappedW = rawRelW;
        float bestDy = threshY + 1f;
        float snappedH = rawRelH;

        float distScreenCx = Math.abs(curRight - 0.5f);
        if (distScreenCx <= threshX && distScreenCx < bestDx) {
            bestDx = distScreenCx;
            snappedW = 0.5f - resizingKey.relX;
        }
        float distScreenCy = Math.abs(curBottom - 0.5f);
        if (distScreenCy <= threshY && distScreenCy < bestDy) {
            bestDy = distScreenCy;
            snappedH = 0.5f - resizingKey.relY;
        }

        for (VirtualKey other : currentKeys) {
            if (other == resizingKey) continue;
            float[] xTargets = {other.relX, other.relX + other.relWidth, other.relX + (other.relWidth / 2f)};
            for (float tx : xTargets) {
                float d = Math.abs(curRight - tx);
                if (d <= threshX && d < bestDx) {
                    bestDx = d;
                    snappedW = tx - resizingKey.relX;
                }
            }
            float[] yTargets = {other.relY, other.relY + other.relHeight, other.relY + (other.relHeight / 2f)};
            for (float ty : yTargets) {
                float d = Math.abs(curBottom - ty);
                if (d <= threshY && d < bestDy) {
                    bestDy = d;
                    snappedH = ty - resizingKey.relY;
                }
            }
        }

        updateAlignmentGuidesForRect(resizingKey, resizingKey.relX, resizingKey.relY, snappedW, snappedH, cW, cH);
        return new float[]{snappedW, snappedH};
    }

    private void updateAlignmentGuidesForRect(VirtualKey activeKey, float x, float y, float w, float h, int cW, int cH) {
        if (alignmentGuideOverlay == null) return;
        float epsX = 1.5f / Math.max(1, cW);
        float epsY = 1.5f / Math.max(1, cH);
        List<Float> vGuides = new ArrayList<>();
        List<Float> hGuides = new ArrayList<>();

        float left = x, right = x + w, cx = x + w / 2f;
        float top = y, bottom = y + h, cy = y + h / 2f;

        if (Math.abs(cx - 0.5f) <= epsX) vGuides.add(0.5f);
        if (Math.abs(cy - 0.5f) <= epsY) hGuides.add(0.5f);

        for (VirtualKey other : currentKeys) {
            if (other == activeKey) continue;
            float oLeft = other.relX, oRight = other.relX + other.relWidth, oCx = other.relX + other.relWidth / 2f;
            float oTop = other.relY, oBottom = other.relY + other.relHeight, oCy = other.relY + other.relHeight / 2f;

            if (Math.abs(left - oLeft) <= epsX || Math.abs(right - oLeft) <= epsX) vGuides.add(oLeft);
            if (Math.abs(left - oRight) <= epsX || Math.abs(right - oRight) <= epsX) vGuides.add(oRight);
            if (Math.abs(cx - oCx) <= epsX) vGuides.add(oCx);

            if (Math.abs(top - oTop) <= epsY || Math.abs(bottom - oTop) <= epsY) hGuides.add(oTop);
            if (Math.abs(top - oBottom) <= epsY || Math.abs(bottom - oBottom) <= epsY) hGuides.add(oBottom);
            if (Math.abs(cy - oCy) <= epsY) hGuides.add(oCy);
        }
        alignmentGuideOverlay.setGuides(vGuides, hGuides);
    }

    private void clearAlignmentGuides() {
        if (alignmentGuideOverlay != null) {
            alignmentGuideOverlay.clearGuides();
        }
    }

    private static final float MIN_KEY_WIDTH = 0.02f;
    private static final float MIN_KEY_HEIGHT = 0.03f;

    private View rootLayout;
    private ImageView bgImageView;
    private FrameLayout keysContainer;
    private AlignmentGuideOverlayView alignmentGuideOverlay;
    private View sidebar;
    private View btnOpenMenu;
    private View btnExitPreview;
    private TextView textEmptyPackHint;
    private boolean isPreviewMode = false;
    private boolean previewOverlayHidden = false;
    private PcKeyboardOverlayView previewPcKeyboardOverlay = null;
    private VirtualMouseOverlayView previewMouseOverlay = null;
    private View previewStreamCatcherView = null;
    private boolean isSidebarOnLeft = false;
    private String currentLayoutId;
    private int cutoutInsetLeft = 0;
    private int cutoutInsetRight = 0;

    private View panelGlobalMenu;
    private View panelSubLayoutEditor;
    private View panelButtonInspector;
    private TextView globalMenuTitle;
    private TextView subLayoutEditorTitle;
    private boolean isInSubLayoutEditMode = false;
    private LinearLayout containerSubLayouts;
    private VkSwitchView checkboxSubLayoutActive;
    private View layoutContainerSubLayoutActive;
    private TextView btnMouseModeTouchpad;
    private TextView btnMouseModeDirect;

    private VirtualKeyboardProfileManager.LayoutPackData currentPack;
    private VirtualKeyboardProfileManager.SubLayout currentSubLayout;
    private VirtualKeyboardProfileManager.SubLayout prePreviewSubLayout;

    // Inspector widgets
    private TextView inspectorTitle;
    private EditText editKeyLabel;
    private TextView btnChangeKeycode;
    private View layoutContainerChangeLayout;
    private View rowChangeLayout;
    private VkSwitchView checkboxKeyChangeLayout;
    private TextView btnSelectTargetLayout;
    private VkSliderView seekbarKeyWidth;
    private VkSliderView seekbarKeyHeight;
    private VkSliderView seekbarKeyRadius;
    private VkSliderView seekbarKeyBgOpacity;
    private VkSliderView seekbarKeyTextOpacity;
    private VkSwitchView checkboxSnapToGrid;
    private VkSwitchView checkboxKeySlide;
    private VkSwitchView checkboxKeyHoldOutside;
    private VkSwitchView checkboxKeyAbsorbTouch;
    private VkSwitchView checkboxKeyBlockTouch;
    private VkSwitchView checkboxKeySwitchInput;
    private VkSwitchView checkboxKeyLockSquare;
    private View layoutContainerSnap;
    private View layoutContainerSlide;
    private View layoutContainerHoldOutside;
    private View layoutContainerAbsorbTouch;
    private View layoutContainerBlockTouch;
    private View layoutContainerSwitchInput;
    private View layoutContainerLockSquare;
    private View dividerInspectorToggles;
    private View layoutKeyLabelContainer;

    private View layoutControllerMapping;
    private TextView btnKeyUp;
    private TextView btnKeyDown;
    private TextView btnKeyLeft;
    private TextView btnKeyRight;

    private TextView labelKeyWidth;
    private TextView labelKeyHeight;
    private TextView labelKeyRadius;
    private TextView labelKeyBgOpacity;
    private TextView labelKeyTextOpacity;
    private VkSliderView seekbarKeyPosX;
    private VkSliderView seekbarKeyPosY;
    private TextView labelKeyPosX;
    private TextView labelKeyPosY;

    private List<VirtualKey> currentKeys = new ArrayList<>();
    private final List<VirtualKeyView> currentKeyViews = new ArrayList<>();
    private VirtualKey selectedKey = null;
    private VirtualKeyView selectedKeyView = null;
    private TextView btnExitEditor = null;
    private boolean isDirty = false;
    private boolean isBindingUI = false;
    private boolean snapEnabled = true;

    public static class KeyOption {
        final String name;
        final VirtualKey.KeyType type;
        final int code;

        KeyOption(String name, VirtualKey.KeyType type, int code) {
            this.name = name;
            this.type = type;
            this.code = code;
        }
    }

    private final List<KeyOption> availableKeyOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();
        setContentView(R.layout.activity_edit_keyboard_layout);

        initKeyOptions();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } catch (Throwable ignored) {}
        }

        rootLayout = findViewById(R.id.editor_root);
        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        cutoutInsetLeft = cutout.getSafeInsetLeft();
                        cutoutInsetRight = cutout.getSafeInsetRight();
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets sbInsets = insets.getInsets(
                            WindowInsets.Type.displayCutout() | WindowInsets.Type.systemBars()
                    );
                    if (sbInsets.left > cutoutInsetLeft) {
                        cutoutInsetLeft = sbInsets.left;
                    }
                    if (sbInsets.right > cutoutInsetRight) {
                        cutoutInsetRight = sbInsets.right;
                    }
                }
                updateSidebarMargins();
                return insets;
            }
        });
        bgImageView = findViewById(R.id.editor_bg_image);
        keysContainer = findViewById(R.id.editor_keys_container);
        keysContainer.setClipChildren(false);
        keysContainer.setClipToPadding(false);

        alignmentGuideOverlay = new AlignmentGuideOverlayView(this);
        if (keysContainer.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) keysContainer.getParent();
            int idx = parent.indexOfChild(keysContainer);
            parent.addView(alignmentGuideOverlay, idx + 1,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        sidebar = findViewById(R.id.editor_sidebar);
        btnOpenMenu = findViewById(R.id.btn_open_menu);
        btnExitPreview = findViewById(R.id.btn_exit_preview);
        textEmptyPackHint = findViewById(R.id.text_empty_pack_hint);

        panelGlobalMenu = findViewById(R.id.panel_global_menu);
        panelSubLayoutEditor = findViewById(R.id.panel_sublayout_editor);
        panelButtonInspector = findViewById(R.id.panel_button_inspector);
        globalMenuTitle = findViewById(R.id.global_menu_title);
        subLayoutEditorTitle = findViewById(R.id.sublayout_editor_title);
        containerSubLayouts = findViewById(R.id.container_sublayouts);
        checkboxSubLayoutActive = findViewById(R.id.checkbox_sublayout_active);
        layoutContainerSubLayoutActive = findViewById(R.id.layout_container_sublayout_active);
        btnMouseModeTouchpad = findViewById(R.id.btn_mouse_mode_touchpad);
        btnMouseModeDirect = findViewById(R.id.btn_mouse_mode_direct);

        inspectorTitle = findViewById(R.id.inspector_title);
        editKeyLabel = findViewById(R.id.edit_key_label);
        btnChangeKeycode = findViewById(R.id.btn_change_keycode);
        layoutContainerChangeLayout = findViewById(R.id.layout_container_change_layout);
        rowChangeLayout = findViewById(R.id.row_change_layout);
        checkboxKeyChangeLayout = findViewById(R.id.checkbox_key_change_layout);
        btnSelectTargetLayout = findViewById(R.id.btn_select_target_layout);
        seekbarKeyWidth = findViewById(R.id.seekbar_key_width);
        seekbarKeyHeight = findViewById(R.id.seekbar_key_height);
        seekbarKeyRadius = findViewById(R.id.seekbar_key_radius);
        seekbarKeyBgOpacity = findViewById(R.id.seekbar_key_bg_opacity);
        seekbarKeyTextOpacity = findViewById(R.id.seekbar_key_text_opacity);
        checkboxSnapToGrid = findViewById(R.id.checkbox_snap_to_grid);
        checkboxKeySlide = findViewById(R.id.checkbox_key_slide);
        checkboxKeyHoldOutside = findViewById(R.id.checkbox_key_hold_outside);
        checkboxKeyAbsorbTouch = findViewById(R.id.checkbox_key_absorb_touch);
        checkboxKeyBlockTouch = findViewById(R.id.checkbox_key_block_touch);
        checkboxKeySwitchInput = findViewById(R.id.checkbox_key_switch_input);
        checkboxKeyLockSquare = findViewById(R.id.checkbox_key_lock_square);
        layoutContainerSnap = findViewById(R.id.layout_container_snap);
        layoutContainerSlide = findViewById(R.id.layout_container_slide);
        layoutContainerHoldOutside = findViewById(R.id.layout_container_hold_outside);
        layoutContainerAbsorbTouch = findViewById(R.id.layout_container_absorb_touch);
        layoutContainerBlockTouch = findViewById(R.id.layout_container_block_touch);
        layoutContainerSwitchInput = findViewById(R.id.layout_container_switch_input);
        layoutContainerLockSquare = findViewById(R.id.layout_container_lock_square);
        dividerInspectorToggles = findViewById(R.id.divider_inspector_toggles);
        layoutKeyLabelContainer = findViewById(R.id.layout_key_label_container);

        layoutControllerMapping = findViewById(R.id.layout_controller_mapping);
        btnKeyUp = findViewById(R.id.btn_key_up);
        btnKeyDown = findViewById(R.id.btn_key_down);
        btnKeyLeft = findViewById(R.id.btn_key_left);
        btnKeyRight = findViewById(R.id.btn_key_right);

        btnExitEditor = findViewById(R.id.btn_exit_editor);
        setDirty(false);

        labelKeyWidth = findViewById(R.id.label_key_width);
        labelKeyHeight = findViewById(R.id.label_key_height);
        labelKeyRadius = findViewById(R.id.label_key_radius);
        labelKeyBgOpacity = findViewById(R.id.label_key_bg_opacity);
        labelKeyTextOpacity = findViewById(R.id.label_key_text_opacity);
        seekbarKeyPosX = findViewById(R.id.seekbar_key_pos_x);
        seekbarKeyPosY = findViewById(R.id.seekbar_key_pos_y);
        labelKeyPosX = findViewById(R.id.label_key_pos_x);
        labelKeyPosY = findViewById(R.id.label_key_pos_y);

        setupGlobalMenuEvents();
        setupInspectorEvents();
        setupBackgroundDragForSelectedKey();
        applyAccentTheme();

        currentLayoutId = getIntent().getStringExtra(ManageKeyboardLayoutsActivity.EXTRA_LAYOUT_ID);
        if (currentLayoutId == null || currentLayoutId.trim().isEmpty()) {
            currentLayoutId = VirtualKeyboardProfileManager.getActiveProfileId(this);
        }
        if (currentLayoutId == null || currentLayoutId.trim().isEmpty()) {
            VirtualKeyboardProfileManager.LayoutProfileInfo p =
                    VirtualKeyboardProfileManager.createProfile(this, getString(R.string.vk_custom_layout_name_format, 1), null);
            currentLayoutId = p.id;
            VirtualKeyboardProfileManager.setActiveProfileId(this, currentLayoutId);
        }

        loadBackground();

        VirtualKeyboardProfileManager.LayoutProfileInfo packInfo =
                VirtualKeyboardProfileManager.getProfileInfo(this, currentLayoutId);
        if (globalMenuTitle != null) {
            String packName = (packInfo != null && packInfo.name != null && !packInfo.name.trim().isEmpty())
                    ? packInfo.name.trim() : "Custom";
            globalMenuTitle.setText(packName + " layout pack");
        }

        currentPack = VirtualKeyboardProfileManager.loadPack(this, currentLayoutId);
        currentSubLayout = currentPack.getActiveOrFirstSubLayout();
        currentKeys = currentSubLayout != null ? currentSubLayout.keys : new ArrayList<VirtualKey>();
        keysContainer.post(new Runnable() {
            @Override
            public void run() {
                rebuildKeyViews();
                refreshSubLayoutListUI();
                updateEmptyPackHint();
            }
        });
    }

    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    private void initKeyOptions() {
        // 1. Special Functions / Action keys (at top)
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_action_toggle_overlay), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_action_open_pc_keyboard), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_action_open_pc_mouse), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_TOGGLE_MOUSE));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_action_open_android_keyboard), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_SHOW_ANDROID_KEYBOARD));

        // 2. Mouse & Scroll buttons
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_mouse_left), VirtualKey.KeyType.MOUSE_BUTTON, MouseButtonPacket.BUTTON_LEFT));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_mouse_right), VirtualKey.KeyType.MOUSE_BUTTON, MouseButtonPacket.BUTTON_RIGHT));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_mouse_middle), VirtualKey.KeyType.MOUSE_BUTTON, MouseButtonPacket.BUTTON_MIDDLE));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_mouse_back), VirtualKey.KeyType.MOUSE_BUTTON, MouseButtonPacket.BUTTON_X1));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_mouse_forward), VirtualKey.KeyType.MOUSE_BUTTON, MouseButtonPacket.BUTTON_X2));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_scroll_up), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_SCROLL_UP));
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_scroll_down), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_SCROLL_DOWN));

        // 3. Letters A - Z
        for (char c = 'A'; c <= 'Z'; c++) {
            int keycode = KeyEvent.KEYCODE_A + (c - 'A');
            availableKeyOptions.add(new KeyOption(String.valueOf(c), VirtualKey.KeyType.KEYBOARD, keycode));
        }

        // 4. Numbers 0 - 9
        for (int i = 0; i <= 9; i++) {
            availableKeyOptions.add(new KeyOption(String.valueOf(i), VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_0 + i));
        }

        // 5. Functional & Special keys
        availableKeyOptions.add(new KeyOption("Space", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SPACE));
        availableKeyOptions.add(new KeyOption("Enter", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_ENTER));
        availableKeyOptions.add(new KeyOption("Backspace", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_DEL));
        availableKeyOptions.add(new KeyOption("Tab", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_TAB));
        availableKeyOptions.add(new KeyOption("Escape", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_ESCAPE));
        availableKeyOptions.add(new KeyOption("Caps Lock", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_CAPS_LOCK));

        availableKeyOptions.add(new KeyOption("Shift (Left)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SHIFT_LEFT));
        availableKeyOptions.add(new KeyOption("Shift (Right)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SHIFT_RIGHT));
        availableKeyOptions.add(new KeyOption("Ctrl (Left)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_CTRL_LEFT));
        availableKeyOptions.add(new KeyOption("Ctrl (Right)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_CTRL_RIGHT));
        availableKeyOptions.add(new KeyOption("Alt (Left)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_ALT_LEFT));
        availableKeyOptions.add(new KeyOption("Alt (Right)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_ALT_RIGHT));
        availableKeyOptions.add(new KeyOption("Windows / Meta", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_META_LEFT));

        availableKeyOptions.add(new KeyOption("Arrow Up", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_DPAD_UP));
        availableKeyOptions.add(new KeyOption("Arrow Down", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_DPAD_DOWN));
        availableKeyOptions.add(new KeyOption("Arrow Left", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_DPAD_LEFT));
        availableKeyOptions.add(new KeyOption("Arrow Right", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_DPAD_RIGHT));

        availableKeyOptions.add(new KeyOption("Insert", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_INSERT));
        availableKeyOptions.add(new KeyOption("Delete", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_FORWARD_DEL));
        availableKeyOptions.add(new KeyOption("Home", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_MOVE_HOME));
        availableKeyOptions.add(new KeyOption("End", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_MOVE_END));
        availableKeyOptions.add(new KeyOption("Page Up", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_PAGE_UP));
        availableKeyOptions.add(new KeyOption("Page Down", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_PAGE_DOWN));

        availableKeyOptions.add(new KeyOption("Print Screen", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SYSRQ));
        availableKeyOptions.add(new KeyOption("Scroll Lock", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SCROLL_LOCK));
        availableKeyOptions.add(new KeyOption("Pause / Break", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_BREAK));

        for (int i = 1; i <= 12; i++) {
            availableKeyOptions.add(new KeyOption("F" + i, VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_F1 + (i - 1)));
        }

        availableKeyOptions.add(new KeyOption("~ (Grave / Tilde)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_GRAVE));
        availableKeyOptions.add(new KeyOption("- (Minus)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_MINUS));
        availableKeyOptions.add(new KeyOption("= (Equals)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_EQUALS));
        availableKeyOptions.add(new KeyOption("[ (Left Bracket)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_LEFT_BRACKET));
        availableKeyOptions.add(new KeyOption("] (Right Bracket)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_RIGHT_BRACKET));
        availableKeyOptions.add(new KeyOption("\\ (Backslash)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_BACKSLASH));
        availableKeyOptions.add(new KeyOption("; (Semicolon)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SEMICOLON));
        availableKeyOptions.add(new KeyOption("' (Apostrophe)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_APOSTROPHE));
        availableKeyOptions.add(new KeyOption(", (Comma)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_COMMA));
        availableKeyOptions.add(new KeyOption(". (Period)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_PERIOD));
        availableKeyOptions.add(new KeyOption("/ (Slash)", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SLASH));
    }

    private void setupGlobalMenuEvents() {
        btnOpenMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGlobalMenu();
            }
        });

        View.OnClickListener swapSidebarSideListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSidebarOnLeft = !isSidebarOnLeft;
                applySidebarPosition(isSidebarOnLeft);
            }
        };
        View btnSwapGlobal = findViewById(R.id.btn_swap_sidebar_global);
        if (btnSwapGlobal != null) btnSwapGlobal.setOnClickListener(swapSidebarSideListener);
        View btnSwapSub = findViewById(R.id.btn_swap_sidebar_sublayout);
        if (btnSwapSub != null) btnSwapSub.setOnClickListener(swapSidebarSideListener);

        findViewById(R.id.btn_close_sidebar_global).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
            }
        });

        findViewById(R.id.btn_add_sublayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateSubLayoutDialog();
            }
        });

        findViewById(R.id.btn_back_to_pack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGlobalMenu();
            }
        });

        findViewById(R.id.btn_close_sidebar_sublayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
            }
        });

        findViewById(R.id.btn_rename_sublayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSubLayout != null) {
                    showRenameSubLayoutDialog(currentSubLayout);
                }
            }
        });

        View btnCloneSubLayout = findViewById(R.id.btn_clone_sublayout);
        if (btnCloneSubLayout != null) {
            btnCloneSubLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentSubLayout != null) {
                        cloneSubLayout(currentSubLayout);
                    }
                }
            });
        }

        findViewById(R.id.btn_delete_sublayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSubLayout != null) {
                    confirmDeleteSubLayout(currentSubLayout);
                }
            }
        });

        if (checkboxSubLayoutActive != null) {
            bindToggleRow(layoutContainerSubLayoutActive, checkboxSubLayoutActive, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (isBindingUI || currentPack == null || currentSubLayout == null) return;
                    if (isChecked) {
                        currentPack.activeSubLayoutId = currentSubLayout.id;
                        refreshSubLayoutListUI();
                        setDirty(true);
                    } else if (currentSubLayout.id.equals(currentPack.activeSubLayoutId)) {
                        // Keep at least one active layout when toggled off only if another exists, otherwise stay active
                        switchView.setChecked(true);
                    }
                }
            });
        }

        if (btnMouseModeTouchpad != null) {
            btnMouseModeTouchpad.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentSubLayout == null) return;
                    currentSubLayout.mouseInputMode = VirtualKeyboardProfileManager.MOUSE_MODE_TOUCHPAD;
                    updateMouseInputModeButtons(currentSubLayout);
                    setDirty(true);
                }
            });
        }

        if (btnMouseModeDirect != null) {
            btnMouseModeDirect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentSubLayout == null) return;
                    currentSubLayout.mouseInputMode = VirtualKeyboardProfileManager.MOUSE_MODE_DIRECT;
                    updateMouseInputModeButtons(currentSubLayout);
                    setDirty(true);
                }
            });
        }

        findViewById(R.id.btn_add_key).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewKey();
            }
        });

        findViewById(R.id.btn_add_controller).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewController();
            }
        });

        findViewById(R.id.btn_pick_bg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_IMAGE);
            }
        });

        findViewById(R.id.btn_clear_bg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VirtualKeyboardProfileManager.clearBackgroundImage(EditKeyboardLayoutActivity.this, currentLayoutId);
                loadBackground();
                Toast.makeText(EditKeyboardLayoutActivity.this, R.string.vk_toast_background_cleared, Toast.LENGTH_SHORT).show();
            }
        });

        final TextView textBgOpacity = findViewById(R.id.text_bg_opacity_label);
        VkSliderView seekbarBgOpacity = findViewById(R.id.seekbar_bg_opacity);
        int currentBgOpacity = VirtualKeyboardProfileManager.getBackgroundOpacity(this, currentLayoutId);
        seekbarBgOpacity.setMax(100);
        seekbarBgOpacity.setProgress(currentBgOpacity);
        textBgOpacity.setText(getString(R.string.vk_background_opacity_format, currentBgOpacity));

        seekbarBgOpacity.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                int val = Math.max(10, progress);
                textBgOpacity.setText(getString(R.string.vk_background_opacity_format, val));
                bgImageView.setAlpha(val / 100f);
                if (fromUser) {
                    VirtualKeyboardProfileManager.saveBackgroundOpacity(EditKeyboardLayoutActivity.this, currentLayoutId, val);
                }
            }
        });

        findViewById(R.id.btn_preview_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterPreviewMode();
            }
        });

        if (btnExitPreview != null) {
            btnExitPreview.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exitPreviewMode();
                }
            });
        }

        findViewById(R.id.btn_save_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentPack();
                setDirty(false);
                Toast.makeText(EditKeyboardLayoutActivity.this, R.string.vk_toast_layout_saved, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_reset_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(EditKeyboardLayoutActivity.this)
                        .setTitle(R.string.vk_reset_layout_title)
                        .setMessage(R.string.vk_reset_layout_message)
                        .setPositiveButton(R.string.vk_reset, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                VirtualKeyboardProfileManager.resetProfile(EditKeyboardLayoutActivity.this, currentLayoutId);
                                currentPack = VirtualKeyboardProfileManager.loadPack(EditKeyboardLayoutActivity.this, currentLayoutId);
                                currentSubLayout = currentPack.getActiveOrFirstSubLayout();
                                currentKeys = currentSubLayout != null ? currentSubLayout.keys : new ArrayList<VirtualKey>();
                                rebuildKeyViews();
                                refreshSubLayoutListUI();
                                setDirty(false);
                                Toast.makeText(EditKeyboardLayoutActivity.this, R.string.vk_toast_layout_reset, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.vk_cancel, null)
                        .show();
            }
        });

        btnExitEditor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleExit();
            }
        });

        View btnCloseMenuBottom = findViewById(R.id.btn_close_menu_bottom);
        if (btnCloseMenuBottom != null) {
            btnCloseMenuBottom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSidebar();
                }
            });
        }
    }

    private void setDirty(boolean dirty) {
        this.isDirty = dirty;
        if (btnExitEditor != null) {
            btnExitEditor.setText(dirty ? R.string.vk_exit_unsaved : R.string.vk_exit);
            btnExitEditor.setTextColor(dirty ? 0xFFFFCC00 : 0xFFFFFFFF);
        }
    }

    private void handleExit() {
        if (isDirty) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.vk_unsaved_title)
                    .setMessage(R.string.vk_unsaved_message)
                    .setPositiveButton(R.string.vk_save_and_exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveCurrentPack();
                            setDirty(false);
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.vk_discard, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNeutralButton(R.string.vk_cancel, null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (isPreviewMode) {
            exitPreviewMode();
            return;
        }
        if (sidebar != null && sidebar.getVisibility() == View.VISIBLE) {
            closeSidebar();
            return;
        }
        handleExit();
    }

    private void setupInspectorEvents() {
        findViewById(R.id.btn_close_inspector).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSubLayout != null) {
                    showSubLayoutEditor(currentSubLayout);
                } else {
                    closeSidebar();
                }
            }
        });

        findViewById(R.id.btn_done_inspector).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentSubLayout != null) {
                    showSubLayoutEditor(currentSubLayout);
                } else {
                    closeSidebar();
                }
            }
        });

        editKeyLabel.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.label = s.toString();
                    selectedKeyView.invalidate();
                    if (!isBindingUI) {
                        setDirty(true);
                    }
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnChangeKeycode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showKeySelectorDialog();
            }
        });

        if (checkboxKeyChangeLayout != null) {
            bindToggleRow(rowChangeLayout, checkboxKeyChangeLayout, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (selectedKey == null) return;
                    selectedKey.changeLayout = isChecked;
                    if (isChecked && !isBindingUI && (selectedKey.targetSubLayoutId == null
                            || (currentPack != null && currentPack.findSubLayout(selectedKey.targetSubLayoutId) == null))) {
                        if (currentPack != null) {
                            for (VirtualKeyboardProfileManager.SubLayout s : currentPack.subLayouts) {
                                if (currentSubLayout == null || !s.id.equals(currentSubLayout.id)) {
                                    selectedKey.targetSubLayoutId = s.id;
                                    break;
                                }
                            }
                        }
                    }
                    updateTargetLayoutButtonText(selectedKey);
                    if (btnSelectTargetLayout != null) {
                        btnSelectTargetLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                    if (!isBindingUI) setDirty(true);
                }
            });
        }

        if (btnSelectTargetLayout != null) {
            btnSelectTargetLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTargetSubLayoutPickerDialog();
                }
            });
        }

        seekbarKeyPosX.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepX = getEffectiveSnapStepX();
                float maxAllowedX = 1.0f - selectedKey.relWidth;
                float newX = progress * snapStepX;
                newX = Math.round(newX / snapStepX) * snapStepX;
                newX = Math.max(0f, Math.min(maxAllowedX, newX));
                selectedKey.relX = newX;
                labelKeyPosX.setText(formatPxX(R.string.vk_label_position_x, newX));
                updateSelectedKeyLayout();
                setDirty(true);
            }
        });

        seekbarKeyPosY.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepY = getEffectiveSnapStepY();
                float maxAllowedY = 1.0f - selectedKey.relHeight;
                float newY = progress * snapStepY;
                newY = Math.round(newY / snapStepY) * snapStepY;
                newY = Math.max(0f, Math.min(maxAllowedY, newY));
                selectedKey.relY = newY;
                labelKeyPosY.setText(formatPxY(R.string.vk_label_position_y, newY));
                updateSelectedKeyLayout();
                setDirty(true);
            }
        });

        // Width / Size slider: interpolates from top-left anchor to right edge by grid steps
        seekbarKeyWidth.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepX = getEffectiveSnapStepX();
                float maxAllowed = 1.0f - selectedKey.relX;
                float newWidth = MIN_KEY_WIDTH + (progress * snapStepX);
                newWidth = Math.min(newWidth, maxAllowed);

                // Snap to grid
                newWidth = Math.round(newWidth / snapStepX) * snapStepX;

                if (selectedKey.isJoystick || selectedKey.lockSquare) {
                    int cW = keysContainer.getWidth();
                    int cH = keysContainer.getHeight();
                    if (cW > 0 && cH > 0) {
                        float px = newWidth * (float) cW;
                        float newH = px / (float) cH;
                        if (newH > (1.0f - selectedKey.relY)) {
                            newH = 1.0f - selectedKey.relY;
                            newWidth = (newH * (float) cH) / (float) cW;
                        }
                        selectedKey.relWidth = newWidth;
                        selectedKey.relHeight = newH;
                    } else {
                        selectedKey.relWidth = newWidth;
                    }
                    labelKeyWidth.setText(formatPxX(R.string.vk_label_size, newWidth));
                } else {
                    selectedKey.relWidth = newWidth;
                    labelKeyWidth.setText(formatPxX(R.string.vk_label_width, newWidth));
                }

                updateSelectedKeyLayout();
                syncPositionSlidersWithSelectedKey();
                setDirty(true);
            }
        });

        // Height slider: interpolates from top-left anchor to bottom edge by grid steps
        seekbarKeyHeight.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepY = getEffectiveSnapStepY();
                float maxAllowed = 1.0f - selectedKey.relY;
                float newHeight = MIN_KEY_HEIGHT + (progress * snapStepY);
                newHeight = Math.min(newHeight, maxAllowed);

                // Snap to grid
                newHeight = Math.round(newHeight / snapStepY) * snapStepY;
                selectedKey.relHeight = newHeight;

                if (selectedKey.isJoystick || selectedKey.lockSquare) {
                    int cW = keysContainer.getWidth();
                    int cH = keysContainer.getHeight();
                    if (cW > 0 && cH > 0) {
                        float py = newHeight * (float) cH;
                        float newW = py / (float) cW;
                        selectedKey.relWidth = Math.min(newW, 1.0f - selectedKey.relX);
                        labelKeyWidth.setText(formatPxX(R.string.vk_label_width, selectedKey.relWidth));
                    }
                }

                labelKeyHeight.setText(formatPxY(R.string.vk_label_height, newHeight));
                updateSelectedKeyLayout();
                syncPositionSlidersWithSelectedKey();
                setDirty(true);
            }
        });

        seekbarKeyRadius.setMax(100);
        seekbarKeyRadius.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                selectedKey.cornerRadius = progress;
                labelKeyRadius.setText("Corner Radius: " + progress + "%");
                selectedKeyView.invalidate();
                setDirty(true);
            }
        });

        if (checkboxKeyLockSquare != null) {
            bindToggleRow(layoutContainerLockSquare, checkboxKeyLockSquare, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (selectedKey == null || selectedKeyView == null) return;
                    selectedKey.lockSquare = isChecked;
                    if (!isBindingUI && isChecked && keysContainer != null) {
                        int cW = keysContainer.getWidth();
                        int cH = keysContainer.getHeight();
                        if (cW > 0 && cH > 0) {
                            float px = selectedKey.relWidth * (float) cW;
                            float newH = px / (float) cH;
                            if (newH > (1.0f - selectedKey.relY)) {
                                newH = 1.0f - selectedKey.relY;
                                selectedKey.relWidth = (newH * (float) cH) / (float) cW;
                            }
                            selectedKey.relHeight = Math.max(MIN_KEY_HEIGHT, newH);
                            updateSelectedKeyLayout();
                        }
                    }
                    updateInspectorUIForKey(selectedKey);
                    syncSlidersWithSelectedKey();
                    if (!isBindingUI) setDirty(true);
                }
            });
        }

        seekbarKeyBgOpacity.setMax(100);
        seekbarKeyBgOpacity.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                selectedKey.bgOpacity = progress;
                labelKeyBgOpacity.setText(getString(R.string.vk_background_opacity_format, progress));
                selectedKeyView.invalidate();
                setDirty(true);
            }
        });

        seekbarKeyTextOpacity.setMax(100);
        seekbarKeyTextOpacity.setOnChangeListener(new VkSliderView.OnChangeListener() {
            @Override
            public void onProgressChanged(VkSliderView slider, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                int val = Math.max(0, progress);
                selectedKey.textOpacity = val;
                labelKeyTextOpacity.setText(getString(R.string.vk_text_opacity_format, val));
                selectedKeyView.invalidate();
                setDirty(true);
            }
        });

        if (checkboxSnapToGrid != null) {
            checkboxSnapToGrid.setChecked(snapEnabled);
            bindToggleRow(layoutContainerSnap, checkboxSnapToGrid, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    snapEnabled = isChecked;
                    if (!isChecked) {
                        clearAlignmentGuides();
                    }
                }
            });
        }

        bindToggleRow(layoutContainerSlide, checkboxKeySlide, new VkSwitchView.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.isSlide = isChecked;
                    selectedKeyView.invalidate();
                    if (!isBindingUI) setDirty(true);
                }
            }
        });

        if (checkboxKeyHoldOutside != null) {
            bindToggleRow(layoutContainerHoldOutside, checkboxKeyHoldOutside, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.holdOutside = isChecked;
                        selectedKeyView.invalidate();
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        if (checkboxKeyAbsorbTouch != null) {
            bindToggleRow(layoutContainerAbsorbTouch, checkboxKeyAbsorbTouch, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.absorbTouch = isChecked;
                        selectedKeyView.invalidate();
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        if (checkboxKeySwitchInput != null) {
            bindToggleRow(layoutContainerSwitchInput, checkboxKeySwitchInput, new VkSwitchView.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.switchInputModeOnToggle = isChecked;
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        bindToggleRow(layoutContainerBlockTouch, checkboxKeyBlockTouch, new VkSwitchView.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VkSwitchView switchView, boolean isChecked) {
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.isBlockTouch = isChecked;
                    updateInspectorUIForKey(selectedKey);
                    if (isChecked) {
                        inspectorTitle.setText(R.string.vk_block_touch);
                    } else {
                        inspectorTitle.setText(getString(R.string.vk_title_button_format, selectedKey.label));
                    }
                    selectedKeyView.invalidate();
                    if (!isBindingUI) setDirty(true);
                }
            }
        });

        btnKeyUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectionKeySelectorDialog(getString(R.string.vk_direction_up), 0);
            }
        });
        btnKeyDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectionKeySelectorDialog(getString(R.string.vk_direction_down), 1);
            }
        });
        btnKeyLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectionKeySelectorDialog(getString(R.string.vk_direction_left), 2);
            }
        });
        btnKeyRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDirectionKeySelectorDialog(getString(R.string.vk_direction_right), 3);
            }
        });

        findViewById(R.id.btn_duplicate_key).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duplicateSelectedKey();
            }
        });

        findViewById(R.id.btn_delete_key).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelectedKey();
            }
        });
    }

    /**
     * Allows 1-finger drag on empty canvas space to move the currently selected button,
     * and holding a 2nd finger on empty space to resize it, while touches on other buttons still target them.
     */
    private void setupBackgroundDragForSelectedKey() {
        keysContainer.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            float initialRelX, initialRelY;
            float initialRelW, initialRelH;
            float p0BaseX, p0BaseY;
            float p1BaseX, p1BaseY;
            boolean isDragging = false;
            boolean isTwoFingerResize = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (selectedKey == null || selectedKeyView == null) return false;
                int cW = keysContainer.getWidth();
                int cH = keysContainer.getHeight();
                if (cW <= 0 || cH <= 0) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        initialRelX = selectedKey.relX;
                        initialRelY = selectedKey.relY;
                        isDragging = false;
                        isTwoFingerResize = false;
                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() == 2) {
                            isTwoFingerResize = true;
                            isDragging = true;
                            initialRelW = selectedKey.relWidth;
                            initialRelH = selectedKey.relHeight;
                            p0BaseX = event.getX(0);
                            p0BaseY = event.getY(0);
                            p1BaseX = event.getX(1);
                            p1BaseY = event.getY(1);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        if (isTwoFingerResize && event.getPointerCount() >= 2) {
                            float dx = (event.getX(0) - p0BaseX) + (event.getX(1) - p1BaseX);
                            float dy = (event.getY(0) - p0BaseY) + (event.getY(1) - p1BaseY);

                            float targetW = initialRelW + (dx / (float) cW);
                            float targetH = initialRelH + (dy / (float) cH);

                            float[] snappedSize = snapSizeToEdges(selectedKey, targetW, targetH, cW, cH);
                            float snappedW = snappedSize[0];
                            float snappedH = snappedSize[1];

                            float maxW = 1.0f - selectedKey.relX;
                            float maxH = 1.0f - selectedKey.relY;

                            if (selectedKey.isJoystick || selectedKey.lockSquare) {
                                float px = snappedW * (float) cW;
                                snappedH = px / (float) cH;
                                if (snappedH > maxH) {
                                    snappedH = maxH;
                                    snappedW = (snappedH * (float) cH) / (float) cW;
                                }
                                if (snappedW > maxW) {
                                    snappedW = maxW;
                                    snappedH = (snappedW * (float) cW) / (float) cH;
                                }
                                snappedW = Math.max(MIN_KEY_WIDTH, snappedW);
                                snappedH = Math.max(MIN_KEY_HEIGHT, snappedH);
                            } else {
                                snappedW = Math.max(MIN_KEY_WIDTH, Math.min(maxW, snappedW));
                                snappedH = Math.max(MIN_KEY_HEIGHT, Math.min(maxH, snappedH));
                            }

                            selectedKey.relWidth = snappedW;
                            selectedKey.relHeight = snappedH;
                            setDirty(true);

                            updateSidebarPositionForCenter(selectedKey.relX + (snappedW / 2f));
                            updateSelectedKeyLayout();
                            syncSlidersWithSelectedKey();
                            return true;
                        }

                        if (!isTwoFingerResize) {
                            float curX = event.getRawX();
                            float curY = event.getRawY();
                            if (!isDragging && (Math.abs(curX - downRawX) > 10 || Math.abs(curY - downRawY) > 10)) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                float targetRelX = initialRelX + ((curX - downRawX) / (float) cW);
                                float targetRelY = initialRelY + ((curY - downRawY) / (float) cH);

                                float[] snappedPos = snapPositionToEdges(selectedKey, targetRelX, targetRelY, cW, cH);
                                float snappedRelX = snappedPos[0];
                                float snappedRelY = snappedPos[1];

                                selectedKey.relX = snappedRelX;
                                selectedKey.relY = snappedRelY;
                                setDirty(true);

                                updateSidebarPositionForCenter(snappedRelX + (selectedKey.relWidth / 2f));
                                updateSelectedKeyLayout();
                                syncPositionSlidersWithSelectedKey();
                            }
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_POINTER_UP:
                        if (isTwoFingerResize && event.getPointerCount() == 2) {
                            isTwoFingerResize = false;
                            isDragging = false;
                            clearAlignmentGuides();
                            int remainingIdx = (event.getActionIndex() == 0) ? 1 : 0;
                            downRawX = event.getX(remainingIdx);
                            downRawY = event.getY(remainingIdx);
                            initialRelX = selectedKey.relX;
                            initialRelY = selectedKey.relY;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        isTwoFingerResize = false;
                        clearAlignmentGuides();
                        return true;
                }
                return false;
            }
        });
    }

    private void applyAccentTheme() {
        int accentColor = VkThemeColors.getAccentColor(this);
        int[] primaryBtnIds = {R.id.btn_add_sublayout, R.id.btn_add_key, R.id.btn_save_layout, R.id.btn_done_inspector};
        for (int id : primaryBtnIds) {
            View v = findViewById(id);
            if (v != null) {
                v.setBackground(VkThemeColors.createPrimaryButtonDrawable(this));
            }
        }
        if (editKeyLabel != null) {
            editKeyLabel.setBackground(VkThemeColors.createInputBackgroundDrawable(this));
        }
        TextView btnAddController = findViewById(R.id.btn_add_controller);
        if (btnAddController != null) {
            btnAddController.setTextColor(accentColor);
        }
        TextView btnPreviewLayout = findViewById(R.id.btn_preview_layout);
        if (btnPreviewLayout != null) {
            btnPreviewLayout.setTextColor(accentColor);
        }
        if (btnChangeKeycode != null) {
            btnChangeKeycode.setTextColor(accentColor);
        }
        if (btnSelectTargetLayout != null) {
            btnSelectTargetLayout.setTextColor(accentColor);
        }
        updateMouseInputModeButtons(currentSubLayout);
    }

    private void bindToggleRow(View container, final VkSwitchView toggle, VkSwitchView.OnCheckedChangeListener listener) {
        if (toggle == null) return;
        toggle.setOnCheckedChangeListener(listener);
        if (container != null) {
            container.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle.toggle();
                }
            });
        }
    }

    private void updateInspectorUIForKey(VirtualKey key) {
        if (key == null) return;
        View labelKeyMapping = findViewById(R.id.label_key_mapping);

        if (key.isJoystick) {
            layoutControllerMapping.setVisibility(View.VISIBLE);
            btnKeyUp.setText(getString(R.string.vk_dir_up_format, getSingleKeyName(key.codeUp)));
            btnKeyDown.setText(getString(R.string.vk_dir_down_format, getSingleKeyName(key.codeDown)));
            btnKeyLeft.setText(getString(R.string.vk_dir_left_format, getSingleKeyName(key.codeLeft)));
            btnKeyRight.setText(getString(R.string.vk_dir_right_format, getSingleKeyName(key.codeRight)));

            layoutKeyLabelContainer.setVisibility(View.GONE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutContainerChangeLayout != null) layoutContainerChangeLayout.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.GONE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            if (layoutContainerLockSquare != null) layoutContainerLockSquare.setVisibility(View.GONE);
            if (dividerInspectorToggles != null) dividerInspectorToggles.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.GONE);
            labelKeyRadius.setVisibility(View.GONE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPxX(R.string.vk_label_size, key.relWidth));
            seekbarKeyHeight.setVisibility(View.GONE);
            labelKeyHeight.setVisibility(View.GONE);
        } else if (key.isScroll) {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.VISIBLE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutContainerChangeLayout != null) layoutContainerChangeLayout.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.GONE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            if (layoutContainerLockSquare != null) layoutContainerLockSquare.setVisibility(View.GONE);
            if (dividerInspectorToggles != null) dividerInspectorToggles.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPxX(R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setText(formatPxY(R.string.vk_label_height, key.relHeight));
        } else if (key.isBlockTouch) {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.GONE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutContainerChangeLayout != null) layoutContainerChangeLayout.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.VISIBLE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            if (layoutContainerLockSquare != null) layoutContainerLockSquare.setVisibility(View.VISIBLE);
            if (dividerInspectorToggles != null) dividerInspectorToggles.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPxX(key.lockSquare ? R.string.vk_label_size : R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(key.lockSquare ? View.GONE : View.VISIBLE);
            labelKeyHeight.setVisibility(key.lockSquare ? View.GONE : View.VISIBLE);
            labelKeyHeight.setText(formatPxY(R.string.vk_label_height, key.relHeight));
        } else {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.VISIBLE);
            btnChangeKeycode.setVisibility(View.VISIBLE);
            if (layoutContainerChangeLayout != null) layoutContainerChangeLayout.setVisibility(View.VISIBLE);
            if (btnSelectTargetLayout != null) {
                btnSelectTargetLayout.setVisibility(key.changeLayout ? View.VISIBLE : View.GONE);
            }
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.VISIBLE);
            boolean isAction = (key.type == VirtualKey.KeyType.ACTION);
            if (isAction) {
                key.isSlide = false;
                key.holdOutside = false;
                key.absorbTouch = false;
                if (checkboxKeySlide != null) checkboxKeySlide.setChecked(false);
                if (checkboxKeyHoldOutside != null) checkboxKeyHoldOutside.setChecked(false);
                if (checkboxKeyAbsorbTouch != null) checkboxKeyAbsorbTouch.setChecked(false);
            }
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.VISIBLE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(isToggleOverlayKey(key) ? View.VISIBLE : View.GONE);
            if (layoutContainerLockSquare != null) layoutContainerLockSquare.setVisibility(View.VISIBLE);
            if (dividerInspectorToggles != null) {
                boolean hasAnyToggle = !isAction || isToggleOverlayKey(key);
                dividerInspectorToggles.setVisibility(hasAnyToggle ? View.VISIBLE : View.GONE);
            }
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPxX(key.lockSquare ? R.string.vk_label_size : R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(key.lockSquare ? View.GONE : View.VISIBLE);
            labelKeyHeight.setVisibility(key.lockSquare ? View.GONE : View.VISIBLE);
            labelKeyHeight.setText(formatPxY(R.string.vk_label_height, key.relHeight));
        }
    }

    private void showDirectionKeySelectorDialog(final String directionName, final int dirIndex) {
        if (selectedKey == null || selectedKeyView == null || !selectedKey.isJoystick) return;

        int curCode = (dirIndex == 0) ? selectedKey.codeUp
                : (dirIndex == 1 ? selectedKey.codeDown : (dirIndex == 2 ? selectedKey.codeLeft : selectedKey.codeRight));

        VkKeyPickerDialog.show(
                this,
                getString(R.string.vk_map_key_title, directionName),
                availableKeyOptions,
                VirtualKey.KeyType.KEYBOARD,
                curCode,
                false,
                false,
                new VkKeyPickerDialog.OnKeySelectedListener() {
                    @Override
                    public void onKeySelected(KeyOption chosen) {
                        if (selectedKey == null || selectedKeyView == null) return;
                        if (dirIndex == 0) {
                            selectedKey.codeUp = chosen.code;
                            btnKeyUp.setText(getString(R.string.vk_dir_up_format, getSingleKeyName(chosen.code)));
                        } else if (dirIndex == 1) {
                            selectedKey.codeDown = chosen.code;
                            btnKeyDown.setText(getString(R.string.vk_dir_down_format, getSingleKeyName(chosen.code)));
                        } else if (dirIndex == 2) {
                            selectedKey.codeLeft = chosen.code;
                            btnKeyLeft.setText(getString(R.string.vk_dir_left_format, getSingleKeyName(chosen.code)));
                        } else if (dirIndex == 3) {
                            selectedKey.codeRight = chosen.code;
                            btnKeyRight.setText(getString(R.string.vk_dir_right_format, getSingleKeyName(chosen.code)));
                        }
                        setDirty(true);
                        selectedKeyView.invalidate();
                    }
                });
    }

    private String getSingleKeyName(int code) {
        for (KeyOption opt : availableKeyOptions) {
            if (opt.code == code && opt.type == VirtualKey.KeyType.KEYBOARD) {
                return opt.name;
            }
        }
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }

    private void applySidebarPosition(boolean isLeft) {
        if (sidebar == null) return;
        FrameLayout.LayoutParams sidebarLp = (FrameLayout.LayoutParams) sidebar.getLayoutParams();
        if (sidebarLp == null) return;
        if (isLeft) {
            sidebarLp.gravity = Gravity.LEFT | Gravity.TOP;
            sidebarLp.leftMargin = cutoutInsetLeft;
            sidebarLp.rightMargin = 0;
        } else {
            sidebarLp.gravity = Gravity.RIGHT | Gravity.TOP;
            sidebarLp.leftMargin = 0;
            sidebarLp.rightMargin = cutoutInsetRight;
        }
        sidebar.setLayoutParams(sidebarLp);
        sidebar.requestLayout();
    }

    private void updateSidebarMargins() {
        if (sidebar == null) return;
        FrameLayout.LayoutParams sidebarLp = (FrameLayout.LayoutParams) sidebar.getLayoutParams();
        if (sidebarLp == null) return;
        int horizontalGravity = sidebarLp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        boolean isLeft = (horizontalGravity == Gravity.LEFT || horizontalGravity == Gravity.START);
        applySidebarPosition(isLeft);
    }

    private void saveCurrentPack() {
        if (currentPack == null) return;
        if (currentSubLayout != null) {
            currentSubLayout.keys = currentKeys;
            if (currentPack.activeSubLayoutId == null || currentPack.findSubLayout(currentPack.activeSubLayoutId) == null) {
                currentPack.activeSubLayoutId = currentSubLayout.id;
            }
        }
        VirtualKeyboardProfileManager.savePack(this, currentPack);
    }

    private void showGlobalMenu() {
        isInSubLayoutEditMode = false;
        deselectCurrentKey();
        applySidebarPosition(isSidebarOnLeft);
        panelGlobalMenu.setVisibility(View.VISIBLE);
        if (panelSubLayoutEditor != null) panelSubLayoutEditor.setVisibility(View.GONE);
        panelButtonInspector.setVisibility(View.GONE);
        refreshSubLayoutListUI();
        sidebar.setVisibility(View.VISIBLE);
        btnOpenMenu.setVisibility(View.GONE);
    }

    private void switchViewedSubLayout(VirtualKeyboardProfileManager.SubLayout sub) {
        if (sub == null) return;
        deselectCurrentKey();
        boolean switched = (currentSubLayout != sub);
        currentSubLayout = sub;
        currentKeys = sub.keys;
        if (switched) {
            rebuildKeyViews();
        }
        updateEmptyPackHint();
        refreshSubLayoutListUI();
    }

    private void showSubLayoutEditor(VirtualKeyboardProfileManager.SubLayout sub) {
        if (sub == null) {
            showGlobalMenu();
            return;
        }
        isInSubLayoutEditMode = true;
        deselectCurrentKey();
        boolean switched = (currentSubLayout != sub);
        currentSubLayout = sub;
        if (currentPack != null && (currentPack.activeSubLayoutId == null
                || currentPack.findSubLayout(currentPack.activeSubLayoutId) == null)) {
            currentPack.activeSubLayoutId = sub.id;
        }
        currentKeys = sub.keys;
        if (switched) {
            rebuildKeyViews();
        }
        updateEmptyPackHint();
        applySidebarPosition(false);
        panelGlobalMenu.setVisibility(View.GONE);
        panelButtonInspector.setVisibility(View.GONE);
        if (panelSubLayoutEditor != null) {
            panelSubLayoutEditor.setVisibility(View.VISIBLE);
        }
        if (subLayoutEditorTitle != null) {
            subLayoutEditorTitle.setText(sub.name);
        }
        isBindingUI = true;
        if (checkboxSubLayoutActive != null && currentPack != null) {
            checkboxSubLayoutActive.setChecked(sub.id.equals(currentPack.activeSubLayoutId));
        }
        updateMouseInputModeButtons(sub);
        isBindingUI = false;
        applySidebarPosition(isSidebarOnLeft);
        sidebar.setVisibility(View.VISIBLE);
        btnOpenMenu.setVisibility(View.GONE);
    }

    private void updateMouseInputModeButtons(VirtualKeyboardProfileManager.SubLayout sub) {
        if (btnMouseModeTouchpad == null || btnMouseModeDirect == null) return;
        boolean isDirect = sub != null
                && VirtualKeyboardProfileManager.MOUSE_MODE_DIRECT.equals(sub.mouseInputMode);
        if (isDirect) {
            btnMouseModeTouchpad.setBackgroundResource(R.drawable.vk_btn_secondary);
            btnMouseModeTouchpad.setTextColor(0xFF8B949E);
            btnMouseModeTouchpad.setTypeface(null, android.graphics.Typeface.NORMAL);
            btnMouseModeDirect.setBackground(VkThemeColors.createPrimaryButtonDrawable(this));
            btnMouseModeDirect.setTextColor(0xFFFFFFFF);
            btnMouseModeDirect.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            btnMouseModeTouchpad.setBackground(VkThemeColors.createPrimaryButtonDrawable(this));
            btnMouseModeTouchpad.setTextColor(0xFFFFFFFF);
            btnMouseModeTouchpad.setTypeface(null, android.graphics.Typeface.BOLD);
            btnMouseModeDirect.setBackgroundResource(R.drawable.vk_btn_secondary);
            btnMouseModeDirect.setTextColor(0xFF8B949E);
            btnMouseModeDirect.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void updateTargetLayoutButtonText(VirtualKey key) {
        if (btnSelectTargetLayout == null || key == null) return;
        String targetName = null;
        if (currentPack != null && key.targetSubLayoutId != null) {
            VirtualKeyboardProfileManager.SubLayout found = currentPack.findSubLayout(key.targetSubLayoutId);
            if (found != null) {
                targetName = found.name;
            }
        }
        btnSelectTargetLayout.setText(targetName != null ? ("Target: " + targetName) : "Target: Select layout...");
    }

    private void showTargetSubLayoutPickerDialog() {
        if (selectedKey == null || currentPack == null) return;
        if (currentPack.subLayouts.isEmpty()) {
            Toast.makeText(this, "No layouts available in this pack", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[currentPack.subLayouts.size()];
        final String[] ids = new String[currentPack.subLayouts.size()];
        int checkedItem = -1;
        for (int i = 0; i < currentPack.subLayouts.size(); i++) {
            VirtualKeyboardProfileManager.SubLayout s = currentPack.subLayouts.get(i);
            names[i] = s.name;
            ids[i] = s.id;
            if (s.id.equals(selectedKey.targetSubLayoutId)) {
                checkedItem = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Select Target Layout")
                .setSingleChoiceItems(names, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (selectedKey != null) {
                            selectedKey.targetSubLayoutId = ids[which];
                            updateTargetLayoutButtonText(selectedKey);
                            setDirty(true);
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void updateEmptyPackHint() {
        if (textEmptyPackHint == null) return;
        if (isPreviewMode) {
            textEmptyPackHint.setVisibility(View.GONE);
            return;
        }
        if (currentPack == null || currentPack.subLayouts.isEmpty()) {
            textEmptyPackHint.setText("No layouts yet. Open Menu to add one and set it as default.");
            textEmptyPackHint.setVisibility(View.VISIBLE);
        } else if (currentSubLayout != null) {
            textEmptyPackHint.setText("Currently on layout: " + currentSubLayout.name);
            textEmptyPackHint.setVisibility(View.VISIBLE);
        } else {
            textEmptyPackHint.setVisibility(View.GONE);
        }
    }

    private void refreshSubLayoutListUI() {
        updateEmptyPackHint();
        if (containerSubLayouts == null || currentPack == null) return;
        containerSubLayouts.removeAllViews();

        if (currentPack.subLayouts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No layouts yet. Tap Add New Layout.");
            empty.setTextColor(0xFF6E7681);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            empty.setPadding(0, Math.round(dpToPx(4f)), 0, Math.round(dpToPx(4f)));
            containerSubLayouts.addView(empty);
            return;
        }

        int accentColor = VkThemeColors.getAccentColor(this);
        int gap = Math.round(dpToPx(4f));
        int rowH = Math.round(dpToPx(34f));

        for (final VirtualKeyboardProfileManager.SubLayout sub : currentPack.subLayouts) {
            final boolean isDefaultActive = sub.id.equals(currentPack.activeSubLayoutId);
            final boolean isCurrentlyEditing = (currentSubLayout != null && sub.id.equals(currentSubLayout.id));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowH);
            rowLp.bottomMargin = gap;
            row.setLayoutParams(rowLp);

            LinearLayout mainBtn = new LinearLayout(this);
            mainBtn.setOrientation(LinearLayout.HORIZONTAL);
            mainBtn.setGravity(Gravity.CENTER_VERTICAL);
            if (isCurrentlyEditing) {
                android.graphics.drawable.GradientDrawable activeBg = new android.graphics.drawable.GradientDrawable();
                activeBg.setCornerRadius(dpToPx(6f));
                activeBg.setColor(VkThemeColors.withAlpha(accentColor, 0x1A));
                activeBg.setStroke(Math.max(2, Math.round(dpToPx(1.5f))), accentColor);
                mainBtn.setBackground(activeBg);
            } else {
                mainBtn.setBackgroundResource(R.drawable.vk_btn_secondary);
            }
            int padH = Math.round(dpToPx(10f));
            mainBtn.setPadding(padH, 0, padH, 0);
            mainBtn.setClickable(true);
            LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            mainLp.rightMargin = gap;

            TextView nameTv = new TextView(this);
            nameTv.setText((isDefaultActive ? "★ " : "") + sub.name);
            nameTv.setTextColor(isDefaultActive ? accentColor : (isCurrentlyEditing ? 0xFFFFFFFF : 0xFFE6EDF3));
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            if (isDefaultActive || isCurrentlyEditing) {
                nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mainBtn.addView(nameTv, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView countTv = new TextView(this);
            int count = sub.keys != null ? sub.keys.size() : 0;
            countTv.setText(String.valueOf(count));
            countTv.setTextColor(0xFF6E7681);
            countTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            mainBtn.addView(countTv);

            mainBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchViewedSubLayout(sub);
                }
            });
            row.addView(mainBtn, mainLp);

            ImageView editBtn = new ImageView(this);
            editBtn.setImageResource(R.drawable.ic_vk_edit);
            editBtn.setBackgroundResource(R.drawable.vk_btn_secondary);
            int editPad = Math.round(dpToPx(8f));
            editBtn.setPadding(editPad, editPad, editPad, editPad);
            editBtn.setClickable(true);
            editBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSubLayoutEditor(sub);
                }
            });
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(rowH, rowH);
            editLp.rightMargin = gap;
            row.addView(editBtn, editLp);

            TextView delBtn = new TextView(this);
            delBtn.setText("×");
            delBtn.setTextColor(0xFFF87171);
            delBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            delBtn.setGravity(Gravity.CENTER);
            delBtn.setBackgroundResource(R.drawable.vk_btn_danger);
            delBtn.setClickable(true);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteSubLayout(sub);
                }
            });
            row.addView(delBtn, new LinearLayout.LayoutParams(rowH, rowH));

            containerSubLayouts.addView(row);
        }
    }

    private void showCreateSubLayoutDialog() {
        if (currentPack == null) return;
        final EditText input = new EditText(this);
        String defaultName = "Layout " + (currentPack.subLayouts.size() + 1);
        input.setText(defaultName);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);

        int pad = Math.round(dpToPx(16f));
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, Math.round(dpToPx(8f)), pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("New Layout")
                .setView(container)
                .setPositiveButton(R.string.vk_create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            name = "Layout " + (currentPack.subLayouts.size() + 1);
                        }
                        String subId = "sub_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                        VirtualKeyboardProfileManager.SubLayout newSub =
                                new VirtualKeyboardProfileManager.SubLayout(subId, name, new ArrayList<VirtualKey>());
                        currentPack.subLayouts.add(newSub);
                        if (currentPack.activeSubLayoutId == null
                                || currentPack.findSubLayout(currentPack.activeSubLayoutId) == null) {
                            currentPack.activeSubLayoutId = subId;
                        }
                        if (currentSubLayout == null) {
                            currentSubLayout = newSub;
                            currentKeys = newSub.keys;
                            rebuildKeyViews();
                        }
                        setDirty(true);
                        refreshSubLayoutListUI();
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void cloneSubLayout(VirtualKeyboardProfileManager.SubLayout source) {
        if (source == null || currentPack == null) return;
        String subId = "sub_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String baseName = source.name != null ? source.name : "Layout";
        String cloneName = baseName + " Copy";
        List<VirtualKey> clonedKeys = VirtualKeyboardProfileManager.parseJsonToKeys(
                VirtualKeyboardProfileManager.keysToJson(source.keys), this);
        VirtualKeyboardProfileManager.SubLayout cloned = new VirtualKeyboardProfileManager.SubLayout(
                subId, cloneName, source.mouseInputMode, clonedKeys);
        currentPack.subLayouts.add(cloned);
        setDirty(true);
        refreshSubLayoutListUI();
        showSubLayoutEditor(cloned);
        Toast.makeText(this, "Cloned \"" + baseName + "\"", Toast.LENGTH_SHORT).show();
    }

    private void showRenameSubLayoutDialog(final VirtualKeyboardProfileManager.SubLayout sub) {
        if (sub == null) return;
        final EditText input = new EditText(this);
        input.setText(sub.name);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);

        int pad = Math.round(dpToPx(16f));
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, Math.round(dpToPx(8f)), pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Rename Layout")
                .setView(container)
                .setPositiveButton(R.string.vk_done, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            sub.name = name;
                            if (subLayoutEditorTitle != null && currentSubLayout == sub) {
                                subLayoutEditorTitle.setText(name);
                            }
                            updateEmptyPackHint();
                            refreshSubLayoutListUI();
                            setDirty(true);
                        }
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void confirmDeleteSubLayout(final VirtualKeyboardProfileManager.SubLayout sub) {
        if (sub == null || currentPack == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Layout")
                .setMessage("Delete \"" + sub.name + "\" and all its buttons?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        currentPack.subLayouts.remove(sub);
                        if (currentSubLayout == sub) {
                            currentSubLayout = currentPack.getActiveOrFirstSubLayout();
                            currentPack.activeSubLayoutId = currentSubLayout != null ? currentSubLayout.id : null;
                            currentKeys = currentSubLayout != null ? currentSubLayout.keys : new ArrayList<VirtualKey>();
                            rebuildKeyViews();
                        }
                        setDirty(true);
                        showGlobalMenu();
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private String formatPxX(int labelResId, float relVal) {
        int cW = keysContainer != null ? keysContainer.getWidth() : 0;
        int px = cW > 0 ? Math.round(relVal * cW) : 0;
        return getString(R.string.vk_px_int_format, getString(labelResId), px);
    }

    private String formatPxY(int labelResId, float relVal) {
        int cH = keysContainer != null ? keysContainer.getHeight() : 0;
        int px = cH > 0 ? Math.round(relVal * cH) : 0;
        return getString(R.string.vk_px_int_format, getString(labelResId), px);
    }

    private boolean isToggleOverlayKey(VirtualKey key) {
        if (key == null || key.type != VirtualKey.KeyType.ACTION) return false;
        return key.code == VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY
                || VirtualKeyboardLayout.ACTION_TOGGLE_VISIBILITY.equals(key.id);
    }

    /**
     * Refreshes the Position X/Y readouts and sliders from the selected key. Invoked on every
     * rectangle change (drag, resize and the size sliders) so the position tracks in real time.
     */
    private void syncPositionSlidersWithSelectedKey() {
        if (selectedKey == null) return;

        float snapStepX = getEffectiveSnapStepX();
        float snapStepY = getEffectiveSnapStepY();

        float maxAllowedX = 1.0f - selectedKey.relWidth;
        int maxStepsX = Math.max(1, Math.round(maxAllowedX / snapStepX));
        seekbarKeyPosX.setMax(maxStepsX);
        int currentStepX = Math.round(selectedKey.relX / snapStepX);
        seekbarKeyPosX.setProgress(Math.max(0, Math.min(maxStepsX, currentStepX)));
        labelKeyPosX.setText(formatPxX(R.string.vk_label_position_x, selectedKey.relX));

        float maxAllowedY = 1.0f - selectedKey.relHeight;
        int maxStepsY = Math.max(1, Math.round(maxAllowedY / snapStepY));
        seekbarKeyPosY.setMax(maxStepsY);
        int currentStepY = Math.round(selectedKey.relY / snapStepY);
        seekbarKeyPosY.setProgress(Math.max(0, Math.min(maxStepsY, currentStepY)));
        labelKeyPosY.setText(formatPxY(R.string.vk_label_position_y, selectedKey.relY));
    }

    private void syncSlidersWithSelectedKey() {
        if (selectedKey == null || selectedKeyView == null) return;

        float snapStepX = getEffectiveSnapStepX();
        float snapStepY = getEffectiveSnapStepY();

        // Configure position X and Y sliders
        syncPositionSlidersWithSelectedKey();

        // Configure width/size slider according to top-left anchor delta to right screen edge
        float maxAllowedW = 1.0f - selectedKey.relX;
        int maxStepsW = Math.max(1, Math.round((maxAllowedW - MIN_KEY_WIDTH) / snapStepX));
        seekbarKeyWidth.setMax(maxStepsW);
        int currentStepW = Math.round((selectedKey.relWidth - MIN_KEY_WIDTH) / snapStepX);
        seekbarKeyWidth.setProgress(Math.max(0, Math.min(maxStepsW, currentStepW)));

        if (selectedKey.isJoystick || selectedKey.lockSquare) {
            labelKeyWidth.setText(formatPxX(R.string.vk_label_size, selectedKey.relWidth));
        } else {
            labelKeyWidth.setText(formatPxX(R.string.vk_label_width, selectedKey.relWidth));

            // Configure height slider according to top-left anchor delta to bottom screen edge
            float maxAllowedH = 1.0f - selectedKey.relY;
            int maxStepsH = Math.max(1, Math.round((maxAllowedH - MIN_KEY_HEIGHT) / snapStepY));
            seekbarKeyHeight.setMax(maxStepsH);
            int currentStepH = Math.round((selectedKey.relHeight - MIN_KEY_HEIGHT) / snapStepY);
            seekbarKeyHeight.setProgress(Math.max(0, Math.min(maxStepsH, currentStepH)));
            labelKeyHeight.setText(formatPxY(R.string.vk_label_height, selectedKey.relHeight));
        }
    }

    private void updateSidebarPositionForCenter(float centerX) {
        if (sidebar == null || panelButtonInspector.getVisibility() != View.VISIBLE) return;
        FrameLayout.LayoutParams sidebarLp = (FrameLayout.LayoutParams) sidebar.getLayoutParams();
        int currentHorizontal = sidebarLp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        boolean isCurrentlyLeft = (currentHorizontal == Gravity.LEFT || currentHorizontal == Gravity.START);

        boolean targetIsLeft;
        if (isCurrentlyLeft) {
            targetIsLeft = (centerX >= 0.46f);
        } else {
            targetIsLeft = (centerX > 0.54f);
        }

        int targetGravity = (targetIsLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP;
        int currentMargin = targetIsLeft ? sidebarLp.leftMargin : sidebarLp.rightMargin;
        int expectedMargin = targetIsLeft ? cutoutInsetLeft : cutoutInsetRight;

        if (sidebarLp.gravity != targetGravity || currentMargin != expectedMargin) {
            applySidebarPosition(targetIsLeft);
        }
    }

    private void showButtonInspector(VirtualKey key, VirtualKeyView view) {
        deselectCurrentKey();
        selectedKey = key;
        selectedKeyView = view;
        selectedKeyView.setSelected(true);

        isBindingUI = true;

        if (key.isJoystick) {
            inspectorTitle.setText(getString(R.string.vk_title_controller_format, key.label));
        } else if (key.isScroll) {
            inspectorTitle.setText(getString(R.string.vk_title_scroll_format, key.label));
        } else if (key.isBlockTouch) {
            inspectorTitle.setText(R.string.vk_block_touch);
        } else {
            inspectorTitle.setText(getString(R.string.vk_title_button_format, key.label));
        }
        editKeyLabel.setText(key.label);
        btnChangeKeycode.setText(getString(R.string.vk_key_format, getKeyDisplayName(key)));

        // Sync width and height sliders with top-left anchor grid deltas
        syncSlidersWithSelectedKey();

        int radiusPct = Math.max(0, Math.min(100, Math.round(key.cornerRadius)));
        seekbarKeyRadius.setProgress(radiusPct);
        labelKeyRadius.setText("Corner Radius: " + radiusPct + "%");

        seekbarKeyBgOpacity.setProgress(key.bgOpacity);
        labelKeyBgOpacity.setText(getString(R.string.vk_background_opacity_format, key.bgOpacity));

        seekbarKeyTextOpacity.setProgress(key.textOpacity);
        labelKeyTextOpacity.setText(getString(R.string.vk_text_opacity_format, key.textOpacity));

        if (checkboxSnapToGrid != null) checkboxSnapToGrid.setChecked(snapEnabled);
        checkboxKeySlide.setChecked(key.isSlide);
        if (checkboxKeyHoldOutside != null) {
            checkboxKeyHoldOutside.setChecked(key.holdOutside);
        }
        if (checkboxKeyAbsorbTouch != null) {
            checkboxKeyAbsorbTouch.setChecked(key.absorbTouch);
        }
        checkboxKeyBlockTouch.setChecked(key.isBlockTouch);
        if (checkboxKeySwitchInput != null) {
            checkboxKeySwitchInput.setChecked(key.switchInputModeOnToggle);
        }
        if (checkboxKeyChangeLayout != null) {
            checkboxKeyChangeLayout.setChecked(key.changeLayout);
        }
        if (checkboxKeyLockSquare != null) {
            checkboxKeyLockSquare.setChecked(key.lockSquare);
        }
        updateTargetLayoutButtonText(key);
        updateInspectorUIForKey(key);

        isBindingUI = false;

        panelGlobalMenu.setVisibility(View.GONE);
        if (panelSubLayoutEditor != null) panelSubLayoutEditor.setVisibility(View.GONE);
        panelButtonInspector.setVisibility(View.VISIBLE);
        sidebar.setVisibility(View.VISIBLE);
        btnOpenMenu.setVisibility(View.GONE);

        // Dynamically position sidebar to opposite half of the screen
        float centerX = key.relX + (key.relWidth / 2f);
        applySidebarPosition(centerX > 0.5f);
    }

    private void closeSidebar() {
        deselectCurrentKey();
        sidebar.setVisibility(View.GONE);
        btnOpenMenu.setVisibility(View.VISIBLE);
    }

    private void deselectCurrentKey() {
        clearAlignmentGuides();
        if (selectedKeyView != null) {
            selectedKeyView.setSelected(false);
            selectedKeyView = null;
        }
        selectedKey = null;
    }

    private void updateSelectedKeyLayout() {
        if (selectedKey == null || selectedKeyView == null) return;
        int cW = keysContainer.getWidth();
        int cH = keysContainer.getHeight();
        if (cW <= 0 || cH <= 0) return;

        if (previewStreamCatcherView == null) {
            previewStreamCatcherView = new View(this);
            previewStreamCatcherView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    handlePreviewSlideTouchEvent(v, event);
                    return true;
                }
            });
            keysContainer.addView(previewStreamCatcherView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        keysContainer.setOnTouchListener(null);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectedKeyView.getLayoutParams();
        lp.width = Math.round(selectedKey.relWidth * cW);
        lp.height = Math.round(selectedKey.relHeight * cH);
        lp.leftMargin = Math.round(selectedKey.relX * cW);
        lp.topMargin = Math.round(selectedKey.relY * cH);
        selectedKeyView.setLayoutParams(lp);
        selectedKeyView.requestLayout();
        selectedKeyView.invalidate();
    }

    private void ensureActiveSubLayout() {
        if (currentSubLayout == null && currentPack != null) {
            String subId = "sub_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            currentSubLayout = new VirtualKeyboardProfileManager.SubLayout(subId, "Layout 1", new ArrayList<VirtualKey>());
            currentPack.subLayouts.add(currentSubLayout);
            currentPack.activeSubLayoutId = subId;
            currentKeys = currentSubLayout.keys;
        }
    }

    private void addNewKey() {
        ensureActiveSubLayout();
        String id = "key_" + System.currentTimeMillis();
        VirtualKey newKey = new VirtualKey(id, "New", VirtualKey.KeyType.KEYBOARD, 0,
                0.45f, 0.42f, 0.08f, 0.13f);
        newKey.showBorder = true;
        newKey.absorbTouch = true;
        currentKeys.add(newKey);
        setDirty(true);
        rebuildKeyViews();

        for (VirtualKeyView v : currentKeyViews) {
            if (v.getKey().id.equals(id)) {
                showButtonInspector(newKey, v);
                break;
            }
        }
    }

    private void addNewController() {
        ensureActiveSubLayout();
        int cW = keysContainer.getWidth();
        int cH = keysContainer.getHeight();
        if (cW <= 0) cW = 1920;
        if (cH <= 0) cH = 1080;

        String id = "controller_" + System.currentTimeMillis();
        float sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150f, getResources().getDisplayMetrics());
        float relW = Math.max(MIN_KEY_WIDTH, sizePx / (float) cW);
        float relH = Math.max(MIN_KEY_HEIGHT, sizePx / (float) cH);

        VirtualKey controller = new VirtualKey(id, "D-PAD", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_W,
                0.10f, 0.45f, relW, relH);
        controller.isJoystick = true;
        controller.codeUp = KeyEvent.KEYCODE_W;
        controller.codeDown = KeyEvent.KEYCODE_S;
        controller.codeLeft = KeyEvent.KEYCODE_A;
        controller.codeRight = KeyEvent.KEYCODE_D;
        controller.bgOpacity = 40;
        controller.showBorder = true;
        controller.absorbTouch = true;

        currentKeys.add(controller);
        setDirty(true);
        rebuildKeyViews();

        for (VirtualKeyView v : currentKeyViews) {
            if (v.getKey().id.equals(id)) {
                showButtonInspector(controller, v);
                break;
            }
        }
    }

    private void duplicateSelectedKey() {
        if (selectedKey == null || selectedKeyView == null) return;

        String prefix = "key_";
        if (selectedKey.isJoystick || selectedKey.lockSquare) {
            prefix = "controller_";
        } else if (selectedKey.isScroll) {
            prefix = "scroll_";
        }
        String newId = prefix + System.currentTimeMillis();
        int cW = keysContainer != null ? keysContainer.getWidth() : 0;
        int cH = keysContainer != null ? keysContainer.getHeight() : 0;
        float offsetX = cW > 0 ? (dpToPx(12f) / (float) cW) : 0.02f;
        float offsetY = cH > 0 ? (dpToPx(12f) / (float) cH) : 0.02f;
        float newX = Math.min(1.0f - selectedKey.relWidth, selectedKey.relX + offsetX);
        float newY = Math.min(1.0f - selectedKey.relHeight, selectedKey.relY + offsetY);

        VirtualKey copy = new VirtualKey(newId, selectedKey.label, selectedKey.type, selectedKey.code,
                newX, newY, selectedKey.relWidth, selectedKey.relHeight);
        copy.cornerRadius = selectedKey.cornerRadius;
        copy.textOpacity = selectedKey.textOpacity;
        copy.bgOpacity = selectedKey.bgOpacity;
        copy.showBorder = selectedKey.showBorder;
        copy.isSlide = selectedKey.isSlide;
        copy.holdOutside = selectedKey.holdOutside;
        copy.absorbTouch = selectedKey.absorbTouch;
        copy.isBlockTouch = selectedKey.isBlockTouch;
        copy.switchInputModeOnToggle = selectedKey.switchInputModeOnToggle;
        copy.changeLayout = selectedKey.changeLayout;
        copy.targetSubLayoutId = selectedKey.targetSubLayoutId;
        copy.lockSquare = selectedKey.lockSquare;
        copy.isJoystick = selectedKey.isJoystick;
        copy.isScroll = selectedKey.isScroll;
        copy.codeUp = selectedKey.codeUp;
        copy.codeDown = selectedKey.codeDown;
        copy.codeLeft = selectedKey.codeLeft;
        copy.codeRight = selectedKey.codeRight;
        copy.extraCode1 = selectedKey.extraCode1;
        copy.extraCode2 = selectedKey.extraCode2;
        copy.extraCode3 = selectedKey.extraCode3;

        currentKeys.add(copy);
        setDirty(true);
        rebuildKeyViews();

        for (VirtualKeyView v : currentKeyViews) {
            if (v.getKey().id.equals(newId)) {
                showButtonInspector(copy, v);
                break;
            }
        }
        Toast.makeText(this, R.string.vk_toast_button_duplicated, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedKey() {
        if (selectedKey == null || selectedKeyView == null) return;
        currentKeys.remove(selectedKey);
        keysContainer.removeView(selectedKeyView);
        currentKeyViews.remove(selectedKeyView);
        deselectCurrentKey();
        closeSidebar();
        setDirty(true);
        Toast.makeText(this, R.string.vk_toast_button_deleted, Toast.LENGTH_SHORT).show();
    }

    private String formatAutoLabel(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.length() == 1) {
            return lower.toUpperCase(java.util.Locale.ROOT);
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String getKeyDisplayName(VirtualKey key) {
        if (key.type == VirtualKey.KeyType.ACTION) {
            if (key.code == VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY || VirtualKeyboardLayout.ACTION_TOGGLE_VISIBILITY.equals(key.id)) {
                return getString(R.string.vk_binding_toggle_overlay);
            }
            if (key.code == VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD) {
                return getString(R.string.vk_binding_open_pc_keyboard);
            }
            if (key.code == VirtualKey.ACTION_CODE_TOGGLE_MOUSE) {
                return getString(R.string.vk_binding_open_pc_mouse);
            }
            if (key.code == VirtualKey.ACTION_CODE_SHOW_ANDROID_KEYBOARD) {
                return getString(R.string.vk_binding_open_android_keyboard);
            }
            if (key.code == VirtualKey.ACTION_CODE_SCROLL_UP) {
                return getString(R.string.vk_binding_scroll_up);
            }
            if (key.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                return getString(R.string.vk_binding_scroll_down);
            }
            return getString(R.string.vk_binding_action_format, key.code);
        }
        if (key.type == VirtualKey.KeyType.MOUSE_BUTTON) {
            if (key.code == MouseButtonPacket.BUTTON_LEFT) return getString(R.string.vk_binding_mouse_left);
            if (key.code == MouseButtonPacket.BUTTON_RIGHT) return getString(R.string.vk_binding_mouse_right);
            if (key.code == MouseButtonPacket.BUTTON_MIDDLE) return getString(R.string.vk_binding_mouse_middle);
            if (key.code == MouseButtonPacket.BUTTON_X1) return getString(R.string.vk_binding_mouse_back);
            if (key.code == MouseButtonPacket.BUTTON_X2) return getString(R.string.vk_binding_mouse_forward);
            return getString(R.string.vk_binding_mouse_format, key.code);
        }
        if (key.code == 0 && key.extraCode1 == 0 && key.extraCode2 == 0 && key.extraCode3 == 0) {
            return "None";
        }
        StringBuilder sb = new StringBuilder(getSingleKeyName(key.code));
        if (key.extraCode1 != 0) sb.append(" + ").append(getSingleKeyName(key.extraCode1));
        if (key.extraCode2 != 0) sb.append(" + ").append(getSingleKeyName(key.extraCode2));
        if (key.extraCode3 != 0) sb.append(" + ").append(getSingleKeyName(key.extraCode3));
        return sb.toString();
    }

    private void showKeySelectorDialog() {
        VirtualKey.KeyType curType = selectedKey != null ? selectedKey.type : VirtualKey.KeyType.KEYBOARD;
        int[] curCodes = selectedKey != null
                ? new int[]{selectedKey.code, selectedKey.extraCode1, selectedKey.extraCode2, selectedKey.extraCode3}
                : new int[]{0};

        VkKeyPickerDialog.showWithCombo(
                this,
                getString(R.string.vk_select_key_mapping),
                availableKeyOptions,
                curType,
                curCodes,
                true,
                new VkKeyPickerDialog.OnKeySelectedListener() {
                    @Override
                    public void onKeySelected(KeyOption opt) {
                        if (selectedKey != null && selectedKeyView != null) {
                            selectedKey.type = opt.type;
                            selectedKey.code = opt.code;
                            selectedKey.extraCode1 = 0;
                            selectedKey.extraCode2 = 0;
                            selectedKey.extraCode3 = 0;
                            if (opt.type == VirtualKey.KeyType.ACTION) {
                                selectedKey.isSlide = false;
                            }
                            if ("New".equalsIgnoreCase(selectedKey.label)) {
                                if (opt.type == VirtualKey.KeyType.ACTION) {
                                    if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY) selectedKey.label = "Toggle";
                                    else if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD) selectedKey.label = "Kb";
                                    else if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_MOUSE) selectedKey.label = "Mouse";
                                    else if (opt.code == VirtualKey.ACTION_CODE_SHOW_ANDROID_KEYBOARD) selectedKey.label = "Ime";
                                    else if (opt.code == VirtualKey.ACTION_CODE_SCROLL_UP) selectedKey.label = "Sc.up";
                                    else if (opt.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) selectedKey.label = "Sc.dn";
                                } else if (opt.type == VirtualKey.KeyType.KEYBOARD && opt.name.length() <= 3) {
                                    selectedKey.label = formatAutoLabel(opt.name);
                                }
                                editKeyLabel.setText(selectedKey.label);
                            }
                            btnChangeKeycode.setText(getString(R.string.vk_key_format, getKeyDisplayName(selectedKey)));
                            updateInspectorUIForKey(selectedKey);
                            selectedKeyView.invalidate();
                            setDirty(true);
                        }
                    }
                },
                new VkKeyPickerDialog.OnComboSelectedListener() {
                    @Override
                    public void onComboSelected(List<KeyOption> selectedKeys) {
                        if (selectedKey == null || selectedKeyView == null) return;
                        selectedKey.type = VirtualKey.KeyType.KEYBOARD;
                        selectedKey.code = selectedKeys.size() > 0 ? selectedKeys.get(0).code : 0;
                        selectedKey.extraCode1 = selectedKeys.size() > 1 ? selectedKeys.get(1).code : 0;
                        selectedKey.extraCode2 = selectedKeys.size() > 2 ? selectedKeys.get(2).code : 0;
                        selectedKey.extraCode3 = selectedKeys.size() > 3 ? selectedKeys.get(3).code : 0;
                        if (!selectedKeys.isEmpty() && "New".equalsIgnoreCase(selectedKey.label)) {
                            selectedKey.label = formatAutoLabel(getSingleKeyName(selectedKey.code));
                            editKeyLabel.setText(selectedKey.label);
                        }
                        btnChangeKeycode.setText(getString(R.string.vk_key_format, getKeyDisplayName(selectedKey)));
                        updateInspectorUIForKey(selectedKey);
                        selectedKeyView.invalidate();
                        setDirty(true);
                    }
                });
    }

    private void loadBackground() {
        File bgFile = VirtualKeyboardProfileManager.getBackgroundImageFile(this, currentLayoutId);
        int opacity = VirtualKeyboardProfileManager.getBackgroundOpacity(this, currentLayoutId);
        bgImageView.setAlpha(opacity / 100f);

        if (bgFile.exists() && bgFile.length() > 0) {
            Bitmap bmp = BitmapFactory.decodeFile(bgFile.getAbsolutePath());
            if (bmp != null) {
                rootLayout.setBackgroundColor(0xFF000000); // Black when image is loaded
                bgImageView.setImageBitmap(bmp);
                return;
            }
        }
        rootLayout.setBackgroundColor(0xFF2B2D31); // Gray when no image
        bgImageView.setImageDrawable(null);
    }

    private void enterPreviewMode() {
        isPreviewMode = true;
        previewOverlayHidden = false;
        prePreviewSubLayout = currentSubLayout;
        if (currentSubLayout == null && currentPack != null) {
            currentSubLayout = currentPack.getActiveOrFirstSubLayout();
            currentKeys = currentSubLayout != null ? currentSubLayout.keys : new ArrayList<VirtualKey>();
        }
        deselectCurrentKey();
        sidebar.setVisibility(View.GONE);
        btnOpenMenu.setVisibility(View.GONE);
        if (btnExitPreview != null) {
            btnExitPreview.setVisibility(View.VISIBLE);
        }
        updateEmptyPackHint();
        rebuildKeyViewsForPreview();
    }

    private void exitPreviewMode() {
        isPreviewMode = false;
        previewOverlayHidden = false;
        if (textEmptyPackHint != null) {
            textEmptyPackHint.animate().cancel();
            textEmptyPackHint.setAlpha(1f);
        }
        if (btnExitPreview != null) {
            btnExitPreview.setVisibility(View.GONE);
        }
        if (prePreviewSubLayout != null) {
            currentSubLayout = prePreviewSubLayout;
            currentKeys = currentSubLayout.keys;
        }
        prePreviewSubLayout = null;
        for (VirtualKeyView v : new ArrayList<>(currentKeyViews)) {
            v.clearAllSlidePointers();
        }
        if (previewPcKeyboardOverlay != null) {
            previewPcKeyboardOverlay.hideKeyboard();
            keysContainer.removeView(previewPcKeyboardOverlay);
            previewPcKeyboardOverlay = null;
        }
        if (previewMouseOverlay != null) {
            previewMouseOverlay.hideOverlay();
            keysContainer.removeView(previewMouseOverlay);
            previewMouseOverlay = null;
        }
        if (previewStreamCatcherView != null) {
            keysContainer.removeView(previewStreamCatcherView);
            previewStreamCatcherView = null;
        }
        rebuildKeyViews();
        setupBackgroundDragForSelectedKey();
        updateEmptyPackHint();
        showGlobalMenu();
    }

    private void flashPreviewLayoutName(String layoutName) {
        if (textEmptyPackHint == null || layoutName == null) return;
        textEmptyPackHint.animate().cancel();
        textEmptyPackHint.setText("Currently on layout: " + layoutName);
        textEmptyPackHint.setAlpha(1f);
        textEmptyPackHint.setVisibility(View.VISIBLE);
        textEmptyPackHint.bringToFront();
        textEmptyPackHint.animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (textEmptyPackHint != null && isPreviewMode) {
                            textEmptyPackHint.setVisibility(View.GONE);
                            textEmptyPackHint.setAlpha(1f);
                        }
                    }
                })
                .start();
    }

    private boolean isStreamDrivenPreviewKey(VirtualKey k) {
        if (k == null || k.type == VirtualKey.KeyType.ACTION) return false;
        if (k.isJoystick || k.isScroll || k.isBlockTouch) return false;
        return k.isSlide || !k.absorbTouch;
    }

    private void handlePreviewSlideTouchEvent(View sourceView, MotionEvent event) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerCount = event.getPointerCount();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            for (VirtualKeyView v : new ArrayList<>(currentKeyViews)) {
                if (isStreamDrivenPreviewKey(v.getKey())) {
                    v.clearSlidePointersForSource(sourceView);
                }
            }
            return;
        }

        int[] sourceLoc = new int[2];
        sourceView.getLocationOnScreen(sourceLoc);

        if (previewPcKeyboardOverlay != null && previewPcKeyboardOverlay.getVisibility() == View.VISIBLE) {
            int[] pcLoc = new int[2];
            previewPcKeyboardOverlay.getLocationOnScreen(pcLoc);
            for (int p = 0; p < pointerCount; p++) {
                float screenX = sourceLoc[0] + event.getX(p);
                float screenY = sourceLoc[1] + event.getY(p);
                if (screenX >= pcLoc[0] && screenX <= pcLoc[0] + previewPcKeyboardOverlay.getWidth() &&
                        screenY >= pcLoc[1] && screenY <= pcLoc[1] + previewPcKeyboardOverlay.getHeight()) {
                    return;
                }
            }
        }
        if (previewMouseOverlay != null && previewMouseOverlay.getVisibility() == View.VISIBLE) {
            int[] mLoc = new int[2];
            previewMouseOverlay.getLocationOnScreen(mLoc);
            for (int p = 0; p < pointerCount; p++) {
                float screenX = sourceLoc[0] + event.getX(p);
                float screenY = sourceLoc[1] + event.getY(p);
                if (screenX >= mLoc[0] && screenX <= mLoc[0] + previewMouseOverlay.getWidth() &&
                        screenY >= mLoc[1] && screenY <= mLoc[1] + previewMouseOverlay.getHeight()) {
                    return;
                }
            }
        }

        VirtualKeyboardProfileManager.SubLayout subLayoutAtStart = currentSubLayout;

        for (int p = 0; p < pointerCount; p++) {
            if (currentSubLayout != subLayoutAtStart) break;
            int pointerId = event.getPointerId(p);
            long pointerKey = VirtualKeyView.makePointerKey(sourceView, pointerId);
            float screenX = sourceLoc[0] + event.getX(p);
            float screenY = sourceLoc[1] + event.getY(p);

            boolean isPointerLeaving = (action == MotionEvent.ACTION_POINTER_UP && p == actionIndex);
            boolean isPointerArriving = (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                    && p == actionIndex;

            for (VirtualKeyView v : new ArrayList<>(currentKeyViews)) {
                if (currentSubLayout != subLayoutAtStart) break;
                VirtualKey k = v.getKey();
                if (!isStreamDrivenPreviewKey(k)) continue;

                if (isPointerLeaving) {
                    v.onSlidePointerExit(pointerKey, true);
                    continue;
                }

                if (isPointerArriving && v.containsScreenPoint(screenX, screenY)) {
                    v.markPointerDownInside(pointerKey);
                }

                boolean eligible = k.isSlide || v.isPointerDownInside(pointerKey);
                if (eligible && v.containsScreenPoint(screenX, screenY)) {
                    v.onSlidePointerEnter(pointerKey);
                } else {
                    v.onSlidePointerExit(pointerKey, false);
                }
            }
        }
    }

    private void rebuildKeyViewsForPreview() {
        for (VirtualKeyView v : new ArrayList<>(currentKeyViews)) {
            v.clearAllSlidePointers();
            keysContainer.removeView(v);
        }
        currentKeyViews.clear();

        int cW = keysContainer.getWidth();
        int cH = keysContainer.getHeight();
        if (cW <= 0 || cH <= 0) return;

        if (previewStreamCatcherView == null) {
            previewStreamCatcherView = new View(this);
            previewStreamCatcherView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    handlePreviewSlideTouchEvent(v, event);
                    return true;
                }
            });
            keysContainer.addView(previewStreamCatcherView, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        keysContainer.setOnTouchListener(null);

        VirtualKeyView.KeyActionListener previewListener = new VirtualKeyView.KeyActionListener() {
            @Override
            public void onKeyDown(VirtualKey key) {
                if (key.type == VirtualKey.KeyType.ACTION) {
                    if (isToggleOverlayKey(key)) {
                        previewOverlayHidden = !previewOverlayHidden;
                        for (VirtualKeyView kv : currentKeyViews) {
                            boolean keepVisible = !previewOverlayHidden || kv.getKey().type == VirtualKey.KeyType.ACTION;
                            kv.setVisibility(keepVisible ? View.VISIBLE : View.GONE);
                        }
                    } else if (key.code == VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD) {
                        if (previewPcKeyboardOverlay != null) {
                            previewPcKeyboardOverlay.toggleKeyboard();
                        }
                    } else if (key.code == VirtualKey.ACTION_CODE_TOGGLE_MOUSE) {
                        if (previewMouseOverlay != null) {
                            previewMouseOverlay.toggleOverlay();
                        }
                    }
                }
                if (key.changeLayout && key.targetSubLayoutId != null && currentPack != null) {
                    VirtualKeyboardProfileManager.SubLayout target = currentPack.findSubLayout(key.targetSubLayoutId);
                    if (target != null && target != currentSubLayout) {
                        currentSubLayout = target;
                        currentKeys = target.keys;
                        previewOverlayHidden = false;
                        rebuildKeyViewsForPreview();
                        flashPreviewLayoutName(target.name);
                    }
                }
            }

            @Override
            public void onKeyUp(VirtualKey key) {}

            @Override
            public void onKeyTouchEvent(VirtualKeyView source, MotionEvent event) {
                handlePreviewSlideTouchEvent(source, event);
            }

            @Override
            public void onExplicitKeyDown(int keycode) {}

            @Override
            public void onExplicitKeyUp(int keycode) {}

            @Override
            public void onScroll(short scrollAmount) {}
        };

        List<VirtualKey> orderedKeys = new ArrayList<>();
        for (VirtualKey k : currentKeys) {
            if (k.isBlockTouch) orderedKeys.add(k);
        }
        for (VirtualKey k : currentKeys) {
            if (!k.isBlockTouch) orderedKeys.add(k);
        }

        for (VirtualKey key : orderedKeys) {
            VirtualKeyView keyView = new VirtualKeyView(this, key, previewListener);
            keyView.setOpacity(100);
            keyView.setHapticEnabled(true);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.round(key.relWidth * cW),
                    Math.round(key.relHeight * cH)
            );
            lp.leftMargin = Math.round(key.relX * cW);
            lp.topMargin = Math.round(key.relY * cH);

            keysContainer.addView(keyView, lp);
            currentKeyViews.add(keyView);
        }

        if (previewPcKeyboardOverlay == null) {
            previewPcKeyboardOverlay = new PcKeyboardOverlayView(this, null, null);
            previewPcKeyboardOverlay.setHapticEnabled(true);
            previewPcKeyboardOverlay.setVisibility(View.GONE);
            int maxPcW = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 680f, getResources().getDisplayMetrics()));
            int pcKbW = Math.min(Math.round(cW * 0.80f), maxPcW);
            int pcKbH = previewPcKeyboardOverlay.calculatePerfectHeight(pcKbW);
            FrameLayout.LayoutParams pcLp = new FrameLayout.LayoutParams(pcKbW, pcKbH);
            pcLp.leftMargin = (cW - pcKbW) / 2;
            pcLp.topMargin = (cH - pcKbH) / 2;
            keysContainer.addView(previewPcKeyboardOverlay, pcLp);
        } else {
            previewPcKeyboardOverlay.bringToFront();
        }

        if (previewMouseOverlay == null) {
            previewMouseOverlay = new VirtualMouseOverlayView(this, null);
            previewMouseOverlay.setHapticEnabled(true);
            previewMouseOverlay.setVisibility(View.GONE);
            int mouseW = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 216f, getResources().getDisplayMetrics()));
            int mouseH = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 110f, getResources().getDisplayMetrics()));
            mouseW = Math.min(mouseW, Math.round(cW * 0.42f));
            mouseH = Math.min(mouseH, Math.round(cH * 0.42f));
            FrameLayout.LayoutParams mouseLp = new FrameLayout.LayoutParams(mouseW, mouseH);
            mouseLp.leftMargin = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, getResources().getDisplayMetrics()));
            mouseLp.topMargin = Math.max(0, cH - mouseH - Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60f, getResources().getDisplayMetrics())));
            keysContainer.addView(previewMouseOverlay, mouseLp);
        } else {
            previewMouseOverlay.bringToFront();
        }
    }

    private void rebuildKeyViews() {
        keysContainer.removeAllViews();
        currentKeyViews.clear();
        deselectCurrentKey();

        int containerW = keysContainer.getWidth();
        int containerH = keysContainer.getHeight();
        if (containerW <= 0 || containerH <= 0) return;

        List<VirtualKey> orderedKeys = new ArrayList<>();
        for (VirtualKey k : currentKeys) {
            if (k.isBlockTouch) orderedKeys.add(k);
        }
        for (VirtualKey k : currentKeys) {
            if (!k.isBlockTouch) orderedKeys.add(k);
        }

        for (final VirtualKey key : orderedKeys) {
            final VirtualKeyView keyView = new VirtualKeyView(this, key, new VirtualKeyView.KeyActionListener() {
                @Override
                public void onKeyDown(VirtualKey k) {}

                @Override
                public void onKeyUp(VirtualKey k) {}

                @Override
                public void onKeyTouchEvent(VirtualKeyView s, MotionEvent e) {}

                @Override
                public void onExplicitKeyDown(int keycode) {}

                @Override
                public void onExplicitKeyUp(int keycode) {}

                @Override
                public void onScroll(short scrollAmount) {}
            });
            keyView.setHapticEnabled(false);

            int x = Math.round(key.relX * containerW);
            int y = Math.round(key.relY * containerH);
            int w = Math.round(key.relWidth * containerW);
            int h = Math.round(key.relHeight * containerH);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            lp.setMargins(x, y, 0, 0);
            keysContainer.addView(keyView, lp);
            currentKeyViews.add(keyView);

            // Drag vs Tap listener
            keyView.setOnTouchListener(new View.OnTouchListener() {
                float dX, dY;
                float downX, downY;
                float downRawX, downRawY;
                float initialRelWidth, initialRelHeight;
                boolean isDragging = false;
                boolean isResizing = false;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    int cW = keysContainer.getWidth();
                    int cH = keysContainer.getHeight();
                    if (cW <= 0 || cH <= 0) return false;

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            isDragging = false;

                            if (selectedKey == key && keyView.isInResizeHandle(event.getX(), event.getY())) {
                                isResizing = true;
                                initialRelWidth = key.relWidth;
                                initialRelHeight = key.relHeight;
                            } else {
                                isResizing = false;
                                dX = view.getX() - event.getRawX();
                                dY = view.getY() - event.getRawY();
                            }

                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float curX = event.getRawX();
                            float curY = event.getRawY();

                            if (!isDragging && (Math.abs(curX - downX) > 10 || Math.abs(curY - downY) > 10)) {
                                isDragging = true;
                            }

                            // Only allow moving/resizing when this button is currently being edited
                            if (selectedKey != key) {
                                return true;
                            }

                            if (isDragging) {
                                if (isResizing) {
                                    // Top-left anchor remains static. Interpolate width and height delta from touch
                                    float deltaRelW = (curX - downRawX) / (float) cW;
                                    float deltaRelH = (curY - downRawY) / (float) cH;

                                    float targetW = initialRelWidth + deltaRelW;
                                    float targetH = initialRelHeight + deltaRelH;

                                    float[] snappedSize = snapSizeToEdges(key, targetW, targetH, cW, cH);
                                    float snappedW = snappedSize[0];
                                    float snappedH = snappedSize[1];

                                    float maxW = 1.0f - key.relX;
                                    float maxH = 1.0f - key.relY;

                                    if (key.isJoystick || key.lockSquare) {
                                        float px = snappedW * (float) cW;
                                        snappedH = px / (float) cH;
                                        if (snappedH > maxH) {
                                            snappedH = maxH;
                                            snappedW = (snappedH * (float) cH) / (float) cW;
                                        }
                                        if (snappedW > maxW) {
                                            snappedW = maxW;
                                            snappedH = (snappedW * (float) cW) / (float) cH;
                                        }
                                        snappedW = Math.max(MIN_KEY_WIDTH, snappedW);
                                        snappedH = Math.max(MIN_KEY_HEIGHT, snappedH);
                                    } else {
                                        snappedW = Math.max(MIN_KEY_WIDTH, Math.min(maxW, snappedW));
                                        snappedH = Math.max(MIN_KEY_HEIGHT, Math.min(maxH, snappedH));
                                    }

                                    key.relWidth = snappedW;
                                    key.relHeight = snappedH;
                                    setDirty(true);

                                    float curCenterX = key.relX + (snappedW / 2f);
                                    updateSidebarPositionForCenter(curCenterX);

                                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                                    params.width = Math.round(snappedW * cW);
                                    params.height = Math.round(snappedH * cH);
                                    view.setLayoutParams(params);
                                    view.requestLayout();
                                    view.invalidate();

                                    syncSlidersWithSelectedKey();
                                } else {
                                    float newX = curX + dX;
                                    float newY = curY + dY;

                                    float rawRelX = newX / (float) cW;
                                    float rawRelY = newY / (float) cH;

                                    float[] snappedPos = snapPositionToEdges(key, rawRelX, rawRelY, cW, cH);
                                    float snappedRelX = snappedPos[0];
                                    float snappedRelY = snappedPos[1];

                                    key.relX = snappedRelX;
                                    key.relY = snappedRelY;
                                    setDirty(true);

                                    float curCenterX = snappedRelX + (key.relWidth / 2f);
                                    updateSidebarPositionForCenter(curCenterX);

                                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                                    params.leftMargin = Math.round(snappedRelX * cW);
                                    params.topMargin = Math.round(snappedRelY * cH);
                                    view.setLayoutParams(params);
                                    view.requestLayout();

                                    syncSlidersWithSelectedKey();
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            clearAlignmentGuides();
                            if (!isDragging && isInSubLayoutEditMode) {
                                // TAP detected -> Open Button Inspector only while editing a sub-layout
                                showButtonInspector(key, keyView);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
                            clearAlignmentGuides();
                            return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    File dest = VirtualKeyboardProfileManager.getBackgroundImageFile(this, currentLayoutId);
                    FileOutputStream outputStream = new FileOutputStream(dest);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.close();
                    inputStream.close();

                    loadBackground();
                    Toast.makeText(this, R.string.vk_toast_background_updated, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.vk_toast_image_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static class AlignmentGuideOverlayView extends View {
        private final android.graphics.Paint linePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final List<Float> vGuides = new ArrayList<>();
        private final List<Float> hGuides = new ArrayList<>();

        public AlignmentGuideOverlayView(android.content.Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            linePaint.setStyle(android.graphics.Paint.Style.STROKE);
            linePaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, getResources().getDisplayMetrics()));
            linePaint.setColor(VkThemeColors.withAlpha(VkThemeColors.getAccentColor(context), 0xCC));
        }

        public void setGuides(List<Float> verticalRel, List<Float> horizontalRel) {
            vGuides.clear();
            hGuides.clear();
            if (verticalRel != null) vGuides.addAll(verticalRel);
            if (horizontalRel != null) hGuides.addAll(horizontalRel);
            invalidate();
        }

        public void clearGuides() {
            if (!vGuides.isEmpty() || !hGuides.isEmpty()) {
                vGuides.clear();
                hGuides.clear();
                invalidate();
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            for (Float rx : vGuides) {
                float px = rx * w;
                canvas.drawLine(px, 0f, px, h, linePaint);
            }
            for (Float ry : hGuides) {
                float py = ry * h;
                canvas.drawLine(0f, py, w, py, linePaint);
            }
        }
    }
}
