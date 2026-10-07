package app.tick.kimai.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tick.kimai.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerNotifier.ACTION_STOP) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TimerRepository(appContext).stop()
            } finally {
                pending.finish()
            }
        }
    }
}
