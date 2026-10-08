package app.tick.kimai

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll
import app.tick.kimai.data.Entry
import app.tick.kimai.data.KimaiApi
import app.tick.kimai.data.Me
import app.tick.kimai.data.PendingAction
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import app.tick.kimai.notify.TimerNotifier
import app.tick.kimai.tile.TimerTileService
import app.tick.kimai.util.Fmt
import app.tick.kimai.widget.TickWidget
import app.tick.kimai.work.SyncWorker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class NotSignedIn : IllegalStateException("Not signed in")

/** entries is null when the change was only queued offline, so the caller keeps its current list. */
data class SyncResult(
    val state: TimerState,
    /** This week's entries, or the most recent ones if the week could not be loaded. */
    val entries: List<Entry>?,
    val week: WeekTotals? = null,
    /** The running entry, which may have started before this week. */
    val running: Entry? = null,
    val weekStart: LocalDate? = null,
    val pending: Int = 0,
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

    /** Kimai's first day of the week, or Monday when the server doesn't say. */
    private fun firstDay(): DayOfWeek =
        runCatching { DayOfWeek.valueOf(prefs.firstWeekday.uppercase()) }.getOrDefault(DayOfWeek.MONDAY)

    /** Caches the Kimai profile bits the week total needs: timezone, week start and contract. */
    fun saveProfile(me: Me) {
        prefs.userTimezone = me.timezone.orEmpty()
        prefs.firstWeekday = me.firstWeekday.orEmpty()
        prefs.weekTarget = me.weekTargetSeconds
        prefs.profileFetchedAt = System.currentTimeMillis()
    }

    /**
     * Every entry in the week [offset] weeks back (0 = this week) plus its finished total. The week starts
     * on the Kimai user's first day of the week. The current week's total is cached for the widget.
     */
    private suspend fun loadWeek(offset: Int): WeekView {
        val stale = System.currentTimeMillis() - prefs.profileFetchedAt > PROFILE_TTL_MS
        if (stale) runCatching { saveProfile(api().me()) }
        val zone = userZone()
        val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(firstDay())).plusWeeks(offset.toLong())
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
            flushQueue()
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
            result(state, view?.entries ?: recent, view?.totals ?: prefs.weekTotals(), running, view?.start)
        }

    /** True for failures to reach the server, as opposed to the server rejecting a request. */
    private fun isOffline(e: Throwable?): Boolean = e is IOException && e !is KimaiApi.ApiException

    private fun nowLocal(): String = Fmt.apiLocal(System.currentTimeMillis(), userZone())

    private fun label(s: TimerState): String =
        listOf(s.project, s.activity).filter {
            it.isNotBlank()
        }.joinToString(" \u00b7 ")

    private fun result(
        state: TimerState,
        entries: List<Entry>?,
        week: WeekTotals?,
        running: Entry? = null,
        weekStart: LocalDate? = null,
    ) = SyncResult(state, entries, week, running, weekStart, prefs.pendingCount())

    /**
     * Replays queued changes in order. Stops at the first network failure and keeps the rest;
     * a change the server rejects (e.g. the entry was deleted elsewhere) is dropped.
     * Returns how many are still waiting.
     */
    suspend fun flushQueue(): Int =
        queueLock.withLock {
            val queue = prefs.pending().toMutableList()
            while (queue.isNotEmpty()) {
                try {
                    replay(queue.first())
                } catch (e: IOException) {
                    if (e is KimaiApi.ApiException) {
                        queue.removeAt(0)
                        prefs.savePending(queue)
                        continue
                    }
                    break
                }
                queue.removeAt(0)
                prefs.savePending(queue)
            }
            queue.size
        }

    private suspend fun replay(action: PendingAction) {
        val api = api()
        when (action) {
            is PendingAction.Start -> api.start(action.projectId, action.activityId, action.description, action.begin)
            is PendingAction.Create ->
                api.start(action.projectId, action.activityId, action.description, action.begin, action.end)
            is PendingAction.Stop ->
                api.recent(10).firstOrNull { it.isRunning }?.let { api.patchTimes(it.id, null, action.end) }
            is PendingAction.Restart -> {
                api.restart(action.entryId)
                api.recent(10).firstOrNull { it.isRunning }?.let { api.patchTimes(it.id, action.begin, null) }
            }
            is PendingAction.Update ->
                api.updateEntry(action.entryId, action.description, action.begin, action.end)
            is PendingAction.Delete -> api.delete(action.entryId)
        }
    }

    /**
     * Runs [online] against the server. If the server can't be reached, or earlier changes are still
     * queued (order matters), the change is queued instead and [optimistic] updates the cached timer
     * so every surface reflects it right away.
     */
    private suspend fun perform(
        pending: PendingAction,
        updateTile: Boolean,
        optimistic: (TimerState) -> TimerState = { it },
        online: suspend () -> Unit,
    ): Result<SyncResult> =
        runCatching {
            val sent =
                flushQueue() == 0 &&
                    try {
                        online()
                        true
                    } catch (e: IOException) {
                        if (!isOffline(e)) throw e
                        false
                    }
            if (sent) {
                sync(updateTile = updateTile).getOrThrow()
            } else {
                queueLock.withLock { prefs.savePending(prefs.pending() + pending) }
                val state = optimistic(prefs.timerState())
                prefs.saveTimerState(state)
                refreshSurfaces(state, updateTile)
                SyncWorker.flushWhenOnline(app)
                result(state, null, prefs.weekTotals())
            }
        }

    suspend fun start(
        projectId: Int,
        activityId: Int,
        description: String,
        projectName: String = "",
        activityName: String = "",
    ): Result<SyncResult> {
        val begin = Fmt.beginNow(userZone())
        return perform(
            PendingAction.Start(projectId, activityId, description, begin),
            updateTile = true,
            optimistic = {
                it.copy(
                    running = true,
                    id = 0,
                    project = projectName,
                    activity = activityName.takeIf { a -> a != projectName }.orEmpty(),
                    description = description,
                    beginMillis = System.currentTimeMillis(),
                )
            },
        ) { api().start(projectId, activityId, description, begin) }
    }

    /** Creates a finished entry directly, e.g. for time logged after the fact. */
    suspend fun createEntry(
        projectId: Int,
        activityId: Int,
        description: String,
        beginMillis: Long,
        endMillis: Long,
    ): Result<SyncResult> {
        val zone = userZone()
        val begin = Fmt.apiLocal(beginMillis, zone)
        val end = Fmt.apiLocal(endMillis, zone)
        return perform(PendingAction.Create(projectId, activityId, description, begin, end), updateTile = true) {
            api().start(projectId, activityId, description, begin, end)
        }
    }

    suspend fun stop(updateTile: Boolean = true): Result<SyncResult> =
        perform(
            PendingAction.Stop(nowLocal()),
            updateTile,
            optimistic = {
                it.copy(
                    running = false,
                    id = 0,
                    project = "",
                    activity = "",
                    description = "",
                    beginMillis = 0L,
                    lastId = if (it.id != 0) it.id else it.lastId,
                    lastLabel = label(it).ifBlank { it.lastLabel },
                )
            },
        ) {
            val current = sync(withWeek = false, updateTile = updateTile).getOrThrow()
            if (current.state.running) api().stop(current.state.id)
        }

    /** project/activity name the optimistic state when offline; the last entry's label is the fallback. */
    suspend fun restart(
        entryId: Int,
        project: String? = null,
        activity: String? = null,
        updateTile: Boolean = true,
    ): Result<SyncResult> {
        if (entryId == 0) return Result.failure(IllegalStateException("Nothing to restart yet"))
        return perform(
            PendingAction.Restart(entryId, nowLocal()),
            updateTile,
            optimistic = {
                it.copy(
                    running = true,
                    id = 0,
                    project = project ?: it.lastLabel,
                    activity = activity.orEmpty(),
                    description = "",
                    beginMillis = System.currentTimeMillis(),
                )
            },
        ) { api().restart(entryId) }
    }

    /** Used by the widget and tile: stop if running, otherwise restart the last entry. */
    suspend fun toggle(updateTile: Boolean = true): Result<SyncResult> =
        runCatching {
            val synced = sync(size = 10, updateTile = updateTile, withWeek = false)
            val failure = synced.exceptionOrNull()
            val state =
                synced.getOrNull()?.state
                    ?: if (isOffline(failure)) prefs.timerState() else throw failure!!
            if (state.running) {
                stop(updateTile).getOrThrow()
            } else {
                restart(state.lastId, updateTile = updateTile).getOrThrow()
            }
        }

    suspend fun delete(entryId: Int): Result<SyncResult> =
        perform(PendingAction.Delete(entryId), updateTile = true) { api().delete(entryId) }

    /** endMillis null leaves the entry running. */
    suspend fun updateEntry(
        entryId: Int,
        description: String,
        beginMillis: Long,
        endMillis: Long?,
    ): Result<SyncResult> {
        val zone = userZone()
        val begin = Fmt.apiLocal(beginMillis, zone)
        val end = endMillis?.let { Fmt.apiLocal(it, zone) }
        return perform(PendingAction.Update(entryId, description, begin, end), updateTile = true) {
            api().updateEntry(entryId, description, begin, end)
        }
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

        /** One replay at a time across the app, widget, tile and worker. */
        val queueLock = Mutex()
    }
}
