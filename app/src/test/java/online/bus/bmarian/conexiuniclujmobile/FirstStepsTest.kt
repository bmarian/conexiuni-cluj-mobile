package online.bus.bmarian.conexiuniclujmobile

import online.bus.bmarian.conexiuniclujmobile.logic.FirstSteps
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as composables/__tests__/useFirstSteps.spec.ts. */
class FirstStepsTest {
    @Test
    fun showsEveryStepToSomeoneStartingWithNothingSaved() {
        assertEquals(FirstSteps.ALL, FirstSteps(on = true).left)
    }

    @Test
    fun neverShowsTheCardToSomeoneWhoAlreadySavedSomething() {
        assertEquals(emptyList<String>(), FirstSteps(on = false).mark(FirstSteps.STOP).left)
    }

    @Test
    fun dropsDoneSteps() {
        assertEquals(listOf(FirstSteps.PLAN, FirstSteps.LINE), FirstSteps(on = true).mark(FirstSteps.STOP).mark(FirstSteps.STOP).left)
    }

    @Test
    fun staysHiddenOnceClosed() {
        assertEquals(emptyList<String>(), FirstSteps(on = true).hidden().left)
    }

    @Test
    fun ignoresUnknownSteps() {
        assertEquals(FirstSteps.ALL, FirstSteps(on = true).mark("nope").left)
    }
}
