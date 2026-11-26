package com.limelight.binding.input.virtual_keyboard;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact, minimalist dark custom UI keymapping picker with category tabs, multi-column grids,
 * live search, and optional 1-4 key combo selection.
 */
public class VkKeyPickerDialog {

    public interface OnKeySelectedListener {
        void onKeySelected(EditKeyboardLayoutActivity.KeyOption option);
    }

    public interface OnComboSelectedListener {
        void onComboSelected(List<EditKeyboardLayoutActivity.KeyOption> selectedKeys);
    }

    private enum Tab {
        KEYS, MOUSE, ACTIONS
    }

    public static void show(
            Context context,
            String title,
            List<EditKeyboardLayoutActivity.KeyOption> allOptions,
            VirtualKey.KeyType currentType,
            int currentCode,
            boolean keyboardOnly,
            boolean allowNone,
            final OnKeySelectedListener listener) {

        showInternal(context, title, allOptions, currentType, currentCode, null,
                keyboardOnly, allowNone, false, listener, null);
    }

    public static void showWithCombo(
            Context context,
            String title,
            List<EditKeyboardLayoutActivity.KeyOption> allOptions,
            VirtualKey.KeyType currentType,
            int[] currentCodes,
            final OnKeySelectedListener singleListener,
            final OnComboSelectedListener comboListener) {
        showWithCombo(context, title, allOptions, currentType, currentCodes, false, singleListener, comboListener);
    }

    public static void showWithCombo(
            Context context,
            String title,
            List<EditKeyboardLayoutActivity.KeyOption> allOptions,
            VirtualKey.KeyType currentType,
            int[] currentCodes,
            boolean allowNone,
            final OnKeySelectedListener singleListener,
            final OnComboSelectedListener comboListener) {

        List<EditKeyboardLayoutActivity.KeyOption> initialCombo = new ArrayList<>();
        if (currentType == VirtualKey.KeyType.KEYBOARD && currentCodes != null) {
            for (int code : currentCodes) {
                if (code == 0) continue;
                EditKeyboardLayoutActivity.KeyOption found = findKeyboardOption(allOptions, code);
                if (found != null) {
                    initialCombo.add(found);
                }
            }
        }
        int primaryCode = (currentCodes != null && currentCodes.length > 0) ? currentCodes[0] : 0;
        showInternal(context, title, allOptions, currentType, primaryCode, initialCombo,
                false, allowNone, true, singleListener, comboListener);
    }

    private static EditKeyboardLayoutActivity.KeyOption findKeyboardOption(
            List<EditKeyboardLayoutActivity.KeyOption> allOptions, int code) {
        for (EditKeyboardLayoutActivity.KeyOption opt : allOptions) {
            if (opt.type == VirtualKey.KeyType.KEYBOARD && opt.code == code) {
                return opt;
            }
        }
        return null;
    }

    private static void showInternal(
            final Context context,
            String title,
            final List<EditKeyboardLayoutActivity.KeyOption> allOptions,
            final VirtualKey.KeyType currentType,
            final int currentCode,
            List<EditKeyboardLayoutActivity.KeyOption> initialCombo,
            final boolean keyboardOnly,
            final boolean allowNone,
            final boolean allowCombo,
            final OnKeySelectedListener singleListener,
            final OnComboSelectedListener comboListener) {

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int accentColor = VkThemeColors.getAccentColor(context);
        final int accentPressed = VkThemeColors.getAccentPressedColor(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 12);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(0xFF111419);
        rootBg.setCornerRadius(dp(context, 8));
        rootBg.setStroke(dp(context, 1), 0xFF232933);
        root.setBackground(rootBg);

        // Header Row: Title + Search + Close X
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(context, 8);
        header.setLayoutParams(headerLp);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(0xFFE6EDF3);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.rightMargin = dp(context, 8);
        header.addView(titleView, titleLp);

        final EditText searchInput = new EditText(context);
        searchInput.setHint("Search...");
        searchInput.setHintTextColor(0xFF6E7681);
        searchInput.setTextColor(0xFFE6EDF3);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        searchInput.setSingleLine(true);
        searchInput.setBackground(VkThemeColors.createInputBackgroundDrawable(context));
        int hPad = dp(context, 8);
        searchInput.setPadding(hPad, 0, hPad, 0);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                dp(context, 140), dp(context, 30));
        searchLp.rightMargin = dp(context, 8);
        header.addView(searchInput, searchLp);

