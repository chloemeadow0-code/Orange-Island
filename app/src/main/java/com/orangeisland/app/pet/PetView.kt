package com.orangeisland.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.orangeisland.app.util.DebugLog
import kotlin.random.Random

/**
 * The animated desktop-pet character. A transparent [View] that draws one Mikan
 * sprite at a time and runs a lightweight behaviour loop:
 *
 *  - **Idle**: every few seconds randomly switches between a pool of idle
 *    expressions (smile, wink, blush…).
 *  - **Walk**: occasionally strolls a few steps left or right, flipping to the
 *    walking sprite and nudging the window via [Host.moveBy].
 *  - **Sleep**: if untouched for [SLEEP_AFTER_MS], dozes off (sleep_zzz) until
 *    the user interacts again.
 *
 * Touch:
 *  - **Drag**: the window follows the finger; the pet looks startled while held.
 *  - **Double-tap**: a friendly wave / heart one-shot.
 *
 * The view never touches [android.view.WindowManager] directly — all window
 * movement and persistence is delegated to [Host], which the owning service
 * implements. This keeps the view testable and free of system-service lookups.
 *
 * Sprites are drawn scaled to [TARGET_WIDTH_DP] wide (aspect preserved) so the
 * pet is a consistent physical size across screen densities.
 */
