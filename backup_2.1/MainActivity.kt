@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.multiprehrava

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.multiprehrava.ui.theme.GreenPrimary
import com.example.multiprehrava.ui.theme.MultiprehravačTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MultiprehravačTheme {
                MainContent()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MainContent() {
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

        Toast.makeText(context, "Aplikace ukončena", Toast.LENGTH_SHORT).show()
        (context as? Activity)?.finishAndRemoveTask()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Oznámení byla úspěšně povolena", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Oznámení nebyla povolena", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Chyba načítání: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
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
                "video_1080" -> "videa 1080p (MP4)"
                "video_720" -> "videa 720p (MP4)"
                "video_480" -> "videa 480p (MP4)"
                "audio_320" -> "zvuku 320 kbps (MP3)"
                else -> "zvuku 128 kbps (MP3)"
            }
            Toast.makeText(context, "Spouštím stahování $typeText...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val result = MediaHelper.downloadMedia(context, urlInput.trim(), qualitySpec = qualitySpec) { progress, _ ->
                    downloadProgress = progress
                }
                isDownloading = false
                result.onSuccess { path ->
                    Toast.makeText(context, "Soubor úspěšně stažen do složky Stažené soubory!\n($path)", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Chyba stahování: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, "Zadejte nejprve URL adresu videa z internetu", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val fileName = getFileName(context, selectedUri) ?: "Lokální soubor"
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
            Toast.makeText(context, "Další v pořadí: ${next.title}", Toast.LENGTH_SHORT).show()
            playTrack(next)
        } else {
            Toast.makeText(context, "Konec seznamu skladeb", Toast.LENGTH_SHORT).show()
        }
    }

    val playPreviousTrack: () -> Unit = {
        val prev = PlaylistManager.getPreviousTrack()
        if (prev != null) {
            Toast.makeText(context, "Předchozí: ${prev.title}", Toast.LENGTH_SHORT).show()
            playTrack(prev)
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
                                Toast.makeText(context, "Automaticky přehrávám další: ${nextTrack.title}", Toast.LENGTH_SHORT).show()
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
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ukončit aplikaci?", fontWeight = FontWeight.Bold) },
            text = { Text("Aplikace zastaví přehrávání na pozadí, ukončí službu a bude kompletně zavřena.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        exitAppAction()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ukončit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("Zrušit")
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
            onExitFullscreen = { isFullscreen = false },
            onDownloadClick = { showDownloadDialog = true }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Multipřehrávač",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            historyItems = HistoryManager.getHistory(context)
                            showHistoryDialog = true
                        }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Historie přehrávání",
                                tint = GreenPrimary
                            )
                        }
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Nápověda",
                                tint = GreenPrimary
                            )
                        }
                        IconButton(onClick = { showPermissionsDialog = true }) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = "Seznam oprávnění",
                                tint = GreenPrimary
                            )
                        }
                        IconButton(onClick = { showExitDialog = true }) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Ukončit aplikaci",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Search Card Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("YouTube / YouTube Music") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = GreenPrimary
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

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
                                                    Toast.makeText(context, "Žádné výsledky nenalezeny", Toast.LENGTH_SHORT).show()
                                                }
                                            }.onFailure { error ->
                                                Toast.makeText(context, "Chyba vyhledávání: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isSearching && searchQuery.isNotBlank(),
                                modifier = Modifier
                                    .height(56.dp)
                                    .width(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Vyhledat",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Search Results List
                        if (searchResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (!item.thumbnailUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = item.thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(width = 80.dp, height = 50.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.DarkGray)
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2
                                                )
                                                Text(
                                                    text = "${item.uploader} ${if (item.duration.isNotEmpty()) "• ${item.duration}" else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct URL & Actions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Zadejte URL (YouTube / internet)") },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, tint = GreenPrimary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        playMediaAction(urlInput.trim())
                                    }
                                },
                                enabled = !isLoading && urlInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Přehrát",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showDownloadDialog = true },
                                enabled = !isDownloading && urlInput.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                if (isDownloading) {
                                    Text(
                                        "${downloadProgress.toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Stáhnout",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Title & Subtitle / Playlist Header
                if (currentTitle.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
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
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentPlaylistIndex > 0) {
                                    IconButton(
                                        onClick = { playPreviousTrack() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Předchozí", tint = GreenPrimary)
                                    }
                                }

                                if (currentPlaylistIndex in 0 until (activePlaylist.size - 1)) {
                                    IconButton(
                                        onClick = { playNextTrack() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Další", tint = GreenPrimary)
                                    }
                                }

                                // 1. Settings / Quality Button
                                OutlinedButton(
                                    onClick = { showQualityDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedPlaybackQuality != "auto") "${selectedPlaybackQuality}p" else "Kvalita",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                // 2. A.I titulky Button (ALWAYS VISIBLE between Settings/Quality and CC!)
                                OutlinedButton(
                                    onClick = {
                                        selectSubtitleLanguage(mediaController, "cs")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "A.I titulky",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 3. CC (Titulky)
                                if (currentSubtitles.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showSubtitleDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (selectedSubtitleLanguage != null) "Titulky ($selectedSubtitleLanguage)" else "Titulky",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                // 4. Odeslat (Cast)
                                OutlinedButton(
                                    onClick = { showShareCastDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(16.dp), tint = GreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Odeslat", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (activePlaylist.isNotEmpty() && currentPlaylistIndex in 0 until activePlaylist.size) {
                            Text(
                                text = "Playlist: Skladba ${currentPlaylistIndex + 1} z ${activePlaylist.size}" +
                                        if (currentPlaylistIndex < activePlaylist.size - 1) " • Další: ${activePlaylist[currentPlaylistIndex + 1].title}" else " • Konec seznamu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Embedded Player Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(16.dp)
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

private fun selectSubtitleLanguage(mediaController: MediaController?, language: String?) {
    mediaController?.let { controller ->
        if (language == null || language == "OFF") {
            controller.trackSelectionParameters = controller.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            var selectedOverride: TrackSelectionOverride? = null
            for (group in controller.currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.language.equals(language, ignoreCase = true) ||
                            (language.startsWith("cs", ignoreCase = true) &&
                                (format.language.equals("cs", ignoreCase = true) ||
                                 format.label?.contains("Čeština", ignoreCase = true) == true ||
                                 format.label?.contains("AI", ignoreCase = true) == true))
                        ) {
                            selectedOverride = TrackSelectionOverride(group.mediaTrackGroup, i)
                            break
                        }
                    }
                    if (selectedOverride != null) break
                }
            }

            val builder = controller.trackSelectionParameters
                .buildUpon()
                .setSelectUndeterminedTextLanguage(true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(language)

            if (selectedOverride != null) {
                builder.setOverrideForType(selectedOverride)
            }

            controller.trackSelectionParameters = builder.build()
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
        icon = { Icon(Icons.Default.History, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Historie přehrávání", fontWeight = FontWeight.Bold) },
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!item.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 60.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.DarkGray)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = GreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = item.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveItem(item.url) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Smazat",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zavřít")
            }
        },
        dismissButton = {
            if (historyItems.isNotEmpty()) {
                OutlinedButton(onClick = onClearAll) {
                    Text("Vymazat historii")
                }
            }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Nápověda k aplikaci", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HelpItem(
                    icon = Icons.Default.Search,
                    title = "Vyhledávání médií",
                    description = "Zadejte název videa nebo skladby do vyhledávacího pole a klepněte na tlačítko Lupa. Výsledky z YouTube se zobrazí v přehledném seznamu."
                )
                HelpItem(
                    icon = Icons.Default.SkipNext,
                    title = "Automatické přehrávání (Playlist)",
                    description = "Při vyhledání a spuštění skladby se z výsledků vyhledávání vytvoří playlist. Po skončení jedné skladby přehrávač automaticky pokračuje na další. K dispozici jsou tlačítka 'Předchozí' (⏮) a 'Další' (⏭)."
                )
                HelpItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Volba titulků",
                    description = "U videí s dostupnými titulky klepněte na tlačítko 'Titulky' nebo zvolte jazyk titulků v nabídce ozubeného kola (např. Čeština, Angličtina) nebo je vypněte."
                )
                HelpItem(
                    icon = Icons.Default.Cast,
                    title = "Odeslání na TV a sdílení",
                    description = "Klepnutím na tlačítko 'Odeslat' můžete přehrávané video přenést do aplikace YouTube na vaší Smart TV, streamovat přes Chromecast / Smart View nebo sdílet odkaz."
                )
                HelpItem(
                    icon = Icons.Default.History,
                    title = "Historie přehrávání",
                    description = "Klepnutím na ikonu 'Historie' v horní liště zobrazíte nedávno přehrávané položky. Klepnutím na jakoukoliv položku ji okamžitě znovu přehrajete."
                )
                HelpItem(
                    icon = Icons.Default.FolderOpen,
                    title = "Lokální soubory",
                    description = "Klepnutím na tlačítko 'Otevřít' vyberete libovolný audio nebo video soubor uložený v paměti telefonu."
                )
                HelpItem(
                    icon = Icons.Default.Download,
                    title = "Stahování audia a videa",
                    description = "Zadejte URL adresu a zvolte 'Stáhnout'. Můžete vybrat stahování celého videa (MP4) nebo pouze zvukové stopy (MP3). Soubor se uloží do složky Stažené soubory (Downloads)."
                )
                HelpItem(
                    icon = Icons.Default.Fullscreen,
                    title = "Celoobrazovkový režim",
                    description = "Během přehrávání přepněte do celoobrazovkového režimu. K dispozici jsou funkce pro titulky, přepínání skladeb, otáčení obrazovky a úpravu poměru stran."
                )
                HelpItem(
                    icon = Icons.Default.MusicNote,
                    title = "Přehrávání na pozadí",
                    description = "Přehrávač běží jako služba na popředí. Zvuk hraje i po zhasnutí obrazovky nebo při přepnutí do jiné aplikace."
                )
                HelpItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Ukončení přehrávače",
                    description = "Klepnutím na červené tlačítko Vypnout v pravém horním rohu okamžitě zastavíte přehrávání na pozadí, uvolníte paměť, ukončíte službu a zavřete aplikaci."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Rozumím")
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
        "auto" to "Automaticky (Nejvyšší dostupná)",
        "1080" to "1080p Full HD",
        "720" to "720p HD",
        "480" to "480p SD",
        "360" to "360p Úspora dat"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HighQuality, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Kvalita přehrávání", fontWeight = FontWeight.Bold) },
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zavřít")
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
        icon = { Icon(Icons.Default.Download, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Kvalita stahování", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Vyberte rozlišení videa nebo datový tok zvuku:", style = MaterialTheme.typography.bodyMedium)

                // Video 1080p
                Card(
                    onClick = { onSelectQuality("video_1080") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Video 1080p Full HD (MP4)", fontWeight = FontWeight.SemiBold)
                            Text("Nejvyšší kvalita obrazu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Video 720p
                Card(
                    onClick = { onSelectQuality("video_720") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Video 720p HD (MP4)", fontWeight = FontWeight.SemiBold)
                            Text("Vyvážená kvalita a velikost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Video 480p
                Card(
                    onClick = { onSelectQuality("video_480") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Video 480p SD (MP4)", fontWeight = FontWeight.SemiBold)
                            Text("Úspora místa v telefonu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Audio 320 kbps
                Card(
                    onClick = { onSelectQuality("audio_320") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Zvuk 320 kbps (MP3)", fontWeight = FontWeight.SemiBold)
                            Text("Vysoká kvalita zvuku (HQ)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Audio 128 kbps
                Card(
                    onClick = { onSelectQuality("audio_128") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Zvuk 128 kbps (MP3)", fontWeight = FontWeight.SemiBold)
                            Text("Standardní kvalita zvuku", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zrušit")
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
        icon = { Icon(Icons.Default.Cast, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Odeslat na TV / Sdílet", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title.ifBlank { "Aktivní video" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                if (url.isNotBlank()) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Odeslat do aplikace YouTube / TV", fontWeight = FontWeight.SemiBold)
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
                        context.startActivity(Intent.createChooser(shareIntent, "Sdílet video / Odeslat na TV"))
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Sdílet odkaz / Odeslat do zařízení", fontWeight = FontWeight.SemiBold)
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
                        Toast.makeText(context, "Odkaz zkopírován do schránky", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Zkopírovat odkaz", fontWeight = FontWeight.SemiBold)
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
            Button(onClick = onDismiss) {
                Text("Zavřít")
            }
        }
    )
}

@Composable
private fun HelpItem(icon: ImageVector, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GreenPrimary,
            modifier = Modifier.size(22.dp).padding(top = 2.dp)
        )
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
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
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = GreenPrimary) },
        title = { Text("Seznam oprávnění", fontWeight = FontWeight.Bold) },
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
                    title = "Internet (INTERNET)",
                    status = "Aktivní",
                    isGranted = true,
                    description = "Pro vyhledávání, načítání streamů a stahování videí/hudby."
                )

                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Oznámení (POST_NOTIFICATIONS)",
                    status = if (hasNotificationPermission) "Povoleno" else "Chybí",
                    isGranted = hasNotificationPermission,
                    description = "Pro ovládání přehrávače v notifikační liště na pozadí."
                )

                PermissionItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Služba na popředí",
                    status = "Aktivní",
                    isGranted = true,
                    description = "Pro nepřetržité přehrávání zvuku i při vypnuté obrazovce."
                )

                PermissionItem(
                    icon = Icons.Default.Folder,
                    title = "Přístup k médiím",
                    status = "Aktivní",
                    isGranted = true,
                    description = "Pro otevírání lokálních souborů a ukládání stažených videí."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zavřít")
            }
        },
        dismissButton = {
            if (!hasNotificationPermission) {
                OutlinedButton(onClick = onRequestNotificationPermission) {
                    Text("Povolit oznámení")
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
        shape = RoundedCornerShape(10.dp),
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
    onShareCastClick: () -> Unit = {},
    onExitFullscreen: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity

    var isLandscape by rememberSaveable { mutableStateOf(true) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
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

            // Overlay Controls (Previous + Next + Subtitles + Download + Rotation + Aspect Ratio mode)
            if (areControlsVisible) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 16.dp),
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
                            Text("Předchozí")
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
                            Text("Další")
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
                        Text(if (selectedPlaybackQuality != "auto") "${selectedPlaybackQuality}p" else "Kvalita")
                    }

                    // 2. A.I titulky Button (ALWAYS VISIBLE between Settings/Quality and CC Subtitles!)
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
                        Text("A.I titulky", fontWeight = FontWeight.Bold)
                    }

                    // 3. CC Subtitles
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
                            Text("Titulky")
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
                        Text("Odeslat")
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
                        Text("Stáhnout")
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
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Roztáhnout"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Oříznout"
                            else -> "Přizpůsobit"
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
                        Text(if (isLandscape) "Portrét" else "Na šířku")
                    }
                }
            }
        } else {
            CircularProgressIndicator(color = GreenPrimary)
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}
