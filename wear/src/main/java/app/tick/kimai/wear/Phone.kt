package app.tick.kimai.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** What the phone app last pushed (app/.../watch/WearSync.kt). */
data class TickState(
    val running: Boolean = false,
    val begin: Long = 0L,
    val label: String = "",
    val lastLabel: String = "",
    val weekDone: Long = 0L,
    val weekTarget: Long = 0L,
    val todayDone: Long = 0L,
    val pending: Int = 0,
) {
    /** Seconds the running timer adds on top of the finished totals. */
    fun runningSeconds(now: Long = System.currentTimeMillis()) = if (running && begin > 0) (now - begin) / 1000 else 0L

    fun today(now: Long = System.currentTimeMillis()) = todayDone + runningSeconds(now)

    fun week(now: Long = System.currentTimeMillis()) = weekDone + runningSeconds(now)

    companion object {
        fun from(map: DataMap) =
            TickState(
                running = map.getBoolean("running"),
                begin = map.getLong("begin"),
                label = map.getString("label", ""),
                lastLabel = map.getString("last_label", ""),
                weekDone = map.getLong("week_done"),
                weekTarget = map.getLong("week_target"),
                todayDone = map.getLong("today_done"),
                pending = map.getInt("pending"),
            )
    }
}

class PhoneException(message: String) : Exception(message)

/** The phone app does the work; the watch asks it over the Data Layer. Paths mirror WearSync. */
object Phone {
    const val STATE = "/tick/state"
    const val SYNC = "/tick/sync"
    const val TOGGLE = "/tick/toggle"
    const val START = "/tick/start"
    const val PROJECTS = "/tick/projects"
    const val ACTIVITIES = "/tick/activities"
    private const val CAPABILITY = "tick_phone"
    private const val SIGNED_OUT = "signed_out"

    private suspend fun <T> Task<T>.get(seconds: Long = 30): T =
        withContext(Dispatchers.IO) {
            Tasks.await(this@get, seconds, TimeUnit.SECONDS)
        }

    /** Last pushed state, or null when the phone never sent one. */
    suspend fun state(context: Context): TickState? {
        val uri = Uri.Builder().scheme("wear").path(STATE).build()
        val items = Wearable.getDataClient(context).getDataItems(uri).get()
        return try {
            items.firstOrNull()?.let { TickState.from(DataMapItem.fromDataItem(it).dataMap) }
        } finally {
            items.release()
        }
    }

    /** Sends [path] to the phone and returns its reply. Throws [PhoneException] with a message to show. */
    suspend fun request(
        context: Context,
        path: String,
        data: DataMap = DataMap(),
    ): DataMap {
        val noPhone = PhoneException(context.getString(R.string.no_phone))
        val reply =
            try {
                val nodes =
                    Wearable.getCapabilityClient(
                        context,
                    ).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).get().nodes
                val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: throw noPhone
                DataMap.fromByteArray(
                    Wearable.getMessageClient(context).sendRequest(node.id, path, data.toByteArray()).get(),
                )
            } catch (e: PhoneException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw noPhone
            }
        reply.getString("error")?.let {
            throw PhoneException(if (it == SIGNED_OUT) context.getString(R.string.signed_out) else it)
        }
        return reply
    }
}

fun hoursMinutes(seconds: Long) = "%d:%02d".format(seconds / 3600, seconds / 60 % 60)
