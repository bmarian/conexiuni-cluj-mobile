package online.bus.bmarian.conexiuniclujmobile

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import online.bus.bmarian.conexiuniclujmobile.ui.Pop
import org.junit.Assert.assertEquals
import org.junit.Test

class PopTest {
    private object Clock : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            nanos += 16_000_000
            return onFrame(nanos)
        }
    }

    @Test
    fun aCancelledSquashStillLands() = runBlocking {
        val pop = Pop()
        withContext(Clock) {
            val squash = launch { pop.squash() }
            delay(50)
            squash.cancelAndJoin()
        }
        assertEquals(1f, pop.x.value, 0.001f)
        assertEquals(1f, pop.y.value, 0.001f)
    }
}
