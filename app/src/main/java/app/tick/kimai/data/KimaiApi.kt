package app.tick.kimai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class KimaiApi(
    baseUrl: String,
    private val token: String,
    private val legacyUser: String = "",
) {
    class ApiException(val code: Int, message: String) : IOException(message)

    private val base = baseUrl.trimEnd('/') + "/api/"

    private suspend fun request(method: String, path: String, body: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val payload = when {
                body != null -> body.toString().toRequestBody(JSON)
                method == "GET" || method == "DELETE" -> null
                else -> "{}".toRequestBody(JSON)
            }
            val builder = Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .method(method, payload)
            if (legacyUser.isNotBlank()) {
                builder.header("X-AUTH-USER", legacyUser).header("X-AUTH-TOKEN", token)
            } else {
                builder.header("Authorization", "Bearer $token")
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException(resp.code, errorMessage(resp.code, text))
                text
            }
        }

    suspend fun me(): Me =
        json.decodeFromString(Me.serializer(), request("GET", "users/me"))

    /** Newest first. Running entries have end == null. */
    suspend fun recent(size: Int): List<Entry> {
        val text = request("GET", "timesheets?full=true&order=DESC&orderBy=begin&size=$size")
        return json.decodeFromString(ListSerializer(Entry.serializer()), text)
    }

    suspend fun projects(): List<Project> {
        val text = request("GET", "projects?size=500&order=ASC&orderBy=name")
        return json.decodeFromString(ListSerializer(Project.serializer()), text)
    }

    suspend fun activities(projectId: Int): List<Activity> {
        val text = request("GET", "activities?project=$projectId&size=500&order=ASC&orderBy=name")
        return json.decodeFromString(ListSerializer(Activity.serializer()), text)
    }

    suspend fun start(projectId: Int, activityId: Int, description: String, begin: String) {
        val body = buildJsonObject {
            put("project", projectId)
            put("activity", activityId)
            put("begin", begin)
            if (description.isNotBlank()) put("description", description)
        }
        request("POST", "timesheets", body)
    }

    suspend fun stop(id: Int) {
        request("PATCH", "timesheets/$id/stop")
    }

    suspend fun restart(id: Int) {
        request("PATCH", "timesheets/$id/restart")
    }

    suspend fun delete(id: Int) {
        request("DELETE", "timesheets/$id")
    }

    /** begin and end are local times in the user's Kimai timezone, e.g. 2026-10-07T09:05:00. */
    suspend fun updateEntry(id: Int, description: String, begin: String, end: String) {
        val body = buildJsonObject {
            put("description", description)
            put("begin", begin)
            put("end", end)
        }
        request("PATCH", "timesheets/$id", body)
    }

    private fun errorMessage(code: Int, text: String): String {
        if (code == 401 || code == 403) return "Server rejected the token (HTTP $code)"
        val msg = runCatching {
            json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Server error (HTTP $code)"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val client: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
