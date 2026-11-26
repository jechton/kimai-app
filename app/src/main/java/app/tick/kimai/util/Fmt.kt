package app.tick.kimai.util

import android.content.Context
import android.text.format.DateFormat
import app.tick.kimai.R
import app.tick.kimai.data.PaceMessage
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TimeMode {
    const val SYSTEM = 0
    const val H12 = 1
    const val H24 = 2
}

object Fmt {
    // Kimai returns e.g. 2026-10-07T13:00:00-0500
    private val API = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    // Kimai expects begin as local time in the user's timezone, no offset
    private val BEGIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun parseMillis(s: String): Long {
        val odt =
            try {
                OffsetDateTime.parse(s, API)
            } catch (e: DateTimeParseException) {
                OffsetDateTime.parse(s)
            }
        return odt.toInstant().toEpochMilli()
    }

    /** In-app override if set, else the system 12/24h setting. */
    fun is24(
        context: Context,
        mode: Int,
    ): Boolean =
        when (mode) {
            TimeMode.H12 -> false
            TimeMode.H24 -> true
            else -> DateFormat.is24HourFormat(context)
        }

    /** Format an instant as Kimai expects for writes: local time in the given zone, no offset. */
    fun apiLocal(
        millis: Long,
        zone: ZoneId,
    ): String = Instant.ofEpochMilli(millis).atZone(zone).format(BEGIN)

    fun clock(
        context: Context,
        millis: Long,
        mode: Int,
    ): String {
        val f = DateTimeFormatter.ofPattern(if (is24(context, mode)) "HH:mm" else "h:mm a", Locale.getDefault())
        return f.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    fun elapsed(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Hours and minutes, e.g. 1:05, for places where ticking seconds are noise. */
    fun hm(seconds: Long): String {
        val m = seconds.coerceAtLeast(0) / 60
        return "%d:%02d".format(m / 60, m % 60)
    }

    /** For narrow columns: no space, and no minutes on a whole hour, e.g. 8h or 7h45m. */
    fun narrow(seconds: Long): String {
        val m = seconds / 60
        return if (m % 60 == 0L) "${m / 60}h" else "${m / 60}h%02dm".format(m % 60)
    }

    fun paceText(
        context: Context,
        message: PaceMessage,
    ): String =
        when (message) {
            PaceMessage.TargetReached -> context.getString(R.string.pace_target_reached)
            is PaceMessage.Short -> context.getString(R.string.pace_short, hoursMinutes(message.seconds))
            is PaceMessage.Behind -> context.getString(R.string.pace_behind, hoursMinutes(message.seconds))
            is PaceMessage.MoreToday -> context.getString(R.string.pace_more_today, hoursMinutes(message.seconds))
            is PaceMessage.Ahead -> context.getString(R.string.pace_ahead, hoursMinutes(message.seconds))
            PaceMessage.OnPace -> context.getString(R.string.pace_on)
        }

    /** Compact hours for day headers and the widget, e.g. 7h 45m. */
    fun hoursMinutes(seconds: Long): String {
        val m = seconds.coerceAtLeast(0) / 60
        return "%dh %02dm".format(m / 60, m % 60)
    }

    /** The zone named by [id], or the device's when it is blank or unknown. */
    fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())

    fun localDate(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun dayLabel(
        context: Context,
        d: LocalDate,
    ): String {
        val today = LocalDate.now()
        return when (d) {
            today -> context.getString(R.string.today)
            today.minusDays(1) -> context.getString(R.string.yesterday)
            else ->
                d.format(
                    DateTimeFormatter.ofPattern(
                        DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMd"),
                        Locale.getDefault(),
                    ),
                )
        }
    }
}
