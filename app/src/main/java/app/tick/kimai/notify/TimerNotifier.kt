package app.tick.kimai.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.tick.kimai.MainActivity
import app.tick.kimai.R
import app.tick.kimai.data.TimerState

object TimerNotifier {
    const val ACTION_STOP = "app.tick.kimai.action.STOP"
    private const val CHANNEL = "timer"
    private const val ID = 1

    /**
     * Shows an ongoing notification with a live chronometer while a timer runs,
     * and removes it when none does. The system draws the ticking clock itself,
     * so no service has to stay alive.
     */
    fun update(
        context: Context,
        s: TimerState,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!s.running) {
            nm.cancel(ID)
            return
        }

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

        val n =
            NotificationCompat.Builder(context, CHANNEL)
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

        nm.notify(ID, n)
    }
}
