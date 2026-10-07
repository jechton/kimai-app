package app.tick.kimai.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
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

    fun beginNow(zone: ZoneId): String = ZonedDateTime.now(zone).format(BEGIN)

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

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> d.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        }
    }
}
