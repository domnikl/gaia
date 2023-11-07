package org.domnikl.gaia

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

@Component
class FritzboxActor(
    private val properties: FritzBoxProperties,
    private val registry: MeterRegistry,
    private val fritzBox: FritzBox,
    private val eventTrigger: EventTrigger,
) {
    private val logger = LoggerFactory.getLogger(FritzboxActor::class.java)
    private val power = mutableMapOf<Appliance, Double>()
    private val energy = mutableMapOf<Appliance, Double>()
    private val temperature = mutableMapOf<Appliance, Double>()

    // TODO: store the state of each device so that we can build a HTML page from it

    private val appliances by lazy {
        val knownAppliances = properties.appliances.map { it.value.ain to it.value }.toMap()

        fritzBox.list().map { ain ->
            val name = fritzBox.name(ain)
            val appliance = knownAppliances.getOrDefault(
                ain,
                Appliance(ain, name)
            )

            appliance.name = name

            Gauge
                .builder("gaia_appliance_power", power) { p -> p[appliance] ?: 0.0 }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain)))
                .strongReference(true)
                .register(registry)

            Gauge
                .builder("gaia_appliance_energy", energy) { e -> e[appliance] ?: 0.0 }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain)))
                .strongReference(true)
                .register(registry)

            Gauge
                .builder("gaia_appliance_temperature", temperature) { t -> t[appliance] ?: 0.0 }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain)))
                .strongReference(true)
                .register(registry)

            appliance
        }
    }

    @Scheduled(fixedRate = 1000)
    fun refreshMetrics() {
        appliances.forEach {
            power[it] = fritzBox.power(it.ain)
            energy[it] = fritzBox.energy(it.ain)
            temperature[it] = fritzBox.temperature(it.ain)

            logger.info("read from ${it.ain} (${it.name}): power = ${power[it]}, energy = ${energy[it]}, temperature = ${temperature[it]}")

            power[it]?.let { p -> eventTrigger.add(it, p) }
        }
    }
}
