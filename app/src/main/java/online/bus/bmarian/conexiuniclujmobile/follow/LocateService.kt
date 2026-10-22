package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.data.Fix
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation

const val LOCATE_MS = 5_000L

/** One fix for a reminder while the app is closed: from the background, a request often goes unanswered. */
class LocateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FollowNotifications.ensureChannels(this)
        val notification = Notification.Builder(this, FollowNotifications.FOLLOW_CHANNEL)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(getString(R.string.locating))
            .setLocalOnly(true)
            .build()
        val city = intent?.getStringExtra(EXTRA_CITY)
        val foreground = runCatching {
            ServiceCompat.startForeground(this, FollowNotifications.slot("locate"), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }.isSuccess
        scope.launch {
            val fix = if (foreground && city != null) withTimeoutOrNull(LOCATE_MS) { UserLocation.current(this@LocateService, city) } else null
            pending?.complete(fix)
            ServiceCompat.stopForeground(this@LocateService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CITY = "city"
        private val lock = Mutex()
        @Volatile private var pending: CompletableDeferred<Fix?>? = null

        /** Falls back to a plain request when the service may not start, as from an inexact alarm. */
        suspend fun fix(context: Context, city: String): Fix? = lock.withLock {
            val result = CompletableDeferred<Fix?>().also { pending = it }
            val started = runCatching {
                context.startForegroundService(Intent(context, LocateService::class.java).putExtra(EXTRA_CITY, city))
            }.isSuccess
            if (started) result.await() else withTimeoutOrNull(LOCATE_MS) { UserLocation.current(context, city) }
        }
    }
}
