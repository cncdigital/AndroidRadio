package mx.sntss1puebla.credenciales

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.os.PowerManager
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Decorative, local Winamp-inspired motion. No microphone, audio effects or network access. */
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
    private var seconds = 0f
    private var lastFrame = 0L
    private var scheduled = false
    private val frame = object : Runnable {
        override fun run() {
            scheduled = false
            if (!shouldAnimate()) { lastFrame = 0L; return }
            val now = SystemClock.uptimeMillis()
            if (lastFrame != 0L) seconds += ((now - lastFrame).coerceIn(0L, 100L)) / 1000f
            lastFrame = now
            invalidate()
            scheduled = true
            postDelayed(this, 42L) // At most 24 fps; drawing never drives audio or lyric timing.
        }
    }

    private fun shouldAnimate(): Boolean = motionActive && style != RadioVisualStyle.COVER &&
        isAttachedToWindow && windowVisibility == VISIBLE && isShown &&
        ValueAnimator.areAnimatorsEnabled() && power?.isPowerSaveMode != true && getGlobalVisibleRect(visibleArea)

    private fun syncAnimation() {
        if (shouldAnimate()) {
            if (!scheduled) { scheduled = true; post(frame) }
        } else {
            removeCallbacks(frame)
            scheduled = false
            lastFrame = 0L
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        scheduled = false
        lastFrame = 0L
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        spectrumGradient = LinearGradient(0f, h.toFloat(), 0f, 0f,
            intArrayOf(0xff36e6bc.toInt(), 0xff48c9ff.toInt(), 0xffb87bff.toInt(), 0xffffb653.toInt()),
            null, Shader.TileMode.CLAMP)
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
    }

    private fun drawBars(canvas: Canvas) {
        val step = width / 28f
        paint.shader = spectrumGradient
        for (i in 0 until 28) {
            val energy = 0.13f + 0.55f * ((sin(seconds * 2.2f + i * 0.46f) + 1f) / 2f) +
                0.18f * ((cos(seconds * 1.3f - i * 0.8f) + 1f) / 2f)
            val top = height * (1f - energy)
            paint.alpha = 215
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
                val y = height * (0.5f + sin(x * 7f + seconds + wave * 0.5f) * 0.19f +
                    sin(x * 15f - seconds * 1.4f + wave) * 0.07f)
                if (point == 0) path.moveTo(0f, y) else path.lineTo(x * width, y)
            }
            paint.color = colors[wave % colors.size]
            paint.strokeWidth = 9f * resources.displayMetrics.density
            paint.alpha = 24
            canvas.drawPath(path, paint)
            paint.strokeWidth = 1.6f * resources.displayMetrics.density
            paint.alpha = 200
            canvas.drawPath(path, paint)
        }
    }

    private fun drawTunnel(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * resources.displayMetrics.density
        val cx = width * (0.5f + sin(seconds * 0.4f) * 0.08f)
        val cy = height * (0.5f + cos(seconds * 0.5f) * 0.08f)
        for (ring in 0 until 18) {
            val depth = ((ring / 18f + seconds * 0.12f) % 1f)
            val rx = width * depth * depth * 0.85f
            val ry = height * depth * depth * 0.75f
            paint.color = colors[ring % colors.size]
            paint.alpha = (220f * sin(depth * PI).toFloat()).toInt().coerceIn(0, 255)
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        }
    }

    private fun drawAurora(canvas: Canvas) {
        for (ribbon in 0 until 5) {
            path.rewind()
            for (point in 0..64) {
                val x = point / 64f
                val y = height * (0.35f + ribbon * 0.06f + sin(x * 4f + seconds * 0.45f + ribbon) * 0.18f)
                if (point == 0) path.moveTo(0f, y) else path.lineTo(x * width, y)
            }
            for (point in 64 downTo 0) {
                val x = point / 64f
                val y = height * (0.47f + ribbon * 0.06f + sin(x * 4f + seconds * 0.45f + ribbon + 0.4f) * 0.18f)
                path.lineTo(x * width, y)
            }
            path.close()
            paint.color = colors[ribbon % colors.size]
            paint.alpha = 65
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
            paint.alpha = ((1f - depth) * 220f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, (0.6f + (1f - depth) * 2f) * resources.displayMetrics.density, paint)
        }
    }
}
