# CustomPhone 0.1.5

A native Android phone launcher and neural live wallpaper, adapted from [CustomTV](https://github.com/Nuzair46/tv-dotenv/tree/b60a5c38bc463aeede6c99897bbf1b71533d04c1). The current Home layout has a clock/date, an appearance button, and a translucent dock **without a border**. There is no branding header, favorites section, or All apps button. Swipe up to open the searchable app drawer.

The dock opens Phone, Messaging, Browser and the existing OnePlus Camera. Long-press an app for App info. Tap the upper-right appearance control, or long-press empty Home space, for icon style, wallpaper, system settings, and the default-home selector. Other app icons use their installed artwork, rendered monochrome where possible; common phone functions have original outline icons.

The launcher adds one package, `dev.nuzair.phonehome`. It does not remove or disable apps, tune device performance, replace the lock screen, read notifications, or change the camera/fingerprint patches. It has no network permission, analytics, accessibility service, or wake lock. Trebuchet remains available as a fallback and continues providing the ROM's system app-switching integration.

Tested on the OnePlus 8 / LineageOS 24.0 setup documented in the [main README](../README.md). The manifest declares API 30 minimum and targets API 35; other Android versions and devices have not been validated. There is no widget host, notification-badge implementation, or work-profile UI.

## Build

Use Linux or WSL, Python 3.10+, JDK 21, and existing official Android SDK platform/build tools. Obtain the SDK through [Android's official tools](https://developer.android.com/tools). Validation used the Android 35 extension 15 platform and Build Tools 35.0.0. The locally installed app was built using R8/D8 9.5.29; `--d8-jar` can select that compiler. Otherwise the script uses Build Tools' bundled D8.

Supply your own tool paths; there are no machine-specific paths in the source. Keep build output and signing material outside this checkout:

```sh
python3 launcher/scripts/build.py \
  --android-jar /path/to/android-sdk/platforms/android-35/android.jar \
  --build-tools /path/to/android-sdk/build-tools/35.0.0 \
  --java-home /path/to/jdk-21 \
  --work-dir ../op8-dotenv-build
```

The output is `../op8-dotenv-build/build/customphone.apk`, with a SHA-256 file beside it. The first build creates a local signing key and password under the work directory's `signing/` subdirectory. **Keep both private and backed up.** A fresh build workspace generates a different key and cannot update an existing installation signed with another key. Restore your original key/password pair when building an update; do not uninstall an existing app just to work around a signature mismatch.

The build checks the committed wallpaper hashes, compiles Java/resources, aligns the APK, signs it, and verifies the signature. It uses native Android APIs with no Gradle, WebView, Compose, or third-party runtime library. The public build tool was compile/sign tested separately; a fresh local signing key means its APK is not byte-identical to the owner's installed build.

## Install and select Home

Authorize USB debugging normally and explicitly select the intended device with `adb -s YOUR_DEVICE`. Record the current HOME component and theme settings privately before making changes. Do not copy identifiers, backups, or logs into this repository.

```sh
adb devices -l
adb -s YOUR_DEVICE shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s YOUR_DEVICE install -r ../op8-dotenv-build/build/customphone.apk
adb -s YOUR_DEVICE shell am start -n dev.nuzair.phonehome/.MainActivity
```

Test the launcher first, then choose **CustomPhone** in Android Settings > Apps > Default apps > Home app, or use:

```sh
adb -s YOUR_DEVICE shell cmd package set-home-activity --user 0 dev.nuzair.phonehome/.MainActivity
```

Select **Neural live wallpaper** from the launcher's Appearance menu. Android's normal wallpaper picker lets you apply it to Home, lock screen, or both. Keep a private copy of your current wallpaper if you want to restore it later. No root is required to install/select this launcher or wallpaper.

## Matching system theme

The phone uses dark mode, LineageOS's native pure-black setting, and a monochromatic Material palette. These are appearance changes only. Record the original values before applying anything:

```sh
adb -s YOUR_DEVICE shell cmd uimode night
adb -s YOUR_DEVICE shell settings --lineage get secure berry_black_theme
adb -s YOUR_DEVICE shell settings get secure theme_customization_overlay_packages
```

On the tested LineageOS build, the persistent pure-black setting is:

```sh
adb -s YOUR_DEVICE shell cmd uimode night yes
adb -s YOUR_DEVICE shell settings --lineage put secure berry_black_theme 1
```

The final palette settings are documented in [monochrome-theme.json](../config/monochrome-theme.json). Use the system wallpaper/style controls where available. If editing the secure JSON setting manually, merge those entries into your recorded configuration, preserving unrelated entries; do not blindly replace another device's font/icon/theme choices. The pure-black setting is LineageOS-specific and is not an instruction for stock OxygenOS.

## Wallpaper and provenance

The two committed binary assets are generated artwork, not phone captures: a silent 1080 x 2400, 30 fps, 24-second HEVC Main10 loop and a matching PNG fallback. The player releases when the wallpaper is no longer visible. A Canvas still is used only on playback failure, because drawing it into the video surface before playback prevents the video producer from connecting.

See [asset provenance and regeneration](assets.md). The assets are pinned in [PUBLIC_ASSETS.json](../PUBLIC_ASSETS.json); no APK, signing key, SDK archive, or device screenshot is committed.

## Verification and rollback

On the test phone, swipe-up/search, Home return by gesture, the OnePlus Camera dock shortcut, and wallpaper release/resume were checked. The launcher and wallpaper survived reboot; the owner confirmed touch/fingerprint unlock and sleep/wake afterward. The later layout-only updates removed favorites/branding/the app button and the dock border, and were visually checked without another reboot. The pure-black theme was changed to its native saved setting after the reboot check.

To switch back, use Appearance > Default home app and choose **Trebuchet**, or restore your own recorded HOME component with `cmd package set-home-activity`. Restore your recorded theme values; if a setting was originally `null`, delete that setting instead of writing the string `null`. Restore wallpaper through Android Wallpaper & style. Switching HOME does not require uninstalling either launcher or disabling any package.
