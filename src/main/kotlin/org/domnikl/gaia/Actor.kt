package org.domnikl.gaia

interface Actor {
    val id: String

    suspend fun run(): Double
}
