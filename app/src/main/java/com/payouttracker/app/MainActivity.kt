package com.payouttracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Summary and Report are the target lists; SummaryDetail and ReportDetail show one target
// (or all targets combined when targetId is null).
enum class Screen {
    Home, Login, Live, PreMatch, Results, Strategy, StrategyPost, Admin, AdminEvent, AdminPost,
    AdminUsers, AdminReports, AdminInbox, Subscribe, PayReport, Contact, Mailbox,
    Targets, Detail, Summary, SummaryDetail, Report, ReportDetail }

/** One place in the app. [targetId] is used by Detail, SummaryDetail and ReportDetail. */
data class Route(val screen: Screen, val targetId: Long? = null, val docId: String? = null)

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
    var showProfile by remember { mutableStateOf(false) }

    // Sign-in (Gmail, one device at a time) and the forced-update gate
    val auth = remember { AuthManager(ctx.applicationContext, scope) }
    val gate = remember { AppGate(ctx.applicationContext) }
    // Forced updates download the new APK from the latest GitHub release
    val updater = remember { UpdateController(ctx.applicationContext, scope) }

    // Live events, results and strategy posts
    val repo = remember { EventsRepo(ctx.applicationContext) }
    LaunchedEffect(auth.user) { repo.onAuth(auth.user != null) }
    // Messages, payments, reports and (for the admin) users
    val account = remember { AccountRepo(ctx.applicationContext) }
    LaunchedEffect(auth.user) { account.onAuth(auth.user) }
    val access = computeAccess(auth.user, auth.profile, repo.now)

    // Notifications: permission, channel, and a quiet check for a newer release
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        Notifier.init(ctx.applicationContext)
        updater.check(silent = true)
    }
    LaunchedEffect(auth.user) {
        if (auth.user != null && Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(updater.newVersion) {
        val v = updater.newVersion
        if (v != null) {
            val prefs = ctx.getSharedPreferences("account", android.content.Context.MODE_PRIVATE)
            if (prefs.getString("notified_version", "") != v) {
                prefs.edit().putString("notified_version", v).apply()
                Notifier.show(ctx, "Update available", "Payout Tracker " + v + " is available.")
            }
        }
    }
    // Moves Pre-Match events to Live when their start time arrives
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            repo.tick()
        }
    }
    val isAdmin = auth.user?.isAdmin == true

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

    fun openContact() {
        if (auth.user == null) openLogin() else push(Route(Screen.Contact))
    }

    // Daily reminders: from the 5th day of the trial a message each day, after the trial the
    // payment page opens once a day.
    var nudge by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(auth.profile.loaded, auth.user) {
        val u = auth.user
        if (u != null && !u.isAdmin && auth.profile.loaded) {
            val now = System.currentTimeMillis()
            val a = computeAccess(u, auth.profile, now)
            if (!a.terminated) {
                val dayIndex = (endOfDay(now) - endOfDay(auth.profile.createdAt)) / DAY_MS
                if (a.phase == "trial" && dayIndex >= 5 && account.firstTimeToday("nudge", now)) {
                    nudge = "Your free trial ends on " + dateOnly(a.until) + ". Subscribe to keep seeing Live and Pre-Match."
                } else if (a.phase == "expired" && account.firstTimeToday("pay", now)) {
                    stack.add(Route(Screen.Subscribe))
                }
            }
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        modifier = if (access.terminated) Modifier.blur(14.dp) else Modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0E0E0E),
                drawerContentColor = White
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val me = auth.user
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                        Text("Payout Tracker", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Menu", color = Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        if (me == null) {
                            Button(
                                onClick = {
                                    closeDrawer()
                                    openLogin()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Continue with Google", fontWeight = FontWeight.Bold) }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(me.name.ifBlank { me.email }, 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        me.name.ifBlank { me.email },
                                        color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        me.email,
                                        color = Gray, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    closeDrawer()
                                    auth.logout()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = Red, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Log Out", color = Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Divider(color = LineColor)
                    Spacer(Modifier.height(8.dp))
                    MenuItem("Home", Icons.Filled.Home, White, current.screen == Screen.Home) { goHome() }
                    MenuItem("Live", Icons.Filled.FiberManualRecord, Green, current.screen == Screen.Live) {
                        push(Route(Screen.Live))
                    }
                    MenuItem("Pre-Match", Icons.Filled.Schedule, PreMatchColors[0], current.screen == Screen.PreMatch) {
                        push(Route(Screen.PreMatch))
                    }
                    MenuItem("Results", Icons.Filled.EmojiEvents, GoldColors[0], current.screen == Screen.Results) {
                        push(Route(Screen.Results))
                    }
                    MenuItem(
                        "Strategy", Icons.Filled.Lightbulb, StrategyColors[0],
                        current.screen == Screen.Strategy || current.screen == Screen.StrategyPost
                    ) { push(Route(Screen.Strategy)) }
                    MenuItem(
                        "Track Payout", Icons.Filled.AttachMoney, GoldColors[0],
                        current.screen == Screen.Targets || current.screen == Screen.Detail
                    ) { push(Route(Screen.Targets)) }
                    MenuItem(
                        "Summary", Icons.Filled.BarChart, SummaryColors[0],
                        current.screen == Screen.Summary || current.screen == Screen.SummaryDetail,
                        indent = true
                    ) { push(Route(Screen.Summary)) }
                    MenuItem(
                        "Brief Report", Icons.Filled.Description, ReportColors[0],
                        current.screen == Screen.Report || current.screen == Screen.ReportDetail,
                        indent = true
                    ) { push(Route(Screen.Report)) }
                    if (me != null && !isAdmin) {
                        MenuItem("Subscribe", Icons.Filled.Payments, Green, current.screen == Screen.Subscribe) {
                            push(Route(Screen.Subscribe))
                        }
                    }
                    if (isAdmin) {
                        MenuItem(
                            "Admin Panel", Icons.Filled.AdminPanelSettings, Green,
                            current.screen == Screen.Admin || current.screen == Screen.AdminEvent ||
                                current.screen == Screen.AdminPost || current.screen == Screen.AdminUsers ||
                                current.screen == Screen.AdminReports || current.screen == Screen.AdminInbox
                        ) { push(Route(Screen.Admin)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Divider(color = LineColor)
                    Spacer(Modifier.height(8.dp))
                    MenuItem("Messages", Icons.Filled.Email, Green, current.screen == Screen.Mailbox) {
                        if (me == null) openLogin() else push(Route(Screen.Mailbox))
                    }
                    MenuItem("Telegram", Icons.AutoMirrored.Filled.Send, MessageColors[0], false) {
                        openLink(LINK_TELEGRAM)
                    }
                    MenuItem("Contact Us", Icons.Filled.SupportAgent, UpdateColors[0], current.screen == Screen.Contact) {
                        closeDrawer()
                        openContact()
                    }
                    MenuItem("Exit App", Icons.AutoMirrored.Filled.ExitToApp, Red, false) { onExit() }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        when (current.screen) {
            Screen.Home -> HomeScreen(
                state = state,
                repo = repo,
                user = auth.user,
                onLive = { push(Route(Screen.Live)) },
                onPreMatch = { push(Route(Screen.PreMatch)) },
                onResults = { push(Route(Screen.Results)) },
                onStrategy = { push(Route(Screen.Strategy)) },
                onStrategyPost = { id -> push(Route(Screen.StrategyPost, docId = id)) },
                onAdmin = { push(Route(Screen.Admin)) },
                onProfile = { if (auth.user == null) openLogin() else showProfile = true },
                welcome = auth.welcome,
                onWelcomeDone = { auth.welcome = false },
                onTrack = { push(Route(Screen.Targets)) },
                onSummary = { push(Route(Screen.Summary)) },
                onReport = { push(Route(Screen.Report)) },
                onContact = { openContact() },
                onTelegram = { openLink(LINK_TELEGRAM) },
                onLogin = { openLogin() },
                onSubscribe = { push(Route(Screen.Subscribe)) },
                onMailbox = { push(Route(Screen.Mailbox)) },
                access = access,
                unread = if (auth.user != null && auth.profile.loaded) account.unread(auth.profile.createdAt).size else 0,
                onMenu = openMenu
            )

            Screen.Live -> EventListScreen(
                title = "Live",
                colors = LiveColors,
                live = true,
                events = repo.liveEvents,
                emptyText = "No live events right now.",
                repo = repo,
                access = access,
                onLogin = { openLogin() },
                onSubscribe = { push(Route(Screen.Subscribe)) },
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu
            )

            Screen.PreMatch -> EventListScreen(
                title = "Pre-Match",
                colors = PreMatchColors,
                live = false,
                events = repo.preMatchEvents,
                emptyText = "No upcoming events.",
                repo = repo,
                access = access,
                onLogin = { openLogin() },
                onSubscribe = { push(Route(Screen.Subscribe)) },
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu
            )

            Screen.Results -> ResultsScreen(
                repo = repo,
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu
            )

            Screen.Strategy -> StrategyListScreen(
                repo = repo,
                signedIn = auth.user != null,
                onLogin = { openLogin() },
                onOpen = { id -> push(Route(Screen.StrategyPost, docId = id)) },
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu
            )

            Screen.StrategyPost -> {
                val post = repo.findPost(current.docId)
                if (post == null) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    StrategyPostScreen(
                        post = post,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

            Screen.AdminUsers -> if (isAdmin) {
                AdminUsersScreen(account, onBack = { stack.removeAt(stack.lastIndex) }, onMenu = openMenu)
            } else {
                LaunchedEffect(current) {
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
            }

            Screen.AdminReports -> if (isAdmin) {
                AdminReportsScreen(account, onBack = { stack.removeAt(stack.lastIndex) }, onMenu = openMenu)
            } else {
                LaunchedEffect(current) {
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
            }

            Screen.AdminInbox -> if (isAdmin) {
                AdminInboxScreen(account, onBack = { stack.removeAt(stack.lastIndex) }, onMenu = openMenu)
            } else {
                LaunchedEffect(current) {
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
            }

            Screen.Mailbox -> MailboxScreen(
                account = account,
                onBack = { stack.removeAt(stack.lastIndex) },
                onMenu = openMenu
            )

            Screen.Contact -> {
                val me = auth.user
                if (me == null) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    ContactScreen(
                        account = account,
                        user = me,
                        onGroup = { openLink(LINK_GROUP) },
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

            Screen.Subscribe -> {
                val me = auth.user
                if (me == null) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    SubscribeScreen(
                        account = account,
                        gate = gate,
                        user = me,
                        access = access,
                        now = repo.now,
                        onReport = { id -> push(Route(Screen.PayReport, docId = id)) },
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

            Screen.PayReport -> {
                val me = auth.user
                if (me == null) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else {
                    PayReportScreen(
                        account = account,
                        user = me,
                        paymentId = current.docId ?: "",
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

            Screen.Admin, Screen.AdminEvent, Screen.AdminPost -> {
                if (!isAdmin) {
                    LaunchedEffect(current) {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                } else if (current.screen == Screen.Admin) {
                    AdminScreen(
                        repo = repo,
                        gate = gate,
                        account = account,
                        onUsers = { push(Route(Screen.AdminUsers)) },
                        onReports = { push(Route(Screen.AdminReports)) },
                        onInbox = { push(Route(Screen.AdminInbox)) },
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu,
                        onNewEvent = { push(Route(Screen.AdminEvent, docId = "new")) },
                        onEditEvent = { id -> push(Route(Screen.AdminEvent, docId = id)) },
                        onNewPost = { push(Route(Screen.AdminPost)) }
                    )
                } else if (current.screen == Screen.AdminEvent) {
                    EventEditorScreen(
                        initial = repo.findEvent(current.docId),
                        repo = repo,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                } else {
                    PostEditorScreen(
                        repo = repo,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onMenu = openMenu
                    )
                }
            }

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
                        onContactMessage = { openContact() },
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

    if (access.terminated) TerminatedOverlay(onLogout = { auth.logout() })
    }

    AppPopups(
        account = account,
        auth = auth,
        access = access,
        onMailboxOpen = current.screen == Screen.Mailbox,
        nudge = nudge,
        onNudgeLater = { nudge = null },
        onSubscribe = {
            nudge = null
            push(Route(Screen.Subscribe))
        }
    )

    val profileUser = auth.user
    if (showProfile && profileUser != null) {
        ProfileDialog(
            user = profileUser,
            access = access,
            onLogout = {
                showProfile = false
                auth.logout()
            },
            onDismiss = { showProfile = false }
        )
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
private fun AppPopups(
    account: AccountRepo,
    auth: AuthManager,
    access: Access,
    onMailboxOpen: Boolean,
    nudge: String?,
    onNudgeLater: () -> Unit,
    onSubscribe: () -> Unit
) {
    // A new message is shown in full; OK closes it and the next unread one appears
    if (auth.user != null && auth.profile.loaded && !access.terminated && !onMailboxOpen) {
        val next = account.unread(auth.profile.createdAt).firstOrNull()
        if (next != null) {
            MessagePopup(next) { account.markSeen(next.createdAt) }
        }
    }
    if (nudge != null) NudgeDialog(nudge, onSubscribe = onSubscribe, onLater = onNudgeLater)
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
