# Architecture Deep Dive

> This document is for developers and curious minds who want to understand how Speak to Write is built under the hood — what choices were made, why, and what alternatives exist.

---

## Table of Contents

- [System Architecture Overview](#system-architecture-overview)
- [The Accessibility Service Layer](#1-the-accessibility-service-layer)
- [On-Device Speech Recognition](#2-on-device-speech-recognition)
- [Coroutine & State Architecture](#3-coroutine--state-architecture)
- [Optional AI Post-Processor](#4-optional-ai-post-processor)
- [Model Management Pipeline](#5-model-management-pipeline)
- [UI Stack](#6-ui-stack)
- [Robustness & Diagnostics](#7-robustness--diagnostics)
- [Design Decisions & Alternatives](#design-decisions--alternatives)

---

## System Architecture Overview

Speak to Write is built around a single core constraint: **the microphone never talks to the cloud.** Every architectural decision flows from that.

```
┌─────────────────────────────────────────────────────────┐
│                     USER DEVICE                         │
│                                                         │
│  ┌──────────┐    ┌──────────────────────────────────┐  │
│  │ Main App │    │  SpeakToWriteAccessibilityService │  │
│  │(Compose) │    │                                   │  │
│  │          │    │  ┌────────┐   ┌───────────────┐  │  │
│  │ Settings │    │  │AudioRec│──▶│LocalTranscriber│  │  │
│  │ Models   │    │  │16kHz   │   │(sherpa-onnx)  │  │  │
│  │ Prompts  │    │  └────────┘   └───────┬───────┘  │  │
│  └──────────┘    │                       │           │  │
│                  │          ┌────────────▼──────┐    │  │
│                  │          │  Raw Transcript   │    │  │
│                  │          └────────────┬──────┘    │  │
│                  │                       │           │  │
│                  │          ┌────────────▼──────┐    │  │
│                  │          │  GeminiClient     │    │  │
│                  │          │  (optional, API)  │    │  │
│                  │          └────────────┬──────┘    │  │
│                  │                       │           │  │
│                  │  ┌────────────────────▼──────┐    │  │
│                  │  │  ACTION_PASTE into focused │    │  │
│                  │  │  AccessibilityNodeInfo     │    │  │
│                  │  └───────────────────────────┘    │  │
│                  └──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 1. The Accessibility Service Layer

### What it does

`SpeakToWriteAccessibilityService` extends Android's `AccessibilityService`. It is the beating heart of the app — it runs as a long-lived system service and is responsible for:

1. Rendering the floating overlay via `WindowManager`
2. Tracking the currently focused, editable `AccessibilityNodeInfo`
3. Injecting final text via `ACTION_PASTE`

### Why `AccessibilityService` and not `SYSTEM_ALERT_WINDOW`?

Most floating-overlay apps (e.g. chat heads, floating calculators) use `SYSTEM_ALERT_WINDOW` permission with window type `TYPE_APPLICATION_OVERLAY`. This was considered but rejected because:

| Factor | `SYSTEM_ALERT_WINDOW` | `AccessibilityService` |
|---|---|---|
| Requires explicit user grant in Settings | ✅ Yes (extra friction) | ✅ Yes (different screen) |
| Can read focused input node | ❌ No | ✅ Yes |
| Can inject text without keyboard | ❌ No | ✅ Yes |
| Window type allowed | `TYPE_APPLICATION_OVERLAY` | `TYPE_ACCESSIBILITY_OVERLAY` (higher z-order) |
| Survives app being killed | ❌ No | ✅ Yes (system-managed) |

The ability to **detect the focused node and inject text without simulating keystrokes** is exclusive to the Accessibility API. There is no other Android API that can do this without root.

### Floating Overlay Implementation

The overlay is a `LinearLayout` inflated and added to `WindowManager` with:

```kotlin
WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    TYPE_ACCESSIBILITY_OVERLAY,
    FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH,
    PixelFormat.TRANSLUCENT
)
```

`FLAG_NOT_FOCUSABLE` ensures the overlay never steals keyboard focus from the app the user is typing in. `FLAG_WATCH_OUTSIDE_TOUCH` lets the service detect taps outside the overlay to collapse the model dropdown.

### Snap-to-Edge Drag Logic

The pill implements a drag-and-release system using `ACTION_DOWN`, `ACTION_MOVE`, and `ACTION_UP` touch events. On `ACTION_UP`, the button's X position is snapped to the nearest horizontal edge of the screen using a simple `lerp`-less calculation:

```kotlin
val snapX = if (currentX < screenWidth / 2) edgePadding
            else screenWidth - overlayWidth - edgePadding
```

### Zone-Aware Touch Areas

The pill is split into two independent touch zones without using nested `ViewGroup` click listeners, which would cause touch event conflicts with `WindowManager`'s `FLAG_NOT_TOUCH_MODAL`. Instead, `onTouchListener` on the root view dispatches based on the raw `event.x` position relative to the view's width.

### Service Lifecycle & Robustness

Android can kill the accessibility service process at any time — particularly on heavily customized OEM ROMs (ColorOS, MIUI, OneUI) that aggressively reclaim memory. The service handles this with:

- A `SupervisorJob` + `Dispatchers.Main` coroutine scope that is cancelled in `onDestroy()`
- 120ms debounced `showOverlay()`/`removeOverlay()` calls via a `Handler` to prevent `WindowManager` event floods (which trigger ANRs)
- Defensive `instance != null` checks inside `withEndAction` animation callbacks to prevent operations on destroyed instances
- A persistent rotating `ServiceLogger` that records every lifecycle event to `files/service_log.txt`

---

## 2. On-Device Speech Recognition

### Engine: sherpa-onnx

The transcription engine is [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — an ONNX Runtime-based speech recognition framework developed by the k2/Kaldi team. It was chosen over alternatives for the following reasons:

| Engine | On-device | Multi-model | Android AAR | License |
|---|---|---|---|---|
| **sherpa-onnx** | ✅ | ✅ (Whisper, Moonshine, Transducer, CTC) | ✅ | Apache 2.0 |
| Vosk | ✅ | ✅ | ✅ | Apache 2.0 |
| Whisper.cpp (JNI) | ✅ | ❌ (Whisper only) | ❌ (manual build) | MIT |
| Android SpeechRecognizer API | ✅ (on-device since API 31) | ❌ | ✅ (built-in) | Proprietary |
| Google Cloud Speech | ❌ | ✅ | ✅ | Commercial |

Android's built-in `SpeechRecognizer` was tested but rejected because:
- It requires internet on older API levels and has no offline guarantee
- It provides no control over model quality or language
- Real-time partial results are unreliable across OEMs

Vosk was a close contender but sherpa-onnx's support for **Moonshine** (a state-of-the-art tiny model from Useful Sensors) and its native `.tar.bz2` model distribution ecosystem made it the stronger choice.

### Supported Model Topologies

The `LocalTranscriber` dynamically detects the model type by inspecting which ONNX files are present in the model directory:

| Topology | Detection Heuristic | Models |
|---|---|---|
| **Moonshine** | `encode.onnx` + `cached_decode.onnx` present | Moonshine Tiny |
| **Whisper** | `whisper-encoder.onnx` present | Whisper Base |
| **Transducer** | `encoder.onnx` + `decoder.onnx` + `joiner.onnx` | Parakeet TDT |
| **CTC** | `model.onnx` only (no joiner) | Parakeet 110M, Zipformer |

### Audio Pipeline

```
Microphone
    │
    ▼
AudioRecord (16000 Hz, MONO, PCM_16BIT, ~4096 byte buffer)
    │
    ▼
ByteArrayOutputStream (RAM buffer — audio NEVER written to disk)
    │
    ▼
Short array → normalize to [-1.0, 1.0] FloatArray
    │
    ▼
sherpa-onnx OfflineRecognizer.decode(samples, sampleRate)
    │
    ▼
Raw transcript string
```

All audio is processed in a RAM `ByteArrayOutputStream` and immediately discarded after transcription. No audio file is ever written to internal storage or transmitted over the network.

---

## 3. Coroutine & State Architecture

### State Management

The app uses Kotlin `StateFlow` throughout — no LiveData, no RxJava. The `ViewModel`-equivalent state is held in `TranscriberManager`, a singleton scoped to the application process (not to the Activity lifecycle):

```
TranscriberManager (independent CoroutineScope)
├── modelState: StateFlow<ModelState>   ← loading / ready / error
├── loadError: StateFlow<String?>       ← surface errors to UI
└── transcriber: LocalTranscriber?      ← the loaded engine instance
```

`TranscriberManager` uses its **own** `CoroutineScope` (not the service's) deliberately: if the accessibility service is killed and restarted, the already-loaded model survives in memory and doesn't need to be reloaded. This is the primary reason cold-start after a service restart feels instant.

### Why not ViewModel?

`ViewModel` is scoped to the `Activity` or `NavBackStackEntry`. The transcription engine needs to outlive both — it must persist across screen rotations, the app being backgrounded, and the service being restarted. A manually-managed singleton with an explicit `CoroutineScope` is the correct tool here.

### Coroutine Scopes Summary

| Scope | Owner | Cancelled when |
|---|---|---|
| `serviceScope` | `AccessibilityService` | `onDestroy()` called |
| `TranscriberManager.scope` | Application process | Process is killed |
| `ModelDownloader` scope | `ViewModel` (via Compose) | Composable leaves composition |

---

## 4. Optional AI Post-Processor

When the user provides a Gemini API key, the raw transcript is optionally sent to `GeminiClient` before injection. This is a simple REST client built on OkHttp:

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
```

The user-defined **prompt preset** is used as the system instruction. Presets are JSON-serialized and stored in `SharedPreferences`. Example presets include:

- **Clean up** — fix filler words, add punctuation
- **Formal** — rewrite in formal register
- **Translate** — translate from spoken to target language
- **Code comments** — format as a code comment block

### Why Gemini and not a local LLM?

Running a grammar-correction LLM locally (e.g. a 1B parameter model) would require 800MB–2GB of RAM and 2–5 seconds of inference time on a mid-range phone. For a keyboard-replacement tool where latency is paramount, that trade-off is not acceptable. The Gemini API adds ~600ms of latency on a good connection and costs fractions of a cent per request on the free tier.

If you want a fully offline solution, disable AI Polish and use the raw transcript directly — it's the default.

---

## 5. Model Management Pipeline

### Download & Extraction

Models are distributed as `.tar.bz2` archives from the [k2-fsa/sherpa-onnx GitHub Releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models) CDN. The `ModelDownloader`:

1. Sends an HTTP `Range` request to support **resumable downloads** (if the device loses connection mid-download)
2. Streams bytes to `cacheDir/${model}.tar.bz2` (not `filesDir`, so Android can clear it under storage pressure)
3. Extracts the archive to `filesDir/models/${model}/` using Apache Commons Compress
4. Includes a **path traversal guard** on every archive entry:
   ```kotlin
   require(dest.canonicalPath.startsWith(outDir.canonicalPath)) {
       "Path traversal: ${entry.name}"
   }
   ```

### Available Models

| Model | Size | Speed | Accuracy | Languages |
|---|---|---|---|---|
| Parakeet 110M | ~100 MB | ⚡⚡⚡ Fastest | Good | English |
| Moonshine Tiny | ~103 MB | ⚡⚡⚡ Fast | Very Good | English |
| Whisper Base | ~199 MB | ⚡⚡ Medium | Very Good | English |
| Parakeet 0.6B | ~465 MB | ⚡ Slower | Best | English |
| Zipformer Bangla | ~83 MB | ⚡⚡⚡ Fast | Good | Bengali |

You can also **import a custom model** from local storage — any sherpa-onnx compatible model folder works.

---

## 6. UI Stack

### Jetpack Compose + Material 3

The entire UI is written in Jetpack Compose with a custom design system called **Aurora** — a dark, matte aesthetic built on a Zinc/Emerald palette. Key UI choices:

- **No Navigation Component** for single-screen apps: the main screen uses a `LazyColumn` with in-place dialog overlays instead of separate destinations
- **Navigation3** (alpha) is included for future multi-screen expansion
- **`WindowManager` overlay** is vanilla Android Views (not Compose) because Compose cannot be rendered into a `WindowManager` window without a `ComposeView` host Activity, and the service has no Activity

### Why not XML layouts?

Compose was chosen for the main app UI because it eliminates the `View`/`ViewModel`/`DataBinding` triangle and produces significantly less boilerplate for a settings-heavy UI with many conditional states. The overlay, however, uses XML because `WindowManager` pre-dates Compose and the integration story (`ComposeView` inside `WindowManager`) adds latency and memory overhead that is not acceptable for a persistent overlay.

---

## 7. Robustness & Diagnostics

### ServiceLogger

`ServiceLogger` is a rotating, persistent file logger that writes to `filesDir/service_log.txt`. It:
- Writes tagged, timestamped entries: `MM-dd HH:mm:ss.SSS I/Tag [thread]: message`
- Rotates at 200KB to keep storage usage bounded
- Survives service restarts (file persists in `filesDir`)

### DiagnosticsDialog

End-users can access the last 80 log lines from within the app via **⚙ View Diagnostics** at the bottom of the main screen. The dialog includes a **Share Log** button that uses Android's `FileProvider` + `Intent.ACTION_SEND` to share the raw log file via any installed app (email, messaging, etc.). This allows bug reports without ADB.

---

## Design Decisions & Alternatives

### Why an Accessibility Service instead of a keyboard (IME)?

A custom **Input Method Editor (IME)** was the first design considered. It was rejected because:

1. Users must **manually switch keyboard** to activate it — high friction
2. The IME has no access to the current window layout outside its own input view
3. You cannot overlay content on top of other apps from an IME
4. The accessibility service approach works **with the user's existing keyboard**, not instead of it

### Why not a Tile / Quick Settings panel?

Quick Settings tiles can trigger recording but cannot render a persistent overlay or inject text into an arbitrary focused field. They'd only work as a trigger mechanism, still requiring an accessibility service underneath — making them redundant.

### Why OkHttp instead of Retrofit?

Model downloads require fine-grained control over HTTP `Range` headers, streaming byte reads, and manual progress calculation. Retrofit's abstraction layer would need to be bypassed for all of these. OkHttp's `ResponseBody.byteStream()` provides exactly the right level of control.

### Why Apache Commons Compress instead of `java.util.zip`?

Java's standard library has no support for `.bz2` compression. The sherpa-onnx model archives use `.tar.bz2`. Apache Commons Compress is the de-facto standard for this on the JVM and is well-maintained (1.27.x as of writing).
