package pro.dasoft.ownsonic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.dasoft.ownsonic.data.AppFailure
import pro.dasoft.ownsonic.data.LibraryStore
import pro.dasoft.ownsonic.data.MessageCode
import pro.dasoft.ownsonic.data.ServerCatalog
import pro.dasoft.ownsonic.data.ServerSettings
import pro.dasoft.ownsonic.data.ServerSource
import pro.dasoft.ownsonic.data.SubsonicClient

@Composable
private fun SourcePicker(label: String, selectedName: String, sources: List<ServerSource>, enabled: Boolean,
                         allowNone: Boolean = false, onSelected: (ServerSource?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()
        .semantics { contentDescription = label }) {
        Text(selectedName.ifBlank { stringResource(if (allowNone) R.string.no_podcast else R.string.choose_playlist) },
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (expanded && enabled) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(label) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (allowNone) TextButton(onClick = { onSelected(null); expanded = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.no_podcast))
                }
                sources.forEach { source ->
                    TextButton(onClick = { onSelected(source); expanded = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (sources.count { it.name == source.name } > 1) "${source.name} · ${source.id}" else source.name)
                    }
                }
                if (sources.isEmpty()) Text(stringResource(R.string.load_sources_hint))
            }
        }, confirmButton = {
            TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.close)) }
        })
}

@Composable
internal fun SettingsScreen(store: LibraryStore, syncing: Boolean) {
    val loaded by produceState<Result<ServerSettings>?>(null, store) {
        val result = runCatching { store.onIo { settings() } }
        withContext(Dispatchers.Main.immediate) { value = result }
    }
    val result = loaded
    if (result == null) {
        CircularProgressIndicator()
        return
    }
    SettingsForm(store, syncing, result.getOrElse { store.settings(includePassword = false) }, result.exceptionOrNull())
}

