import kotlinx.coroutines.runBlocking
import org.domnikl.gaia.TriggeringQueue
import org.junit.Assert.assertEquals
import org.junit.Test

class TriggeringQueueTest {
    private var l = emptyList<Float>()
    private val q = TriggeringQueue(3, TriggeringQueue.Trigger({ f -> f < 10F }) {
        l = it
    })

    @Test
    fun canTriggerWhenTheThresholdWasReached() {
        runBlocking {
            q.add(10F)
            q.add(9F)
            q.add(9F)
            q.add(9F) // now it will trigger
        }

        assertEquals(listOf(10F, 9F, 9F, 9F), l)
    }

    @Test
    fun willNotTriggerIfTheThresholdWasReachedButCrossedAgain() {
        runBlocking {
            q.add(10F)
            q.add(9F)
            q.add(9F)
            q.add(10F)
        }

        assertEquals(emptyList<Float>(), l)
    }

    @Test
    fun willNotTriggerIfValueWasAlwaysBelowThreshold() {
        runBlocking {
            q.add(9F)
            q.add(9F)
            q.add(9F)
            q.add(9F)
        }

        assertEquals(emptyList<Float>(), l)
    }
}
