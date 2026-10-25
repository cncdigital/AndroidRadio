package mx.sntss1puebla.credenciales

import kotlin.math.ln
import kotlin.random.Random

/** Weighted random permutation: every song occurs once; new uploads get a boost. */
internal object RadioPriority {
    fun <T> order(items: List<T>, previous: String?, id: (T) -> String,
        uploaded: (T) -> Long, preference: (T) -> Int, now: Long = System.currentTimeMillis(),
        random: Random = Random.Default): List<T> {
        val result = items.distinctBy(id).map { item ->
            val age = now - uploaded(item)
            val recent = if (uploaded(item) > 0 && age in 0..14L * 86_400_000L) 6 else 0
            val weight = 1 + recent + preference(item).coerceIn(0, 20)
            item to -ln(random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / weight
        }.sortedBy { it.second }.map { it.first }.toMutableList()
        if (result.size > 1 && id(result.first()) == previous) java.util.Collections.swap(result, 0, 1)
        return result
    }
}
