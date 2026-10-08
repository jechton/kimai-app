package app.tick.kimai.data

import android.content.Context
import androidx.core.content.edit
import app.tick.kimai.util.TimeMode

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

    val effectiveWeekTarget: Long get() = weekTargetOverride.takeIf { it > 0 } ?: weekTarget

    /** When the user profile (week start, contract) was last read from Kimai. */
    var profileFetchedAt: Long
        get() = sp.getLong("profile_at", 0L)
        set(v) = sp.edit { putLong("profile_at", v) }

    fun weekTotals(): WeekTotals? =
        sp.getLong("week_done", -1L).takeIf { it >= 0 }?.let { WeekTotals(it, effectiveWeekTarget) }

    fun saveWeekDone(seconds: Long) = sp.edit { putLong("week_done", seconds) }

    var timeMode: Int
        get() = sp.getInt("time_mode", TimeMode.SYSTEM)
        set(v) = sp.edit { putInt("time_mode", v) }

    var lastProjectId: Int
        get() = sp.getInt("last_project", 0)
        set(v) = sp.edit { putInt("last_project", v) }

    var lastActivityId: Int
        get() = sp.getInt("last_activity", 0)
        set(v) = sp.edit { putInt("last_activity", v) }

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
