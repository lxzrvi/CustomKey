# CustomKey ⌨️

**Your keyboard, your way.** A fast, private and fully customizable Android keyboard —
zero dependencies, zero analytics, 100% programmatic UI (no XML layouts, no AppCompat).

![Build](https://github.com/lxzrvi/CustomKey/actions/workflows/build.yml/badge.svg)

## Features

### Keyboard
- Premium neutral-grey design that follows the system **Light/Dark theme**
- Always stays **above the system navigation bar** (hide-arrow / keyboard-switcher bar)
  on both **gesture** and **3-button** navigation — edge-to-edge insets are handled on
  the IME window itself, plus an optional manual "extra bottom padding"
- **Shift** with active state, **double-tap → Caps Lock**, **auto-capitalization**
- **Hold backspace** to continuously delete
- Context-aware **Enter key**: ↵ / Done / Go / Search / Send / Next / Prev
- **Number & symbol page** (`?123`) + **extra symbols page** (`=\<`)
- **Full Android emoji page** (all categories + recents) via the 😀 key
- **Long-press keys** for accents, digits and punctuation (a → à á â ä ã å …)
- Key-press **preview popups** that appear **above the keyboard**, press animation
- **Per-key colors**, custom key width, keyboard background image, transparency

### Feedback settings (persisted on-device)
- Key sound with **5 premium synthesized sounds** (Tap · Pop · Click · Wood · Bubble)
  and volume control
- Vibration with strength control

### Keyboard Editor
- **Sticky exact live preview** at the top — it never scrolls away and updates instantly
- Add / edit / remove **custom keys** (any text, emoji, snippet…)
- **Batch (bunch) editing** — long-press keys to select many, then change width or delete
- **Move keys** left / right / up / down
- Per-key **color**, **width**, and **long-press action** (repeat · type a shortcut)
- Full **Android emoji picker** with recents
- **Key height**, **key spacing**, **keyboard transparency**, **extra bottom padding**
- **Keyboard background image** from your gallery
- Save instantly, or **reset to default**

### Setup
- Auto-detects whether CustomKey is **enabled** and currently **selected**
- Shows only the pending step, and a green "active" card when you're done

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
- Every screen, key, toggle and slider is drawn programmatically (iOS-style widgets)
- Key sounds are synthesized WAVs; emoji set is embedded in code
- Layouts are shared between the editor and the IME as JSON (`org.json`)

---
Made by **lxzrvi** · [Report an issue](../../issues)
