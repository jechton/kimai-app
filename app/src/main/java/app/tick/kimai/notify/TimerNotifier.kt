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

object TimerNotifier {
    const val ACTION_STOP = "app.tick.kimai.action.STOP"
    const val ACTION_DISMISSED = "app.tick.kimai.action.DISMISSED"
    private const val CHANNEL = "timer"

    internal const val ID = 1
    private const val TO_DO = 0x55808080 // faded grey for time not yet worked

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

        val elapsed = Fmt.hm((System.currentTimeMillis() - s.beginMillis) / 1000)
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
            // re-posts every minute. A system chronometer jams against the title on One UI.
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
     * The progress bar: one piece per day, sized by its goal. Past days are bright when met, today
     * fills live, and a day off with work gets a piece sized by its hours. Indeterminate without a target.
     */
    private fun progressStyle(
        context: Context,
        s: TimerState,
    ): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
        val prefs = Prefs(context)
        val totals = prefs.weekTotals()
        val detail = prefs.weekDetail()
        val week = totals?.let { t -> detail?.let { t.progress(it.start, it.perDay, s) } }
        val goals = week?.goals ?: return style.setProgressIndeterminate(true)
        // One piece per day. A bar can only fill in order, so the bar is always full and "not done"
        // is a faded colour. Past days are all or nothing; only today shows its progress live.
        var max = 0
        goals.forEachIndexed { i, goal ->
            val done = week.done[i]
            val pieces =
                when {
                    goal == 0L -> listOf(done to true)
                    i == week.idx -> listOf(minOf(done, goal) to true, (goal - done).coerceAtLeast(0) to false)
                    else -> listOf(goal to (i < week.idx && done >= goal - 59))
                }
            pieces.filter { it.first > 0 }.forEach { (length, met) ->
                val segment = NotificationCompat.ProgressStyle.Segment(length.toInt())
                style.addProgressSegment(if (met) segment else segment.setColor(TO_DO))
                max += length.toInt()
            }
        }
        if (max == 0) return style.setProgressIndeterminate(true)
        style.setProgress(max)
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
