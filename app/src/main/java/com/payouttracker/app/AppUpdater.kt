package com.payouttracker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    data class Failed(val message: String) : UpdateState()
}

/**
 * Looks for a newer release on GitHub and hands the APK link to the browser, which downloads it.
 * The app itself needs only the INTERNET permission: it never installs anything on its own, so
 * you open the downloaded file and confirm the installation yourself.
 */
class UpdateController(private val ctx: Context, private val scope: CoroutineScope) {

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Version name of a newer release found by the quiet check at app start, or null. */
    var newVersion by mutableStateOf<String?>(null)
        private set

    val currentVersion: String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Called when the update dialog opens. */
    fun onOpen() {
        check(silent = false)
    }

    /** [silent] checks only record [newVersion] and never change the dialog state. */
    fun check(silent: Boolean) {
        if (state is UpdateState.Checking) return
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

    /** Opens the APK link; the browser downloads the file. Returns false if nothing could open it. */
    fun openDownload(info: ReleaseInfo): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

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

/** The App Update dialog: checking, up to date, update available, error. */
@Composable
fun UpdateDialog(controller: UpdateController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val st = controller.state

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap Download. Your browser saves the APK; open it from the download notification and tap Install. Your targets and entries are kept.",
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
                is UpdateState.Available -> TextButton(onClick = {
                    if (!controller.openDownload(st.info)) {
                        Toast.makeText(ctx, "Could not open the download link.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Download", color = Green, fontWeight = FontWeight.Bold) }
                is UpdateState.Failed -> TextButton(onClick = { controller.check(silent = false) }) {
                    Text("Retry", color = Green, fontWeight = FontWeight.Bold)
                }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    when (st) {
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
