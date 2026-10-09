package online.bus.bmarian.conexiuniclujmobile

import androidx.compose.ui.unit.Density
import online.bus.bmarian.conexiuniclujmobile.ui.SheetSnap
import online.bus.bmarian.conexiuniclujmobile.ui.SheetState
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetFlingTest {
    private val density = Density(1f)

    private fun sheet(at: SheetSnap, moveBy: Float = 0f) = SheetState(at).apply {
        updateAnchors(heightPx = 2000f, peekPx = 200f)
        if (moveBy != 0f) anchored.dispatchRawDelta(moveBy)
    }

    @Test
    fun hardThrowsMoveOneRest() {
        assertEquals(SheetSnap.Half, sheet(SheetSnap.Peek).targetFor(-30_000f, density))
        assertEquals(SheetSnap.Full, sheet(SheetSnap.Half).targetFor(-30_000f, density))
        assertEquals(SheetSnap.Half, sheet(SheetSnap.Full).targetFor(30_000f, density))
        assertEquals(SheetSnap.Peek, sheet(SheetSnap.Half).targetFor(30_000f, density))
    }

    @Test
    fun throwsPastTheEndsStayThere() {
        assertEquals(SheetSnap.Full, sheet(SheetSnap.Full).targetFor(-5_000f, density))
        assertEquals(SheetSnap.Peek, sheet(SheetSnap.Peek).targetFor(5_000f, density))
    }

    @Test
    fun slowReleaseGoesToTheNearerRest() {
        assertEquals(SheetSnap.Half, sheet(SheetSnap.Half, moveBy = -300f).targetFor(-200f, density))
        assertEquals(SheetSnap.Full, sheet(SheetSnap.Half, moveBy = -500f).targetFor(0f, density))
    }

    @Test
    fun aThrowCarriesOnFromTheFingerOncePastARest() {
        assertEquals(SheetSnap.Full, sheet(SheetSnap.Peek, moveBy = -1_100f).targetFor(-3_000f, density))
    }

    @Test
    fun aThrowBackReturnsToTheRest() {
        assertEquals(SheetSnap.Half, sheet(SheetSnap.Half, moveBy = 300f).targetFor(-3_000f, density))
        assertEquals(SheetSnap.Peek, sheet(SheetSnap.Half, moveBy = -10f).targetFor(3_000f, density))
    }
}
