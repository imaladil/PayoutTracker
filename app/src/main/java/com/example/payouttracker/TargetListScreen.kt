package com.example.payouttracker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * First screen inside "Track Payout": the Set Target box and a card for every Payout Target.
 */
@Composable
fun TargetListScreen(
    state: TrackerState,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onOpen: (Long) -> Unit
) {
    var targetText by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val tv = targetText.toDoubleOrNull()
    val canSet = tv != null && tv > 0

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Track Payout", onBack, onMenu)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Set Target", color = Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The amount you enter becomes a new Payout Target. Payouts from your stakes count toward it.",
                            color = Gray,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = targetText,
                                onValueChange = { targetText = it },
                                label = { Text("Set Target") },
                                singleLine = true,
                                colors = fieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    state.addTarget(tv!!)
                                    targetText = ""
                                },
                                enabled = canSet,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Green,
                                    contentColor = Black,
                                    disabledContainerColor = Color(0xFF1B3A26),
                                    disabledContentColor = Gray
                                )
                            ) { Text("Set", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }

            if (state.targets.isEmpty()) {
                item {
                    Text(
                        "No Payout Targets yet. Set your first target above.",
                        color = Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(state.targets) { index, t ->
                TargetListCard(
                    no = index + 1,
                    t = t,
                    onClick = { onOpen(t.id) },
                    onDelete = { deleteId = t.id }
                )
            }
        }
    }

    val toDelete = state.findTarget(deleteId)
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Delete this Payout Target?") },
            text = { Text(toDelete.name + " and all of its entries will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteTarget(toDelete.id)
                    deleteId = null
                }) { Text("Delete", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text("Cancel", color = White) }
            }
        )
    }
}

/** Card for one Payout Target. The delete button only shows when [onDelete] is given. */
@Composable
fun TargetListCard(no: Int, t: TargetData, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val fulfilled = t.isFulfilled
    val done = t.ratio * 100.0
    val left = max(100.0 - done, 0.0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Green),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$no", color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t.name,
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "Opened " + dateTimeText(t.openedAt),
                        color = Gray,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .width(36.dp)
                            .height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("✕", color = Red, fontSize = 14.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total Payout", money(t.balance), Green, Modifier.weight(1f))
                StatTile("Remaining", money(t.remaining), White, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            MiniProgress(
                fraction = t.ratio.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    pct(done) + " done",
                    color = Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(pct(left) + " left", color = White, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (fulfilled) "✓ Target Fulfilled" else "Active")
                Spacer(Modifier.weight(1f))
                Text(
                    if (t.entries.size == 1) "1 stake" else "${t.entries.size} stakes",
                    color = Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * List of every Payout Target, used by Summary and Brief Report.
 * Tapping a card opens that target's own Summary / Report.
 */
@Composable
fun TargetPickerScreen(
    title: String,
    state: TrackerState,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onOpen: (Long?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title, onBack, onMenu)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.targets.isEmpty()) {
                item {
                    Text(
                        "No Payout Targets yet. Create one from Track Payout and it will be listed here.",
                        color = Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            itemsIndexed(state.targets) { index, t ->
                TargetListCard(no = index + 1, t = t, onClick = { onOpen(t.id) })
            }
        }
    }
}
