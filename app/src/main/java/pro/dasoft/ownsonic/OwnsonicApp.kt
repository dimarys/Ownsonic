package pro.dasoft.ownsonic

import kotlinx.coroutines.Dispatchers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import pro.dasoft.ownsonic.data.LibraryStore

@Composable
internal fun OwnsonicApp(store: LibraryStore, workManager: WorkManager, player: MediaController?, connectionError: String?) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val actions = remember(scope) { LibraryActions(scope) }
    val writeFailure by store.writeFailure.collectAsState(context = Dispatchers.Main.immediate)
    val loadFailure by store.loadFailure.collectAsState(context = Dispatchers.Main.immediate)
    val library by store.library.collectAsState(context = Dispatchers.Main.immediate)
    val work by remember(workManager) { workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.NAME) }.collectAsState(initial = emptyList(), context = Dispatchers.Main.immediate)
    val job = work.firstOrNull { !it.state.isFinished } ?: work.firstOrNull()
    val waitingAutomaticSync = job?.tags?.contains(AutoSyncScheduler.REQUEST_TAG) == true && job.state != WorkInfo.State.RUNNING
    val syncing = job?.state?.isFinished == false && !waitingAutomaticSync
    var screen by rememberSaveable { mutableStateOf("Player") }
    LaunchedEffect(syncing) {
        if (syncing && screen in listOf("Player", "Aerostat")) screen = "Sync"
    }
    val displayedScreen = if (syncing && screen in listOf("Player", "Aerostat")) "Sync" else screen
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) { while (true) { delay(500); tick++ } }
    CompositionLocalProvider(LocalLibraryActions provides actions) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = Color(0xFF101713),
            secondaryContainer = Color(0xFF35452C), onSecondaryContainer = Accent,
            background = Color(0xFF101713), surface = Surface)) {
            Scaffold(bottomBar = {
                NavigationBar(containerColor = Surface) {
                    listOf("Player" to "🎵", "Aerostat" to "🎈", "Sync" to "🔄", "Settings" to "⚙️").forEach { (name, icon) ->
                        NavigationBarItem(selected = displayedScreen == name, onClick = { screen = name },
                            enabled = !actions.busy && (!syncing || name !in listOf("Player", "Aerostat")),
                            icon = { Text(icon, fontSize = 23.sp) }, label = { Text(stringResource(when (name) { "Player" -> R.string.player; "Sync" -> R.string.sync; "Aerostat" -> R.string.aerostat; else -> R.string.settings }), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    // Reserve space for metadata, playback controls, ratings, and padding.
                    val musicCoverHeight = (maxHeight - 470.dp).coerceIn(180.dp, 280.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("ownsonic", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.offline_first), fontSize = 11.sp, color = Accent)
                        }
                        if (writeFailure) Text(stringResource(R.string.playback_save_error), color = MaterialTheme.colorScheme.error)
                        actions.error?.let { Text(localizedMessage(resources, it), color = MaterialTheme.colorScheme.error) }
                        if (loadFailure) {
                            LibraryRecoveryScreen(store, actions, syncing)
                        } else when (displayedScreen) {
                            "Player" -> MusicScreen(store, library, player, syncing || actions.busy, tick, musicCoverHeight, connectionError) { screen = "Settings" }
                            "Sync" -> SyncScreen(store, library, workManager, job, syncing, waitingAutomaticSync)
                            "Aerostat" -> PodcastScreen(store, library, player, syncing || actions.busy, tick)
                            "Settings" -> SettingsScreen(store, syncing || actions.busy)
                        }
                    }
                }
            }
        }
    }
}
