package com.payouttracker.app

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Summary and Report are the target lists; SummaryDetail and ReportDetail show one target
// (or all targets combined when targetId is null).
enum class Screen { Home, Login, Targets, Detail, Summary, SummaryDetail, Report, ReportDetail }

/** One place in the app. [targetId] is used by Detail, SummaryDetail and ReportDetail. */
data class Route(val screen: Screen, val targetId: Long? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Black) {
                    AppRoot(onExit = { finishAffinity() })
                }
            }
        }
    }
}

@Composable
fun AppRoot(onExit: () -> Unit) {
    val ctx = LocalContext.current
    val state = remember { TrackerState(ctx.applicationContext) }
    // Navigation stack. The Home screen is always the bottom entry.
    val stack = remember { mutableStateListOf(Route(Screen.Home)) }
    val current = stack.last()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val lastBackAt = remember { longArrayOf(0L) }
    var contactExpanded by remember { mutableStateOf(false) }

    // Sign-in (Gmail, one device at a time) and the forced-update gate
    val auth = remember { AuthManager(ctx.applicationContext, scope) }
    val gate = remember { AppGate(ctx.applicationContext) }
    // Forced updates download the new APK from the latest GitHub release
    val updater = remember { UpdateController(ctx.applicationContext, scope) }

    // Close the login page as soon as sign-in succeeds
    LaunchedEffect(auth.user) {
        if (auth.user != null && stack.last().screen == Screen.Login) {
            stack.removeAt(stack.lastIndex)
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun push(r: Route) {
        if (stack.last() != r) stack.add(r)
        closeDrawer()
    }

    fun goHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        closeDrawer()
    }

    fun openLink(url: String) {
        closeDrawer()
        uriHandler.openUri(url)
    }

    fun openLogin() {
        push(Route(Screen.Login))
    }

    // Back: close the menu first, then go one screen back.
    // On the Home screen, pressing back twice quickly exits the app.
    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            closeDrawer()
        } else if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackAt[0] < 2000) {
                onExit()
            } else {
                lastBackAt[0] = now
                Toast.makeText(ctx, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // An installed version below the required minimum cannot be used until it is updated
    if (gate.mustUpdate) {
        ForceUpdateScreen(updater = updater, onExit = onExit)
        return
    }

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0E0E0E),
                drawerContentColor = White
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text("Payout Tracker", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Menu", color = Gray, fontSize = 13.sp)
                }
                Divider(color = LineColor)
                Spacer(Modifier.height(8.dp))
                MenuItem("Home", Icons.Filled.Home, White, current.screen == Screen.Home) { goHome() }
                MenuItem(
                    "Track Payout", Icons.Filled.AttachMoney, TrackColors[0],
                    current.screen == Screen.Targets || current.screen == Screen.Detail
                ) { push(Route(Screen.Targets)) }
                MenuItem(
                    "Summary", Icons.Filled.BarChart, SummaryColors[0],
                    current.screen == Screen.Summary || current.screen == Screen.SummaryDetail
                ) { push(Route(Screen.Summary)) }
                MenuItem(
                    "Brief Report", Icons.Filled.Description, ReportColors[0],
                    current.screen == Screen.Report || current.screen == Screen.ReportDetail
                ) { push(Route(Screen.Report)) }

                // Contact Us expands into Message Me and Telegram Group
                MenuItem(
                    "Contact Us", Icons.Filled.SupportAgent, MessageColors[0], false,
                    trailing = if (contactExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore
                ) { contactExpanded = !contactExpanded }
                if (contactExpanded) {
                    MenuItem(
                        "Message Me", Icons.AutoMirrored.Filled.Send, MessageColors[0], false,
                        indent = true
                    ) { openLink(LINK_MESSAGE) }
                    MenuItem(
                        "Telegram Group", Icons.Filled.Groups, MessageColors[0], false,
                        indent = true
                    ) { openLink(LINK_GROUP) }
                }

                MenuItem("Telegram", Icons.AutoMirrored.Filled.Send, UpdateColors[0], false) {
                    openLink(LINK_TELEGRAM)
                }
                Spacer(Modifier.height(8.dp))
                Divider(color = LineColor)
                Spacer(Modifier.height(8.dp))
                val me = auth.user
                if (me == null) {
                    MenuItem("Login / Sign Up", Icons.Filled.Person, Green, current.screen == Screen.Login) {
                        openLogin()
                    }
                } else {
                    Text(
                        me.email,
                        color = Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                    )
                    MenuItem("Log Out", Icons.Filled.PowerSettingsNew, Red, false) {
                        closeDrawer()
                        auth.logout()
                    }
                }
                MenuItem("Exit App", Icons.AutoMirrored.Filled.ExitToApp, Red, false) { onExit() }
            }
        }
    ) {
        when (current.screen) {
            Screen.Home -> HomeScreen(
                state = state,
                onTrack = { push(Route(Screen.Targets)) },
                onSummary = { push(Route(Screen.Summary)) },
                onReport = { push(Route(Screen.Report)) },
                onContactMessage = { openLink(LINK_MESSAGE) },
                onContactGroup = { openLink(LINK_GROUP) },
                onTelegram = { openLink(LINK_TELEGRAM) },
                user = auth.user,
                onLogin = { openLogin() },
                onMenu = openMenu
            )

            Screen.Login -> LoginScreen(
                auth = auth,
                onBack = { stack.removeAt(stack.lastIndex) }
            )

            Screen.Targets -> TargetListScreen(
                state = state,
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu,
                onOpen = { id -> push(Route(Screen.Detail, id)) }
            )

            Screen.Detail -> {
                val target = state.findTarget(current.targetId)
                if (target == null) {
                    // The target no longer exists; return to the previous screen
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    TargetDetailScreen(
                        state = state,
                        target = target,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu,
                        onSummary = { push(Route(Screen.SummaryDetail, target.id)) },
                        onReport = { push(Route(Screen.ReportDetail, target.id)) },
                        onContactMessage = { openLink(LINK_MESSAGE) },
                        onContactGroup = { openLink(LINK_GROUP) },
                        onNextTarget = {
                            // Switch to the next Payout Target (wraps around to the first one)
                            val list = state.targets
                            if (list.size > 1) {
                                val i = list.indexOfFirst { it.id == target.id }
                                val next = list[(i + 1) % list.size]
                                stack[stack.lastIndex] = Route(Screen.Detail, next.id)
                            } else {
                                Toast.makeText(ctx, "No other Payout Target yet", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Screen.Summary -> TargetPickerScreen(
                title = "Summary",
                state = state,
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu,
                onOpen = { id -> push(Route(Screen.SummaryDetail, id)) }
            )

            Screen.Report -> TargetPickerScreen(
                title = "Brief Report",
                state = state,
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu,
                onOpen = { id -> push(Route(Screen.ReportDetail, id)) }
            )

            Screen.SummaryDetail -> {
                if (current.targetId != null && state.findTarget(current.targetId) == null) {
                    // The target was deleted; go back
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    SummaryScreen(
                        state = state,
                        targetId = current.targetId,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

            Screen.ReportDetail -> {
                if (current.targetId != null && state.findTarget(current.targetId) == null) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    ReportScreen(
                        state = state,
                        targetId = current.targetId,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }
        }
    }

    val notice = auth.notice
    if (notice != null) {
        AlertDialog(
            onDismissRequest = { auth.notice = null },
            containerColor = Color(0xFF111111),
            titleContentColor = White,
            textContentColor = White,
            title = { Text("Signed out", fontWeight = FontWeight.Bold) },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { auth.notice = null }) {
                    Text("OK", color = Green, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    indent: Boolean = false,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        badge = if (trailing != null) {
            { Icon(trailing, contentDescription = null, tint = Gray) }
        } else {
            null
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color(0xFF16301B),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = White,
            unselectedTextColor = White
        ),
        modifier = Modifier.padding(
            start = if (indent) 28.dp else 0.dp
        ).padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
