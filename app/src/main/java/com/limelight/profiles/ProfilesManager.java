package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.LimeLog;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProfilesManager {
    private static final String PROFILES_DIR = "profiles";
    private static final String PROFILES_FILE = "profiles.json";
    private static final String DEFAULT_PROFILES_DIR = "default_profiles";
    private static final String LAYOUTS_FILE = "layouts.json";

    static ProfilesManager instance;

    private final Map<UUID, SettingsProfile> profiles = new LinkedHashMap<>();
    private UUID activeProfileId;
    private final List<ProfileChangeListener> listeners = new ArrayList<>();
    private Context appContext; // Application context for auto-save

    private ProfilesManager() {}

    public static synchronized ProfilesManager getInstance() {
        if (instance == null) {
            instance = new ProfilesManager();
        }
        return instance;
    }

    public boolean load(Context context) {
        LimeLog.info("ArtemisProfile: Loading profile...");
        if (context == null) {
            return false;
        }

        try {
            this.appContext = context.getApplicationContext();
        } catch (Exception e) {
            // If getApplicationContext() fails (e.g., during app startup), use the context directly
            this.appContext = context;
        }

        // Additional safety check
        if (this.appContext == null) {
            return false;
        }

        try {
            File dir = new File(this.appContext.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            if (!file.exists()) {
                // First start: the profiles shipped with the app, if any. We don't want to warn user about
                // profile not exist
                if (!installDefaultProfiles(file)) {
                    return true;
                }
            }
            try (Reader reader = new FileReader(file)) {
                Gson gson = new Gson();
                Type type = new TypeToken<ProfilesData>(){}.getType();
                ProfilesData data = gson.fromJson(reader, type);
                if (data != null && data.profiles != null) {
                    profiles.clear();
                    for (SettingsProfile p : data.profiles) {
                        profiles.put(p.getUuid(), p);
                    }
                    activeProfileId = data.activeProfileId;
                }
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to load profiles from file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to load profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Installs the profiles the app ships with (assets/default_profiles): profiles.json is copied as the
     * profiles file, and layouts.json holds each profile's on-screen controls layout, by profile uuid, which
     * go to that profile's layout preferences. Returns false if the app ships none.
     */
    private boolean installDefaultProfiles(File profilesFile) {
        try {
            byte[] profilesJson;
            try (InputStream in = appContext.getAssets().open(DEFAULT_PROFILES_DIR + "/" + PROFILES_FILE)) {
                profilesJson = readAll(in);
            }
            try (InputStream in = appContext.getAssets().open(DEFAULT_PROFILES_DIR + "/" + LAYOUTS_FILE)) {
                Map<String, Map<String, Object>> layouts = new Gson().fromJson(
                        new String(readAll(in), StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Map<String, Object>>>(){}.getType());
                for (Map.Entry<String, Map<String, Object>> layout : layouts.entrySet()) {
                    SharedPreferences.Editor editor = appContext.getSharedPreferences(
                            VirtualControllerConfigurationLoader.OSC_PREFERENCE + "_" + layout.getKey(),
                            Context.MODE_PRIVATE).edit();
                    for (Map.Entry<String, Object> value : layout.getValue().entrySet()) {
                        if (value.getValue() instanceof Boolean) {
                            editor.putBoolean(value.getKey(), (Boolean) value.getValue());
                        } else {
                            editor.putString(value.getKey(), String.valueOf(value.getValue()));
                        }
                    }
                    editor.commit();
                }
            }
            // The profiles file last: if anything above failed, the next start tries again
            try (FileOutputStream out = new FileOutputStream(profilesFile)) {
                out.write(profilesJson);
            }
            LimeLog.info("ArtemisProfile: Installed the default profiles");
            return true;
        } catch (IOException | RuntimeException e) {
            LimeLog.info("ArtemisProfile: No default profiles installed: " + e);
            return false;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    public boolean save(Context context) {
        if (context == null) {
            return false;
        }

        try {
            File dir = new File(context.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            try (Writer writer = new FileWriter(file)) {
                Gson gson = new Gson();
                ProfilesData data = new ProfilesData();
                data.profiles = new ArrayList<>(profiles.values());
                data.activeProfileId = activeProfileId;
                gson.toJson(data, writer);
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to save profiles to file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to save profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public List<SettingsProfile> getProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public void add(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void update(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void delete(UUID uuid) {
        profiles.remove(uuid);
        if (uuid.equals(activeProfileId)) {
            activeProfileId = null;
        }
        notifyListeners();
        saveIfPossible();
    }

    public void setActive(UUID uuid) {
        activeProfileId = uuid;
        notifyListeners();
        saveIfPossible();
    }

    public SettingsProfile getActive() {
        return activeProfileId == null ? null : profiles.get(activeProfileId);
    }

    @NonNull
    public String getActiveName() {
        SettingsProfile active = getActive();
        return active == null ? "" : active.getName();
    }

    public void addListener(ProfileChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProfileChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ProfileChangeListener listener : listeners) {
            listener.onProfilesChanged();
        }
    }

    private static class ProfilesData {
        List<SettingsProfile> profiles;
        UUID activeProfileId;
    }

    public interface ProfileChangeListener {
        void onProfilesChanged();
    }

    /**
     * Returns a SharedPreferences that overlays the active profile's options on top of the real prefs.
     */
    public SharedPreferences getOverlayingSharedPreferences(Context context) {
        SharedPreferences base = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SettingsProfile active = getActive();
        if (active == null || active.getOptions() == null) {
            return base;
        }
        return new OverlaySharedPreferences(base, active.getOptions());
    }

    /**
     * Wraps a SharedPreferences to override and shadow values from a profile's options map.
     */
    private static class OverlaySharedPreferences implements SharedPreferences {
        private final SharedPreferences base;
        private final Map<String, Object> patch;
        OverlaySharedPreferences(SharedPreferences base, Map<String, Object> patch) {
            this.base = base;
            this.patch = patch;
        }
        @Override public Map<String, ?> getAll() {
            Map<String, Object> combined = new LinkedHashMap<>(base.getAll());
            combined.putAll(patch);
            return combined;
        }
        @Override public String getString(String key, String defValue) {
            if (patch.containsKey(key)) return (String) patch.get(key);
            return base.getString(key, defValue);
        }
        @Override public int getInt(String key, int defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).intValue();
            return base.getInt(key, defValue);
        }
        @Override public long getLong(String key, long defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).longValue();
            return base.getLong(key, defValue);
        }
        @Override public float getFloat(String key, float defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).floatValue();
            return base.getFloat(key, defValue);
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            if (patch.containsKey(key)) return (Boolean) patch.get(key);
            return base.getBoolean(key, defValue);
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            if (patch.containsKey(key)) return (Set<String>) patch.get(key);
            return base.getStringSet(key, defValues);
        }
        @Override public boolean contains(String key) {
            return patch.containsKey(key) || base.contains(key);
        }
        @Override public Editor edit() { return base.edit(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.registerOnSharedPreferenceChangeListener(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private boolean saveIfPossible() {
        if (appContext != null) {
            return save(appContext);
        }
        return false;
    }
}