        ImageView btnClose = new ImageView(context);
        btnClose.setImageResource(R.drawable.ic_vk_close);
        btnClose.setBackgroundResource(R.drawable.vk_btn_secondary);
        int closePad = dp(context, 5);
        btnClose.setPadding(closePad, closePad, closePad, closePad);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        header.addView(btnClose, new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        root.addView(header);

        // Tab Bar
        final LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabBarLp.bottomMargin = dp(context, 8);
        tabBar.setLayoutParams(tabBarLp);
        root.addView(tabBar);

        // Combo Selection Bar (visible only in Combo mode)
        final LinearLayout comboBar = new LinearLayout(context);
        comboBar.setOrientation(LinearLayout.HORIZONTAL);
        comboBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams comboBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        comboBarLp.bottomMargin = dp(context, 8);
        comboBar.setLayoutParams(comboBarLp);
        root.addView(comboBar);

        // Scrollable Grid Content
        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollLp);

        final LinearLayout gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(gridContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView);

        final List<EditKeyboardLayoutActivity.KeyOption> comboList = new ArrayList<>();
        if (initialCombo != null) {
            comboList.addAll(initialCombo);
        }

        // Determine initial tab from currentType
        Tab initialTab = Tab.KEYS;
        if (!keyboardOnly) {
            if (currentType == VirtualKey.KeyType.MOUSE_BUTTON
                    || currentCode == VirtualKey.ACTION_CODE_SCROLL_UP
                    || currentCode == VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                initialTab = Tab.MOUSE;
            } else if (currentType == VirtualKey.KeyType.ACTION) {
                initialTab = Tab.ACTIONS;
            }
        }

