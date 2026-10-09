package online.bus.bmarian.conexiuniclujmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.data.Nominatim
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.ui.AppShell
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ConexiuniTheme

class MainActivity : ComponentActivity() {
    // Links the app was opened with: a shared stop or line, a transfer code, a notification.
    private val links = MutableStateFlow<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        setContent {
            val mode by Services.settings.theme.collectAsStateWithLifecycle()
            val dynamic by Services.settings.dynamicColor.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            ConexiuniTheme(dark, dynamic) {
                AppShell(dark = dark, links = links)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::planToShared)
            return
        }
        val data = intent?.data ?: intent?.getStringExtra(EXTRA_LINK)?.let(android.net.Uri::parse) ?: return
        links.value = data
    }

    /**
     * Shared from Maps or a chat: a place becomes a trip there. A Google Maps link resolves to its
     * pin, anything else is searched for; if nothing comes up, Home searches for it instead.
     */
    private fun planToShared(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value
        if (url != null && url.toUri().host == "bus.bmarian.online") {
            links.value = url.toUri()
            return
        }
        val query = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("http") } ?: text.trim()
        Services.settings.showToast(getString(R.string.share_finding, query.take(40)))
        lifecycleScope.launch {
            val place = if (url != null && Nominatim.isGoogleMapsUrl(url)) {
                Nominatim.resolveGoogleMapsLink(url)
            } else {
                Nominatim.search(query, Services.settings.locale.value, 1).firstOrNull()
            }
            links.value = if (place != null) {
                (BuildConfig.SITE_ORIGIN + "/plan?" + PlanQuery(lat = place.lat, lon = place.lon, name = place.label).encode()).toUri()
            } else {
                Services.settings.showToast(getString(R.string.share_not_found))
                "${WearLink.APP_SCHEME}://bus.bmarian.online/search?q=${Uri.encode(query)}".toUri()
            }
        }
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}
