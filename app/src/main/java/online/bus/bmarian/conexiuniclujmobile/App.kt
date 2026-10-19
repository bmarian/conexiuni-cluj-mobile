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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Cities
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

// Long enough for a cold fix indoors.
private const val LOCATE_TIMEOUT_MS = 8_000L

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
    private lateinit var app: Application
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: Repository
        private set
    lateinit var favorites: Favorites
        private set
    lateinit var settings: Settings
        private set
    lateinit var routeUpdates: RouteUpdates
        private set

    private val rawLocation = MutableStateFlow<Fix?>(null)
    /** Null away from the city on screen: nothing there is near the person. */
    val location: StateFlow<Fix?> = combine(rawLocation, Cities.current, Cities.list) { fix, city, _ ->
        fix?.takeIf { Cities.contains(it.lat, it.lon, city) }
    }.stateIn(scope, SharingStarted.Eagerly, null)
    private val permissionChecks = MutableStateFlow(0)
    private val locationInterval = MutableStateFlow(LOCATION_UPDATE_MS)

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    private val _clock = MutableStateFlow(System.currentTimeMillis())
    val clock: StateFlow<Long> = _clock

    fun init(app: Application) {
        this.app = app
        Api.init(app)
        Cities.init(app)
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
        scope.launch { openOnCity(app) }
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
                        rawLocation.value = fix
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

    /** Shows [slug]: its bundle, favorites and followed lines. */
    fun showCity(slug: String) {
        if (slug == Cities.current.value) return
        repository.use(slug)
        favorites.use(slug)
        routeUpdates.use(slug)
        Cities.show(slug)
        scope.launch {
            repository.loadCached()
            repository.refreshNetwork(now = true)
        }
    }

    /** Null for the nearest city. */
    fun chooseCity(slug: String?) {
        Cities.choose(slug)
        showCity(Cities.resolve(rawLocation.value ?: UserLocation.recent(app, city = null)))
    }

    // The nearest city needs a fix; none is asked for here. A link opened meanwhile has the last word.
    private suspend fun openOnCity(context: Context) {
        val shown = Cities.current.value
        val list = Cities.reload()
        val fix = if (Cities.choice.value == null && list.size > 1 && UserLocation.permitted(context)) {
            UserLocation.recent(context, city = null) ?: withTimeoutOrNull(LOCATE_TIMEOUT_MS) { rawLocation.filterNotNull().first() }
        } else null
        if (Cities.current.value == shown) showCity(Cities.resolve(fix))
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
