package com.limelight.binding.input.virtual_keyboard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;

import java.util.List;

public class InStreamMenuOverlayView extends FrameLayout {

    private final Game game;
    private final VirtualKeyboardController keyboardController;

    private View scrimView;
    private View sidebarView;
    private TextView labelLayoutOpacity;
    private SeekBar seekbarLayoutOpacity;
    private TextView labelMouseSensitivity;
    private SeekBar seekbarMouseSensitivity;
    private CheckBox checkboxTouchscreenTrackpad;
    private LinearLayout containerMenuLayouts;

    private boolean isMenuOpen = false;

    public InStreamMenuOverlayView(Context context, Game game, VirtualKeyboardController controller) {
        super(context);
        this.game = game;
        this.keyboardController = controller;
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.layout_in_stream_menu, this, true);

        scrimView = findViewById(R.id.stream_menu_scrim);
        sidebarView = findViewById(R.id.stream_menu_sidebar);
        labelLayoutOpacity = findViewById(R.id.label_menu_layout_opacity);
        seekbarLayoutOpacity = findViewById(R.id.seekbar_menu_layout_opacity);
        labelMouseSensitivity = findViewById(R.id.label_menu_mouse_sensitivity);
        seekbarMouseSensitivity = findViewById(R.id.seekbar_menu_mouse_sensitivity);
        checkboxTouchscreenTrackpad = findViewById(R.id.checkbox_menu_touchscreen_trackpad);
        containerMenuLayouts = findViewById(R.id.container_menu_layouts);

        if (seekbarMouseSensitivity != null
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekbarMouseSensitivity.setMin(com.limelight.preferences.PreferenceConfiguration.MOUSE_SENSITIVITY_MIN);
        }

        populateLayoutSelector();

