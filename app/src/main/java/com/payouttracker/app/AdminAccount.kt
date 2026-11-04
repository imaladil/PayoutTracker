package com.payouttracker.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun Context.say(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

@Composable
private fun AdminPanelBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun AdminField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PlanChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PLANS.forEach { p ->
            val sel = p.key == selected
            OutlinedButton(
                onClick = { onSelect(p.key) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) { Text(p.label, color = if (sel) Green else White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }
        }
    }
}

private fun statusOf(u: UserRow, now: Long): Pair<String, Color> {
    val paid = maxOf(u.paidUntil, u.bonusUntil)
    return when {
        isAdminEmail(u.email) -> "ADMIN" to Green
        u.terminated -> "TERMINATED" to Red
        paid >= now -> ("PREMIUM until " + dateOnly(paid)) to Green
        trialEnd(u.createdAt) >= now -> ("TRIAL until " + dateOnly(trialEnd(u.createdAt))) to UnsettledColor
        else -> "EXPIRED" to Gray
    }
}

// ---------------------------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------------------------

@Composable
fun AdminUsersScreen(account: AccountRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var trx by remember { mutableStateOf("") }
    var trxPlan by remember { mutableStateOf("30d") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var spendOf by remember { mutableStateOf<UserRow?>(null) }
    var confirmTerminate by remember { mutableStateOf<UserRow?>(null) }
    val now = System.currentTimeMillis()

    val q = query.trim().lowercase()
    val list = account.users.filter { u ->
        q.isEmpty() || u.email.lowercase().contains(q) || u.name.lowercase().contains(q) ||
            account.payments.any { p -> p.uid == u.uid && p.trxId.lowercase().contains(q) }
    }
    val selectable = list.filter { !isAdminEmail(it.email) }
    val chosen = account.users.filter { it.uid in selected && !isAdminEmail(it.email) }

    fun done(ok: Boolean) {
        ctx.say(if (ok) "Done" else "Could not save. Check your internet connection.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Users", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Registered users: " + account.users.size, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            item {
                AdminPanelBox {
                    Text("Activate by Transaction ID", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Pick the time, enter the ID the user submitted, and the matching user becomes premium.", color = Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    PlanChips(trxPlan) { trxPlan = it }
                    Spacer(Modifier.height(8.dp))
                    AdminField("Transaction ID", trx, { trx = it.filter { c -> c.isLetterOrDigit() }.take(24).uppercase() })
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            account.activateByTrx(trx, trxPlan) { err ->
                                if (err == null) {
                                    ctx.say("Activated")
                                    trx = ""
                                } else {
                                    ctx.say(err)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activate", fontWeight = FontWeight.Bold) }
                }
            }
            item { AdminField("Search email, name or Transaction ID", query, { query = it }) }
            item {
                AdminPanelBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectable.isNotEmpty() && selectable.all { it.uid in selected },
                            onCheckedChange = { on ->
                                selected = if (on) selected + selectable.map { it.uid } else selected - selectable.map { it.uid }.toSet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Green)
                        )
                        Text("Select all shown (" + chosen.size + " selected)", color = White, fontSize = 13.sp)
                    }
                    Text("Bonus days (added on top of what they already have)", color = Gray, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 7, 15, 30).forEach { d ->
                            OutlinedButton(onClick = {
                                if (chosen.isEmpty()) ctx.say("Select users first.") else account.grantBonus(chosen, d, ::done)
                            }) { Text("+$d days", color = Green) }
                        }
                        OutlinedButton(onClick = {
                            if (chosen.isEmpty()) ctx.say("Select users first.") else account.removeBonus(chosen, ::done)
                        }) { Text("Remove bonus", color = Red) }
                    }
                }
            }
            items(list, key = { it.uid }) { u ->
                val st = statusOf(u, now)
                AdminPanelBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAdminEmail(u.email)) {
                            Spacer(Modifier.width(48.dp))
                        } else {
                            Checkbox(
                                checked = u.uid in selected,
                                onCheckedChange = { on -> selected = if (on) selected + u.uid else selected - u.uid },
                                colors = CheckboxDefaults.colors(checkedColor = Green)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.name.ifBlank { u.email }, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(u.email, color = Gray, fontSize = 12.sp)
                            Text("Joined " + dateOnly(u.createdAt), color = Gray, fontSize = 12.sp)
                            Text(st.first, color = st.second, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isAdminEmail(u.email)) {
                        Text("Admin account", color = Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Spent: " + u.totalPaid + " BDT  >",
                            color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { spendOf = u }
                                .padding(vertical = 6.dp)
                        )
                        if (u.terminated) {
                            TextButton(onClick = { account.setTerminated(u, false, ::done) }) { Text("Restore", color = Green, fontWeight = FontWeight.Bold) }
                        } else {
                            TextButton(onClick = { confirmTerminate = u }) { Text("Terminate", color = Red, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    val s = spendOf
    if (s != null) {
        val paid = account.payments.filter { it.uid == s.uid && it.status == "active" }.sortedByDescending { it.activatedAt }
        AlertDialog(
            onDismissRequest = { spendOf = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Spent: " + s.totalPaid + " BDT") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (paid.isEmpty()) Text("No payments yet.", color = Gray)
                    paid.forEach { p ->
                        Text(monthKey(p.activatedAt), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            dateTimeText(p.activatedAt) + " • " + p.paidAmount + " BDT • " + methodOf(p.method).label +
                                " • " + planOf(p.grantedPlan.ifBlank { p.plan }).label,
                            color = White, fontSize = 13.sp
                        )
                        Text("ID " + p.trxId, color = Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { spendOf = null }) { Text("Close", color = Green, fontWeight = FontWeight.Bold) } }
        )
    }
    val ct = confirmTerminate
    if (ct != null) {
        AlertDialog(
            onDismissRequest = { confirmTerminate = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Terminate this account?") },
            text = { Text(ct.email) },
            confirmButton = {
                TextButton(onClick = {
                    account.setTerminated(ct, true, ::done)
                    confirmTerminate = null
                }) { Text("Terminate", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmTerminate = null }) { Text("Cancel", color = White) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pending payments: Transaction IDs that users submitted and that wait for approval (oldest first)
// ---------------------------------------------------------------------------------------------

@Composable
fun AdminPendingPaymentsScreen(account: AccountRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var approveFor by remember { mutableStateOf<Payment?>(null) }
    var declineFor by remember { mutableStateOf<Payment?>(null) }
    var note by remember { mutableStateOf("") }
    val list = account.payments.filter { it.status == "pending" }.sortedBy { it.submittedAt.takeIf { t -> t > 0 } ?: it.createdAt }

    fun done(ok: Boolean) {
        ctx.say(if (ok) "Done" else "Could not save. Check your internet connection.")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Pending payments", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    if (list.isEmpty()) "No payments are waiting." else list.size.toString() + " waiting, oldest first.",
                    color = Gray, fontSize = 13.sp
                )
            }
            items(list, key = { it.id }) { p ->
                val m = methodOf(p.method)
                val plan = planOf(p.plan)
                AdminPanelBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dateTimeText(if (p.submittedAt > 0) p.submittedAt else p.createdAt),
                            color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f)
                        )
                        Text(m.label, color = m.color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(p.name.ifBlank { p.email }, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(p.email, color = Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(p.amount.toString() + " BDT  •  " + plan.label, color = Green, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Transaction ID: " + p.trxId, color = White, fontSize = 13.sp)
                    Text("Sent from: " + p.sender, color = Gray, fontSize = 12.sp)
                    if (p.amount != plan.price) {
                        Text("The amount does not match the plan price (" + plan.price + " BDT).", color = UnsettledColor, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { approveFor = p },
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                            modifier = Modifier.weight(1f)
                        ) { Text("Approve", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                note = ""
                                declineFor = p
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Decline", color = Red, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    val a = approveFor
    if (a != null) {
        AlertDialog(
            onDismissRequest = { approveFor = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Approve this payment?") },
            text = {
                Text(
                    (a.name.ifBlank { a.email }) + "\n" + a.amount + " BDT  •  " + planOf(a.plan).label +
                        "\nID " + a.trxId + "\nThe subscription starts right away."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    account.activate(a, a.plan, account.reports.firstOrNull { it.paymentId == a.id }, ::done)
                    approveFor = null
                }) { Text("Approve", color = Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { approveFor = null }) { Text("Cancel", color = White) } }
        )
    }
    val d = declineFor
    if (d != null) {
        AlertDialog(
            onDismissRequest = { declineFor = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Decline payment") },
            text = { AdminField("Message to the user", note, { note = it.take(500) }, singleLine = false) },
            confirmButton = {
                TextButton(onClick = {
                    account.declinePayment(d, note, ::done)
                    declineFor = null
                }) { Text("Decline", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { declineFor = null }) { Text("Cancel", color = White) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Payment reports (oldest first)
// ---------------------------------------------------------------------------------------------

@Composable
fun AdminReportsScreen(account: AccountRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var activateFor by remember { mutableStateOf<PayReport?>(null) }
    var declineFor by remember { mutableStateOf<PayReport?>(null) }
    var terminateFor by remember { mutableStateOf<PayReport?>(null) }
    var plan by remember { mutableStateOf("30d") }
    var note by remember { mutableStateOf("") }
    val list = account.reports.sortedBy { it.createdAt }

    fun done(ok: Boolean) {
        ctx.say(if (ok) "Done" else "Could not save. Check your internet connection.")
    }

    fun linked(r: PayReport): Payment? =
        account.findPayment(r.paymentId) ?: account.payments.firstOrNull { it.trxId == r.trxId }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Payment reports", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (list.isEmpty()) {
                item { Text("No reports.", color = Gray, fontSize = 14.sp) }
            }
            items(list, key = { it.id }) { r ->
                val p = linked(r)
                AdminPanelBox {
                    Text("Reported " + dateTimeText(r.createdAt), color = Gray, fontSize = 11.sp)
                    Text(r.name.ifBlank { r.email }, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(r.email, color = Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(methodOf(r.method).label + " • ID " + r.trxId, color = White, fontSize = 13.sp)
                    Text("Paid at " + dateTimeText(r.paidAt), color = Gray, fontSize = 12.sp)
                    if (p != null) {
                        Text(
                            "Submitted: " + planOf(p.plan).label + " • " + p.amount + " BDT • " + p.status +
                                " • from " + p.sender,
                            color = Gray, fontSize = 12.sp
                        )
                    } else {
                        Text("No submitted payment matches this report.", color = UnsettledColor, fontSize = 12.sp)
                    }
                    Row {
                        TextButton(onClick = {
                            plan = p?.plan ?: "30d"
                            activateFor = r
                        }) { Text("Activate", color = Green, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = {
                            note = ""
                            declineFor = r
                        }) { Text("Decline", color = UnsettledColor, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { terminateFor = r }) { Text("Terminate", color = Red, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    val a = activateFor
    if (a != null) {
        AlertDialog(
            onDismissRequest = { activateFor = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Activate for") },
            text = { PlanChips(plan) { plan = it } },
            confirmButton = {
                TextButton(onClick = {
                    val p = linked(a)
                    if (p == null) {
                        ctx.say("No payment matches this report.")
                    } else if (p.status != "pending") {
                        ctx.say("That payment is already " + p.status + ".")
                    } else {
                        account.activate(p, plan, a, ::done)
                    }
                    activateFor = null
                }) { Text("Activate", color = Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { activateFor = null }) { Text("Cancel", color = White) } }
        )
    }
    val d = declineFor
    if (d != null) {
        AlertDialog(
            onDismissRequest = { declineFor = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Decline report") },
            text = { AdminField("Message to the user", note, { note = it.take(500) }, singleLine = false) },
            confirmButton = {
                TextButton(onClick = {
                    account.decline(linked(d), d, note, ::done)
                    declineFor = null
                }) { Text("Decline", color = UnsettledColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { declineFor = null }) { Text("Cancel", color = White) } }
        )
    }
    val t = terminateFor
    if (t != null) {
        AlertDialog(
            onDismissRequest = { terminateFor = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Terminate this account?") },
            text = { Text(t.email) },
            confirmButton = {
                TextButton(onClick = {
                    account.terminateFromReport(t, ::done)
                    terminateFor = null
                }) { Text("Terminate", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { terminateFor = null }) { Text("Cancel", color = White) } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Inbox and broadcast
// ---------------------------------------------------------------------------------------------

@Composable
fun AdminInboxScreen(account: AccountRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ThreadMsg?>(null) }
    var reply by remember { mutableStateOf("") }
    val list = account.threads.sortedWith(
        compareBy<ThreadMsg> { it.reply.isNotBlank() }.thenByDescending { it.createdAt }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Inbox", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AdminPanelBox {
                    Text("Message to everyone", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    AdminField("Title", title, { title = it.take(80) })
                    Spacer(Modifier.height(8.dp))
                    AdminField("Message", body, { body = it.take(2000) }, singleLine = false)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank() || body.isBlank()) {
                                ctx.say("Write a title and a message.")
                            } else {
                                account.broadcast(title.trim(), body.trim()) { ok ->
                                    ctx.say(if (ok) "Sent to everyone" else "Could not send.")
                                    if (ok) {
                                        title = ""
                                        body = ""
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send to everyone", fontWeight = FontWeight.Bold) }
                }
            }
            item { Text("Messages from users", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            if (list.isEmpty()) {
                item { Text("No messages yet.", color = Gray, fontSize = 13.sp) }
            }
            items(list, key = { it.id }) { t ->
                AdminPanelBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (t.reply.isBlank()) "NEEDS REPLY" else "REPLIED",
                            color = if (t.reply.isBlank()) UnsettledColor else Green,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(dateTimeText(t.createdAt), color = Gray, fontSize = 11.sp)
                    }
                    Text(t.name.ifBlank { t.email } + "  •  " + t.email, color = Gray, fontSize = 12.sp)
                    Text(t.subject, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(t.body, color = White, fontSize = 13.sp)
                    if (t.reply.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Your reply: " + t.reply, color = Green, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        reply = ""
                        replyTo = t
                    }) { Text(if (t.reply.isBlank()) "Reply" else "Reply again", color = Green, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    val rt = replyTo
    if (rt != null) {
        AlertDialog(
            onDismissRequest = { replyTo = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Reply to " + rt.name.ifBlank { rt.email }) },
            text = { AdminField("Reply", reply, { reply = it.take(2000) }, singleLine = false) },
            confirmButton = {
                TextButton(onClick = {
                    if (reply.isBlank()) {
                        ctx.say("Write a reply.")
                    } else {
                        account.replyThread(rt, reply.trim()) { ok -> ctx.say(if (ok) "Reply sent" else "Could not send.") }
                        replyTo = null
                    }
                }) { Text("Send", color = Green, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { replyTo = null }) { Text("Cancel", color = White) } }
        )
    }
}

/** Payment numbers form shown in the Admin Panel. */
@Composable
fun PaymentNumbersBox(account: AccountRepo, gate: AppGate) {
    val ctx = LocalContext.current
    var bkash by remember { mutableStateOf(gate.bkash) }
    var nagad by remember { mutableStateOf(gate.nagad) }
    var rocket by remember { mutableStateOf(gate.rocket) }
    AdminPanelBox {
        Text("Payment numbers (Send Money, personal)", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        AdminField("bKash number", bkash, { bkash = it.filter { c -> c.isDigit() }.take(11) })
        Spacer(Modifier.height(6.dp))
        AdminField("Nagad number", nagad, { nagad = it.filter { c -> c.isDigit() }.take(11) })
        Spacer(Modifier.height(6.dp))
        AdminField("Rocket number", rocket, { rocket = it.filter { c -> c.isDigit() }.take(12) })
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = {
                    account.setPaymentNumbers(bkash, nagad, rocket) { ok ->
                        ctx.say(if (ok) "Saved" else "Could not save.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)
            ) { Text("Save numbers", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
        }
    }
}
