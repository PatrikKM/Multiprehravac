package com.example.multiprehrava

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.multiprehrava.ui.theme.GreenPrimary
import com.example.multiprehrava.ui.theme.MultiprehravačTheme
import com.example.multiprehrava.ui.theme.TextWhite
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        LanguageManager.init(this)

        setContent {
            val context = LocalContext.current
            var currentLanguage by remember { mutableStateOf(LanguageManager.getLanguage(context)) }

            val localizedResources = remember(currentLanguage) {
                val locale = Locale(currentLanguage)
                Locale.setDefault(locale)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                context.createConfigurationContext(config).resources
            }

            val localizedConfiguration = remember(currentLanguage) {
                localizedResources.configuration
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalResources provides localizedResources
            ) {
                MultiprehravačTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainContent(
                            currentLanguage = currentLanguage,
                            onLanguageChange = { newLang ->
                                currentLanguage = newLang
                                LanguageManager.saveLanguage(context, newLang)
                                LanguageManager.setLocale(context, newLang)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }

    var urlInput by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showShareCastDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var selectedPlaybackQuality by rememberSaveable { mutableStateOf("auto") }
    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    var currentTitle by rememberSaveable { mutableStateOf("") }
    var currentSubtitles by remember { mutableStateOf<List<SubtitleTrack>>(emptyList()) }
    var selectedSubtitleLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    var activePlaylist by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var currentPlaylistIndex by remember { mutableIntStateOf(-1) }

    var nativeSubtitleLines by remember { mutableStateOf<List<SubtitleLine>>(emptyList()) }
    var playerCurrentTimeMs by remember { mutableLongStateOf(0L) }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(mediaController) {
        while (true) {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    playerCurrentTimeMs = controller.currentPosition
                }
            }
            delay(150)
        }
    }

    val exitAppAction: () -> Unit = {
        try {
            mediaController?.let { controller ->
                controller.stop()
                controller.clearMediaItems()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val serviceIntent = Intent(context, PlaybackService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(context, context.getString(R.string.app_exited), Toast.LENGTH_SHORT).show()
        (context as? Activity)?.finishAndRemoveTask()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, context.getString(R.string.notifications_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.notifications_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val playTrack: (SearchResultItem) -> Unit = { track ->
        urlInput = track.url
        isLoading = true
        scope.launch {
            val lines = CzechSubtitleFetcher.fetchCzechAiSubtitles(track.url)
            nativeSubtitleLines = lines
        }
        scope.launch {
            val result = MediaHelper.extractMedia(context, track.url, preferredResolution = selectedPlaybackQuality)
            isLoading = false
            result.onSuccess { media ->
                currentTitle = media.title
                currentSubtitles = media.subtitles
                selectedSubtitleLanguage = null

                HistoryManager.addHistoryItem(
                    context,
                    HistoryItem(title = media.title, url = track.url, thumbnailUrl = media.thumbnailUrl)
                )

                mediaController?.let { controller ->
                    val subtitleConfigs = media.subtitles.map { sub ->
                        var cleanUrl = sub.url
                        if (cleanUrl.contains("timedtext") && !cleanUrl.contains("fmt=vtt")) {
                            cleanUrl = if (cleanUrl.contains("fmt=")) {
                                cleanUrl.replace(Regex("fmt=[^&]+"), "fmt=vtt")
                            } else {
                                "$cleanUrl&fmt=vtt"
                            }
                        }

                        MediaItem.SubtitleConfiguration.Builder(cleanUrl.toUri())
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(sub.language)
                            .setLabel(sub.name)
                            .setRoleFlags(C.ROLE_FLAG_CAPTION)
                            .setSelectionFlags(if (sub.isAutoGenerated || sub.language.startsWith("cs")) C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT else 0)
                            .build()
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(media.streamUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(media.title)
                                .build()
                        )
                        .build()

                    controller.setMediaItem(mediaItem)
                    controller.trackSelectionParameters = controller.trackSelectionParameters
                        .buildUpon()
                        .setSelectUndeterminedTextLanguage(true)
                        .setPreferredTextLanguage("cs")
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    controller.playWhenReady = true
                    controller.prepare()
                    controller.play()
                    isFullscreen = true
                }
            }.onFailure { error ->
                Toast.makeText(context, context.getString(R.string.loading_error, error.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    val playMediaAction: (String) -> Unit = { urlToPlay ->
        playTrack(
            SearchResultItem(
                title = "Media",
                url = urlToPlay,
                uploader = "",
                duration = "",
                thumbnailUrl = null
            )
        )
    }

    val startDownloadWithQualityAction: (String) -> Unit = { qualitySpec ->
        showDownloadDialog = false
        if (urlInput.isNotBlank()) {
            isDownloading = true
            val typeText = when (qualitySpec) {
                "video_1080" -> context.getString(R.string.video_1080_title)
                "video_720" -> context.getString(R.string.video_720_title)
                "video_480" -> context.getString(R.string.video_480_title)
                "audio_320" -> context.getString(R.string.audio_320_title)
                else -> context.getString(R.string.audio_128_title)
            }
            Toast.makeText(context, context.getString(R.string.download_started, typeText), Toast.LENGTH_SHORT).show()
            scope.launch {
                val result = MediaHelper.downloadMedia(context, urlInput.trim(), qualitySpec = qualitySpec) { progress, _ ->
                    downloadProgress = progress
                }
                isDownloading = false
                result.onSuccess { path ->
                    Toast.makeText(context, context.getString(R.string.download_success, path), Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, context.getString(R.string.download_error, error.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.enter_url_first), Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val fileName = getFileName(context, selectedUri) ?: context.getString(R.string.local_file_name)
            currentTitle = fileName
            currentSubtitles = emptyList()
            selectedSubtitleLanguage = null

            HistoryManager.addHistoryItem(
                context,
                HistoryItem(title = fileName, url = selectedUri.toString(), thumbnailUrl = null)
            )
            mediaController?.let { controller ->
                val mediaItem = MediaItem.Builder()
                    .setUri(selectedUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(fileName)
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.playWhenReady = true
                controller.prepare()
                controller.play()
                isFullscreen = true
            }
        }
    }

    val playNextTrack: () -> Unit = {
        val next = PlaylistManager.getNextTrack()
        if (next != null) {
            Toast.makeText(context, context.getString(R.string.next_in_queue, next.title), Toast.LENGTH_SHORT).show()
            playTrack(next)
        } else {
            Toast.makeText(context, context.getString(R.string.end_of_playlist), Toast.LENGTH_SHORT).show()
        }
    }

    val playPreviousTrack: () -> Unit = {
        val prev = PlaylistManager.getPreviousTrack()
        if (prev != null) {
            Toast.makeText(context, context.getString(R.string.previous_track, prev.title), Toast.LENGTH_SHORT).show()
            playTrack(prev)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.voice_command_recognized, spokenText), Toast.LENGTH_SHORT).show()
                when (val command = VoiceCommandAgent.parseCommand(spokenText)) {
                    is VoiceCommandAgent.CommandResult.SearchAndPlay -> {
                        searchQuery = command.query
                        isSearching = true
                        scope.launch {
                            val res = MediaHelper.searchYouTube(command.query)
                            isSearching = false
                            res.onSuccess { items ->
                                if (items.isNotEmpty()) {
                                    PlaylistManager.setPlaylist(items, 0)
                                    playTrack(items[0])
                                } else {
                                    Toast.makeText(context, "Nenalezeny žádné výsledky", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    is VoiceCommandAgent.CommandResult.PlayNext -> playNextTrack()
                    is VoiceCommandAgent.CommandResult.PlayPrevious -> playPreviousTrack()
                    is VoiceCommandAgent.CommandResult.Pause -> mediaController?.pause()
                    is VoiceCommandAgent.CommandResult.Resume -> mediaController?.play()
                    is VoiceCommandAgent.CommandResult.Download -> showDownloadDialog = true
                    is VoiceCommandAgent.CommandResult.ChangeQuality -> {
                        selectedPlaybackQuality = command.quality
                        if (urlInput.isNotBlank()) playMediaAction(urlInput)
                    }
                    is VoiceCommandAgent.CommandResult.ExitApp -> exitAppAction()
                    is VoiceCommandAgent.CommandResult.Unknown -> {
                        Toast.makeText(context, context.getString(R.string.unrecognized_command, command.rawText), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val launchVoiceRecognizer: () -> Unit = {
        try {
            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Řekněte hlasový příkaz (např. \"Přehrát Queen\", \"Další\", \"Pauza\")...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.voice_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        var activeListener: Player.Listener? = null

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller

                var isSubstituting = false

                activeListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            val nextTrack = PlaylistManager.getNextTrack()
                            if (nextTrack != null) {
                                Toast.makeText(context, context.getString(R.string.playing_next_auto, nextTrack.title), Toast.LENGTH_SHORT).show()
                                playTrack(nextTrack)
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        if (isSubstituting) return

                        for (group in tracks.groups) {
                            if (group.type == C.TRACK_TYPE_TEXT) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        val format = group.getTrackFormat(i)
                                        val lang = format.language ?: ""
                                        val label = format.label ?: ""

                                        if (!lang.startsWith("cs", ignoreCase = true) && !label.contains("tlang=cs") && !label.contains("AI")) {
                                            isSubstituting = true
                                            try {
                                                var targetIndex = -1
                                                for (j in 0 until group.length) {
                                                    val fmt = group.getTrackFormat(j)
                                                    if (fmt.language.equals("cs", ignoreCase = true) || fmt.label?.contains("AI") == true || fmt.label?.contains("Čeština") == true) {
                                                        targetIndex = j
                                                        break
                                                    }
                                                }
                                                if (targetIndex >= 0) {
                                                    val override = TrackSelectionOverride(group.mediaTrackGroup, targetIndex)
                                                    controller.trackSelectionParameters = controller.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                        .setOverrideForType(override)
                                                        .build()
                                                }
                                            } finally {
                                                isSubstituting = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                activeListener?.let { controller.addListener(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            activeListener?.let { mediaController?.removeListener(it) }
            MediaController.releaseFuture(controllerFuture)
        }
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showEqualizerDialog) {
        EqualizerDialog(onDismiss = { showEqualizerDialog = false })
    }

    if (showPermissionsDialog) {
        PermissionsDialog(
            onDismiss = { showPermissionsDialog = false },
            onRequestNotificationPermission = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            historyItems = historyItems,
            onDismiss = { showHistoryDialog = false },
            onPlayItem = { urlToPlay ->
                urlInput = urlToPlay
                if (urlToPlay.startsWith("content://") || urlToPlay.startsWith("file://")) {
                    currentTitle = getFileName(context, urlToPlay.toUri()) ?: "Lokální soubor"
                    currentSubtitles = emptyList()
                    selectedSubtitleLanguage = null
                    mediaController?.let { controller ->
                        val mediaItem = MediaItem.Builder()
                            .setUri(urlToPlay.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(currentTitle)
                                    .build()
                            )
                            .build()
                        controller.setMediaItem(mediaItem)
                        controller.playWhenReady = true
                        controller.prepare()
                        controller.play()
                        isFullscreen = true
                    }
                } else {
                    playMediaAction(urlToPlay)
                }
            },
            onRemoveItem = { urlToRemove ->
                HistoryManager.removeItem(context, urlToRemove)
                historyItems = HistoryManager.getHistory(context)
            },
            onClearAll = {
                HistoryManager.clearHistory(context)
                historyItems = emptyList()
            }
        )
    }

    if (showShareCastDialog) {
        ShareCastDialog(
            title = currentTitle,
            url = urlInput,
            onDismiss = { showShareCastDialog = false }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            },
            title = { Text(stringResource(R.string.exit_app_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(R.string.exit_app_desc), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        exitAppAction()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.quit))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showQualityDialog) {
        PlaybackQualityDialog(
            selectedQuality = selectedPlaybackQuality,
            onDismiss = { showQualityDialog = false },
            onSelectQuality = { qual ->
                selectedPlaybackQuality = qual
                if (urlInput.isNotBlank()) {
                    playMediaAction(urlInput)
                }
            }
        )
    }

    if (showDownloadDialog) {
        DownloadQualityDialog(
            onDismiss = { showDownloadDialog = false },
            onSelectQuality = { qualitySpec ->
                startDownloadWithQualityAction(qualitySpec)
            }
        )
    }

    if (isFullscreen) {
        FullscreenPlayer(
            mediaController = mediaController,
            hasSubtitles = currentSubtitles.isNotEmpty() || nativeSubtitleLines.isNotEmpty(),
            subtitleLines = nativeSubtitleLines,
            currentTimeMs = playerCurrentTimeMs,
            selectedPlaybackQuality = selectedPlaybackQuality,
            onSubtitlesClick = { showSubtitleDialog = true },
            onQualityClick = { showQualityDialog = true },
            onEqualizerClick = { showEqualizerDialog = true },
            onExitFullscreen = { isFullscreen = false },
            onDownloadClick = { showDownloadDialog = true }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {},
                    actions = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.control_bar),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Prominent Language Toggle Button in TopAppBar
                                Surface(
                                    color = GreenPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        onLanguageChange(if (currentLanguage == "cs") "en" else "cs")
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (currentLanguage == "cs") "CZ" else "EN",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = GreenPrimary
                                        )
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = {
                                        historyItems = HistoryManager.getHistory(context)
                                        showHistoryDialog = true
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.History, contentDescription = stringResource(R.string.history), tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                }

                                FilledTonalIconButton(
                                    onClick = { showHelpDialog = true },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Help, contentDescription = stringResource(R.string.help), tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                }

                                FilledTonalIconButton(
                                    onClick = { showPermissionsDialog = true },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = stringResource(R.string.permissions), tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                }

                                FilledTonalIconButton(
                                    onClick = { showExitDialog = true },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Icon(Icons.Default.PowerSettingsNew, contentDescription = stringResource(R.string.quit), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact Header Banner below Control Bar (App name + Support badge only)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(GreenPrimary, Color(0xFF00B0FF))
                                    ),
                                    shape = RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // "Podpoř mě" Buy Me a Coffee badge under Control Bar
                    Surface(
                        color = Color(0xFFFFC107).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/patrikmohler".toUri())
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.cannot_open_link), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "☕", fontSize = 12.sp)
                            Text(
                                text = stringResource(R.string.support_me),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFFFC107)
                            )
                        }
                    }
                }

                // Ultra-Compact Search Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                GreenPrimary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = stringResource(R.string.search_youtube),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.search_caption),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(Color(0xFF0F141C), RoundedCornerShape(8.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (searchQuery.isNotEmpty()) GreenPrimary else GreenPrimary.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(stringResource(R.string.search_placeholder), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                            cursorBrush = SolidColor(GreenPrimary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    if (searchQuery.isNotBlank()) {
                                                        isSearching = true
                                                        scope.launch {
                                                            val result = MediaHelper.searchYouTube(searchQuery)
                                                            isSearching = false
                                                            result.onSuccess { items ->
                                                                searchResults = items
                                                                if (items.isEmpty()) {
                                                                    Toast.makeText(context, context.getString(R.string.no_results), Toast.LENGTH_SHORT).show()
                                                                }
                                                            }.onFailure { error ->
                                                                Toast.makeText(context, context.getString(R.string.search_error, error.localizedMessage), Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { launchVoiceRecognizer() },
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.voice_search_desc),
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        isSearching = true
                                        scope.launch {
                                            val result = MediaHelper.searchYouTube(searchQuery)
                                            isSearching = false
                                            result.onSuccess { items ->
                                                searchResults = items
                                                if (items.isEmpty()) {
                                                    Toast.makeText(context, context.getString(R.string.no_results), Toast.LENGTH_SHORT).show()
                                                }
                                            }.onFailure { error ->
                                                Toast.makeText(context, context.getString(R.string.search_error, error.localizedMessage), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isSearching && searchQuery.isNotBlank(),
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search_button_desc),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Search Results List
                        if (searchResults.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.search_results_title, searchResults.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                                TextButton(
                                    onClick = { searchResults = emptyList() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(stringResource(R.string.hide), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(searchResults) { index, item ->
                                    Card(
                                        onClick = {
                                            val currentSearchList = searchResults.toList()
                                            searchResults = emptyList()
                                            PlaylistManager.setPlaylist(currentSearchList, index)
                                            playTrack(item)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 72.dp, height = 44.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.DarkGray)
                                            ) {
                                                if (!item.thumbnailUrl.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = item.thumbnailUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }
                                                if (item.duration.isNotEmpty()) {
                                                    Surface(
                                                        color = Color.Black.copy(alpha = 0.8f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(2.dp)
                                                    ) {
                                                        Text(
                                                            text = item.duration,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 9.sp,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.uploader.ifBlank { "YouTube" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Icon(
                                                Icons.Default.PlayCircleFilled,
                                                contentDescription = stringResource(R.string.play),
                                                tint = GreenPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Direct URL Card - Ultra Compact
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(13.dp))
                                Text(
                                    text = stringResource(R.string.play_from_url),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.paste_link),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(38.dp)
                                    .background(Color(0xFF0F141C), RoundedCornerShape(8.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (urlInput.isNotEmpty()) GreenPrimary else GreenPrimary.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(15.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (urlInput.isEmpty()) {
                                            Text(stringResource(R.string.url_placeholder), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        BasicTextField(
                                            value = urlInput,
                                            onValueChange = { urlInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                            cursorBrush = SolidColor(GreenPrimary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(
                                                onDone = {
                                                    if (urlInput.isNotBlank()) {
                                                        playMediaAction(urlInput.trim())
                                                    }
                                                }
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (urlInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = { urlInput = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        playMediaAction(urlInput.trim())
                                    }
                                },
                                enabled = !isLoading && urlInput.isNotBlank(),
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(stringResource(R.string.play), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(stringResource(R.string.open), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            FilledTonalButton(
                                onClick = { showDownloadDialog = true },
                                enabled = !isDownloading && urlInput.isNotBlank(),
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                if (isDownloading) {
                                    Text(
                                        "${downloadProgress.toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(stringResource(R.string.download), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Now Playing Header & Actions Bar
                if (currentTitle.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (currentPlaylistIndex > 0) {
                                        IconButton(
                                            onClick = { playPreviousTrack() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    if (currentPlaylistIndex in 0 until (activePlaylist.size - 1)) {
                                        IconButton(
                                            onClick = { playNextTrack() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            // Quick Action Chips Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Quality Chip
                                FilterChip(
                                    selected = selectedPlaybackQuality != "auto",
                                    onClick = { showQualityDialog = true },
                                    label = {
                                        Text(
                                            if (selectedPlaybackQuality != "auto") "${selectedPlaybackQuality}p" else stringResource(R.string.quality),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // 2. Equalizer Chip
                                FilterChip(
                                    selected = AudioFxManager.isEnabled,
                                    onClick = { showEqualizerDialog = true },
                                    label = {
                                        Text(
                                            if (AudioFxManager.isEnabled) "${stringResource(R.string.help_eq_title).split(" ").first()} ON" else stringResource(R.string.help_eq_title).split(" ").first(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // 3. A.I Subtitles Chip (ALWAYS VISIBLE)
                                FilterChip(
                                    selected = true,
                                    onClick = { selectSubtitleLanguage(context, mediaController, "cs") },
                                    label = {
                                        Text(
                                            stringResource(R.string.ai_subtitles),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // 4. Subtitles Chip
                                if (currentSubtitles.isNotEmpty()) {
                                    FilterChip(
                                        selected = selectedSubtitleLanguage != null,
                                        onClick = { showSubtitleDialog = true },
                                        label = {
                                            Text(
                                                if (selectedSubtitleLanguage != null) "${stringResource(R.string.subtitles)} ($selectedSubtitleLanguage)" else stringResource(R.string.subtitles),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // 5. Cast Chip
                                FilterChip(
                                    selected = false,
                                    onClick = { showShareCastDialog = true },
                                    label = { Text(stringResource(R.string.cast), style = MaterialTheme.typography.labelMedium, fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Embedded Video Player Card - Drastically Increased Height & Spacious
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GreenPrimary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaController != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = mediaController
                                        useController = true
                                        setShowSubtitleButton(true)
                                        setShowNextButton(true)
                                        setShowPreviousButton(true)
                                        subtitleView?.apply {
                                            setUserDefaultStyle()
                                            setUserDefaultTextSize()
                                            visibility = View.VISIBLE
                                        }
                                        keepScreenOn = true
                                        setFullscreenButtonClickListener {
                                            isFullscreen = true
                                        }
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = mediaController
                                    playerView.keepScreenOn = true
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            NativeSubtitleOverlay(
                                subtitleLines = nativeSubtitleLines,
                                currentTimeMs = playerCurrentTimeMs,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        } else {
                            CircularProgressIndicator(color = GreenPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun selectSubtitleLanguage(context: Context, controller: MediaController?, targetLang: String) {
    controller?.let { player ->
        val currentTracks = player.currentTracks
        var setTarget = false
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                var targetIndex = -1
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language ?: ""
                    val label = format.label ?: ""
                    if (lang.equals(targetLang, ignoreCase = true) || label.contains("AI", ignoreCase = true) || label.contains("Čeština", ignoreCase = true)) {
                        targetIndex = i
                        break
                    }
                }
                if (targetIndex >= 0) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, targetIndex)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(override)
                        .build()
                    setTarget = true
                } else {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(targetLang)
                        .build()
                    setTarget = true
                }
            }
        }
        if (setTarget) {
            Toast.makeText(context, context.getString(R.string.ai_subs_activated), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.en_subs_requested), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun EqualizerDialog(onDismiss: () -> Unit) {
    var isEnabled by remember { mutableStateOf(AudioFxManager.isEnabled) }
    var selectedPreset by remember { mutableIntStateOf(AudioFxManager.currentPreset.toInt()) }
    val presets = remember { AudioFxManager.getPresets() }
    val numBands = remember { AudioFxManager.getNumberOfBands() }
    val bandRange = remember { AudioFxManager.getBandLevelRange() }

    var bassBoostValue by remember { mutableFloatStateOf(AudioFxManager.bassBoostStrength.toFloat()) }
    var loudnessValue by remember { mutableFloatStateOf(AudioFxManager.loudnessGainMb.toFloat()) }

    var dummyStateTrigger by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            "Ekvalizér a Zvukové Efekty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Zavřít", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Master Switch Row
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.activate_equalizer), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    isEnabled = checked
                                    AudioFxManager.setEnabled(checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = GreenPrimary
                                )
                            )
                        }
                    }

                    if (isEnabled) {
                        // Presets Row
                        if (presets.isNotEmpty()) {
                            Text(stringResource(R.string.presets), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEachIndexed { index, name ->
                                    val isSel = (selectedPreset == index)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            selectedPreset = index
                                            AudioFxManager.usePreset(index.toShort())
                                            dummyStateTrigger++
                                        },
                                        label = { Text(name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        // 5-Band Equalizer Sliders
                        Text(stringResource(R.string.freq_bands), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GreenPrimary)
                        key(dummyStateTrigger) {
                            for (i in 0 until numBands) {
                                val bandIndex = i.toShort()
                                val centerFreqHz = AudioFxManager.getCenterFreq(bandIndex) / 1000
                                val freqLabel = if (centerFreqHz >= 1000) "${centerFreqHz / 1000} kHz" else "$centerFreqHz Hz"
                                val currentLevel = AudioFxManager.getBandLevel(bandIndex)

                                var sliderVal by remember { mutableFloatStateOf(currentLevel.toFloat()) }

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(freqLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        val dbValue = (sliderVal / 100).toInt()
                                        Text(
                                            text = if (dbValue > 0) "+$dbValue dB" else "$dbValue dB",
                                            fontSize = 11.sp,
                                            color = GreenPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Slider(
                                        value = sliderVal,
                                        onValueChange = { newLevel ->
                                            sliderVal = newLevel
                                            AudioFxManager.setBandLevel(bandIndex, newLevel.toInt().toShort())
                                        },
                                        valueRange = bandRange.first.toFloat()..bandRange.second.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = GreenPrimary,
                                            activeTrackColor = GreenPrimary
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Bass Boost Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Speaker, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.bass_boost), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${(bassBoostValue / 10).toInt()}%", fontSize = 11.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = bassBoostValue,
                                onValueChange = { newVal ->
                                    bassBoostValue = newVal
                                    AudioFxManager.setBassBoost(newVal.toInt().toShort())
                                },
                                valueRange = 0f..1000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = GreenPrimary,
                                    activeTrackColor = GreenPrimary
                                )
                            )
                        }

                        // Loudness Enhancer Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.loudness_enhancer), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                val loudnessDb = (loudnessValue / 100).toInt()
                                Text("+$loudnessDb dB", fontSize = 11.sp, color = GreenPrimary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = loudnessValue,
                                onValueChange = { newVal ->
                                    loudnessValue = newVal
                                    AudioFxManager.setLoudnessEnhancer(newVal.toInt())
                                },
                                valueRange = 0f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = GreenPrimary,
                                    activeTrackColor = GreenPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
fun HistoryDialog(
    historyItems: List<HistoryItem>,
    onDismiss: () -> Unit,
    onPlayItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.playback_history), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Historie je prázdná",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyItems) { item ->
                        Card(
                            onClick = {
                                onPlayItem(item.url)
                                onDismiss()
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!item.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 64.dp, height = 42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 64.dp, height = 42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = GreenPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveItem(item.url) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            if (historyItems.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Help, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.help), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Buy Me a Coffee Support Card
                Card(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/patrikmohler".toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_link), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFC107).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("☕", fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.buy_me_coffee_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFFFFC107))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.buy_me_coffee_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    }
                }

                HelpItem(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.help_search_title),
                    description = stringResource(R.string.help_search_desc)
                )
                HelpItem(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.help_eq_title),
                    description = stringResource(R.string.help_eq_desc)
                )
                HelpItem(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.help_github_title),
                    description = stringResource(R.string.help_github_desc)
                )
                HelpItem(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.help_voice_title),
                    description = stringResource(R.string.help_voice_desc)
                )
                HelpItem(
                    icon = Icons.Default.SkipNext,
                    title = stringResource(R.string.help_playlist_title),
                    description = "Při vyhledání a spuštění skladby se z výsledků vyhledávání vytvoří playlist. Po skončení jedné skladby přehráváče automaticky pokračuje na další. K dispozici jsou tlačítka 'Předchozí' (⏮) a 'Další' (⏭)."
                )
                HelpItem(
                    icon = Icons.Default.ClosedCaption,
                    title = stringResource(R.string.help_subs_title),
                    description = stringResource(R.string.help_subs_desc)
                )
                HelpItem(
                    icon = Icons.Default.Cast,
                    title = stringResource(R.string.help_cast_title),
                    description = stringResource(R.string.help_cast_desc)
                )
                HelpItem(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.help_history_title),
                    description = stringResource(R.string.help_history_desc)
                )
                HelpItem(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.help_local_title),
                    description = stringResource(R.string.help_local_desc)
                )
                HelpItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.help_download_title),
                    description = stringResource(R.string.help_download_desc)
                )
                HelpItem(
                    icon = Icons.Default.Fullscreen,
                    title = stringResource(R.string.help_fullscreen_title),
                    description = stringResource(R.string.help_fullscreen_desc)
                )
                HelpItem(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.help_bg_title),
                    description = stringResource(R.string.help_bg_desc)
                )
                HelpItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.help_quit_title),
                    description = stringResource(R.string.help_quit_desc)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.i_understand));
            }
        }
    )
}

@Composable
fun PlaybackQualityDialog(
    selectedQuality: String,
    onDismiss: () -> Unit,
    onSelectQuality: (String) -> Unit
) {
    val qualities = listOf(
        "auto" to stringResource(R.string.auto_quality),
        "1080" to stringResource(R.string.quality_1080),
        "720" to stringResource(R.string.quality_720),
        "480" to stringResource(R.string.quality_480),
        "360" to stringResource(R.string.quality_360)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.playback_quality), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qualities.forEach { (code, label) ->
                    val isSelected = (selectedQuality == code)
                    Card(
                        onClick = {
                            onSelectQuality(code)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun DownloadQualityDialog(
    onDismiss: () -> Unit,
    onSelectQuality: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.download_quality), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.download_quality_desc), style = MaterialTheme.typography.bodyMedium)

                // Video 1080p
                Card(
                    onClick = { onSelectQuality("video_1080") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.video_1080_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.video_1080_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Video 720p
                Card(
                    onClick = { onSelectQuality("video_720") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.video_720_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.video_720_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Video 480p
                Card(
                    onClick = { onSelectQuality("video_480") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.video_480_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.video_480_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Audio 320 kbps
                Card(
                    onClick = { onSelectQuality("audio_320") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.audio_320_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.audio_320_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Audio 128 kbps
                Card(
                    onClick = { onSelectQuality("audio_128") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.audio_128_title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.audio_128_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ShareCastDialog(
    title: String,
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Cast, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.cast_share_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title.ifBlank { stringResource(R.string.active_video) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (url.isNotBlank()) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: Open in YouTube / TV App
                Card(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                setPackage("com.google.android.youtube")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            context.startActivity(intent)
                        }
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(stringResource(R.string.cast_youtube_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Spustí video v aplikaci YouTube pro odeslání na TV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: System Share Sheet
                Card(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_link_title)))
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(stringResource(R.string.share_link_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Otevře systémovou nabídku sdílení pro Smart View a Chromecast",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 3: Copy link
                Card(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("URL videa", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text(stringResource(R.string.copy_link_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                "Uloží URL adresu videa do schránky",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun HelpItem(icon: ImageVector, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(GreenPrimary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PermissionsDialog(
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val hasNotificationPermission = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(GreenPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = GreenPrimary)
            }
        },
        title = { Text(stringResource(R.string.permissions_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Přehled systémových oprávnění vyžadovaných aplikací:",
                    style = MaterialTheme.typography.bodyMedium
                )

                PermissionItem(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.perm_internet),
                    status = stringResource(R.string.active),
                    isGranted = true,
                    description = stringResource(R.string.perm_internet_desc)
                )

                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.perm_notifications),
                    status = if (hasNotificationPermission) stringResource(R.string.allowed) else stringResource(R.string.missing),
                    isGranted = hasNotificationPermission,
                    description = stringResource(R.string.perm_notifications_desc)
                )

                PermissionItem(
                    icon = Icons.Default.PlayCircle,
                    title = stringResource(R.string.perm_foreground),
                    status = stringResource(R.string.active),
                    isGranted = true,
                    description = stringResource(R.string.perm_foreground_desc)
                )

                PermissionItem(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.perm_media),
                    status = stringResource(R.string.active),
                    isGranted = true,
                    description = stringResource(R.string.perm_media_desc)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            if (!hasNotificationPermission) {
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.allow_notifications))
                }
            }
        }
    )
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    status: String,
    isGranted: Boolean,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isGranted) GreenPrimary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) GreenPrimary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullscreenPlayer(
    mediaController: MediaController?,
    hasSubtitles: Boolean = false,
    hasNextTrack: Boolean = false,
    hasPreviousTrack: Boolean = false,
    subtitleLines: List<SubtitleLine> = emptyList(),
    currentTimeMs: Long = 0L,
    selectedPlaybackQuality: String = "auto",
    onPreviousTrackClick: () -> Unit = {},
    onNextTrackClick: () -> Unit = {},
    onSubtitlesClick: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onShareCastClick: () -> Unit = {},
    onExitFullscreen: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    var isLandscape by rememberSaveable { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    var areControlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(isLandscape) {
        if (activity != null) {
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            activity.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {
            if (activity != null) {
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                val insetsController = WindowCompat.getInsetsController(activity.window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    BackHandler {
        onExitFullscreen()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (mediaController != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = mediaController
                        useController = true
                        setShowSubtitleButton(true)
                        setShowNextButton(true)
                        setShowPreviousButton(true)
                        subtitleView?.apply {
                            setUserDefaultStyle()
                            setUserDefaultTextSize()
                            visibility = View.VISIBLE
                        }
                        keepScreenOn = true
                        this.resizeMode = resizeMode
                        setFullscreenButtonClickListener {
                            onExitFullscreen()
                        }
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                areControlsVisible = (visibility == View.VISIBLE)
                            }
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = mediaController
                    playerView.keepScreenOn = true
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            NativeSubtitleOverlay(
                subtitleLines = subtitleLines,
                currentTimeMs = currentTimeMs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Overlay Controls (Previous + Next + Equalizer + Subtitles + Download + Rotation + Aspect Ratio mode)
            if (areControlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasPreviousTrack) {
                        Button(
                            onClick = onPreviousTrackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.previous))
                        }
                    }

                    if (hasNextTrack) {
                        Button(
                            onClick = onNextTrackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.next))
                        }
                    }

                    // 1. Quality / Settings Button
                    Button(
                        onClick = onQualityClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedPlaybackQuality != "auto") "${selectedPlaybackQuality}p" else stringResource(R.string.quality))
                    }

                    // 2. Equalizer Button
                    Button(
                        onClick = onEqualizerClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.help_eq_title).split(" ").first())
                    }

                    // 3. A.I titulky Button (ALWAYS VISIBLE)
                    Button(
                        onClick = onSubtitlesClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_subtitles), fontWeight = FontWeight.Bold)
                    }

                    // 4. CC Subtitles
                    if (hasSubtitles) {
                        Button(
                            onClick = onSubtitlesClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.subtitles))
                        }
                    }

                    Button(
                        onClick = onShareCastClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cast))
                    }

                    // Download Button
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.download))
                    }

                    // Aspect ratio / Fill toggle (VLC style)
                    Button(
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        val text = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> stringResource(R.string.resize_fill)
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.resize_zoom)
                            else -> stringResource(R.string.resize_fit)
                        }
                        Text(text)
                    }

                    // Rotation toggle
                    Button(
                        onClick = { isLandscape = !isLandscape },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isLandscape) stringResource(R.string.orientation_portrait) else stringResource(R.string.orientation_landscape))
                    }
                }
            }
        } else {
            CircularProgressIndicator(color = GreenPrimary)
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var fileName: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
    }
    if (fileName == null) {
        fileName = uri.path
        val cut = fileName?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            fileName = fileName?.substring(cut + 1)
        }
    }
    return fileName
}