class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** Service-side hooks for moving the overlay window and reacting to taps. */
    interface Host {
        /** Translate the window by [dx]/[dy] px. Called frequently during drag/walk. */
        fun moveBy(dx: Int, dy: Int)
        /** A double-tap occurred — service may open chat or similar. */
        fun onDoubleTap()
        /** The pet has dozed off (optional hook for analytics/notifications). */
        fun onSleepChanged(asleep: Boolean) {}
    }

    private val density = context.resources.displayMetrics.density
    private val targetWidthPx = (TARGET_WIDTH_DP * density).toInt()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val random = Random(SystemClock.elapsedRealtime())

    private var host: Host? = null

    // ── Sprite state ──────────────────────────────────────────
    private var currentName: String = SPRITE_FRONT
    private var currentBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRectF = RectF()
    private val clearPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // ── Interaction state ────────────────────────────────────
    private var dragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastInteractionAt = SystemClock.elapsedRealtime()
    private var asleep = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            lastInteractionAt = SystemClock.elapsedRealtime()
            wakeUp()
            // Wave, then drift to a heart — a small affection burst.
            setSprite(SPRITE_WAVE, temporary = true) {
                setSprite(SPRITE_HEART, temporary = true) { relaxToIdle() }
            }
            host?.onDoubleTap()
            return true
        }
    })

    fun attachHost(host: Host) {
        this.host = host
    }

    /**
     * Pre-warm the initial sprite so the first frame is non-blank. Call once the
     * view is about to be added to the window.
     */
    fun prime() {
        ensureSprite(SPRITE_FRONT)
        currentName = SPRITE_FRONT
        currentBitmap = PetAssets.get(context, SPRITE_FRONT)
        currentBitmap?.let { srcRect.set(0, 0, it.width, it.height) }
        startLoop()
    }

    // ── Behaviour loop ────────────────────────────────────────
    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, IDLE_TICK_MS)
        }
    }

    private fun startLoop() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.postDelayed(tickRunnable, IDLE_TICK_MS)
    }

    private fun stopLoop() {
        mainHandler.removeCallbacks(tickRunnable)
    }

    private fun tick() {
        if (dragging) return
        val now = SystemClock.elapsedRealtime()
        // Long-idle → sleep. Once asleep, mostly stay that way until touched.
        if (!asleep && now - lastInteractionAt > SLEEP_AFTER_MS) {
            fallAsleep()
            return
        }
        if (asleep) return // waking is touch-driven only

        val roll = random.nextInt(100)
        when {
            roll < 18 -> {
                // Stroll a few steps. Pick a direction and walk.
                val dir = if (random.nextBoolean()) 1 else -1
                startWalk(dir)
            }
            roll < 70 -> {
                // Switch to a random idle expression.
                setSprite(IDLE_POOL.random(random))
            }
            else -> {
                // Occasional food/drink cuteness.
                setSprite(FOOD_POOL.random(random), temporary = false)
            }
        }
    }

    /** Brief horizontal walk, returning to an idle stance on completion. */
    private var walking = false
    private fun startWalk(direction: Int) {
        if (walking) return
        walking = true
        setSprite(SPRITE_WALK)
        val steps = 8 + random.nextInt(8)
        val stepPx = (WALK_STEP_DP * density).toInt() * direction
        var taken = 0
        mainHandler.post(object : Runnable {
            override fun run() {
                if (taken >= steps || dragging) {
                    walking = false
                    setSprite(SPRITE_FRONT)
                    return
                }
                host?.moveBy(stepPx, 0)
                taken++
                mainHandler.postDelayed(this, WALK_INTERVAL_MS)
            }
        })
    }

    private fun fallAsleep() {
        asleep = true
        setSprite(SPRITE_SLEEP)
        host?.onSleepChanged(true)
    }

    /**
     * TG-style "incoming message" jiggle: the pet shakes in place a few times to
     * catch the eye. Implemented as a render-time horizontal offset (via a
     * decaying ValueAnimator) rather than moving the actual window, so it never
     * drifts off its saved position. Cancellable — a new nudge restarts the wobble.
     */
    private var wobbleAnim: android.animation.ValueAnimator? = null
    private var wobbleOffset = 0f
    fun wobble() {
        wakeUp()
        DebugLog.d(TAG, "wobble() called")
        wobbleAnim?.cancel()
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WOBBLE_DURATION_MS
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                // Damped sine: full-amplitude left/right swings that decay to ~0.
                val t = it.animatedValue as Float
                val decay = (1f - t).coerceAtLeast(0f)
                wobbleOffset = (Math.sin((t * WOBBLE_CYCLES * 2 * Math.PI).toDouble()).toFloat()) *
                    WOBBLE_AMPLITUDE_DP * density * decay
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    wobbleOffset = 0f
                    invalidate()
                }
            })
        }
        wobbleAnim = anim
        anim.start()
    }

    private fun wakeUp() {
        if (!asleep) return
        asleep = false
        host?.onSleepChanged(false)
    }

    /** Reset the idle/sleep clocks (call on any user interaction). */
    private fun noteInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        wakeUp()
    }

    /**
     * Apply a named sprite. When [temporary] is true, [after] runs once and can
     * restore a previous expression — used for transient reactions (wave/heart).
     */
    fun setSprite(name: String, temporary: Boolean = false, after: (() -> Unit)? = null) {
        ensureSprite(name)
        currentName = name
        currentBitmap = PetAssets.get(context, name)
        currentBitmap?.let { srcRect.set(0, 0, it.width, it.height) }
        invalidate()
        if (temporary && after != null) {
            mainHandler.postDelayed({ after() }, TEMP_SPRITE_MS)
        }
    }

    /** Force an expression by logical name (e.g. "heart", "cry"). Falls back to idle. */
    fun setExpression(name: String) {
        noteInteraction()
        setSprite(name, temporary = true) { relaxToIdle() }
    }

    private fun relaxToIdle() {
        if (dragging || asleep) return
        setSprite(IDLE_POOL.random(random))
    }

    private fun ensureSprite(name: String) {
        if (PetAssets.get(context, name) == null) {
            DebugLog.w(TAG, "Sprite '$name' missing, falling back to $SPRITE_FRONT")
        }
    }

    // ── Drawing ──────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bmp = currentBitmap
        val w: Int
        val h: Int
        if (bmp != null && bmp.width > 0) {
            val scale = targetWidthPx.toFloat() / bmp.width
            w = targetWidthPx
            h = (bmp.height * scale).toInt()
        } else {
            w = targetWidthPx
            h = (targetWidthPx * DEFAULT_ASPECT).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = currentBitmap ?: return
        if (bmp.isRecycled) return
        // Transparent background — the overlay window itself is FORMAT_RGBA_8888,
        // so unpainted pixels are see-through and only the character shows.
        canvas.drawColor(Color.TRANSPARENT)
        // Apply the wobble offset only to the sprite drawing, not the window, so
        // the character appears to shake within its bounds during a nudge.
        val dx = wobbleOffset
        dstRectF.set(dx, 0f, measuredWidth.toFloat() + dx, measuredHeight.toFloat())
        canvas.drawBitmap(bmp, srcRect, dstRectF, clearPaint)
    }

    // ── Touch: drag + double-tap ─────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                noteInteraction()
                dragging = true
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                setSprite(SPRITE_SURPRISED, temporary = true) { relaxToIdle() }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = (event.rawX - lastTouchX).toInt()
                    val dy = (event.rawY - lastTouchY).toInt()
                    if (dx != 0 || dy != 0) {
                        host?.moveBy(dx, dy)
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                lastInteractionAt = SystemClock.elapsedRealtime()
                relaxToIdle()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    companion object {
        private const val TAG = "PetView"

        // Sprite logical names (suffix after the index in the asset filename).
        const val SPRITE_FRONT = "front"
        private const val SPRITE_BACK = "back"
        private const val SPRITE_WALK = "walk"
        private const val SPRITE_WAVE = "wave"
        private const val SPRITE_HEART = "heart"
        private const val SPRITE_HOLD_HEART = "hold_heart"
        private const val SPRITE_SURPRISED = "surprised"
        private const val SPRITE_SLEEP = "sleep_zzz"
        private const val SPRITE_CRY = "cry"

        private val IDLE_POOL = listOf(
            SPRITE_FRONT, "smile", "wink_sparkle", "blush",
            "wink_tongue", "pout", "wide_eyes", "hiphop"
        )
        private val FOOD_POOL = listOf("eat_orange", "drink_juice")

        // Tunables.
        private const val TARGET_WIDTH_DP = 40
        private const val DEFAULT_ASPECT = 300f / 244f
        private const val IDLE_TICK_MS = 4000L
        private const val SLEEP_AFTER_MS = 60_000L
        private const val TEMP_SPRITE_MS = 1500L
        private const val WALK_STEP_DP = 6
        private const val WALK_INTERVAL_MS = 90L
        // Wobble (incoming-message jiggle) tuning.
        private const val WOBBLE_DURATION_MS = 600L
        private const val WOBBLE_CYCLES = 3f        // number of full left-right swings
        private const val WOBBLE_AMPLITUDE_DP = 6f  // peak swing magnitude
    }
}
