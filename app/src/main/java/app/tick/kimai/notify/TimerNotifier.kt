package app.tick.kimai.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.tick.kimai.MainActivity
import app.tick.kimai.R
import app.tick.kimai.data.Prefs
import app.tick.kimai.data.TimerState
import app.tick.kimai.util.Fmt
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TimerNotifier {
    const val ACTION_STOP = "app.tick.kimai.action.STOP"
    const val ACTION_DISMISSED = "app.tick.kimai.action.DISMISSED"
    private const val CHANNEL = "timer"

    internal const val ID = 1

    /**
     * Shows an ongoing notification with a live chronometer while a timer runs,
     * and removes it when none does. While running it is hosted by a foreground
     * service so it cannot be swiped away on Android 14+. If the system refuses
     * to start the service (app in background), it falls back to a plain
     * notification, which the next sync or app open promotes again.
     */
    fun update(
        context: Context,
        s: TimerState,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val service = Intent(context, TimerService::class.java)
        if (!s.running) {
            context.stopService(service)
            nm.cancel(ID)
            return
        }

        val n = build(context, s)
        try {
            ContextCompat.startForegroundService(context, service)
        } catch (e: Exception) {
            nm.notify(ID, n)
        }
    }

    internal fun build(
        context: Context,
        s: TimerState,
    ): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
        )

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                flags,
            )
        val stop =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, TimerActionReceiver::class.java).setAction(ACTION_STOP),
                flags,
            )

        val dismissed =
            PendingIntent.getBroadcast(
                context,
                2,
                Intent(context, TimerActionReceiver::class.java).setAction(ACTION_DISMISSED),
                flags,
            )

        val elapsed = Fmt.elapsed(((System.currentTimeMillis() - s.beginMillis) / 1000).coerceAtLeast(0))
        val detail =
            listOfNotNull(paceLine(context, s), s.activity, s.description)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { context.getString(R.string.timer_running) }

        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${s.project.ifBlank { context.getString(R.string.timer_running) }} · $elapsed")
            .setContentText(detail)
            .setShowWhen(false)
            .setWhen(s.beginMillis)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Live Update (Android 16, Samsung Now Bar). The chip shows this text; TimerService
            // re-posts every second. A system chronometer jams against the title on One UI.
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(elapsed)
            .setStyle(progressStyle(context, s))
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()
    }

    /**
     * The progress bar: one segment per work day of the week's target, filled by the week's total
     * (running timer included), with a marker where you should be by the end of today. Without a
     * target or a current-week pace it is an indeterminate bar.
     */
    private fun progressStyle(
        context: Context,
        s: TimerState,
    ): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
        val prefs = Prefs(context)
        val totals = prefs.weekTotals()
        val weights = totals?.paceWeights.orEmpty()
        val sum = weights.sum()
        if (totals == null || totals.targetSeconds <= 0 || weights.size != 7 || sum <= 0) {
            return style.setProgressIndeterminate(true)
        }
        val segments = weights.filter { it > 0 }.map { (totals.targetSeconds * it / sum).toInt().coerceAtLeast(1) }
        val max = segments.sum()
        segments.forEach { style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(it)) }
        style.setProgress(totals.total(s).coerceIn(0, max.toLong()).toInt())
        val idx = prefs.weekDetail()?.let { ChronoUnit.DAYS.between(it.start, LocalDate.now(totals.zone)).toInt() }
        if (idx != null && idx in 0..6) {
            val due = (totals.targetSeconds * weights.take(idx + 1).sum() / sum).toInt()
            if (due in 1 until max) style.addProgressPoint(NotificationCompat.ProgressStyle.Point(due))
        }
        return style.setProgressIndeterminate(false)
    }

    /** The on-pace line from the last sync plus the running timer, or null without a week target. */
    private fun paceLine(
        context: Context,
        s: TimerState,
    ): String? {
        val prefs = Prefs(context)
        val totals = prefs.weekTotals() ?: return null
        val detail = prefs.weekDetail() ?: return null
        val pace = totals.progress(detail.start, detail.perDay, s)?.pace ?: return null
        return Fmt.paceText(context, pace.message)
    }
}
