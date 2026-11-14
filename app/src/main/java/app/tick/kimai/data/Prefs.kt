package app.tick.kimai.data

import android.content.Context
import androidx.core.content.edit
import app.tick.kimai.util.TimeMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tick", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("url", "").orEmpty()
        set(v) = sp.edit { putString("url", v) }

    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(v) = sp.edit { putString("token", v) }

    /** Only for old Kimai servers that use the legacy API password headers. */
    var username: String
        get() = sp.getString("username", "").orEmpty()
        set(v) = sp.edit { putString("username", v) }

    var userTimezone: String
        get() = sp.getString("tz", "").orEmpty()
        set(v) = sp.edit { putString("tz", v) }

    var firstWeekday: String
        get() = sp.getString("first_weekday", "").orEmpty()
        set(v) = sp.edit { putString("first_weekday", v) }

    /** Contracted seconds per week from Kimai, 0 when none. */
    var weekTarget: Long
        get() = sp.getLong("week_target", 0L)
        set(v) = sp.edit { putLong("week_target", v) }

    /** Weekly target typed into the app, in seconds. 0 means use the Kimai contract. */
    var weekTargetOverride: Long
        get() = sp.getLong("week_target_override", 0L)
        set(v) = sp.edit { putLong("week_target_override", v) }

    /** Days worked, typed into the app: bit 0 is Monday .. bit 6 Sunday. 0 means not set (all days count). */
    var workDaysMask: Int
        get() = sp.getInt("work_days_mask", 0)
        set(v) = sp.edit { putInt("work_days_mask", v) }

    /** Contracted seconds Monday..Sunday, comma separated; empty unless Kimai has a per-day contract. */
    var workdays: String
        get() = sp.getString("workdays", "").orEmpty()
        set(v) = sp.edit { putString("workdays", v) }

    val effectiveWeekTarget: Long get() = weekTargetOverride.takeIf { it > 0 } ?: weekTarget

    /** Hourly pay rate typed into the app, overriding Kimai's per-entry rate when > 0. */
    var payRateOverride: Float
        get() = sp.getFloat("pay_rate_override", 0f)
        set(v) = sp.edit { putFloat("pay_rate_override", v) }

    /** Percent of gross pay withheld as tax, 0-100. */
    var payTaxPercent: Float
        get() = sp.getFloat("pay_tax_percent", 0f)
        set(v) = sp.edit { putFloat("pay_tax_percent", v) }

    /** For people who never use descriptions: hide the field in the Start, Add and Edit forms. */
    var hideDescription: Boolean
        get() = sp.getBoolean("hide_description", false)
        set(v) = sp.edit { putBoolean("hide_description", v) }

    /** Minutes-ago choices on the Start form, at most 3 so they fit on small screens. */
    var startPresets: List<Int>
        get() =
            sp.getString("start_presets", null)
                ?.split(',')?.mapNotNull { it.toIntOrNull() }
                ?: listOf(5, 15, 30)
        set(v) = sp.edit { putString("start_presets", v.joinToString(",")) }

    fun weekTotals(): WeekTotals? =
        sp.getLong("week_done", -1L).takeIf { it >= 0 }?.let { WeekTotals(it, effectiveWeekTarget) }

    fun saveWeekDone(seconds: Long) = sp.edit { putLong("week_done", seconds) }

    /** Finished entries cached for the large widget's recent-entries list. */
    fun recentEntries(): List<RecentEntry> = RecentEntry.decodeList(sp.getString("recent_entries", "").orEmpty())

    fun saveRecentEntries(list: List<RecentEntry>) =
        sp.edit {
            putString(
                "recent_entries",
                RecentEntry.encodeList(list),
            )
        }

    /** Projects and per-project activities from the last successful fetch, so the pickers work offline. */
    fun projects(): List<Project> = decodeCache("projects", Project.serializer())

    fun saveProjects(list: List<Project>) = encodeCache("projects", list, Project.serializer())

    fun activities(projectId: Int): List<Activity> = decodeCache("activities_$projectId", Activity.serializer())

    fun saveActivities(
        projectId: Int,
        list: List<Activity>,
    ) = encodeCache("activities_$projectId", list, Activity.serializer())

    private fun <T> decodeCache(
        key: String,
        item: KSerializer<T>,
    ): List<T> =
        runCatching { Json.decodeFromString(ListSerializer(item), sp.getString(key, "").orEmpty()) }
            .getOrDefault(emptyList())

    private fun <T> encodeCache(
        key: String,
        list: List<T>,
        item: KSerializer<T>,
    ) = sp.edit { putString(key, Json.encodeToString(ListSerializer(item), list)) }

    fun pending(): List<PendingAction> = PendingAction.decodeList(sp.getString("pending", "").orEmpty())

    fun savePending(list: List<PendingAction>) =
        sp.edit {
            if (list.isEmpty()) remove("pending") else putString("pending", PendingAction.encodeList(list))
        }

    fun pendingCount(): Int = pending().size

    var timeMode: Int
        get() = sp.getInt("time_mode", TimeMode.SYSTEM)
        set(v) = sp.edit { putInt("time_mode", v) }

    var lastProjectId: Int
        get() = sp.getInt("last_project", 0)
        set(v) = sp.edit { putInt("last_project", v) }

    var lastActivityId: Int
        get() = sp.getInt("last_activity", 0)
        set(v) = sp.edit { putInt("last_activity", v) }

    /** Whether the user dismissed the battery optimization prompt (don't ask again). */
    var batteryPromptDismissed: Boolean
        get() = sp.getBoolean("battery_prompt_dismissed", false)
        set(v) = sp.edit { putBoolean("battery_prompt_dismissed", v) }

    val loggedIn: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

    fun timerState() =
        TimerState(
            running = sp.getBoolean("t_running", false),
            id = sp.getInt("t_id", 0),
            project = sp.getString("t_project", "").orEmpty(),
            activity = sp.getString("t_activity", "").orEmpty(),
            description = sp.getString("t_desc", "").orEmpty(),
            beginMillis = sp.getLong("t_begin", 0L),
            lastId = sp.getInt("t_last_id", 0),
            lastLabel = sp.getString("t_last_label", "").orEmpty(),
        )

    fun saveTimerState(s: TimerState) =
        sp.edit {
            putBoolean("t_running", s.running)
            putInt("t_id", s.id)
            putString("t_project", s.project)
            putString("t_activity", s.activity)
            putString("t_desc", s.description)
            putLong("t_begin", s.beginMillis)
            putInt("t_last_id", s.lastId)
            putString("t_last_label", s.lastLabel)
        }

    fun signOut() {
        val mode = timeMode
        sp.edit { clear() }
        timeMode = mode
    }
}
