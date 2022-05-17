package org.domnikl.gaia

class Observer {
    private val subscriptions = mutableListOf<(Event) -> Unit>()

    fun notify(event: Event) {
        subscriptions.forEach { it(event) }
    }

    fun subscribe(action: (type: Event) -> Unit) {
        subscriptions.add(action)
    }
}
