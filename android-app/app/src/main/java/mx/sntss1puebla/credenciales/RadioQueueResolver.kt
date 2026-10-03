package mx.sntss1puebla.credenciales

import androidx.media3.common.MediaItem

internal data class RadioQueueResolution(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
)

/** Resolves car selections only against the current public catalog and restores the full queue. */
internal object RadioQueueResolver {
    fun resolve(
        requestedItems: List<MediaItem>,
        requestedStartIndex: Int,
        catalog: List<MediaItem>,
    ): RadioQueueResolution {
        val selectedId = requestedItems.getOrNull(requestedStartIndex)?.mediaId
        val catalogById = catalog.associateBy { it.mediaId }
        val selectedInCatalog = selectedId?.let { id -> catalog.indexOfFirst { it.mediaId == id } } ?: -1
        val queue = if (requestedItems.size == 1 && selectedInCatalog >= 0) {
            catalog
        } else {
            requestedItems.mapNotNull { candidate -> catalogById[candidate.mediaId] }
        }
        val selectedInQueue = queue.indexOfFirst { it.mediaId == selectedId }
        val safeStartIndex = when {
            queue.isEmpty() -> 0
            selectedInQueue >= 0 -> selectedInQueue
            else -> requestedStartIndex.coerceIn(0, queue.lastIndex)
        }
        return RadioQueueResolution(queue, safeStartIndex)
    }
}
