package online.bus.bmarian.conexiuniclujmobile

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Favorites
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.LOCATION_UPDATE_MS
import online.bus.bmarian.conexiuniclujmobile.data.Repository
import online.bus.bmarian.conexiuniclujmobile.data.RouteUpdates
import online.bus.bmarian.conexiuniclujmobile.data.Settings
import online.bus.bmarian.conexiuniclujmobile.data.Shortcuts
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.follow.Reminders
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.work.RouteChangeWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.init(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Services.settings.applyLocale()
    }
}

object Services {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: Repository
        private set
    lateinit var favorites: Favorites
        private set
    lateinit var settings: Settings
        private set
    lateinit var routeUpdates: RouteUpdates
        private set

    private val _location = MutableStateFlow<Fix?>(null)
    /** Null outside Cluj county. */
    val location: StateFlow<Fix?> = _location
    private val permissionChecks = MutableStateFlow(0)
    private val locationInterval = MutableStateFlow(LOCATION_UPDATE_MS)

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    private val _clock = MutableStateFlow(System.currentTimeMillis())
    val clock: StateFlow<Long> = _clock

    fun init(app: Application) {
        Api.init(app)
        repository = Repository(app)
        favorites = Favorites(app)
        settings = Settings(app)
        routeUpdates = RouteUpdates(app)
        settings.applyLocale()
        watchConnectivity(app)
        scope.launch {
            repository.loadCached()
            repository.refreshNetwork(now = true)
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        _clock.value = System.currentTimeMillis()
                        delay(10_000)
                    }
                }
                launch {
                    online.collect { up ->
                        if (up) {
                            repository.refreshNetwork()
                        }
                    }
                }
                combine(permissionChecks, locationInterval) { _, ms -> ms }.collectLatest { ms ->
                    UserLocation.updates(app, ms).collect { fix ->
                        _location.value = fix
                        if (fix != null) UserLocation.save(app, fix)
                    }
                }
            }
        }
        RouteChangeWorker.schedule(app)
        WearLink.start(app)
        Reminders.init(app)
        AppUpdates.init(app)
        scope.launch {
            combine(favorites.stops, repository.stops) { ids, stops -> ids to stops }.collect { (ids, stops) ->
                if (stops.isNotEmpty()) Shortcuts.updateFavoriteStops(app, ids, stops)
            }
        }
    }

    fun locationPermissionChanged() {
        permissionChecks.value++
    }

    fun fastLocation(on: Boolean) {
        locationInterval.value = if (on) 1_000L else LOCATION_UPDATE_MS
    }

    fun tick() {
        _clock.value = System.currentTimeMillis()
    }

    private fun watchConnectivity(context: Context) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = true
            }

            override fun onLost(network: Network) {
                _online.value = false
            }
        })
    }
}
