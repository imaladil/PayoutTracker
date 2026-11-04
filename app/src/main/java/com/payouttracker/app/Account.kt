package com.payouttracker.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------------------------
// Dates. Everything that counts calendar days uses Bangladesh time.
// ---------------------------------------------------------------------------------------------

val DHAKA: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")
const val DAY_MS = 86_400_000L

/** The last millisecond of the day that is [days]/[months]/[years] after [ms] (Dhaka time). */
fun endOfDay(ms: Long, days: Int = 0, months: Int = 0, years: Int = 0): Long {
    val c = Calendar.getInstance(DHAKA)
    c.timeInMillis = ms
    c.add(Calendar.YEAR, years)
    c.add(Calendar.MONTH, months)
    c.add(Calendar.DAY_OF_YEAR, days)
    c.set(Calendar.HOUR_OF_DAY, 23)
    c.set(Calendar.MINUTE, 59)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}

fun dayStamp(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = DHAKA }.format(Date(ms))

fun dateOnly(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.US).apply { timeZone = DHAKA }.format(Date(ms))

fun monthKey(ms: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.US).apply { timeZone = DHAKA }.format(Date(ms))

/** The free trial: 7 calendar days after the day of registration. */
fun trialEnd(createdAt: Long): Long = endOfDay(createdAt, days = 7)

fun daysLeft(until: Long, now: Long): Int =
    if (until <= now) 0 else ((until - now + DAY_MS - 1) / DAY_MS).toInt()

// ---------------------------------------------------------------------------------------------
// Plans and payment methods
// ---------------------------------------------------------------------------------------------

data class PlanOption(val key: String, val label: String, val price: Long)

val PLANS = listOf(
    PlanOption("30d", "30 days", 1000),
    PlanOption("6m", "6 months", 6000),
    PlanOption("1y", "1 year", 12000)
)

fun planOf(key: String): PlanOption = PLANS.firstOrNull { it.key == key } ?: PLANS[0]

/** New end of a paid period: the plan is added after the current end, or after today. */
fun extendPaid(currentEnd: Long, now: Long, plan: String): Long {
    val base = if (currentEnd > now) currentEnd else now
    return when (plan) {
        "30d" -> endOfDay(base, days = 30)
        "6m" -> endOfDay(base, months = 6)
        else -> endOfDay(base, years = 1)
    }
}

fun extendDays(currentEnd: Long, now: Long, days: Int): Long {
    val base = if (currentEnd > now) currentEnd else now
    return endOfDay(base, days = days)
}

data class PayMethod(val key: String, val label: String, val color: Color)

val METHODS = listOf(
    PayMethod("bkash", "bKash", Color(0xFFE2136E)),
    PayMethod("nagad", "Nagad", Color(0xFFE8312A)),
    PayMethod("rocket", "Rocket", Color(0xFF8C3494))
)

fun methodOf(key: String): PayMethod = METHODS.firstOrNull { it.key == key } ?: METHODS[0]

/** Transaction IDs are stored in capital letters; only letters and digits are allowed. */
fun normalizeTrx(raw: String): String? {
    val t = raw.trim().uppercase(Locale.US)
    return if (Regex("[A-Z0-9]{6,24}").matches(t)) t else null
}

fun validNumber(raw: String): Boolean = Regex("01[0-9]{9}").matches(raw.trim())

// ---------------------------------------------------------------------------------------------
// Who may see what
// ---------------------------------------------------------------------------------------------

data class Profile(
    val loaded: Boolean = false,
    val createdAt: Long = 0L,
    val paidUntil: Long = 0L,
    val bonusUntil: Long = 0L,
    val terminated: Boolean = false,
    val totalPaid: Long = 0L
)

/** [phase] is "guest", "trial", "premium" or "expired". */
data class Access(
    val signedIn: Boolean,
    val terminated: Boolean,
    val phase: String,
    val until: Long,
    val daysLeft: Int
) {
    val canView: Boolean get() = signedIn && !terminated && (phase == "trial" || phase == "premium")
    val premium: Boolean get() = phase == "premium"
}

