package mx.sntss1puebla.credenciales

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.os.PowerManager
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Music PCM drives local artwork. No microphone, audio effects or network access. */
class RadioVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var style: RadioVisualStyle = RadioVisualStyle.COVER
        set(value) { field = value; invalidate(); syncAnimation() }
    var motionActive = false
        set(value) { field = value; syncAnimation() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val visibleArea = Rect()
    private val power = context.getSystemService(PowerManager::class.java)
    private var spectrumGradient: LinearGradient? = null
    private val colors = intArrayOf(0xff47e9ff.toInt(), 0xff9c77ff.toInt(), 0xffff5fb7.toInt(), 0xff46efbd.toInt())
    private val audio = RadioAudioFrame()
    private var hasPcm = false
    private var audible = false
    private var energy = 0f
    private var bass = 0f
    private var treble = 0f
    private var seconds = 0f
    private var lastFrame = 0L
    private var scheduled = false
    private var observing = false
    private var ready = false
    private val density = resources.displayMetrics.density
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { syncAnimation() }
    }
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { syncAnimation() }
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { syncAnimation() }
    private val frame = object : Runnable {
        override fun run() {
            scheduled = false
            if (!shouldAnimate()) {
                RadioAudioAnalysis.setConsumerActive(this@RadioVisualizerView, false)
                lastFrame = 0L
                invalidate()
                return
            }
            readAudio()
            advanceAudioPhase(SystemClock.uptimeMillis())
            invalidate()
            scheduled = true
            // Poll idle PCM slowly to recover automatically, only while visible and playing.
            postDelayed(this, if (audible) 42L else 200L)
        }
    }

    init { ready = true }

    private fun advanceAudioPhase(now: Long) {
        // Integrate music energy, not wall time. Silence also resets the time baseline.
        if (audible) {
            if (lastFrame != 0L) {
                seconds += ((now - lastFrame).coerceIn(0L, 100L)) / 1000f *
                    (energy * 1.5f + bass * 0.8f + treble * 0.3f)
            }
            lastFrame = now
        } else {
            lastFrame = 0L
        }
    }

    private fun readAudio() {
        hasPcm = RadioAudioAnalysis.read(audio)
        audible = hasPcm && audio.available && (audio.rms > 0.001f || audio.peak > 0.003f)
        energy = if (audible) (audio.rms * 3.5f + audio.peak * 0.15f).coerceIn(0f, 1f) else 0f
        bass = if (audible) audio.bass else 0f
        treble = if (audible) audio.treble else 0f
    }

    private fun shouldAnimate(): Boolean = motionActive && style != RadioVisualStyle.COVER &&
        isAttachedToWindow && windowVisibility == VISIBLE && isShown &&
        ValueAnimator.areAnimatorsEnabled() && power?.isPowerSaveMode != true && getGlobalVisibleRect(visibleArea)

    private fun syncAnimation() {
        if (!ready) return // View's constructor can dispatch visibility callbacks.
        val active = shouldAnimate()
        RadioAudioAnalysis.setConsumerActive(this, active)
        if (active) {
            if (!scheduled) { scheduled = true; post(frame) }
        } else {
            removeCallbacks(frame)
            scheduled = false
            lastFrame = 0L
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!observing) {
            ContextCompat.registerReceiver(context, powerReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            observing = true
        }
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        syncAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimation()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        syncAnimation()
    }

    override fun onDetachedFromWindow() {
        RadioAudioAnalysis.setConsumerActive(this, false)
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        removeCallbacks(frame)
        scheduled = false
        lastFrame = 0L
        if (observing) {
            context.unregisterReceiver(powerReceiver)
            context.contentResolver.unregisterContentObserver(motionObserver)
            observing = false
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        spectrumGradient = LinearGradient(0f, h.toFloat(), 0f, 0f,
            intArrayOf(0xff36e6bc.toInt(), 0xff48c9ff.toInt(), 0xffb87bff.toInt(), 0xffffb653.toInt()),
            null, Shader.TileMode.CLAMP)
        syncAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xff061326.toInt())
        if (width <= 0 || height <= 0) return
        paint.reset()
        paint.isAntiAlias = true
        when (style) {
            RadioVisualStyle.COVER -> Unit
            RadioVisualStyle.BARS -> drawBars(canvas)
            RadioVisualStyle.WAVES -> drawWaves(canvas)
            RadioVisualStyle.TUNNEL -> drawTunnel(canvas)
            RadioVisualStyle.AURORA -> drawAurora(canvas)
            RadioVisualStyle.STARS -> drawStars(canvas)
        }
        // Gentle permanent scrim reduces competition with foreground text.
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x22020a18
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (style != RadioVisualStyle.COVER) drawStatus(canvas)
    }

    private fun drawStatus(canvas: Canvas) {
        val status = when {
            !motionActive -> "En pausa"
            power?.isPowerSaveMode == true -> "Ahorro de batería · fondo estático"
            !ValueAnimator.areAnimatorsEnabled() -> "Movimiento reducido · fondo estático"
            !hasPcm -> "Esperando audio"
            !audible -> "Sin señal de audio"
            else -> return
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xd9061326.toInt()
        val textSize = min(12f * density, width / 28f)
        val inset = 10f * density
        paint.textSize = textSize
        val labelWidth = paint.measureText(status)
        canvas.drawRoundRect(inset, inset, inset + labelWidth + textSize * 1.5f,
            inset + textSize * 2.2f, textSize / 2f, textSize / 2f, paint)
        paint.color = 0xffd2dbea.toInt()
        canvas.drawText(status, inset + textSize * 0.75f, inset + textSize * 1.5f, paint)
    }

    private fun drawBars(canvas: Canvas) {
        val step = width / 28f
        paint.shader = spectrumGradient
        for (i in 0 until 28) {
            val level = if (audible) audio.bands[i] else 0f
            val top = height * (0.94f - level * 0.82f)
            paint.alpha = (100f + level * 155f).toInt()
            canvas.drawRoundRect(i * step + step * 0.15f, top, (i + 1) * step - step * 0.15f,
                height * 0.96f, step * 0.13f, step * 0.13f, paint)
            paint.alpha = 100
            canvas.drawRect(i * step + step * 0.15f, top - step * 0.6f,
                (i + 1) * step - step * 0.15f, top - step * 0.4f, paint)
        }
        paint.shader = null
    }

    private fun drawWaves(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        for (wave in 0 until 6) {
            path.rewind()
            for (point in 0..80) {
                val x = point / 80f
                val y = height * (0.5f + sin(x * 7f + seconds + wave * 0.5f) * (0.04f + bass * 0.22f) +
                    sin(x * 15f - seconds * 1.4f + wave) * (0.015f + treble * 0.12f))
                if (point == 0) path.moveTo(0f, y) else path.lineTo(x * width, y)
            }
            paint.color = colors[wave % colors.size]
            paint.strokeWidth = 9f * density
            paint.alpha = 24
            canvas.drawPath(path, paint)
            paint.strokeWidth = 1.6f * density
            paint.alpha = (90f + energy * 165f).toInt()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawTunnel(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        val cx = width * (0.5f + sin(seconds * 0.4f) * 0.08f)
        val cy = height * (0.5f + cos(seconds * 0.5f) * 0.08f)
        for (ring in 0 until 18) {
            val depth = ((ring / 18f + seconds * 0.12f) % 1f)
            val rx = width * depth * depth * (0.72f + bass * 0.25f)
            val ry = height * depth * depth * (0.62f + bass * 0.25f)
            paint.color = colors[ring % colors.size]
            paint.alpha = ((80f + energy * 175f) * sin(depth * PI).toFloat()).toInt().coerceIn(0, 255)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        }
    }

    private fun drawAurora(canvas: Canvas) {
        for (ribbon in 0 until 5) {
            path.rewind()
            for (point in 0..64) {
                val x = point / 64f
                val y = height * (0.35f + ribbon * 0.06f + sin(x * 4f + seconds * 0.45f + ribbon) * (0.05f + bass * 0.22f))
                if (point == 0) path.moveTo(0f, y) else path.lineTo(x * width, y)
            }
            for (point in 64 downTo 0) {
                val x = point / 64f
                val y = height * (0.47f + ribbon * 0.06f + sin(x * 4f + seconds * 0.45f + ribbon + 0.4f) * (0.05f + bass * 0.22f))
                path.lineTo(x * width, y)
            }
            path.close()
            paint.color = colors[ribbon % colors.size]
            paint.alpha = (35f + energy * 75f + treble * 25f).toInt()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawStars(canvas: Canvas) {
        val scale = min(width, height) * 0.5f
        for (star in 0 until 100) {
            val angle = star * 2.399963f
            val depth = 1f - ((star * 0.137f + seconds * 0.075f) % 1f)
            val distance = (0.08f + (star % 13) / 25f) / (depth + 0.1f)
            val x = width / 2f + cos(angle) * distance * scale
            val y = height / 2f + sin(angle) * distance * scale
            paint.color = colors[star % colors.size]
            paint.alpha = ((1f - depth) * (100f + energy * 155f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, (0.6f + (1f - depth) * (2f + treble * 3f)) * density, paint)
        }
    }
}
