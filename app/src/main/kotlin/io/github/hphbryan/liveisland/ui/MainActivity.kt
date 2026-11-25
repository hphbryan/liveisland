package io.github.hphbryan.liveisland.ui

import io.github.hphbryan.liveisland.platform.SettingsStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.hphbryan.liveisland.R
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** An app whose page to open, from the Adjust button on a relayed Live Update; taken once shown. */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = appToOpen(intent)
        setContent { LiveIslandTheme { LiveIslandApp(openRequest.value) { openRequest.value = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appToOpen(intent)?.let { openRequest.value = it }
    }

    /** The app named by [EXTRA_APP], if it is installed (the activity is exported, so anything can send one). */
    private fun appToOpen(intent: Intent): String? = intent.getStringExtra(EXTRA_APP)?.takeIf {
        runCatching { packageManager.getApplicationInfo(it, 0) }.isSuccess
    }

    companion object {
        /** Package name of the app whose page to open. */
        const val EXTRA_APP = "io.github.hphbryan.liveisland.extra.APP"

        /**
         * Opens LiveIsland from a notification or tile, bringing back the screen already open rather than
         * stacking another. The same intent everywhere, as the system shares one PendingIntent among them.
         */
        fun open(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Screens by name; an app's page is "app:<package>". Kept as strings so the back stack survives recreation. */
private object Route {
    const val HOME = "home"
    const val ADD = "add"
    const val ACTIVITY = "activity"
    const val SAMPLES = "samples"
    const val ABOUT = "about"
    const val KEYWORDS = "keywords"
    const val APP = "app:"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveIslandApp(openRequest: String?, onOpened: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf(Route.HOME)) }
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            stack = listOf(Route.HOME, Route.APP + openRequest)
            onOpened()
        }
    }
    val route = stack.last()
    val push = { r: String -> stack = stack + r }
    val pop = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settingsState = store.state.collectAsStateWithLifecycle().value
    val paused = settingsState.paused
    val overview = settingsState.overview
    // Only Samsung phones have a Now Bar to look at.
    val testSent = stringResource(
        if (io.github.hphbryan.liveisland.platform.Device.isSamsung) R.string.test_sent_samsung else R.string.test_sent,
    )
    val pausedDone = stringResource(R.string.paused_done)
    val resumedDone = stringResource(R.string.resumed_done)
    val undo = stringResource(R.string.undo)
    val appPackage = route.removePrefix(Route.APP).takeIf { route.startsWith(Route.APP) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            appPackage != null -> rememberAppInfo(appPackage).label
                            route == Route.ADD -> stringResource(R.string.picker_title)
                            route == Route.ACTIVITY -> stringResource(R.string.activity_title)
                            route == Route.SAMPLES -> stringResource(R.string.diagnostics_title)
                            route == Route.ABOUT -> stringResource(R.string.about_title)
                            route == Route.KEYWORDS -> stringResource(R.string.shared_keywords_title)
                            else -> stringResource(R.string.app_name)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = pop) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                        }
                    }
                },
                actions = {
                    if (appPackage != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_copy)) },
                                onClick = {
                                    menuOpen = false
                                    copyRules(context, store, listOf(appPackage))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_paste)) },
                                onClick = {
                                    menuOpen = false
                                    pasteRules(context, store)?.takeIf { it != appPackage }?.let { push(Route.APP + it) }
                                },
                            )
                        }
                    }
                    if (route == Route.HOME) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (paused) R.string.action_resume else R.string.action_pause)) },
                                onClick = {
                                    menuOpen = false
                                    val pausing = !paused
                                    setPaused(context, store, pausing)
                                    // Say what happened: the island may be off screen, or showing setup steps.
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(
                                            message = if (pausing) pausedDone else resumedDone,
                                            actionLabel = undo,
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) setPaused(context, store, !pausing)
                                    }
                                },
                            )
                            if (Build.VERSION.SDK_INT >= 37) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.overview_setting)) },
                                    trailingIcon = { Checkbox(checked = overview, onCheckedChange = null) },
                                    onClick = { store.setOverview(!overview) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_copy_all)) },
                                onClick = {
                                    menuOpen = false
                                    copyRules(context, store, installedPackages(context, store.state.value.enabled).sorted())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rules_paste)) },
                                onClick = {
                                    menuOpen = false
                                    pasteRules(context, store)?.let { push(Route.APP + it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shared_keywords_title)) },
                                onClick = {
                                    menuOpen = false
                                    push(Route.KEYWORDS)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.diagnostics_title)) },
                                onClick = {
                                    menuOpen = false
                                    push(Route.SAMPLES)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_title)) },
                                onClick = {
                                    menuOpen = false
                                    push(Route.ABOUT)
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding -> CompositionLocalProvider(LocalSnackbar provides snackbar) {
        val openApp = { pkg: String -> push(Route.APP + pkg) }
        when {
            appPackage != null -> AppScreen(appPackage, padding, onActivity = { push(Route.ACTIVITY) }, onSharedKeywords = { push(Route.KEYWORDS) })
            route == Route.KEYWORDS -> SharedKeywordsScreen(padding)
            route == Route.ADD -> AddAppScreen(padding, onOpenApp = openApp)
            route == Route.ACTIVITY -> ActivityScreen(padding, onOpenApp = openApp)
            route == Route.SAMPLES -> SamplesScreen(padding)
            route == Route.ABOUT -> AboutScreen(padding)
            else -> HomeScreen(
                padding,
                onOpenApp = openApp,
                onAddApp = { push(Route.ADD) },
                onActivity = { push(Route.ACTIVITY) },
                onTestSent = { scope.launch { snackbar.showSnackbar(testSent) } },
            )
        }
    } }
}
