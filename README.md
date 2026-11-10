# <img src="icon.png" width="36" height="36" align="center" alt="Logo" /> Moonlight Android — Keyboard Controller Edition

[![Latest Release](https://img.shields.io/github/v/release/natelyt12/moonlight-android-keyboard-controller?label=download&sort=semver)](https://github.com/natelyt12/moonlight-android-keyboard-controller/releases/latest)

This repository is a customized fork of [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android) (originally created by [Cameron Gutman](https://github.com/cgutman) and the Moonlight team). It replaces the traditional gamepad controller system with a powerful, fully customizable **PojavLauncher-style on-screen virtual keyboard and mouse layer**, tailored specifically for PC gaming (MMOs, FPS, RPGs, RTS) and remote desktop productivity directly on a touchscreen.

> [!TIP]
> **Side-by-side installation**: This app uses its own application ID, so it can be **installed and run alongside the original Moonlight app** on the same Android device without conflicts or overwriting. Every core Moonlight connection and streaming feature remains **100% intact** — only the gamepad controller system is removed and replaced by the virtual keyboard and mouse mapping system.

---

## 📱 Screenshots

|                          In-game virtual overlay                           |                       Full PC keyboard overlay                        |
| :------------------------------------------------------------------------: | :-------------------------------------------------------------------: |
| ![In-game virtual overlay](docs/screenshots/01-ingame-virtual-overlay.jpg) | ![Full PC keyboard overlay](docs/screenshots/02-full-pc-keyboard.jpg) |

|                       Virtual key editor                        |                          Key behavior options                          |
| :-------------------------------------------------------------: | :--------------------------------------------------------------------: |
| ![Virtual key editor](docs/screenshots/03-key-editor-panel.jpg) | ![Key behavior options](docs/screenshots/04-key-behavior-settings.jpg) |

|          Layout menu & preview background           |
| :-------------------------------------------------: |
| ![Layout menu](docs/screenshots/05-layout-menu.jpg) |

> **Note:** The application does not come with the controller shown in the images.

---

## ✨ Features in this Fork

- **Customizable Virtual Keyboard Overlay**: Create and place on-screen keys anywhere on the display with custom size, text, opacity, and haptic feedback.
- **Visual Layout Editor**: Drag, drop, resize, and configure virtual keys in real time, using an in-game screenshot as the preview background.
- **Layout Profile Management**:
  - Create, duplicate, and switch between layout profiles tailored for different games.
  - **Import / Export** layouts seamlessly via JSON files or directly through the clipboard.
- **Advanced Key Behaviors**:
  - Single key presses, toggle locks (caps/run/crouch lock), and multi-key combos / macros.
  - Slide gestures between keys for fluid touchscreen movement.
- **Full PC Keyboard Overlay**: Quickly toggle a full-size PC keyboard during a stream for typing, cheats, and function keys (F1–F12).
- **Virtual Mouse & Scroll Control**:
  - Virtual mouse overlay with left, right, and middle click buttons.
  - Adjustable mouse sensitivity and two-finger scroll speed.
- **Direct Windows Multi-Touch**: Pass native multi-touch gestures directly to the Windows host when trackpad mode is disabled.
- **In-Stream Quick Menu**: A convenient floating overlay menu to switch layouts, toggle the mouse/keyboard layers, or adjust settings on the fly.

> [!NOTE]
> **Differences from upstream**: To streamline touch keyboard and mouse gameplay, legacy USB gamepad drivers and the default on-screen gamepad controls (analog sticks and D-pad) have been removed in this fork.

---

## 📥 Download & Install

1. Download the latest APK from the [**Releases**](https://github.com/natelyt12/moonlight-android-keyboard-controller/releases/latest) page.
2. Enable _Install unknown apps_ for your browser or file manager, then install the APK. The app shows up as **Moonlight KB** (package `com.natelyt12.moonlightkb`), so it does not replace the official Moonlight app.
3. Start Moonlight, pair with your PC running [Sunshine](https://github.com/LizardByte/Sunshine) or NVIDIA GameStream, and stream any app or game.

### Which APK should I pick?

| Build     | Use it when                                                                                                        |
| :-------- | :----------------------------------------------------------------------------------------------------------------- |
| `nonRoot` | Default choice for every device.                                                                                   |
| `root`    | Only for rooted devices running Android 7.1 (API 25) or older, where root is needed to capture system mouse input. |

---

## 🛠️ Building from Source

### Requirements

- [Android Studio](https://developer.android.com/studio) (or the Android command-line SDK) with **Android SDK Platform 37**, **Build-Tools 36.0.0**, and **NDK 29.0.14206865**
- JDK 17 or newer

### Setup

```bash
# Clone with the native streaming submodule
git clone --recursive https://github.com/natelyt12/moonlight-android-keyboard-controller.git
cd moonlight-android-keyboard-controller
```

- In the project root, create a `local.properties` file and point `sdk.dir` at your Android SDK (Android Studio does this automatically). The NDK is resolved from the `ndk` directory of that SDK (see `ndkVersion` in [app/build.gradle](app/build.gradle)).
- Build the debug APK:

```bash
./gradlew assembleNonRootDebug
```

The APK is written to `app/build/outputs/apk/nonRoot/debug/`.

### Release builds

Release builds are minified with R8 and must be signed with your own keystore. Create one with:

```bash
keytool -genkeypair -v -keystore release.keystore -alias moonlight -keyalg RSA -keysize 4096 -validity 10000
```

Then add these properties to `~/.gradle/gradle.properties` (environment variables with the same names also work):

```properties
MOONLIGHT_STORE_FILE=/absolute/path/to/release.keystore
MOONLIGHT_STORE_PASSWORD=your-store-password
MOONLIGHT_KEY_ALIAS=moonlight
MOONLIGHT_KEY_PASSWORD=your-key-password
```

And build:

```bash
./gradlew assembleNonRootRelease
```

The signed APK is written to `app/build/outputs/apk/nonRoot/release/`.

### Automated releases (GitHub Actions)

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds a signed `nonRoot` APK and attaches it to a GitHub Release whenever a `v*` tag is pushed. Configure these repository secrets first:

| Secret | Value |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64 of your keystore file, e.g. `base64 -w0 release.keystore` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |

Then publish a build:

```bash
git tag v1.0
git push origin v1.0
```

The workflow uploads `MoonlightKB-<version>-nonRoot.apk` plus `SHA256SUMS.txt` and verifies the APK signature before publishing.

---

## About Moonlight

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android lets you stream your full collection of games from your PC to your Android device, whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and an [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on the official [Discord server](https://moonlight-stream.org/discord) and help translate official Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Credits & Upstream Authors

All core streaming protocol and engine code are the work of the original **Moonlight GameStream** project:

- [Cameron Gutman](https://github.com/cgutman)
- [Diego Waxemberg](https://github.com/dwaxemberg)
- [Aaron Neyer](https://github.com/Aaronneyer)
- [Andrew Hennessy](https://github.com/yetanothername)

Moonlight was originally created by students at [Case Western Reserve University](http://case.edu) and started as a project at [MHacks](http://mhacks.org).

This fork is maintained at [natelyt12/moonlight-android-keyboard-controller](https://github.com/natelyt12/moonlight-android-keyboard-controller).
