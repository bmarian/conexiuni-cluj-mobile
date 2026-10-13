package online.bus.bmarian.conexiuniclujmobile.follow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.UserLocation
import online.bus.bmarian.conexiuniclujmobile.logic.CLUJ_ZONE
import online.bus.bmarian.conexiuniclujmobile.logic.nextOccurrence
import online.bus.bmarian.conexiuniclujmobile.logic.pickSlot
import online.bus.bmarian.conexiuniclujmobile.data.StopInfo
import online.bus.bmarian.conexiuniclujmobile.logic.TimetableTab
import online.bus.bmarian.conexiuniclujmobile.logic.stopTimesByDayType
import online.bus.bmarian.conexiuniclujmobile.logic.walkMinutes
import java.time.LocalDate

const val LEAVE_BUFFER_MIN = 5
// How early to go when the walk can't be timed.
const val NO_LOCATION_LEAD_MIN = 20
// The first look, early enough for a long walk.
private const val FIRST_LOOK_MIN = 75
// The card shows up this long before it's time to leave.
private const val HEADS_UP_MIN = 10
private const val REARM_SLACK_MS = 2 * 60_000L
private const val MISSED_MS = 2 * 60_000L
private const val LOCATE_MS = 5_000L

/** A departure to be told about, every time it runs on [days], or once on [date]. */
@Serializable
data class Reminder(
    val id: String,
    val followed: Followed,
    val lat: Double,
    val lon: Double,
    // Minute of the day at the stop; null follows the day's last bus.
    val time: Int? = null,
    // ISO days of the week; empty for a one-off on [date].
    val days: List<Int> = emptyList(),
    val date: String? = null,
    // Timetable minute at the stop per day type.
    val slots: Map<String, Int> = emptyMap(),
    // Epoch ms of the latest departure already handed to a follow.
    val handled: Long = 0,
) {
    val once get() = date != null

    fun next(afterMs: Long = System.currentTimeMillis()): Long? =
        nextOccurrence(slots, days, date?.let(LocalDate::parse), maxOf(afterMs - MISSED_MS, handled), CLUJ_ZONE)

    fun isFor(stopId: Int, routeId: Int, tripId: String) =
        followed.stopId == stopId && followed.routeId == routeId && followed.tripId == tripId
}

/** How long before the bus to set off: the walk and a few minutes, or a fixed lead when there's no fix. */
fun leadMinutes(walk: Int?) = walk?.plus(LEAVE_BUFFER_MIN) ?: NO_LOCATION_LEAD_MIN

/** The stop's timetable minutes per day type for [followed], each departure timed for its own hour. */
suspend fun reminderTimes(info: StopInfo, followed: Followed): Map<String, List<Int>> {
    val shape = info.shapesInfo.firstOrNull { it.routeId == followed.routeId } ?: return emptyMap()
    val hourly = TimetableTab.entries.associate { tab ->
        tab.dayType to runCatching { Services.repository.hourlyOffsets(shape.shortName, tab.dayType)[followed.tripId] }.getOrNull()
    }
    return stopTimesByDayType(shape, followed.tripId, followed.stopId, hourly)
}

/** [time] null for the last bus. */
fun resolveSlots(times: Map<String, List<Int>>, time: Int?): Map<String, Int> =
    times.mapNotNull { (type, list) -> pickSlot(list, time)?.let { type to it } }.toMap()

object Reminders {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow<List<Reminder>>(emptyList())
    val all: StateFlow<List<Reminder>> = mutable

    private fun prefs(context: Context) = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun init(context: Context) {
        mutable.value = prefs(context).getString("all", null)
            ?.let { runCatching { Api.json.decodeFromString<List<Reminder>>(it) }.getOrNull() }
            .orEmpty()
        arm(context)
        scope.launch { refreshSlots(context) }
    }

    fun forBus(stopId: Int, routeId: Int, tripId: String) = all.value.firstOrNull { it.isFor(stopId, routeId, tripId) }

    fun put(context: Context, reminder: Reminder) {
        mutable.update { list -> if (list.any { it.id == reminder.id }) list.map { if (it.id == reminder.id) reminder else it } else list + reminder }
        save(context)
        arm(context, listOf(reminder))
    }

    fun remove(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id, 0))
        mutable.update { list -> list.filterNot { it.id == id } }
        save(context)
    }

    fun exactAllowed(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun save(context: Context) {
        prefs(context).edit { putString("all", Api.json.encodeToString(all.value)) }
    }

    /** Sets each reminder's alarm for the first look at its next departure. */
    fun arm(context: Context, reminders: List<Reminder> = all.value) {
        for (reminder in reminders) {
            val at = reminder.next()
            if (at == null) {
                if (reminder.once) remove(context, reminder.id)
                continue
            }
            alarm(context, reminder.id, at, at - FIRST_LOOK_MIN * 60_000L)
        }
    }

    private fun alarm(context: Context, id: String, at: Long, wakeAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context, id, at)
        if (exactAllowed(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, pending)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, pending)
    }

    private fun pending(context: Context, id: String, at: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java)
            .setData("${WearLink.APP_SCHEME}://reminder/$id".toUri())
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_AT, at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Times the walk from where the user is, and either looks again later or starts following the bus. */
    internal suspend fun fire(context: Context, id: String, at: Long) {
        val reminder = all.value.firstOrNull { it.id == id } ?: return
        if (at <= reminder.handled) return
        val now = System.currentTimeMillis()
        if (at < now - MISSED_MS) return handled(context, reminder, at)
        val fix = withTimeoutOrNull(LOCATE_MS) { UserLocation.current(context) } ?: UserLocation.recent(context)
        val walk = fix?.let { walkMinutes(it.lat, it.lon, reminder.lat, reminder.lon) }
        val wakeAt = at - (leadMinutes(walk) + HEADS_UP_MIN) * 60_000L
        if (wakeAt > now + REARM_SLACK_MS) return alarm(context, id, at, wakeAt)
        handled(context, reminder, at)
        val target = FollowTarget(id, at, reminder.lat, reminder.lon)
        if (!FollowManager.start(context, reminder.followed, target)) FollowNotifications.nudge(context, reminder.followed, at, walk)
    }

    private fun handled(context: Context, reminder: Reminder, at: Long) {
        if (reminder.once) remove(context, reminder.id) else put(context, reminder.copy(handled = at))
    }

    /** Timetables change: each reminder follows its bus to the current times. */
    private suspend fun refreshSlots(context: Context) {
        val repository = Services.repository
        var changed = false
        val updated = all.value.map { reminder ->
            val info = runCatching { repository.stopInfo(reminder.followed.stopId) }.getOrNull() ?: return@map reminder
            val slots = resolveSlots(reminderTimes(info, reminder.followed), reminder.time)
            if (slots.isEmpty() || slots == reminder.slots) reminder else reminder.copy(slots = slots).also { changed = true }
        }
        if (!changed) return
        mutable.update { list -> list.map { r -> updated.firstOrNull { it.id == r.id } ?: r } }
        save(context)
        arm(context)
    }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exact alarms were allowed or taken away: set them all again.
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return Reminders.arm(context.applicationContext)
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val app = context.applicationContext
        val done = goAsync()
        Reminders.launch {
            try {
                Reminders.fire(app, id, at)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_AT = "at"
    }
}

/** Alarms don't survive reboots; starting the app sets them again. */
class RemindersRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
