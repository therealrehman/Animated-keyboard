# ChromaTap â€” Animated Android Keyboard (IME)

A custom Android keyboard app built natively in Kotlin, featuring a signature multi-color "ripple" animation on every key press, full Urdu (Roman phonetic) typing support, and a clean dark UI. Built as a standalone Input Method Service â€” works system-wide in any app once enabled.

---

## âœ¨ Key Features

- **Signature press animation** â€” every keystroke triggers a smooth, full-width radial gradient ripple (White â†’ Pink â†’ Orange), giving the keyboard a distinctive, premium feel that's instantly recognizable and different from stock keyboards.
- **Roman Urdu â†’ Urdu transliteration** â€” a built-in phonetic engine lets users type Roman/English letters (e.g. "sh", "kh", "gh") and have them automatically converted to native Urdu script in real time. Toggleable on/off.
- **3 complete keyboard layouts** â€” Letters (QWERTY), Numbers/Symbols, and an Extended Symbols page (currency signs, math symbols, special characters) â€” all sharing a consistent visual structure.
- **Procedurally generated key-click sound** â€” a short, pleasant "tick" tone is synthesized in code (no external audio assets, zero licensing concerns), toggleable on/off.
- **Haptic feedback** â€” vibration on key press, toggleable on/off.
- **Caps Lock support** â€” long-press Shift to lock capitals, standard tap for one-shot Shift.
- **Backspace auto-repeat** â€” long-press Backspace to continuously delete.
- **Persisted user preferences** â€” all toggles (sound, vibration, Urdu mode) are saved via Android's SharedPreferences and survive app/device restarts.
- **Lightweight, dependency-light codebase** â€” built with a custom Canvas-based renderer (no Jetpack Compose overhead), keeping the APK small and startup fast.

## ðŸ—ï¸ Tech Stack

- **Language:** 100% Kotlin
- **UI:** Custom `View` + `Canvas` rendering (not Jetpack Compose) â€” full control over every pixel of the animation
- **Architecture:** Android `InputMethodService` (the standard framework for building system keyboards)
- **Persistence:** Android SharedPreferences
- **Build system:** Gradle (Kotlin DSL/Groovy), with a working **GitHub Actions CI workflow** included â€” every push automatically builds a debug APK, so you can verify the build status at a glance without setting up a local environment first.
- **Min SDK:** 24 (Android 7.0) â€” **Target SDK:** 34 (Android 14)

## ðŸ“‚ What's Included

- Full Kotlin source code (`KeyboardView.kt`, `AnimatedKeyboardIME.kt`, `KeyboardSettings.kt`, `KeySoundEngine.kt`, `UrduTransliterationEngine.kt`, `AnimationEngine.kt`, `KeyModel.kt`)
- Complete Gradle build configuration
- GitHub Actions workflow for automated APK builds
- AndroidManifest with the IME service properly registered

## ðŸš€ Getting Started

1. Clone the repo and open it in Android Studio (or build via the included GitHub Actions workflow).
2. Build and install the debug APK.
3. On the device: **Settings â†’ System â†’ Languages & Input â†’ On-screen keyboard â†’ Manage keyboards** â†’ enable this keyboard.
4. Switch to it from any text field's keyboard switcher icon.

> **Note:** This version is a focused keyboard engine â€” it does not yet include a launcher app icon or in-app settings screen. All current toggles (sound/vibration/Urdu mode) are wired into the code via `KeyboardSettings` and persist correctly, but are not yet exposed through a visual settings UI. This is a clean, well-isolated starting point for adding one.

## ðŸ›£ï¸ Suggested Next Steps for a New Owner

- Add a launcher Activity + in-app settings screen (the settings data layer already exists and is ready to be wired to UI controls)
- Add more keyboard layouts (AZERTY, QWERTZ, etc.) â€” the layout system is already structured as simple, swappable key-grids
- Add a user-facing theme/color picker (the animation's colors are centralized and easy to make configurable)
- Publish to Google Play

## ðŸ“œ License / Transfer

Full source code ownership and IP transfer upon sale. No third-party paid licenses, APIs, or subscriptions are used anywhere in this codebase â€” everything (including the click sound) is generated in-code with zero external dependencies or licensing risk.

---

*Built as a solo project exploring custom Android IME development, animation systems, and Urdu localization.*
