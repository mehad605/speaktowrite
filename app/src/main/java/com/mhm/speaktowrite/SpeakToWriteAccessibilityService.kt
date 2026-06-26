package com.mhm.speaktowrite

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import android.widget.FrameLayout
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private enum class SliderState { COLLAPSED, EXPANDED }
    private var sliderState = SliderState.COLLAPSED
    private var lastAddedState: SliderState? = null
    private var isTransitioning = false
    private var isDraggingToOpen = false
    private var currentDragDx = 0f
    private var activeTempHandle: View? = null

    // ── Views ───────────────────────────────────────────────────────────
    private var rootWrapper: FrameLayout? = null
    private var handleView: View? = null
    private var controlPanelContainer: LinearLayout? = null
    private var backdropView: View? = null
    private var micButton: ImageView? = null
    private var arrowButton: ImageView? = null
    private var promptArrowButton: ImageView? = null
    private var dividerView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dropdownView: LinearLayout? = null
    private var isDropdownOpen = false
    private var activeDropdownType: DropdownType? = null

    private enum class DropdownType { MODELS, PROMPTS }

    // ── Audio ───────────────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var pcmStream: ByteArrayOutputStream? = null
    private val handler = Handler(Looper.getMainLooper())

    // ── Model loading observer ──────────────────────────────────────────
    private var observeJob: Job? = null
    private var lockScreenReceiver: android.content.BroadcastReceiver? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    // ── Lifecycle-bound coroutine scope ────────────────────────────────
    // Using SupervisorJob so one child failure doesn't cancel the whole scope.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Token used to debounce rapid overlay-rebuild requests from the pref listener
    private val overlayRebuildToken = Any()

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
        ServiceLogger.init(this)
        ServiceLogger.i(TAG, "onServiceConnected() PID=${android.os.Process.myPid()}")
        TranscriberManager.init(this)
        showOverlay()
        observeModelLoading()
        registerLockScreenReceiver()
        registerPrefListener()
    }

    override fun onDestroy() {
        ServiceLogger.w(TAG, "onDestroy() — service is being torn down")
        instance = null
        // Cancel ALL coroutines tied to this service instance
        serviceJob.cancel()
        observeJob?.cancel()
        unregisterLockScreenReceiver()
        unregisterPrefListener()
        // Remove any pending debounced overlay rebuilds
        handler.removeCallbacksAndMessages(overlayRebuildToken)
        removeOverlay()
        removeDropdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        // Called by Android when the accessibility service is about to be force-disconnected.
        ServiceLogger.w(TAG, "onInterrupt() — Android is interrupting/restarting the service")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceLogger.w(TAG, "onUnbind() — system unbound the accessibility service")
        return super.onUnbind(intent)
    }

    // ====================================================================
    // Model-loading state observer
    // ====================================================================

    private fun observeModelLoading() {
        // Use serviceScope so this coroutine is cancelled when the service is destroyed.
        observeJob = serviceScope.launch {
            TranscriberManager.isLoading
                .collect { loading ->
                    if (loading && state == State.IDLE) {
                        ServiceLogger.d(TAG, "Model loading started — entering LOADING_MODEL state")
                        enterState(State.LOADING_MODEL)
                    } else if (!loading && state == State.LOADING_MODEL) {
                        val err = TranscriberManager.loadError.value
                        if (err != null) {
                            ServiceLogger.w(TAG, "Model load finished with error: $err")
                            toast("Model failed to load — $err")
                        } else {
                            ServiceLogger.d(TAG, "Model loading finished — returning to IDLE")
                        }
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
        updateOverlayLayout()
    }

    private fun expandSlider() {
        if (sliderState == SliderState.EXPANDED || isTransitioning) return
        isTransitioning = true
        removeDropdown()
        sliderState = SliderState.EXPANDED
        updateOverlayLayout()
    }

    private fun collapseSlider() {
        if (sliderState == SliderState.COLLAPSED || isTransitioning) return
        isTransitioning = true
        removeDropdown()

        val settings = com.mhm.speaktowrite.models.SettingsManager(this)
        val isLeftEdge = settings.sliderIsLeftEdge

        val micSize = (MIC_DP * dp).toInt()
        val arrowSize = (ARROW_DP * dp).toInt()
        val hasValidApiKey = settings.apiKey.isNotBlank() && settings.isApiKeyValid
        val totalW = if (hasValidApiKey) micSize + arrowSize * 2 else micSize + arrowSize

        val handleWidth = (6 * dp).toInt()
        val handleHeight = micSize

        // Create the temporary handle to animate sliding in
        val tempHandle = View(this).apply {
            background = pillBg(getStateColor(state)).mutate().apply {
                val alphaPercent = (1.0f - settings.sliderOpacity).coerceIn(0.0f, 1.0f)
                alpha = (alphaPercent * 255).toInt()
            }
        }
        activeTempHandle = tempHandle

        val tempHandleParams = FrameLayout.LayoutParams(handleWidth, handleHeight).apply {
            gravity = (if (isLeftEdge) Gravity.START else Gravity.END) or Gravity.TOP
        }

        rootWrapper?.addView(tempHandle, tempHandleParams)

        // Initial off-screen translation for the handle
        val handleStartTranslationX = if (isLeftEdge) -handleWidth.toFloat() else handleWidth.toFloat()
        tempHandle.translationX = handleStartTranslationX

        // Target translations
        val containerTargetX = if (isLeftEdge) -totalW.toFloat() else totalW.toFloat()

        // Animate control panel sliding out (snappy closing curve)
        controlPanelContainer?.animate()
            ?.translationX(containerTargetX)
            ?.setDuration(220)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        // Animate handle sliding in
        tempHandle.animate()
            ?.translationX(0f)
            ?.setDuration(220)
            ?.setInterpolator(DecelerateInterpolator())
            ?.withEndAction {
                // Guard: if service was destroyed mid-animation, skip the rebuild
                if (instance == null) {
                    ServiceLogger.w(TAG, "collapseSlider withEndAction: service gone, skipping overlay rebuild")
                    return@withEndAction
                }
                sliderState = SliderState.COLLAPSED
                isTransitioning = false
                activeTempHandle = null
                updateOverlayLayout()
            }
            ?.start()
    }

    @SuppressLint("ClickTouchListener", "ClickableViewAccessibility")
    private fun updateOverlayLayout() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val settings = com.mhm.speaktowrite.models.SettingsManager(this)
        val isLeftEdge = settings.sliderIsLeftEdge

        val micSize = (MIC_DP * dp).toInt()
        val arrowSize = (ARROW_DP * dp).toInt()
        val pad = (PAD_DP * dp).toInt()
        val margin = (MARGIN_DP * dp).toInt()

        val hasValidApiKey = settings.apiKey.isNotBlank() && settings.isApiKeyValid
        val totalW = if (hasValidApiKey) micSize + arrowSize * 2 else micSize + arrowSize

        val root = rootWrapper ?: FrameLayout(this).also { rootWrapper = it }
        val isAdded = root.parent != null

        lastAddedState = sliderState

        val targetWidth = if (sliderState == SliderState.COLLAPSED) {
            (24 * dp).toInt()
        } else {
            totalW
        }
        val targetHeight = micSize

        val savedY = settings.sliderY.let {
            if (it == -1) screenH / 4 else it
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (sliderState == SliderState.EXPANDED) {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        val params = WindowManager.LayoutParams(
            targetWidth, targetHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isLeftEdge) Gravity.START else Gravity.END) or Gravity.TOP
            windowAnimations = R.style.WindowNoAnimation
            x = 0
            y = savedY.coerceIn(margin, screenH - targetHeight - margin)
        }
        layoutParams = params

        root.removeAllViews()

        if (sliderState == SliderState.COLLAPSED) {
            val handleWidth = (6 * dp).toInt()
            val handleHeight = micSize

            val handle = View(this).apply {
                background = pillBg(getStateColor(state))
            }
            handleView = handle

            val handleParams = FrameLayout.LayoutParams(handleWidth, handleHeight).apply {
                gravity = (if (isLeftEdge) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            }
            root.addView(handle, handleParams)

            // Apply System Gesture Exclusion Rects to bypass Android back swipe trigger
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                root.post {
                    try {
                        root.systemGestureExclusionRects = listOf(
                            Rect(0, 0, targetWidth, targetHeight)
                        )
                    } catch (_: Exception) {}
                }
            }

            var startY = 0
            var touchX = 0f; var touchY = 0f
            var isDragging = false
            var isLongPressed = false
            var currentIsLeftEdge = isLeftEdge
            val longPressHandler = Handler(Looper.getMainLooper())

            val longPressRunnable = Runnable {
                isLongPressed = true
                val jumpAmount = (8 * dp).toInt()
                val targetTranslationX = if (currentIsLeftEdge) jumpAmount.toFloat() else -jumpAmount.toFloat()
                handleView?.animate()?.translationX(targetTranslationX)?.setDuration(150)?.start()
                
                try {
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } catch (_: Exception) {}
            }

            root.setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = params.y
                        touchX = ev.rawX
                        touchY = ev.rawY
                        isDragging = false
                        isLongPressed = false
                        isDraggingToOpen = false
                        currentIsLeftEdge = settings.sliderIsLeftEdge
                        longPressHandler.postDelayed(longPressRunnable, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - touchX
                        val dy = ev.rawY - touchY
                        
                        if (!isLongPressed && !isDraggingToOpen && (abs(dx) > TAP_THRESHOLD_DP * dp || abs(dy) > TAP_THRESHOLD_DP * dp)) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            
                            // Check if the drag is primarily horizontal to open the drawer
                            if (abs(dx) > abs(dy)) {
                                val canOpen = if (isLeftEdge) dx > 0 else dx < 0
                                if (canOpen) {
                                    isDraggingToOpen = true
                                    currentDragDx = dx
                                    expandSlider()
                                } else {
                                    isDragging = true
                                }
                            } else {
                                isDragging = true
                            }
                        }
                        
                        if (isDraggingToOpen) {
                            currentDragDx = dx
                            val progress = if (isLeftEdge) {
                                (dx / totalW).coerceIn(0f, 1f)
                            } else {
                                (-dx / totalW).coerceIn(0f, 1f)
                            }
                            
                            val containerTargetX = if (isLeftEdge) {
                                -totalW + dx
                            } else {
                                totalW + dx
                            }
                            val clampedContainerX = if (isLeftEdge) {
                                containerTargetX.coerceAtMost(0f)
                            } else {
                                containerTargetX.coerceAtLeast(0f)
                            }
                            
                            controlPanelContainer?.translationX = clampedContainerX
                            
                            val handleTargetX = if (isLeftEdge) -handleWidth.toFloat() else handleWidth.toFloat()
                            activeTempHandle?.translationX = handleTargetX * progress
                        } else if (isLongPressed) {
                            params.y = (startY + dy.toInt()).coerceIn(margin, screenH - handleHeight - margin)
                            
                            val rawX = ev.rawX
                            val jumpAmount = (8 * dp).toInt()
                            
                            if (currentIsLeftEdge && rawX > screenW / 2) {
                                currentIsLeftEdge = false
                                settings.sliderIsLeftEdge = false
                                params.gravity = Gravity.END or Gravity.TOP
                                params.x = 0
                                
                                handleView?.background = pillBg(getStateColor(state))
                                handleView?.translationX = -jumpAmount.toFloat()
                            } else if (!currentIsLeftEdge && rawX < screenW / 2) {
                                currentIsLeftEdge = true
                                settings.sliderIsLeftEdge = true
                                params.gravity = Gravity.START or Gravity.TOP
                                params.x = 0
                                
                                handleView?.background = pillBg(getStateColor(state))
                                handleView?.translationX = jumpAmount.toFloat()
                            }
                            
                            try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        
                        if (isLongPressed) {
                            settings.sliderY = params.y
                            settings.sliderIsLeftEdge = currentIsLeftEdge
                            
                            handleView?.animate()?.translationX(0f)?.setDuration(150)?.withEndAction {
                                // Guard: service may have been destroyed mid-drag
                                if (instance == null) {
                                    ServiceLogger.w(TAG, "Long-press withEndAction: service gone, skipping overlay rebuild")
                                    return@withEndAction
                                }
                                removeOverlay()
                                showOverlay()
                            }?.start()
                        } else if (isDraggingToOpen) {
                            val dx = ev.rawX - touchX
                            val progress = if (isLeftEdge) {
                                dx / totalW
                            } else {
                                -dx / totalW
                            }
                            
                            val shouldOpen = progress > 0.4f && ev.action == MotionEvent.ACTION_UP
                            
                            if (shouldOpen) {
                                // Complete open animation
                                controlPanelContainer?.animate()
                                    ?.translationX(0f)
                                    ?.setDuration(150)
                                    ?.setInterpolator(DecelerateInterpolator())
                                    ?.start()
                                
                                val handleTargetX = if (isLeftEdge) -handleWidth.toFloat() else handleWidth.toFloat()
                                activeTempHandle?.animate()
                                    ?.translationX(handleTargetX)
                                    ?.setDuration(150)
                                    ?.setInterpolator(DecelerateInterpolator())
                                    ?.withEndAction {
                                        root.removeView(activeTempHandle)
                                        activeTempHandle = null
                                        isTransitioning = false
                                        isDraggingToOpen = false
                                        root.setOnTouchListener { _, outEv ->
                                            if (outEv.action == MotionEvent.ACTION_OUTSIDE) {
                                                collapseSlider()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    }
                                    ?.start()
                            } else {
                                // Revert to collapsed
                                val containerTargetX = if (isLeftEdge) -totalW.toFloat() else totalW.toFloat()
                                controlPanelContainer?.animate()
                                    ?.translationX(containerTargetX)
                                    ?.setDuration(150)
                                    ?.setInterpolator(DecelerateInterpolator())
                                    ?.start()
                                
                                activeTempHandle?.animate()
                                    ?.translationX(0f)
                                    ?.setDuration(150)
                                    ?.setInterpolator(DecelerateInterpolator())
                                    ?.withEndAction {
                                        sliderState = SliderState.COLLAPSED
                                        isTransitioning = false
                                        isDraggingToOpen = false
                                        updateOverlayLayout()
                                    }
                                    ?.start()
                            }
                        } else if (ev.action == MotionEvent.ACTION_UP) {
                            val dx = ev.rawX - touchX
                            val dy = ev.rawY - touchY
                            val isTap = abs(dx) < TAP_THRESHOLD_DP * dp && abs(dy) < TAP_THRESHOLD_DP * dp
                            val swipeThreshold = 30 * dp
                            val expand = isTap || if (isLeftEdge) {
                                dx > swipeThreshold
                            } else {
                                dx < -swipeThreshold
                            }
                            
                            if (expand) {
                                expandSlider()
                            } else {
                                handleView?.animate()?.translationX(0f)?.setDuration(150)?.start()
                            }
                        } else {
                            handleView?.animate()?.translationX(0f)?.setDuration(150)?.start()
                        }
                        true
                    }
                    else -> false
                }
            }

        } else {
            if (!isDraggingToOpen) {
                root.setOnTouchListener { _, ev ->
                    if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                        collapseSlider()
                        true
                    } else {
                        false
                    }
                }
            }

            val micImg = ImageView(this).apply {
                setImageResource(R.drawable.ic_mic)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(pad, pad, pad, pad)
                setColorFilter(0xFFFFFFFF.toInt())
            }
            micButton = micImg

            val div = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0x5510B981.toInt())
                }
                layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    setMargins(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
                }
            }
            dividerView = div

            val arrowImg = ImageView(this).apply {
                setImageResource(R.drawable.ic_dropdown)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(0xFF10B981.toInt())
                setPadding((8 * dp).toInt(), pad, (8 * dp).toInt(), pad)
            }
            arrowButton = arrowImg

            var promptArrowImg: ImageView? = null
            if (hasValidApiKey) {
                promptArrowImg = ImageView(this).apply {
                    setImageResource(R.drawable.ic_dropdown)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setColorFilter(0xFF3B82F6.toInt())
                    setPadding((8 * dp).toInt(), pad, (8 * dp).toInt(), pad)
                }
            }
            promptArrowButton = promptArrowImg

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                elevation = 8 * dp
                clipChildren = false
                background = pillBg(getStateColor(state))
            }
            controlPanelContainer = container

            container.addView(micImg, LinearLayout.LayoutParams(micSize, micSize))
            container.addView(div)
            container.addView(arrowImg, LinearLayout.LayoutParams(arrowSize, micSize))
            if (hasValidApiKey && promptArrowImg != null) {
                container.addView(promptArrowImg, LinearLayout.LayoutParams(arrowSize, micSize))
            }

            val containerParams = FrameLayout.LayoutParams(totalW, micSize).apply {
                gravity = Gravity.TOP or (if (isLeftEdge) Gravity.START else Gravity.END)
            }
            root.addView(container, containerParams)

            // Create temporary handle to slide offscreen while container slides onscreen
            val handleWidth = (6 * dp).toInt()
            val handleHeight = micSize
            val tempHandle = View(this).apply {
                background = pillBg(getStateColor(state)).mutate().apply {
                    val alphaPercent = (1.0f - settings.sliderOpacity).coerceIn(0.0f, 1.0f)
                    alpha = (alphaPercent * 255).toInt()
                }
            }
            val tempHandleParams = FrameLayout.LayoutParams(handleWidth, handleHeight).apply {
                gravity = (if (isLeftEdge) Gravity.START else Gravity.END) or Gravity.TOP
            }
            root.addView(tempHandle, tempHandleParams)

            var startY = 0
            var touchX = 0f; var touchY = 0f
            var isDragging = false

            container.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
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
                            params.y = (startY + dy.toInt()).coerceIn(margin, screenH - micSize - margin)
                            try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - touchX
                        val dy = ev.rawY - touchY
                        if (!isDragging &&
                            abs(dx) < TAP_THRESHOLD_DP * dp &&
                            abs(dy) < TAP_THRESHOLD_DP * dp
                        ) {
                            val tapX = ev.x
                            if (tapX <= micSize) {
                                onZoneTapMic()
                            } else if (hasValidApiKey && tapX > micSize + arrowSize) {
                                onZoneTapPrompt()
                            } else {
                                onZoneTapModel()
                            }
                        } else {
                            settings.sliderY = params.y

                            val swipeThreshold = 30 * dp
                            val collapsed = if (isLeftEdge) {
                                dx < -swipeThreshold
                            } else {
                                dx > swipeThreshold
                            }

                            if (collapsed) {
                                collapseSlider()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            activeTempHandle = tempHandle

            if (isDraggingToOpen) {
                val dx = currentDragDx
                val progress = (if (isLeftEdge) dx / totalW else -dx / totalW).coerceIn(0f, 1f)
                
                val containerTargetX = if (isLeftEdge) -totalW + dx else totalW + dx
                val clampedContainerTargetX = if (isLeftEdge) containerTargetX.coerceAtMost(0f) else containerTargetX.coerceAtLeast(0f)
                container.translationX = clampedContainerTargetX
                
                val handleTargetX = if (isLeftEdge) -handleWidth.toFloat() else handleWidth.toFloat()
                tempHandle.translationX = handleTargetX * progress
            } else {
                container.translationX = if (isLeftEdge) -totalW.toFloat() else totalW.toFloat()
                tempHandle.translationX = 0f

                container.animate()
                    .translationX(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                val handleTargetTranslationX = if (isLeftEdge) -handleWidth.toFloat() else handleWidth.toFloat()
                tempHandle.animate()
                    .translationX(handleTargetTranslationX)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        root.removeView(tempHandle)
                        activeTempHandle = null
                        isTransitioning = false
                    }
                    .start()
            }
        }

        val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (km.isKeyguardLocked && !settings.showOnLockScreen) {
            root.visibility = View.GONE
        } else {
            root.visibility = View.VISIBLE
        }

        val isNowAdded = root.parent != null
        if (!isNowAdded) {
            try {
                wm.addView(root, params)
                ServiceLogger.d(TAG, "Overlay added to WindowManager (state=$state, slider=$sliderState)")
            } catch (e: Exception) {
                ServiceLogger.e(TAG, "wm.addView failed: ${e.javaClass.simpleName} — ${e.message}", e)
            }
        } else {
            try {
                wm.updateViewLayout(root, params)
            } catch (e: Exception) {
                ServiceLogger.e(TAG, "wm.updateViewLayout failed: ${e.javaClass.simpleName} — ${e.message}", e)
            }
        }
    }

    private fun removeOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        rootWrapper?.let {
            try {
                wm.removeView(it)
                ServiceLogger.d(TAG, "Overlay removed from WindowManager")
            } catch (e: Exception) {
                ServiceLogger.e(TAG, "wm.removeView failed: ${e.javaClass.simpleName} — ${e.message}", e)
            }
        }
        rootWrapper = null
        handleView = null
        controlPanelContainer = null
        backdropView = null
        micButton = null
        arrowButton = null
        promptArrowButton = null
        dividerView = null
        layoutParams = null
        isTransitioning = false
        activeTempHandle = null
    }

    // ====================================================================
    // Dropdown panel
    // ====================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showDropdown(type: DropdownType) {
        if (isDropdownOpen) return
        isDropdownOpen = true
        activeDropdownType = type

        val settings = com.mhm.speaktowrite.models.SettingsManager(this)

        val models = if (type == DropdownType.MODELS) TranscriberManager.getInstalledModels(this) else emptyList()
        val prompts = if (type == DropdownType.PROMPTS) settings.prompts else emptyList()

        if (type == DropdownType.MODELS && models.isEmpty()) {
            toast("No models installed — download one in the app")
            isDropdownOpen = false
            activeDropdownType = null
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = layoutParams ?: return

        val itemH = (44 * dp).toInt()
        val pad = (8 * dp).toInt()
        val radius = (16 * dp).toInt()
        val maxVisible = 3

        val itemsCount = if (type == DropdownType.MODELS) models.size else prompts.size + 1
        val visibleCount = itemsCount.coerceAtMost(maxVisible)
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

        // Add ScrollView inside container to enable scrolling
        val scrollView = ScrollView(android.view.ContextThemeWrapper(this, R.style.DropdownScrollView)).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = true
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        if (type == DropdownType.MODELS) {
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
                scrollContent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemH))

                if (index < models.size - 1) {
                    val sep = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(0xFF2C373A.toInt())
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                        ).apply { setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0) }
                    }
                    scrollContent.addView(sep)
                }
            }
        } else {
            // First item: No Post-processing
            val isNoPostSelected = !settings.cleanupEnabled

            val noPostRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    val r = 8 * dp
                    cornerRadii = floatArrayOf(r, r, r, r, r, r, r, r)
                    setColor(if (isNoPostSelected) DROPDOWN_ITEM_SELECTED_BG else DROPDOWN_ITEM_BG)
                }
            }

            val noPostLabel = TextView(this).apply {
                text = "No Post-processing"
                setTextColor(if (isNoPostSelected) DROPDOWN_ACCENT else DROPDOWN_TEXT)
                setTypeface(null, if (isNoPostSelected) Typeface.BOLD else Typeface.NORMAL)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            noPostRow.addView(noPostLabel)

            if (isNoPostSelected) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(DROPDOWN_ACCENT)
                    val s = (14 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s)
                }
                noPostRow.addView(check)
            }

            noPostRow.setOnClickListener { onPromptSelected(null, "No Post-processing") }
            scrollContent.addView(noPostRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemH))

            if (prompts.isNotEmpty()) {
                val sep = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xFF2C373A.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                    ).apply { setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0) }
                }
                scrollContent.addView(sep)
            }

            // Other prompt presets
            val currentPromptId = settings.selectedPromptId
            prompts.forEachIndexed { index, preset ->
                val isSelected = settings.cleanupEnabled && preset.id == currentPromptId

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
                    text = preset.title
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

                row.setOnClickListener { onPromptSelected(preset.id, preset.title) }
                scrollContent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemH))

                if (index < prompts.size - 1) {
                    val sep = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(0xFF2C373A.toInt())
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                        ).apply { setMargins((8 * dp).toInt(), 0, (8 * dp).toInt(), 0) }
                    }
                    scrollContent.addView(sep)
                }
            }
        }

        scrollView.addView(scrollContent)
        container.addView(scrollView)

        // Sizing & Positioning calculations
        val micSize = (MIC_DP * dp).toInt()
        val arrowSize = (ARROW_DP * dp).toInt()
        val dropdownW = (200 * dp).toInt()
        val gap = (8 * dp).toInt()

        val hasValidApiKey = settings.apiKey.isNotBlank() && settings.isApiKeyValid
        val totalW = if (hasValidApiKey) micSize + arrowSize * 2 + (1 * dp).toInt() else micSize + arrowSize + (1 * dp).toInt()

        // Root's screen X position based on which edge it's docked to
        val rootScreenX = if (settings.sliderIsLeftEdge) 0 else screenW - totalW

        // Arrow centers on screen (relative to root's left edge)
        val modelArrowCenter = rootScreenX + micSize + (1 * dp).toInt() + arrowSize / 2
        val promptArrowCenter = rootScreenX + micSize + (1 * dp).toInt() + arrowSize + arrowSize / 2

        // Dropdown X: centered on the tapped arrow, clamped to screen
        val arrowCenter = if (type == DropdownType.PROMPTS && hasValidApiKey) promptArrowCenter else modelArrowCenter
        val dropX = (arrowCenter - dropdownW / 2).coerceIn(0, screenW - dropdownW)

        // Dropdown Y: model prefers above, prompt prefers below; flips if near edge
        val prefersAbove = type == DropdownType.MODELS
        val roomAbove = lp.y >= dropdownH + gap
        val roomBelow = lp.y + micSize + gap + dropdownH <= screenH
        val dropY = if (prefersAbove && roomAbove) {
            lp.y - dropdownH - gap
        } else if (!prefersAbove && roomBelow) {
            lp.y + micSize + gap
        } else if (roomAbove) {
            lp.y - dropdownH - gap
        } else {
            lp.y + micSize + gap
        }

        val dropdownParams = WindowManager.LayoutParams(
            dropdownW,
            dropdownH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dropX
            y = dropY
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

        // Rotate target arrow to point up
        val targetArrow = if (type == DropdownType.MODELS) arrowButton else promptArrowButton
        targetArrow?.animate()?.rotation(180f)?.setDuration(180)?.start()
    }

    private fun removeDropdown() {
        if (!isDropdownOpen) return
        isDropdownOpen = false
        activeDropdownType = null

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

        // Rotate arrows back to down
        arrowButton?.animate()?.rotation(0f)?.setDuration(120)?.start()
        promptArrowButton?.animate()?.rotation(0f)?.setDuration(120)?.start()
    }

    private fun onModelSelected(archive: String) {
        removeDropdown()

        // If same model is already loaded, nothing to do
        if (archive == TranscriberManager.currentModel.value && TranscriberManager.transcriber != null) return

        // Load the new model — the observer will catch isLoading and enter LOADING_MODEL
        TranscriberManager.loadModel(this, archive)
    }

    private fun onPromptSelected(id: String?, title: String) {
        removeDropdown()
        val settings = com.mhm.speaktowrite.models.SettingsManager(this)
        if (id == null) {
            settings.cleanupEnabled = false
        } else {
            settings.cleanupEnabled = true
            settings.selectedPromptId = id
        }
        toast("Active prompt: $title")
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

                val settings = com.mhm.speaktowrite.models.SettingsManager(this)
                val hasValidApiKey = settings.apiKey.isNotBlank() && settings.isApiKeyValid
                promptArrowButton?.visibility = if (hasValidApiKey) View.VISIBLE else View.GONE
            }
            State.RECORDING -> {
                setButtonBg(pillBg(COLOR_RECORDING))
                micButton?.setColorFilter(0xFFFFFFFF.toInt())
                micButton?.alpha = 1f
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
                promptArrowButton?.visibility = View.GONE
                startPulse()
            }
            State.TRANSCRIBING -> {
                stopPulse()
                setButtonBg(pillBg(COLOR_BUSY))
                micButton?.setColorFilter(0xFFFFFFFF.toInt())
                micButton?.alpha = 1f
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
                promptArrowButton?.visibility = View.GONE
            }
            State.LOADING_MODEL -> {
                setButtonBg(pillBg(COLOR_LOADING))
                micButton?.setColorFilter(0xFF9DB0AC.toInt()) // dimmed
                dividerView?.visibility = View.GONE
                arrowButton?.visibility = View.GONE
                promptArrowButton?.visibility = View.GONE
            }
        }
    }

    private fun onZoneTapMic() {
        when (state) {
            State.IDLE -> {
                removeDropdown()
                startRecording()
            }
            State.RECORDING -> {
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

    private fun onZoneTapModel() {
        if (state != State.IDLE) {
            if (state == State.TRANSCRIBING) toast("Transcribing… please wait")
            if (state == State.LOADING_MODEL) toast("Model is loading into memory — please wait")
            return
        }
        if (isDropdownOpen && activeDropdownType == DropdownType.MODELS) {
            removeDropdown()
        } else {
            showDropdown(DropdownType.MODELS)
        }
    }

    private fun onZoneTapPrompt() {
        if (state != State.IDLE) {
            if (state == State.TRANSCRIBING) toast("Transcribing… please wait")
            if (state == State.LOADING_MODEL) toast("Model is loading into memory — please wait")
            return
        }
        if (isDropdownOpen && activeDropdownType == DropdownType.PROMPTS) {
            removeDropdown()
        } else {
            showDropdown(DropdownType.PROMPTS)
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
            ServiceLogger.w(TAG, "stopAndTranscribe: no audio captured")
            toast("No audio captured")
            resetState()
            return
        }

        ServiceLogger.d(TAG, "stopAndTranscribe: ${pcm.size} bytes captured, starting transcription")

        // Use serviceScope so this is cancelled if the service is destroyed mid-transcription.
        serviceScope.launch(Dispatchers.IO) {
            try {
                val floatArray = FloatArray(pcm.size / 2)
                var i = 0
                while (i < pcm.size - 1) {
                    val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                    floatArray[i / 2] = sample.toShort().toFloat() / 32768f
                    i += 2
                }

                val rawText = TranscriberManager.transcriber?.transcribe(floatArray, SAMPLE_RATE) ?: ""
                ServiceLogger.d(TAG, "Transcription result: ${rawText.take(120)}")

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
                    // Guard: service may have been destroyed while transcription was in flight
                    if (instance == null) {
                        ServiceLogger.w(TAG, "Transcription complete but service was destroyed; discarding result")
                        return@post
                    }
                    if (finalText.isBlank()) {
                        toast("No speech detected")
                    } else {
                        injectText(finalText)
                    }
                    resetState()
                }
            } catch (e: Exception) {
                ServiceLogger.e(TAG, "Transcription failed: ${e.javaClass.simpleName} — ${e.message}", e)
                handler.post {
                    if (instance != null) {
                        toast("Transcription error: ${e.message}")
                        resetState()
                    }
                }
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
        val target = if (sliderState == SliderState.EXPANDED) micButton else handleView
        target?.let {
            it.animate().alpha(0.3f).setDuration(600).withEndAction {
                it.animate().alpha(1f).setDuration(600).withEndAction {
                    if (state == State.RECORDING) startPulse()
                }.start()
            }.start()
        }
    }

    private fun stopPulse() {
        micButton?.animate()?.cancel()
        micButton?.alpha = 1f
        handleView?.animate()?.cancel()
        handleView?.alpha = 1f
    }

    /** Pill-shaped background with solid borders matching the matte design states, edge-aware. */
    private fun pillBg(color: Int): GradientDrawable {
        val settings = com.mhm.speaktowrite.models.SettingsManager(this)
        val isLeftEdge = settings.sliderIsLeftEdge
        val radius = 24 * dp
        val radii = if (isLeftEdge) {
            floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        } else {
            floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(color)
            if (sliderState == SliderState.COLLAPSED) {
                val alphaPercent = (1.0f - settings.sliderOpacity).coerceIn(0.0f, 1.0f)
                alpha = (alphaPercent * 255).toInt()
            } else {
                alpha = 255
            }
            when (color) {
                COLOR_IDLE -> setStroke((2 * dp).toInt(), 0xFF10B981.toInt())
                COLOR_RECORDING -> setStroke((2 * dp).toInt(), 0xFFEF4444.toInt())
                COLOR_BUSY -> setStroke((2 * dp).toInt(), 0xFF06B6D4.toInt())
                COLOR_LOADING -> setStroke((2 * dp).toInt(), 0xFF71717A.toInt())
            }
        }
    }

    private fun setButtonBg(drawable: GradientDrawable) {
        handler.post {
            if (sliderState == SliderState.EXPANDED) {
                controlPanelContainer?.background = drawable
            } else {
                handleView?.background = drawable
            }
        }
    }

    private fun getStateColor(s: State): Int {
        return when (s) {
            State.IDLE -> COLOR_IDLE
            State.RECORDING -> COLOR_RECORDING
            State.TRANSCRIBING -> COLOR_BUSY
            State.LOADING_MODEL -> COLOR_LOADING
        }
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
                    rootWrapper?.visibility = View.GONE
                } else if (intent.action == android.content.Intent.ACTION_SCREEN_ON) {
                    if (isLocked && !showOnLock) {
                        rootWrapper?.visibility = View.GONE
                    } else {
                        rootWrapper?.visibility = View.VISIBLE
                    }
                } else if (intent.action == android.content.Intent.ACTION_USER_PRESENT) {
                    rootWrapper?.visibility = View.VISIBLE
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
            ServiceLogger.d(TAG, "Pref changed: $key")
            if (key == "show_on_lock_screen") {
                val settings = com.mhm.speaktowrite.models.SettingsManager(this)
                val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                val isLocked = km.isKeyguardLocked
                
                if (isLocked && !settings.showOnLockScreen) {
                    rootWrapper?.visibility = View.GONE
                } else {
                    rootWrapper?.visibility = View.VISIBLE
                }
            } else if (key == "api_key" || key == "is_api_key_valid" || key == "slider_opacity" || key == "slider_is_left_edge") {
                // Debounce: cancel any pending rebuild and schedule a fresh one.
                // This prevents a rapid burst of pref changes (e.g. dragging the
                // opacity slider) from flooding the WindowManager with rebuild calls.
                handler.removeCallbacksAndMessages(overlayRebuildToken)
                handler.postDelayed(
                    /* r= */ {
                        // Guard: ensure service is still alive before rebuilding
                        if (instance != null) {
                            ServiceLogger.d(TAG, "Rebuilding overlay after pref change: $key")
                            removeOverlay()
                            showOverlay()
                        }
                    },
                    /* token= */ overlayRebuildToken,
                    /* delayMillis= */ 120L   // 120 ms debounce
                )
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
