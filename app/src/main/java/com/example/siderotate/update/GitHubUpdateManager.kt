package com.example.siderotate.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

data class UpdateInfo(
    val hasUpdate: Boolean,
    val tagName: String? = null,
    val cleanVersion: String? = null,
    val releaseTitle: String? = null,
    val changelogBody: String? = null,
    val apkDownloadUrl: String? = null,
    val apkFileName: String? = null,
    val apkSize: Long = 0,
    val publishedAt: String? = null
)

interface UpdateCheckCallback {
    fun onResult(info: UpdateInfo)
    fun onError(message: String)
}

interface DownloadCallback {
    fun onProgress(percent: Int, downloadedBytes: Long, totalBytes: Long)
    fun onComplete(apkFile: File)
    fun onError(message: String)
}

class GitHubUpdateManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.1"
        } catch (e: Exception) {
            "1.0.1"
        }
    }

    /**
     * Checks GitHub releases for SideRotate updates.
     * @param isManual true if triggered manually by user tap; false if automatic (throttled).
     */
    fun checkForUpdates(isManual: Boolean, callback: UpdateCheckCallback) {
        if (!isManual) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
            val now = System.currentTimeMillis()
            // Throttle background auto-checks to once every 4 hours
            if (now - lastCheck < 4 * 60 * 60 * 1000) {
                mainHandler.post { callback.onResult(UpdateInfo(hasUpdate = false)) }
                return
            }
        }

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(GITHUB_RELEASES_API)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "SideRotate-App")
                    connectTimeout = 12000
                    readTimeout = 15000
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    mainHandler.post { callback.onResult(UpdateInfo(hasUpdate = false)) }
                    return@execute
                }

                if (responseCode !in 200..299) {
                    throw Exception("HTTP $responseCode: ${conn.responseMessage}")
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.use { it.readText() }
                val releaseObj = JSONObject(jsonStr)

                val tagName = releaseObj.optString("tag_name", "")
                val releaseTitle = releaseObj.optString("name", tagName)
                val body = releaseObj.optString("body", "")
                val publishedAt = releaseObj.optString("published_at", "")

                val cleanRemoteVer = extractCleanVersion(tagName)
                val currentVer = getCurrentVersionName()

                var apkDownloadUrl: String? = null
                var apkFileName: String? = null
                var apkSize: Long = 0

                val assets = releaseObj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            apkFileName = name
                            apkSize = asset.optLong("size", 0)
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(cleanRemoteVer, currentVer) && !apkDownloadUrl.isNullOrEmpty()

                // Save last check timestamp
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                val info = UpdateInfo(
                    hasUpdate = hasUpdate,
                    tagName = tagName,
                    cleanVersion = cleanRemoteVer,
                    releaseTitle = releaseTitle,
                    changelogBody = body,
                    apkDownloadUrl = apkDownloadUrl,
                    apkFileName = apkFileName,
                    apkSize = apkSize,
                    publishedAt = publishedAt
                )

                mainHandler.post { callback.onResult(info) }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                mainHandler.post { callback.onError(e.message ?: "Failed to check for updates") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    /**
     * Downloads the APK file from GitHub Release asset URL with redirect handling.
     */
    fun downloadApk(apkUrl: String, apkFileName: String?, callback: DownloadCallback) {
        executor.execute {
            var conn: HttpURLConnection? = null
            var inputStream: InputStream? = null
            var fos: FileOutputStream? = null

            try {
                var currentUrl = URL(apkUrl)
                var redirects = 0
                var connected = false

                // Follow redirects (GitHub releases redirect to S3 storage)
                while (redirects < 7) {
                    conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "SideRotate-App")
                        setRequestProperty("Accept", "*/*")
                        connectTimeout = 15000
                        readTimeout = 30000
                        connect()
                    }

                    val status = conn.responseCode
                    if (status in listOf(
                            HttpURLConnection.HTTP_MOVED_TEMP,
                            HttpURLConnection.HTTP_MOVED_PERM,
                            HttpURLConnection.HTTP_SEE_OTHER,
                            307, 308
                        )
                    ) {
                        val newLocation = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!newLocation.isNullOrEmpty()) {
                            currentUrl = URL(currentUrl, newLocation)
                            redirects++
                            continue
                        }
                    }

                    if (status in 200..299) {
                        connected = true
                        break
                    } else {
                        throw Exception("HTTP $status: ${conn.responseMessage}")
                    }
                }

                if (!connected || conn == null) {
                    throw Exception("Failed to connect after redirects")
                }

                val totalBytes = conn.contentLengthLong
                inputStream = conn.inputStream

                cleanOldApks(context)
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val outputFile = File(cacheDir, apkFileName ?: "siderotate_update.apk")
                fos = FileOutputStream(outputFile)

                val buffer = ByteArray(65536) // 64 KB buffer
                var downloadedBytes = 0L
                var read: Int
                var lastProgressUpdate = 0L

                while (inputStream.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100 || downloadedBytes == totalBytes) {
                        lastProgressUpdate = now
                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
                        val cur = downloadedBytes
                        mainHandler.post { callback.onProgress(percent, cur, totalBytes) }
                    }
                }

                fos.flush()
                fos.close()
                fos = null

                mainHandler.post { callback.onComplete(outputFile) }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update APK", e)
                mainHandler.post { callback.onError(e.message ?: "Failed to download update") }
            } finally {
                try {
                    inputStream?.close()
                    fos?.close()
                    conn?.disconnect()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK.
     */
    fun installApk(activity: Activity, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val allowIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(allowIntent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            false
        }
    }

    companion object {
        private const val TAG = "GitHubUpdateManager"
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/code4nigel/SideRotate/releases/latest"
        private const val PREF_NAME = "siderotate_update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_update_check_time"

        fun extractCleanVersion(tag: String?): String {
            if (tag.isNullOrBlank()) return "0.0.0"
            val pattern = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+)?)")
            val matcher = pattern.matcher(tag)
            return if (matcher.find()) {
                matcher.group(1) ?: tag.replace("v", "").trim()
            } else {
                tag.replace("v", "").trim()
            }
        }

        fun isNewerVersion(remoteVersion: String?, localVersion: String?): Boolean {
            if (remoteVersion == null || localVersion == null) return false
            return try {
                val cleanRemote = extractNumericSemver(remoteVersion)
                val cleanLocal = extractNumericSemver(localVersion)

                val rParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
                val lParts = cleanLocal.split(".").map { it.toIntOrNull() ?: 0 }

                val maxLen = maxOf(rParts.size, lParts.size)
                for (i in 0 until maxLen) {
                    val rNum = rParts.getOrElse(i) { 0 }
                    val lNum = lParts.getOrElse(i) { 0 }
                    if (rNum > lNum) return true
                    if (rNum < lNum) return false
                }
                false
            } catch (e: Exception) {
                !remoteVersion.trim().equals(localVersion.trim(), ignoreCase = true)
            }
        }

        private fun extractNumericSemver(version: String): String {
            val p = Pattern.compile("(\\d+(\\.\\d+)+)")
            val m = p.matcher(version)
            return if (m.find()) m.group(1) ?: version else version.replace("[^0-9.]".toRegex(), "").trim()
        }

        fun cleanOldApks(context: Context) {
            try {
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val files = cacheDir.listFiles { _, name -> name?.endsWith(".apk", ignoreCase = true) == true }
                files?.forEach { file ->
                    runCatching { file.delete() }
                }
            } catch (ignored: Exception) {}
        }
    }
}
