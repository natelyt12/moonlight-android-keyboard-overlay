package com.limelight.binding.input.virtual_keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ManageKeyboardLayoutsActivity extends Activity {

    public static final String EXTRA_LAYOUT_ID = "EXTRA_LAYOUT_ID";
    private static final int REQUEST_EXPORT_LAYOUT = 2001;
    private static final int REQUEST_IMPORT_LAYOUT = 2002;
    private static final int REQUEST_EDIT_LAYOUT = 2003;

    // Matches the toolbar background so the status bar blends into the header
    private static final int TOP_BAR_COLOR = 0xFF1A1C22;

    private ListView listView;
    private TextView emptyState;
    private LayoutAdapter adapter;
    private List<VirtualKeyboardProfileManager.LayoutProfileInfo> profileList = new ArrayList<>();
    private String activeProfileId;

    private String pendingExportLayoutId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiHelper.setLocale(this);
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(TOP_BAR_COLOR);
        }
        // Android 15+ enforces edge-to-edge and ignores setStatusBarColor(), so paint the window
        // background with the same colour to keep the status bar flush with the action bar.
        window.setBackgroundDrawable(new ColorDrawable(TOP_BAR_COLOR));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        }

        setContentView(R.layout.activity_manage_keyboard_layouts);
        UiHelper.notifyNewRootView(this);

        listView = findViewById(R.id.list_keyboard_layouts);
        emptyState = findViewById(R.id.txt_empty_state);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btn_top_new_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateLayoutDialog();
            }
        });

        findViewById(R.id.btn_top_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportOptionsDialog();
            }
        });

        adapter = new LayoutAdapter();
        listView.setAdapter(adapter);

        refreshProfiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfiles();
    }

    private void refreshProfiles() {
        profileList = VirtualKeyboardProfileManager.getProfiles(this);
        activeProfileId = VirtualKeyboardProfileManager.getActiveProfileId(this);

        if (profileList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void showCreateLayoutDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        final EditText inputName = new EditText(this);
        inputName.setHint(R.string.vk_layout_name_hint);
        layout.addView(inputName);

        final boolean hasActiveProfile = (activeProfileId != null && !activeProfileId.trim().isEmpty()
                && VirtualKeyboardProfileManager.getProfileInfo(this, activeProfileId) != null);

        final RadioGroup radioGroup;
        final RadioButton rbCloneActive;
        final RadioButton rbBlank;

        if (hasActiveProfile) {
            radioGroup = new RadioGroup(this);
            radioGroup.setPadding(0, 20, 0, 10);

            rbBlank = new RadioButton(this);
            rbBlank.setId(View.generateViewId());
            rbBlank.setText(R.string.vk_template_blank);
            radioGroup.addView(rbBlank);

            rbCloneActive = new RadioButton(this);
            rbCloneActive.setId(View.generateViewId());
            VirtualKeyboardProfileManager.LayoutProfileInfo activeInfo =
                    VirtualKeyboardProfileManager.getProfileInfo(this, activeProfileId);
            String activeName = activeInfo != null ? activeInfo.name : getString(R.string.vk_active_name_fallback);
            rbCloneActive.setText(getString(R.string.vk_template_clone_format, activeName));
            radioGroup.addView(rbCloneActive);

            radioGroup.check(rbBlank.getId());
            layout.addView(radioGroup);
        } else {
            radioGroup = null;
            rbCloneActive = null;
            rbBlank = null;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_create_layout_title)
                .setView(layout)
                .setPositiveButton(R.string.vk_create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = inputName.getText().toString().trim();
                        if (name.isEmpty()) {
                            name = getString(R.string.vk_custom_layout_name_format, profileList.size() + 1);
                        }

                        List<VirtualKey> initialKeys;
                        if (rbCloneActive != null && rbCloneActive.isChecked()) {
                            initialKeys = VirtualKeyboardProfileManager.loadProfile(ManageKeyboardLayoutsActivity.this, activeProfileId);
                        } else {
                            initialKeys = new ArrayList<>();
                        }

                        VirtualKeyboardProfileManager.LayoutProfileInfo newProfile =
                                VirtualKeyboardProfileManager.createProfile(ManageKeyboardLayoutsActivity.this, name, initialKeys);

                        if (activeProfileId == null || activeProfileId.isEmpty()) {
                            VirtualKeyboardProfileManager.setActiveProfileId(ManageKeyboardLayoutsActivity.this, newProfile.id);
                        }

                        refreshProfiles();
                        Toast.makeText(ManageKeyboardLayoutsActivity.this, getString(R.string.vk_toast_created, name), Toast.LENGTH_SHORT).show();

                        // Open editor for the newly created layout
                        openEditor(newProfile.id);
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void openEditor(String layoutId) {
        Intent intent = new Intent(this, EditKeyboardLayoutActivity.class);
        intent.putExtra(EXTRA_LAYOUT_ID, layoutId);
        startActivityForResult(intent, REQUEST_EDIT_LAYOUT);
    }

    private void showRenameDialog(final VirtualKeyboardProfileManager.LayoutProfileInfo profile) {
        final EditText input = new EditText(this);
        input.setText(profile.name);
        input.setSelection(profile.name.length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_rename_layout_title)
                .setView(input)
                .setPositiveButton(R.string.vk_rename, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newName = input.getText().toString().trim();
                        if (!newName.isEmpty()) {
                            VirtualKeyboardProfileManager.renameProfile(ManageKeyboardLayoutsActivity.this, profile.id, newName);
                            refreshProfiles();
                        }
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void showDuplicateDialog(final VirtualKeyboardProfileManager.LayoutProfileInfo profile) {
        final EditText input = new EditText(this);
        input.setText(getString(R.string.vk_copy_name_format, profile.name));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_clone_layout_title)
                .setView(input)
                .setPositiveButton(R.string.vk_clone, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newName = input.getText().toString().trim();
                        if (newName.isEmpty()) {
                            newName = getString(R.string.vk_copy_name_format, profile.name);
                        }
                        VirtualKeyboardProfileManager.duplicateProfile(ManageKeyboardLayoutsActivity.this, profile.id, newName);
                        refreshProfiles();
                        Toast.makeText(ManageKeyboardLayoutsActivity.this, getString(R.string.vk_toast_cloned, newName), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void showDeleteConfirmDialog(final VirtualKeyboardProfileManager.LayoutProfileInfo profile) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_delete_layout_title)
                .setMessage(getString(R.string.vk_delete_layout_message, profile.name))
                .setPositiveButton(R.string.vk_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        VirtualKeyboardProfileManager.deleteProfile(ManageKeyboardLayoutsActivity.this, profile.id);
                        refreshProfiles();
                        Toast.makeText(ManageKeyboardLayoutsActivity.this, getString(R.string.vk_toast_deleted, profile.name), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void showExportOptionsDialog(final VirtualKeyboardProfileManager.LayoutProfileInfo profile) {
        final String[] options = new String[] { getString(R.string.vk_export_option_clipboard), getString(R.string.vk_export_option_file) };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.vk_export_layout_title, profile.name))
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            String json = VirtualKeyboardProfileManager.exportProfileToJson(ManageKeyboardLayoutsActivity.this, profile.id);
                            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cb != null) {
                                cb.setPrimaryClip(ClipData.newPlainText(getString(R.string.vk_clipboard_label), json));
                                Toast.makeText(ManageKeyboardLayoutsActivity.this, R.string.vk_toast_json_copied, Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            pendingExportLayoutId = profile.id;
                            String fileName = profile.name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/json");
                            intent.putExtra(Intent.EXTRA_TITLE, fileName);
                            try {
                                startActivityForResult(intent, REQUEST_EXPORT_LAYOUT);
                            } catch (Exception e) {
                                Toast.makeText(ManageKeyboardLayoutsActivity.this, R.string.vk_toast_export_unavailable, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showImportOptionsDialog() {
        final String[] options = new String[] { getString(R.string.vk_import_option_file), getString(R.string.vk_import_option_clipboard) };
        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_import_layout_title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream", "*/*"});
                            try {
                                startActivityForResult(intent, REQUEST_IMPORT_LAYOUT);
                            } catch (Exception e) {
                                showImportPasteDialog(null);
                            }
                        } else if (which == 1) {
                            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            String clipboardText = "";
                            if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                                CharSequence text = cb.getPrimaryClip().getItemAt(0).getText();
                                if (text != null) clipboardText = text.toString();
                            }
                            showImportPasteDialog(clipboardText);
                        }
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void showImportPasteDialog(String initialText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        final EditText editName = new EditText(this);
        editName.setHint(R.string.vk_layout_name_optional_hint);
        layout.addView(editName);

        final EditText editJson = new EditText(this);
        editJson.setHint(R.string.vk_paste_json_hint);
        editJson.setMinLines(5);
        if (initialText != null && !initialText.trim().isEmpty()) {
            editJson.setText(initialText);
        }
        layout.addView(editJson);

        new AlertDialog.Builder(this)
                .setTitle(R.string.vk_paste_json_title)
                .setView(layout)
                .setPositiveButton(R.string.vk_import, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String json = editJson.getText().toString().trim();
                        String name = editName.getText().toString().trim();
                        VirtualKeyboardProfileManager.LayoutProfileInfo profile =
                                VirtualKeyboardProfileManager.importProfileFromJson(ManageKeyboardLayoutsActivity.this, json, name);
                        if (profile != null) {
                            refreshProfiles();
                            Toast.makeText(ManageKeyboardLayoutsActivity.this, getString(R.string.vk_toast_imported, profile.name), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ManageKeyboardLayoutsActivity.this, R.string.vk_toast_parse_failed, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.vk_cancel, null)
                .show();
    }

    private void showItemOverflowMenu(View anchor, final VirtualKeyboardProfileManager.LayoutProfileInfo profile) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.vk_clone));
        popup.getMenu().add(0, 2, 1, getString(R.string.vk_rename));
        popup.getMenu().add(0, 3, 2, getString(R.string.vk_export_json));
        popup.getMenu().add(0, 4, 3, getString(R.string.vk_delete));

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        showDuplicateDialog(profile);
                        return true;
                    case 2:
                        showRenameDialog(profile);
                        return true;
                    case 3:
                        showExportOptionsDialog(profile);
                        return true;
                    case 4:
                        showDeleteConfirmDialog(profile);
                        return true;
                }
                return false;
            }
        });
        popup.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPORT_LAYOUT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (pendingExportLayoutId != null) {
                try {
                    Uri uri = data.getData();
                    String json = VirtualKeyboardProfileManager.exportProfileToJson(this, pendingExportLayoutId);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(json.getBytes(StandardCharsets.UTF_8));
                            Toast.makeText(this, R.string.vk_toast_exported, Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.vk_toast_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                } finally {
                    pendingExportLayoutId = null;
                }
            }
        } else if (requestCode == REQUEST_IMPORT_LAYOUT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                try (InputStream is = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    VirtualKeyboardProfileManager.LayoutProfileInfo profile =
                            VirtualKeyboardProfileManager.importProfileFromJson(this, sb.toString(), null);
                    if (profile != null) {
                        refreshProfiles();
                        Toast.makeText(this, getString(R.string.vk_toast_imported, profile.name), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.vk_toast_parse_failed, Toast.LENGTH_LONG).show();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, getString(R.string.vk_toast_read_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_EDIT_LAYOUT) {
            refreshProfiles();
        }
    }

    private class LayoutAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return profileList.size();
        }

        @Override
        public VirtualKeyboardProfileManager.LayoutProfileInfo getItem(int position) {
            return profileList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ManageKeyboardLayoutsActivity.this)
                        .inflate(R.layout.item_keyboard_layout, parent, false);
            }

            final VirtualKeyboardProfileManager.LayoutProfileInfo profile = getItem(position);
            final boolean isActive = (activeProfileId != null && profile.id.equals(activeProfileId));

            View cardRoot = convertView.findViewById(R.id.card_layout_root);
            if (cardRoot != null) {
                cardRoot.setBackgroundResource(isActive ? R.drawable.bg_layout_card_active : R.drawable.bg_layout_card);
            }

            TextView txtName = convertView.findViewById(R.id.item_layout_name);
            TextView txtInfo = convertView.findViewById(R.id.item_layout_info);
            TextView badgeActive = convertView.findViewById(R.id.badge_active);

            Button btnSetActive = convertView.findViewById(R.id.btn_item_set_active);
            Button btnEdit = convertView.findViewById(R.id.btn_item_edit);
            ImageButton btnMore = convertView.findViewById(R.id.btn_item_more);

            txtName.setText(profile.name);
            badgeActive.setVisibility(isActive ? View.VISIBLE : View.GONE);

            String dateStr = DateFormat.format("yyyy-MM-dd HH:mm", new Date(profile.lastModified)).toString();
            txtInfo.setText(getString(R.string.vk_profile_info_format, profile.keyCount, dateStr));

            if (isActive) {
                btnSetActive.setText(R.string.vk_active);
                btnSetActive.setEnabled(false);
                btnSetActive.setBackgroundTintList(ColorStateList.valueOf(0xFF143A4D));
                btnSetActive.setTextColor(0xFF00B4D8);
            } else {
                btnSetActive.setText(R.string.vk_set_active);
                btnSetActive.setEnabled(true);
                btnSetActive.setBackgroundTintList(ColorStateList.valueOf(0xFF0077B6));
                btnSetActive.setTextColor(0xFFFFFFFF);
            }

            btnSetActive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    VirtualKeyboardProfileManager.setActiveProfileId(ManageKeyboardLayoutsActivity.this, profile.id);
                    refreshProfiles();
                    Toast.makeText(ManageKeyboardLayoutsActivity.this, getString(R.string.vk_toast_active_layout, profile.name), Toast.LENGTH_SHORT).show();
                }
            });

            btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openEditor(profile.id);
                }
            });

            btnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showItemOverflowMenu(v, profile);
                }
            });

            return convertView;
        }
    }
}
