package org.domnikl.gaia

class TriggeringQueue(
    private val size: Int,
    private val trigger: Trigger
) {
    private var elements = mutableListOf<Pair<Float, Boolean>>()

    suspend fun add(element: Float) {
        elements.add(element to trigger.condition(element))

        triggerIfThresholdWasReached()

        elements = elements.takeLast(size).toMutableList()
    }

    private suspend fun triggerIfThresholdWasReached() {
        val head = elements.first().second
        val tail = elements.takeLast(elements.size - 1)

        if (!head && tail.all { it.second } && elements.size > size) {
            trigger.fn(elements.map { it.first})
        }
    }

    data class Trigger(val condition: (Float) -> Boolean, val fn: suspend (List<Float>) -> Unit)
}
