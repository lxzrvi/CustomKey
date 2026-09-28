# CustomKey 🔩⌨️

**Build your own keyboard.** CustomKey is a fully customizable Android IME —
every key, layout, color, sound and behavior is editable on-device.
No ads, no internet, no tracking: zero dependencies, zero permissions beyond vibration.

## Features

### ✏️ Keyboard Editor Pro (v1.4)
- **Tabbed live editor** — Key · Selected · All Keys · Keyboard · Emoji · Toolbar · Sound · Vibration · Layout — every change updates the interactive preview instantly
- **Live preview** at the top — tap any key for a floating action box (Edit This / Select / Move / Cancel), **hold & drag** to move it anywhere; dropping a selected key moves the whole group
- **Per-key background**: solid color or **gradient** (angle + second color) or **imported image** (PNG / JPG / WebP) with zoom, opacity, blur
- **Per-key shape**: corner radius, borders (color + thickness + *per side*), drop shadow (soft/hard, angle, distance, blur), **glow**, **inner shadow**, whole-key rotation & scale, transparency, padding
- **Per-key text**: color, size, bold, italic, position (center / top / bottom / left / right), custom `.ttf`/`.otf` fonts, letter spacing, rotation, opacity, text shadow (color / blur / X / Y)
- **Per-key feedback**: any of the 10 sound presets or an **imported sound** (MP3 / WAV / OGG), plus per-key vibration strength
- **Pressed state**: custom pressed color + pressed scale
- **Batch editing** — multi-select (or *All Keys*) and change only the properties you touch; every key keeps its actions, sounds and other settings
- **Keyboard background**: color, gradient, image + blur + readability overlay, border, corner radius, transparency
- **Emoji grid controls**: size, columns, row height, spacing — with a live mini grid preview
- **Optional toolbar** (off by default): cursor keys, copy / cut / paste, select-all, emoji, next-field, hide — pick the buttons, height, icon size & colors
- **Layout tab**: key height, key gap, row gap, paddings, keyboard width %, row alignment, number row, +row / −row, trash strip, reset
- **Custom assets** — import fonts, images and sounds via the system picker; files are validated and stored app-privately (rename / delete / preview)
- **Add new keys** with position choice (left / right / above / below / end) — letters, function keys, emoji, clipboard keys…
- **Trash strip** — deleted keys rest at the bottom until you restore them
- **Presets** — save/load/delete whole keyboard setups (saving never touches your current layout)
- **Unsaved-changes guard** — Reset / Cancel / Preset / Apply in a fixed bottom bar; Cancel restores everything (layout + all settings) to how it was when you opened the editor

### ⌨️ Typing
- Smart shift (auto-capitalization, double-tap ⇧ for caps lock)
- **10 premium synthesized key sounds** (Tap · Pop · Click · Wood · Bubble · Thock · Snap · Mellow · Crystal · Feather) with smooth attack/fade — no harsh chirps — plus a **custom sound** slot (pitch + length)
- Adjustable vibration strength
- **Swipe-to-choose long-press popups**, customizable press **preview popup** (color, size, radius, linger) and **press zoom**
- **Cursor & clipboard page**: copy / cut / paste / select-all, selection boundary keys (start ⇤ / end ⇥), arrow keys (hold = fast move), next-field finder
- **Space trackpad** — long-press space and slide to move the cursor (optional Select mode), tap to exit; 8 quick cursor buttons (arrows, select left/right, jump to start/end) on the trackpad overlay
- Emoji keyboard with categories + recents
- Gesture & 3-button navigation bar safe — the keyboard always sits above the system bar

### 🎨 App
- Clean iOS-style grey UI, light/dark, edge-to-edge safe
- Start page: setup only until CustomKey is active, then straight to the test field
- Keyboard options (trackpad toggle) on the start page; everything else lives in the editor

## Development

- **Stack**: Kotlin, framework APIs only — *no* AndroidX/appcompat/Gson; UI is 100 % programmatic
- **Layout format**: JSON via `org.json` (see `Layout.kt`)
- **Sounds**: synthesized 16-bit PCM WAV at runtime (`KeySounds.kt`), played through `SoundPool`
- **Build**: Android Gradle Plugin 8.11.1, Kotlin 2.1.20, Gradle 8.13, Java 17, `minSdk 26` / `targetSdk 35`
- **CI**: GitHub Actions (`.github/workflows/build.yml`) builds debug + release APKs and uploads them as artifacts. A demo release keystore is committed so CI can sign out of the box — replace it (env vars `CK_STORE_PASSWORD`, `CK_KEY_ALIAS`, `CK_KEY_PASSWORD`) for store uploads.

## Releases

Grab the latest signed APK from [GitHub Releases](https://github.com/lxzrvi/CustomKey/releases).

---

Your keyboard, your rules.
