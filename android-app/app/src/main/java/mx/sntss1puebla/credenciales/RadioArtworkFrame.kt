package mx.sntss1puebla.credenciales

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import kotlin.math.abs

/** A background-only gesture surface; foreground lyric dragging and controls remain independent. */
class RadioArtworkFrame @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var onBackgroundSwipe: ((Int) -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistance = 48f * resources.displayMetrics.density
    private var startX = 0f
    private var startY = 0f
    private var pointerId = -1
    private var horizontal = false
    private var canceled = false
    private var moved = false

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (onBackgroundSwipe != null) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                R.id.radio_background_previous, context.getString(R.string.radio_visual_previous)))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                R.id.radio_background_next, context.getString(R.string.radio_visual_next)))
        }
        if (isClickable) info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.radio_show_controls)))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val direction = when (action) {
            R.id.radio_background_previous -> -1
            R.id.radio_background_next -> 1
            else -> return super.performAccessibilityAction(action, arguments)
        }
        val change = onBackgroundSwipe ?: return false
        change(direction)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                pointerId = event.getPointerId(0)
                horizontal = false
                canceled = false
                moved = false
                // ScrollView sees MOVE before this child. Reserve the gesture until its
                // direction is known, then immediately give vertical scrolling back.
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0 || canceled) return true
                val dx = event.getX(index) - startX
                val dy = event.getY(index) - startY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                if (!horizontal && moved) {
                    horizontal = abs(dx) > abs(dy) * 1.5f
                    canceled = !horizontal
                    parent?.requestDisallowInterceptTouchEvent(horizontal)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                canceled = true
                pointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (!canceled && index >= 0) {
                    val dx = event.getX(index) - startX
                    val dy = event.getY(index) - startY
                    val direction = RadioBackgroundGesture.direction(dx, dy, swipeDistance)
                    if (direction != 0) onBackgroundSwipe?.invoke(direction)
                    else if (!moved && abs(dx) < touchSlop && abs(dy) < touchSlop) performClick()
                }
                pointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
