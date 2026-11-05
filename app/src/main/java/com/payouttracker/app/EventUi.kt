package com.payouttracker.app

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- small building blocks ----------

/** The "1x CODE:" box with a copy button. */
@Composable
fun CodeBox(code: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("1x CODE:", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(code, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f))
                .clickable {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(ctx, "Code copied", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy", color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

val SingleTagColor = Color(0xFF26C6DA)
val MultiTagColor = Color(0xFFE040FB)

/** SINGLE or MULTI, each in its own colour. */
@Composable
fun BetTypeTag(multi: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (multi) MultiTagColor else SingleTagColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            if (multi) "MULTI" else "SINGLE",
            color = Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
        )
    }
}

/** "2h 10m", "1d 3h" or "Starting" until [startAt]. */
fun countdownText(startAt: Long, now: Long): String {
    val m = (startAt - now) / 60000
    if (m <= 0) return "Starting"
    val d = m / 1440
    val h = (m % 1440) / 60
    val min = m % 60
    return if (d > 0) "${d}d ${h}h" else if (h > 0) "${h}h ${min}m" else "${min}m"
}

/** Sport icon and name, the tournament and the date and time of one game. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeader(g: Leg) {
    // When the tournament name does not fit next to the sport, it drops to the next line
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sportIcon(g.sport), contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(g.sport, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        if (g.tournament.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(g.tournament, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(dateTimeText(g.startAt), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
}

/** Bet and odds are black; a light chip keeps them readable on every card colour. */
@Composable
private fun BetChip(bet: String, odds: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.88f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(bet, color = Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(odds, color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * One event as a coloured card. A single bet shows its game, bet, odds and code. A multi bet lists
 * every game the same way, then Number of Events and Total Odds, with the one shared code.
 * [now] (from the repo clock) lets a pre-match card count down to its start.
 */
@Composable
fun EventCard(
    e: BetEvent,
    colors: List<Color>,
    live: Boolean,
    modifier: Modifier = Modifier,
    now: Long = 0L,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        // Top: the Single/Multi tag on the left; Unsettled (live) or a clock (pre-match) in the corner
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BetTypeTag(e.isMulti)
            Spacer(Modifier.weight(1f))
            if (live) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(UnsettledColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Unsettled", color = Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Starts soon", tint = White, modifier = Modifier.size(14.dp))
                    if (now > 0 && e.startAt > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(countdownText(e.startAt, now), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!e.isMulti) {
            val g = e.games[0]
            GameHeader(g)
            Spacer(Modifier.height(10.dp))
            // Bottom: teams, bet and odds on the left, the code box in the bottom-right corner
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(g.teamA, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("vs", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Text(g.teamB, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    BetChip(g.bet, g.odds)
                }
                if (e.code.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    CodeBox(e.code, modifier = Modifier.width(104.dp))
                }
            }
        } else {
            e.legs.forEachIndexed { i, g ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(10.dp)
                ) {
                    GameHeader(g)
                    Spacer(Modifier.height(6.dp))
                    Text(g.teamA + "  vs  " + g.teamB, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    BetChip(g.bet, g.odds)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.88f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Number of Events: " + e.legs.size, color = Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Total Odds: " + e.odds, color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                if (e.code.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    CodeBox(e.code, modifier = Modifier.width(104.dp))
                }
            }
        }
    }
}

/** A settled bet in the history list. The unique bet ID sits in the top-right corner (tap to copy). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultRow(e: BetEvent) {
    val won = e.status == "won"
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tag, sport and tournament flow together; a tournament that does not fit moves to the next line whole
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BetTypeTag(e.isMulti)
                if (!e.isMulti) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(sportIcon(e.sport), contentDescription = null, tint = Gray, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(e.sport, color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    if (e.tournament.isNotBlank()) {
                        Text(e.tournament, color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("" + e.legs.size + " events", color = Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "ID: " + e.displayId,
                color = GoldColors[0],
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(e.displayId))
                        Toast.makeText(ctx, "Bet ID copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (!e.isMulti) {
                    Text(e.teamA + "  vs  " + e.teamB, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(e.bet + "  •  " + e.odds, color = White, fontSize = 13.sp)
                    Text(dateTimeText(e.startAt), color = Gray, fontSize = 11.sp)
                } else {
                    e.legs.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(sportIcon(g.sport), contentDescription = null, tint = Gray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                g.teamA + " vs " + g.teamB + "  •  " + g.bet + "  •  " + g.odds,
                                color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Text("Number of Events: " + e.legs.size + "  •  Total Odds: " + e.odds, color = GoldColors[0], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(dateTimeText(e.startAt), color = Gray, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (won) Color(0xFF14391C) else Color(0xFF3A1414))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (won) "WON" else "LOST",
                    color = if (won) Green else Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Total, Unsettled, Won and Lost counts, with the success rate (won out of settled) below. */
@Composable
fun StatsRow(repo: EventsRepo) {
    val rate = repo.successRate
    val rateColor = if (repo.won + repo.lost == 0) Gray else if (rate >= 50) Green else UnsettledColor
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Total", repo.total.toString(), White, Modifier.weight(1f))
            StatTile("Unsettled", repo.unsettled.toString(), UnsettledColor, Modifier.weight(1f))
            StatTile("Won", repo.won.toString(), Green, Modifier.weight(1f))
            StatTile("Lost", repo.lost.toString(), Red, Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBg)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Success rate", color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("$rate%", color = rateColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF2A2A2A))
            ) {
                if (rate > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rate / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(rateColor)
                    )
                }
            }
        }
    }
}

/** Dimmed card with a lock, shown to people who may not open a section. */
@Composable
fun LockedBox(
    label: String,
    colors: List<Color>,
    onClick: () -> Unit,
    actionText: String = "Login",
    hint: String = "Login to view"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.35f) }))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label + " is locked", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(hint, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
        ) { Text(actionText, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun EmptyCard(text: String, colors: List<Color>, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.25f) }))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
    ) {
        Text(text, color = White.copy(alpha = 0.9f), fontSize = 14.sp)
    }
}

