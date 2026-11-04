package com.payouttracker.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sign Up and Login are the same step: continue with a Gmail account. */
@Composable
fun LoginScreen(auth: AuthManager, onBack: () -> Unit) {
    val ctx = LocalContext.current

    // Sign Up and Login are the same thing: the Google account picker opens straight away
    LaunchedEffect(Unit) {
        val activity = ctx.findActivity()
        if (auth.user == null && auth.firebaseReady && activity != null) auth.signIn(activity)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = White)
            }
            Text("Sign in", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Green, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(14.dp))
            Text("Welcome to Payout Tracker", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Continue with your Gmail account. New accounts are created automatically.",
                color = Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val activity = ctx.findActivity()
                    if (activity != null) auth.signIn(activity)
                },
                enabled = !auth.busy,
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (auth.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Black, strokeWidth = 2.dp)
                } else {
                    Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            val err = auth.error
            if (err != null) {
                Spacer(Modifier.height(14.dp))
                Text(err, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "One account can be active on one device at a time. Signing in on a new device signs you out of the old one.",
                color = Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
