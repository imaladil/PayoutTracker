package com.example.payouttracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Stake / odds entry for one Payout Target, with its table, progress gauge and bottom bar.
 * The round bottom-center button is named after the target and switches to the next one.
 */
@Composable
fun TargetDetailScreen(
    state: TrackerState,
    target: TargetData,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onSummary: () -> Unit,
    onReport: () -> Unit,
    onContactMessage: () -> Unit,
    onContactGroup: () -> Unit,
    onNextTarget: () -> Unit
) {
    var stakeText by remember(target.id) { mutableStateOf("") }
    var oddsText by remember(target.id) { mutableStateOf("") }
    var showReset by remember(target.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val rows = target.rows()
    val balance = rows.lastOrNull()?.paidOut ?: 0.0
    val fulfilled = target.isFulfilled
    val chained = rows.isNotEmpty()

    LaunchedEffect(target.id, rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp)
        ) {
            // Header: back + target name + status
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
                }
                Text(
                    target.name,
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Pill(if (fulfilled) "✓ Fulfilled" else "Active")
            }

            Spacer(Modifier.height(6.dp))

            TopSummaryCard(target)

            Spacer(Modifier.height(12.dp))

            if (fulfilled) {
                FulfilledBanner(target)
            } else {
                val oddsVal = oddsText.toDoubleOrNull()
                val stakeVal = stakeText.toDoubleOrNull()
                val canAdd = oddsVal != null && oddsVal > 0 &&
                        (chained || (stakeVal != null && stakeVal > 0))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = if (chained) plain(balance) else stakeText,
                        onValueChange = { if (!chained) stakeText = it },
                        readOnly = chained,
                        label = { Text(if (chained) "Stake (auto)" else "Stake") },
                        singleLine = true,
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = oddsText,
                        onValueChange = { oddsText = it },
                        label = { Text("Odds") },
                        singleLine = true,
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (state.addEntry(target, if (chained) null else stakeVal, oddsVal!!)) {
                                stakeText = ""
                                oddsText = ""
                            }
                        },
                        enabled = canAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green,
                            contentColor = Black,
                            disabledContainerColor = Color(0xFF1B3A26),
                            disabledContentColor = Gray
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Add Entry", fontWeight = FontWeight.SemiBold) }
                    TextButton(
                        onClick = { showReset = true },
                        enabled = chained,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Red,
                            disabledContentColor = Gray
                        )
                    ) { Text("Reset") }
                }
            }

            Spacer(Modifier.height(12.dp))

            TableHeader()
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "No entries yet. Enter your first stake and odds above.",
                            color = Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                itemsIndexed(rows) { index, r ->
                    EntryCard(
                        r = r,
                        targetAmount = target.amount,
                        isLast = index == rows.lastIndex,
                        onUndo = {
                            val back = state.undoLast(target)
                            if (back != null) stakeText = plain(back)
                        }
                    )
                }
            }
        }

        BottomBar(
            centerLabel = target.name,
            onCenter = onNextTarget,
            onMenu = onMenu,
            onSummary = onSummary,
            onReport = onReport,
            onContactMessage = onContactMessage,
            onContactGroup = onContactGroup
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Delete all entries of this target?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.resetTarget(target)
                    showReset = false
                }) { Text("Delete", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancel", color = White) }
            }
        )
    }
}

@Composable
private fun FulfilledBanner(target: TargetData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14391C))
            .border(1.dp, Green, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text("✓ Target Fulfilled", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        val extra = if (target.exceeded > 0) "Target crossed by " + money(target.exceeded) + ". " else "Target reached. "
        Text(extra + "This Payout Target is closed for new entries.", color = White, fontSize = 12.sp)
    }
}

