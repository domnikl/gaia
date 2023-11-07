package org.domnikl.gaia

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

data class ApplianceStartedEvent(val source: String, val appliance: Appliance) : ApplicationEvent(source)
data class ApplianceStoppedEvent(val source: String, val appliance: Appliance) : ApplicationEvent(source)

@Component
class EventTrigger(private val eventPublisher: ApplicationEventPublisher) {
    private val logger = LoggerFactory.getLogger(EventTrigger::class.java)
    private val startQueues = mutableMapOf<Appliance, TriggeringQueue>()
    private val endQueues = mutableMapOf<Appliance, TriggeringQueue>()

    fun add(appliance: Appliance, element: Double) {
        if (appliance.queueSize != null && appliance.thresholdStart != null) {
            startQueues.getOrPut(appliance) {
                TriggeringQueue(appliance.queueSize, Trigger({ e -> e > appliance.thresholdStart }) {
                    logger.info("Triggering ApplianceStartedEvent for ${appliance.ain} (${appliance.name})")
                    eventPublisher.publishEvent(ApplianceStartedEvent(this::class.java.toString(), appliance))
                })
            }.add(element)
        }

        if (appliance.queueSize != null && appliance.thresholdEnd != null) {
            endQueues.getOrPut(appliance) {
                TriggeringQueue(appliance.queueSize, Trigger({ e -> e < appliance.thresholdEnd }) {
                    logger.info("Triggering ApplianceStoppedEvent for ${appliance.ain} (${appliance.name})")
                    eventPublisher.publishEvent(ApplianceStoppedEvent(this::class.java.toString(), appliance))
                })
            }.add(element)
        }
    }

    private inner class TriggeringQueue(private val size: Int, private val trigger: Trigger) {
        private var elements = mutableListOf<Pair<Double, Boolean>>()

        fun add(element: Double) {
            elements.add(element to trigger.condition(element))

            triggerIfThresholdWasReached()

            elements = elements.takeLast(size).toMutableList()
        }

        private fun triggerIfThresholdWasReached() {
            val head = elements.first().second
            val tail = elements.takeLast(elements.size - 1)

            if (!head && tail.all { it.second } && elements.size > size) {
                trigger.fn(elements.map { it.first})
            }
        }
    }

    private data class Trigger(val condition: (Double) -> Boolean, val fn: (List<Double>) -> Unit)
}
