package com.payouttracker.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---- Colors: black background, green / white / red text ----
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Green = Color(0xFF2ECC71)
val Red = Color(0xFFFF5252)
val Gray = Color(0xFF9E9E9E)
val LineColor = Color(0xFF2A2A2A)
val CardBg = Color(0xFF0B1F0B)
val PanelBg = Color(0xFF111111)

// Accent colors of the five main boxes
val TrackColors = listOf(Color(0xFF2FB344), Color(0xFF0E5A1F))
val SummaryColors = listOf(Color(0xFF14B8A6), Color(0xFF0F4C75))
val ReportColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B1D8F))
val MessageColors = listOf(Color(0xFF2AABEE), Color(0xFF1565C0))
val UpdateColors = listOf(Color(0xFFFFA726), Color(0xFFD84315))

val AppColors = darkColorScheme(
    primary = Green,
    onPrimary = Black,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    error = Red
)

const val LINK_MESSAGE = "https://t.me/austinaries24"
const val LINK_GROUP = "https://t.me/+ry3c80xzSJIyNmVl"

// ---- Number / date formatting ----
fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)
fun plain(v: Double): String = String.format(Locale.US, "%.2f", v)

/** Compact number so big compounded values still fit in a small column. */
fun short(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e12 -> String.format(Locale.US, "%.2fT", v / 1e12)
        a >= 1e9 -> String.format(Locale.US, "%.2fB", v / 1e9)
        a >= 1e6 -> String.format(Locale.US, "%.2fM", v / 1e6)
        else -> fmt(v)
    }
}

fun signed(v: Double): String = (if (v > 0) "+" else "") + short(v)
fun money(v: Double): String = short(v)
fun pct(v: Double): String = String.format(Locale.US, "%.1f%%", v)

/** 30000.0 -> "30000", 2500.5 -> "2500.50" */
fun amountText(v: Double): String =
    if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString() else plain(v)

fun targetName(amount: Double): String = "Payout Target - " + amountText(amount)
fun avgOf(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size
fun avgText(v: Double?): String = if (v == null) "—" else short(v)

fun dateTimeText(t: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(t))
fun timeText(t: Long): String = SimpleDateFormat("hh:mm a", Locale.US).format(Date(t))
fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
fun dayLabel(t: Long): String = SimpleDateFormat("dd MMM yyyy, EEE", Locale.US).format(Date(t))

// ---- Shared components ----
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = Green,
    unfocusedBorderColor = Gray,
    focusedLabelColor = Green,
    unfocusedLabelColor = Gray,
    cursorColor = Green
)

@Composable
fun AppTopBar(title: String, onBack: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
        }
        Text(
            title,
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = White)
        }
    }
}

@Composable
fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanelBg)
            .padding(12.dp)
    ) {
        Text(label, color = Gray, fontSize = 11.sp, maxLines = 1)
        Text(value, color = valueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF14391C))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MiniProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2A2A2A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Green)
        )
    }
}

/**
 * Half-circle progress gauge: white track, green progress.
 * Give it a height of about (width / 2 + 10.dp).
 */
@Composable
fun SemiGauge(progress: Float, label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val diameter = size.width - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = Green,
                    startAngle = 180f,
                    sweepAngle = 180f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            label,
            color = White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}
