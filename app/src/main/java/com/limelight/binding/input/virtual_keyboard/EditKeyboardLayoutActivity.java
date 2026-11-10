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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
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
    private static final float GRID_BASE_DP = 3f;

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float getSnapStepX() {
        int cW = keysContainer != null ? keysContainer.getWidth() : 0;
        if (cW <= 0) return 0.005f;
        return dpToPx(GRID_BASE_DP) / (float) cW;
    }

    private float getSnapStepY() {
        int cH = keysContainer != null ? keysContainer.getHeight() : 0;
        if (cH <= 0) return 0.005f;
        return dpToPx(GRID_BASE_DP) / (float) cH;
    }

    private static final float MIN_KEY_WIDTH = 0.02f;
    private static final float MIN_KEY_HEIGHT = 0.03f;

    private View rootLayout;
    private ImageView bgImageView;
    private FrameLayout keysContainer;
    private View sidebar;
    private View btnOpenMenu;
    private String currentLayoutId;
    private int cutoutInsetLeft = 0;
    private int cutoutInsetRight = 0;

    private View panelGlobalMenu;
    private View panelButtonInspector;

    // Inspector widgets
    private TextView inspectorTitle;
    private EditText editKeyLabel;
    private Button btnChangeKeycode;
    private View layoutComboKeys;
    private Button btnChangeExtraKey1;
    private Button btnChangeExtraKey2;
    private Button btnChangeExtraKey3;
    private SeekBar seekbarKeyWidth;
    private SeekBar seekbarKeyHeight;
    private SeekBar seekbarKeyRadius;
    private SeekBar seekbarKeyBgOpacity;
    private SeekBar seekbarKeyTextOpacity;
    private CheckBox checkboxKeyBorder;
    private CheckBox checkboxKeySlide;
    private CheckBox checkboxKeyHoldOutside;
    private CheckBox checkboxKeyAbsorbTouch;
    private CheckBox checkboxKeyBlockTouch;
    private CheckBox checkboxKeySwitchInput;
    private View layoutContainerBorder;
    private View layoutContainerSlide;
    private View layoutContainerHoldOutside;
    private View layoutContainerAbsorbTouch;
    private View layoutContainerBlockTouch;
    private View layoutContainerSwitchInput;
    private View layoutKeyLabelContainer;

    private View layoutControllerMapping;
    private Button btnKeyUp;
    private Button btnKeyDown;
    private Button btnKeyLeft;
    private Button btnKeyRight;

    private TextView labelKeyWidth;
    private TextView labelKeyHeight;
    private TextView labelKeyRadius;
    private TextView labelKeyBgOpacity;
    private TextView labelKeyTextOpacity;
    private SeekBar seekbarKeyPosX;
    private SeekBar seekbarKeyPosY;
    private TextView labelKeyPosX;
    private TextView labelKeyPosY;

    private List<VirtualKey> currentKeys = new ArrayList<>();
    private final List<VirtualKeyView> currentKeyViews = new ArrayList<>();
    private VirtualKey selectedKey = null;
    private VirtualKeyView selectedKeyView = null;
    private Button btnExitEditor = null;
    private boolean isDirty = false;
    private boolean isBindingUI = false;

    private static class KeyOption {
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
        sidebar = findViewById(R.id.editor_sidebar);
        btnOpenMenu = findViewById(R.id.btn_open_menu);

        panelGlobalMenu = findViewById(R.id.panel_global_menu);
        panelButtonInspector = findViewById(R.id.panel_button_inspector);

        inspectorTitle = findViewById(R.id.inspector_title);
        editKeyLabel = findViewById(R.id.edit_key_label);
        btnChangeKeycode = findViewById(R.id.btn_change_keycode);
        layoutComboKeys = findViewById(R.id.layout_combo_keys);
        btnChangeExtraKey1 = findViewById(R.id.btn_change_extra_key1);
        btnChangeExtraKey2 = findViewById(R.id.btn_change_extra_key2);
        btnChangeExtraKey3 = findViewById(R.id.btn_change_extra_key3);
        seekbarKeyWidth = findViewById(R.id.seekbar_key_width);
        seekbarKeyHeight = findViewById(R.id.seekbar_key_height);
        seekbarKeyRadius = findViewById(R.id.seekbar_key_radius);
        seekbarKeyBgOpacity = findViewById(R.id.seekbar_key_bg_opacity);
        seekbarKeyTextOpacity = findViewById(R.id.seekbar_key_text_opacity);
        checkboxKeyBorder = findViewById(R.id.checkbox_key_border);
        checkboxKeySlide = findViewById(R.id.checkbox_key_slide);
        checkboxKeyHoldOutside = findViewById(R.id.checkbox_key_hold_outside);
        checkboxKeyAbsorbTouch = findViewById(R.id.checkbox_key_absorb_touch);
        checkboxKeyBlockTouch = findViewById(R.id.checkbox_key_block_touch);
        checkboxKeySwitchInput = findViewById(R.id.checkbox_key_switch_input);
        layoutContainerBorder = findViewById(R.id.layout_container_border);
        layoutContainerSlide = findViewById(R.id.layout_container_slide);
        layoutContainerHoldOutside = findViewById(R.id.layout_container_hold_outside);
        layoutContainerAbsorbTouch = findViewById(R.id.layout_container_absorb_touch);
        layoutContainerBlockTouch = findViewById(R.id.layout_container_block_touch);
        layoutContainerSwitchInput = findViewById(R.id.layout_container_switch_input);
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

        currentKeys = VirtualKeyboardProfileManager.loadProfile(this, currentLayoutId);
        keysContainer.post(new Runnable() {
            @Override
            public void run() {
                rebuildKeyViews();
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
        availableKeyOptions.add(new KeyOption(getString(R.string.vk_binding_action_toggle_stream_menu), VirtualKey.KeyType.ACTION, VirtualKey.ACTION_CODE_TOGGLE_STREAM_MENU));

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

        findViewById(R.id.btn_close_sidebar_global).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
            }
        });

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
        SeekBar seekbarBgOpacity = findViewById(R.id.seekbar_bg_opacity);
        int currentBgOpacity = VirtualKeyboardProfileManager.getBackgroundOpacity(this, currentLayoutId);
        seekbarBgOpacity.setProgress(currentBgOpacity);
        textBgOpacity.setText(getString(R.string.vk_background_opacity_format, currentBgOpacity));

        seekbarBgOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(10, progress);
                textBgOpacity.setText(getString(R.string.vk_background_opacity_format, val));
                bgImageView.setAlpha(val / 100f);
                if (fromUser) {
                    VirtualKeyboardProfileManager.saveBackgroundOpacity(EditKeyboardLayoutActivity.this, currentLayoutId, val);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btn_save_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VirtualKeyboardProfileManager.saveProfile(EditKeyboardLayoutActivity.this, currentLayoutId, currentKeys);
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
                                currentKeys = VirtualKeyboardProfileManager.loadProfile(EditKeyboardLayoutActivity.this, currentLayoutId);
                                rebuildKeyViews();
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
                            VirtualKeyboardProfileManager.saveProfile(EditKeyboardLayoutActivity.this, currentLayoutId, currentKeys);
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
                closeSidebar();
            }
        });

        findViewById(R.id.btn_done_inspector).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSidebar();
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

        if (btnChangeExtraKey1 != null) {
            btnChangeExtraKey1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showExtraKeySelectorDialog(1);
                }
            });
        }
        if (btnChangeExtraKey2 != null) {
            btnChangeExtraKey2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showExtraKeySelectorDialog(2);
                }
            });
        }
        if (btnChangeExtraKey3 != null) {
            btnChangeExtraKey3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showExtraKeySelectorDialog(3);
                }
            });
        }

        seekbarKeyPosX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepX = getSnapStepX();
                float maxAllowedX = 1.0f - selectedKey.relWidth;
                float newX = progress * snapStepX;
                newX = Math.round(newX / snapStepX) * snapStepX;
                newX = Math.max(0f, Math.min(maxAllowedX, newX));
                selectedKey.relX = newX;
                labelKeyPosX.setText(formatPercent(R.string.vk_label_position_x, newX));
                updateSelectedKeyLayout();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarKeyPosY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepY = getSnapStepY();
                float maxAllowedY = 1.0f - selectedKey.relHeight;
                float newY = progress * snapStepY;
                newY = Math.round(newY / snapStepY) * snapStepY;
                newY = Math.max(0f, Math.min(maxAllowedY, newY));
                selectedKey.relY = newY;
                labelKeyPosY.setText(formatPercent(R.string.vk_label_position_y, newY));
                updateSelectedKeyLayout();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Width / Size slider: interpolates from top-left anchor to right edge by grid steps
        seekbarKeyWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepX = getSnapStepX();
                float maxAllowed = 1.0f - selectedKey.relX;
                float newWidth = MIN_KEY_WIDTH + (progress * snapStepX);
                newWidth = Math.min(newWidth, maxAllowed);

                // Snap to grid
                newWidth = Math.round(newWidth / snapStepX) * snapStepX;

                if (selectedKey.isJoystick) {
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
                    labelKeyWidth.setText(formatPercent(R.string.vk_label_size, newWidth));
                } else {
                    selectedKey.relWidth = newWidth;
                    labelKeyWidth.setText(formatPercent(R.string.vk_label_width, newWidth));
                }

                updateSelectedKeyLayout();
                syncPositionSlidersWithSelectedKey();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Height slider: interpolates from top-left anchor to bottom edge by grid steps
        seekbarKeyHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                float snapStepY = getSnapStepY();
                float maxAllowed = 1.0f - selectedKey.relY;
                float newHeight = MIN_KEY_HEIGHT + (progress * snapStepY);
                newHeight = Math.min(newHeight, maxAllowed);

                // Snap to grid
                newHeight = Math.round(newHeight / snapStepY) * snapStepY;
                selectedKey.relHeight = newHeight;

                if (selectedKey.isJoystick) {
                    int cW = keysContainer.getWidth();
                    int cH = keysContainer.getHeight();
                    if (cW > 0 && cH > 0) {
                        float py = newHeight * (float) cH;
                        float newW = py / (float) cW;
                        selectedKey.relWidth = Math.min(newW, 1.0f - selectedKey.relX);
                        labelKeyWidth.setText(formatPercent(R.string.vk_label_width, selectedKey.relWidth));
                    }
                }

                labelKeyHeight.setText(formatPercent(R.string.vk_label_height, newHeight));
                updateSelectedKeyLayout();
                syncPositionSlidersWithSelectedKey();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarKeyRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                selectedKey.cornerRadius = progress;
                labelKeyRadius.setText(getString(R.string.vk_corner_radius_format, progress));
                selectedKeyView.invalidate();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarKeyBgOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                selectedKey.bgOpacity = progress;
                labelKeyBgOpacity.setText(getString(R.string.vk_background_opacity_format, progress));
                selectedKeyView.invalidate();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarKeyTextOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || selectedKey == null || selectedKeyView == null) return;
                int val = Math.max(0, progress);
                selectedKey.textOpacity = val;
                labelKeyTextOpacity.setText(getString(R.string.vk_text_opacity_format, val));
                selectedKeyView.invalidate();
                setDirty(true);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        checkboxKeyBorder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.showBorder = isChecked;
                    selectedKeyView.invalidate();
                    if (!isBindingUI) setDirty(true);
                }
            }
        });

        checkboxKeySlide.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.isSlide = isChecked;
                    selectedKeyView.invalidate();
                    if (!isBindingUI) setDirty(true);
                }
            }
        });

        if (checkboxKeyHoldOutside != null) {
            checkboxKeyHoldOutside.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.holdOutside = isChecked;
                        selectedKeyView.invalidate();
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        if (checkboxKeyAbsorbTouch != null) {
            checkboxKeyAbsorbTouch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.absorbTouch = isChecked;
                        selectedKeyView.invalidate();
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        if (checkboxKeySwitchInput != null) {
            checkboxKeySwitchInput.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (selectedKey != null && selectedKeyView != null) {
                        selectedKey.switchInputModeOnToggle = isChecked;
                        if (!isBindingUI) setDirty(true);
                    }
                }
            });
        }

        checkboxKeyBlockTouch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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

    private void updateInspectorUIForKey(VirtualKey key) {
        if (key == null) return;
        View labelKeyMapping = findViewById(R.id.label_key_mapping);

        if (key.isJoystick) {
            layoutControllerMapping.setVisibility(View.VISIBLE);
            btnKeyUp.setText(getString(R.string.vk_dir_up_format, getSingleKeyName(key.codeUp)));
            btnKeyDown.setText(getString(R.string.vk_dir_down_format, getSingleKeyName(key.codeDown)));
            btnKeyLeft.setText(getString(R.string.vk_dir_left_format, getSingleKeyName(key.codeLeft)));
            btnKeyRight.setText(getString(R.string.vk_dir_right_format, getSingleKeyName(key.codeRight)));

            layoutKeyLabelContainer.setVisibility(View.VISIBLE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutComboKeys != null) layoutComboKeys.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerBorder != null) layoutContainerBorder.setVisibility(View.VISIBLE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.GONE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.GONE);
            labelKeyRadius.setVisibility(View.GONE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPercent(R.string.vk_label_size, key.relWidth));
            seekbarKeyHeight.setVisibility(View.GONE);
            labelKeyHeight.setVisibility(View.GONE);
        } else if (key.isScroll) {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.VISIBLE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutComboKeys != null) layoutComboKeys.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerBorder != null) layoutContainerBorder.setVisibility(View.VISIBLE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.GONE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPercent(R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setText(formatPercent(R.string.vk_label_height, key.relHeight));
        } else if (key.isBlockTouch) {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.GONE);
            btnChangeKeycode.setVisibility(View.GONE);
            if (layoutComboKeys != null) layoutComboKeys.setVisibility(View.GONE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.GONE);
            if (layoutContainerBorder != null) layoutContainerBorder.setVisibility(View.VISIBLE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(View.GONE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(View.GONE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(View.GONE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.VISIBLE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(View.GONE);
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPercent(R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setText(formatPercent(R.string.vk_label_height, key.relHeight));
        } else {
            layoutControllerMapping.setVisibility(View.GONE);
            layoutKeyLabelContainer.setVisibility(View.VISIBLE);
            btnChangeKeycode.setVisibility(View.VISIBLE);
            if (labelKeyMapping != null) labelKeyMapping.setVisibility(View.VISIBLE);
            if (key.type == VirtualKey.KeyType.KEYBOARD) {
                if (layoutComboKeys != null) layoutComboKeys.setVisibility(View.VISIBLE);
                if (btnChangeExtraKey1 != null) {
                    btnChangeExtraKey1.setText(getString(R.string.vk_combo_key_format, 2, key.extraCode1 == 0 ? getString(R.string.vk_label_combo_none) : getSingleKeyName(key.extraCode1)));
                }
                if (btnChangeExtraKey2 != null) {
                    btnChangeExtraKey2.setText(getString(R.string.vk_combo_key_format, 3, key.extraCode2 == 0 ? getString(R.string.vk_label_combo_none) : getSingleKeyName(key.extraCode2)));
                }
                if (btnChangeExtraKey3 != null) {
                    btnChangeExtraKey3.setText(getString(R.string.vk_combo_key_format, 4, key.extraCode3 == 0 ? getString(R.string.vk_label_combo_none) : getSingleKeyName(key.extraCode3)));
                }
            } else {
                if (layoutComboKeys != null) layoutComboKeys.setVisibility(View.GONE);
            }
            boolean isAction = (key.type == VirtualKey.KeyType.ACTION);
            if (isAction) {
                key.isSlide = false;
                key.holdOutside = false;
                key.absorbTouch = false;
                if (checkboxKeySlide != null) checkboxKeySlide.setChecked(false);
                if (checkboxKeyHoldOutside != null) checkboxKeyHoldOutside.setChecked(false);
                if (checkboxKeyAbsorbTouch != null) checkboxKeyAbsorbTouch.setChecked(false);
            }
            if (layoutContainerBorder != null) layoutContainerBorder.setVisibility(View.VISIBLE);
            if (layoutContainerSlide != null) layoutContainerSlide.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerHoldOutside != null) layoutContainerHoldOutside.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerAbsorbTouch != null) layoutContainerAbsorbTouch.setVisibility(isAction ? View.GONE : View.VISIBLE);
            if (layoutContainerBlockTouch != null) layoutContainerBlockTouch.setVisibility(View.VISIBLE);
            if (layoutContainerSwitchInput != null) layoutContainerSwitchInput.setVisibility(isToggleOverlayKey(key) ? View.VISIBLE : View.GONE);
            seekbarKeyRadius.setVisibility(View.VISIBLE);
            labelKeyRadius.setVisibility(View.VISIBLE);
            seekbarKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setVisibility(View.VISIBLE);
            labelKeyWidth.setText(formatPercent(R.string.vk_label_width, key.relWidth));
            seekbarKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setVisibility(View.VISIBLE);
            labelKeyHeight.setText(formatPercent(R.string.vk_label_height, key.relHeight));
        }
    }

    private void showExtraKeySelectorDialog(final int slot) {
        if (selectedKey == null || selectedKeyView == null) return;

        final List<KeyOption> validOptions = new ArrayList<>();
        validOptions.add(new KeyOption(getString(R.string.vk_label_none_option), VirtualKey.KeyType.KEYBOARD, 0));
        for (KeyOption opt : availableKeyOptions) {
            if (opt.type == VirtualKey.KeyType.KEYBOARD) {
                validOptions.add(opt);
            }
        }

        String[] names = new String[validOptions.size()];
        for (int i = 0; i < validOptions.size(); i++) {
            names[i] = validOptions.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.vk_map_combo_key_title, slot + 1));
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedKey == null || selectedKeyView == null) return;
                KeyOption chosen = validOptions.get(which);
                if (slot == 1) {
                    selectedKey.extraCode1 = chosen.code;
                    if (btnChangeExtraKey1 != null) {
                        btnChangeExtraKey1.setText(getString(R.string.vk_combo_key_format, 2, chosen.code == 0 ? getString(R.string.vk_label_combo_none) : chosen.name));
                    }
                } else if (slot == 2) {
                    selectedKey.extraCode2 = chosen.code;
                    if (btnChangeExtraKey2 != null) {
                        btnChangeExtraKey2.setText(getString(R.string.vk_combo_key_format, 3, chosen.code == 0 ? getString(R.string.vk_label_combo_none) : chosen.name));
                    }
                } else if (slot == 3) {
                    selectedKey.extraCode3 = chosen.code;
                    if (btnChangeExtraKey3 != null) {
                        btnChangeExtraKey3.setText(getString(R.string.vk_combo_key_format, 4, chosen.code == 0 ? getString(R.string.vk_label_combo_none) : chosen.name));
                    }
                }
                setDirty(true);
            }
        });
        builder.show();
    }

    private void showDirectionKeySelectorDialog(final String directionName, final int dirIndex) {
        if (selectedKey == null || selectedKeyView == null || !selectedKey.isJoystick) return;

        String[] names = new String[availableKeyOptions.size()];
        for (int i = 0; i < availableKeyOptions.size(); i++) {
            names[i] = availableKeyOptions.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.vk_map_key_title, directionName));
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selectedKey == null || selectedKeyView == null) return;
                KeyOption chosen = availableKeyOptions.get(which);
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
        builder.show();
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

    private void showGlobalMenu() {
        deselectCurrentKey();
        applySidebarPosition(false);
        panelGlobalMenu.setVisibility(View.VISIBLE);
        panelButtonInspector.setVisibility(View.GONE);
        sidebar.setVisibility(View.VISIBLE);
        btnOpenMenu.setVisibility(View.GONE);
    }

    private String formatPercent(int labelResId, float relVal) {
        String label = getString(labelResId);
        float pct = relVal * 100f;
        if (Math.abs(pct - Math.round(pct)) < 0.05f) {
            return getString(R.string.vk_percent_int_format, label, Math.round(pct));
        } else {
            return getString(R.string.vk_percent_float_format, label, pct);
        }
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

        float snapStepX = getSnapStepX();
        float snapStepY = getSnapStepY();

        float maxAllowedX = 1.0f - selectedKey.relWidth;
        int maxStepsX = Math.max(1, Math.round(maxAllowedX / snapStepX));
        seekbarKeyPosX.setMax(maxStepsX);
        int currentStepX = Math.round(selectedKey.relX / snapStepX);
        seekbarKeyPosX.setProgress(Math.max(0, Math.min(maxStepsX, currentStepX)));
        labelKeyPosX.setText(formatPercent(R.string.vk_label_position_x, selectedKey.relX));

        float maxAllowedY = 1.0f - selectedKey.relHeight;
        int maxStepsY = Math.max(1, Math.round(maxAllowedY / snapStepY));
        seekbarKeyPosY.setMax(maxStepsY);
        int currentStepY = Math.round(selectedKey.relY / snapStepY);
        seekbarKeyPosY.setProgress(Math.max(0, Math.min(maxStepsY, currentStepY)));
        labelKeyPosY.setText(formatPercent(R.string.vk_label_position_y, selectedKey.relY));
    }

    private void syncSlidersWithSelectedKey() {
        if (selectedKey == null || selectedKeyView == null) return;

        float snapStepX = getSnapStepX();
        float snapStepY = getSnapStepY();

        // Configure position X and Y sliders
        syncPositionSlidersWithSelectedKey();

        // Configure width/size slider according to top-left anchor delta to right screen edge
        float maxAllowedW = 1.0f - selectedKey.relX;
        int maxStepsW = Math.max(1, Math.round((maxAllowedW - MIN_KEY_WIDTH) / snapStepX));
        seekbarKeyWidth.setMax(maxStepsW);
        int currentStepW = Math.round((selectedKey.relWidth - MIN_KEY_WIDTH) / snapStepX);
        seekbarKeyWidth.setProgress(Math.max(0, Math.min(maxStepsW, currentStepW)));

        if (selectedKey.isJoystick) {
            labelKeyWidth.setText(formatPercent(R.string.vk_label_size, selectedKey.relWidth));
        } else {
            labelKeyWidth.setText(formatPercent(R.string.vk_label_width, selectedKey.relWidth));

            // Configure height slider according to top-left anchor delta to bottom screen edge
            float maxAllowedH = 1.0f - selectedKey.relY;
            int maxStepsH = Math.max(1, Math.round((maxAllowedH - MIN_KEY_HEIGHT) / snapStepY));
            seekbarKeyHeight.setMax(maxStepsH);
            int currentStepH = Math.round((selectedKey.relHeight - MIN_KEY_HEIGHT) / snapStepY);
            seekbarKeyHeight.setProgress(Math.max(0, Math.min(maxStepsH, currentStepH)));
            labelKeyHeight.setText(formatPercent(R.string.vk_label_height, selectedKey.relHeight));
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
        btnChangeKeycode.setText(getString(R.string.vk_key_main_format, getKeyDisplayName(key)));

        // Sync width and height sliders with top-left anchor grid deltas
        syncSlidersWithSelectedKey();

        seekbarKeyRadius.setProgress(Math.round(key.cornerRadius));
        labelKeyRadius.setText(getString(R.string.vk_corner_radius_format, Math.round(key.cornerRadius)));

        seekbarKeyBgOpacity.setProgress(key.bgOpacity);
        labelKeyBgOpacity.setText(getString(R.string.vk_background_opacity_format, key.bgOpacity));

        seekbarKeyTextOpacity.setProgress(key.textOpacity);
        labelKeyTextOpacity.setText(getString(R.string.vk_text_opacity_format, key.textOpacity));

        checkboxKeyBorder.setChecked(key.showBorder);
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
        updateInspectorUIForKey(key);

        isBindingUI = false;

        panelGlobalMenu.setVisibility(View.GONE);
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

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectedKeyView.getLayoutParams();
        lp.width = Math.round(selectedKey.relWidth * cW);
        lp.height = Math.round(selectedKey.relHeight * cH);
        lp.leftMargin = Math.round(selectedKey.relX * cW);
        lp.topMargin = Math.round(selectedKey.relY * cH);
        selectedKeyView.setLayoutParams(lp);
        selectedKeyView.requestLayout();
        selectedKeyView.invalidate();
    }

    private void addNewKey() {
        String id = "key_" + System.currentTimeMillis();
        VirtualKey newKey = new VirtualKey(id, "NEW", VirtualKey.KeyType.KEYBOARD, KeyEvent.KEYCODE_SPACE,
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
        if (selectedKey.isJoystick) {
            prefix = "controller_";
        } else if (selectedKey.isScroll) {
            prefix = "scroll_";
        }
        String newId = prefix + System.currentTimeMillis();
        float snapStepX = getSnapStepX();
        float snapStepY = getSnapStepY();
        float newX = Math.min(1.0f - selectedKey.relWidth, selectedKey.relX + snapStepX * 3);
        float newY = Math.min(1.0f - selectedKey.relHeight, selectedKey.relY + snapStepY * 3);

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
            if (key.code == VirtualKey.ACTION_CODE_TOGGLE_STREAM_MENU) {
                return getString(R.string.vk_binding_toggle_stream_menu);
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
        return KeyEvent.keyCodeToString(key.code).replace("KEYCODE_", "");
    }

    private void showKeySelectorDialog() {
        String[] names = new String[availableKeyOptions.size()];
        for (int i = 0; i < availableKeyOptions.size(); i++) {
            names[i] = availableKeyOptions.get(i).name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.vk_select_key_mapping);
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                KeyOption opt = availableKeyOptions.get(which);
                if (selectedKey != null && selectedKeyView != null) {
                    selectedKey.type = opt.type;
                    selectedKey.code = opt.code;
                    if (opt.type == VirtualKey.KeyType.ACTION) {
                        selectedKey.isSlide = false;
                    }
                    if ("NEW".equals(selectedKey.label)) {
                        if (opt.type == VirtualKey.KeyType.ACTION) {
                            if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_VISIBILITY) selectedKey.label = "Toggle";
                            else if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_PC_KEYBOARD) selectedKey.label = "KB";
                            else if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_MOUSE) selectedKey.label = "Mouse";
                            else if (opt.code == VirtualKey.ACTION_CODE_SHOW_ANDROID_KEYBOARD) selectedKey.label = "IME";
                            else if (opt.code == VirtualKey.ACTION_CODE_SCROLL_UP) selectedKey.label = "SC.UP";
                            else if (opt.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) selectedKey.label = "SC.DN";
                            else if (opt.code == VirtualKey.ACTION_CODE_TOGGLE_STREAM_MENU) selectedKey.label = "MENU";
                        } else if (opt.type == VirtualKey.KeyType.KEYBOARD && opt.name.length() <= 3) {
                            selectedKey.label = opt.name;
                        }
                        editKeyLabel.setText(selectedKey.label);
                    }
                    btnChangeKeycode.setText(getString(R.string.vk_key_format, getKeyDisplayName(selectedKey)));
                    updateInspectorUIForKey(selectedKey);
                    selectedKeyView.invalidate();
                    setDirty(true);
                }
            }
        });
        builder.show();
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
                                // If sidebar inspector is already open and user drags a different key, switch inspector to it
                                if (!isResizing && selectedKey != key && panelButtonInspector.getVisibility() == View.VISIBLE) {
                                    showButtonInspector(key, keyView);
                                }
                            }

                            if (isDragging) {
                                float snapStepX = getSnapStepX();
                                float snapStepY = getSnapStepY();

                                if (isResizing) {
                                    // Top-left anchor remains static. Interpolate width and height delta from touch
                                    float deltaRelW = (curX - downRawX) / (float) cW;
                                    float deltaRelH = (curY - downRawY) / (float) cH;

                                    float targetW = initialRelWidth + deltaRelW;
                                    float targetH = initialRelHeight + deltaRelH;

                                    float snappedW = Math.round(targetW / snapStepX) * snapStepX;
                                    float snappedH = Math.round(targetH / snapStepY) * snapStepY;

                                    float maxW = 1.0f - key.relX;
                                    float maxH = 1.0f - key.relY;

                                    if (key.isJoystick) {
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

                                    if (selectedKey == key) {
                                        syncSlidersWithSelectedKey();
                                    }
                                } else {
                                    float newX = curX + dX;
                                    float newY = curY + dY;

                                    float rawRelX = newX / (float) cW;
                                    float rawRelY = newY / (float) cH;

                                    float snappedRelX = Math.round(rawRelX / snapStepX) * snapStepX;
                                    float snappedRelY = Math.round(rawRelY / snapStepY) * snapStepY;

                                    snappedRelX = Math.max(0f, Math.min(1f - key.relWidth, snappedRelX));
                                    snappedRelY = Math.max(0f, Math.min(1f - key.relHeight, snappedRelY));

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

                                    if (selectedKey == key) {
                                        syncSlidersWithSelectedKey();
                                    }
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                // TAP detected -> Open Button Inspector!
                                showButtonInspector(key, keyView);
                            }
                            return true;

                        case MotionEvent.ACTION_CANCEL:
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
}
