package app.tick.kimai.data

import android.content.Context
import app.tick.kimai.R
import app.tick.kimai.util.Fmt
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Kimai returns project/activity as a bare id or as an object, depending on the endpoint. */
@Serializable
data class Ref(val id: Int, val name: String? = null)

object RefSerializer : JsonTransformingSerializer<Ref>(Ref.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive) JsonObject(mapOf("id" to element)) else element
}

@Serializable
data class Entry(
    val id: Int,
    val begin: String,
    val end: String? = null,
    val duration: Long? = null,
    val description: String? = null,
    /** Kimai's computed pay for this entry (hourly rate x duration), null if rates aren't configured or visible to this user. */
    val rate: Double? = null,
    @Serializable(with = RefSerializer::class) val project: Ref,
    @Serializable(with = RefSerializer::class) val activity: Ref,
    /** Only set by the all-users query; the signed-in user otherwise. */
    @Serializable(with = RefSerializer::class) val user: Ref? = null,
) {
    val isRunning: Boolean get() = end == null
    val beginMillis: Long get() = Fmt.parseMillis(begin)
    val endMillis: Long? get() = end?.let { Fmt.parseMillis(it) }
    val seconds: Long
        get() = duration?.takeIf { it > 0 } ?: (((endMillis ?: beginMillis) - beginMillis) / 1000)

    /** Activity name, or null when missing or identical to the project name. */
    val activityLabel: String?
        get() = activity.name?.takeIf { it != project.name }
    val label: String
        get() = listOfNotNull(project.name, activityLabel).joinToString(" · ")
}

@Serializable
data class Project(
    val id: Int,
    val name: String,
    val parentTitle: String? = null,
) {
    val display: String
        get() = if (parentTitle.isNullOrBlank()) name else "$name · $parentTitle"
}

@Serializable
data class Activity(
    val id: Int,
    val name: String,
    val parentTitle: String? = null,
)

@Serializable
data class User(
    val id: Int,
    val username: String = "",
    val alias: String? = null,
) {
    val display: String get() = alias?.takeIf { it.isNotBlank() } ?: username
}

@Serializable
data class Me(
    val id: Int = 0,
    val username: String = "",
    val alias: String? = null,
    val timezone: String? = null,
    val roles: List<String> = emptyList(),
    val preferences: List<Pref> = emptyList(),
) {
    /** Roles Kimai lets read other users' timesheets (the server still enforces it). */
    val canViewOthers: Boolean
        get() = roles.any { it in setOf("ROLE_TEAMLEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN") }

    private fun pref(name: String): String? =
        preferences.firstOrNull { it.name == name }?.value?.let { (it as? JsonPrimitive)?.content }

    /** Kimai's per-user "first day of the week" preference, e.g. "monday" or "sunday". */
    val firstWeekday: String? get() = pref("first_weekday")

    /**
     * Contracted seconds per week, or 0 when none is configured. Kimai stores the contract as
     * seconds, either as a weekly total or as one value per day. Monthly contracts give no weekly target.
     */
    val weekTargetSeconds: Long
        get() =
            when (pref("work_contract_type")) {
                "week" -> pref("hours_per_week")?.toLongOrNull() ?: 0L
                "day" -> workdaySeconds.sum()
                else -> 0L
            }.coerceAtLeast(0L)

    /** Contracted seconds for Monday..Sunday, or empty unless the contract is set per day. */
    val workdaySeconds: List<Long>
        get() =
            if (pref("work_contract_type") != "day") {
                emptyList()
            } else {
                listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                    .map { (pref("work_$it")?.toLongOrNull() ?: 0L).coerceAtLeast(0L) }
            }
}

@Serializable
data class Pref(val name: String, val value: JsonElement? = null)

/** Payload of Kimai's "Create API access" QR code, e.g. {"type":"kimai","version":1,"url":"...","token":"..."}. */
@Serializable
data class KimaiQr(
    val type: String = "",
    val url: String = "",
    val token: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): KimaiQr? =
            runCatching { json.decodeFromString(serializer(), raw) }
                .getOrNull()
                ?.takeIf { it.type == "kimai" && it.url.isNotBlank() && it.token.isNotBlank() }
    }
}

/** Cached view of the running timer, shared by the app, notification, widget and tile. */
data class TimerState(
    val running: Boolean = false,
    val id: Int = 0,
    val project: String = "",
    val activity: String = "",
    val description: String = "",
    val beginMillis: Long = 0L,
    val lastId: Int = 0,
    val lastLabel: String = "",
)

/** A finished entry as cached for the large widget's recent-entries list. */
@Serializable
data class RecentEntry(val label: String, val seconds: Long, val beginMillis: Long, val endMillis: Long) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        // lazy, same reason as PendingAction's listSerializer: serializer() needs the companion's
        // own generated $cachedSerializer field, which isn't set yet during <clinit>.
        private val listSerializer by lazy { ListSerializer(serializer()) }

        fun decodeList(raw: String): List<RecentEntry> =
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())

        fun encodeList(list: List<RecentEntry>): String = json.encodeToString(listSerializer, list)
    }
}

/** What the week still needs: today's goal and the average for each work day after today. */
data class PacePlan(
    val todayGoal: Long,
    /** How far behind the even pace you were when today began. */
    val missedBefore: Long,
    /** False when today and the days after it carry no work, so nothing more can be planned. */
    val hasWorkLeft: Boolean,
    val laterDays: Int,
    val laterPerDay: Long,
    /** Even-pace hours due by the end of today, and what was done before today began. */
    val dueThroughToday: Long = 0,
    val doneBeforeToday: Long = 0,
)

enum class PaceState { MET, MISSED, PENDING }