@Composable
private fun TopSummaryCard(target: TargetData) {
    val balance = target.balance
    val profit = target.profit
    val label = String.format(Locale.US, "%.0f%%", target.ratio * 100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Payout", color = White, fontSize = 12.sp)
                    Text(
                        short(balance),
                        color = Green,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Profit", color = White, fontSize = 12.sp)
                    Text(
                        signed(profit),
                        color = if (profit >= 0) Green else Red,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                SemiGauge(
                    progress = target.ratio.toFloat(),
                    label = label,
                    modifier = Modifier
                        .width(140.dp)
                        .height(80.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = LineColor)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("Target", money(target.amount), White, Modifier.weight(1f))
                StatCell("Achieved", money(balance), Green, Modifier.weight(1f))
                StatCell("Remaining", money(target.remaining), White, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Gray, fontSize = 11.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// Column weights shared by the header and the entry rows so everything lines up
private const val W_NO = 0.55f
private const val W_STAKE = 1.25f
private const val W_ODDS = 0.85f
private const val W_PROFIT = 1.2f
private const val W_PAID = 1.3f

@Composable
private fun Cell(
    text: String,
    modifier: Modifier,
    color: Color,
    bold: Boolean = false,
    size: Int = 12
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cell("#", Modifier.weight(W_NO), Green, bold = true)
        Cell("Stake", Modifier.weight(W_STAKE), Green, bold = true)
        Cell("Odds", Modifier.weight(W_ODDS), Green, bold = true)
        Cell("Profit", Modifier.weight(W_PROFIT), Green, bold = true)
        Cell("Paid Out", Modifier.weight(W_PAID), Green, bold = true)
        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun EntryCard(r: ChainRow, targetAmount: Double, isLast: Boolean, onUndo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cell("#${r.no}", Modifier.weight(W_NO), Green, bold = true, size = 13)
            Cell(short(r.stake), Modifier.weight(W_STAKE), White)
            Cell(plain(r.odds), Modifier.weight(W_ODDS), White)
            Cell(signed(r.profit), Modifier.weight(W_PROFIT), if (r.profit >= 0) Green else Red)
            Cell(short(r.paidOut), Modifier.weight(W_PAID), White, bold = true)
            if (isLast) {
                // Only the newest entry can be undone, so the chain of stakes never breaks
                TextButton(
                    onClick = onUndo,
                    modifier = Modifier
                        .width(32.dp)
                        .height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("✕", color = Red, fontSize = 14.sp) }
            } else {
                Spacer(Modifier.width(32.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dateTimeText(r.time),
                color = Gray,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                pct(r.progress * 100),
                color = Green,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            MiniProgress(
                fraction = r.progress.toFloat(),
                modifier = Modifier
                    .width(70.dp)
                    .height(6.dp)
            )
        }
        if (r.progress >= 1.0) {
            // The stake that reached or crossed the target is shown with its status
            val over = r.paidOut - targetAmount
            Spacer(Modifier.height(4.dp))
            Text(
                "✓ Target Fulfilled" + (if (over > 0) " • crossed by " + money(over) else ""),
                color = Green,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BarItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = Gray, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun BottomBar(
    centerLabel: String,
    onCenter: () -> Unit,
    onMenu: () -> Unit,
    onSummary: () -> Unit,
    onReport: () -> Unit,
    onContactMessage: () -> Unit,
    onContactGroup: () -> Unit
) {
    var contactOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF0E0E0E))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(Icons.Filled.Menu, "Menu", White, Modifier.weight(1f), onMenu)
            BarItem(Icons.Filled.BarChart, "Summary", SummaryColors[0], Modifier.weight(1f), onSummary)
            Spacer(Modifier.width(88.dp))
            BarItem(Icons.Filled.Description, "Brief Report", ReportColors[0], Modifier.weight(1f), onReport)
            // Contact Us opens a dropdown (Message Me / Telegram Group) above the bar
            Box(modifier = Modifier.weight(1f)) {
                BarItem(
                    Icons.Filled.SupportAgent, "Contact Us", MessageColors[0],
                    Modifier.fillMaxWidth(),
                    onClick = { contactOpen = true }
                )
                ContactMenu(
                    expanded = contactOpen,
                    onDismiss = { contactOpen = false },
                    onMessageMe = onContactMessage,
                    onTelegramGroup = onContactGroup
                )
            }
        }

        // Round button in the bottom center, named after the current Payout Target.
        // Tapping it switches to the next Payout Target.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(76.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3DDC84), Color(0xFF1B8A4A))))
                .border(3.dp, Black, CircleShape)
                .clickable(onClick = onCenter),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = Black,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    centerLabel,
                    color = Black,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    }
}
