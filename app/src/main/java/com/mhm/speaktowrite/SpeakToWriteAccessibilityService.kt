package com.mhm.speaktowrite

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import com.mhm.speaktowrite.models.TranscriberManager
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SpeakToWriteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SpeakToWrite"
        private const val SAMPLE_RATE = 16000

        // Two-zone button dimensions
        private const val MIC_DP = 48
        private const val ARROW_DP = 28
        private const val PAD_DP = 10
        private const val MARGIN_DP = 16
        private const val TAP_THRESHOLD_DP = 10

        // Aurora overlay palette (Zinc/Matte Redesign)
        private const val COLOR_IDLE = 0xDD112A20.toInt()
        private const val COLOR_RECORDING = 0xDD3A1010.toInt()
        private const val COLOR_BUSY = 0xDD0B2A30.toInt()
        private const val COLOR_LOADING = 0xDD1E1E22.toInt() // Zinc 900 variant surface

        // Dropdown panel colors
        private const val DROPDOWN_BG = 0xEE1E1E22.toInt()        // Zinc 900 variant
        private const val DROPDOWN_BORDER = 0xFF2C2C30.toInt()    // Solid border
        private const val DROPDOWN_ITEM_BG = 0xFF121214.toInt()    // Very dark gray/black
        private const val DROPDOWN_ITEM_SELECTED_BG = 0xFF1E1E22.toInt()
        private const val DROPDOWN_TEXT = 0xFFFAFAFA.toInt()       // Zinc 50
        private const val DROPDOWN_ACCENT = 0xFF10B981.toInt()     // Matte Emerald

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

    // ── States ──────────────────────────────────────────────────────────
    // IDLE         → mic green, arrow visible, can record or open dropdown
    // RECORDING    → mic red+pulse, arrow hidden, tap stops recording
    // TRANSCRIBING → mic teal, arrow hidden, tap shows "wait" toast
    // LOADING_MODEL→ mic grey, arrow hidden, tap shows "loading" toast
    private enum class State { IDLE, RECORDING, TRANSCRIBING, LOADING_MODEL }
    private var state = State.IDLE

    // ── Views ───────────────────────────────────────────────────────────
    private var overlayView: LinearLayout? = null
    private var micButton: ImageView? = null
    private var arrowButton: ImageView? = null
    private var dividerView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dropdownView: LinearLayout? = null
    private var isDropdownOpen = false

    // ── Audio ───────────────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var pcmStream: ByteArrayOutputStream? = null
    private val handler = Handler(Looper.getMainLooper())

    // ── Model loading observer ──────────────────────────────────────────
    private var observeJob: Job? = null
    private var lockScreenReceiver: android.content.BroadcastReceiver? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    // ── Helpers ──────────────────────────────────────────────────────────
    private val dp get() = resources.displayMetrics.density
    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    // ====================================================================
    // Lifecycle
    // ====================================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TranscriberManager.init(this)
        showOverlay()
        observeModelLoading()
        registerLockScreenReceiver()
        registerPrefListener()
    }

    override fun onDestroy() {
        instance = null
        observeJob?.cancel()
        unregisterLockScreenReceiver()
        unregisterPrefListener()
        removeOverlay()
        removeDropdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ====================================================================
    // Model-loading state observer
    // ====================================================================

    private fun observeModelLoading() {
        observeJob = CoroutineScope(Dispatchers.Main).launch {
            TranscriberManager.isLoading
                .collect { loading ->
                    if (loading && state == State.IDLE) {
                        enterState(State.LOADING_MODEL)
                    } else if (!loading && state == State.LOADING_MODEL) {
                        enterState(State.IDLE)
                    }
                }
        }
    }

    // ====================================================================
    // Overlay — two-zone button (mic | arrow)
    // ====================================================================

    @SuppressLint("ClickTouchListener", "ClickableViewAccessibility")
    private fun showOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val micSize = (MIC_DP * dp).toInt()
        val arrowSize = (ARROW_DP * dp).toInt()
        val pad = (PAD_DP * dp).toInt()
        val margin = (MARGIN_DP * dp).toInt()
        val totalW = micSize + arrowSize

        // Mic ImageView
        val micImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            setColorFilter(0xFFFFFFFF.toInt())
        }

        // Divider (1dp emerald line between mic and arrow)
        val div = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0x5510B981.toInt())
            }
            layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, (12 * dp).toInt(), 0, (12 * dp).toInt()) }
        }

        // Arrow ImageView
        val arrowImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_dropdown)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(0xFF10B981.toInt())
            setPadding((8 * dp).toInt(), pad, (8 * dp).toInt(), pad) // Symmetrical padding prevents visual swinging/shifting on rotation
        }

        // Root container — pill-shaped
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = 8 * dp
            clipChildren = false
            background = pillBg(COLOR_IDLE)
        }
        container.addView(micImg, LinearLayout.LayoutParams(micSize, micSize))
        container.addView(div)
        container.addView(arrowImg, LinearLayout.LayoutParams(arrowSize, micSize))

        val params = WindowManager.LayoutParams(
            totalW, micSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - totalW - margin
            y = screenH / 2 - micSize / 2
        }

        // Touch handling — zone-aware tap vs drag
        var startX = 0; var startY = 0
        var touchX = 0f; var touchY = 0f
        var isDragging = false

        container.setOnTouchListener { v, ev ->
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
                    if (!isDragging &&
                        abs(ev.rawX - touchX) < TAP_THRESHOLD_DP * dp &&
                        abs(ev.rawY - touchY) < TAP_THRESHOLD_DP * dp
                    ) {
                        // Determine zone from local X
                        val isMicZone = ev.x <= micSize
                        onZoneTap(isMicZone)
                    } else {
                        // Snap to edge
                        val targetX = if (params.x + totalW / 2 > screenW / 2) {
                            screenW - totalW - margin
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

        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val settings = com.mhm.speaktowrite.models.SettingsManager(this)
        if (km.isKeyguardLocked && !settings.showOnLockScreen) {
            container.visibility = View.GONE
        }

        wm.addView(container, params)
        overlayView = container
        micButton = micImg
        arrowButton = arrowImg
        dividerView = div
        layoutParams = params
    }

    private fun removeOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        micButton = null
        arrowButton = null
        dividerView = null
        layoutParams = null
    }

    // ====================================================================
    // Dropdown panel
    // ====================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showDropdown() {
        if (isDropdownOpen) return
        isDropdownOpen = true

        val models = TranscriberManager.getInstalledModels(this)
        if (models.isEmpty()) {
            toast("No models installed — download one in the app")
            isDropdownOpen = false
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = layoutParams ?: return

        val itemH = (44 * dp).toInt()
        val pad = (8 * dp).toInt()
        val radius = (16 * dp).toInt()
        val maxVisible = 8

        val visibleCount = models.size.coerceAtMost(maxVisible)
        val dropdownH = visibleCount * itemH + pad * 2

        // Build dropdown LinearLayout
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(),
                    radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(),
                )
                setColor(DROPDOWN_BG)
                setStroke((1 * dp).toInt(), DROPDOWN_BORDER)
            }
            setPadding(pad, pad, pad, pad)
            elevation = 12 * dp
        }

        val currentModel = TranscriberManager.currentModel.value

        models.forEachIndexed { index, (archive, displayName) ->
            val isSelected = archive == currentModel

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    val r = 8 * dp
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, r, r)
                    setColor(if (isSelected) DROPDOWN_ITEM_SELECTED_BG else DROPDOWN_ITEM_BG)
                }
            }

            val label = TextView(this).apply {
                text = displayName
                setTextColor(if (isSelected) DROPDOWN_ACCENT else DROPDOWN_TEXT)
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)

            if (isSelected) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(DROPDOWN_ACCENT)
                    val s = (14 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s)
                }
                row.addView(check)
            }

            row.setOnClickListener { onModelSelected(archive) }

            container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemH))

            // Separator between items (not after last visible)
            if (index < models.size - 1 && index < maxVisible - 1) {
                val sep = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xFF2C373A.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).apply { setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0) }
                }
                container.addView(sep)
            }
        }

        // Position above the button, or below if near top of screen
        val btnCenterY = lp.y + lp.height / 2
        val showAbove = btnCenterY > dropdownH + (16 * dp)

        val dropdownParams = WindowManager.LayoutParams(
            (200 * dp).toInt(),
            dropdownH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lp.x + lp.width / 2 - width / 2
            y = if (showAbove) lp.y - dropdownH - (8 * dp).toInt()
                 else lp.y + lp.height + (8 * dp).toInt()
        }

        // Animate in
        container.alpha = 0f
        container.scaleX = 0.92f
        container.scaleY = 0.92f
        container.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        wm.addView(container, dropdownParams)
        dropdownView = container

        // Rotate arrow to point up
        arrowButton?.animate()?.rotation(180f)?.setDuration(180)?.start()
    }

    private fun removeDropdown() {
        if (!isDropdownOpen) return
        isDropdownOpen = false

        val view = dropdownView ?: return
        view.animate()
            .alpha(0f).scaleX(0.92f).scaleY(0.92f)
            .setDuration(120)
            .withEndAction {
                try {
                    val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                    wm.removeView(view)
                } catch (_: Exception) {}
                dropdownView = null
            }
            .start()

        // Rotate arrow back to down
        arrowButton?.animate()?.rotation(0f)?.setDuration(120)?.start()
    }

    private fun onModelSelected(archive: String) {
        removeDropdown()

        // If same model is already loaded, nothing to do
        if (archive == TranscriberManager.currentModel.value && TranscriberManager.transcriber != null) return

        // Load the new model — the observer will catch isLoading and enter LOADING_MODEL
        TranscriberManager.loadModel(this, archive)
    }

    // ====================================================================
    // State machine
    // ====================================================================

    private fun enterState(newState: State) {
        state = newState
        when (newState) {
            State.IDLE -> {
                setButtonBg(pillBg(COLOR_IDLE))
                micButton?.setColorFilter(0xFFFFFFFF.toInt())
                micButton?.alpha = 1f
                dividerView?.visibility = View.VISIBLE
                arrowButton?.visibility = View.VISIBLE
            }
            State.RECORDING -> {
                setButtonBg(pillBg(COLOR_RECORDING))
                micButton?.setColorFilter(0xFFFFFFFF.toInt())
                micButton?.alpha = 1f
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
                startPulse()
            }
            State.TRANSCRIBING -> {
                stopPulse()
                setButtonBg(pillBg(COLOR_BUSY))
                micButton?.setColorFilter(0xFFFFFFFF.toInt())
                micButton?.alpha = 1f
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
            }
            State.LOADING_MODEL -> {
                setButtonBg(pillBg(COLOR_LOADING))
                micButton?.setColorFilter(0xFF9DB0AC.toInt()) // dimmed
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
            }
        }
    }

    private fun onZoneTap(isMicZone: Boolean) {
        when (state) {
            State.IDLE -> {
                if (isMicZone) {
                    if (isDropdownOpen) removeDropdown()
                    startRecording()
                } else {
                    if (isDropdownOpen) {
                        removeDropdown() // Tapping the now up arrow close toggles it
                    } else {
                        showDropdown()
                    }
                }
            }
            State.RECORDING -> {
                // Any tap stops recording (arrow is hidden, so all taps land on mic)
                stopAndTranscribe()
            }
            State.TRANSCRIBING -> {
                toast("Transcribing… please wait")
            }
            State.LOADING_MODEL -> {
                toast("Model is loading into memory — please wait")
            }
        }
    }

    private fun resetState() {
        enterState(State.IDLE)
    }

    // ====================================================================
    // Recording
    // ====================================================================

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Grant microphone permission in the app first")
            return
        }

        if (TranscriberManager.isLoading.value) {
            toast("Model is loading, please wait…")
            return
        }

        if (TranscriberManager.transcriber == null) {
            toast("No model loaded — select one from the dropdown")
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
        enterState(State.RECORDING)

        thread {
            val buf = ByteArray(bufSize)
            while (state == State.RECORDING) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: break
                if (n > 0) pcmStream?.write(buf, 0, n)
            }
        }
    }

    private fun stopAndTranscribe() {
        enterState(State.TRANSCRIBING)

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

        CoroutineScope(Dispatchers.IO).launch {
            val floatArray = FloatArray(pcm.size / 2)
            var i = 0
            while (i < pcm.size - 1) {
                val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                floatArray[i / 2] = sample.toShort().toFloat() / 32768f
                i += 2
            }

            val rawText = TranscriberManager.transcriber?.transcribe(floatArray, SAMPLE_RATE) ?: ""

            var finalText = rawText
            if (finalText.isNotBlank()) {
                val settings = com.mhm.speaktowrite.models.SettingsManager(this@SpeakToWriteAccessibilityService)
                if (settings.cleanupEnabled && settings.apiKey.isNotBlank()) {
                    val promptText = settings.prompts.find { it.id == settings.selectedPromptId }?.content ?: ""
                    val cleaned = com.mhm.speaktowrite.models.PostProcessor.process(
                        text = rawText,
                        prompt = promptText,
                        apiKey = settings.apiKey,
                        model = settings.selectedAiModel
                    )
                    if (cleaned != null) finalText = cleaned
                }
            }

            handler.post {
                if (finalText.isBlank()) {
                    toast("No speech detected")
                } else {
                    injectText(finalText)
                }
                resetState()
            }
        }
    }

    // ====================================================================
    // Text injection
    // ====================================================================

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

    // ====================================================================
    // Animations & drawables
    // ====================================================================

    private fun animateSnap(fromX: Int, toX: Int, params: WindowManager.LayoutParams, wm: WindowManager, view: View) {
        val animator = ValueAnimator.ofInt(fromX, toX)
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        animator.start()
    }

    private fun startPulse() {
        micButton?.let {
            it.animate().alpha(0.5f).setDuration(600).withEndAction {
                it.animate().alpha(1f).setDuration(600).withEndAction {
                    if (state == State.RECORDING) startPulse()
                }.start()
            }.start()
        }
    }

    private fun stopPulse() {
        micButton?.animate()?.cancel()
        micButton?.alpha = 1f
    }

    /** Pill-shaped background with solid borders matching the matte design states. */
    private fun pillBg(color: Int): GradientDrawable {
        val radius = 24 * dp
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                radius, radius, radius, radius,
                radius, radius, radius, radius,
            )
            setColor(color)
            when (color) {
                COLOR_IDLE -> setStroke((2 * dp).toInt(), 0xFF10B981.toInt())
                COLOR_RECORDING -> setStroke((2 * dp).toInt(), 0xFFEF4444.toInt())
                COLOR_BUSY -> setStroke((2 * dp).toInt(), 0xFF06B6D4.toInt())
                COLOR_LOADING -> setStroke((2 * dp).toInt(), 0xFF71717A.toInt())
            }
        }
    }

    private fun setButtonBg(drawable: GradientDrawable) {
        handler.post { overlayView?.background = drawable }
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun registerLockScreenReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        lockScreenReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                val settings = com.mhm.speaktowrite.models.SettingsManager(context)
                val showOnLock = settings.showOnLockScreen
                
                val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                val isLocked = km.isKeyguardLocked
                
                if (intent.action == android.content.Intent.ACTION_SCREEN_OFF) {
                    overlayView?.visibility = View.GONE
                } else if (intent.action == android.content.Intent.ACTION_SCREEN_ON) {
                    if (isLocked && !showOnLock) {
                        overlayView?.visibility = View.GONE
                    } else {
                        overlayView?.visibility = View.VISIBLE
                    }
                } else if (intent.action == android.content.Intent.ACTION_USER_PRESENT) {
                    overlayView?.visibility = View.VISIBLE
                }
            }
        }
        registerReceiver(lockScreenReceiver, filter)
    }

    private fun unregisterLockScreenReceiver() {
        lockScreenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lockScreenReceiver = null
    }

    private fun registerPrefListener() {
        prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_on_lock_screen") {
                val settings = com.mhm.speaktowrite.models.SettingsManager(this)
                val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                val isLocked = km.isKeyguardLocked
                
                if (isLocked && !settings.showOnLockScreen) {
                    overlayView?.visibility = View.GONE
                } else {
                    overlayView?.visibility = View.VISIBLE
                }
            }
        }
        val prefs = getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun unregisterPrefListener() {
        prefListener?.let {
            val prefs = getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        prefListener = null
    }
}
