package com.payouttracker.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

fun durationText(ms: Long): String {
    val minutes = (ms + 59_999L) / 60_000L
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@Composable
private fun TextBox(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .padding(14.dp)
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Mailbox
// ---------------------------------------------------------------------------------------------

@Composable
fun MailButton(unread: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
            Icon(Icons.Filled.Email, contentDescription = "Messages", tint = White)
        }
    }
}

private data class MailItem(
    val key: String,
    val title: String,
    val preview: String,
    val time: Long,
    val tag: String,
    val tagColor: Color,
    val detail: String
)

@Composable
fun MailboxScreen(account: AccountRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    var open by remember { mutableStateOf<MailItem?>(null) }
    val items = (account.messages.map { m ->
        MailItem("m" + m.id, m.title, m.body, m.createdAt, "From admin", Green, m.body)
    } + account.myThreads.map { t ->
        val detail = "Your message:\n" + t.body + "\n\n" +
            (if (t.reply.isBlank()) "Waiting for the admin's reply." else "Admin's reply:\n" + t.reply)
        MailItem(
            "t" + t.id, t.subject, t.body, t.createdAt,
            if (t.reply.isBlank()) "Your message • waiting" else "Your message • replied",
            if (t.reply.isBlank()) UnsettledColor else Green, detail
        )
    }).sortedByDescending { it.time }

    // Opening Messages counts as reading everything in it
    val newest = account.messages.maxOfOrNull { it.createdAt } ?: 0L
    LaunchedEffect(newest) { account.markSeen(newest) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Messages", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.isEmpty()) {
                Text("No messages yet.", color = Gray, fontSize = 14.sp)
            }
            items.forEach { item ->
                Panel(modifier = Modifier.clickable { open = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.tag, color = item.tagColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(dateTimeText(item.time), color = Gray, fontSize = 11.sp)
                    }
                    Text(item.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(item.preview, color = Gray, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
    }

    val o = open
    if (o != null) {
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text(o.title, fontWeight = FontWeight.Bold) },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(o.detail) } },
            confirmButton = { TextButton(onClick = { open = null }) { Text("OK", color = Green, fontWeight = FontWeight.Bold) } }
        )
    }
}

/** A new message, shown in full when the app is opened. OK closes it. */
@Composable
fun MessagePopup(m: InboxMessage, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF111111),
        titleContentColor = White,
        textContentColor = White,
        title = { Text(m.title, fontWeight = FontWeight.Bold) },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(m.body) } },
        confirmButton = { TextButton(onClick = onOk) { Text("OK", color = Green, fontWeight = FontWeight.Bold) } }
    )
}

