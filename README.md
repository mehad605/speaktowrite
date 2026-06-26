<div align="center">

<img src="assets/icon.png" alt="Speak to Write" width="120" />

# Speak to Write

**Private, on-device voice dictation for every app on your phone.**

Tap a floating mic button anywhere — browser, notes, chat — and your spoken words appear in the active text field. No cloud. No subscription. No permissions beyond what's necessary.

[![Release](https://img.shields.io/github/v/release/mehad605/speaktowrite?style=flat-square&color=22c55e&label=latest)](https://github.com/mehad605/speaktowrite/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)](https://github.com/mehad605/speaktowrite/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

---

## What is this?

Speak to Write is a solo-built Android app that puts a draggable floating mic button on top of every screen. Tap it, speak, tap again — your words land in whatever text field is focused. It works in any app, no copy-paste needed.

Everything happens on your phone using open-source offline speech models. Your voice never leaves the device unless you explicitly opt in to optional AI grammar cleanup (via your own Gemini API key).

---

## Install

**Grab the latest APK from [Releases](https://github.com/mehad605/speaktowrite/releases/latest).**

| ABI | Who needs it |
|-----|-------------|
| `arm64-v8a` | Almost all phones made after 2015 (start here) |
| `armeabi-v7a` | Old 32-bit phones |
| `x86_64` | Android emulators on Intel/AMD Macs and PCs |
| `x86` | Older Intel-based emulators |

Not sure? Install `arm64-v8a`. If it says "App not installed", try `armeabi-v7a`.

### Sideload steps

1. Download the `.apk` for your ABI
2. On your phone: **Settings → Security → Install unknown apps** → allow your browser/file manager
3. Open the downloaded APK and tap **Install**
4. Open the app and follow the two-step setup

---

## Setup

### Step 1 — Download a speech model

The app ships without a model to keep the APK small. On first launch, tap **Download** next to any model. Recommended starting point:

> **Moonshine Tiny** (~103 MB) — fast, accurate, English

A full list of available models with size and accuracy tradeoffs is in [Architecture → Model Management](docs/architecture.md#5-model-management-pipeline).

You can also import your own sherpa-onnx compatible model from local storage.

### Step 2 — Enable the Accessibility Service

Go to **Settings → Accessibility → Speak to Write** and toggle it on. This is what allows the floating mic button to appear and inject text.

> [!NOTE]
> **Why Accessibility?** See [Permissions & Privacy](docs/permissions.md) for the full explanation. Short version: it's the only Android API that can detect the focused text field in a third-party app and paste text into it without root.

After enabling, a green pill-shaped button appears on your screen. Drag it anywhere. Tap the **mic** side to start/stop recording. Tap the **arrow** side to switch models on the fly.

---

## Features

- 🎙️ **Tap-to-dictate floating button** — works in every app, sticks to the screen edge
- 📡 **Fully offline transcription** — audio never leaves your device
- 🔄 **Live model switching** — switch between installed models from the overlay
- ✨ **Optional AI polish** — send transcript to Gemini for grammar cleanup, translation, or reformatting
- 📝 **Custom prompt presets** — define your own post-processing instructions
- 📦 **Import/export settings** — back up your prompts and preferences as JSON
- 🔍 **Built-in diagnostics** — view and share service logs without a computer

---

## Permissions

Three permissions are requested. Here's why each one is strictly necessary:

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Capture your voice when the mic button is active |
| `BIND_ACCESSIBILITY_SERVICE` | Render the floating overlay, detect focused fields, inject text |
| `INTERNET` | Download speech models on setup; optional Gemini API calls |

Your audio is processed in a RAM buffer and discarded after transcription. No audio file is ever saved to disk or sent to a server.

→ **Full privacy details:** [docs/permissions.md](docs/permissions.md)

---

## AI Polish (optional)

Speak to Write can optionally send your raw transcript to Google's Gemini API for cleanup before pasting. This is **off by default** and requires you to enter your own free API key.

What it can do:
- Fix filler words ("um", "uh"), add punctuation, fix capitalisation
- Reformat spoken text into bullet points, formal prose, or code comments
- Translate between languages
- Apply any instruction you write yourself as a prompt preset

Get a free API key at [aistudio.google.com](https://aistudio.google.com).

---

## Build Locally

**Requirements:** Android Studio Meerkat or newer, JDK 17, Android SDK 36

```bash
git clone https://github.com/mehad605/speaktowrite.git
cd speaktowrite

# Debug build (unsigned, works for local testing)
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-<abi>-debug.apk
```

Install directly to a connected phone:
```bash
./gradlew installDebug
```

> [!NOTE]
> You do not need a signing keystore to build or run locally. The debug build is automatically signed with a debug key by Android tooling.

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Kotlin 2.3 | Coroutines, null safety, concise syntax |
| UI | Jetpack Compose + Material 3 | Declarative, no XML boilerplate |
| Overlay | Android `WindowManager` (Views) | Compose can't attach to `WindowManager` without an Activity host |
| Speech | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Multi-model, offline, Apache 2.0, official AAR |
| Networking | OkHttp 4 | Resumable downloads via `Range` headers |
| Archive | Apache Commons Compress | `.tar.bz2` support (JDK stdlib has none) |
| AI Polish | Gemini API (REST) | Low latency, free tier, optional |
| State | Kotlin `StateFlow` | No LiveData/RxJava dependency |
| Build | Gradle 9 + Version Catalog | Centralised dependency management |

→ **Full architecture breakdown with design decisions and alternatives:** [docs/architecture.md](docs/architecture.md)

---

## Documentation

| Doc | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | How every system component works, why it was chosen, and what alternatives were considered |
| [docs/how-it-works.md](docs/how-it-works.md) | End-to-end flow diagram and component walkthrough |
| [docs/permissions.md](docs/permissions.md) | Every permission explained, with privacy guarantees |

---

## Project Structure

```
app/src/main/java/com/mhm/speaktowrite/
├── MainActivity.kt                         # Entry point, edge-to-edge setup
├── SpeakToWriteAccessibilityService.kt     # Core service: overlay, recording, injection
├── ServiceLogger.kt                        # Persistent rotating log for diagnostics
├── models/
│   ├── TranscriberManager.kt               # Singleton: owns the loaded model
│   ├── LocalTranscriber.kt                 # sherpa-onnx wrapper
│   ├── ModelDownloader.kt                  # Resumable download + tar.bz2 extraction
│   ├── ModelImporter.kt                    # Import models from local storage
│   ├── GeminiClient.kt                     # Optional AI post-processing
│   ├── PostProcessor.kt                    # Prompt preset logic
│   └── SettingsManager.kt                  # SharedPreferences wrapper
├── ui/
│   ├── main/MainScreen.kt                  # Root Compose screen
│   ├── components/AuroraComponents.kt      # Design system components
│   ├── sections/                           # Setup, Model, AI Polish sections
│   └── dialogs/Dialogs.kt                  # All modal dialogs incl. Diagnostics
└── theme/                                  # Aurora colour palette + typography
```

---

## Contributing

This is a personal side project. Issues and PRs are welcome but responses may be slow.

If you find a bug, the most useful thing you can do is:
1. Open the app → scroll to the bottom → tap **⚙ View Diagnostics**
2. Tap **Share Log** and attach the file to your issue

---

## Licence

[Apache 2.0](LICENSE) — do whatever you want with it, just keep the attribution.

---

<div align="center">
<sub>Built with too much coffee by <a href="https://github.com/mehad605">mehad605</a></sub>
</div>
