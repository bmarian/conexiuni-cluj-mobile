package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.Hint
import online.bus.bmarian.conexiuniclujmobile.logic.HintContext
import online.bus.bmarian.conexiuniclujmobile.logic.HintLine
import online.bus.bmarian.conexiuniclujmobile.logic.HintState
import online.bus.bmarian.conexiuniclujmobile.logic.Hints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as utils/__tests__/hints.spec.ts. */
class HintsTest {
    private fun ctx(day: String = "2026-11-13", saved: Boolean = false, grouped: Boolean = false, canRemind: Boolean = false) =
        HintContext("cluj:1", day, 7, saved, { _, _ -> grouped }, canRemind)

    private val days = (11..13).map { "2026-11-$it" }
    private fun visits(n: Int, hour: Int = 7) = days.take(n).fold(HintState()) { s, day -> Hints.visitStop(s, "cluj:1", day, hour) }
    private fun line(routeId: Int) = HintLine(routeId, "0", 1)

    @Test
    fun suggestsSavingAStopOpenedOnThreeDifferentDays() {
        assertNull(Hints.pick(visits(2), ctx()))
        assertNull(Hints.pick(Hints.visitStop(visits(2), "cluj:1", "2026-11-12", 7), ctx()))
        assertEquals(Hint.Save, Hints.pick(visits(3), ctx()))
        assertNull(Hints.pick(visits(3), ctx(saved = true)))
    }

    @Test
    fun suggestsAGroupForTwoLinesEachOpenedFromTheStopOnTwoDays() {
        var s = HintState()
        for (day in days.take(2)) s = Hints.openLineFromStop(Hints.openLineFromStop(s, "cluj:1", line(24), day), "cluj:1", line(35), day)
        s = Hints.openLineFromStop(s, "cluj:2", line(9), "2026-11-11")
        assertEquals(Hint.Group(line(24), line(35)), Hints.pick(s, ctx()))
        assertNull(Hints.pick(s, ctx(grouped = true)))
        assertNull(Hints.pick(Hints.openLineFromStop(HintState(), "cluj:1", line(24), "2026-11-11"), ctx()))
    }

    @Test
    fun suggestsAReminderForTheSameHourOnThreeDays() {
        assertEquals(Hint.Remind, Hints.pick(visits(3), ctx(saved = true, canRemind = true)))
        assertNull(Hints.pick(visits(3, hour = 8), ctx(saved = true, canRemind = true)))
    }

    @Test
    fun showsEachTipOnceAndAtMostOneADay() {
        val shown = Hints.markShown(visits(3), Hint.SAVE, "2026-11-13")
        assertNull(Hints.pick(shown, ctx()))
        assertNull(Hints.pick(shown, ctx(day = "2026-11-14")))
        assertEquals(Hint.Remind, Hints.pick(shown, ctx(day = "2026-11-14", saved = true, canRemind = true)))
    }

    @Test
    fun readsBackWhatItSavesAndDropsAnythingElse() {
        val s = Hints.markShown(visits(3), Hint.SAVE, "2026-11-13")
        assertEquals(s, Hints.read(Hints.write(s)))
        assertEquals(listOf(Hint.GROUP), Hints.read("""{"shown":["nope","group"],"days":{}}""").shown)
        assertEquals(HintState(), Hints.read("not json"))
    }
}
