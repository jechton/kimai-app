package app.tick.kimai.notify

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.tick.kimai.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hosts the running-timer notification as a foreground service so Android 14+
 * does not let it be swiped away. While the timer runs it re-posts the
 * notification every second, because the Live Update chip text is static and
 * [TimerNotifier.update] stops the service when the timer ends.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

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
        ticker?.cancel()
        if (!state.running) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        ticker =
            scope.launch {
                while (isActive) {
                    delay(1000 - System.currentTimeMillis() % 1000)
                    val s = Prefs(this@TimerService).timerState()
                    if (s.running) nm.notify(TimerNotifier.ID, TimerNotifier.build(this@TimerService, s))
                }
            }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
    }
}
