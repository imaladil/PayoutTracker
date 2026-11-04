package com.payouttracker.app

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.util.Date

/** The sports an event can belong to. Tennis is a ball; Table Tennis keeps the racket icon. */
val SPORTS: List<Pair<String, ImageVector>> = listOf(
    "Cricket" to Icons.Filled.SportsCricket,
    "Football" to Icons.Filled.SportsSoccer,
    "Tennis" to TennisBallIcon,
    "Table Tennis" to Icons.Filled.SportsTennis,
    "Basketball" to Icons.Filled.SportsBasketball,
    "Volleyball" to Icons.Filled.SportsVolleyball
)

fun sportIcon(name: String): ImageVector =
    SPORTS.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second ?: Icons.Filled.SportsSoccer

/** One game inside a multi bet. */
data class Leg(
    val sport: String = "Cricket",
    val tournament: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val startAt: Long = 0L,
    val bet: String = "",
    val odds: String = ""
)

/** Odds typed as text, "1.85" or "1,85"; null when it is not a number. */
fun parseOdds(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** All odds multiplied together, shown with 2 decimals; null if any of them is not a number. */
fun totalOddsOf(odds: List<String>): String? {
    if (odds.isEmpty()) return null
    var t = 1.0
    for (o in odds) t *= parseOdds(o)?.takeIf { it > 0 } ?: return null
    return String.format(java.util.Locale.US, "%.2f", t)
}

/**
 * One bet. [status] is "prematch", "live", "won" or "lost". A prematch event whose start time has
 * passed counts as live automatically; the admin can also send it live earlier.
 */
data class BetEvent(
    val id: String = "",
    val sport: String = "Cricket",
    val tournament: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val startAt: Long = 0L,
    val bet: String = "",
    val odds: String = "",
    val code: String = "",
    val status: String = "prematch",
    val createdAt: Long = 0L,
    val settledAt: Long = 0L,
    val betId: String = "",
    /** Two or more games make this a multi bet; empty for a single bet. */
    val legs: List<Leg> = emptyList()
) {
    val isMulti: Boolean get() = legs.size >= 2

    /** The games of this bet: the legs of a multi bet, or the one game of a single bet. */
    val games: List<Leg>
        get() = if (isMulti) legs else listOf(Leg(sport, tournament, teamA, teamB, startAt, bet, odds))

    /** Short text for notifications. */
    val title: String get() = if (isMulti) "Multi bet (" + legs.size + " events)" else teamA + " vs " + teamB

    val settled: Boolean get() = status == "won" || status == "lost"

    /** The unique bet ID. Older bets without a stored ID get a stable one made from their document id. */
    val displayId: String get() = betId.ifBlank { id.take(8).uppercase() }
}

private const val BET_ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** 8 characters, no look-alike letters (no 0/O, 1/I). */
fun newBetId(): String = (1..8).map { BET_ID_CHARS.random() }.joinToString("")

data class StrategyPost(
    val id: String,
    val title: String,
    val body: String,
    val image: String,
    val createdAt: Long
)

private fun DocumentSnapshot.time(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

private fun DocumentSnapshot.toEvent(): BetEvent = BetEvent(
    id = id,
    sport = getString("sport") ?: "",
    tournament = getString("tournament") ?: "",
    teamA = getString("teamA") ?: "",
    teamB = getString("teamB") ?: "",
    startAt = time("startAt"),
    bet = getString("bet") ?: "",
    odds = getString("odds") ?: "",
    code = getString("code") ?: "",
    status = getString("status") ?: "prematch",
    createdAt = time("createdAt"),
    settledAt = time("settledAt"),
    betId = getString("betId") ?: "",
    legs = readLegs()
)

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.readLegs(): List<Leg> {
    val raw = get("legs") as? List<Map<String, Any?>> ?: return emptyList()
    return raw.map { m ->
        Leg(
            sport = m["sport"] as? String ?: "",
            tournament = m["tournament"] as? String ?: "",
            teamA = m["teamA"] as? String ?: "",
            teamB = m["teamB"] as? String ?: "",
            startAt = (m["startAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            bet = m["bet"] as? String ?: "",
            odds = m["odds"] as? String ?: ""
        )
    }
}

private fun DocumentSnapshot.toPost(): StrategyPost = StrategyPost(
    id = id,
    title = getString("title") ?: "",
    body = getString("body") ?: "",
    image = getString("image") ?: "",
    createdAt = time("createdAt")
)

/**
 * Live data from the shared database.
 * - Settled results (won/lost) can be read by everyone.
 * - Open events (prematch/live) and strategy posts are read only while signed in.
 */
class EventsRepo(private val ctx: Context) {
    private val ready = FirebaseApp.getApps(ctx).isNotEmpty()

    var settled by mutableStateOf<List<BetEvent>>(emptyList())
        private set
    var open by mutableStateOf<List<BetEvent>>(emptyList())
        private set
    var strategies by mutableStateOf<List<StrategyPost>>(emptyList())
        private set
    var now by mutableStateOf(System.currentTimeMillis())
        private set

    private var openLoaded = false
    private var known: Map<String, String> = emptyMap()
    private val liveSeen = mutableSetOf<String>()

    private var settledReg: ListenerRegistration? = null
    private var openReg: ListenerRegistration? = null
    private var strategyReg: ListenerRegistration? = null

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    init {
        if (ready) {
            settledReg = db.collection("events").whereEqualTo("settled", true)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) settled = snap.documents.map { it.toEvent() }
                }
        }
    }

    /** Called whenever sign-in changes: open events and strategies need a signed-in person. */
    fun onAuth(signedIn: Boolean) {
        if (!ready) return
        openReg?.remove()
        strategyReg?.remove()
        openReg = null
        strategyReg = null
        openLoaded = false
        known = emptyMap()
        liveSeen.clear()
        if (!signedIn) {
            open = emptyList()
            strategies = emptyList()
            return
        }
        openReg = db.collection("events").whereEqualTo("settled", false)
            .addSnapshotListener { snap, _ ->
                if (snap != null) handleOpen(snap.documents.map { it.toEvent() }, snap.metadata.hasPendingWrites())
            }
        strategyReg = db.collection("strategies")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ ->
                if (snap != null) strategies = snap.documents.map { it.toPost() }
            }
    }

    /** Updates the list of open events and tells the person about new or newly live events. */
    private fun handleOpen(list: List<BetEvent>, pending: Boolean) {
        if (openLoaded && !pending) {
            for (e in list) {
                val before = known[e.id]
                if (before == null) {
                    Notifier.show(ctx, "New event", if (e.isMulti) e.title else e.title + " (" + e.sport + ")")
                } else if (before != "live" && e.status == "live" && e.id !in liveSeen) {
                    Notifier.show(ctx, "Now live", e.title)
                }
            }
        }
        liveSeen.addAll(list.filter { it.status == "live" || it.startAt <= now }.map { it.id })
        known = list.associate { it.id to it.status }
        openLoaded = true
        open = list
    }

    fun tick() {
        now = System.currentTimeMillis()
        if (!openLoaded) return
        for (e in open) {
            if (e.status == "prematch" && e.startAt in 1..now && e.id !in liveSeen) {
                liveSeen.add(e.id)
                Notifier.show(ctx, "Now live", e.title)
            }
        }
    }

    // ---- derived lists ----
    val liveEvents: List<BetEvent>
        get() = open.filter { it.status == "live" || it.startAt <= now }.sortedBy { it.startAt }
    val preMatchEvents: List<BetEvent>
        get() = open.filter { it.status != "live" && it.startAt > now }.sortedBy { it.startAt }
    val results: List<BetEvent>
        get() = settled.sortedByDescending { it.settledAt }

    val total: Int get() = open.size + settled.size
    val unsettled: Int get() = open.size
    val won: Int get() = settled.count { it.status == "won" }
    val lost: Int get() = settled.count { it.status == "lost" }

    /** Won out of all settled bets, as a whole percentage; 0 when nothing is settled yet. */
    val successRate: Int get() = if (won + lost == 0) 0 else Math.round(won * 100f / (won + lost))

    /** Admin only: every event, newest start first. */
    val allEvents: List<BetEvent>
        get() = (open + settled).sortedByDescending { it.startAt }

    fun findEvent(id: String?): BetEvent? = (open + settled).firstOrNull { it.id == id }
    fun findPost(id: String?): StrategyPost? = strategies.firstOrNull { it.id == id }

    // ---- admin writes (the database rules allow them only for the admin account) ----
    fun saveEvent(e: BetEvent, onResult: (Boolean) -> Unit) {
        val data = hashMapOf<String, Any>(
            "sport" to e.sport,
            "tournament" to e.tournament,
            "teamA" to e.teamA,
            "teamB" to e.teamB,
            "startAt" to Timestamp(Date(e.startAt)),
            "bet" to e.bet,
            "odds" to e.odds,
            "code" to e.code,
            "status" to e.status,
            "multi" to e.isMulti,
            "legs" to e.legs.map { l ->
                hashMapOf<String, Any>(
                    "sport" to l.sport,
                    "tournament" to l.tournament,
                    "teamA" to l.teamA,
                    "teamB" to l.teamB,
                    "startAt" to Timestamp(Date(l.startAt)),
                    "bet" to l.bet,
                    "odds" to l.odds
                )
            }
        )
        if (e.id.isEmpty()) {
            createWithUniqueId(data, 5, onResult)
        } else {
            // editing keeps the bet ID that was given when the bet was created
            db.collection("events").document(e.id).update(data)
                .addOnCompleteListener { onResult(it.isSuccessful) }
        }
    }

    /** A new bet gets a unique bet ID, which is also its document id; a taken ID is never reused. */
    private fun createWithUniqueId(data: HashMap<String, Any>, tries: Int, onResult: (Boolean) -> Unit) {
        val betId = newBetId()
        val ref = db.collection("events").document(betId)
        db.runTransaction { tx ->
            if (tx.get(ref).exists()) throw IllegalStateException("taken")
            val doc = HashMap(data)
            doc["betId"] = betId
            doc["settled"] = false
            doc["createdAt"] = FieldValue.serverTimestamp()
            tx.set(ref, doc)
            null
        }.addOnCompleteListener {
            when {
                it.isSuccessful -> onResult(true)
                tries > 1 && (it.exception is IllegalStateException || it.exception?.cause is IllegalStateException) -> createWithUniqueId(data, tries - 1, onResult)
                else -> onResult(false)
            }
        }
    }

    fun setStatus(id: String, status: String, onResult: (Boolean) -> Unit) {
        val data = hashMapOf<String, Any>("status" to status)
        if (status == "won" || status == "lost") {
            data["settled"] = true
            data["settledAt"] = FieldValue.serverTimestamp()
        } else {
            data["settled"] = false
        }
        db.collection("events").document(id).update(data)
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun deleteEvent(id: String, onResult: (Boolean) -> Unit) {
        db.collection("events").document(id).delete()
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun savePost(title: String, body: String, image: String, onResult: (Boolean) -> Unit) {
        val data = hashMapOf<String, Any>(
            "title" to title,
            "body" to body,
            "image" to image,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("strategies").add(data).addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun deletePost(id: String, onResult: (Boolean) -> Unit) {
        db.collection("strategies").document(id).delete()
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun setMinVersion(code: Long, onResult: (Boolean) -> Unit) {
        db.collection("config").document("app")
            .set(hashMapOf<String, Any>("minVersionCode" to code), SetOptions.merge())
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }
}

/** Every word of [query] must appear somewhere in the event's text, including its unique bet ID. */
fun BetEvent.matches(query: String): Boolean {
    val words = query.trim().lowercase().replace("#", "").split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = (listOf(sport, tournament, teamA, teamB, bet, odds, code, status, displayId) +
        legs.flatMap { listOf(it.sport, it.tournament, it.teamA, it.teamB, it.bet, it.odds) } +
        (if (isMulti) listOf("multi") else listOf("single")))
        .joinToString(" ").lowercase()
    return words.all { text.contains(it) }
}


/**
 * Everything typed into the event form before: tournaments, teams or players, and bets.
 * They come from every event still in the database plus a copy kept on this phone, so a name
 * stays available after its event is deleted.
 */
class SuggestStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("suggest", Context.MODE_PRIVATE)

    private fun saved(kind: String): Set<String> = prefs.getStringSet(kind, emptySet()) ?: emptySet()

    fun remember(e: BetEvent) {
        val teams = saved("team").toMutableSet()
        val tours = saved("tournament").toMutableSet()
        val bets = saved("bet").toMutableSet()
        for (g in e.games) {
            listOf(g.teamA, g.teamB).filter { it.isNotBlank() }.forEach { teams.add(it) }
            if (g.tournament.isNotBlank()) tours.add(g.tournament)
            if (g.bet.isNotBlank()) bets.add(g.bet)
        }
        prefs.edit().putStringSet("team", teams).putStringSet("tournament", tours).putStringSet("bet", bets).apply()
    }

    fun teams(all: List<BetEvent>): List<String> =
        (saved("team") + all.flatMap { ev -> ev.games.flatMap { listOf(it.teamA, it.teamB) } }).clean()

    fun tournaments(all: List<BetEvent>): List<String> =
        (saved("tournament") + all.flatMap { ev -> ev.games.map { it.tournament } }).clean()

    fun bets(all: List<BetEvent>): List<String> =
        (saved("bet") + all.flatMap { ev -> ev.games.map { it.bet } }).clean()

    private fun Collection<String>.clean(): List<String> =
        map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}
