package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualKeyboardProfileManager {

    private static final String PREFS_NAME = "virtual_keyboard_prefs";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_PROFILES_INDEX = "keyboard_profiles_index";

    public static final String DEFAULT_PROFILE_ID = "default";
    public static final String DEFAULT_PROFILE_NAME = "Default Layout";

    public static class LayoutProfileInfo {
        public String id;
        public String name;
        public boolean isDefault;
        public int keyCount;
        public long lastModified;

        public LayoutProfileInfo(String id, String name, boolean isDefault, int keyCount, long lastModified) {
            this.id = id;
            this.name = name;
            this.isDefault = isDefault;
            this.keyCount = keyCount;
            this.lastModified = lastModified;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("isDefault", isDefault);
                obj.put("keyCount", keyCount);
                obj.put("lastModified", lastModified);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static LayoutProfileInfo fromJson(JSONObject obj) {
            if (obj == null) return null;
            String id = obj.optString("id", DEFAULT_PROFILE_ID);
            String name = obj.optString("name", DEFAULT_PROFILE_NAME);
            boolean isDefault = obj.optBoolean("isDefault", DEFAULT_PROFILE_ID.equals(id));
            int keyCount = obj.optInt("keyCount", 0);
            long lastModified = obj.optLong("lastModified", System.currentTimeMillis());
            return new LayoutProfileInfo(id, name, isDefault, keyCount, lastModified);
        }
    }

    private static File getLayoutDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "keyboard_layouts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File getLayoutFile(Context context, String layoutId) {
        return new File(getLayoutDirectory(context), layoutId + ".json");
    }

    public static File getBackgroundImageFile(Context context, String layoutId) {
        return new File(getLayoutDirectory(context), layoutId + "_bg.png");
    }

    public static File getBackgroundImageFile(Context context) {
        return getBackgroundImageFile(context, getActiveProfileId(context));
    }

    public static synchronized void ensureInitialized(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String indexStr = prefs.getString(KEY_PROFILES_INDEX, null);

        if (indexStr == null) {
            // Fresh initialization: do not create any default buttons layout
            saveProfilesIndex(context, new ArrayList<LayoutProfileInfo>());
            setActiveProfileId(context, null);
        }
    }

    private static synchronized void saveProfilesIndex(Context context, List<LayoutProfileInfo> profiles) {
        try {
            JSONArray arr = new JSONArray();
            for (LayoutProfileInfo p : profiles) {
                arr.put(p.toJson());
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_PROFILES_INDEX, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized List<LayoutProfileInfo> getProfiles(Context context) {
        ensureInitialized(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String indexStr = prefs.getString(KEY_PROFILES_INDEX, null);
        List<LayoutProfileInfo> list = new ArrayList<>();

        if (indexStr != null) {
            try {
                JSONArray arr = new JSONArray(indexStr);
                for (int i = 0; i < arr.length(); i++) {
                    LayoutProfileInfo info = LayoutProfileInfo.fromJson(arr.getJSONObject(i));
                    if (info != null) {
                        list.add(info);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return list;
    }

    public static synchronized String getActiveProfileId(Context context) {
        ensureInitialized(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        if (activeId == null || activeId.trim().isEmpty()) {
            return null;
        }

        // Verify that activeId exists
        List<LayoutProfileInfo> profiles = getProfiles(context);
        boolean exists = false;
        for (LayoutProfileInfo p : profiles) {
            if (p.id.equals(activeId)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            setActiveProfileId(context, null);
            return null;
        }
        return activeId;
    }

    public static synchronized void setActiveProfileId(Context context, String layoutId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, layoutId).apply();
    }

    public static synchronized LayoutProfileInfo getProfileInfo(Context context, String layoutId) {
        if (layoutId == null) return null;
        List<LayoutProfileInfo> profiles = getProfiles(context);
        for (LayoutProfileInfo p : profiles) {
            if (p.id.equals(layoutId)) {
                return p;
            }
        }
        return null;
    }

    public static synchronized List<VirtualKey> loadProfile(Context context) {
        String activeId = getActiveProfileId(context);
        if (activeId == null || activeId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return loadProfile(context, activeId);
    }

    public static synchronized List<VirtualKey> loadProfile(Context context, String layoutId) {
        if (layoutId == null || layoutId.trim().isEmpty()) {
            return new ArrayList<>();
        }
        ensureInitialized(context);
        File file = getLayoutFile(context, layoutId);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }

            JSONObject root = new JSONObject(sb.toString());
            String keysJson = root.optString("keys", "");
            return parseJsonToKeys(keysJson, context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static synchronized void saveProfile(Context context, List<VirtualKey> keys) {
        saveProfile(context, getActiveProfileId(context), keys);
    }

    public static synchronized void saveProfile(Context context, String layoutId, List<VirtualKey> keys) {
        int opacity = getBackgroundOpacity(context, layoutId);
        saveProfileInternal(context, layoutId, keys, opacity);

        // Update keyCount & timestamp in index
        List<LayoutProfileInfo> profiles = getProfiles(context);
        for (LayoutProfileInfo p : profiles) {
            if (p.id.equals(layoutId)) {
                p.keyCount = keys.size();
                p.lastModified = System.currentTimeMillis();
                break;
            }
        }
        saveProfilesIndex(context, profiles);
    }

    private static synchronized void saveProfileInternal(Context context, String layoutId, List<VirtualKey> keys, int bgOpacity) {
        File file = getLayoutFile(context, layoutId);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            JSONObject root = new JSONObject();
            root.put("id", layoutId);
            root.put("bgOpacity", bgOpacity);
            root.put("keys", keysToJson(keys));
            writer.write(root.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized LayoutProfileInfo createProfile(Context context, String name, List<VirtualKey> initialKeys) {
        ensureInitialized(context);
        String id = "layout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        if (initialKeys == null) {
            initialKeys = new ArrayList<>();
        }

        saveProfileInternal(context, id, initialKeys, 100);

        LayoutProfileInfo info = new LayoutProfileInfo(id, name, false, initialKeys.size(), System.currentTimeMillis());
        List<LayoutProfileInfo> profiles = getProfiles(context);
        profiles.add(info);
        saveProfilesIndex(context, profiles);

        return info;
    }

    public static synchronized LayoutProfileInfo duplicateProfile(Context context, String sourceId, String newName) {
        List<VirtualKey> keys = loadProfile(context, sourceId);
        LayoutProfileInfo newProfile = createProfile(context, newName, keys);

        // Duplicate background image if exists
        File srcBg = getBackgroundImageFile(context, sourceId);
        if (srcBg.exists() && srcBg.length() > 0) {
            File destBg = getBackgroundImageFile(context, newProfile.id);
            try (FileInputStream fis = new FileInputStream(srcBg);
                 FileOutputStream fos = new FileOutputStream(destBg)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int opacity = getBackgroundOpacity(context, sourceId);
        saveBackgroundOpacity(context, newProfile.id, opacity);

        return newProfile;
    }

    public static synchronized boolean renameProfile(Context context, String layoutId, String newName) {
        if (newName == null || newName.trim().isEmpty()) return false;
        List<LayoutProfileInfo> profiles = getProfiles(context);
        for (LayoutProfileInfo p : profiles) {
            if (p.id.equals(layoutId)) {
                p.name = newName.trim();
                p.lastModified = System.currentTimeMillis();
                saveProfilesIndex(context, profiles);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean deleteProfile(Context context, String layoutId) {
        if (layoutId == null) return false;

        List<LayoutProfileInfo> profiles = getProfiles(context);
        LayoutProfileInfo toRemove = null;
        for (LayoutProfileInfo p : profiles) {
            if (p.id.equals(layoutId)) {
                toRemove = p;
                break;
            }
        }

        if (toRemove != null) {
            profiles.remove(toRemove);
            saveProfilesIndex(context, profiles);

            // Delete json & bg file
            File file = getLayoutFile(context, layoutId);
            if (file.exists()) file.delete();
            File bg = getBackgroundImageFile(context, layoutId);
            if (bg.exists()) bg.delete();

            // If active layout was deleted, clear active
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String currentActive = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
            if (layoutId.equals(currentActive)) {
                setActiveProfileId(context, null);
            }
            return true;
        }
        return false;
    }

    public static synchronized void resetProfile(Context context, String layoutId) {
        saveProfile(context, layoutId, new ArrayList<VirtualKey>());
        clearBackgroundImage(context, layoutId);
        saveBackgroundOpacity(context, layoutId, 100);
    }

    public static synchronized void resetProfile(Context context) {
        resetProfile(context, getActiveProfileId(context));
    }

    public static synchronized int getBackgroundOpacity(Context context, String layoutId) {
        File file = getLayoutFile(context, layoutId);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                JSONObject root = new JSONObject(sb.toString());
                return root.optInt("bgOpacity", 100);
            } catch (Exception ignored) {}
        }
        return 100;
    }

    public static synchronized int getBackgroundOpacity(Context context) {
        return getBackgroundOpacity(context, getActiveProfileId(context));
    }

    public static synchronized void saveBackgroundOpacity(Context context, String layoutId, int opacity) {
        List<VirtualKey> keys = loadProfile(context, layoutId);
        saveProfileInternal(context, layoutId, keys, opacity);
    }

    public static synchronized void saveBackgroundOpacity(Context context, int opacity) {
        saveBackgroundOpacity(context, getActiveProfileId(context), opacity);
    }

    public static boolean hasCustomBackgroundImage(Context context, String layoutId) {
        File file = getBackgroundImageFile(context, layoutId);
        return file.exists() && file.length() > 0;
    }

    public static boolean hasCustomBackgroundImage(Context context) {
        return hasCustomBackgroundImage(context, getActiveProfileId(context));
    }

    public static void clearBackgroundImage(Context context, String layoutId) {
        File file = getBackgroundImageFile(context, layoutId);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void clearBackgroundImage(Context context) {
        clearBackgroundImage(context, getActiveProfileId(context));
    }

    public static synchronized String exportProfileToJson(Context context, String layoutId) {
        LayoutProfileInfo info = getProfileInfo(context, layoutId);
        List<VirtualKey> keys = loadProfile(context, layoutId);
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("name", info != null ? info.name : "Exported Layout");
            root.put("bgOpacity", getBackgroundOpacity(context, layoutId));
            root.put("keys", keysToJson(keys));
            return root.toString(2);
        } catch (Exception e) {
            e.printStackTrace();
            return keysToJson(keys);
        }
    }

    public static synchronized LayoutProfileInfo importProfileFromJson(Context context, String jsonStr, String customName) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            String name = customName;
            int bgOpacity = 100;
            List<VirtualKey> keys;

            if (jsonStr.trim().startsWith("{")) {
                JSONObject root = new JSONObject(jsonStr);
                if (name == null || name.trim().isEmpty()) {
                    name = root.optString("name", "Imported Layout");
                }
                bgOpacity = root.optInt("bgOpacity", 100);
                String keysStr = root.optString("keys", "");
                keys = parseJsonToKeys(keysStr, context);
            } else {
                if (name == null || name.trim().isEmpty()) {
                    name = "Imported Layout";
                }
                keys = parseJsonToKeys(jsonStr, context);
            }

            LayoutProfileInfo profile = createProfile(context, name, keys);
            saveBackgroundOpacity(context, profile.id, bgOpacity);
            return profile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<VirtualKey> parseJsonToKeys(String jsonStr) {
        return parseJsonToKeys(jsonStr, null);
    }

    public static List<VirtualKey> parseJsonToKeys(String jsonStr, Context context) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JSONArray arr = new JSONArray(jsonStr);
            List<VirtualKey> keys = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.getString("id");
                String label = obj.getString("label");
                VirtualKey.KeyType type = VirtualKey.KeyType.valueOf(obj.getString("type"));
                int code = obj.getInt("code");
                float relX = (float) obj.getDouble("relX");
                float relY = (float) obj.getDouble("relY");
                float relW = (float) obj.getDouble("relW");
                float relH = (float) obj.getDouble("relH");

                VirtualKey key = new VirtualKey(id, label, type, code, relX, relY, relW, relH);
                key.cornerRadius = (float) obj.optDouble("cornerRadius", 0.0);
                key.textOpacity = obj.optInt("textOpacity", 100);
                key.bgOpacity = obj.optInt("bgOpacity", 45);
                key.showBorder = obj.optBoolean("showBorder", true);
                key.isSlide = obj.optBoolean("isSlide", false);
                key.holdOutside = obj.optBoolean("holdOutside", false);
                key.absorbTouch = obj.optBoolean("absorbTouch", true);
                key.isBlockTouch = obj.optBoolean("isBlockTouch", false);
                key.switchInputModeOnToggle = obj.optBoolean("switchInputModeOnToggle", false);
                boolean isScroll = obj.optBoolean("isScroll", false);
                if (isScroll) {
                    continue;
                }
                key.isJoystick = obj.optBoolean("isJoystick", false);
                key.codeUp = obj.optInt("codeUp", android.view.KeyEvent.KEYCODE_W);
                key.codeDown = obj.optInt("codeDown", android.view.KeyEvent.KEYCODE_S);
                key.codeLeft = obj.optInt("codeLeft", android.view.KeyEvent.KEYCODE_A);
                key.codeRight = obj.optInt("codeRight", android.view.KeyEvent.KEYCODE_D);
                key.extraCode1 = obj.optInt("extraCode1", 0);
                key.extraCode2 = obj.optInt("extraCode2", 0);
                key.extraCode3 = obj.optInt("extraCode3", 0);

                keys.add(key);
            }
            return keys;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static String keysToJson(List<VirtualKey> keys) {
        try {
            JSONArray arr = new JSONArray();
            for (VirtualKey k : keys) {
                JSONObject obj = new JSONObject();
                obj.put("id", k.id);
                obj.put("label", k.label);
                obj.put("type", k.type.name());
                obj.put("code", k.code);
                obj.put("relX", k.relX);
                obj.put("relY", k.relY);
                obj.put("relW", k.relWidth);
                obj.put("relH", k.relHeight);
                obj.put("cornerRadius", k.cornerRadius);
                obj.put("textOpacity", k.textOpacity);
                obj.put("bgOpacity", k.bgOpacity);
                obj.put("showBorder", k.showBorder);
                obj.put("isSlide", k.isSlide);
                obj.put("holdOutside", k.holdOutside);
                obj.put("absorbTouch", k.absorbTouch);
                obj.put("isBlockTouch", k.isBlockTouch);
                obj.put("switchInputModeOnToggle", k.switchInputModeOnToggle);
                obj.put("isJoystick", k.isJoystick);
                obj.put("isScroll", k.isScroll);
                obj.put("codeUp", k.codeUp);
                obj.put("codeDown", k.codeDown);
                obj.put("codeLeft", k.codeLeft);
                obj.put("codeRight", k.codeRight);
                obj.put("extraCode1", k.extraCode1);
                obj.put("extraCode2", k.extraCode2);
                obj.put("extraCode3", k.extraCode3);
                arr.put(obj);
            }
            return arr.toString(2);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
