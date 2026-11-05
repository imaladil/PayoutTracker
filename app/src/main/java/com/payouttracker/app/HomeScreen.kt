package com.payouttracker.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeBox(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    footer: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        // large faded icon as a background decoration
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(if (big) 130.dp else 72.dp)
        )
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Box(
                modifier = Modifier
                    .size(if (big) 52.dp else 42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (big) 30.dp else 24.dp)
                )
            }
            Spacer(Modifier.height(if (big) 14.dp else 10.dp))
            Text(
                title,
                color = Color.White,
                fontSize = if (big) 24.sp else 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = if (big) 13.sp else 11.sp
            )
        }
        if (footer != null) {
            Text(
                footer,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/** A frosted panel inside the hub card. Its tint fades into the card colours so both blend together. */
@Composable
private fun HubMini(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, tint.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.14f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(54.dp)
        )
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp)
        }
    }
}

// Same flat two-colour gradient style as the Live card, in its own colour (ocean blue)
private val HubColors = listOf(Color(0xFFEF4444), Color(0xFF991B1B))
private val HubSummaryTint = Color(0xFFFFD54F)
private val HubReportTint = Color(0xFFE1BEE7)

/**
 * Track Payout with Summary (top right) and Brief Report (bottom right) blended into the same card.
 * The left part opens Track Payout; each panel on the right opens its own page.
 */