@Composable
fun NudgeDialog(text: String, onSubscribe: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Color(0xFF111111),
        titleContentColor = White,
        textContentColor = White,
        title = { Text("Subscription", fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onSubscribe) { Text("Subscribe", color = Green, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later", color = White) } }
    )
}

// ---------------------------------------------------------------------------------------------
// Contact Us
// ---------------------------------------------------------------------------------------------

@Composable
fun ContactScreen(
    account: AccountRepo,
    user: AuthUser,
    onGroup: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    val ctx = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Contact Us", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Send a message to the admin. The reply arrives in your Messages.", color = Gray, fontSize = 13.sp)
            TextBox("Subject", subject, { subject = it.take(80) })
            TextBox("Message", body, { body = it.take(2000) }, singleLine = false)
            Button(
                onClick = {
                    if (subject.isBlank() || body.isBlank()) {
                        Toast.makeText(ctx, "Write a subject and a message.", Toast.LENGTH_SHORT).show()
                    } else {
                        busy = true
                        account.sendThread(user, subject.trim(), body.trim()) { ok ->
                            busy = false
                            if (ok) {
                                Toast.makeText(ctx, "Message sent.", Toast.LENGTH_SHORT).show()
                                subject = ""
                                body = ""
                            } else {
                                Toast.makeText(ctx, "Could not send. Check your internet connection.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) { Text(if (busy) "Sending..." else "Send", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = onGroup) { Text("Join the Telegram group", color = MessageColors[0]) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Subscribe
// ---------------------------------------------------------------------------------------------

@Composable
fun SubscribeScreen(
    account: AccountRepo,
    gate: AppGate,
    user: AuthUser,
    access: Access,
    now: Long,
    onReport: (String) -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var plan by remember { mutableStateOf("30d") }
    var method by remember { mutableStateOf("bkash") }
    var sender by remember { mutableStateOf("") }
    var trx by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Payment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val m = methodOf(method)
    val pending = account.pendingFor(method)
    val number = gate.numberFor(method)
    val price = planOf(plan).price
    val showForm = editing != null || pending == null

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Subscribe", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status
            Panel {
                when (access.phase) {
                    "premium" -> {
                        Text("Premium", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Active until " + dateOnly(access.until), color = White, fontSize = 14.sp)
                    }
                    "trial" -> {
                        Text("Free trial", color = UnsettledColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(access.daysLeft.toString() + " day(s) left, until " + dateOnly(access.until), color = White, fontSize = 14.sp)
                    }
                    else -> {
                        Text("Not subscribed", color = Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Subscribe to open Live and Pre-Match.", color = White, fontSize = 14.sp)
                    }
                }
            }

            // Plan
            Text("Plan", color = Gray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLANS.forEach { p ->
                    val sel = p.key == plan
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Color(0xFF14391C) else PanelBg)
                            .clickable { plan = p.key }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(p.label, color = if (sel) Green else White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(p.price.toString() + " BDT", color = if (sel) Green else Gray, fontSize = 12.sp)
                    }
                }
            }

            // Method
            Text("Payment method", color = Gray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                METHODS.forEach { pm ->
                    val sel = pm.key == method
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) pm.color else PanelBg)
                            .clickable {
                                method = pm.key
                                editing = null
                                error = null
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(pm.label, color = White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Where to send the money
            Panel {
                Text("Send Money only (Personal number)", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                if (number.isBlank()) {
                    Text("The " + m.label + " number has not been added yet. Please try another method or contact the admin.", color = Gray, fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.label + " personal: ", color = Gray, fontSize = 13.sp)
                        Text(number, color = m.color, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(number))
                            Toast.makeText(ctx, "Number copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = White) }
                    }
                    Text("Amount: " + price + " BDT (" + planOf(plan).label + ")", color = White, fontSize = 14.sp)
                    Text("Use Send Money. Other kinds of payment are not accepted.", color = Gray, fontSize = 12.sp)
                }
            }

            if (showForm) {
                Panel {
                    Text(if (editing != null) "Edit Transaction ID" else "After sending the money", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    TextBox("Your " + m.label + " number", sender, { sender = it.filter { c -> c.isDigit() }.take(11) }, KeyboardType.Phone)
                    Spacer(Modifier.height(8.dp))
                    TextBox("Transaction ID", trx, { trx = it.filter { c -> c.isLetterOrDigit() }.take(24).uppercase() })
                    val err = error
                    if (err != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = Red, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                error = null
                                busy = true
                                val done: (String?) -> Unit = { e ->
                                    busy = false
                                    if (e == null) {
                                        Toast.makeText(ctx, "Submitted", Toast.LENGTH_SHORT).show()
                                        trx = ""
                                        sender = ""
                                        editing = null
                                    } else {
                                        error = e
                                    }
                                }
                                val ed = editing
                                if (ed != null) {
                                    account.editPayment(ed, method, plan, sender, trx, done)
                                } else {
                                    account.submitPayment(user, method, plan, sender, trx, done)
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) "Please wait..." else if (editing != null) "Save changes" else "Submit", fontWeight = FontWeight.Bold) }
                        if (editing != null) {
                            OutlinedButton(onClick = { editing = null; error = null }) { Text("Cancel", color = White) }
                        }
                    }
                }
            } else if (pending != null) {
                val editLeft = pending.submittedAt + 3_600_000L - now
                val hasReport = account.myReports.any { it.paymentId == pending.id }
                Panel {
                    Text("Submitted Transaction ID", color = Gray, fontSize = 12.sp)
                    Text(pending.trxId, color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(planOf(pending.plan).label + " • " + pending.amount + " BDT • " + methodOf(pending.method).label, color = Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Your subscription will be active within 1 hour.", color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (editLeft > 0) {
                            OutlinedButton(onClick = {
                                sender = pending.sender
                                trx = pending.trxId
                                plan = pending.plan
                                editing = pending
                                error = null
                            }) { Text("Edit", color = White) }
                        }
                        if (hasReport) {
                            Text("Waiting for admin response", color = UnsettledColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            OutlinedButton(onClick = {
                                val wait = pending.submittedAt + 7_200_000L - now
                                if (wait > 0) {
                                    notice = "You can report after " + durationText(wait) + " (2 hours after submitting)."
                                } else {
                                    onReport(pending.id)
                                }
                            }) { Text("Report", color = Red) }
                        }
                    }
                    if (editLeft > 0) {
                        Text("You can edit the Transaction ID for " + durationText(editLeft) + " more.", color = Gray, fontSize = 12.sp)
                    } else {
                        Text("The edit time is over.", color = Gray, fontSize = 12.sp)
                    }
                }
            }

            // History
            val mine = account.myPayments
            if (mine.isNotEmpty()) {
                Text("Your payments", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                mine.forEach { p ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.trxId, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                p.status.uppercase(),
                                color = when (p.status) { "active" -> Green; "declined" -> Red; else -> UnsettledColor },
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            methodOf(p.method).label + " • " + planOf(p.plan).label + " • " + p.amount + " BDT • " + dateTimeText(p.createdAt),
                            color = Gray, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    val n = notice
    if (n != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Report") },
            text = { Text(n) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK", color = Green, fontWeight = FontWeight.Bold) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Payment report
// ---------------------------------------------------------------------------------------------

@Composable
fun PayReportScreen(
    account: AccountRepo,
    user: AuthUser,
    paymentId: String,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    val ctx = LocalContext.current
    val payment = account.findPayment(paymentId)
    var method by remember { mutableStateOf(payment?.method ?: "bkash") }
    var trx by remember { mutableStateOf(payment?.trxId ?: "") }
    var paidAt by remember { mutableLongStateOf(payment?.submittedAt ?: System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    val already = account.myReports.any { it.paymentId == paymentId }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Report a payment", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (already) {
                Panel {
                    Text("Waiting for admin response", color = UnsettledColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Your report was sent. The answer arrives in your Messages.", color = White, fontSize = 14.sp)
                }
            } else {
                Text("Payment method", color = Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METHODS.forEach { pm ->
                        val sel = pm.key == method
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (sel) pm.color else PanelBg)
                                .clickable { method = pm.key }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(pm.label, color = White, fontWeight = FontWeight.Bold) }
                    }
                }
                Text("Date and time of the payment", color = Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = paidAt }
                            DatePickerDialog(ctx, { _, y, mo, d ->
                                val n = Calendar.getInstance().apply { timeInMillis = paidAt; set(y, mo, d) }
                                paidAt = n.timeInMillis
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateTimeText(paidAt).substringBefore(","), color = White) }
                    OutlinedButton(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = paidAt }
                            TimePickerDialog(ctx, { _, h, mi ->
                                val n = Calendar.getInstance().apply {
                                    timeInMillis = paidAt
                                    set(Calendar.HOUR_OF_DAY, h)
                                    set(Calendar.MINUTE, mi)
                                }
                                paidAt = n.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(timeText(paidAt), color = White) }
                }
                TextBox("Transaction ID", trx, { trx = it.filter { c -> c.isLetterOrDigit() }.take(24).uppercase() })
                Button(
                    onClick = {
                        if (normalizeTrx(trx) == null) {
                            Toast.makeText(ctx, "Enter a valid Transaction ID.", Toast.LENGTH_SHORT).show()
                        } else {
                            busy = true
                            account.submitReport(user, paymentId, method, paidAt, trx) { ok ->
                                busy = false
                                if (!ok) Toast.makeText(ctx, "Could not send the report.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) { Text(if (busy) "Sending..." else "Send report", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Terminated accounts
// ---------------------------------------------------------------------------------------------

@Composable
fun TerminatedOverlay(onLogout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = Red, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text("Terminated", color = Red, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your account has been terminated.",
                color = White, fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
            ) { Text("Log Out", fontWeight = FontWeight.Bold) }
        }
    }
}
