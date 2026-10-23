package com.payouttracker.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

/** One recorded bet: when it was placed and the odds that were entered. */
data class Entry(val time: Long, val odds: Double)

/** One computed table row of a Payout Target. [progress] is paidOut / target amount. */
data class ChainRow(
    val no: Int,
    val time: Long,
    val stake: Double,
    val odds: Double,
    val profit: Double,
    val paidOut: Double,
    val progress: Double,
    val targetName: String
)

/**
 * One Payout Target. Every stake after the first one is the whole previous paid out
 * (1000 x 1.1 = 1100, 1100 x 1.1 = 1210, ...), so only the first stake and the odds are stored.
 */
class TargetData(val id: Long, val amount: Double, val openedAt: Long) {
    var firstStake by mutableStateOf<Double?>(null)
    val entries = mutableStateListOf<Entry>()

    val name: String get() = targetName(amount)

    fun rows(): List<ChainRow> {
        val first = firstStake ?: return emptyList()
        val out = ArrayList<ChainRow>()
        var stake = first
        entries.forEachIndexed { i, e ->
            val paid = stake * e.odds
            out.add(
                ChainRow(
                    no = i + 1,
                    time = e.time,
                    stake = stake,
                    odds = e.odds,
                    profit = paid - stake,
                    paidOut = paid,
                    progress = paid / amount,
                    targetName = name
                )
            )
            stake = paid
        }
        return out
    }

    /** Latest paid out = how much of the target has been reached. */
    val balance: Double get() = rows().lastOrNull()?.paidOut ?: 0.0
    val profit: Double get() = balance - (firstStake ?: 0.0)
    val ratio: Double get() = balance / amount
    val remaining: Double get() = max(amount - balance, 0.0)
    val exceeded: Double get() = max(balance - amount, 0.0)
    val isFulfilled: Boolean get() = entries.isNotEmpty() && balance >= amount
}

private const val PREFS = "payout_prefs"
private const val KEY_TARGETS = "targets_v4"
private const val KEY_LEGACY_TARGET = "target_v1"
private const val KEY_LEGACY_V3 = "chain_v3"
private const val KEY_LEGACY_V2 = "chain_v2"

/** Holds all Payout Targets and saves them on the device. */
class TrackerState(private val ctx: Context) {
    val targets = mutableStateListOf<TargetData>()

    init {
        load()
    }

    fun findTarget(id: Long?): TargetData? = targets.firstOrNull { it.id == id }

    fun addTarget(amount: Double): TargetData {
        val now = System.currentTimeMillis()
        var id = now
        while (targets.any { it.id == id }) id++
        val t = TargetData(id, amount, now)
        targets.add(t)
        save()
        return t
    }

    fun deleteTarget(id: Long) {
        targets.removeAll { it.id == id }
        save()
    }

    /** Adds a stake to [t]. A fulfilled target does not take new entries. */
    fun addEntry(t: TargetData, stake: Double?, odds: Double): Boolean {
        if (t.isFulfilled) return false
        if (t.entries.isEmpty()) {
            if (stake == null) return false
            t.firstStake = stake
        }
        t.entries.add(Entry(System.currentTimeMillis(), odds))
        save()
        return true
    }

    /** Removes the newest entry. Returns the first stake when the list became empty, so the field can be refilled. */
    fun undoLast(t: TargetData): Double? {
        if (t.entries.isEmpty()) return null
        t.entries.removeAt(t.entries.lastIndex)
        var restore: Double? = null
        if (t.entries.isEmpty()) {
            restore = t.firstStake
            t.firstStake = null
        }
        save()
        return restore
    }

    fun resetTarget(t: TargetData) {
        t.entries.clear()
        t.firstStake = null
        save()
    }

    fun allRows(): List<ChainRow> = targets.flatMap { it.rows() }.sortedBy { it.time }

    // ---- Totals across every target (used by the Summary screen) ----
    val totalTarget: Double get() = targets.sumOf { it.amount }
    val totalAchieved: Double get() = targets.sumOf { minOf(it.balance, it.amount) }
    val totalRemaining: Double get() = targets.sumOf { it.remaining }
    val fulfilledCount: Int get() = targets.count { it.isFulfilled }

    // ---- Storage ----
    private fun prefs() = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save() {
        val sb = StringBuilder()
        for (t in targets) {
            sb.append("T|").append(t.id).append('|').append(t.amount).append('|')
                .append(t.openedAt).append('|').append(t.firstStake?.toString() ?: "").append('\n')
            for (e in t.entries) {
                sb.append("E|").append(e.time).append('|').append(e.odds).append('\n')
            }
        }
        prefs().edit()
            .putString(KEY_TARGETS, sb.toString())
            .remove(KEY_LEGACY_TARGET)
            .remove(KEY_LEGACY_V3)
            .remove(KEY_LEGACY_V2)
            .apply()
    }

    private fun load() {
        val sp = prefs()
        val raw = sp.getString(KEY_TARGETS, null)
        if (raw != null) {
            parse(raw)
        } else {
            migrateOldVersion(sp)
        }
    }

    private fun parse(raw: String) {
        var current: TargetData? = null
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val p = line.split("|")
            when (p[0]) {
                "T" -> {
                    val id = p.getOrNull(1)?.toLongOrNull()
                    val amount = p.getOrNull(2)?.toDoubleOrNull()
                    val opened = p.getOrNull(3)?.toLongOrNull()
                    if (id != null && amount != null && amount > 0 && opened != null) {
                        val t = TargetData(id, amount, opened)
                        t.firstStake = p.getOrNull(4)?.toDoubleOrNull()
                        targets.add(t)
                        current = t
                    } else {
                        current = null
                    }
                }
                "E" -> {
                    val t = current
                    val time = p.getOrNull(1)?.toLongOrNull()
                    val odds = p.getOrNull(2)?.toDoubleOrNull()
                    if (t != null && time != null && odds != null) t.entries.add(Entry(time, odds))
                }
            }
        }
    }

    /** The earlier version had a single chain and a single target; keep it as the first Payout Target. */
    private fun migrateOldVersion(sp: SharedPreferences) {
        val oldTarget = sp.getString(KEY_LEGACY_TARGET, "")?.toDoubleOrNull() ?: 0.0
        var raw = sp.getString(KEY_LEGACY_V3, "") ?: ""
        if (raw.isBlank()) raw = sp.getString(KEY_LEGACY_V2, "") ?: ""
        val lines = raw.lines().filter { it.isNotBlank() }
        val first = lines.firstOrNull()?.toDoubleOrNull()
        if (first != null && oldTarget > 0) {
            val now = System.currentTimeMillis()
            val list = ArrayList<Entry>()
            for (line in lines.drop(1)) {
                val p = line.split(",")
                if (p.size >= 2) {
                    val time = p[0].toLongOrNull()
                    val odds = p[1].toDoubleOrNull()
                    if (time != null && odds != null) list.add(Entry(time, odds))
                } else {
                    p[0].toDoubleOrNull()?.let { list.add(Entry(now, it)) }
                }
            }
            val t = TargetData(now, oldTarget, list.firstOrNull()?.time ?: now)
            t.firstStake = first
            t.entries.addAll(list)
            targets.add(t)
        }
        save()
    }
}
