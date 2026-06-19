# Permissions & Privacy Transparency Guide

**Speak to Write** is built with a privacy-first, local-first philosophy. We believe you should have absolute control over your voice data. This document outlines why specific permissions are requested and how your privacy is protected.

---

## 🔑 Permissions Breakdown

### 1. Record Audio (`android.permission.RECORD_AUDIO`)
*   **Purpose:** Required to capture your voice dictation when you tap the floating mic button.
*   **Safety Guarantee:** The microphone is only active while the overlay button is in the red `RECORDING` state. Once stopped, the audio device is immediately released. The app **never** records in the background or listens passively.

### 2. Accessibility Service (`android.permission.BIND_ACCESSIBILITY_SERVICE`)
*   **Purpose:** The accessibility API is used for three essential functions:
    1.  **Overlay Rendering:** Drawing the floating pill-shaped button on top of other applications.
    2.  **Focus Detection:** Finding the active input cursor when you finish dictating.
    3.  **Automatic Injection:** Pasting the transcribed text directly into the active text field.
*   **Safety Guarantee:** The service only monitors `typeViewFocused` events to keep track of the current input target. It does not monitor keyboard input, passwords, or track screen changes across other apps.

### 3. Internet Access (`android.permission.INTERNET`)
*   **Purpose:** Used to download offline speech models during setup and to make API calls to Google's Gemini endpoint (only if AI Polish/cleanup is enabled by the user).
*   **Safety Guarantee:** No audio or transcription data is sent over the internet for standard dictation. If AI Polish is disabled, all text is generated entirely on your device using the offline ONNX engine.

---

## 🔒 Privacy Controls

*   ❌ **Zero-Cloud Audio Processing:** Audio files are recorded directly to a RAM buffer (`ByteArrayOutputStream`) and processed locally. Your voice recording never touches a server.
*   🔑 **Secure API Key Storage:** Your Gemini API key is stored securely on your device inside standard Android private `SharedPreferences`.
*   📦 **Local Configurations:** User-defined prompts, presets, and model settings are kept local. You can export or import these settings manually via JSON configurations.
*   🔍 **Verify Network Usage:** You can inspect network usage at any time in Android settings (Settings > Apps > Speak to Write > Mobile Data) to confirm that no background upload takes place.