@Composable
fun SectionHeader(title: String, accent: Color, count: Int?, live: Boolean, onClick: () -> Unit, clock: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = "Live", tint = Red, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (clock) {
            Icon(Icons.Filled.Schedule, contentDescription = "Pre-match", tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(title, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (count != null) Text("   $count", color = Gray, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Gray)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text("Search by team, bet or ID") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gray) },
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- images stored inside the database ----------

fun decodeBase64Image(data: String, sample: Int = 1): ImageBitmap? = try {
    val bytes = Base64.decode(data, Base64.DEFAULT)
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
fun StoredImage(data: String, modifier: Modifier, sample: Int = 1, scale: ContentScale = ContentScale.Crop) {
    val bmp = remember(data, sample) { decodeBase64Image(data, sample) }
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier, contentScale = scale)
    } else {
        Box(modifier = modifier.background(PanelBg))
    }
}

// ---------- pages ----------

@Composable
fun EventListScreen(
    title: String,
    colors: List<Color>,
    live: Boolean,
    events: List<BetEvent>,
    emptyText: String,
    repo: EventsRepo,
    access: Access,
    onLogin: () -> Unit,
    onSubscribe: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title, onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatsRow(repo) }
            if (!access.signedIn) {
                item { LockedBox(title, colors, onLogin) }
            } else if (!access.canView) {
                item { LockedBox(title, colors, onSubscribe, "Subscribe", "Subscribe to view") }
            } else if (events.isEmpty()) {
                item { EmptyCard(emptyText, colors) }
            } else {
                items(events, key = { it.id }) { e -> EventCard(e, colors, live, now = repo.now) }
            }
            item {
                Text("Results history", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (repo.results.isEmpty()) {
                item { Text("No results yet.", color = Gray, fontSize = 13.sp) }
            } else {
                items(repo.results, key = { it.id }) { e -> ResultRow(e) }
            }
        }
    }
}

@Composable
fun ResultsScreen(repo: EventsRepo, onBack: () -> Unit, onMenu: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = repo.results.filter { it.matches(query) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Results", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { StatsRow(repo) }
            item { SearchField(query, { query = it }) }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "No results yet." else "No matching results.",
                        color = Gray, fontSize = 13.sp
                    )
                }
            } else {
                items(shown, key = { it.id }) { e -> ResultRow(e) }
            }
        }
    }
}

@Composable
fun StrategyListScreen(
    repo: EventsRepo,
    signedIn: Boolean,
    onLogin: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Strategy", onBack, onMenu)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!signedIn) {
                item { LockedBox("Strategy", StrategyColors, onLogin) }
            } else if (repo.strategies.isEmpty()) {
                item { EmptyCard("No strategy posts yet.", StrategyColors) }
            } else {
                items(repo.strategies, key = { it.id }) { p -> StrategyRow(p) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
fun StrategyRow(p: StrategyPost, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(StrategyColors))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (p.image.isNotBlank()) {
            StoredImage(
                p.image,
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp)),
                sample = 2
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(p.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            if (p.createdAt > 0) Text(dateTimeText(p.createdAt), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = White)
    }
}

@Composable
fun StrategyPostScreen(post: StrategyPost, onBack: () -> Unit, onMenu: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar("Strategy", onBack, onMenu)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (post.image.isNotBlank()) {
                StoredImage(
                    post.image,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    scale = ContentScale.FillWidth
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(post.title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (post.createdAt > 0) Text(dateTimeText(post.createdAt), color = Gray, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text(post.body, color = White, fontSize = 15.sp)
        }
    }
}
