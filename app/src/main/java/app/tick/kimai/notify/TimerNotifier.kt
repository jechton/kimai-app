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
import app.tick.kimai.data.TimerState

object TimerNotifier {
    const val ACTION_STOP = "app.tick.kimai.action.STOP"
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
            NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
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

        val detail = listOf(s.activity, s.description).filter { it.isNotBlank() }.joinToString(" · ")

        return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(s.project.ifBlank { "Timer running" })
                .setContentText(detail)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(s.beginMillis)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(0, "Stop", stop)
                .build()
    }
}
