package com.payouttracker.app

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The "Contact Us" dropdown: Message Me and Telegram (opens the same Telegram link as the Telegram button).
 * Place it inside the same Box as the button that opens it, so it drops down from that button.
 */
@Composable
fun ContactMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMessageMe: () -> Unit,
    onTelegramGroup: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(Color(0xFF16301B))
    ) {
        DropdownMenuItem(
            text = { Text("Message Me", color = White, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MessageColors[0])
            },
            onClick = {
                onDismiss()
                onMessageMe()
            }
        )
        DropdownMenuItem(
            text = { Text("Telegram", color = White, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF2AABEE))
            },
            onClick = {
                onDismiss()
                onTelegramGroup()
            }
        )
    }
}
