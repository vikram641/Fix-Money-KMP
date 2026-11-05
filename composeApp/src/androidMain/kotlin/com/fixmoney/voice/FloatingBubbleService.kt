package com.fixmoney.voice

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.fixmoney.MainActivity
import com.fixmoney.R
import com.fixmoney.shared.onboarding.AppPrefs
import com.fixmoney.shared.model.CurrencyConstants
import com.fixmoney.shared.coins.AiGate
import com.fixmoney.shared.coins.shortMessage
import com.fixmoney.ui.LaunchTarget
import com.fixmoney.shared.voice.VoiceAddResult
import com.fixmoney.shared.voice.VoiceQuickAddEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs

private const val TAG = "FloatingBubbleService"
private const val NOTIFICATION_CHANNEL_ID = "voice_quickadd_channel"
private const val NOTIFICATION_ID = 4201
private const val BUBBLE_SIZE_DP = 58

// The two theme tokens this overlay needs, as plain ints. The bubble is built
// from Android views in a window the Compose theme never reaches, so it can't
// read AppTheme.colors - these are accent and expense_red from Theme.kt, and
// have to be changed alongside them.
private const val COLOR_IDLE = 0xFF7B61FF.toInt()
private const val COLOR_LISTENING = 0xFFFF5C5C.toInt()
private const val COLOR_DISMISS_BG = 0xE6202833.toInt()

/** Idle behaviour: after this long untouched the bubble fades and tucks into its edge. */
private const val DORMANT_AFTER_MS = 4_000L
private const val DORMANT_ALPHA = 0.4f
/** How much of the bubble slides past the screen edge once dormant. */
private const val DORMANT_TUCK_FRACTION = 0.45f

private const val LONG_PRESS_MS = 450L
private const val DISMISS_VISIBLE_MS = 3_000L
/** Movement beyond this is a drag, not a tap. */
private const val TAP_SLOP_PX = 20f
/** Keeps the bubble clear of the very top and bottom of the screen. */
private const val EDGE_MARGIN_DP = 12

/**
 * Port of the old app's `FloatingBubbleService`: a draggable overlay bubble that
 * sits above every other app, listens when tapped, and files what you say as an
 * expense with no screen in between. A foreground service purely so the overlay
 * window and the mic survive while FixMoney itself isn't in the foreground.
 *
 * Android-only by nature - iOS has no overlay window and no third-party
 * equivalent - so it lives in androidMain. The part that isn't Android-specific
 * (parse -> match category -> save) is [VoiceQuickAddEngine] in shared code.
 *
 * Differences from the old service, both because the KMP module has no Android
 * view resources:
 * - the bubble is built in code instead of inflating `overlay_voice_bubble.xml`;
 * - listening is a pulsing icon (ValueAnimator) instead of a Lottie animation.
 *   Compottie is a Compose renderer and can't draw into a plain ImageView.
 *
 * Kept from the old one: Hilt is gone, so dependencies come from Koin via
 * [KoinComponent]; a Service isn't a Compose scope, so `koinInject()` doesn't
 * apply here.
 */
class FloatingBubbleService : Service(), KoinComponent {

    private val engine: VoiceQuickAddEngine by inject()
    private val prefs: AppPrefs by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FrameLayout
    private lateinit var bubbleBackground: ImageView
    private lateinit var bubbleIcon: ImageView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var idleDrawable: GradientDrawable
    private lateinit var listeningDrawable: GradientDrawable

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isBusy = false
    private var snapAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    // Drag-vs-tap detection
    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamX = 0
    private var downParamY = 0
    /** Whether the bubble was dormant when this gesture started - see onBubbleTapped. */
    private var downDormant = false
    private var longPressFired = false

    private var dormant = false
    private var onLeftEdge = true
    private var idleJob: Job? = null
    private var dismissJob: Job? = null
    private var dismissView: TextView? = null
    private var dismissParams: WindowManager.LayoutParams? = null