fun computeAccess(user: AuthUser?, p: Profile, now: Long): Access {
    if (user == null) return Access(false, false, "guest", 0L, 0)
    if (user.isAdmin) return Access(true, false, "premium", Long.MAX_VALUE, 9999)
    // Until the profile has arrived, do not flash a lock
    if (!p.loaded) return Access(true, false, "trial", 0L, 0)
    if (p.terminated) return Access(true, true, "expired", 0L, 0)
    val paid = maxOf(p.paidUntil, p.bonusUntil)
    val tEnd = trialEnd(p.createdAt)
    return when {
        paid >= now -> Access(true, false, "premium", paid, daysLeft(paid, now))
        tEnd >= now -> Access(true, false, "trial", tEnd, daysLeft(tEnd, now))
        else -> Access(true, false, "expired", maxOf(paid, tEnd), 0)
    }
}

// ---------------------------------------------------------------------------------------------
// Notifications (shown while the app process is running)
// ---------------------------------------------------------------------------------------------

object Notifier {
    private const val CHANNEL = "alerts"

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun show(ctx: Context, title: String, text: String) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pending = if (launch != null) {
                PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                null
            }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
        } catch (e: Exception) {
            // a notification is never worth a crash
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------

data class InboxMessage(val id: String, val to: String, val title: String, val body: String, val createdAt: Long)

data class ThreadMsg(
    val id: String,
    val uid: String,
    val email: String,
    val name: String,
    val subject: String,
    val body: String,
    val createdAt: Long,
    val reply: String,
    val repliedAt: Long
)

data class Payment(
    val id: String,
    val uid: String,
    val email: String,
    val name: String,
    val method: String,
    val plan: String,
    val amount: Long,
    val sender: String,
    val trxId: String,
    val status: String,
    val createdAt: Long,
    val submittedAt: Long,
    val activatedAt: Long,
    val grantedPlan: String,
    val paidAmount: Long
)

data class PayReport(
    val id: String,
    val uid: String,
    val email: String,
    val name: String,
    val paymentId: String,
    val method: String,
    val paidAt: Long,
    val trxId: String,
    val createdAt: Long
)

data class UserRow(
    val uid: String,
    val email: String,
    val name: String,
    val createdAt: Long,
    val paidUntil: Long,
    val bonusUntil: Long,
    val terminated: Boolean,
    val totalPaid: Long
)

private fun DocumentSnapshot.ms(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

private fun DocumentSnapshot.toMessage() = InboxMessage(
    id, getString("to") ?: "", getString("title") ?: "", getString("body") ?: "", ms("createdAt")
)

private fun DocumentSnapshot.toThread() = ThreadMsg(
    id, getString("uid") ?: "", getString("email") ?: "", getString("name") ?: "",
    getString("subject") ?: "", getString("body") ?: "", ms("createdAt"),
    getString("reply") ?: "", ms("repliedAt")
)

private fun DocumentSnapshot.toPayment() = Payment(
    id, getString("uid") ?: "", getString("email") ?: "", getString("name") ?: "",
    getString("method") ?: "bkash", getString("plan") ?: "30d", getLong("amount") ?: 0L,
    getString("sender") ?: "", getString("trxId") ?: "", getString("status") ?: "pending",
    ms("createdAt"), ms("submittedAt"), ms("activatedAt"),
    getString("grantedPlan") ?: "", getLong("paidAmount") ?: 0L
)

private fun DocumentSnapshot.toReport() = PayReport(
    id, getString("uid") ?: "", getString("email") ?: "", getString("name") ?: "",
    getString("paymentId") ?: "", getString("method") ?: "bkash", ms("paidAt"),
    getString("trxId") ?: "", ms("createdAt")
)

private fun DocumentSnapshot.toUserRow() = UserRow(
    id, getString("email") ?: "", getString("name") ?: "", ms("createdAt"),
    ms("paidUntil"), ms("bonusUntil"), getBoolean("terminated") == true, getLong("totalPaid") ?: 0L
)

fun profileOf(d: DocumentSnapshot): Profile = Profile(
    loaded = true,
    createdAt = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
    paidUntil = d.getTimestamp("paidUntil")?.toDate()?.time ?: 0L,
    bonusUntil = d.getTimestamp("bonusUntil")?.toDate()?.time ?: 0L,
    terminated = d.getBoolean("terminated") == true,
    totalPaid = d.getLong("totalPaid") ?: 0L
)

// ---------------------------------------------------------------------------------------------
// The repository: messages, contact threads, payments, payment reports and (for the admin) users
// ---------------------------------------------------------------------------------------------

class AccountRepo(private val ctx: Context) {
    private val ready = FirebaseApp.getApps(ctx).isNotEmpty()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val prefs = ctx.getSharedPreferences("account", Context.MODE_PRIVATE)

    var msgAll by mutableStateOf<List<InboxMessage>>(emptyList())
        private set
    var msgMine by mutableStateOf<List<InboxMessage>>(emptyList())
        private set
    var threads by mutableStateOf<List<ThreadMsg>>(emptyList())
        private set
    var payments by mutableStateOf<List<Payment>>(emptyList())
        private set
    var reports by mutableStateOf<List<PayReport>>(emptyList())
        private set
    var users by mutableStateOf<List<UserRow>>(emptyList())
        private set
    var seenAt by mutableLongStateOf(0L)
        private set

    private var uid: String = ""
    private var admin = false
    private val regs = mutableListOf<ListenerRegistration>()

    val messages: List<InboxMessage>
        get() = (msgAll + msgMine).sortedByDescending { it.createdAt }

    fun unread(joinedAt: Long): List<InboxMessage> {
        val after = maxOf(seenAt, joinedAt)
        return messages.filter { it.createdAt > after }.sortedBy { it.createdAt }
    }

    fun markSeen(upTo: Long) {
        seenAt = maxOf(seenAt, upTo)
        if (uid.isNotEmpty()) prefs.edit().putLong("seen_$uid", seenAt).apply()
    }

    /** True the first time it is asked on a given day for [key]. */
    fun firstTimeToday(key: String, now: Long): Boolean {
        val k = "daily_$key"
        val today = dayStamp(now)
        if (prefs.getString(k, "") == today) return false
        prefs.edit().putString(k, today).apply()
        return true
    }

    val myThreads: List<ThreadMsg> get() = threads.filter { it.uid == uid }.sortedByDescending { it.createdAt }
    val myPayments: List<Payment> get() = payments.filter { it.uid == uid }.sortedByDescending { it.createdAt }
    val myReports: List<PayReport> get() = reports.filter { it.uid == uid }

    fun pendingFor(method: String): Payment? =
        myPayments.firstOrNull { it.method == method && it.status == "pending" }

    private fun watchMessages(query: Query, set: (List<InboxMessage>) -> Unit) {
        var first = true
        regs += query.addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            set(snap.documents.map { it.toMessage() })
            if (!first && !snap.metadata.hasPendingWrites() && !admin) {
                for (c in snap.documentChanges) {
                    if (c.type == DocumentChange.Type.ADDED) {
                        val m = c.document.toMessage()
                        Notifier.show(ctx, m.title, m.body)
                    }
                }
            }
            first = false
        }
    }

    fun onAuth(user: AuthUser?) {
        regs.forEach { it.remove() }
        regs.clear()
        msgAll = emptyList()
        msgMine = emptyList()
        threads = emptyList()
        payments = emptyList()
        reports = emptyList()
        users = emptyList()
        uid = user?.uid ?: ""
        admin = user?.isAdmin == true
        if (!ready || user == null) return
        seenAt = prefs.getLong("seen_" + user.uid, 0L)

        watchMessages(db.collection("messages").whereEqualTo("to", "all")) { msgAll = it }
        watchMessages(db.collection("messages").whereEqualTo("to", user.uid)) { msgMine = it }

        val threadQ: Query = if (admin) db.collection("threads") else db.collection("threads").whereEqualTo("uid", user.uid)
        val payQ: Query = if (admin) db.collection("payments") else db.collection("payments").whereEqualTo("uid", user.uid)
        val repQ: Query = if (admin) db.collection("reports") else db.collection("reports").whereEqualTo("uid", user.uid)
        regs += threadQ.addSnapshotListener { s, _ -> if (s != null) threads = s.documents.map { it.toThread() } }
        regs += payQ.addSnapshotListener { s, _ -> if (s != null) payments = s.documents.map { it.toPayment() } }
        regs += repQ.addSnapshotListener { s, _ -> if (s != null) reports = s.documents.map { it.toReport() } }
        if (admin) {
            regs += db.collection("users").addSnapshotListener { s, _ ->
                if (s != null) users = s.documents.map { it.toUserRow() }.sortedByDescending { it.createdAt }
            }
        }
    }

    fun findUser(uid: String): UserRow? = users.firstOrNull { it.uid == uid }
    fun findPayment(id: String): Payment? = payments.firstOrNull { it.id == id }

    // ---- contact messages ----
    fun sendThread(user: AuthUser, subject: String, body: String, onDone: (Boolean) -> Unit) {
        val data = hashMapOf<String, Any>(
            "uid" to user.uid,
            "email" to user.email,
            "name" to user.name,
            "subject" to subject,
            "body" to body,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("threads").add(data).addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun replyThread(t: ThreadMsg, text: String, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        batch.update(
            db.collection("threads").document(t.id),
            hashMapOf<String, Any>("reply" to text, "repliedAt" to FieldValue.serverTimestamp())
        )
        batch.set(db.collection("messages").document(), messageData(t.uid, "Re: " + t.subject, text))
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun broadcast(title: String, body: String, onDone: (Boolean) -> Unit) {
        db.collection("messages").add(messageData("all", title, body))
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    private fun messageData(to: String, title: String, body: String) = hashMapOf<String, Any>(
        "to" to to,
        "title" to title,
        "body" to body,
        "createdAt" to FieldValue.serverTimestamp()
    )

    // ---- payments ----
    fun submitPayment(
        user: AuthUser, method: String, plan: String, sender: String, trxRaw: String,
        onDone: (String?) -> Unit
    ) {
        val trx = normalizeTrx(trxRaw)
        if (trx == null) {
            onDone("Enter a valid Transaction ID (letters and digits, 6 to 24 characters).")
            return
        }
        if (!validNumber(sender)) {
            onDone("Enter your 11 digit mobile number, for example 01XXXXXXXXX.")
            return
        }
        val trxRef = db.collection("trxIds").document(trx)
        trxRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                onDone("This Transaction ID already exists.")
                return@addOnSuccessListener
            }
            val payRef = db.collection("payments").document()
            val batch = db.batch()
            batch.set(
                payRef,
                hashMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to user.email,
                    "name" to user.name,
                    "method" to method,
                    "plan" to plan,
                    "amount" to planOf(plan).price,
                    "sender" to sender.trim(),
                    "trxId" to trx,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "submittedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(trxRef, hashMapOf<String, Any>("uid" to user.uid, "paymentId" to payRef.id))
            batch.commit().addOnCompleteListener {
                onDone(if (it.isSuccessful) null else "Could not submit. This Transaction ID may already exist, or check your connection.")
            }
        }.addOnFailureListener {
            onDone("Could not check the Transaction ID. Check your internet connection.")
        }
    }

    /** Allowed for one hour after submitting; every edit starts a new hour. */
    fun editPayment(
        p: Payment, method: String, plan: String, sender: String, trxRaw: String,
        onDone: (String?) -> Unit
    ) {
        val trx = normalizeTrx(trxRaw)
        if (trx == null) {
            onDone("Enter a valid Transaction ID (letters and digits, 6 to 24 characters).")
            return
        }
        if (!validNumber(sender)) {
            onDone("Enter your 11 digit mobile number, for example 01XXXXXXXXX.")
            return
        }
        val payRef = db.collection("payments").document(p.id)
        val data = hashMapOf<String, Any>(
            "method" to method,
            "plan" to plan,
            "amount" to planOf(plan).price,
            "sender" to sender.trim(),
            "trxId" to trx,
            "submittedAt" to FieldValue.serverTimestamp()
        )
        fun commit() {
            val batch = db.batch()
            batch.update(payRef, data)
            if (trx != p.trxId) {
                batch.delete(db.collection("trxIds").document(p.trxId))
                batch.set(
                    db.collection("trxIds").document(trx),
                    hashMapOf<String, Any>("uid" to p.uid, "paymentId" to p.id)
                )
            }
            batch.commit().addOnCompleteListener {
                onDone(if (it.isSuccessful) null else "Could not save. The edit time may be over, or the ID already exists.")
            }
        }
        if (trx == p.trxId) {
            commit()
        } else {
            db.collection("trxIds").document(trx).get().addOnSuccessListener { snap ->
                if (snap.exists()) onDone("This Transaction ID already exists.") else commit()
            }.addOnFailureListener { onDone("Could not check the Transaction ID. Check your internet connection.") }
        }
    }

    fun submitReport(
        user: AuthUser, paymentId: String, method: String, paidAt: Long, trxId: String,
        onDone: (Boolean) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "uid" to user.uid,
            "email" to user.email,
            "name" to user.name,
            "paymentId" to paymentId,
            "method" to method,
            "paidAt" to Timestamp(Date(paidAt)),
            "trxId" to trxId.trim().uppercase(Locale.US),
            "status" to "waiting",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("reports").add(data).addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // ---- admin: activating a subscription ----
    fun activate(p: Payment, planKey: String, report: PayReport?, onDone: (Boolean) -> Unit) {
        val now = System.currentTimeMillis()
        val row = findUser(p.uid)
        val end = extendPaid(row?.paidUntil ?: 0L, now, planKey)
        val plan = planOf(planKey)
        val batch = db.batch()
        batch.update(
            db.collection("payments").document(p.id),
            hashMapOf<String, Any>(
                "status" to "active",
                "activatedAt" to FieldValue.serverTimestamp(),
                "grantedPlan" to plan.key,
                "paidAmount" to plan.price
            )
        )
        batch.update(
            db.collection("users").document(p.uid),
            hashMapOf<String, Any>(
                "paidUntil" to Timestamp(Date(end)),
                "totalPaid" to FieldValue.increment(plan.price)
            )
        )
        batch.set(
            db.collection("messages").document(),
            messageData(
                p.uid, "Subscription activated",
                "Your subscription was activated successfully for " + plan.label + ". It is active until " + dateOnly(end) + "."
            )
        )
        if (report != null) batch.delete(db.collection("reports").document(report.id))
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Finds the waiting payment that carries this Transaction ID and activates it. */
    fun activateByTrx(trxRaw: String, planKey: String, onDone: (String?) -> Unit) {
        val trx = normalizeTrx(trxRaw)
        if (trx == null) {
            onDone("Enter a valid Transaction ID.")
            return
        }
        val p = payments.firstOrNull { it.trxId == trx && it.status == "pending" }
        if (p == null) {
            val any = payments.firstOrNull { it.trxId == trx }
            onDone(if (any != null) "That payment is already " + any.status + "." else "No user has submitted this Transaction ID yet.")
            return
        }
        val report = reports.firstOrNull { it.paymentId == p.id }
        activate(p, planKey, report) { ok -> onDone(if (ok) null else "Could not activate. Check your internet connection.") }
    }

    fun decline(p: Payment?, report: PayReport, note: String, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        if (p != null && p.status == "pending") {
            batch.update(db.collection("payments").document(p.id), hashMapOf<String, Any>("status" to "declined"))
        }
        val text = if (note.isBlank()) "Your payment could not be confirmed." else note.trim()
        batch.set(db.collection("messages").document(), messageData(report.uid, "Payment report declined", text))
        batch.delete(db.collection("reports").document(report.id))
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Declines a waiting payment (no report needed) and tells the user why. */
    fun declinePayment(p: Payment, note: String, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        batch.update(db.collection("payments").document(p.id), hashMapOf<String, Any>("status" to "declined"))
        val text = if (note.isBlank()) "Your payment could not be confirmed." else note.trim()
        batch.set(db.collection("messages").document(), messageData(p.uid, "Payment declined", text))
        reports.firstOrNull { it.paymentId == p.id }?.let { batch.delete(db.collection("reports").document(it.id)) }
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Responds to a report by terminating the account (the report is removed). */
    fun terminateFromReport(report: PayReport, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        batch.update(db.collection("users").document(report.uid), hashMapOf<String, Any>("terminated" to true))
        batch.delete(db.collection("reports").document(report.id))
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    // ---- admin: users ----
    fun setTerminated(u: UserRow, value: Boolean, onDone: (Boolean) -> Unit) {
        db.collection("users").document(u.uid).update("terminated", value)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Extra free days for the chosen people; they add to any bonus they already have. */
    fun grantBonus(list: List<UserRow>, days: Int, onDone: (Boolean) -> Unit) {
        val now = System.currentTimeMillis()
        val batch = db.batch()
        for (u in list) {
            batch.update(
                db.collection("users").document(u.uid),
                hashMapOf<String, Any>("bonusUntil" to Timestamp(Date(extendDays(u.bonusUntil, now, days))))
            )
        }
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun removeBonus(list: List<UserRow>, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        for (u in list) {
            batch.update(db.collection("users").document(u.uid), hashMapOf<String, Any>("bonusUntil" to FieldValue.delete()))
        }
        batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun setPaymentNumbers(bkash: String, nagad: String, rocket: String, onDone: (Boolean) -> Unit) {
        db.collection("config").document("app")
            .set(
                hashMapOf<String, Any>(
                    "bkashNumber" to bkash.trim(),
                    "nagadNumber" to nagad.trim(),
                    "rocketNumber" to rocket.trim()
                ),
                SetOptions.merge()
            ).addOnCompleteListener { onDone(it.isSuccessful) }
    }
}
