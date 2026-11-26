package app.tick.kimai.watch

import app.tick.kimai.NotSignedIn
import app.tick.kimai.TimerRepository
import app.tick.kimai.data.KimaiApi
import app.tick.kimai.data.Prefs
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/** Answers the watch: timer actions and the project and activity lists. A reply with "error" means it failed. */
class WearService : WearableListenerService() {
    // Not cancelled in onDestroy: the watch waits for the reply even if the service is unbound meanwhile.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray> {
        val reply = TaskCompletionSource<ByteArray>()
        scope.launch {
            val result =
                try {
                    handle(path, DataMap.fromByteArray(request))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DataMap().apply {
                        putString(
                            "error",
                            if (e is NotSignedIn) WearSync.SIGNED_OUT else e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
            reply.setResult(result.toByteArray())
        }
        return reply.task
    }

    private suspend fun handle(
        path: String,
        data: DataMap,
    ): DataMap {
        val repo = TimerRepository(this)
        val prefs = Prefs(this)
        when (path) {
            WearSync.SYNC -> repo.sync().getOrThrow()
            WearSync.TOGGLE -> repo.toggle().getOrThrow()
            WearSync.START -> {
                val projectId = data.getInt("project_id")
                val activityId = data.getInt("activity_id")
                prefs.lastProjectId = projectId
                prefs.lastActivityId = activityId
                repo.start(projectId, activityId, "", data.getString("project", ""), data.getString("activity", ""))
                    .getOrThrow()
            }
            WearSync.PROJECTS -> {
                val projects = fetchOrCache(repo, { it.projects().also(prefs::saveProjects) }, prefs.projects())
                return items(projects.map { it.id to it.display }, prefs.lastProjectId)
            }
            WearSync.ACTIVITIES -> {
                val id = data.getInt("project_id")
                val activities =
                    fetchOrCache(repo, {
                        it.activities(id).also {
                                a ->
                            prefs.saveActivities(id, a)
                        }
                    }, prefs.activities(id))
                return items(activities.map { it.id to it.name }, prefs.lastActivityId)
            }
            else -> error("Unknown path $path")
        }
        return DataMap()
    }

    /** Fetch from Kimai, or use [cached] when offline, like the app's pickers. */
    private suspend fun <T> fetchOrCache(
        repo: TimerRepository,
        fetch: suspend (KimaiApi) -> List<T>,
        cached: List<T>,
    ): List<T> {
        val api = repo.apiOrNull() ?: throw NotSignedIn()
        return try {
            fetch(api)
        } catch (e: IOException) {
            cached.ifEmpty { throw e }
        }
    }

    /** id/name pairs for the watch's pickers, the last used one first. */
    private fun items(
        list: List<Pair<Int, String>>,
        lastId: Int,
    ) = DataMap().apply {
        putDataMapArrayList(
            "items",
            ArrayList(
                list.sortedByDescending { it.first == lastId }.map { (id, name) ->
                    DataMap().apply {
                        putInt("id", id)
                        putString("name", name)
                    }
                },
            ),
        )
    }
}
