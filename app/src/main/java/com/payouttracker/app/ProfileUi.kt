package com.payouttracker.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A round green badge with the first letter of the name. */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF43E97B), Color(0xFF0F9D58)))),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.46f).sp)
    }
}

/** The profile button in the top bar of the home screen. */
@Composable
fun ProfileButton(user: AuthUser?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (user != null) {
            Avatar(user.name.ifBlank { user.email }, 34.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = "Sign in", tint = White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ProfileDialog(user: AuthUser, access: Access, onLogout: () -> Unit, onDismiss: () -> Unit) {
    val status = when {
        user.isAdmin -> "Admin"
        access.phase == "premium" -> "Premium until " + dateOnly(access.until)
        access.phase == "trial" -> "Free trial: " + access.daysLeft + " day(s) left"
        else -> "Not subscribed"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        titleContentColor = White,
        textContentColor = White,
        title = null,
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(user.name.ifBlank { user.email }, 76.dp)
                Spacer(Modifier.height(12.dp))
                Text(user.name.ifBlank { user.email }, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(user.email, color = Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(status, color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = White) }
        }
    )
}
