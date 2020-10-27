package org.domnikl.gaia

class Queue(
    private val size: Int,
    private val threshold: Float,
    private val trigger: suspend (List<Float>) -> Unit
) {
    private var elements = mutableListOf<Pair<Float, Boolean>>()

    suspend fun add(element: Float) {
        elements.add(element to (element < threshold))

        triggerIfThresholdWasReached()

        elements = elements.takeLast(size).toMutableList()
    }

    private suspend fun triggerIfThresholdWasReached() {
        val head = elements.first().second
        val tail = elements.takeLast(elements.size - 1)

        if (!head && tail.all { it.second } && elements.size > size) {
            trigger(elements.map { it.first })
        }
    }
}
