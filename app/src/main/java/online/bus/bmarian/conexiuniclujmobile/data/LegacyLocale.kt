package online.bus.bmarian.conexiuniclujmobile.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper
import online.bus.bmarian.conexiuniclujmobile.Services
import java.util.Locale

/** The in-app language before Android 13, which has no per-app locales. */
object LegacyLocale {
    /** The shared resources services, receivers and widgets use. The system resets them on every configuration change. */
    fun apply(context: Context, tag: String) {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val resources = context.applicationContext.resources
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION") resources.updateConfiguration(config, resources.displayMetrics)
    }

    /** From attachBaseContext. */
    fun override(activity: ContextThemeWrapper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val locale = Locale.forLanguageTag(Services.settings.locale.value)
        activity.applyOverrideConfiguration(Configuration().apply { setLocale(locale) })
    }
}
