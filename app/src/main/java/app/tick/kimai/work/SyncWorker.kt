package app.tick.kimai.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tick.kimai.NotSignedIn
import app.tick.kimai.TimerRepository

/** Picks up timers started or stopped elsewhere (web UI, another phone) so the notification stays right. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = TimerRepository(applicationContext).sync(size = 10)
        return if (r.isSuccess || r.exceptionOrNull() is NotSignedIn) Result.success() else Result.retry()
    }
}