/** The on-pace line: its text and state, shared by the app and the widget. */
data class PaceStatus(val message: String, val state: PaceState)

/**
 * [weekLeft] is the target minus everything tracked this week, [doneToday] what was tracked today.
 * Under a minute either way counts as on pace, since the text only shows whole minutes.
 */
fun PacePlan.status(
    context: Context,
    weekLeft: Long,
    doneToday: Long,
): PaceStatus {
    val todayLeft = todayGoal - doneToday
    // Behind the even schedule, even if the catch-up plan for today is met. Spreading missed hours over
    // later days must not turn this into "On pace".
    val behind = dueThroughToday - doneBeforeToday - doneToday
    val stillBehind = weekLeft >= 60 && missedBefore >= 60 && behind >= 60
    val message =
        when {
            weekLeft < 60 -> context.getString(R.string.pace_target_reached)
            !hasWorkLeft -> context.getString(R.string.pace_short, Fmt.hoursMinutes(weekLeft))
            stillBehind -> context.getString(R.string.pace_behind, Fmt.hoursMinutes(behind))
            todayLeft >= 60 -> context.getString(R.string.pace_more_today, Fmt.hoursMinutes(todayLeft))
            todayLeft <= -60 -> context.getString(R.string.pace_ahead, Fmt.hoursMinutes(-todayLeft))
            else -> context.getString(R.string.pace_on)
        }
    // Green once today's goal is met, neutral while today's work is still to do, and red when earlier days
    // were missed and today's catch-up isn't done (or no days are left).
    val met = weekLeft < 60 || todayLeft < 60
    val missed = weekLeft >= 60 && (!hasWorkLeft || stillBehind || (missedBefore >= 60 && !met))
    return PaceStatus(
        message,
        when {
            missed -> PaceState.MISSED
            met -> PaceState.MET
            else -> PaceState.PENDING
        },
    )
}

/** Seconds tracked in finished entries this week, and the contracted target (0 = none). */
data class WeekTotals(
    val doneSeconds: Long,
    val targetSeconds: Long,
    /** Share of the target due on each of the week's 7 days, in week order; empty when not the current week. */
    val paceWeights: List<Long> = emptyList(),
    /** Time zone the week was computed in; blank means the device's zone. */
    val zoneId: String = "",
) {
    val zone: ZoneId
        get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

    /**
     * Today's share of what is still left, or null when there is no pace to show. Hours missed on earlier
     * days are spread over today and the work days after it, by their weights, instead of landing on today.
     * [doneBeforeToday] is everything tracked this week before today started. Worked out when drawn,
     * so it stays right after midnight without a refresh.
     */
    fun pacePlan(
        start: LocalDate,
        today: LocalDate,
        doneBeforeToday: Long,
        doneToday: Long = 0,
    ): PacePlan? {
        val sum = paceWeights.sum()
        if (paceWeights.size != 7 || sum <= 0 || targetSeconds <= 0) return null
        val idx = ChronoUnit.DAYS.between(start, today).toInt()
        if (idx !in 0..6) return null
        val remaining = (targetSeconds - doneBeforeToday).coerceAtLeast(0)
        val workLeft = paceWeights.drop(idx).sum()
        val dueBefore = targetSeconds * paceWeights.take(idx).sum() / sum
        val dueThrough = targetSeconds * paceWeights.take(idx + 1).sum() / sum
        val todayGoal = if (workLeft > 0) remaining * paceWeights[idx] / workLeft else 0L
        val laterDays = paceWeights.drop(idx + 1).count { it > 0 }
        return PacePlan(
            todayGoal = todayGoal,
            missedBefore = (dueBefore - doneBeforeToday).coerceAtLeast(0),
            hasWorkLeft = workLeft > 0,
            dueThroughToday = dueThrough,
            doneBeforeToday = doneBeforeToday,
            laterDays = laterDays,
            laterPerDay =
                if (laterDays > 0) (remaining - maxOf(todayGoal, doneToday)).coerceAtLeast(0) / laterDays else 0L,
        )
    }

    /**
     * The goal for each of the week's 7 days, or null when there is no pace to show. Days before [today]
     * keep their even share of the target. Today and the days after share what is still left, by weight,
     * which is the same plan [pacePlan] gives for today.
     */
    fun dayGoals(
        start: LocalDate,
        today: LocalDate,
        doneBeforeToday: Long,
        doneToday: Long = 0,
    ): List<Long>? {
        val sum = paceWeights.sum()
        if (paceWeights.size != 7 || sum <= 0 || targetSeconds <= 0) return null
        val idx = ChronoUnit.DAYS.between(start, today).toInt()
        if (idx !in 0..6) return null
        val remaining = (targetSeconds - doneBeforeToday).coerceAtLeast(0)
        val workLeft = paceWeights.drop(idx).sum()
        val todayGoal = if (workLeft > 0) remaining * paceWeights[idx] / workLeft else 0L
        val laterWeight = workLeft - paceWeights[idx]
        // Hours worked today beyond today's goal come off the later days.
        val laterLeft = (remaining - maxOf(todayGoal, doneToday)).coerceAtLeast(0)
        return paceWeights.mapIndexed { i, w ->
            when {
                i < idx -> targetSeconds * w / sum
                i == idx -> todayGoal
                laterWeight > 0 -> laterLeft * w / laterWeight
                else -> 0L
            }
        }
    }

    /** Adds the running timer's elapsed time, which grows between syncs. */
    fun total(
        timer: TimerState,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = doneSeconds + if (timer.running) ((nowMillis - timer.beginMillis) / 1000).coerceAtLeast(0) else 0L
}

/** One person's finished seconds in a week, for the team overview. */
data class TeamRow(val user: User, val doneSeconds: Long, val running: Boolean)
