package mx.sntss1puebla.credenciales

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioIconControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun normal(width: Int): View {
        val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_NoActionBar)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
        val density = context.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((900 * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }
    private fun bounds(root: View, child: View): Rect {
        val location = IntArray(2); val base = IntArray(2)
        child.getLocationOnScreen(location); root.getLocationOnScreen(base)
        return Rect(location[0]-base[0], location[1]-base[1], location[0]-base[0]+child.width, location[1]-base[1]+child.height)
    }
    @Test fun windowIconsAreTopRightAccessibleAndStayAboveScrollingContent() = instrumentation.runOnMainSync {
        for (width in listOf(320, 420, 600)) {
            val root = normal(width)
            val ids = listOf(R.id.close_radio, R.id.minimize_radio, R.id.maximize_radio)
            val buttons = ids.map { root.findViewById<ImageButton>(it) }
            val density = root.resources.displayMetrics.density
            buttons.forEach { button ->
                assertNotNull(button.drawable); assertFalse(button.contentDescription.isNullOrBlank())
                assertTrue(button.isFocusable); assertTrue(button.isClickable)
                assertTrue(button.width >= 48 * density); assertTrue(button.height >= 48 * density)
            }
            val rectangles = buttons.map { bounds(root, it) }
            assertTrue(rectangles[0].left >= root.width / 3)
            rectangles.zipWithNext().forEach { (a,b) -> assertTrue(a.right <= b.left) }
            assertTrue(rectangles.last().right <= root.width)
            assertTrue(rectangles.all { it.bottom < 100 * density })
            val scroll = (root as android.view.ViewGroup).getChildAt(1) as ScrollView
            scroll.scrollTo(0, 300)
            assertEquals(rectangles, buttons.map { bounds(root, it) })
            var clicks = 0
            buttons.forEach { it.setOnClickListener { clicks++ }; it.performClick() }
            assertEquals(3, clicks)
        }
    }
    @Test fun transportFitsSmallScreenAndPlaybackIconDescribesCurrentAction() = instrumentation.runOnMainSync {
        val root = normal(320)
        val buttons = listOf(R.id.previous_song, R.id.play_native_radio, R.id.next_song).map { root.findViewById<ImageButton>(it) }
        val rectangles = buttons.map { bounds(root,it) }
        assertTrue(rectangles.first().left >= 0); assertTrue(rectangles.last().right <= root.width)
        rectangles.zipWithNext().forEach { (a,b) -> assertTrue(a.right <= b.left) }
        assertTrue(buttons[1].width > buttons[0].width)
        val play = buttons[1]
        for (playing in listOf(false,true,false,true)) {
            RadioControlIcons.renderPlayback(play, playing)
            val expected = root.context.getString(if (playing) R.string.radio_paused else R.string.play_radio_car)
            assertEquals(expected, play.contentDescription); assertEquals(expected, play.tooltipText)
            assertNotNull(play.drawable)
        }
    }
    @Test fun renderNormalControlsForReview() = instrumentation.runOnMainSync {
        val root = normal(420)
        root.findViewById<TextView>(R.id.song_title).text = "Radio Sindical"
        root.findViewById<TextView>(R.id.song_artist).text = "Vista de prueba · controles"
        RadioControlIcons.renderPlayback(root.findViewById(R.id.play_native_radio), true)
        val bitmap = Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(instrumentation.targetContext.cacheDir,"radio-fullscreen-review").apply { mkdirs() }
        File(dir,"normal-icon-controls.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
    }
}
