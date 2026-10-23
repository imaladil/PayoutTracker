package com.example.payouttracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** The GitHub repository whose latest Release carries the newest APK. */
const val UPDATE_REPO = "imaladil/PayoutTracker"

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val apkUrl: String,
    val sizeBytes: Long
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val info: ReleaseInfo) : UpdateState()
    data class Downloading(val info: ReleaseInfo, val done: Long, val total: Long) : UpdateState()
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

private class DownloadCancelled : Exception()

/**
 * Checks GitHub for a newer release, downloads its APK and hands it to Android's installer.
 * Android always asks the user to confirm the installation, so it can never happen silently.
 */
class UpdateController(private val ctx: Context, private val scope: CoroutineScope) {

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Version name of a newer release found by the quiet check at app start, or null. */
    var newVersion by mutableStateOf<String?>(null)
        private set

    private var cancelled = false

    val currentVersion: String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Called when the update dialog opens: keep a running download, otherwise look for a new release. */
    fun onOpen() {
        when (state) {
            is UpdateState.Downloading, is UpdateState.Ready -> Unit
            else -> check(silent = false)
        }
    }

    /** [silent] checks only record [newVersion] and never change the dialog state. */
    fun check(silent: Boolean) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        if (!silent) state = UpdateState.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchLatest() }
            result.onSuccess { info ->
                if (isNewer(info.version, currentVersion)) {
                    newVersion = info.version
                    if (!silent) state = UpdateState.Available(info)
                } else {
                    newVersion = null
                    if (!silent) state = UpdateState.UpToDate
                }
            }.onFailure { e ->
                if (!silent) {
                    val msg = if (e is IOException) {
                        "Could not reach GitHub. Check your internet connection and try again."
                    } else {
                        e.message ?: "Could not check for updates."
                    }
                    state = UpdateState.Failed(msg)
                }
            }
        }
    }

    fun download(info: ReleaseInfo) {
        if (state is UpdateState.Downloading) return
        cancelled = false
        state = UpdateState.Downloading(info, 0L, info.sizeBytes)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    downloadApk(info) { done, total ->
                        state = UpdateState.Downloading(info, done, total)
                    }
                }
            }
            result.onSuccess { file ->
                state = UpdateState.Ready(info, file)
            }.onFailure { e ->
                state = if (e is DownloadCancelled) {
                    UpdateState.Idle
                } else if (e is IOException) {
                    UpdateState.Failed("The download was interrupted. Check your internet connection and try again.")
                } else {
                    UpdateState.Failed(e.message ?: "The download failed.")
                }
            }
        }
    }

    fun cancelDownload() {
        cancelled = true
    }

    /**
     * Opens Android's installer for the downloaded APK. Returns false when the user first has to
     * allow this app to install apps; the settings screen for that is opened instead.
     */
    fun install(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    // ---- Network ----

    private fun fetchLatest(): Result<ReleaseInfo> = runCatching {
        val conn = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val code = conn.responseCode
            if (code == 404) {
                throw Exception("No release was found. Publish a release first, and make sure the repository is public.")
            }
            if (code != 200) throw Exception("GitHub answered with error $code.")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.getString("browser_download_url")
                    size = a.optLong("size", 0L)
                    break
                }
            }
            if (url == null) throw Exception("The latest release has no APK file attached.")
            ReleaseInfo(tag, tag.removePrefix("v"), url, size)
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(info: ReleaseInfo, onProgress: (Long, Long) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }   // remove older downloads
        val out = File(dir, "PayoutTracker-" + info.tag + ".apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) throw Exception("The download failed (error ${conn.responseCode}).")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var lastReported = 0L
                    while (true) {
                        if (cancelled) throw DownloadCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done - lastReported >= 64 * 1024) {
                            lastReported = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (total > 0 && done != total) {
                out.delete()
                throw IOException("incomplete download")
            }
            onProgress(done, total)
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}

/** True when version name [remote] (like "1.10") is higher than [local] (like "1.9"). */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String): List<Int> =
        v.split(".").map { p -> p.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun megabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

/** The App Update dialog: checking, up to date, update available, downloading, ready to install, error. */
@Composable
fun UpdateDialog(controller: UpdateController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val st = controller.state

    val close = {
        if (st is UpdateState.Downloading) controller.cancelDownload()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = close,
        containerColor = Color(0xFF111111),
        titleContentColor = White,
        textContentColor = White,
        title = { Text("App Update", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Installed version: " + controller.currentVersion, color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                when (st) {
                    is UpdateState.Idle, is UpdateState.Checking -> {
                        Text("Checking for updates...")
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Green,
                            trackColor = LineColor
                        )
                    }
                    is UpdateState.UpToDate -> {
                        Text("You already have the latest version.", color = Green, fontWeight = FontWeight.SemiBold)
                    }
                    is UpdateState.Available -> {
                        Text("Version " + st.info.version + " is available.", color = Green, fontWeight = FontWeight.SemiBold)
                        if (st.info.sizeBytes > 0) {
                            Text("Download size: " + megabytes(st.info.sizeBytes), color = Gray, fontSize = 12.sp)
                        }
                    }
                    is UpdateState.Downloading -> {
                        Text("Downloading version " + st.info.version + "...")
                        Spacer(Modifier.height(10.dp))
                        if (st.total > 0) {
                            LinearProgressIndicator(
                                progress = (st.done.toFloat() / st.total.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = Green,
                                trackColor = LineColor
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(megabytes(st.done) + " of " + megabytes(st.total), color = Gray, fontSize = 12.sp)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Green,
                                trackColor = LineColor
                            )
                        }
                    }
                    is UpdateState.Ready -> {
                        Text("Version " + st.info.version + " is ready to install.", color = Green, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Android will ask you to confirm. Your targets and entries are kept.",
                            color = Gray,
                            fontSize = 12.sp
                        )
                    }
                    is UpdateState.Failed -> {
                        Text(st.message, color = Red)
                    }
                }
            }
        },
        confirmButton = {
            when (st) {
                is UpdateState.Available -> TextButton(onClick = { controller.download(st.info) }) {
                    Text("Download", color = Green, fontWeight = FontWeight.Bold)
                }
                is UpdateState.Ready -> TextButton(onClick = {
                    val started = controller.install(st.file)
                    if (!started) {
                        Toast.makeText(
                            ctx,
                            "Allow Payout Tracker to install apps, then come back and tap Install again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) { Text("Install", color = Green, fontWeight = FontWeight.Bold) }
                is UpdateState.Failed -> TextButton(onClick = { controller.check(silent = false) }) {
                    Text("Retry", color = Green, fontWeight = FontWeight.Bold)
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = close) {
                Text(
                    when (st) {
                        is UpdateState.Downloading -> "Cancel"
                        is UpdateState.Available -> "Later"
                        is UpdateState.UpToDate -> "OK"
                        else -> "Close"
                    },
                    color = White
                )
            }
        }
    )
}
