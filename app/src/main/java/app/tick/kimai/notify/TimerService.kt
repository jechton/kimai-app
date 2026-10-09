package app.tick.kimai.notify

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.tick.kimai.data.Prefs

/**
 * Hosts the running-timer notification as a foreground service so Android 14+
 * does not let it be swiped away. It does no work of its own: the chronometer
 * is drawn by the system, and [TimerNotifier.update] stops the service when
 * the timer ends.
 */
class TimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val state = Prefs(this).timerState()
        ServiceCompat.startForeground(
            this,
            TimerNotifier.ID,
            TimerNotifier.build(this, state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!state.running) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
