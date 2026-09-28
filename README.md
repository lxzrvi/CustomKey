# CustomKey 🔩⌨️

**Build your own keyboard.** CustomKey is a fully customizable Android IME —
every key, layout, color, sound and behavior is editable on-device.
No ads, no internet, no tracking: zero dependencies, zero permissions beyond vibration.

## Features

### ✏️ Keyboard Editor
- **Live preview** at the top — tap any key to edit it, **hold & drag** to move it anywhere (drop on *Trash* to delete)
- **Per-key styling**: corner radius, transparency, key color, borders (color + thickness + *per side*: top / right / bottom / left), drop shadows (soft/hard, angle, distance, blur)
- **Per-key text**: color, size, bold, italic, position (center / top / bottom / left / right), custom `.ttf` fonts
- **Key sizing**: per-key width and height, global key height / gap
- **Long-press actions**: alternate characters (shown as small hint chars, fully editable), long-press output, repeat-on-hold
- **Add new keys** with position choice (left / right / above / below / end) — letters, function keys, emoji, clipboard keys…
- **Multi-select** keys and style/move/delete them together
- **Trash strip** — deleted keys rest at the bottom until you restore them
- **Presets** — save/load/delete whole keyboard setups (saving never touches your current layout)
- **Background image** with adjustable blur, keyboard transparency, extra bottom padding

### ⌨️ Typing
- Smart shift (auto-capitalization, double-tap ⇧ for caps lock)
- **10 premium synthesized key sounds** (Tap · Pop · Click · Wood · Bubble · Thock · Snap · Mellow · Crystal · Feather) with smooth attack/fade — no harsh chirps — plus a **custom sound** slot (pitch + length)
- Adjustable vibration strength
- **Swipe-to-choose long-press popups**, customizable press **preview popup** (color, size, radius, linger) and **press zoom**
- **Cursor & clipboard page**: copy / cut / paste / select-all, selection boundary keys (start ⇤ / end ⇥), arrow keys (hold = fast move), next-field finder
- **Space trackpad** — long-press space and slide to move the cursor (optional Select mode), tap to exit
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
