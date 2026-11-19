# RAF Capsule OS

<p align="center">
  <strong>Dynamic Camera Cutout Capsule &amp; Interactive Status Overlay for Android 13–17</strong><br/>
  Built with Kotlin 2.x • Jetpack Compose • Material 3 Expressive • Navigation 3 • AGSL Shaders • Clean Architecture
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Min%20SDK-33%20(Android%2013)-3DDC84?logo=android&logoColor=white" alt="Min SDK 33" />
  <img src="https://img.shields.io/badge/Target%20SDK-37%20(Android%2017)-0078D7?logo=android&logoColor=white" alt="Target SDK 37" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3%20Expressive-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Languages-14%20Locales-9C27B0" alt="14 Languages" />
</p>

**RAF Capsule OS** transforms your phone's front camera cutout into a fluid, 1:1 hardware-aligned interactive capsule powered by VSYNC-coupled spring physics, real-time AGSL neuron glow shaders, and a pure-Kotlin state coordinator. It includes an isolated **Interactive Simulation** sandbox so you can preview every gesture, glow effect, and notification layout without touching system settings.

---

## Visual Showcase

### Live Capsule Interactions (Recorded on Pixel 9 Pro Simulation)

<table>
  <tr>
    <td align="center" width="50%">
      <img src="docs/media/media-player.gif" alt="Media Player, Visualizer & Synced Lyrics" width="100%" /><br/>
      <strong>Media Player, Audio Switcher &amp; Synced Lyrics</strong>
    </td>
    <td align="center" width="50%">
      <img src="docs/media/separated-bubble.gif" alt="Separated Status Bubble & Liquid Morph" width="100%" /><br/>
      <strong>Separated Status Bubble &amp; Liquid Morph</strong>
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="docs/media/notification-swipe.gif" alt="Scrollable Notifications & Swipe-to-Dismiss" width="100%" /><br/>
      <strong>Scrollable Notifications &amp; Swipe-to-Dismiss</strong>
    </td>
    <td align="center" width="50%">
      <img src="docs/media/now-brief.gif" alt="Now Brief Quick Hub" width="100%" /><br/>
      <strong>Now Brief Quick Hub</strong>
    </td>
  </tr>
  <tr>
    <td align="center" colspan="2">
      <img src="docs/media/incoming-call.gif" alt="Incoming Call Auto-Expand" width="50%" /><br/>
      <strong>Incoming Call Auto-Expand &amp; Neuron Border</strong>
    </td>
  </tr>
</table>

### App Screens & Configuration

<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/media/home-dashboard.png" alt="Home Dashboard & Live Capsule" width="100%" /><br/>
      <strong>Home Dashboard &amp; Live Overlay</strong>
    </td>
    <td align="center" width="33%">
      <img src="docs/media/customization-preview.png" alt="Live Customization & Cutout Preview" width="100%" /><br/>
      <strong>Live Customization &amp; Cutout Preview</strong>
    </td>
    <td align="center" width="33%">
      <img src="docs/media/app-settings-languages.png" alt="Dynamic Theme & 14 Languages" width="100%" /><br/>
      <strong>Dynamic Theme &amp; 14 Languages</strong>
    </td>
  </tr>
</table>

> **Tip:** Open [`CapsuleShowcasePreviews.kt`](presentation/src/main/kotlin/com/raf/capsuleos/presentation/preview/CapsuleShowcasePreviews.kt) in Android Studio to inspect all 8 live Jetpack Compose `@Preview` screens and capsule states directly in the editor.

---

## Core Features

### 3-State Dynamic Cutout Capsule
- **State 1 — Cutout Anchor Ring**: 1:1 hardware camera cutout ring with clockwise battery-level progress arc, charging particles, and automatic cutout calibration (`Left`, `Center`, `Right` + fine `dp` offset tuning).
- **State 2 — Horizontal Pill**: Compact asymmetric pill for active media, notifications, volume, charging, timers, Bluetooth connections, and periodic 30-second notification summaries (with a 1-second bell jiggle).
- **State 3 — Frosted Expanded Card**: Concentric display-corner rounded card with behind-window vertical blur, VSYNC spring physics, and 1:1 finger-tracked vertical scrub-to-expand/collapse.
- **AGSL Shaders**: Hardware-accelerated [`NeuralBorderShader`](presentation/src/main/kotlin/com/raf/capsuleos/presentation/shader/NeuralBorderShader.kt) and [`OuterExpansionAuraShader`](presentation/src/main/kotlin/com/raf/capsuleos/presentation/shader/OuterExpansionAuraShader.kt) with independent toggles for inner card glow, outer screen-frame glow, and persistent playback aura.

### Separated Status Bubble (Left or Right)
- Displays up to **9 active system indicators** in an independent circular satellite bubble next to the main capsule (customizable **Left** or **Right** placement, defaulting to **Left**):
  - Flashlight, Mobile Hotspot, Live Activity / Timer, Airplane Mode, Do Not Disturb, Battery Saver, VPN, Silent / Vibrate, and Data Saver.
