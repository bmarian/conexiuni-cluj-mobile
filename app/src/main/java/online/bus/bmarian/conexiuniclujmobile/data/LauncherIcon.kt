package online.bus.bmarian.conexiuniclujmobile.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import online.bus.bmarian.conexiuniclujmobile.MainActivity

object LauncherIcon {
    // Same names as the activity-alias entries in AndroidManifest.xml.
    private const val STANDARD = ".StandardLauncher"
    private const val DOODLE = ".DoodleLauncher"

    fun follow(context: Context, look: Look) {
        val doodle = look == Look.Doodle
        set(context, DOODLE, doodle)
        set(context, STANDARD, !doodle)
    }

    private fun set(context: Context, alias: String, on: Boolean) {
        val component = ComponentName(context, "${MainActivity::class.java.`package`!!.name}$alias")
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
