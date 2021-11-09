package org.domnikl.gaia

data class Actor(
    val ain: String,
    val name: String,
    val triggeringQueues: List<TriggeringQueue>
) {
    suspend fun add(measurement: Float) {
        triggeringQueues.forEach { it.add(measurement) }
    }
}