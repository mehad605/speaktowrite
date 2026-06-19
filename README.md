<p align="center">
  <img src="assets/icon.png" alt="Speak to Write Logo" width="112" height="112" />
</p>

<h1 align="center">Speak to Write</h1>

<p align="center">
  <strong>Private, Offline Voice Dictation with AI-Powered Grammar Polish</strong>
</p>

<p align="center">
  <a href="../releases"><img src="https://img.shields.io/badge/Release-Latest-22C28E?style=flat-for-the-badge" alt="Latest Release" /></a>
  <img src="https://img.shields.io/badge/Platform-Android-38EF7D?style=flat-for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline-0E1F1C?style=flat-for-the-badge&labelColor=22C28E" alt="Privacy Status" />
</p>

---

## 🎙️ What is Speak to Write?

Tired of inaccurate system voice typing or sending your private voice recordings to cloud servers? **Speak to Write** is an Android utility that brings high-quality, completely offline voice-to-text dictation to your fingertips. 

Using the state-of-the-art ONNX runtime and offline models, Speak to Write runs 100% on-device. With a simple tap of a system-wide floating overlay button, you can dictate text in any app, have it automatically formatted or polished by optional Gemini AI assistance, and injected directly into your active input field.

---

## 🚀 Key Features

*   🌍 **Universal Text Injection:** Dictate anywhere! The accessibility-based text injector automatically pastes your transcribed text into any focused input box.
*   📴 **100% Offline Dictation:** Powered by `sherpa-onnx` and local speech recognition models (like Whisper and Moonshine) to ensure complete data sovereignty and latency-free transcription.
*   ✨ **Optional AI Polish:** Paste your Gemini API key to refine, format, clean up verbal stutters, translate, or adapt the tone of your speech with customizable prompt presets.
*   🔘 **Dual-Zone Floating Overlay:** A smart, draggable pill-shaped button overlay that snap-snaps to screen edges. Access the mic to record or the dropdown menu to hot-swap speech models instantly.
*   🎨 **Modern Aurora Design:** A stunning, glassmorphic dark-theme user interface built with Jetpack Compose.
*   📥 **Easy Configuration Sharing:** Export and import your prompt presets and API keys via a single JSON file.

---

## 📥 Installation & Downloads

Get the pre-compiled application package directly from our GitHub Releases portal:

➡️ **[Download the Latest APK on GitHub Releases](../releases)**

### How to Get Started:
1. Download and install the `speak2write` APK.
2. Grant **Record Audio** permission for the microphone.
3. Enable the **Accessibility Service** to display the floating overlay and allow text injection.
4. Download or import a voice transcription model inside the app, select it, and tap the floating microphone to start dictating!

---

## 📖 Documentation Portal

Explore the internal documentation to learn more about the technical details, privacy architecture, and development guidelines:

*   📘 **[How It Works](docs/how-it-works.md)** – Technical architecture, Accessibility Service overlay mechanism, offline speech processing, and text injection flow.
*   🛡️ **[Permissions & Privacy Guide](docs/permissions.md)** – Detailed transparency report explaining the required Android permissions and privacy policies.
