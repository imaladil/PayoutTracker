package com.payouttracker.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/** The version number of the installed app. */
fun installedVersionCode(ctx: Context): Long = try {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
} catch (e: Exception) {
    0L
}

/**
 * Forced updates. The admin sets config/app.minVersionCode in the database; any installed
 * version below it is blocked until the person updates. If the database cannot be read, the
 * app is not blocked.
 */
class AppGate(ctx: Context) {
    val versionCode: Long = installedVersionCode(ctx)

    var minVersion by mutableStateOf(0L)
        private set

    val mustUpdate: Boolean get() = minVersion > versionCode

    init {
        if (FirebaseApp.getApps(ctx).isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("config").document("app")
                .addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    minVersion = snap.getLong("minVersionCode") ?: 0L
                }
        }
    }
}

/** Full-screen block shown when the installed version is too old. */
@Composable
fun ForceUpdateScreen(updater: UpdateController, onExit: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { updater.check(silent = false) }
    val st = updater.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Green, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Update required", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "A new version of Payout Tracker is available. Please update the app to keep using it.",
            color = Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Text("Installed version: " + updater.currentVersion, color = Gray, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))

        when (st) {
            is UpdateState.Available -> {
                Button(
                    onClick = {
                        if (!updater.openDownload(st.info)) {
                            Toast.makeText(ctx, "Could not open the download link.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Download version " + st.info.version, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Open the downloaded file from the notification and tap Install.",
                    color = Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            is UpdateState.UpToDate -> {
                Text(
                    "The new version is not published yet. Please try again in a few minutes.",
                    color = White,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { updater.check(silent = false) }) {
                    Text("Check again", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            is UpdateState.Failed -> {
                Text(st.message, color = Red, textAlign = TextAlign.Center)
                TextButton(onClick = { updater.check(silent = false) }) {
                    Text("Retry", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            else -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Green,
                trackColor = LineColor
            )
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onExit) { Text("Exit app", color = Gray) }
    }
}
