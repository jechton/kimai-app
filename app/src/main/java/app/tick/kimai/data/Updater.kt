package app.tick.kimai.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    @SerialName("html_url") val page: String,
    val assets: List<GhAsset> = emptyList(),
)

/** Looks for a newer GitHub Release and helps install it. Only talks to api.github.com and the release asset. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/jechton/kimai-app/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }
    private val client =
        OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .build()

    /** The newer release, or null when this build is current. Throws on network errors. */
    suspend fun check(current: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val req =
                Request.Builder()
                    .url(LATEST)
                    .header("Accept", "application/vnd.github+json")
                    .build()
            val release =
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("Update check failed (HTTP ${r.code})")
                    json.decodeFromString<GhRelease>(r.body!!.string())
                }
            val version = release.tag.removePrefix("v")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apk == null || !isNewer(version, current)) {
                null
            } else {
                UpdateInfo(version, release.body.orEmpty().trim(), apk.url, release.page)
            }
        }

    /** Compares dotted numbers such as 0.10.2 and 0.9.0. Anything unparsable counts as not newer. */
    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: return false }
        val b = current.split(".").map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK into the app cache and returns it. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
    ): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "tick-${info.version}.apk")
            client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
                r.body!!.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            file
        }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Opens the settings page where the user lets Tick install apps. */
    fun installPermissionIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the APK to Android's installer, which asks the user to confirm. */
    fun installIntent(
        context: Context,
        file: File,
    ): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
