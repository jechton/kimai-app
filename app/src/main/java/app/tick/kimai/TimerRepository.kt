package app.tick.kimai

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import app.tick.kimai.data.Entry
import app.tick.kimai.data.KimaiApi
import app.tick.kimai.data.Me
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import app.tick.kimai.notify.TimerNotifier
import app.tick.kimai.tile.TimerTileService
import app.tick.kimai.util.Fmt
import app.tick.kimai.widget.TickWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class NotSignedIn : IllegalStateException("Not signed in")

data class SyncResult(
    val state: TimerState,
    /** This week's entries, or the most recent ones if the week could not be loaded. */
    val entries: List<Entry>,
    val week: WeekTotals? = null,
    /** The running entry, which may have started before this week. */
    val running: Entry? = null,
    val weekStart: LocalDate? = null,
)

data class WeekView(val start: LocalDate, val entries: List<Entry>, val totals: WeekTotals)

/**
 * Single place that talks to Kimai for timer actions and keeps the cached state,
 * the ongoing notification, the home screen widget and the quick tile in step.
 */
class TimerRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)

    fun apiOrNull(): KimaiApi? = if (prefs.loggedIn) KimaiApi(prefs.serverUrl, prefs.token, prefs.username) else null

    private fun api(): KimaiApi = apiOrNull() ?: throw NotSignedIn()

    /** Kimai reads and writes times in the user's configured timezone. */
    private fun userZone(): ZoneId =
        prefs.userTimezone.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    /** Caches the Kimai profile bits the week total needs: timezone, week start and contract. */
    fun saveProfile(me: Me) {
        prefs.userTimezone = me.timezone.orEmpty()
        prefs.firstWeekday = me.firstWeekday ?: "monday"
        prefs.weekTarget = me.weekTargetSeconds
        prefs.profileFetchedAt = System.currentTimeMillis()
    }

    /**
     * Every entry in the week [offset] weeks back (0 = this week) plus its finished total. The week starts
     * on the Kimai user's first day of the week. The current week's total is cached for the widget.
     */
    private suspend fun loadWeek(offset: Int): WeekView {
        val stale = System.currentTimeMillis() - prefs.profileFetchedAt > PROFILE_TTL_MS
        if (stale || prefs.firstWeekday.isBlank()) runCatching { saveProfile(api().me()) }
        val zone = userZone()
        val first =
            runCatching { DayOfWeek.valueOf(prefs.firstWeekday.uppercase()) }
                .getOrDefault(DayOfWeek.MONDAY)
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(first)).plusWeeks(offset.toLong())
        val begin = Fmt.apiLocal(start.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
        val end = Fmt.apiLocal(start.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1000, zone)
        val entries = api().between(begin, end).sortedByDescending { it.beginMillis }
        val done = entries.filter { !it.isRunning }.sumOf { it.seconds }
        if (offset == 0) prefs.saveWeekDone(done)
        return WeekView(start, entries, WeekTotals(done, prefs.effectiveWeekTarget))
    }

    suspend fun weekView(offset: Int): Result<WeekView> = runCatching { loadWeek(offset) }

    suspend fun sync(
        size: Int = 40,
        updateTile: Boolean = true,
        withWeek: Boolean = true,
    ): Result<SyncResult> =
        runCatching {
            val recent = api().recent(size)
            val running = recent.firstOrNull { it.isRunning }
            val last = recent.firstOrNull { !it.isRunning }
            val old = prefs.timerState()
            val state =
                TimerState(
                    running = running != null,
                    id = running?.id ?: 0,
                    project = running?.project?.name.orEmpty(),
                    activity = running?.activityLabel.orEmpty(),
                    description = running?.description.orEmpty(),
                    beginMillis = running?.beginMillis ?: 0L,
                    lastId = last?.id ?: old.lastId,
                    lastLabel = last?.label ?: old.lastLabel,
                )
            prefs.saveTimerState(state)
            val view = if (withWeek) runCatching { loadWeek(0) }.getOrNull() else null
            refreshSurfaces(state, updateTile)
            SyncResult(
                state = state,
                entries = view?.entries ?: recent,
                week = view?.totals ?: prefs.weekTotals(),
                running = running,
                weekStart = view?.start,
            )
        }

    suspend fun start(
        projectId: Int,
        activityId: Int,
        description: String,
    ): Result<SyncResult> =
        runCatching {
            api().start(projectId, activityId, description, Fmt.beginNow(userZone()))
            sync().getOrThrow()
        }

    /** Creates a finished entry directly, e.g. for time logged after the fact. */
    suspend fun createEntry(
        projectId: Int,
        activityId: Int,
        description: String,
        beginMillis: Long,
        endMillis: Long,
    ): Result<SyncResult> =
        runCatching {
            val zone = userZone()
            api().start(
                projectId,
                activityId,
                description,
                Fmt.apiLocal(beginMillis, zone),
                Fmt.apiLocal(endMillis, zone),
            )
            sync().getOrThrow()
        }

    suspend fun stop(): Result<SyncResult> =
        runCatching {
            val current = sync().getOrThrow()
            if (current.state.running) api().stop(current.state.id)
            sync().getOrThrow()
        }

    suspend fun restart(entryId: Int): Result<SyncResult> =
        runCatching {
            if (entryId == 0) error("Nothing to restart yet")
            api().restart(entryId)
            sync().getOrThrow()
        }

    /** Used by the widget and tile: stop if running, otherwise restart the last entry. */
    suspend fun toggle(updateTile: Boolean = true): Result<SyncResult> =
        runCatching {
            val current = sync(size = 10, updateTile = updateTile, withWeek = false).getOrThrow()
            if (current.state.running) {
                api().stop(current.state.id)
            } else {
                if (current.state.lastId == 0) error("Nothing to restart yet")
                api().restart(current.state.lastId)
            }
            sync(size = 10, updateTile = updateTile).getOrThrow()
        }

    suspend fun delete(entryId: Int): Result<SyncResult> =
        runCatching {
            api().delete(entryId)
            sync().getOrThrow()
        }

    /** endMillis null leaves the entry running. */
    suspend fun updateEntry(
        entryId: Int,
        description: String,
        beginMillis: Long,
        endMillis: Long?,
    ): Result<SyncResult> =
        runCatching {
            val zone = userZone()
            api().updateEntry(
                entryId,
                description,
                Fmt.apiLocal(beginMillis, zone),
                endMillis?.let { Fmt.apiLocal(it, zone) },
            )
            sync().getOrThrow()
        }

    suspend fun refreshSurfaces(
        state: TimerState,
        updateTile: Boolean = true,
    ) {
        TimerNotifier.update(app, state)
        runCatching { TickWidget().updateAll(app) }
        if (updateTile) {
            runCatching {
                TileService.requestListeningState(app, ComponentName(app, TimerTileService::class.java))
            }
        }
    }

    private companion object {
        const val PROFILE_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
