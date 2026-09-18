# Side Rotate

Side Rotate is an Android utility application designed to provide reliable, on-demand screen rotation without requiring system auto-rotate to remain enabled.

When auto-rotate is locked and the device is physically tilted sideways, Side Rotate renders an unobtrusive floating rotation button at the bottom corner of the display. Tapping this button instantly rotates the screen orientation while keeping auto-rotate locked.

---

## The Problem Solved

On stock Android, a small rotation suggestion button appears in the navigation bar when the phone is tilted. However, on various manufacturer skins, this functionality is often broken or missing:

1. **Vivo Funtouch OS / OriginOS:** When full-screen navigation gestures are enabled, the navigation bar is completely hidden, preventing the native rotation suggestion button from appearing.
2. **Tecno HiOS / Infinix XOS:** Aggressive memory optimization frequently disables background orientation listeners, and gesture navigation suppresses rotation controls.
3. **Samsung One UI:** Users frequently want a quick, thumb-accessible corner button that remains consistent across all apps and home screens.

Side Rotate solves this by running an efficient foreground orientation monitor and presenting an overlay button that works regardless of your navigation method.

---

## Key Features

- **Rotation Lock Preserved:** Rotates the display system-wide by writing directly to system user rotation settings, keeping auto-rotate turned off.
- **Dual Rotation Engines:**
  - *Direct System Rotation (Primary):* Immediately updates the system orientation setting.
  - *Auto-Rotate Quick Pulse (Fallback):* Momentarily engages sensor orientation for devices with restricted settings access.
- **Zero Battery Consumption on Sleep:** Automatically suspends the accelerometer orientation listener whenever the display is turned off or locked, consuming zero extra battery in pockets or while idle.
- **Samsung One UI Aesthetics:** Features a clean, circular floating button with rounded corner geometry, smooth entrance/exit animations, and tactile haptic feedback.
- **Android 13+ Themed Adaptive Icons:** Includes a two-layer adaptive launcher icon with a dedicated monochrome layer that dynamically matches system wallpaper palettes on Android 13, 14, and 15.
- **Built-in GitHub Auto-Updater:** Checks for new releases directly from GitHub, downloads APK updates with real-time progress, and prompts the native Android package installer.
- **Customizable Interface:**
  - Corner position selection (Bottom-Left or Bottom-Right).
  - Button size adjustment (48dp to 72dp).
  - Auto-dismiss timeout configuration (2s to 8s).
  - Haptic feedback toggle.
  - Optional 180-degree upside-down rotation support.

---

## Required Permissions

To function across all apps and control display rotation, Side Rotate requires the following permissions:

| Permission | Purpose |
| :--- | :--- |
| **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)** | Allows rendering the floating rotation button above third-party applications. |
| **Modify System Settings (`WRITE_SETTINGS`)** | Allows updating the system orientation value without toggling auto-rotate on. |
| **Ignore Battery Optimizations** | Prevents aggressive OEM background cleaners from killing the orientation listener. |
| **Post Notifications (`POST_NOTIFICATIONS`)** | Required on Android 13+ for persistent foreground service execution. |
| **Internet (`INTERNET`)** | Used exclusively for querying GitHub releases and downloading app updates. |
| **Request Install Packages (`REQUEST_INSTALL_PACKAGES`)** | Allows in-app installation of downloaded APK updates. |

---

## Device-Specific Setup Instructions

### Vivo (Funtouch OS / OriginOS)
1. Open **Settings** > **Apps** > **Special App Access** > **Autostart** and enable Side Rotate.
2. Open **Settings** > **Battery** > **Background Power Consumption Management**, locate Side Rotate, and choose **High background power consumption**.
3. In Side Rotate, ensure **Display Over Other Apps** and **Modify System Settings** are granted.

### Tecno / Infinix (HiOS / XOS)
1. Open **Phone Master** > **Auto-start management** and enable Side Rotate.
2. Ensure Battery Optimization is set to **Unrestricted**.

### Samsung (One UI)
1. Open **Settings** > **Battery** > **Background usage limits** > **Never sleeping apps** and add Side Rotate.

---

## Installation

### Method 1: Download from GitHub Releases
1. Navigate to the [Releases](https://github.com/code4nigel/SideRotate/releases) tab.
2. Download the latest `SideRotate_vX.X.X.apk`.
3. Open the APK on your Android device and confirm installation.

### Method 2: Install via ADB
```bash
adb install -r version/SideRotate_v1.0.1.apk
```

---

## Development and Release Automation

### Building from Source
Ensure Android SDK and Java 17+ are installed, then run:

```bash
./gradlew assembleDebug
```

### Automated Versioning and Release
A Python release script is included to automate version bumps, compiling, and artifact management:

```bash
python release.py
```

To specify an explicit version number:
```bash
python release.py 1.0.2
```

The script will:
1. Increment the `versionCode` and update `versionName` in `app/build.gradle.kts`.
2. Compile the debug APK via Gradle.
3. Archive the generated binary into the `version/` directory with the format `SideRotate_v<version>.apk`.
4. Commit changes and create a Git release tag.

---

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0). See the [LICENSE](LICENSE) file for details.

---

Built with ❤️ by [nigelweb](https://github.com/code4nigel)
