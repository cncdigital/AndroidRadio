package mx.sntss1puebla.credenciales

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout

/** Observes interaction without intercepting artwork swipes, lyric drags or button clicks. */
class RadioFullscreenFrame @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    internal var onTouchInteraction: ((Boolean) -> Unit)? = null
    internal var onKeyInteraction: (() -> Unit)? = null
    internal var onWindowDetached: (() -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Pick the touch target BEFORE revealing controls. Otherwise a tap intended to
        // reveal the artwork could accidentally press a previously invisible button.
        val handled = super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchInteraction?.invoke(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchInteraction?.invoke(false)
        }
        return handled
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        onKeyInteraction?.invoke()
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        onWindowDetached?.invoke()
        super.onDetachedFromWindow()
    }
}
