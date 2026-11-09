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
        assertEquals(0, context.resources.getIdentifier("radio_background", "id", context.packageName))
        assertEquals(0, context.resources.getIdentifier("full_background", "id", context.packageName))
        assertSame(full, full.findViewById<View>(R.id.full_lyric).parent)
        assertNotSame(full.findViewById<View>(R.id.full_controls), full.findViewById<View>(R.id.full_lyric).parent)
        for (id in listOf(R.id.previous_song, R.id.play_native_radio, R.id.next_song)) {
            assertEquals(View.VISIBLE, normal.findViewById<View>(id).visibility)
            assertEquals(1f, normal.findViewById<View>(id).alpha, 0f)
        }
    }

    @Test fun repeatedSwipesDoNotChangeLyricAndCancelDoesNotChooseBackground() = instrumentation.runOnMainSync {
        val context = instrumentation.targetContext
        val frame = RadioArtworkFrame(context)
        val lyric = TextView(context).apply { text = "La letra sigue aquí" }
        frame.addView(lyric)
        var style = RadioVisualStyle.COVER
        var changes = 0
        var taps = 0
        frame.onBackgroundSwipe = { style = style.shifted(it); changes++ }
        frame.setOnClickListener { taps++ }
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
        assertEquals(0, taps)
        assertEquals("La letra sigue aquí", lyric.text.toString())
        event(MotionEvent.ACTION_DOWN, 20f)
        event(MotionEvent.ACTION_UP, 20f)
        assertEquals(1, taps)
        // Returning to the starting point after a drag must not become a tap.
        event(MotionEvent.ACTION_DOWN, 20f)
        event(MotionEvent.ACTION_MOVE, distance)
        event(MotionEvent.ACTION_UP, 20f)
        assertEquals(1, taps)
        assertEquals(6, changes)
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

    private fun field(@Suppress("UNUSED_PARAMETER") view: RadioVisualizerView, name: String) =
        RadioVisualizerView::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun everyStyleRespondsToPcmEnergyAtTheSameAnimationPhase() = instrumentation.runOnMainSync {
        val view = RadioVisualizerView(instrumentation.targetContext)
        view.layout(0, 0, 360, 480)
        val audio = field(view, "audio").get(view) as RadioAudioFrame
        field(view, "hasPcm").setBoolean(view, true)
        field(view, "audible").setBoolean(view, true)
        for (style in RadioVisualStyle.entries.filter { it != RadioVisualStyle.COVER }) {
            view.style = style
            val quiet = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
            val loud = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
            for (name in arrayOf("energy", "bass", "treble")) field(view, name).setFloat(view, 0f)
            audio.bands.fill(0f)
            view.draw(Canvas(quiet))
            for (name in arrayOf("energy", "bass", "treble")) field(view, name).setFloat(view, 0.85f)
            audio.bands.fill(0.85f)
            view.draw(Canvas(loud))
            assertFalse("${style.id} must react to PCM without advancing time", quiet.sameAs(loud))
            assertEquals(0f, field(view, "seconds").getFloat(view), 0f)
            quiet.recycle()
            loud.recycle()
        }
    }

    @Test fun spectrumUsesIndividualFrequencyBands() = instrumentation.runOnMainSync {
        val view = RadioVisualizerView(instrumentation.targetContext)
        view.layout(0, 0, 560, 480)
        view.style = RadioVisualStyle.BARS
        val audio = field(view, "audio").get(view) as RadioAudioFrame
        field(view, "audible").setBoolean(view, true)
        audio.bands[2] = 1f
        val low = Bitmap.createBitmap(560, 480, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(low))
        audio.bands[2] = 0f
        audio.bands[24] = 1f
        val high = Bitmap.createBitmap(560, 480, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(high))
        assertNotEquals(low.getPixel(50, 240), high.getPixel(50, 240))
        assertNotEquals(low.getPixel(490, 240), high.getPixel(490, 240))
        low.recycle()
        high.recycle()
    }

    @Test fun silenceFreezesPhaseAndResumingDoesNotJump() = instrumentation.runOnMainSync {
        val view = RadioVisualizerView(instrumentation.targetContext)
        val advance = RadioVisualizerView::class.java.getDeclaredMethod("advanceAudioPhase", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }
        field(view, "energy").setFloat(view, 1f)
        advance.invoke(view, 1000L)
        advance.invoke(view, 9000L)
        assertEquals(0f, field(view, "seconds").getFloat(view), 0f)
        field(view, "audible").setBoolean(view, true)
        advance.invoke(view, 10000L)
        assertEquals(0f, field(view, "seconds").getFloat(view), 0f)
        advance.invoke(view, 10042L)
        val movingPhase = field(view, "seconds").getFloat(view)
        assertTrue(movingPhase > 0f)
        field(view, "audible").setBoolean(view, false)
        advance.invoke(view, 11000L)
        advance.invoke(view, 20000L)
        assertEquals(movingPhase, field(view, "seconds").getFloat(view), 0f)
        field(view, "audible").setBoolean(view, true)
        advance.invoke(view, 25000L)
        assertEquals(movingPhase, field(view, "seconds").getFloat(view), 0f)
    }

    @Test fun detachedOrHiddenViewNeverSchedulesFrames() = instrumentation.runOnMainSync {
        val view = RadioVisualizerView(instrumentation.targetContext)
        view.layout(0, 0, 360, 480)
        view.style = RadioVisualStyle.STARS
        view.motionActive = true
        assertFalse(field(view, "scheduled").getBoolean(view))
        view.visibility = View.GONE
        (field(view, "frame").get(view) as Runnable).run()
        assertFalse(field(view, "scheduled").getBoolean(view))
        assertEquals(0f, field(view, "seconds").getFloat(view), 0f)
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
