package online.bus.bmarian.conexiuniclujmobile.data.sync

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.RegionSettings
import java.io.IOException

// Collects a burst of edits (a reorder, a few toggles) into one write.
private const val AFTER_EDIT_MS = 2000L

/** When this device syncs and what the Settings screen shows about it. Port of stores/sync.ts. */
class Sync(private val context: Context) {
    enum class Status { Unknown, Off, On }

    val engine = SyncEngine(SyncVault(context))
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(Status.Unknown)
    val status: StateFlow<Status> = _status
    val busy = MutableStateFlow(false)
    val devices = MutableStateFlow<List<SyncEngine.DeviceView>>(emptyList())
    val problems = MutableStateFlow<Set<SyncEngine.Problem>>(emptySet())
    val lastSynced = MutableStateFlow<Long?>(null)
    val removed = MutableStateFlow(false)
    val offline = MutableStateFlow(false)
    private val _withSettings = MutableStateFlow(prefs.getBoolean(SETTINGS_KEY, true))
    val withSettings: StateFlow<Boolean> = _withSettings

    private val running = Mutex()
    private var again = false

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        _status.value = if (engine.membership() != null) Status.On else Status.Off
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { syncNow() }
        }
        scope.launch { Services.online.drop(1).filter { it }.collect { syncNow() } }
        val favorites = Services.favorites
        scope.launch {
            combine(favorites.routes, favorites.stops, favorites.plans, Services.routeUpdates.followed, Services.routeUpdates.seen) { _, _, _, _, _ -> }
                .drop(1).debounce(AFTER_EDIT_MS).collect { syncNow() }
        }
        val s = Services.settings
        scope.launch {
            combine(s.theme, s.locale, s.autoCenterOnMe, s.autoFitMap) { _, _, _, _ -> }
                .combine(s.regions) { _, regions -> s.regionSetting(Cities.DEFAULT, RegionSettings.GREEN_FRIDAY, regions) }
                .drop(1).debounce(AFTER_EDIT_MS).collect { if (_withSettings.value) syncNow() }
        }
    }

    suspend fun syncNow() {
        if (_status.value != Status.On) return
        if (!running.tryLock()) {
            again = true
            return
        }
        busy.value = true
        try {
            do {
                again = false
                val report = engine.syncOnce(_withSettings.value, SyncLocal.deviceLabel())
                devices.value = report.devices
                problems.value = report.problems
                lastSynced.value = System.currentTimeMillis()
                offline.value = false
                if (report.dropped > 0) Services.settings.showToast(context.getString(R.string.w_sync_dropped, report.dropped.toString()))
            } while (again)
        } catch (_: SyncEngine.Gone) {
            _status.value = Status.Off
            removed.value = true
            devices.value = emptyList()
        } catch (_: SyncHttpException) {
            problems.value = problems.value + SyncEngine.Problem.Broken
        } catch (_: IOException) {
            offline.value = true
        } finally {
            busy.value = false
            running.unlock()
        }
    }

    /** A pairing just made this device a member. */
    suspend fun joined() {
        removed.value = false
        problems.value = emptySet()
        _status.value = Status.On
        syncNow()
    }

    fun setWithSettings(value: Boolean, scope: CoroutineScope) {
        _withSettings.value = value
        prefs.edit { putBoolean(SETTINGS_KEY, value) }
        if (value) scope.launch { syncNow() }
    }

    suspend fun removeDevice(id: String) {
        running.lock()
        busy.value = true
        try {
            engine.removeDevice(id, _withSettings.value, SyncLocal.deviceLabel())
        } finally {
            busy.value = false
            running.unlock()
        }
        syncNow()
    }

    suspend fun stop(everywhere: Boolean) {
        running.lock()
        busy.value = true
        try {
            if (everywhere) engine.deleteEverything() else engine.leave()
            _status.value = Status.Off
            devices.value = emptyList()
            problems.value = emptySet()
        } finally {
            busy.value = false
            running.unlock()
        }
    }

    private companion object {
        const val SETTINGS_KEY = "syncSettings"
    }
}