- Swipe horizontally on the bubble to cycle active indicators (with dot pagination when 2+ states are active), or tap to smoothly merge and expand directly into its control card.

### Media Player & Live Audio
- Multi-session media carousel with album-art color extraction, animated waveform visualizer, and seekable timeline.
- **Synced Lyrics (`LRC`)**: Toggleable line-by-line lyrics synced to playback position when supported by the active media session.
- **Audio Output Switcher**: Launches Android's native blurred SystemUI volume/output panel directly from the expanded media card.
- **Independent Media Customization**: Multi-select expanded animations (`Card Border`, `Outer Frame`, `Album Card`), separate horizontal-pill glow style, and dedicated auto-collapse timeout (`10s–60s` or follow global).

### Messaging & Smart Notifications
- **Dynamic Tall Card & Inline Media**: Long messages automatically expand taller (~5–10 lines) and decode inline photos, animated GIFs, and animated WebP stickers natively via `ImageDecoder` / `AnimatedImageDrawable`.
- **Directional Scroll Indicators**: Top and bottom horizontal primary hairlines with inward primary-to-transparent gradients appear only when scrollable in that direction.
- **Inline Quick Reply & Swipe-to-Dismiss**: Reply inline via `RemoteInput`, or swipe horizontally left/right across the expanded notification card for an elastic rubber-band jiggle and smooth dismissal back into the camera cutout.

### Now Brief, System Alerts & Gestures
- **Now Brief Quick Hub**: Swipe down or long-hold the idle ring for live weather, battery, next alarm, flashlight toggle, volume/ringer controls, adaptive brightness slider, lock screen button, and tap-to-open notification shade.
- **Lock Screen & Quick Settings Integration**: Independently configure **Show on Lock Screen** and **Hide on QS Open** (collapsing cleanly to the non-touchable battery camera ring with zero unlock bounce).
- **Per-Card Gestures**: Configure global and per-card (`Idle Ring`, `Media`, `Flashlight`, `Notification`, `Volume`) Double-Tap and Long-Hold actions, including launching any installed app.
- **Material 3 Expressive & 14 Languages**: Dynamic Color or Monochrome themes (`System`, `Light`, `Dark`), tactile `HapticFeedbackType` feedback, adaptive multi-column `FlowRow` layouts, and searchable per-app localization across 14 languages synced with Android System App Info.

---

## Installation

### Prerequisites
- **Android 13+ (API 33–37)** device (tested on Pixel 9 Pro / Android 17).
- **JDK 21** and **Android SDK Platform-Tools (`adb`)**.
- USB Debugging enabled on the target device (`Settings → Developer options → USB debugging`).

### 1. Build & Install from Source

```bash
# Clone the repository
git clone https://github.com/RidhwanAF/RAF-Capsule.git
cd RAF-Capsule

# Run unit tests, Android Lint, and build the release APK
./gradlew :domain:test lintDebug assembleRelease

# Install onto a connected device via ADB
adb install -r app/build/outputs/apk/release/app-release.apk
```

*(For a debug build, run `./gradlew assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk`.)*

---

## Permissions & ADB Setup Guide

RAF Capsule OS uses Android's official system APIs (`AccessibilityService` overlay, `NotificationListenerService`, and optional settings bridges). You can grant permissions either inside the **Setup** card in-app or directly via **ADB**.

### Android 13+ Sideloaded APK Note (Restricted Settings)
If you install the APK directly via file manager instead of `adb install`, Android 13+ restricts **Accessibility** and **Notification Access** until you enable them:
1. Open **Settings → Apps → RAF Capsule OS**.
2. Tap the **⋮** menu in the top-right corner → **Allow restricted settings**.
3. Authenticate with biometrics/PIN, then enable Accessibility & Notification Listener.

*(Installing with `adb install` or granting permissions via `adb shell` below bypasses this manual step.)*

### Required & Optional Permissions Overview

| Permission / Service | Purpose | Required? | Grant Method |
|---|---|---|---|
| **Accessibility Overlay** (`CapsuleAccessibilityService`) | Renders the `TYPE_ACCESSIBILITY_OVERLAY` capsule above the status bar & lock screen with full gesture support | **Required** | In-app Setup or ADB |
| **Notification & Media Listener** (`CapsuleNotificationListenerService`) | Reads active `MediaSession` tracks, notifications, inline attachments, and live activities | **Required** | In-app Setup or ADB |
| **`WRITE_SECURE_SETTINGS`** | Hides system status bar icons while expanded & suppresses duplicate floating heads-up banners | Optional (Recommended) | **ADB only** |
| **`WRITE_SETTINGS`** | Enables the Brightness slider and Adaptive Brightness toggle in Now Brief | Optional | In-app Setup or ADB |
| **Do Not Disturb Policy** (`ACCESS_NOTIFICATION_POLICY`) | Allows cycling into full Silent mode from Now Brief | Optional | In-app Setup or ADB |
| **`BLUETOOTH_CONNECT`** | Detects connected Bluetooth headphones/accessories and reads peripheral battery level | Optional | In-app Setup or ADB |

