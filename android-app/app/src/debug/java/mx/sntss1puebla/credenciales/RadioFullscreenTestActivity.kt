package mx.sntss1puebla.credenciales

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Debug-only host: actual windows/lifecycle without MainActivity's network or playback setup. */
class RadioFullscreenTestActivity : Activity() {
    companion object {
        const val HIDE_DELAY_MS = 1_000L
        const val FADE_DURATION_MS = 120L
    }

    internal var fullscreen: Dialog? = null
        private set
    internal var controller: RadioFullscreenControls? = null
        private set
    internal var starts = 0
        private set
    internal var stops = 0
        private set
    internal var playClicks = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Prueba local de pantalla completa" })
    }

    internal fun openFullscreen(): Dialog {
        check(fullscreen == null)
        val dialog = Dialog(this, android.R.style.Theme_Material_NoActionBar)
        dialog.setContentView(R.layout.dialog_radio_fullscreen)
        val root = dialog.findViewById<RadioFullscreenFrame>(R.id.full_root)
        dialog.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        val controls = RadioFullscreenControls(root, listOf<View>(
            root.findViewById(R.id.full_controls), root.findViewById(R.id.full_close),
            root.findViewById(R.id.full_lyric_move)
        ), HIDE_DELAY_MS, FADE_DURATION_MS) // Real accessibility and focus predicates.
        root.findViewById<TextView>(R.id.full_lyric).apply {
            text = "La letra permanece visible"
            translationX = 7f
            translationY = -13f
        }
        root.findViewById<RadioArtworkFrame>(R.id.full_artwork).setOnClickListener { controls.show() }
        root.findViewById<Button>(R.id.full_play).setOnClickListener { playClicks++ }
        root.findViewById<Button>(R.id.full_close).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            controls.dispose()
            if (fullscreen === dialog) {
                fullscreen = null
                controller = null
            }
        }
        fullscreen = dialog
        controller = controls
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        ViewCompat.requestApplyInsets(root)
        controls.start()
        return dialog
    }

    override fun onStart() {
        super.onStart()
        starts++
        controller?.start()
    }

    override fun onStop() {
        controller?.stop()
        stops++
        super.onStop()
    }

    override fun onDestroy() {
        controller?.dispose()
        fullscreen?.dismiss()
        super.onDestroy()
    }
}

/** Opaque second window drives the host's real onStop/onStart callbacks without ending its task. */
class RadioFullscreenCoverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Pantalla de prueba temporal" })
    }
}
