package com.example.payouttracker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun HomeScreen(
    state: TrackerState,
    onTrack: () -> Unit,
    onSummary: () -> Unit,
    onReport: () -> Unit,
    onContactMessage: () -> Unit,
    onContactGroup: () -> Unit,
    onUpdate: () -> Unit,
    newVersion: String?,
    onMenu: () -> Unit
) {
    var contactOpen by remember { mutableStateOf(false) }
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
                Text("Track • Analyze • Reach your target", color = Gray, fontSize = 13.sp)
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = White)
            }
        }

        Spacer(Modifier.height(16.dp))

        HomeBox(
            title = "Track Payout",
            subtitle = "Record stakes, odds & payouts",
            icon = Icons.Filled.AttachMoney,
            colors = TrackColors,
            big = true,
            footer = footer,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            onClick = onTrack
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HomeBox(
                title = "Summary",
                subtitle = "Last 7 & 30 days",
                icon = Icons.Filled.BarChart,
                colors = SummaryColors,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onSummary
            )
            HomeBox(
                title = "Brief Report",
                subtitle = "Daily progress",
                icon = Icons.Filled.Description,
                colors = ReportColors,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onReport
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Contact Us opens a dropdown (Message Me / Telegram Group) right under the box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                HomeBox(
                    title = "Contact Us",
                    subtitle = "Telegram chat & group",
                    icon = Icons.Filled.SupportAgent,
                    colors = MessageColors,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { contactOpen = true }
                )
                ContactMenu(
                    expanded = contactOpen,
                    onDismiss = { contactOpen = false },
                    onMessageMe = onContactMessage,
                    onTelegramGroup = onContactGroup
                )
            }
            HomeBox(
                title = "App Update",
                subtitle = "Get the latest version",
                icon = Icons.Filled.SystemUpdate,
                colors = UpdateColors,
                footer = if (newVersion != null) "Update available" else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onUpdate
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}
