package com.payouttracker.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.util.Calendar
import kotlin.math.max

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Shrinks a picked picture and returns it as text that fits inside a database document. */
fun compressImage(ctx: Context, uri: Uri): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val scale = 1000f / max(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bmp
        }
        var quality = 75
        var bytes = ByteArray(0)
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > 450_000 && quality > 25)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        return null
    }
}

@Composable
private fun AdminButton(text: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** The admin panel: add events, move them through live / won / lost, post strategies. */
@Composable
fun AdminScreen(
    repo: EventsRepo,
    gate: AppGate,
    account: AccountRepo,
    onUsers: () -> Unit,
    onReports: () -> Unit,
    onInbox: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onNewEvent: () -> Unit,
    onEditEvent: (String) -> Unit,
    onNewPost: () -> Unit
) {
    val ctx = LocalContext.current
    var minText by remember { mutableStateOf(gate.minVersion.toString()) }
    var confirmDelete by remember { mutableStateOf<BetEvent?>(null) }
    var confirmPost by remember { mutableStateOf<StrategyPost?>(null) }

    fun result(ok: Boolean) {
        if (!ok) ctx.toast("Could not save. Check your internet connection.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Admin Panel", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onNewEvent,
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                        modifier = Modifier.weight(1f)
                    ) { Text("Add Event", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onNewPost,
                        colors = ButtonDefaults.buttonColors(containerColor = StrategyColors[0], contentColor = White),
                        modifier = Modifier.weight(1f)
                    ) { Text("New Strategy", fontWeight = FontWeight.Bold) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUsers, modifier = Modifier.weight(1f)) {
                        Text("Users (" + account.users.size + ")", color = White, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onReports, modifier = Modifier.weight(1f)) {
                        Text(
                            "Reports (" + account.reports.size + ")",
                            color = if (account.reports.isEmpty()) White else Red, fontSize = 12.sp
                        )
                    }
                    OutlinedButton(onClick = onInbox, modifier = Modifier.weight(1f)) {
                        Text("Inbox (" + account.threads.count { it.reply.isBlank() } + ")", color = White, fontSize = 12.sp)
                    }
                }
            }
            item { PaymentNumbersBox(account, gate) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelBg)
                        .padding(12.dp)
                ) {
                    Text("Minimum app version (versionCode)", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Installed apps below this number are blocked until they update. This app is " + gate.versionCode + ".",
                        color = Gray, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { minText = it.filter { c -> c.isDigit() }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val n = minText.toLongOrNull() ?: 0L
                                repo.setMinVersion(n) { ok ->
                                    if (ok) ctx.toast("Saved") else ctx.toast("Could not save.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)
                        ) { Text("Set", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            item { Text("Events", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (repo.allEvents.isEmpty()) {
                item { Text("No events yet.", color = Gray, fontSize = 13.sp) }
            }
            items(repo.allEvents, key = { it.id }) { e ->
                val effective = when {
                    e.settled -> e.status.uppercase()
                    e.status == "live" || e.startAt <= repo.now -> "LIVE"
                    else -> "PRE-MATCH"
                }
                val tagColor = when (effective) {
                    "WON" -> Green
                    "LOST" -> Red
                    "LIVE" -> UnsettledColor
                    else -> Gray
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelBg)
                        .padding(12.dp)
                ) {
                    Text(effective, color = tagColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(e.teamA + " vs " + e.teamB, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(e.sport + "  •  " + e.tournament, color = Gray, fontSize = 12.sp)
                    Text(e.bet + "  •  " + e.odds + "  •  " + dateTimeText(e.startAt), color = Gray, fontSize = 12.sp)
                    Row {
                        if (!e.settled) {
                            if (effective == "PRE-MATCH") {
                                AdminButton("Go Live", Green) { repo.setStatus(e.id, "live", ::result) }
                            }
                            AdminButton("Won", Green) { repo.setStatus(e.id, "won", ::result) }
                            AdminButton("Lost", Red) { repo.setStatus(e.id, "lost", ::result) }
                            AdminButton("Edit", White) { onEditEvent(e.id) }
                        } else {
                            AdminButton("Won", Green) { repo.setStatus(e.id, "won", ::result) }
                            AdminButton("Lost", Red) { repo.setStatus(e.id, "lost", ::result) }
                            AdminButton("Reopen", UnsettledColor) { repo.setStatus(e.id, "live", ::result) }
                        }
                        AdminButton("Delete", Gray) { confirmDelete = e }
                    }
                }
            }

            item { Text("Strategy posts", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (repo.strategies.isEmpty()) {
                item { Text("No posts yet.", color = Gray, fontSize = 13.sp) }
            }
            items(repo.strategies, key = { it.id }) { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelBg)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.title, color = White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    AdminButton("Delete", Gray) { confirmPost = p }
                }
            }
        }
    }

    val ev = confirmDelete
    if (ev != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Delete event?") },
            text = { Text(ev.teamA + " vs " + ev.teamB) },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteEvent(ev.id, ::result)
                    confirmDelete = null
                }) { Text("Delete", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = White) } }
        )
    }
    val post = confirmPost
    if (post != null) {
        AlertDialog(
            onDismissRequest = { confirmPost = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Delete post?") },
            text = { Text(post.title) },
            confirmButton = {
                TextButton(onClick = {
                    repo.deletePost(post.id, ::result)
                    confirmPost = null
                }) { Text("Delete", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmPost = null }) { Text("Cancel", color = White) } }
        )
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Add a new event, or edit an existing one when [initial] is given. */
@Composable
fun EventEditorScreen(
    initial: BetEvent?,
    repo: EventsRepo,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    val ctx = LocalContext.current
    var sport by remember { mutableStateOf(initial?.sport ?: SPORTS[0].first) }
    var tournament by remember { mutableStateOf(initial?.tournament ?: "") }
    var teamA by remember { mutableStateOf(initial?.teamA ?: "") }
    var teamB by remember { mutableStateOf(initial?.teamB ?: "") }
    var bet by remember { mutableStateOf(initial?.bet ?: "") }
    var odds by remember { mutableStateOf(initial?.odds ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var status by remember { mutableStateOf(if (initial?.status == "live") "live" else "prematch") }
    var startAt by remember {
        mutableLongStateOf(initial?.startAt ?: (System.currentTimeMillis() + 3600_000L))
    }
    var sportMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(if (initial == null) "Add Event" else "Edit Event", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Sport", color = Gray, fontSize = 12.sp)
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { sportMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(sportIcon(sport), contentDescription = null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text(sport, color = White, modifier = Modifier.weight(1f))
                }
                DropdownMenu(expanded = sportMenu, onDismissRequest = { sportMenu = false }) {
                    SPORTS.forEach { (name, icon) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            leadingIcon = { Icon(icon, contentDescription = null) },
                            onClick = {
                                sport = name
                                sportMenu = false
                            }
                        )
                    }
                }
            }
            FormField("Tournament (for example US Open)", tournament, { tournament = it })
            FormField("Team / player 1", teamA, { teamA = it })
            FormField("Team / player 2", teamB, { teamB = it })

            Text("Date and time", color = Gray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = startAt }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            val n = Calendar.getInstance().apply {
                                timeInMillis = startAt
                                set(y, m, d)
                            }
                            startAt = n.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(dateTimeText(startAt).substringBefore(","), color = White) }
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = startAt }
                        TimePickerDialog(ctx, { _, h, min ->
                            val n = Calendar.getInstance().apply {
                                timeInMillis = startAt
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, min)
                                set(Calendar.SECOND, 0)
                            }
                            startAt = n.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(timeText(startAt), color = White) }
            }

            FormField("Bet (for example Total Sets 3.5 Over)", bet, { bet = it })
            FormField("Odds", odds, { odds = it })
            FormField("1x code (optional)", code, { code = it })

            Text("Show in", color = Gray, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = status == "prematch",
                    onClick = { status = "prematch" },
                    colors = RadioButtonDefaults.colors(selectedColor = Green, unselectedColor = Gray)
                )
                Text("Pre-Match (goes live by itself at the start time)", color = White, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = status == "live",
                    onClick = { status = "live" },
                    colors = RadioButtonDefaults.colors(selectedColor = Green, unselectedColor = Gray)
                )
                Text("Live now", color = White, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (teamA.isBlank() || teamB.isBlank() || bet.isBlank() || odds.isBlank()) {
                        ctx.toast("Fill in both teams, the bet and the odds.")
                    } else {
                        val e = BetEvent(
                            id = initial?.id ?: "",
                            sport = sport,
                            tournament = tournament.trim(),
                            teamA = teamA.trim(),
                            teamB = teamB.trim(),
                            startAt = startAt,
                            bet = bet.trim(),
                            odds = odds.trim(),
                            code = code.trim(),
                            status = status
                        )
                        repo.saveEvent(e) { ok ->
                            if (!ok) ctx.toast("Could not save the event.")
                        }
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/** New strategy post: title, text and one picture. */
@Composable
fun PostEditorScreen(repo: EventsRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val data = compressImage(ctx, uri)
            if (data == null) ctx.toast("Could not read that picture.") else image = data
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("New Strategy", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormField("Title", title, { title = it })
            FormField("Text", body, { body = it }, singleLine = false)
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (image.isEmpty()) "Choose picture" else "Change picture", color = White)
            }
            if (image.isNotEmpty()) {
                StoredImage(
                    image,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    scale = ContentScale.FillWidth
                )
            }
            Button(
                onClick = {
                    if (title.isBlank()) {
                        ctx.toast("Write a title.")
                    } else {
                        repo.savePost(title.trim(), body.trim(), image) { ok ->
                            if (!ok) ctx.toast("Could not post.")
                        }
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) { Text("Post", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
