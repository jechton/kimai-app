package app.tick.kimai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A change made while the server was unreachable, replayed in order once it is back.
 * Times are local times in the user's Kimai timezone, captured when the user acted.
 */
@Serializable
sealed class PendingAction {
    @Serializable
    @SerialName("start")
    data class Start(
        val projectId: Int,
        val activityId: Int,
        val description: String,
        val begin: String,
    ) : PendingAction()

    @Serializable
    @SerialName("create")
    data class Create(
        val projectId: Int,
        val activityId: Int,
        val description: String,
        val begin: String,
        val end: String,
    ) : PendingAction()

    /** Ends whatever timer is running when replayed, since a timer started offline has no id yet. */
    @Serializable
    @SerialName("stop")
    data class Stop(val end: String) : PendingAction()

    @Serializable
    @SerialName("restart")
    data class Restart(val entryId: Int, val begin: String) : PendingAction()

    @Serializable
    @SerialName("update")
    data class Update(
        val entryId: Int,
        val description: String,
        val begin: String,
        val end: String?,
    ) : PendingAction()

    @Serializable
    @SerialName("delete")
    data class Delete(val entryId: Int) : PendingAction()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(serializer())

        fun decodeList(raw: String): List<PendingAction> =
            runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())

        fun encodeList(list: List<PendingAction>): String = json.encodeToString(listSerializer, list)
    }
}
