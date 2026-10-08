package app.tick.kimai.data

import app.tick.kimai.util.Fmt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/** Kimai returns project/activity as a bare id or as an object, depending on the endpoint. */
@Serializable
data class Ref(val id: Int, val name: String? = null)

object RefSerializer : JsonTransformingSerializer<Ref>(Ref.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive) JsonObject(mapOf("id" to element)) else element
}

@Serializable
data class Entry(
    val id: Int,
    val begin: String,
    val end: String? = null,
    val duration: Long? = null,
    val description: String? = null,
    @Serializable(with = RefSerializer::class) val project: Ref,
    @Serializable(with = RefSerializer::class) val activity: Ref,
) {
    val isRunning: Boolean get() = end == null
    val beginMillis: Long get() = Fmt.parseMillis(begin)
    val endMillis: Long? get() = end?.let { Fmt.parseMillis(it) }
    val seconds: Long
        get() = duration?.takeIf { it > 0 } ?: (((endMillis ?: beginMillis) - beginMillis) / 1000)

    /** Activity name, or null when missing or identical to the project name. */
    val activityLabel: String?
        get() = activity.name?.takeIf { it != project.name }
    val label: String
        get() = listOfNotNull(project.name, activityLabel).joinToString(" · ")
}

@Serializable
data class Project(
    val id: Int,
    val name: String,
    val parentTitle: String? = null,
) {
    val display: String
        get() = if (parentTitle.isNullOrBlank()) name else "$name · $parentTitle"
}

@Serializable
data class Activity(
    val id: Int,
    val name: String,
    val parentTitle: String? = null,
)

@Serializable
data class Me(
    val id: Int = 0,
    val username: String = "",
    val alias: String? = null,
    val timezone: String? = null,
    val preferences: List<Pref> = emptyList(),
) {
    /** Kimai's per-user "first day of the week" preference, e.g. "monday" or "sunday". */
    val firstWeekday: String?
        get() =
            preferences.firstOrNull { it.name == "first_weekday" }
                ?.value?.let { (it as? JsonPrimitive)?.content }
}

@Serializable
data class Pref(val name: String, val value: JsonElement? = null)

/** Payload of Kimai's "Create API access" QR code, e.g. {"type":"kimai","version":1,"url":"...","token":"..."}. */
@Serializable
data class KimaiQr(
    val type: String = "",
    val url: String = "",
    val token: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): KimaiQr? =
            runCatching { json.decodeFromString(serializer(), raw) }
                .getOrNull()
                ?.takeIf { it.type == "kimai" && it.url.isNotBlank() && it.token.isNotBlank() }
    }
}

/** Cached view of the running timer, shared by the app, notification, widget and tile. */
data class TimerState(
    val running: Boolean = false,
    val id: Int = 0,
    val project: String = "",
    val activity: String = "",
    val description: String = "",
    val beginMillis: Long = 0L,
    val lastId: Int = 0,
    val lastLabel: String = "",
)
