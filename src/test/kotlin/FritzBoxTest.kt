import org.domnikl.gaia.FritzboxActor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FritzBoxTest {
    @Test
    fun `can solve a challenge`() {
        val challenge = "2$10000$5A1711$2000$5A1722"
        val password = "1example!"

        val response = FritzboxActor.FritzBox.solveChallenge(challenge, password)

        assertEquals("5A1722\$1798a1672bca7c6463d6b245f82b53703b0f50813401b03e4045a5861e689adb", response)
    }
}