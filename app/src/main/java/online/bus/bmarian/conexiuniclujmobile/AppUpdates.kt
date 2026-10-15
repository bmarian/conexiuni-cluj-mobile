package online.bus.bmarian.conexiuniclujmobile

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AppUpdates {
    private lateinit var manager: AppUpdateManager
    private lateinit var prefs: SharedPreferences
    private var version = 0

    private val _available = MutableStateFlow(false)
    /** An update the user hasn't turned down. */
    val available: StateFlow<Boolean> = _available

    fun init(context: Context) {
        manager = AppUpdateManagerFactory.create(context)
        prefs = context.getSharedPreferences("appUpdate", Context.MODE_PRIVATE)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) Services.scope.launch { check() }
        })
    }

    private suspend fun check() {
        val info = info() ?: return
        version = info.availableVersionCode()
        _available.value = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && prefs.getInt("declined", 0) != version
    }

    suspend fun update(launcher: ActivityResultLauncher<IntentSenderRequest>) =
        start(launcher, UpdateAvailability.UPDATE_AVAILABLE, UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)

    /** Picks an update the user left back up. */
    suspend fun resume(launcher: ActivityResultLauncher<IntentSenderRequest>) =
        start(launcher, UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)

    fun decline() {
        prefs.edit { putInt("declined", version) }
        _available.value = false
    }

    private suspend fun start(launcher: ActivityResultLauncher<IntentSenderRequest>, vararg states: Int) {
        val info = info() ?: return
        if (info.updateAvailability() in states) {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
        }
    }

    // Fails on installs that didn't come from Play.
    private suspend fun info(): AppUpdateInfo? = runCatching { manager.appUpdateInfo.await() }.getOrNull()
}