@Composable
private fun SettingsForm(store: LibraryStore, syncing: Boolean, settings: ServerSettings, initialError: Throwable?) {
    val actions = LocalLibraryActions.current
    val storeContext = LocalContext.current
    val resources = LocalResources.current
    var url by rememberSaveable { mutableStateOf(settings.url) }
    var username by rememberSaveable { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf(settings.password) }
    var playlistId by rememberSaveable { mutableStateOf(settings.playlistId) }
    var playlistName by rememberSaveable { mutableStateOf(settings.playlistName) }
    var podcastId by rememberSaveable { mutableStateOf(settings.podcastId) }
    var podcastName by rememberSaveable { mutableStateOf(settings.podcastName) }
    var episodeLimit by rememberSaveable { mutableStateOf(settings.episodeLimit.toString()) }
    var likeRating by rememberSaveable { mutableIntStateOf(settings.likeRating) }
    var dislikeRating by rememberSaveable { mutableIntStateOf(settings.dislikeRating) }
    var newestEpisodes by rememberSaveable { mutableStateOf(settings.newestEpisodes) }
    var catalog by remember { mutableStateOf<ServerCatalog?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val editable = !syncing && !loading
    var message by remember { mutableStateOf(initialError?.let { localizedMessage(resources, it) }) }
    var reminderEnabled by remember { mutableStateOf(store.ratingReminderEnabled()) }
    var automaticSyncEnabled by remember { mutableStateOf(store.automaticSyncEnabled()) }
    Text(stringResource(R.string.server_title), fontSize = 26.sp, fontWeight = FontWeight.Bold)
    OutlinedTextField(url, { url = it; catalog = null }, label = { Text(stringResource(R.string.server_url)) }, placeholder = { Text("https://music.example.com/airsonic") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), enabled = editable)
    OutlinedTextField(username, { username = it; catalog = null }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = editable)
    OutlinedTextField(password, { password = it; catalog = null }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = editable)
    OutlinedButton(enabled = editable, onClick = {
        loading = true
        message = null
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val client = SubsonicClient(settings.copy(url = url.trim().trimEnd('/'), username = username.trim(), password = password))
                val loaded = client.catalog()
                catalog = loaded
                if (playlistId.isBlank()) {
                    val initial = loaded.playlists.singleOrNull { it.name == playlistName } ?: loaded.playlists.singleOrNull()
                    if (initial != null) { playlistId = initial.id; playlistName = initial.name }
                }
                loaded.playlists.find { it.id == playlistId }?.let { playlistName = it.name }
                if (loaded.podcastError == null) {
                    val selected = loaded.podcasts.find { it.id == podcastId }
                    podcastId = selected?.id
                    podcastName = selected?.name.orEmpty()
                }
                message = when {
                    loaded.playlists.isEmpty() -> resources.getString(R.string.no_playlists)
                    loaded.podcastError != null -> resources.getString(R.string.podcasts_unavailable,
                        localizedMessage(resources, loaded.podcastError))
                    else -> resources.getString(R.string.sources_loaded)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = localizedMessage(resources, error) }
            finally { loading = false }
        }
    }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(if (loading) R.string.loading_sources else R.string.load_sources))
    }
    Text(stringResource(R.string.sync_sources), fontWeight = FontWeight.Bold)
    SourcePicker(stringResource(R.string.music_playlist),
        playlistName.takeIf { playlistId.isNotBlank() || settings.configured }.orEmpty(),
        catalog?.playlists.orEmpty(), editable && catalog != null) { source ->
        if (source != null) { playlistId = source.id; playlistName = source.name }
    }
    SourcePicker(stringResource(R.string.podcast_source), podcastName.takeIf { podcastId != null }.orEmpty(),
        catalog?.podcasts.orEmpty(), editable, allowNone = true) { source ->
        podcastId = source?.id
        podcastName = source?.name.orEmpty()
        if (source == null && episodeLimit.toIntOrNull() !in 1..50) episodeLimit = "5"
    }
    SourcePicker(stringResource(R.string.podcast_selection),
        stringResource(if (newestEpisodes) R.string.newest_episodes else R.string.oldest_episodes),
        listOf(ServerSource("oldest", stringResource(R.string.oldest_episodes)),
            ServerSource("newest", stringResource(R.string.newest_episodes))), editable && podcastId != null) { selected ->
        newestEpisodes = selected?.id == "newest"
    }
    OutlinedTextField(episodeLimit, { episodeLimit = it }, label = { Text(stringResource(R.string.episode_limit)) },
        supportingText = { Text(stringResource(R.string.episode_limit_hint)) },
        isError = episodeLimit.toIntOrNull() !in 1..50,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = editable && podcastId != null, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.rating_actions), fontWeight = FontWeight.Bold)
    val ratingChoices = (1..5).map { ServerSource(it.toString(), resources.getString(R.string.rating_value, it)) }
    SourcePicker(stringResource(R.string.like_rating), stringResource(R.string.rating_value, likeRating), ratingChoices, editable) {
        likeRating = it!!.id.toInt()
    }
    SourcePicker(stringResource(R.string.dislike_rating), stringResource(R.string.rating_value, dislikeRating), ratingChoices, editable) {
        dislikeRating = it!!.id.toInt()
    }
    Text(stringResource(R.string.rating_actions_hint), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.sources_hint), style = MaterialTheme.typography.bodySmall)
    Button(enabled = editable, onClick = { actions.run {
        try {
            require(settings.configured || playlistId.isNotBlank()) { throw AppFailure(MessageCode.CHOOSE_PLAYLIST_ERROR) }
            require(catalog == null || catalog!!.playlists.any { it.id == playlistId }) { throw AppFailure(MessageCode.CHOOSE_PLAYLIST_ERROR) }
            val count = episodeLimit.toIntOrNull()
            require(count != null && count in 1..50) { throw AppFailure(MessageCode.EPISODE_LIMIT_ERROR) }
            val value = ServerSettings(url.trim().trimEnd('/'), username.trim(), password,
                playlistId, playlistName, podcastId, podcastName, count,
                likeRating = likeRating, dislikeRating = dislikeRating, newestEpisodes = newestEpisodes)
            SubsonicClient(value)
            store.onIo { saveSettings(value) }
            message = resources.getString(R.string.settings_saved)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { message = localizedMessage(resources, error, R.string.save_settings_error) }
    } }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save_settings)) }
    message?.let { Text(it) }
    Text(stringResource(R.string.security_hint), style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.rating_reminder), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.reminder_description), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = reminderEnabled, onCheckedChange = { enabled -> actions.run {
            runCatching { store.onIo { setRatingReminderEnabled(enabled) } }
                .onSuccess { reminderEnabled = enabled }
                .onFailure { message = resources.getString(R.string.reminder_error) }
        } }, enabled = !actions.busy, modifier = Modifier.semantics { contentDescription = resources.getString(R.string.rating_reminder) })
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.automatic_sync), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.automatic_description), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = automaticSyncEnabled, onCheckedChange = { enabled -> actions.run {
            runCatching {
                store.onIo { setAutomaticSyncEnabled(enabled) }
                withContext(Dispatchers.IO) { AutoSyncScheduler.schedule(context = storeContext, refresh = true) }
            }.onSuccess { automaticSyncEnabled = enabled }
                .onFailure { message = resources.getString(R.string.automatic_error) }
        } }, enabled = !actions.busy, modifier = Modifier.semantics { contentDescription = resources.getString(R.string.automatic_sync) })
    }
}
