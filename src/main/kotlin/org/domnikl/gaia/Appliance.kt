package org.domnikl.gaia

data class Appliance(
    val ain: String,
    var name: String? = null,
    val queueSize: Int? = null,
    val thresholdStart: Double? = null,
    val thresholdEnd: Double? = null,
    val notificationStarted: String? = null,
    val notificationEnded: String? = null
)
