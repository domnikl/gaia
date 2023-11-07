package org.domnikl.gaia

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan("org.domnikl.gaia")
@EnableScheduling
class GaiaApplication

fun main(args: Array<String>) {
	runApplication<GaiaApplication>(*args)
}
