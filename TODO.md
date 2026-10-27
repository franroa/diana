# Diana: pending tasks

- [ ] **Sign releases with a strong key.** The release workflow currently signs with the debug keystore
  of the game server (`/root/.android/debug.keystore`, alias `androiddebugkey`, password `android`), so
  releases update the Diana installed with adb. That password is public knowledge. To change it:
  1. Create a keystore with a strong password:
     `keytool -genkeypair -v -keystore diana.keystore -storetype PKCS12 -alias diana -keyalg RSA -keysize 4096 -validity 10000`
  2. Replace the secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
     `ANDROID_KEY_PASSWORD` of franroa/diana (`gh secret set ... -R franroa/diana`).
  3. A different key cannot update the installed app: back up Diana's profiles and layouts
     (`files/profiles/profiles.json`, `shared_prefs/OSC*.xml`, `shared_prefs/com.limelight.noirdebug_preferences.xml`
     via `adb shell run-as com.limelight.noirdebug`), uninstall Diana, install the new release and copy them back.
  4. Keep the keystore and its password somewhere safe: without them no update can be installed over it.

- [ ] **Scale the shipped layouts to the screen.** `app/src/main/assets/default_profiles/` holds the profiles
  and on-screen layouts exported from the Pixel 8 Pro; the layouts are in pixels of that screen, so on
  another screen (the Galaxy Tab A) the buttons are not in the right place. Store them as fractions of the
  screen, or scale them on the first start.