        // Tap outside to close
        scrimView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
            }
        });

        // Header close [ Close ]
        findViewById(R.id.btn_menu_close_x).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
            }
        });

        // Bottom close button
        findViewById(R.id.btn_menu_close_bottom).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
            }
        });

        // Appearance: Layout opacity slider
        if (keyboardController != null && seekbarLayoutOpacity != null) {
            int currentOpacity = keyboardController.getOpacity();
            seekbarLayoutOpacity.setProgress(currentOpacity);
            labelLayoutOpacity.setText(getContext().getString(R.string.vk_layout_opacity_format, currentOpacity));
        }

        if (seekbarLayoutOpacity != null) {
            seekbarLayoutOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = Math.max(10, progress);
                    labelLayoutOpacity.setText(getContext().getString(R.string.vk_layout_opacity_format, val));
                    if (keyboardController != null && fromUser) {
                        keyboardController.setOpacity(val);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Appearance: Mouse Sensitivity slider
        if (game != null && seekbarMouseSensitivity != null) {
            int currentSens = game.getMouseSensitivity();
            seekbarMouseSensitivity.setProgress(currentSens);
            labelMouseSensitivity.setText(getContext().getString(R.string.vk_mouse_sensitivity_format, currentSens));
        }

        if (seekbarMouseSensitivity != null) {
            seekbarMouseSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = Math.max(10, progress);
                    labelMouseSensitivity.setText(getContext().getString(R.string.vk_mouse_sensitivity_format, val));
                    if (game != null && fromUser) {
                        game.setMouseSensitivity(val);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        // Touch input mode: trackpad vs direct touchscreen
        if (checkboxTouchscreenTrackpad != null && game != null) {
            checkboxTouchscreenTrackpad.setChecked(game.isTouchscreenTrackpad());
            checkboxTouchscreenTrackpad.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    // Ignore programmatic refreshes; only react to real user changes so an
                    // in-progress touch is never cancelled unnecessarily.
                    if (game != null && game.isTouchscreenTrackpad() != isChecked) {
                        game.setTouchscreenTrackpad(isChecked);
                    }
                }
            });
        }



        // Group 3: Session Actions
        findViewById(R.id.btn_menu_disconnect).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.vk_disconnect_title)
                        .setMessage(R.string.vk_disconnect_message)
                        .setPositiveButton(R.string.vk_disconnect, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                closeMenu();
                                if (game != null) {
                                    game.disconnectStream();
                                }
                            }
                        })
                        .setNegativeButton(R.string.vk_cancel, null)
                        .show();
            }
        });

        findViewById(R.id.btn_menu_quit_game).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.vk_quit_game)
                        .setMessage(R.string.vk_quit_game_message)
                        .setPositiveButton(R.string.vk_quit_game, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                closeMenu();
                                if (game != null) {
                                    game.quitGameAndDisconnect();
                                }
                            }
                        })
                        .setNegativeButton(R.string.vk_cancel, null)
                        .show();
            }
        });

        setVisibility(View.GONE);
    }

    public void openMenu() {
        isMenuOpen = true;
        if (keyboardController != null && seekbarLayoutOpacity != null) {
            int currentOpacity = keyboardController.getOpacity();
            seekbarLayoutOpacity.setProgress(currentOpacity);
            labelLayoutOpacity.setText(getContext().getString(R.string.vk_layout_opacity_format, currentOpacity));
        }
        if (game != null && seekbarMouseSensitivity != null) {
            int currentSens = game.getMouseSensitivity();
            seekbarMouseSensitivity.setProgress(currentSens);
            labelMouseSensitivity.setText(getContext().getString(R.string.vk_mouse_sensitivity_format, currentSens));
        }
        if (checkboxTouchscreenTrackpad != null && game != null) {
            checkboxTouchscreenTrackpad.setChecked(game.isTouchscreenTrackpad());
        }


        populateLayoutSelector();

        // Direct instant open with zero animation lag
        sidebarView.setTranslationX(0f);
        scrimView.setAlpha(1f);
        setVisibility(View.VISIBLE);
        bringToFront();
    }

    public void closeMenu() {
        if (!isMenuOpen) {
            setVisibility(View.GONE);
            return;
        }
        isMenuOpen = false;

        // Direct instant close with zero animation lag
        float defaultW = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 310f, getResources().getDisplayMetrics());
        float targetW = sidebarView.getWidth() > 0 ? sidebarView.getWidth() : defaultW;
        sidebarView.setTranslationX(targetW);
        scrimView.setAlpha(0f);
        setVisibility(View.GONE);
    }

    public void toggleMenu() {
        if (isMenuOpen && getVisibility() == View.VISIBLE) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    public boolean isMenuOpen() {
        return isMenuOpen && getVisibility() == View.VISIBLE;
    }

    private void populateLayoutSelector() {
        if (containerMenuLayouts == null) return;
        containerMenuLayouts.removeAllViews();

        List<VirtualKeyboardProfileManager.LayoutProfileInfo> profiles =
                VirtualKeyboardProfileManager.getProfiles(getContext());
        String activeId = VirtualKeyboardProfileManager.getActiveProfileId(getContext());

        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 9, getResources().getDisplayMetrics());
        int marginB = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        int radius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        int strokeW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, getResources().getDisplayMetrics());

        for (final VirtualKeyboardProfileManager.LayoutProfileInfo profile : profiles) {
            final boolean isActive = profile.id.equals(activeId);

            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginB;
            item.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(radius);
            if (isActive) {
                bg.setColor(0xFF143A4D);
                bg.setStroke(strokeW, 0xFF00B4D8);
            } else {
                bg.setColor(0xFF1F2228);
                bg.setStroke(strokeW, 0xFF2D323C);
            }
            item.setBackground(bg);

            TextView txtName = new TextView(getContext());
            txtName.setText(profile.name);
            txtName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            txtName.setTextColor(isActive ? 0xFFFFFFFF : 0xFFCCCCCC);
            txtName.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            item.addView(txtName, nameLp);

            TextView txtCount = new TextView(getContext());
            txtCount.setText(getContext().getString(R.string.vk_keys_count_format, profile.keyCount));
            txtCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            txtCount.setTextColor(0xFF777777);
            txtCount.setPadding(0, 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()), 0);
            item.addView(txtCount);

            if (isActive) {
                TextView txtCheck = new TextView(getContext());
                txtCheck.setText("✔");
                txtCheck.setTextColor(0xFF00B4D8);
                txtCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                txtCheck.setTypeface(null, Typeface.BOLD);
                item.addView(txtCheck);
            }

            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!profile.id.equals(VirtualKeyboardProfileManager.getActiveProfileId(getContext()))) {
                        VirtualKeyboardProfileManager.setActiveProfileId(getContext(), profile.id);
                        populateLayoutSelector();
                        if (keyboardController != null) {
                            keyboardController.refreshLayout();
                        }
                    }
                }
            });

            containerMenuLayouts.addView(item);
        }
    }
}