@Composable
private fun TrackHubCard(footer: String, onTrack: () -> Unit, onSummary: () -> Unit, onReport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(HubColors))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Track Payout is its own raised, outlined button with an arrow, so it is clear it can be tapped
            val trackShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(trackShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.65f), trackShape)
                    .clickable(onClick = onTrack)
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Filled.AttachMoney,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(120.dp)
                )
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open Track Payout",
                                    tint = HubColors[1],
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Track Payout", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Record stakes, odds & payouts", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                    }
                    Text(footer, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                modifier = Modifier
                    .width(146.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HubMini(
                    "Summary", "Last 7 & 30 days", Icons.Filled.BarChart, HubSummaryTint,
                    Modifier.weight(1f).fillMaxWidth(), onSummary
                )
                HubMini(
                    "Brief Report", "Daily progress", Icons.Filled.Description, HubReportTint,
                    Modifier.weight(1f).fillMaxWidth(), onReport
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    state: TrackerState,
    repo: EventsRepo,
    user: AuthUser?,
    onTrack: () -> Unit,
    onSummary: () -> Unit,
    onReport: () -> Unit,
    onContact: () -> Unit,
    onTelegram: () -> Unit,
    onLogin: () -> Unit,
    onSubscribe: () -> Unit,
    onMailbox: () -> Unit,
    access: Access,
    unread: Int,
    onLive: () -> Unit,
    onPreMatch: () -> Unit,
    onResults: () -> Unit,
    onStrategy: () -> Unit,
    onStrategyPost: (String) -> Unit,
    onAdmin: () -> Unit,
    onProfile: () -> Unit,
    welcome: Boolean,
    onWelcomeDone: () -> Unit,
    onMenu: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val total = state.targets.size
    val active = state.targets.count { !it.isFulfilled }
    val footer = if (total == 0) {
        "No targets yet"
    } else {
        "$total target" + (if (total == 1) "" else "s") + " • $active active"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Payout Tracker", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Track • Reach your target", color = Gray, fontSize = 13.sp)
            }
            if (user != null) MailButton(unread, onMailbox)
            ProfileButton(user, onProfile)
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = White)
            }
        }

        Spacer(Modifier.height(12.dp))

        // A short welcome strip after signing in; it fades away and the space closes up
        LaunchedEffect(welcome) {
            if (welcome) {
                delay(3500)
                onWelcomeDone()
            }
        }
        AnimatedVisibility(
            visible = welcome && user != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF0F9D58), Color(0xFF2ECC71))))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Signed in as " + (user?.email ?: ""),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (user == null) {
            // Sign Up and Login are the same step: continue with Google
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        } else {
            if (!user.isAdmin) {
                Spacer(Modifier.height(8.dp))
                if (access.premium) {
                    Pill("Premium until " + dateOnly(access.until))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onSubscribe,
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)
                        ) { Text("Subscribe", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (access.phase == "trial") access.daysLeft.toString() + " day(s) of free trial left" else "Free trial ended",
                            color = Gray, fontSize = 12.sp
                        )
                    }
                }
            }
            if (user.isAdmin) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAdmin) { Text("Admin Panel", color = Green, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Live ----
        val live = repo.liveEvents
        SectionHeader("LIVE", Green, if (access.canView) live.size else null, true, onLive)
        if (user == null) {
            LockedBox("Live", LiveColors, onLogin)
        } else if (!access.canView) {
            LockedBox("Live", LiveColors, onSubscribe, "Subscribe", "Subscribe to view")
        } else if (live.isEmpty()) {
            EmptyCard("No live events right now.", LiveColors, onLive)
        } else {
            live.take(2).forEach { e ->
                EventCard(e, LiveColors, true, now = repo.now, onClick = onLive)
                Spacer(Modifier.height(10.dp))
            }
        }

        // ---- Pre-Match ----
        val pre = repo.preMatchEvents
        SectionHeader("Pre-Match", Color(0xFF8C9EFF), if (access.canView) pre.size else null, false, onPreMatch, clock = true)
        if (user == null) {
            LockedBox("Pre-Match", PreMatchColors, onLogin)
        } else if (!access.canView) {
            LockedBox("Pre-Match", PreMatchColors, onSubscribe, "Subscribe", "Subscribe to view")
        } else if (pre.isEmpty()) {
            EmptyCard("No upcoming events.", PreMatchColors, onPreMatch)
        } else {
            pre.take(2).forEach { e ->
                EventCard(e, PreMatchColors, false, now = repo.now, onClick = onPreMatch)
                Spacer(Modifier.height(10.dp))
            }
        }

        // ---- Results (open to everyone) ----
        SectionHeader("Results", GoldColors[0], null, false, onResults)
        StatsRow(repo)
        Spacer(Modifier.height(10.dp))
        SearchField(query, { query = it })
        Spacer(Modifier.height(10.dp))
        val shown = repo.results.filter { it.matches(query) }
        val limit = if (query.isBlank()) 5 else 20
        if (shown.isEmpty()) {
            Text(if (query.isBlank()) "No results yet." else "No matching results.", color = Gray, fontSize = 13.sp)
        } else {
            shown.take(limit).forEach { e ->
                ResultRow(e)
                Spacer(Modifier.height(8.dp))
            }
            if (shown.size > limit) {
                TextButton(onClick = onResults) { Text("View all results", color = GoldColors[0], fontWeight = FontWeight.Bold) }
            }
        }

        // ---- Strategy ----
        SectionHeader("Strategy", Color(0xFFF48FB1), null, false, onStrategy)
        if (user == null) {
            LockedBox("Strategy", StrategyColors, onLogin)
        } else if (repo.strategies.isEmpty()) {
            EmptyCard("No strategy posts yet.", StrategyColors, onStrategy)
        } else {
            repo.strategies.take(3).forEach { p ->
                StrategyRow(p) { onStrategyPost(p.id) }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        TrackHubCard(footer = footer, onTrack = onTrack, onSummary = onSummary, onReport = onReport)

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeBox(
                title = "Contact Us",
                subtitle = "Message the admin",
                icon = Icons.Filled.SupportAgent,
                colors = UpdateColors,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onContact
            )
            HomeBox(
                title = "Telegram",
                subtitle = "Chat with the admin",
                icon = Icons.AutoMirrored.Filled.Send,
                colors = MessageColors,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onTelegram
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}
