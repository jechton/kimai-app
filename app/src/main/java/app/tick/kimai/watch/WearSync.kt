package app.tick.kimai.watch

import android.content.Context
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Keeps the Wear OS app (`wear` module) in step. The watch shows what [push] puts in the Data Layer and
 * sends its taps back as requests, which [WearService] runs through TimerRepository like any other surface.
 * The paths and keys are mirrored in the wear module's `Phone.kt`.
 */
object WearSync {
    const val STATE = "/tick/state"
    const val SYNC = "/tick/sync"
    const val TOGGLE = "/tick/toggle"
    const val START = "/tick/start"
    const val PROJECTS = "/tick/projects"
    const val ACTIVITIES = "/tick/activities"
    const val SIGNED_OUT = "signed_out"

    /** Does nothing without Google Play services or a paired watch. */
    fun push(
        context: Context,
        state: TimerState,
    ) {
        val prefs = Prefs(context)
        val week = prefs.weekTotals()
        val detail = prefs.weekDetail()
        val todayDone =
            detail?.let {
                val today = LocalDate.now(week?.zone ?: ZoneId.systemDefault())
                it.perDay.getOrNull(ChronoUnit.DAYS.between(it.start, today).toInt())
            } ?: 0L
        val request =
            PutDataMapRequest.create(STATE).apply {
                dataMap.putBoolean("running", state.running)
                dataMap.putLong("begin", state.beginMillis)
                dataMap.putString(
                    "label",
                    listOf(state.project, state.activity).filter { it.isNotBlank() }.joinToString(" · "),
                )
                dataMap.putString("last_label", state.lastLabel)
                dataMap.putLong("week_done", week?.doneSeconds ?: 0L)
                dataMap.putLong("week_target", week?.targetSeconds ?: 0L)
                dataMap.putLong("today_done", todayDone)
                dataMap.putInt("pending", prefs.pendingCount())
            }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }
}
