package mx.sntss1puebla.credenciales

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeFalse
import org.junit.runner.RunWith
import java.io.File

/** No media, network, services or real user data: exercises the production layouts and controller. */
@RunWith(AndroidJUnit4::class)
class RadioFullscreenControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixtures = mutableListOf<Fixture>()
    private data class Fixture(
        val root: RadioFullscreenFrame,
        val controls: RadioFullscreenControls,
        val chrome: List<View>,
        val lyric: TextView,
        val artwork: RadioArtworkFrame
    )

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun fixture(
        delay: Long = 80L,
        fade: Long = 60L,
        exploration: () -> Boolean = { false },
        keyboard: () -> Boolean = { false }
    ): Fixture {
        lateinit var result: Fixture
        onMain {
            val context = ContextThemeWrapper(instrumentation.targetContext, android.R.style.Theme_Material_NoActionBar)
            val root = LayoutInflater.from(context).inflate(R.layout.dialog_radio_fullscreen, null) as RadioFullscreenFrame
            val chrome = listOf<View>(root.findViewById(R.id.full_controls),
                root.findViewById(R.id.full_close), root.findViewById(R.id.full_lyric_move))
            val controller = RadioFullscreenControls(root, chrome, delay, fade, exploration, keyboard)
            val lyric = root.findViewById<TextView>(R.id.full_lyric).apply {
                text = "La letra permanece visible"
                translationX = 7f
                translationY = -13f
            }
            val artwork = root.findViewById<RadioArtworkFrame>(R.id.full_artwork)
            artwork.setOnClickListener { controller.show() }
            root.findViewById<TextView>(R.id.full_title).text = "Radio Sindical · demostración"
            root.findViewById<TextView>(R.id.full_artist).text = "Vista de prueba local"
            root.findViewById<TextView>(R.id.full_time).text = "1:20 / 3:45"
            root.findViewById<ImageView>(R.id.full_cover).visibility = View.INVISIBLE
            root.findViewById<RadioVisualizerView>(R.id.full_visualizer).apply {
                style = RadioVisualStyle.STARS
                visibility = View.VISIBLE
                motionActive = false
            }
            val density = context.resources.displayMetrics.density
            val width = (420 * density).toInt()
            val height = (900 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            result = Fixture(root, controller, chrome, lyric, artwork)
            fixtures.add(result)
            controller.start()
        }
        return result
    }

    @After fun cleanup() = onMain {
        fixtures.forEach { it.controls.dispose() }
        fixtures.clear()
    }

    private fun awaitOnMain(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        }
        onMain { assertTrue(message, condition()) }
    }

    private fun awaitHidden(f: Fixture) = awaitOnMain("Fullscreen controls should hide after inactivity") {
        f.chrome.all { it.visibility == View.INVISIBLE && it.alpha == 0f }
    }

    private fun assertShown(f: Fixture) {
        f.chrome.forEach {
            assertEquals(View.VISIBLE, it.visibility)
            assertEquals(1f, it.alpha, 0f)
        }
    }

    private fun assertLyricUntouched(f: Fixture) {
        assertEquals("La letra permanece visible", f.lyric.text.toString())
        assertEquals(View.VISIBLE, f.lyric.visibility)
        assertEquals(1f, f.lyric.alpha, 0f)
        assertEquals(7f, f.lyric.translationX, 0f)
        assertEquals(-13f, f.lyric.translationY, 0f)
        assertEquals(1f, f.root.alpha, 0f)
        assertEquals(1f, f.artwork.alpha, 0f)
        assertSame(f.root, f.lyric.parent)
    }

    private fun touch(f: Fixture, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        f.root.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun center(f: Fixture, view: View): Pair<Float, Float> {
        val bounds = Rect(0, 0, view.width, view.height)
        f.root.offsetDescendantRectToMyCoords(view, bounds)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun startFade(f: Fixture) {
        // Trigger the real timeout callback deterministically; production still uses its Handler.
        val hide = RadioFullscreenControls::class.java.getDeclaredField("hide").apply { isAccessible = true }
        (hide.get(f.controls) as Runnable).run()
    }

    private fun explorationChanged(f: Fixture, enabled: Boolean) {
        val listener = RadioFullscreenControls::class.java.getDeclaredField("explorationListener")
            .apply { isAccessible = true }.get(f.controls) as AccessibilityManager.TouchExplorationStateChangeListener
        listener.onTouchExplorationStateChanged(enabled)
    }

    private fun snapshot(f: Fixture, name: String) {
        val directory = File(instrumentation.targetContext.cacheDir, "radio-fullscreen-review").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(f.root.width, f.root.height, Bitmap.Config.ARGB_8888)
        f.root.draw(Canvas(bitmap))
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun onlyFullscreenChromeFadesAndLyricRemainsVisible() {
        val f = fixture(delay = 10_000L)
        onMain {
            assertShown(f)
            snapshot(f, "fullscreen-controls-visible.png")
            startFade(f)
        }
        awaitHidden(f)
        onMain {
            assertLyricUntouched(f)
            snapshot(f, "fullscreen-controls-hidden.png")
            val normal = LayoutInflater.from(f.root.context).inflate(R.layout.activity_main, null)
            for (id in listOf(R.id.previous_song, R.id.play_native_radio, R.id.next_song)) {
                assertEquals(View.VISIBLE, normal.findViewById<View>(id).visibility)
                assertEquals(1f, normal.findViewById<View>(id).alpha, 0f)
            }
            f.artwork.performClick()
            assertShown(f)
            assertLyricUntouched(f)
        }
    }

    @Test fun firstRevealTapDoesNotReachHiddenButtonsAndSwipesAreNotClicks() {
        val f = fixture()
        var buttonTouches = 0
        var taps = 0
        var style = RadioVisualStyle.COVER
        var swipes = 0
        onMain {
            f.root.findViewById<Button>(R.id.full_play).setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) buttonTouches++
                false
            }
            f.artwork.setOnClickListener { taps++; f.controls.show() }
            f.artwork.onBackgroundSwipe = { style = style.shifted(it); swipes++; f.controls.show() }
        }
        repeat(3) {
            awaitHidden(f)
            onMain {
                val (x, y) = center(f, f.root.findViewById(R.id.full_play))
                touch(f, MotionEvent.ACTION_DOWN, x, y)
                assertShown(f)
                touch(f, MotionEvent.ACTION_UP, x, y)
                assertEquals(0, buttonTouches)
                assertEquals(it + 1, taps)
            }
            awaitHidden(f)
            onMain {
                val y = f.root.height * 0.2f // Free artwork, clear of lyric and top buttons.
                touch(f, MotionEvent.ACTION_DOWN, f.root.width * 0.8f, y)
                touch(f, MotionEvent.ACTION_MOVE, f.root.width * 0.2f, y)
                touch(f, MotionEvent.ACTION_UP, f.root.width * 0.2f, y)
                assertEquals(it + 1, swipes)
                assertEquals(it + 1, taps)
                assertShown(f)
                assertLyricUntouched(f)
            }
        }
        assertEquals(RadioVisualStyle.TUNNEL, style)
        onMain {
            val (x, y) = center(f, f.root.findViewById(R.id.full_play))
            touch(f, MotionEvent.ACTION_DOWN, x, y)
            touch(f, MotionEvent.ACTION_CANCEL, x, y)
            assertEquals("A second, visible touch must still reach the transport", 1, buttonTouches)
        }
        awaitHidden(f)
    }

    @Test fun lyricInteractionSuspendsTimeoutUntilUpOrCancel() {
        val f = fixture()
        onMain {
            f.lyric.setOnTouchListener { _, _ -> true }
            val (x, y) = center(f, f.lyric)
            touch(f, MotionEvent.ACTION_DOWN, x, y)
            touch(f, MotionEvent.ACTION_MOVE, x + 12f, y + 20f)
        }
        SystemClock.sleep(350)
        onMain {
            assertShown(f)
            assertLyricUntouched(f)
            touch(f, MotionEvent.ACTION_CANCEL, 0f, 0f)
        }
        awaitHidden(f)
        onMain {
            assertLyricUntouched(f)
            val (x, y) = center(f, f.lyric)
            touch(f, MotionEvent.ACTION_DOWN, x, y)
        }
        SystemClock.sleep(350)
        onMain {
            assertShown(f)
            val (x, y) = center(f, f.lyric)
            touch(f, MotionEvent.ACTION_UP, x, y)
        }
        awaitHidden(f)
        onMain { assertLyricUntouched(f) }
    }

    @Test fun revealingDuringFadeCancelsOldAnimationAndStopCancelsNewWork() {
        val f = fixture(delay = 10_000L, fade = 240L)
        onMain { startFade(f) }
        awaitOnMain("The real fade must advance") { f.chrome.first().alpha < 1f }
        onMain {
            f.controls.show()
            assertShown(f)
        }
        SystemClock.sleep(400)
        onMain {
            assertShown(f) // The old animator's onAnimationEnd must not hide these views.
            assertLyricUntouched(f)
            startFade(f)
            f.controls.stop()
        }
        SystemClock.sleep(400)
        onMain {
            assertShown(f)
            startFade(f) // A stale timeout is harmless after onStop.
            assertShown(f)
            f.controls.start()
            startFade(f)
        }
        awaitHidden(f)
        onMain {
            f.root.onWindowDetached?.invoke()
            assertShown(f)
            assertLyricUntouched(f)
        }
    }

    @Test fun touchExplorationAndKeyboardKeepControlsVisibleAndAccessible() {
        var exploring = true
        var keyboard = false
        val f = fixture(exploration = { exploring }, keyboard = { keyboard })
        SystemClock.sleep(350)
        onMain {
            assertShown(f)
            exploring = false
            explorationChanged(f, false)
        }
        awaitHidden(f)
        onMain {
            exploring = true
            explorationChanged(f, true)
            assertShown(f)
            exploring = false
            keyboard = true
            explorationChanged(f, false)
            f.root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
        }
        SystemClock.sleep(350)
        onMain {
            assertShown(f)
            assertLyricUntouched(f)
            keyboard = false
            f.controls.show()
        }
        awaitHidden(f)
    }

    @Test fun dismissAndReopenDoNotLeaveCallbacksOrAnimations() {
        val f = fixture()
        onMain {
            f.controls.dispose()
            assertNull(f.root.onTouchInteraction)
            assertNull(f.root.onKeyInteraction)
            assertNull(f.root.onWindowDetached)
        }
        SystemClock.sleep(350)
        onMain {
            f.controls.start() // A disposed controller cannot restart.
            startFade(f)
            assertShown(f)
            assertLyricUntouched(f)
        }
        val reopened = fixture()
        awaitHidden(reopened)
        onMain {
            f.controls.dispose() // Old cleanup cannot affect the newly opened fullscreen view.
            reopened.artwork.performClick()
            assertShown(reopened)
            assertLyricUntouched(reopened)
        }
        awaitHidden(reopened)
    }

    @Test fun artworkAccessibilityActionsReplacePickerWithoutDuplicates() = onMain {
        val frame = RadioArtworkFrame(instrumentation.targetContext)
        var style = RadioVisualStyle.COVER
        var taps = 0
        frame.setOnClickListener { taps++ }
        frame.onBackgroundSwipe = {
            style = style.shifted(it)
            frame.contentDescription = frame.context.getString(R.string.radio_visual_label,
                frame.context.getString(style.titleRes), style.ordinal + 1, RadioVisualStyle.entries.size)
        }
        repeat(3) {
            val info = frame.createAccessibilityNodeInfo()
            val backgroundActions = info.actionList.filter { action ->
                action.id == R.id.radio_background_previous || action.id == R.id.radio_background_next
            }
            assertEquals(2, backgroundActions.size)
            assertEquals(2, backgroundActions.map { action -> action.id }.toSet().size)
            assertTrue(backgroundActions.all { action -> !action.label.isNullOrBlank() })
            assertTrue(frame.performAccessibilityAction(R.id.radio_background_next, null))
            assertEquals(RadioVisualStyle.BARS, style)
            assertTrue(frame.contentDescription.contains(frame.context.getString(R.string.radio_visual_bars)))
            assertTrue(frame.performAccessibilityAction(R.id.radio_background_previous, null))
            assertEquals(RadioVisualStyle.COVER, style)
            info.recycle()
        }
        assertEquals(0, taps)
        assertTrue(frame.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(1, taps)
    }

    @Test fun realDialogHandlesTouchFocusDismissReopenAndActivityStop() {
        val context = instrumentation.targetContext
        val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        assumeFalse("Auto-hide is intentionally disabled with touch exploration", accessibility.isTouchExplorationEnabled)
        val host = instrumentation.startActivitySync(Intent(context, RadioFullscreenTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RadioFullscreenTestActivity
        var cover: RadioFullscreenCoverActivity? = null
        val settledDelay = RadioFullscreenTestActivity.HIDE_DELAY_MS +
            RadioFullscreenTestActivity.FADE_DURATION_MS + 250L

        fun open(): Fixture {
            lateinit var f: Fixture
            onMain {
                val dialog = host.openFullscreen()
                val root = dialog.findViewById<RadioFullscreenFrame>(R.id.full_root)
                f = Fixture(root, requireNotNull(host.controller), listOf<View>(
                    root.findViewById(R.id.full_controls), root.findViewById(R.id.full_close),
                    root.findViewById(R.id.full_lyric_move)
                ), root.findViewById(R.id.full_lyric), root.findViewById(R.id.full_artwork))
            }
            awaitOnMain("The real dialog must be attached and focused") {
                f.root.isAttachedToWindow && f.root.hasWindowFocus() && f.root.width > 0
            }
            return f
        }

        fun tap(view: View, xFraction: Float = 0.5f, yFraction: Float = 0.5f) {
            var x = 0f
            var y = 0f
            onMain {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                x = location[0] + view.width * xFraction
                y = location[1] + view.height * yFraction
            }
            val downTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            instrumentation.waitForIdleSync()
        }

        try {
            val first = open()
            tap(first.artwork, 0.2f, 0.2f)
            onMain { assertTrue("A real touchscreen event must enter touch mode", first.root.isInTouchMode) }
            awaitHidden(first)

            // This is real ViewRoot keyboard focus, not the injected predicate used by unit fixtures.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
            instrumentation.waitForIdleSync()
            awaitOnMain("Keyboard navigation must leave touch mode and focus the dialog") {
                !first.root.isInTouchMode && first.root.hasFocus()
            }
            SystemClock.sleep(settledDelay)
            onMain { assertShown(first); assertLyricUntouched(first) }
            tap(first.artwork, 0.2f, 0.2f)
            awaitHidden(first)

            // Tapping the screen location of a hidden button must reveal, not activate, it.
            lateinit var play: Button
            onMain { play = first.root.findViewById(R.id.full_play) }
            tap(play)
            onMain {
                assertShown(first)
                assertEquals(0, host.playClicks)
                assertLyricUntouched(first)
                host.fullscreen?.dismiss()
            }
            awaitOnMain("Dismiss must detach the old window and dispose callbacks") {
                !first.root.isAttachedToWindow && first.root.onTouchInteraction == null && host.fullscreen == null
            }
            SystemClock.sleep(settledDelay)
            onMain { assertShown(first); assertLyricUntouched(first) }

            val reopened = open()
            tap(reopened.artwork, 0.2f, 0.2f)
            awaitHidden(reopened)
            tap(reopened.artwork, 0.2f, 0.2f)
            var startsBeforeCover = 0
            var stopsBeforeCover = 0
            onMain {
                startsBeforeCover = host.starts
                stopsBeforeCover = host.stops
                assertShown(reopened)
            }
            cover = instrumentation.startActivitySync(Intent(context, RadioFullscreenCoverActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RadioFullscreenCoverActivity
            awaitOnMain("Covering the activity must run its actual onStop without destroying it") {
                host.stops > stopsBeforeCover && !host.isDestroyed && !reopened.root.hasWindowFocus()
            }
            SystemClock.sleep(settledDelay)
            onMain {
                assertShown(reopened) // The stopped activity's pending timeout must stay cancelled.
                assertLyricUntouched(reopened)
                cover?.finish()
            }
            awaitOnMain("Uncovering must restart the host and refocus the existing dialog") {
                host.starts > startsBeforeCover && reopened.root.hasWindowFocus()
            }
            tap(reopened.artwork, 0.2f, 0.2f)
            awaitHidden(reopened)
            onMain { assertLyricUntouched(reopened) }
        } finally {
            onMain {
                cover?.takeUnless { it.isDestroyed }?.finish()
                host.finish()
            }
            awaitOnMain("The test host must finish and release its real dialog") {
                host.isDestroyed && host.fullscreen == null && host.controller == null
            }
        }
    }

}
