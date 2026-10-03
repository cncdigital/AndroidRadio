package mx.sntss1puebla.credenciales

/** Completed listens stay on this device; ties prefer recently uploaded songs. */
internal object RadioDiscovery {
    fun <T> order(songs: List<T>, previousId: String?, id: (T) -> String,
        count: (T) -> Int, uploaded: (T) -> Long): List<T> {
        val ordered = songs.shuffled().sortedWith(compareBy<T> { count(it).coerceAtLeast(0) }
            .thenByDescending { uploaded(it).coerceAtLeast(0L) }).toMutableList()
        if (ordered.size > 1 && id(ordered.first()) == previousId) java.util.Collections.swap(ordered, 0, 1)
        return ordered
    }
}
