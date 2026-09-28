# CustomKey ⌨️

**Your keyboard, your way.** A fast, private and fully customizable Android keyboard —
zero dependencies, zero analytics, 100% programmatic UI (no XML layouts, no AppCompat).

![Build](https://github.com/lxzrvi/CustomKey/actions/workflows/build.yml/badge.svg)

## Features

### Keyboard
- Modern rounded-key design that follows the system **Light/Dark theme** automatically
- Always sits **above the Android navigation bar** — the bar (hide-arrow / keyboard
  switcher) stays system-controlled, on both **gesture** and **3-button** navigation
- **Shift** with active state, **double-tap for Caps Lock**, and **auto-capitalization**
  at sentence starts and in name/all-caps fields
- **Hold backspace** to continuously delete
- Context-aware **Enter key**: ↵ / Done / Go / Search / Send / Next / Prev
- Dedicated **number & symbol page** (`?123`) plus an **extra symbols page** (`=\<`)
- **Long-press keys** for accents, digits and punctuation (a → à á â ä ã å, . → ! ? : ; …)
- Key-press **preview popups** and press animation

### Feedback settings (persisted on-device)
- Key sound on/off with **volume control**
- Vibration on/off with **strength control**

### Keyboard Editor
Edit the whole letters layout from the app — no recompile needed:
- Add / edit / remove **custom keys** (any text, email, snippet…)
- Change **key width**, **keyboard height** and **key spacing**
- Add / remove rows
- Save instantly, or **reset to default**

### Setup
- Auto-detects whether CustomKey is **enabled** and currently **selected**
- Shows only the pending step, and a green “active” card when you're done

### Privacy
- **No internet permission. No analytics. No tracking.** Nothing you type ever leaves your device.
- Layout & settings live in local SharedPreferences only.

## Install
1. Download an APK from **Actions → latest run → Artifacts** (or build it yourself)
2. Open CustomKey → **Enable CustomKey**
3. Tap **Select CustomKey** and choose CustomKey
4. Type anywhere 🎉

## Build it yourself
Requires JDK 17 and Gradle 8.13+:

```bash
gradle :app:assembleDebug        # debug APK
gradle :app:assembleRelease      # signed release APK (uses release.keystore)
```

APKs land in `app/build/outputs/apk/`.

CI builds both APKs on every push to `main` and on `v*` tags — see
[Actions](../../actions).

## Signing
The repo includes a **demo keystore** (`release.keystore`, alias & password `customkey`)
so CI can produce installable signed APKs immediately. Before publishing to any store,
generate your own key:

```bash
keytool -genkeypair -v -keystore release.keystore -alias customkey \
  -keyalg RSA -keysize 2048 -validity 10950
```

and set `CK_STORE_PASSWORD`, `CK_KEY_ALIAS`, `CK_KEY_PASSWORD` in your environment or
CI secrets. **Keep your real keystore + passwords private.**

## Tech notes
- Kotlin, `InputMethodService`, no external libraries at all
- Every screen and every key is drawn programmatically
- Layouts are shared between the editor and the IME as JSON (`org.json`)

---
Made by **lxzrvi** · [Report an issue](../../issues)
