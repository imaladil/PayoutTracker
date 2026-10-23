package com.payouttracker.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max

private const val DAY_MS = 24L * 60L * 60L * 1000L

// ------------------------------------------------------------------
// Summary: last 7 days, last 30 days and overall target achievement
// ------------------------------------------------------------------
@Composable
fun SummaryScreen(state: TrackerState, targetId: Long?, onBack: () -> Unit, onMenu: () -> Unit) {
    // targetId == null means all targets combined
    val single = state.findTarget(targetId)
    val rows = single?.rows() ?: state.allRows()
    val now = System.currentTimeMillis()
    val last7 = rows.filter { it.time >= now - 7 * DAY_MS }
    val last30 = rows.filter { it.time >= now - 30 * DAY_MS }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Summary", onBack, onMenu)
        ScopeLabel(single?.name ?: "All Targets (Combined)")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PeriodCard("Last 7 Days", last7, SummaryColors[0])
            PeriodCard("Last 30 Days", last30, MessageColors[0])
            if (single != null) {
                TargetCard(
                    title = "Target Achievement",
                    target = single.amount,
                    achieved = minOf(single.balance, single.amount),
                    remaining = single.remaining,
                    fulfilled = if (single.isFulfilled) 1 else 0,
                    count = 0
                )
            } else {
                TargetCard(
                    title = "Target Achievement (All Targets)",
                    target = state.totalTarget,
                    achieved = state.totalAchieved,
                    remaining = state.totalRemaining,
                    fulfilled = state.fulfilledCount,
                    count = state.targets.size
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeriodCard(title: String, list: List<ChainRow>, accent: Color) {
    val avgStake = avgOf(list.map { it.stake })
    val avgOdds = avgOf(list.map { it.odds })
    val avgProfit = avgOf(list.map { it.profit })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Pill(if (list.size == 1) "1 stake" else "${list.size} stakes")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Avg Stake", avgText(avgStake), White, Modifier.weight(1f))
                StatTile("Avg Odds", if (avgOdds == null) "—" else plain(avgOdds), White, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "Avg Profit",
                    if (avgProfit == null) "—" else signed(avgProfit),
                    if (avgProfit == null || avgProfit >= 0) Green else Red,
                    Modifier.weight(1f)
                )
                StatTile(
                    "Total Profit",
                    if (list.isEmpty()) "—" else signed(list.sumOf { it.profit }),
                    if (list.sumOf { it.profit } >= 0) Green else Red,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TargetCard(
    title: String,
    target: Double,
    achieved: Double,
    remaining: Double,
    fulfilled: Int,
    count: Int
) {
    val hasTarget = target > 0
    val ratio = if (hasTarget) achieved / target else 0.0
    val label = if (hasTarget) String.format(Locale.US, "%.0f%%", ratio * 100) else "--"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = Green,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (count > 0) Pill("$fulfilled/$count done") else if (fulfilled > 0) Pill("✓ Fulfilled")
            }
            Spacer(Modifier.height(14.dp))
            SemiGauge(
                progress = ratio.toFloat(),
                label = label,
                modifier = Modifier
                    .width(200.dp)
                    .height(112.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total Target", if (hasTarget) money(target) else "Not set", White, Modifier.weight(1f))
                StatTile("Achieved", money(achieved), Green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            StatTile(
                "Remaining",
                if (hasTarget) money(remaining) else "Set a Payout Target first",
                White,
                Modifier.fillMaxWidth()
            )
        }
    }
}

// ------------------------------------------------------------------
// Brief Report: overall averages + one card per day
// ------------------------------------------------------------------
@Composable
fun ReportScreen(state: TrackerState, targetId: Long?, onBack: () -> Unit, onMenu: () -> Unit) {
    // targetId == null means all targets combined
    val single = state.findTarget(targetId)
    val rows = single?.rows() ?: state.allRows()
    val ctx = LocalContext.current
    // Lets the user pick where to save the PDF, so no storage permission is needed
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && single != null) {
            val saved = try {
                ctx.contentResolver.openOutputStream(uri)?.use { writeReportPdf(single, it) } != null
            } catch (e: Exception) {
                false
            }
            Toast.makeText(ctx, if (saved) "PDF exported" else "Could not export the PDF", Toast.LENGTH_SHORT).show()
        }
    }
    val byDay = rows.groupBy { dayKey(it.time) }
    val dayList = byDay.entries.toList().reversed()   // newest day first

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Brief Report", onBack, onMenu)
        ScopeLabel(single?.name ?: "All Targets (Combined)")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The export is offered only once the target is fulfilled
            if (single != null && single.isFulfilled) {
                item {
                    ExportCard(
                        onClick = {
                            exportLauncher.launch("Payout_Target_" + amountText(single.amount) + "_Report.pdf")
                        }
                    )
                }
            }
            item { OverallCard(rows, byDay.size) }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "No entries yet. Your daily report will appear here.",
                        color = Gray,
                        fontSize = 13.sp
                    )
                }
            }
            items(dayList) { entry -> DayCard(entry.value) }
        }
    }
}