    private val longPressRunnable = Runnable { onLongPress() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Floating voice quick-add",
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.voice_bubble_notification_title))
            .setContentText(getString(R.string.voice_bubble_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun bubbleSizePx(): Int = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()

    private fun addBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val size = bubbleSizePx()
        // Built in code, not from a <shape> XML resource - in the old app a shape
        // drawable with no explicit <size> had an undefined intrinsic size and
        // silently failed to render at all inside a TYPE_APPLICATION_OVERLAY
        // window. An explicit setSize() sidesteps that.
        idleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(COLOR_IDLE)
            setSize(size, size)
        }
        listeningDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(COLOR_LISTENING)
            setSize(size, size)
        }

        bubbleBackground = ImageView(this).apply {
            setImageDrawable(idleDrawable)
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
        val iconSize = (24 * resources.displayMetrics.density).toInt()
        bubbleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        bubbleView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            addView(bubbleBackground)
            addView(bubbleIcon)
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        onLeftEdge = true
        // A third of the way down rather than a bare 300px: on a tall screen the
        // old constant parked it over the top card of whatever was behind it.
        layoutParams.y = clampY(screenHeightPx() / 3)

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = layoutParams.x
                    downParamY = layoutParams.y
                    downDormant = dormant
                    longPressFired = false
                    idleJob?.cancel()
                    hideDismissOption()
                    bubbleView.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) + abs(dy) > TAP_SLOP_PX) {
                        // Past the slop this is a drag, so it is no longer a
                        // long press - otherwise dragging would pop the Hide
                        // chip up mid-gesture.
                        bubbleView.removeCallbacks(longPressRunnable)
                        if (dormant) restoreFromDormant()
                    }
                    layoutParams.x = downParamX + dx.toInt()
                    layoutParams.y = clampY(downParamY + dy.toInt())
                    runCatching { windowManager.updateViewLayout(bubbleView, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    bubbleView.removeCallbacks(longPressRunnable)
                    val moved = abs(event.rawX - downRawX) + abs(event.rawY - downRawY)
                    when {
                        longPressFired -> scheduleDormant()
                        moved < TAP_SLOP_PX -> {
                            wakeUp()
                            // A tap on a tucked, faded bubble only brings it
                            // back - it does not also open the microphone.
                            // Waking and listening on the same tap means any
                            // accidental brush starts a recording.
                            if (!downDormant) onBubbleTapped()
                        }
                        else -> snapToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager.addView(bubbleView, layoutParams) }
            .onFailure { Log.w(TAG, "failed to add bubble overlay", it) }
        scheduleDormant()
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun screenWidthPx(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.width()
        } else {
            resources.displayMetrics.widthPixels
        }

    private fun screenHeightPx(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }

    /**
     * The status bar and navigation bar heights.
     *
     * These have to come out of the vertical range explicitly. `screenHeightPx`
     * reports the **whole display** - `maximumWindowMetrics` deliberately
     * includes the system bars - so clamping against it alone still let the
     * bubble settle underneath the navigation bar, where it is half hidden and
     * the buttons swallow the touches meant for it.
     */
    private fun systemBarInsets(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                // Ignoring visibility, not the current state: a bar that is
                // temporarily hidden (immersive video, say) must not widen the
                // range, or the bubble is stranded once it comes back.
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return insets.top to insets.bottom
        }
        return legacyBarHeight("status_bar_height") to legacyBarHeight("navigation_bar_height")
    }

    private fun legacyBarHeight(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /**
     * Keeps the bubble on screen vertically, clear of both system bars.
     *
     * The window carries FLAG_LAYOUT_NO_LIMITS so that it can tuck
     * *horizontally* past the edge when dormant - without this clamp that same
     * flag let a drag throw it off the top or bottom, where it could not be
     * retrieved at all.
     */
    private fun clampY(y: Int): Int {
        val margin = (EDGE_MARGIN_DP * resources.displayMetrics.density).toInt()
        val (topInset, bottomInset) = systemBarInsets()
        val min = topInset + margin
        val max = screenHeightPx() - bottomInset - bubbleSizePx() - margin
        return y.coerceIn(min, if (max < min) min else max)
    }

    private fun restingX(left: Boolean): Int =
        if (left) 0 else screenWidthPx() - bubbleSizePx()

    private fun dormantX(left: Boolean): Int {
        val tuck = (bubbleSizePx() * DORMANT_TUCK_FRACTION).toInt()
        return if (left) -tuck else screenWidthPx() - bubbleSizePx() + tuck
    }

    /** After a drag, animates the bubble to rest against whichever edge it's closer to. */
    private fun snapToNearestEdge() {
        val size = bubbleSizePx()
        onLeftEdge = layoutParams.x + size / 2 < screenWidthPx() / 2
        // Defensive: pulls the bubble back into range if anything ever parked it
        // outside, since only x is animated from here.
        layoutParams.y = clampY(layoutParams.y)
        animateX(restingX(onLeftEdge))
        scheduleDormant()
    }

    private fun animateX(targetX: Int) {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                runCatching { windowManager.updateViewLayout(bubbleView, layoutParams) }
                moveDismissOptionBesideBubble()
            }
            start()
        }
    }

    // ---- Dormancy ---------------------------------------------------------
    //
    // Left alone, the bubble sits on top of whatever is underneath it - on this
    // app's own screens it covered a card heading. Fading and tucking most of it
    // past the edge keeps it reachable without it being in the way; a tap brings
    // it straight back.

    private fun scheduleDormant() {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            delay(DORMANT_AFTER_MS)
            goDormant()
        }
    }

    private fun goDormant() {
        // Never while the mic is open or a save is in flight: a half-hidden
        // bubble during recording reads as the feature having crashed.
        if (dormant || isListening || isBusy) return
        dormant = true
        hideDismissOption()
        bubbleView.animate().alpha(DORMANT_ALPHA).setDuration(220).start()
        animateX(dormantX(onLeftEdge))
    }

    /** Visual half of waking, without touching the idle timer - used mid-drag. */
    private fun restoreFromDormant() {
        dormant = false
        bubbleView.animate().cancel()
        bubbleView.alpha = 1f
    }

    private fun wakeUp() {
        idleJob?.cancel()
        if (dormant) {
            restoreFromDormant()
            animateX(restingX(onLeftEdge))
        }
        scheduleDormant()
    }

    // ---- Long press -> "Hide bubble" --------------------------------------

    private fun onLongPress() {
        longPressFired = true
        wakeUp()
        showDismissOption()
    }

    /**
     * A second, separate overlay window rather than a badge inside the bubble's
     * own: the bubble window is sized exactly to the circle, so anything drawn
     * beside it would be clipped.
     */
    private fun showDismissOption() {
        hideDismissOption()
        val density = resources.displayMetrics.density
        val chip = TextView(this).apply {
            text = getString(R.string.voice_bubble_hide)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(
                (16 * density).toInt(), (11 * density).toInt(),
                (16 * density).toInt(), (11 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22 * density
                setColor(COLOR_DISMISS_BG)
            }
            setOnClickListener { dismissBubble() }
        }
        chip.measure(0, 0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // No FLAG_NOT_TOUCHABLE - unlike the bubble this one has to be
            // tappable - but it must not take focus or the app behind it loses
            // its keyboard.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        dismissView = chip
        dismissParams = params
        // Position before adding: moveDismissOptionBesideBubble() would call
        // updateViewLayout on a view the window manager has not seen yet.
        positionDismissChip()

        runCatching { windowManager.addView(chip, params) }
            .onFailure {
                Log.w(TAG, "failed to add dismiss chip", it)
                dismissView = null
                dismissParams = null
            }

        dismissJob?.cancel()
        dismissJob = serviceScope.launch {
            delay(DISMISS_VISIBLE_MS)
            hideDismissOption()
        }
    }

    /** Sets the chip's position from the bubble's, without touching the window. */
    private fun positionDismissChip() {
        val chip = dismissView ?: return
        val params = dismissParams ?: return
        val gap = (10 * resources.displayMetrics.density).toInt()
        val size = bubbleSizePx()

        // Always on the inward side, so it can't land off the screen edge.
        params.x = if (onLeftEdge) {
            layoutParams.x + size + gap
        } else {
            layoutParams.x - gap - chip.measuredWidth
        }
        params.y = layoutParams.y + (size - chip.measuredHeight) / 2
    }

    private fun moveDismissOptionBesideBubble() {
        val chip = dismissView ?: return
        val params = dismissParams ?: return
        positionDismissChip()
        runCatching { windowManager.updateViewLayout(chip, params) }
    }

    private fun hideDismissOption() {
        dismissJob?.cancel()
        dismissJob = null
        val chip = dismissView ?: return
        dismissView = null
        dismissParams = null
        runCatching { windowManager.removeView(chip) }
    }

    /**
     * Clears the stored preference as well as stopping the service, so Settings
     * doesn't go on claiming the bubble is on with nothing on screen - the same
     * trap `AndroidVoiceQuickAdd.refresh()` exists to close.
     */
    private fun dismissBubble() {
        prefs.setVoiceBubble(false)
        toast(getString(R.string.voice_bubble_hidden))
        stopSelf()
    }

    private fun onBubbleTapped() {
        // isBusy covers the window after speech ends while the parse/save round
        // trip is still running - the old service only guarded isListening, so a
        // second tap during that gap started a fresh recognition on top of it.
        if (isListening || isBusy) return
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input isn't available on this device")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        setListeningVisual(true)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}

            override fun onError(error: Int) {
                setListeningVisual(false)
                releaseRecognizer()
                toast("Didn't catch that — try again")
            }

            override fun onResults(results: android.os.Bundle?) {
                setListeningVisual(false)
                val spokenText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                releaseRecognizer()

                if (spokenText.isNullOrEmpty()) {
                    toast("Didn't catch that — try again")
                } else {
                    parseAndSave(spokenText)
                }
            }
        })

        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    private fun releaseRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun setListeningVisual(listening: Boolean) {
        isListening = listening
        // Hold the bubble awake and fully opaque for the whole recording, then
        // let the idle timer take over again.
        if (listening) {
            idleJob?.cancel()
            restoreFromDormant()
        } else {
            scheduleDormant()
        }
        bubbleBackground.setImageDrawable(if (listening) listeningDrawable else idleDrawable)

        pulseAnimator?.cancel()
        if (listening) {
            pulseAnimator = ValueAnimator.ofFloat(1f, 0.55f).apply {
                duration = 600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val v = it.animatedValue as Float
                    bubbleIcon.alpha = v
                    bubbleIcon.scaleX = v
                    bubbleIcon.scaleY = v
                }
                start()
            }
        } else {
            pulseAnimator = null
            bubbleIcon.alpha = 1f
            bubbleIcon.scaleX = 1f
            bubbleIcon.scaleY = 1f
        }
    }

    private fun parseAndSave(spokenText: String) {
        isBusy = true
        serviceScope.launch {
            try {
                when (val result = engine.addFromSpeech(spokenText)) {
                    is VoiceAddResult.Saved -> toast(
                        "✓ ${CurrencyConstants.symbolFor(result.currency)}${result.amount}" +
                            " • ${result.categoryName}"
                    )
                    is VoiceAddResult.NoAmount -> toast(
                        "Heard “${result.spokenText}” but no amount — try again",
                        long = true
                    )
                    is VoiceAddResult.Failed -> toast(
                        "Couldn't add that: ${result.reason}",
                        long = true
                    )
                    is VoiceAddResult.Blocked -> {
                        toast(result.gate.shortMessage(), long = true)
                        // Open the app on the sheet that offers the way on - a
                        // video, or Fixoo Premium - rather than only saying so.
                        // Allowed from the background: this app holds "display
                        // over other apps". Busy and Premium's own cap have
                        // nothing to offer, so those stay a toast.
                        val target = when (result.gate) {
                            AiGate.NoCoins -> LaunchTarget.OUT_OF_COINS
                            AiGate.TrialLimit -> LaunchTarget.TRIAL_LIMIT
                            else -> null
                        }
                        if (target != null) {
                            startActivity(
                                Intent(this@FloatingBubbleService, MainActivity::class.java)
                                    .putExtra(LaunchTarget.EXTRA, target)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            } finally {
                isBusy = false
            }
        }
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(
            this,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseRecognizer()
        snapAnimator?.cancel()
        pulseAnimator?.cancel()
        idleJob?.cancel()
        if (::bubbleView.isInitialized) bubbleView.removeCallbacks(longPressRunnable)
        hideDismissOption()
        serviceScope.cancel()
        runCatching {
            if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
