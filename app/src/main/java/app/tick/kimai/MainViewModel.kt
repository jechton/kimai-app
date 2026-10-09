package app.tick.kimai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tick.kimai.data.Activity
import app.tick.kimai.data.Entry
import app.tick.kimai.data.KimaiApi
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.Project
import app.tick.kimai.data.TimerState
import app.tick.kimai.data.WeekTotals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.time.LocalDate

data class UiState(
    val loggedIn: Boolean,
    val timer: TimerState,
    val timeMode: Int,
    val busy: Boolean = false,
    val error: String? = null,
    val entries: List<Entry> = emptyList(),
    val projects: List<Project> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val selectedProject: Project? = null,
    val selectedActivity: Activity? = null,
    val description: String = "",
    val week: WeekTotals? = null,
    val pending: Int = 0,
    val weekTargetOverride: Long = 0L,
    val payRateOverride: Float = 0f,
    val payTaxPercent: Float = 0f,
    /** 0 is this week, -1 last week, and so on. The list and totals show this week. */
    val weekOffset: Int = 0,
    val weekStart: LocalDate? = null,
    val running: Entry? = null,
    /** Kimai's first day of the week as sent by the server, blank if it sent none. */
    val firstWeekday: String = "",
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = TimerRepository(app)

    private val _ui =
        MutableStateFlow(
            UiState(
                loggedIn = prefs.loggedIn,
                timer = prefs.timerState(),
                timeMode = prefs.timeMode,
                week = prefs.weekTotals(),
                pending = prefs.pendingCount(),
                weekTargetOverride = prefs.weekTargetOverride,
                payRateOverride = prefs.payRateOverride,
                payTaxPercent = prefs.payTaxPercent,
                firstWeekday = prefs.firstWeekday,
            ),
        )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = describe(e)) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun describe(e: Exception): String =
        when (e) {
            is UnknownHostException -> "Can't reach the server"
            else -> e.message ?: "Something went wrong"
        }

    private fun apply(r: SyncResult) {
        val offset = _ui.value.weekOffset
        _ui.update { it.copy(firstWeekday = prefs.firstWeekday) }
        _ui.update {
            if (offset == 0) {
                it.copy(
                    timer = r.state,
                    running = if (r.entries != null) r.running else it.running,
                    entries = r.entries ?: it.entries,
                    week = r.week ?: it.week,
                    weekStart = r.weekStart ?: it.weekStart,
                    pending = r.pending,
                )
            } else {
                it.copy(
                    timer = r.state,
                    running = if (r.entries != null) r.running else it.running,
                    pending = r.pending,
                )
            }
        }
        if (offset != 0) viewModelScope.launch { runCatching { loadWeekView(offset) } }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    // Sign in / out

    fun login(
        rawUrl: String,
        token: String,
        username: String,
    ) = launchBusy {
        val url = normalizeUrl(rawUrl)
        val cleanToken = token.trim()
        val legacyUser = username.trim()
        val me = KimaiApi(url, cleanToken, legacyUser).me()
        prefs.serverUrl = url
        prefs.token = cleanToken
        prefs.username = legacyUser
        repo.saveProfile(me)
        _ui.update { it.copy(loggedIn = true) }
        apply(repo.sync().getOrThrow())
        loadProjects()
    }

    fun logout() {
        prefs.signOut()
        _ui.value = UiState(loggedIn = false, timer = TimerState(), timeMode = prefs.timeMode)
        viewModelScope.launch { repo.refreshSurfaces(TimerState()) }
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.trimEnd('/')
        if (u.endsWith("/api")) u = u.removeSuffix("/api")
        return u
    }

    // Loading

    fun refresh() =
        launchBusy {
            if (!prefs.loggedIn) return@launchBusy
            apply(repo.sync().getOrThrow())
            if (_ui.value.projects.isEmpty()) loadProjects()
        }

    private suspend fun loadWeekView(offset: Int) {
        val v = repo.weekView(offset).getOrThrow()
        _ui.update { it.copy(weekOffset = offset, weekStart = v.start, entries = v.entries, week = v.totals) }
    }

    /** offset 0 is this week, -1 last week. Later weeks don't exist yet. */
    fun showWeek(offset: Int) = launchBusy { loadWeekView(offset.coerceAtMost(0)) }

    private suspend fun loadProjects() {
        val api = repo.apiOrNull() ?: return
        val projects = api.projects()
        val selected = projects.firstOrNull { it.id == prefs.lastProjectId } ?: projects.firstOrNull()
        _ui.update { it.copy(projects = projects, selectedProject = selected) }
        if (selected != null) loadActivities(selected)
    }

    private suspend fun loadActivities(project: Project) {
        val api = repo.apiOrNull() ?: return
        val activities = api.activities(project.id)
        val selected = activities.firstOrNull { it.id == prefs.lastActivityId } ?: activities.firstOrNull()
        _ui.update { it.copy(activities = activities, selectedActivity = selected) }
    }

    // Start form

    fun selectProject(p: Project) {
        _ui.update { it.copy(selectedProject = p, activities = emptyList(), selectedActivity = null) }
        launchBusy { loadActivities(p) }
    }

    fun selectActivity(a: Activity) = _ui.update { it.copy(selectedActivity = a) }

    fun setDescription(text: String) = _ui.update { it.copy(description = text) }

    // Timer actions

    fun start() =
        launchBusy {
            val s = _ui.value
            val project = s.selectedProject ?: error("Choose a project")
            val activity = s.selectedActivity ?: error("Choose an activity")
            prefs.lastProjectId = project.id
            prefs.lastActivityId = activity.id
            apply(
                repo.start(project.id, activity.id, s.description.trim(), project.name, activity.name).getOrThrow(),
            )
            _ui.update { it.copy(description = "") }
        }

    fun stop() =
        launchBusy {
            apply(repo.stop().getOrThrow())
        }

    fun restart(entry: Entry) =
        launchBusy {
            apply(repo.restart(entry.id, entry.project.name, entry.activityLabel).getOrThrow())
        }

    fun restartLast() =
        launchBusy {
            apply(repo.restart(_ui.value.timer.lastId).getOrThrow())
        }

    fun delete(entry: Entry) =
        launchBusy {
            apply(repo.delete(entry.id).getOrThrow())
        }

    fun addEntry(
        projectId: Int,
        activityId: Int,
        description: String,
        beginMillis: Long,
        endMillis: Long,
    ) = launchBusy {
        apply(repo.createEntry(projectId, activityId, description.trim(), beginMillis, endMillis).getOrThrow())
    }

    fun updateEntry(
        entry: Entry,
        description: String,
        beginMillis: Long,
        endMillis: Long?,
    ) = launchBusy {
        apply(repo.updateEntry(entry.id, description.trim(), beginMillis, endMillis).getOrThrow())
    }

    // Settings

    /** Hours per week typed in the app, or null to go back to the Kimai contract. */
    fun setWeekTarget(hours: Double?) {
        prefs.weekTargetOverride = hours?.takeIf { it > 0 }?.let { (it * 3600).toLong() } ?: 0L
        _ui.update {
            it.copy(
                weekTargetOverride = prefs.weekTargetOverride,
                week = it.week?.copy(targetSeconds = prefs.effectiveWeekTarget),
            )
        }
        viewModelScope.launch { repo.refreshSurfaces(prefs.timerState()) }
    }

    fun setTimeMode(mode: Int) {
        prefs.timeMode = mode
        _ui.update { it.copy(timeMode = mode) }
        viewModelScope.launch { repo.refreshSurfaces(prefs.timerState()) }
    }

    /** Hourly rate typed in the app, or null to use Kimai's per-entry rate instead. */
    fun setPayRate(hourlyRate: Double?) {
        prefs.payRateOverride = hourlyRate?.takeIf { it > 0 }?.toFloat() ?: 0f
        _ui.update { it.copy(payRateOverride = prefs.payRateOverride) }
    }

    /** Percent of gross pay withheld as tax, or null for none. */
    fun setPayTax(percent: Double?) {
        prefs.payTaxPercent = percent?.takeIf { it > 0 }?.toFloat() ?: 0f
        _ui.update { it.copy(payTaxPercent = prefs.payTaxPercent) }
    }
}