@Composable
private fun OverallCard(rows: List<ChainRow>, activeDays: Int) {
    val avgStake = avgOf(rows.map { it.stake })
    val avgOdds = avgOf(rows.map { it.odds })
    val avgProfit = avgOf(rows.map { it.profit })
    val perDay = if (activeDays == 0) null else rows.size.toDouble() / activeDays

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Overall", color = ReportColors[0], fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total Stakes", rows.size.toString(), White, Modifier.weight(1f))
                StatTile("Active Days", activeDays.toString(), White, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Avg Stake (Total)", avgText(avgStake), White, Modifier.weight(1f))
                StatTile("Avg Odds (Total)", if (avgOdds == null) "—" else plain(avgOdds), White, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    "Avg Profit",
                    if (avgProfit == null) "—" else signed(avgProfit),
                    if (avgProfit == null || avgProfit >= 0) Green else Red,
                    Modifier.weight(1f)
                )
                StatTile(
                    "Stakes / Day",
                    if (perDay == null) "—" else String.format(Locale.US, "%.1f", perDay),
                    White,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayCard(list: List<ChainRow>) {
    val avgStake = avgOf(list.map { it.stake })
    val avgProfit = avgOf(list.map { it.profit })
    val avgOdds = avgOf(list.map { it.odds })

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dayLabel(list.first().time),
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Pill(if (list.size == 1) "1 stake" else "${list.size} stakes")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Avg Stake", avgText(avgStake), White, Modifier.weight(1f))
                StatTile(
                    "Avg Profit",
                    if (avgProfit == null) "—" else signed(avgProfit),
                    if (avgProfit == null || avgProfit >= 0) Green else Red,
                    Modifier.weight(1f)
                )
                StatTile("Avg Odds", if (avgOdds == null) "—" else plain(avgOdds), White, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = LineColor)
            list.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${r.no}",
                        color = Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(38.dp)
                    )
                    Text(
                        timeText(r.time),
                        color = Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.width(76.dp)
                    )
                    Text(
                        short(r.stake) + " × " + plain(r.odds),
                        color = White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        signed(r.profit),
                        color = if (r.profit >= 0) Green else Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Text(
                    r.targetName,
                    color = Gray,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 38.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** Small heading under the top bar saying which Payout Target the screen is about. */
@Composable
private fun ScopeLabel(text: String) {
    Text(
        text,
        color = Green,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 10.dp)
    )
}

/** Shown on a fulfilled target's Brief Report: saves the report as a PDF. */
@Composable
private fun ExportCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Target Fulfilled", color = Green, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Save this Brief Report as a PDF", color = White, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)
            ) { Text("Export PDF", fontWeight = FontWeight.SemiBold) }
        }
    }
}
