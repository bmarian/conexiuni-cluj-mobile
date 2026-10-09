package online.bus.bmarian.conexiuniclujmobile.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

const val PACKAGE = "online.bmarian.bus"
private const val WAIT_MS = 15_000L

/**
 * The paths people take every day: the map and Home's nearby buses, the sheet up and down,
 * search, a line, a stop and the planner. Needs an English emulator with a location set near
 * Cluj stops, and the app past its first-run screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        for (permission in listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS")) {
            device.executeShellCommand("pm grant $packageName android.permission.$permission")
        }
        pressHome()
        startActivityAndWait()
        // A fresh install starts with the first-run screens: through them, nothing allowed.
        for (step in 1..6) {
            val done = device.wait(Until.findObject(By.text("Done")), 1_500)
            if (done != null) {
                done.click()
                break
            }
            (device.findObject(By.text("Next")) ?: device.findObject(By.text("Not now")))?.click()
        }
        Thread.sleep(4_000)

        // The sheet up to full and back to half, then the map panned.
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 30)
        Thread.sleep(1_000)
        device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 30)
        Thread.sleep(1_000)
        device.swipe(w / 3, h / 4, w * 2 / 3, h / 5, 30)
        Thread.sleep(1_500)

        // Search from the bar on the map, and a line from the results.
        await(By.text("Search routes, stops, places...")).click()
        device.waitForIdle()
        device.executeShellCommand("input text 25")
        await(By.text(Pattern.compile("Str\\..*"))).click()
        Thread.sleep(3_000)
        device.pressBack()
        Thread.sleep(1_000)

        // A stop from Home's nearby buses.
        device.wait(Until.findObject(By.text(Pattern.compile("\\d+ m"))), WAIT_MS)?.let {
            it.click()
            Thread.sleep(3_000)
            device.pressBack()
            Thread.sleep(1_000)
        }

        // The planner, from a link as a shared place opens it.
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://bus.bmarian.online/plan?lat=46.7694&lon=23.5899&name=Unirii"))
                .setPackage(PACKAGE),
        )
        Thread.sleep(6_000)
        device.pressBack()
    }

    private fun MacrobenchmarkScope.await(selector: BySelector): UiObject2 =
        device.wait(Until.findObject(selector), WAIT_MS) ?: error("not found: $selector")
}
