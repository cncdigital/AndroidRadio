package mx.sntss1puebla.credenciales

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityManager

/** Owns only fullscreen chrome; artwork and the independently draggable lyric are never targets. */
internal class RadioFullscreenControls(
    private val root: RadioFullscreenFrame,
    private val controls: List<View>,
    private val hideDelayMillis: Long = 4_000L,
    private val fadeDurationMillis: Long = 280L,
    private val touchExplorationEnabled: () -> Boolean = {
        (root.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    },
    private val keyboardNavigationActive: () -> Boolean = { !root.isInTouchMode && root.hasFocus() }
) {
    private val handler = Handler(Looper.getMainLooper())
    private val accessibility = root.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private var active = false
    private var disposed = false
    private var touching = false
    private var generation = 0
    private var fade: ValueAnimator? = null
    private val hide = Runnable { if (canHide()) fadeOut() }
    private val explorationListener = AccessibilityManager.TouchExplorationStateChangeListener { show() }

    init {
        root.onTouchInteraction = { down ->
            touching = down
            show()
        }
        root.onKeyInteraction = ::show
        root.onWindowDetached = ::stop
    }

    fun start() {
        if (disposed || active) return
        active = true
        accessibility.addTouchExplorationStateChangeListener(explorationListener)
        show()
    }

    fun stop() {
        if (active) accessibility.removeTouchExplorationStateChangeListener(explorationListener)
        active = false
        touching = false
        cancelPendingWork()
        restoreControls()
    }

    fun dispose() {
        stop()
        disposed = true
        root.onTouchInteraction = null
        root.onKeyInteraction = null
        root.onWindowDetached = null
    }

    fun show() {
        if (!active || disposed) return
        cancelPendingWork()
        restoreControls()
        if (canHide()) handler.postDelayed(hide, hideDelayMillis)
    }

    private fun canHide(): Boolean =
        active && !disposed && !touching && !touchExplorationEnabled() && !keyboardNavigationActive()

    private fun cancelPendingWork() {
        // cancel() also dispatches onAnimationEnd. Invalidate it BEFORE cancellation so
        // an old fade can never hide newly revealed controls or a reopened dialog.
        generation++
        handler.removeCallbacks(hide)
        fade?.cancel()
        fade = null
    }

    private fun restoreControls() {
        controls.forEach {
            it.alpha = 1f
            it.visibility = View.VISIBLE
        }
    }

    private fun fadeOut() {
        val operation = ++generation
        val animation = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = fadeDurationMillis
            addUpdateListener {
                if (operation == generation) {
                    val alpha = it.animatedValue as Float
                    controls.forEach { control -> control.alpha = alpha }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (operation != generation) return
                    fade = null
                    if (canHide()) {
                        controls.forEach {
                            it.alpha = 0f
                            it.visibility = View.INVISIBLE
                        }
                    } else {
                        restoreControls()
                    }
                }
            })
        }
        fade = animation
        animation.start()
    }
}
