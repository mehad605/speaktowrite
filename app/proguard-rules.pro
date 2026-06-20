# ─────────────────────────────────────────────────────────────────────────────
# ProGuard / R8 keep rules for Speak to Write
# ─────────────────────────────────────────────────────────────────────────────

# Keep JNI classes for sherpa-onnx since they are called from C++ native code
-keep class com.k2fsa.sherpa.onnx.** { *; }