        final Tab[] activeTab = {initialTab};
        final String[] searchQuery = {""};

        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = new Runnable() {
            @Override
            public void run() {
                final boolean comboActive = allowCombo && activeTab[0] == Tab.KEYS;

                buildTabs(context, tabBar, activeTab[0], keyboardOnly, comboActive,
                        accentColor, accentPressed,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                activeTab[0] = (Tab) v.getTag();
                                refreshHolder[0].run();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (!allowNone && comboList.isEmpty()) {
                                    Toast.makeText(context, "Select at least 1 key", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                dialog.dismiss();
                                if (comboListener != null) {
                                    comboListener.onComboSelected(new ArrayList<>(comboList));
                                }
                            }
                        });

                if (comboActive) {
                    comboBar.setVisibility(View.VISIBLE);
                    buildComboBar(context, comboBar, comboList,
                            new Runnable() {
                                @Override
                                public void run() {
                                    refreshHolder[0].run();
                                }
                            });
                } else {
                    comboBar.setVisibility(View.GONE);
                }

                OnKeySelectedListener itemClick = new OnKeySelectedListener() {
                    @Override
                    public void onKeySelected(EditKeyboardLayoutActivity.KeyOption opt) {
                        if (comboActive && opt.type == VirtualKey.KeyType.KEYBOARD) {
                            int existingIdx = findComboIndex(comboList, opt.code);
                            if (existingIdx >= 0) {
                                comboList.remove(existingIdx);
                            } else {
                                if (comboList.size() >= 4) {
                                    Toast.makeText(context, "Max 4 keys in combo", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                comboList.add(opt);
                            }
                            refreshHolder[0].run();
                        } else {
                            dialog.dismiss();
                            if (singleListener != null) {
                                singleListener.onKeySelected(opt);
                            }
                        }
                    }
                };

                gridContainer.removeAllViews();
                String q = searchQuery[0].trim().toLowerCase(Locale.ROOT);
                boolean onlyKb = keyboardOnly;

                if (!q.isEmpty()) {
                    List<EditKeyboardLayoutActivity.KeyOption> filtered = new ArrayList<>();
                    for (EditKeyboardLayoutActivity.KeyOption opt : allOptions) {
                        if (onlyKb && opt.type != VirtualKey.KeyType.KEYBOARD) continue;
                        if (opt.name.toLowerCase(Locale.ROOT).contains(q)) {
                            filtered.add(opt);
                        }
                    }
                    if (filtered.isEmpty()) {
                        TextView empty = new TextView(context);
                        empty.setText("No matching keys");
                        empty.setTextColor(0xFF6E7681);
                        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(0, dp(context, 24), 0, dp(context, 24));
                        gridContainer.addView(empty);
                    } else {
                        addGridSection(context, gridContainer, null, filtered, 4,
                                currentType, currentCode, comboActive, comboList,
                                accentColor, accentPressed, itemClick);
                    }
                    return;
                }

                if (activeTab[0] == Tab.KEYS) {
                    List<EditKeyboardLayoutActivity.KeyOption> letters = new ArrayList<>();
                    List<EditKeyboardLayoutActivity.KeyOption> numbers = new ArrayList<>();
                    List<EditKeyboardLayoutActivity.KeyOption> fKeys = new ArrayList<>();
                    List<EditKeyboardLayoutActivity.KeyOption> symbols = new ArrayList<>();
                    List<EditKeyboardLayoutActivity.KeyOption> modifiers = new ArrayList<>();

                    for (EditKeyboardLayoutActivity.KeyOption opt : allOptions) {
                        if (opt.type != VirtualKey.KeyType.KEYBOARD) continue;
                        if (opt.code >= KeyEvent.KEYCODE_A && opt.code <= KeyEvent.KEYCODE_Z) {
                            letters.add(opt);
                        } else if (opt.code >= KeyEvent.KEYCODE_0 && opt.code <= KeyEvent.KEYCODE_9) {
                            numbers.add(opt);
                        } else if (opt.code >= KeyEvent.KEYCODE_F1 && opt.code <= KeyEvent.KEYCODE_F12) {
                            fKeys.add(opt);
                        } else if (isSymbolCode(opt.code)) {
                            symbols.add(opt);
                        } else {
                            modifiers.add(opt);
                        }
                    }

                    addGridSection(context, gridContainer, "A – Z", letters, 9,
                            currentType, currentCode, comboActive, comboList,
                            accentColor, accentPressed, itemClick);
                    addGridSection(context, gridContainer, "0 – 9", numbers, 10,
                            currentType, currentCode, comboActive, comboList,
                            accentColor, accentPressed, itemClick);
                    addGridSection(context, gridContainer, "Modifiers & Navigation", modifiers, 4,
                            currentType, currentCode, comboActive, comboList,
                            accentColor, accentPressed, itemClick);
                    addGridSection(context, gridContainer, "Function Keys", fKeys, 6,
                            currentType, currentCode, comboActive, comboList,
                            accentColor, accentPressed, itemClick);
                    addGridSection(context, gridContainer, "Symbols", symbols, 4,
                            currentType, currentCode, comboActive, comboList,
                            accentColor, accentPressed, itemClick);
                } else if (activeTab[0] == Tab.MOUSE) {
                    List<EditKeyboardLayoutActivity.KeyOption> mouseOpts = new ArrayList<>();
                    for (EditKeyboardLayoutActivity.KeyOption opt : allOptions) {
                        if (opt.type == VirtualKey.KeyType.MOUSE_BUTTON
                                || opt.code == VirtualKey.ACTION_CODE_SCROLL_UP
                                || opt.code == VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                            mouseOpts.add(opt);
                        }
                    }
                    addGridSection(context, gridContainer, "Mouse & Scroll", mouseOpts, 2,
                            currentType, currentCode, false, comboList,
                            accentColor, accentPressed, itemClick);
                } else {
                    List<EditKeyboardLayoutActivity.KeyOption> actionOpts = new ArrayList<>();
                    for (EditKeyboardLayoutActivity.KeyOption opt : allOptions) {
                        if (opt.type == VirtualKey.KeyType.ACTION
                                && opt.code != VirtualKey.ACTION_CODE_SCROLL_UP
                                && opt.code != VirtualKey.ACTION_CODE_SCROLL_DOWN) {
                            actionOpts.add(opt);
                        }
                    }
                    addGridSection(context, gridContainer, "Overlay Actions", actionOpts, 2,
                            currentType, currentCode, false, comboList,
                            accentColor, accentPressed, itemClick);
                }
            }
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery[0] = s != null ? s.toString() : "";
                refreshHolder[0].run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        refreshHolder[0].run();

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int w = Math.min(dp(context, 620), (int) (dm.widthPixels * 0.86f));
            int h = Math.min(dp(context, 400), (int) (dm.heightPixels * 0.88f));
            window.setLayout(w, h);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        dialog.show();
    }

    private static int findComboIndex(List<EditKeyboardLayoutActivity.KeyOption> comboList, int code) {
        for (int i = 0; i < comboList.size(); i++) {
            if (comboList.get(i).code == code) return i;
        }
        return -1;
    }

    private static void buildComboBar(
            Context context,
            LinearLayout comboBar,
            final List<EditKeyboardLayoutActivity.KeyOption> comboList,
            final Runnable onChanged) {

        comboBar.removeAllViews();

        LinearLayout chipsWrap = new LinearLayout(context);
        chipsWrap.setOrientation(LinearLayout.HORIZONTAL);
        chipsWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        comboBar.addView(chipsWrap, wrapLp);

        if (comboList.isEmpty()) {
            TextView hint = new TextView(context);
            hint.setText("Tap up to 4 keys below...");
            hint.setTextColor(0xFF6E7681);
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            chipsWrap.addView(hint);
        } else {
            for (int i = 0; i < comboList.size(); i++) {
                final int idx = i;
                EditKeyboardLayoutActivity.KeyOption opt = comboList.get(i);
                if (i > 0) {
                    TextView plus = new TextView(context);
                    plus.setText("+");
                    plus.setTextColor(0xFF6E7681);
                    plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    plus.setPadding(dp(context, 4), 0, dp(context, 4), 0);
                    chipsWrap.addView(plus);
                }
                TextView chip = new TextView(context);
                chip.setText(opt.name + " ×");
                chip.setTextColor(0xFFE6EDF3);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                chip.setBackgroundResource(R.drawable.vk_btn_secondary);
                int pH = dp(context, 8);
                int pV = dp(context, 4);
                chip.setPadding(pH, pV, pH, pV);
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        comboList.remove(idx);
                        onChanged.run();
                    }
                });
                chipsWrap.addView(chip);
            }
        }
    }

    private static void buildTabs(
            Context context,
            LinearLayout tabBar,
            Tab activeTab,
            boolean keyboardOnly,
            boolean showApply,
            int accentColor,
            int accentPressed,
            View.OnClickListener onTabClick,
            View.OnClickListener onApplyClick) {

        tabBar.removeAllViews();
        addTabButton(context, tabBar, "Keys", Tab.KEYS,
                activeTab == Tab.KEYS, accentColor, onTabClick);
        if (!keyboardOnly) {
            addTabButton(context, tabBar, "Mouse", Tab.MOUSE, activeTab == Tab.MOUSE, accentColor, onTabClick);
            addTabButton(context, tabBar, "Actions", Tab.ACTIONS, activeTab == Tab.ACTIONS, accentColor, onTabClick);
        }

        if (showApply) {
            View spacer = new View(context);
            tabBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            TextView btnApply = new TextView(context);
            btnApply.setText("Apply");
            btnApply.setTextColor(0xFFFFFFFF);
            btnApply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            btnApply.setTypeface(null, android.graphics.Typeface.BOLD);
            btnApply.setGravity(Gravity.CENTER);
            btnApply.setBackground(createKeyTileDrawable(context, accentColor, accentPressed));
            int pH = dp(context, 14);
            btnApply.setPadding(pH, 0, pH, 0);
            btnApply.setOnClickListener(onApplyClick);
            tabBar.addView(btnApply, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 28)));
        }
    }

    private static void addTabButton(
            Context context,
            LinearLayout tabBar,
            String label,
            Tab tab,
            boolean isActive,
            int accentColor,
            View.OnClickListener onClick) {

        TextView btn = new TextView(context);
        btn.setTag(tab);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(isActive ? 0xFFFFFFFF : 0xFF8B949E);
        if (isActive) {
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(context, 6));
        bg.setColor(isActive ? accentColor : 0xFF1A1F27);
        btn.setBackground(bg);

        int pH = dp(context, 12);
        btn.setPadding(pH, 0, pH, 0);
        btn.setOnClickListener(onClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 28));
        lp.rightMargin = dp(context, 6);
        tabBar.addView(btn, lp);
    }

    private static void addGridSection(
            Context context,
            LinearLayout container,
            String sectionTitle,
            List<EditKeyboardLayoutActivity.KeyOption> options,
            int columns,
            VirtualKey.KeyType currentType,
            int currentCode,
            boolean isComboMode,
            List<EditKeyboardLayoutActivity.KeyOption> comboList,
            int accentColor,
            int accentPressed,
            final OnKeySelectedListener onKeyClick) {

        if (options.isEmpty()) return;

        if (sectionTitle != null) {
            TextView header = new TextView(context);
            header.setText(sectionTitle);
            header.setTextColor(0xFF6E7681);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            header.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hLp.topMargin = dp(context, 4);
            hLp.bottomMargin = dp(context, 4);
            container.addView(header, hLp);
        }

        int gap = dp(context, 4);
        int cellHeight = dp(context, 32);

        for (int i = 0; i < options.size(); i += columns) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = gap;
            row.setLayoutParams(rowLp);

            for (int c = 0; c < columns; c++) {
                int idx = i + c;
                if (idx < options.size()) {
                    final EditKeyboardLayoutActivity.KeyOption opt = options.get(idx);
                    boolean isSelected = isComboMode
                            ? (opt.type == VirtualKey.KeyType.KEYBOARD && findComboIndex(comboList, opt.code) >= 0)
                            : (opt.type == currentType && opt.code == currentCode);

                    TextView cell = new TextView(context);
                    cell.setText(opt.name);
                    cell.setTextColor(isSelected ? 0xFFFFFFFF : 0xFFE6EDF3);
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    cell.setSingleLine(true);
                    cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    cell.setGravity(Gravity.CENTER);
                    cell.setPadding(dp(context, 4), 0, dp(context, 4), 0);
                    if (isSelected) {
                        cell.setTypeface(null, android.graphics.Typeface.BOLD);
                        cell.setBackground(createKeyTileDrawable(context, accentColor, accentPressed));
                    } else {
                        cell.setBackgroundResource(R.drawable.vk_btn_secondary);
                    }
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (onKeyClick != null) {
                                onKeyClick.onKeySelected(opt);
                            }
                        }
                    });

                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, cellHeight, 1f);
                    if (c < columns - 1) cellLp.rightMargin = gap;
                    row.addView(cell, cellLp);
                } else {
                    View spacer = new View(context);
                    LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, cellHeight, 1f);
                    if (c < columns - 1) spLp.rightMargin = gap;
                    row.addView(spacer, spLp);
                }
            }
            container.addView(row);
        }
    }

    private static StateListDrawable createKeyTileDrawable(Context context, int normal, int pressed) {
        float r = dp(context, 6);
        GradientDrawable p = new GradientDrawable();
        p.setCornerRadius(r);
        p.setColor(pressed);

        GradientDrawable n = new GradientDrawable();
        n.setCornerRadius(r);
        n.setColor(normal);

        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, p);
        s.addState(new int[]{}, n);
        return s;
    }

    private static boolean isSymbolCode(int code) {
        return code == KeyEvent.KEYCODE_GRAVE
                || code == KeyEvent.KEYCODE_MINUS
                || code == KeyEvent.KEYCODE_EQUALS
                || code == KeyEvent.KEYCODE_LEFT_BRACKET
                || code == KeyEvent.KEYCODE_RIGHT_BRACKET
                || code == KeyEvent.KEYCODE_BACKSLASH
                || code == KeyEvent.KEYCODE_SEMICOLON
                || code == KeyEvent.KEYCODE_APOSTROPHE
                || code == KeyEvent.KEYCODE_COMMA
                || code == KeyEvent.KEYCODE_PERIOD
                || code == KeyEvent.KEYCODE_SLASH;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }
}
