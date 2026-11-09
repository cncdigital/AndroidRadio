package mx.sntss1puebla.credenciales

import kotlin.math.abs

/** Stable IDs keep the saved choice valid when more backgrounds are added later. */
enum class RadioVisualStyle(val id: String, val titleRes: Int) {
    COVER("cover", R.string.radio_visual_cover),
    BARS("bars", R.string.radio_visual_bars),
    WAVES("waves", R.string.radio_visual_waves),
    TUNNEL("tunnel", R.string.radio_visual_tunnel),
    AURORA("aurora", R.string.radio_visual_aurora),
    STARS("stars", R.string.radio_visual_stars);

    fun shifted(direction: Int): RadioVisualStyle =
        entries[Math.floorMod(ordinal + direction.coerceIn(-1, 1), entries.size)]

    companion object {
        fun fromId(id: String?): RadioVisualStyle = entries.firstOrNull { it.id == id } ?: COVER
    }
}

/** Horizontal swipes change only the background. Scrolling, taps and diagonals do not. */
internal object RadioBackgroundGesture {
    fun direction(dx: Float, dy: Float, threshold: Float): Int {
        if (!dx.isFinite() || !dy.isFinite() || threshold <= 0 || !threshold.isFinite()) return 0
        if (abs(dx) < threshold || abs(dx) <= abs(dy) * 1.5f) return 0
        return if (dx < 0) 1 else -1
    }
}
