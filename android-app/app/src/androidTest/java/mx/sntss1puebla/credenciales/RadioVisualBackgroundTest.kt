package mx.sntss1puebla.credenciales

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioVisualBackgroundTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun normalAndFullscreenLayoutsKeepLyricsAboveBackground() = instrumentation.runOnMainSync {
        val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_NoActionBar)
        val normal = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        val frame = normal.findViewById<RadioArtworkFrame>(R.id.radio_artwork)
        val lyric = normal.findViewById<TextView>(R.id.ghost_lyric)
        assertSame(frame, lyric.parent)
        assertTrue(frame.indexOfChild(lyric) > frame.indexOfChild(normal.findViewById(R.id.radio_visualizer)))
        val full = LayoutInflater.from(context).inflate(R.layout.dialog_radio_fullscreen, null) as android.widget.FrameLayout
        assertTrue(full.indexOfChild(full.findViewById(R.id.full_lyric)) > full.indexOfChild(full.findViewById(R.id.full_artwork)))
        assertNotNull(normal.findViewById<View>(R.id.radio_background))
        assertNotNull(full.findViewById<View>(R.id.full_background))
    }

    @Test fun repeatedSwipesDoNotChangeLyricAndCancelDoesNotChooseBackground() = instrumentation.runOnMainSync {
        val context = instrumentation.targetContext
        val frame = RadioArtworkFrame(context)
        val lyric = TextView(context).apply { text = "La letra sigue aquí" }
        frame.addView(lyric)
        var style = RadioVisualStyle.COVER
        var changes = 0
        frame.onBackgroundSwipe = { style = style.shifted(it); changes++ }
        val distance = 120f * context.resources.displayMetrics.density
        fun event(action: Int, x: Float, y: Float = 20f) {
            val touch = MotionEvent.obtain(100, 120, action, x, y, 0)
            frame.onTouchEvent(touch)
            touch.recycle()
        }
        repeat(6) {
            event(MotionEvent.ACTION_DOWN, distance + 20f)
            event(MotionEvent.ACTION_MOVE, 20f)
            event(MotionEvent.ACTION_UP, 20f)
        }
        assertEquals(6, changes)
        assertEquals(RadioVisualStyle.COVER, style)
        assertEquals("La letra sigue aquí", lyric.text.toString())
        event(MotionEvent.ACTION_DOWN, distance + 20f)
        event(MotionEvent.ACTION_MOVE, 20f)
        event(MotionEvent.ACTION_CANCEL, 20f)
        event(MotionEvent.ACTION_UP, 20f)
        assertEquals(6, changes)
        event(MotionEvent.ACTION_DOWN, 20f)
        event(MotionEvent.ACTION_MOVE, 25f, distance)
        event(MotionEvent.ACTION_UP, distance, distance)
        assertEquals(6, changes)
    }

    @Test fun everyAnimatedStyleRendersWithoutAudioOrPermissions() = instrumentation.runOnMainSync {
        val view = RadioVisualizerView(instrumentation.targetContext)
        view.layout(0, 0, 360, 480)
        for (style in RadioVisualStyle.entries.filter { it != RadioVisualStyle.COVER }) {
            view.style = style
            view.motionActive = false
            val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(360 * 480)
            bitmap.getPixels(pixels, 0, 360, 0, 0, 360, 480)
            assertTrue("${style.id} must contain visible artwork", pixels.toSet().size > 8)
            bitmap.recycle()
        }
    }

    @Test fun scrollViewAllowsHorizontalSwipesAndKeepsVerticalScrolling() = instrumentation.runOnMainSync {
        val context = instrumentation.targetContext
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val scroll = android.widget.ScrollView(context)
        val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val frame = RadioArtworkFrame(context)
        var changes = 0
        frame.onBackgroundSwipe = { changes++ }
        column.addView(frame, android.widget.LinearLayout.LayoutParams(dp(400), dp(280)))
        column.addView(View(context), android.widget.LinearLayout.LayoutParams(dp(400), dp(1000)))
        scroll.addView(column)
        scroll.measure(View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(300), View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, dp(400), dp(300))
        var time = android.os.SystemClock.uptimeMillis()
        val down = time
        fun dispatch(action: Int, x: Int, y: Int) {
            time += 16
            val event = MotionEvent.obtain(down, time, action, dp(x).toFloat(), dp(y).toFloat(), 0)
            scroll.dispatchTouchEvent(event)
            event.recycle()
        }
        dispatch(MotionEvent.ACTION_DOWN, 300, 140)
        dispatch(MotionEvent.ACTION_MOVE, 120, 155) // Enough dy for ScrollView's normal touch slop.
        dispatch(MotionEvent.ACTION_UP, 100, 158)
        assertEquals(1, changes)
        assertEquals(0, scroll.scrollY)
        dispatch(MotionEvent.ACTION_DOWN, 200, 200)
        dispatch(MotionEvent.ACTION_MOVE, 202, 140)
        dispatch(MotionEvent.ACTION_MOVE, 204, 60)
        dispatch(MotionEvent.ACTION_MOVE, 205, 20)
        dispatch(MotionEvent.ACTION_UP, 205, 20)
        assertEquals(1, changes)
        assertTrue("Vertical scrolling must be released to the parent", scroll.scrollY > 0)
    }
}