### Individual ADB Commands

```bash
# 1. Grant WRITE_SECURE_SETTINGS (Status Bar icon hiding + Heads-up suppression)
adb shell pm grant com.raf.capsuleos android.permission.WRITE_SECURE_SETTINGS

# 2. Grant BLUETOOTH_CONNECT (Bluetooth connected alerts & battery level)
adb shell pm grant com.raf.capsuleos android.permission.BLUETOOTH_CONNECT

# 3. Allow WRITE_SETTINGS (Now Brief Brightness & Adaptive Brightness)
adb shell appops set com.raf.capsuleos WRITE_SETTINGS allow

# 4. Allow Do Not Disturb Policy Access (Silent ringer toggle)
adb shell cmd notification allow_dnd com.raf.capsuleos

# 5. Enable Notification & Media Listener Service
adb shell cmd notification allow_listener \
  com.raf.capsuleos/com.raf.capsuleos.data.media.CapsuleNotificationListenerService

# 6. Enable Accessibility Overlay Service (safely preserves existing enabled services)
EXISTING=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
SERVICE="com.raf.capsuleos/com.raf.capsuleos.presentation.capsule.service.CapsuleAccessibilityService"
if [ -z "$EXISTING" ] || [ "$EXISTING" = "null" ]; then
  adb shell settings put secure enabled_accessibility_services "$SERVICE"
elif ! echo "$EXISTING" | grep -q "$SERVICE"; then
  adb shell settings put secure enabled_accessibility_services "$EXISTING:$SERVICE"
fi
adb shell settings put secure accessibility_enabled 1
```

### One-Shot ADB Permission Setup Script

Run this right after `adb install -r ...` to configure all permissions and launch the app in one command:

```bash
adb shell pm grant com.raf.capsuleos android.permission.WRITE_SECURE_SETTINGS && \
adb shell pm grant com.raf.capsuleos android.permission.BLUETOOTH_CONNECT && \
adb shell appops set com.raf.capsuleos WRITE_SETTINGS allow && \
adb shell cmd notification allow_dnd com.raf.capsuleos && \
adb shell cmd notification allow_listener \
  com.raf.capsuleos/com.raf.capsuleos.data.media.CapsuleNotificationListenerService && \
adb shell am start -n com.raf.capsuleos/.MainActivity
```

---

## Architecture

RAF Capsule OS follows strict multi-module **Clean Architecture + MVVM**:

```text
CapsuleOS/
├── app/            # Application entry point, MainActivity, Hilt root, manifest & adaptive icons
├── domain/         # Pure Kotlin business logic (zero android.* dependencies)
│   ├── coordinator/  # IslandStateCoordinator priority stack, timers & gesture state machine
│   ├── model/        # CapsuleUiState, CapsuleContent, CapsuleEvent, CapsulePreferences, LrcLyricsParser
│   └── repository/   # Pure repository interfaces
├── data/           # Android platform implementations & Hilt bindings
│   ├── hardware/     # Torch, Volume, Brightness, Bluetooth repositories
│   ├── media/        # CapsuleNotificationListenerService & MediaSessionRepositoryImpl
│   ├── settings/     # DataStore Preferences persistence
│   ├── statusbar/    # StatusBarIconControllerImpl (WRITE_SECURE_SETTINGS)
│   └── system/       # SystemEventsRepositoryImpl (Battery, Calls, Hotspot, System toggles)
└── presentation/   # Jetpack Compose UI, Navigation 3, AGSL Shaders & Accessibility Overlay host
    ├── capsule/      # CapsuleAccessibilityService, CapsuleOverlayController, IslandCapsule & cards
    ├── shader/       # NeuralBorderShader & OuterExpansionAuraShader (AGSL)
    ├── setup/        # Home Dashboard & modular M3 Expressive configuration cards
    ├── customization/# Dedicated section screens with real-time interactive mockup
    ├── simulation/   # Isolated interactive sandbox (SimulationViewModel & CapsulePhoneMockup)
    ├── appsettings/  # Theme polarity, Dynamic Color / Monochrome & 14-language selector
    └── preview/      # Jetpack Compose @Preview showcase definitions
```

### Verification Commands

```bash
# Run all pure-Kotlin domain coordinator & lyrics parser unit tests
./gradlew :domain:test

# Run Android Lint across all modules (zero warnings policy)
./gradlew lintDebug

# Build minified R8 release APK
./gradlew assembleRelease
```
