package com.mehad.speaktowrite

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.view.accessibility.AccessibilityNodeInfo
import com.mehad.speaktowrite.models.TranscriberManager
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs

class SpeakToWriteAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "SpeakToWrite"
        private const val SAMPLE_RATE = 16000
        private const val BTN_DP = 56
        private const val PAD_DP = 14
        private const val MARGIN_DP = 16
        private const val TAP_THRESHOLD_DP = 10

        private const val COLOR_IDLE = 0xFF388E3C.toInt()
        private const val COLOR_RECORDING = 0xFFE53935.toInt()
        private const val COLOR_BUSY = 0xFF555555.toInt()

        var instance: SpeakToWriteAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, SpeakToWriteAccessibilityService::class.java)
            val expectedString = componentName.flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(expectedString, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private enum class State { IDLE, RECORDING, TRANSCRIBING }
    private var state = State.IDLE

    private var overlayView: FrameLayout? = null
    private var button: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var audioRecord: AudioRecord? = null
    private var pcmStream: ByteArrayOutputStream? = null
    private val handler = Handler(Looper.getMainLooper())

    private val dp get() = resources.displayMetrics.density
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TranscriberManager.init(this)
        showOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val buttonSize = (BTN_DP * dp).toInt()
        val pad = (PAD_DP * dp).toInt()
        val margin = (MARGIN_DP * dp).toInt()

        val img = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic) 
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            background = circle(COLOR_IDLE)
            elevation = 8 * dp 
        }

        val overlay = FrameLayout(this).apply {
            addView(img, FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER))
        }

        val params = WindowManager.LayoutParams(
            buttonSize, buttonSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - buttonSize - margin
            y = screenH / 2 - buttonSize / 2
        }

        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f
        var isDragging = false

        overlay.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = ev.rawX
                    touchY = ev.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - touchX
                    val dy = ev.rawY - touchY
                    if (abs(dx) > TAP_THRESHOLD_DP * dp || abs(dy) > TAP_THRESHOLD_DP * dp) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        wm.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging && abs(ev.rawX - touchX) < TAP_THRESHOLD_DP * dp && abs(ev.rawY - touchY) < TAP_THRESHOLD_DP * dp) {
                        onTap()
                    } else {
                        // Animate snap to edge
                        val targetX = if (params.x + buttonSize / 2 > screenW / 2) {
                            screenW - buttonSize - margin
                        } else {
                            margin
                        }
                        animateSnap(params.x, targetX, params, wm, v)
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(overlay, params)
        overlayView = overlay
        button = img
        layoutParams = params
    }

    private fun animateSnap(fromX: Int, toX: Int, params: WindowManager.LayoutParams, wm: WindowManager, view: View) {
        val animator = ValueAnimator.ofInt(fromX, toX)
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                // View might be detached
            }
        }
        animator.start()
    }

    private fun removeOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
        button = null
        layoutParams = null
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun setAppearance(color: Int) {
        handler.post { button?.background = circle(color) }
    }

    private fun startPulse() {
        button?.let {
            it.animate().alpha(0.5f).setDuration(600).withEndAction {
                it.animate().alpha(1f).setDuration(600).withEndAction {
                    if (state == State.RECORDING) startPulse()
                }.start()
            }.start()
        }
    }

    private fun stopPulse() {
        button?.animate()?.cancel()
        button?.alpha = 1f
    }

    private fun onTap() {
        when (state) {
            State.IDLE -> startRecording()
            State.RECORDING -> stopAndTranscribe()
            State.TRANSCRIBING -> {
                toast("Please wait, transcribing...")
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Please grant audio permission in the app first")
            return
        }
        
        if (TranscriberManager.isLoading.value) {
            toast("Model is loading, please wait...")
            return
        }
        
        if (TranscriberManager.transcriber == null) {
            toast("Please select a model in the app first")
            return
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: SecurityException) {
            toast("Audio permission denied")
            return
        }

        pcmStream = ByteArrayOutputStream()
        audioRecord?.startRecording()
        state = State.RECORDING
        setAppearance(COLOR_RECORDING)
        startPulse()

        thread {
            val buf = ByteArray(bufSize)
            while (state == State.RECORDING) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: break
                if (n > 0) pcmStream?.write(buf, 0, n)
            }
        }
    }

    private fun stopAndTranscribe() {
        state = State.TRANSCRIBING
        stopPulse()
        setAppearance(COLOR_BUSY)

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcm = pcmStream?.toByteArray() ?: ByteArray(0)
        pcmStream = null

        if (pcm.isEmpty()) {
            toast("No audio captured")
            resetState()
            return
        }

        thread {
            val floatArray = FloatArray(pcm.size / 2)
            var i = 0
            while (i < pcm.size - 1) {
                val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                floatArray[i / 2] = sample.toShort().toFloat() / 32768f
                i += 2
            }
            
            val text = TranscriberManager.transcriber?.transcribe(floatArray, SAMPLE_RATE) ?: ""
            
            handler.post {
                if (text.isBlank()) {
                    toast("Could not transcribe audio")
                } else {
                    injectText(text)
                }
                resetState()
            }
        }
    }

    private fun injectText(text: String) {
        val root = rootInActiveWindow
        var targetNode: AccessibilityNodeInfo? = null
        
        if (root != null) {
            targetNode = findFocusedNode(root)
        }
        
        var success = false
        if (targetNode != null && targetNode.isEditable) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SpeakToWrite", text)
            clipboard.setPrimaryClip(clip)
            
            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        
        if (!success) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SpeakToWrite", text)
            clipboard.setPrimaryClip(clip)
            toast("Copied to clipboard (no active text field)")
        }
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun resetState() {
        state = State.IDLE
        setAppearance(COLOR_IDLE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        instance = null
        removeOverlay()
        super.onDestroy()
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
