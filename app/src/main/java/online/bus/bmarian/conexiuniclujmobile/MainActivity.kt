package online.bus.bmarian.conexiuniclujmobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.LegacyLocale
import online.bus.bmarian.conexiuniclujmobile.data.Places
import online.bus.bmarian.conexiuniclujmobile.follow.FollowManager
import online.bus.bmarian.conexiuniclujmobile.follow.FollowNotifications
import online.bus.bmarian.conexiuniclujmobile.follow.FollowTarget
import online.bus.bmarian.conexiuniclujmobile.follow.Followed
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.ui.PlanQuery
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.ui.AppShell
import online.bus.bmarian.conexiuniclujmobile.ui.theme.ConexiuniTheme

class MainActivity : ComponentActivity() {
    private val links = MutableStateFlow<android.net.Uri?>(null)
    private val updateFlow = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        LegacyLocale.override(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        setContent {
            val mode by Services.settings.theme.collectAsStateWithLifecycle()
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
            ConexiuniTheme(dark) {
                AppShell(dark = dark, links = links)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch { Services.settings.locale.drop(1).collect { recreate() } }
        }
    }

    override fun onResume() {
        super.onResume()
        WearLink.refreshReach(this)
        lifecycleScope.launch { AppUpdates.resume(updateFlow) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::planToShared)
            return
        }
        val data = intent.data ?: intent.getStringExtra(EXTRA_LINK)?.let(android.net.Uri::parse) ?: return
        links.value = data
        // A tapped reminder; not again when reopened from recents.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            intent.getStringExtra(FollowNotifications.EXTRA_FOLLOW)
                ?.let { runCatching { Api.json.decodeFromString<Followed>(it) }.getOrNull() }
                ?.let { followed ->
                    val at = intent.getLongExtra(FollowNotifications.EXTRA_FOLLOW_AT, 0L)
                    FollowManager.start(this, followed, FollowTarget(at).takeIf { at > 0 })
                }
        }
    }

    private fun planToShared(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value
        if (url != null && url.toUri().host in SITE_HOSTS) {
            links.value = url.toUri()
            return
        }
        val query = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("http") } ?: text.trim()
        Services.settings.showToast(getString(R.string.share_finding, query.take(40)))
        lifecycleScope.launch {
            val place = Places.search(query, 1).firstOrNull()
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

        // Shared links can still point at the old host.
        private val SITE_HOSTS = setOf(BuildConfig.SITE_ORIGIN.toUri().host, "bus.bmarian.online")
    }
}
