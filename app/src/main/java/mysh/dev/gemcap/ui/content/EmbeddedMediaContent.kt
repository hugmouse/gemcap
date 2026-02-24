package mysh.dev.gemcap.ui.content

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.DisableSelection
import coil.compose.AsyncImage
import coil.request.ImageRequest
import mysh.dev.gemcap.R
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.StableByteArray
import java.util.Locale
import kotlin.math.min

@Composable
fun EmbeddedMediaContent(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    onLoadMedia: (Int) -> Unit,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray, String) -> Unit
) {
    when (item.state) {
        GeminiContent.EmbeddedMediaState.COLLAPSED -> {
            CollapsedMediaCard(
                item = item,
                styles = styles,
                onLoadClick = { onLoadMedia(item.id) },
                onOpenInNewTab = onOpenInNewTab,
                onCopyLink = onCopyLink
            )
        }

        GeminiContent.EmbeddedMediaState.LOADING -> {
            LoadingMediaCard(item = item, styles = styles)
        }

        GeminiContent.EmbeddedMediaState.LOADED -> {
            LoadedMediaCard(
                item = item,
                onCollapseMedia = onCollapseMedia,
                onOpenInNewTab = onOpenInNewTab,
                onCopyLink = onCopyLink,
                onDownloadMedia = onDownloadMedia
            )
        }

        GeminiContent.EmbeddedMediaState.ERROR -> {
            ErrorMediaCard(
                item = item,
                onRetryClick = { onLoadMedia(item.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollapsedMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    onLoadClick: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val mediaType = getMediaType(item.mimeType)
    val icon = when (mediaType) {
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.AUDIO -> Icons.Default.MusicNote
        MediaType.VIDEO -> Icons.Default.PlayCircle
        MediaType.UNKNOWN -> Icons.Default.BrokenImage
    }

    val displayText = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { item.url }

    val actionText = when (mediaType) {
        MediaType.IMAGE -> stringResource(R.string.embedded_media_action_view_image)
        MediaType.AUDIO -> stringResource(R.string.embedded_media_action_load_audio)
        MediaType.VIDEO -> stringResource(R.string.embedded_media_action_load_video)
        MediaType.UNKNOWN -> stringResource(R.string.embedded_media_action_load_file)
    }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onLoadClick,
                    onLongClick = { showMenu = true }
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = styles.primaryColor
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayText,
                        style = styles.linkStyle,
                        color = styles.primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.8f)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.embedded_media_load_content_description),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        MediaContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            url = item.url,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink
        )
    }
}

@Composable
private fun MediaContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    url: String,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownload: (() -> Unit)? = null,
    extraItems: (@Composable (() -> Unit))? = null
) {
    DisableSelection {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.embedded_media_menu_open_new_tab)) },
                onClick = {
                    onDismiss()
                    onOpenInNewTab(url)
                },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.embedded_media_menu_copy_link)) },
                onClick = {
                    onDismiss()
                    onCopyLink(url)
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
            )
            onDownload?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.embedded_media_menu_download)) },
                    onClick = {
                        onDismiss()
                        it()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                )
            }
            extraItems?.invoke()
        }
    }
}

@Composable
private fun LoadingMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles
) {
    val mediaLabel = mediaTypeLabel(getMediaType(item.mimeType)).lowercase(Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = styles.primaryColor
            )
            Text(
                text = stringResource(R.string.embedded_media_loading, mediaLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadedMediaCard(
    item: GeminiContent.EmbeddedMedia,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray, String) -> Unit
) {
    val mediaType = getMediaType(item.mimeType)
    val data = item.data ?: StableByteArray(ByteArray(0))

    if (mediaType == MediaType.IMAGE) {
        LoadedInlineImage(
            item = item,
            imageData = data,
            onCollapseMedia = onCollapseMedia,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownloadMedia = onDownloadMedia
        )
        return
    }

    if (mediaType == MediaType.AUDIO) {
        LoadedAudioMediaCard(
            item = item,
            audioData = data,
            onCollapseMedia = onCollapseMedia,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownloadMedia = onDownloadMedia
        )
        return
    }

    LoadedBinaryMediaCard(
        item = item,
        mediaType = mediaType,
        data = data,
        onCollapseMedia = onCollapseMedia,
        onOpenInNewTab = onOpenInNewTab,
        onCopyLink = onCopyLink,
        onDownloadMedia = onDownloadMedia
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedInlineImage(
    item: GeminiContent.EmbeddedMedia,
    imageData: StableByteArray,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray, String) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val unknownSizeLabel = stringResource(R.string.embedded_media_unknown_size)
    val imageMetadata = remember(item.mimeType, imageData) {
        buildImageMetadataText(item.mimeType, imageData.bytes, unknownSizeLabel)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .combinedClickable(
                        onClick = { onCollapseMedia(item.id) },
                        onLongClick = { showMenu = true }
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageData.bytes)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(
                        R.string.embedded_media_image_content_description,
                        item.url
                    ),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            MediaContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                url = item.url,
                onOpenInNewTab = onOpenInNewTab,
                onCopyLink = onCopyLink,
                onDownload = { onDownloadMedia(item.url, imageData, item.mimeType) },
                extraItems = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.embedded_media_hide_image)) },
                        onClick = {
                            showMenu = false
                            onCollapseMedia(item.id)
                        }
                    )
                }
            )
        }

        Text(
            text = imageMetadata,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberAudioPlayerState(
    itemId: Int,
    audioData: StableByteArray,
    unableToStartMessage: String,
    playbackErrorMessage: String,
    unableToPlayFileMessage: String,
    unableToPauseMessage: String,
    unableToRestartMessage: String,
    onPrepared: () -> Unit = {},
    onCompletion: () -> Unit = {},
    onError: (String) -> Unit = {}
): AudioPlayerState {
    return remember(itemId, audioData) {
        AudioPlayerState(
            audioData = audioData,
            unableToStartMessage = unableToStartMessage,
            playbackErrorMessage = playbackErrorMessage,
            unableToPlayFileMessage = unableToPlayFileMessage,
            unableToPauseMessage = unableToPauseMessage,
            unableToRestartMessage = unableToRestartMessage,
            onPrepared = onPrepared,
            onCompletion = onCompletion,
            onError = onError
        )
    }
}

@Stable
private class AudioPlayerState(
    private val audioData: StableByteArray,
    private val unableToStartMessage: String,
    private val playbackErrorMessage: String,
    private val unableToPlayFileMessage: String,
    private val unableToPauseMessage: String,
    private val unableToRestartMessage: String,
    private val onPrepared: () -> Unit,
    private val onCompletion: () -> Unit,
    private val onError: (String) -> Unit
) {
    var mediaPlayer by mutableStateOf<MediaPlayer?>(null)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set

    fun release() = releasePlayer()

    fun releasePlayer() {
        val player = mediaPlayer ?: return
        runCatching {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
        mediaPlayer = null
        isPreparing = false
        isPlaying = false
    }

    fun prepareAndStartPlayer() {
        releasePlayer()
        playbackError = null
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setOnPreparedListener { preparedPlayer ->
                isPreparing = false
                playbackError = null
                onPrepared()
                runCatching { preparedPlayer.start() }
                    .onSuccess { isPlaying = true }
                    .onFailure { error ->
                        isPlaying = false
                        val message = error.message ?: unableToStartMessage
                        playbackError = message
                        onError(message)
                    }
            }
            player.setOnCompletionListener { completedPlayer ->
                isPlaying = false
                onCompletion()
                runCatching { completedPlayer.seekTo(0) }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                runCatching { failedPlayer.release() }
                mediaPlayer = null
                isPreparing = false
                isPlaying = false
                playbackError = playbackErrorMessage
                onError(playbackErrorMessage)
                true
            }
            player.setDataSource(ByteArrayMediaDataSource(audioData.bytes))
            mediaPlayer = player
            isPreparing = true
            player.prepareAsync()
        } catch (error: Exception) {
            runCatching { player.release() }
            mediaPlayer = null
            isPreparing = false
            isPlaying = false
            val message = error.message ?: unableToPlayFileMessage
            playbackError = message
            onError(message)
        }
    }

    fun start() {
        if (isPreparing) return

        val player = mediaPlayer
        if (player == null) {
            prepareAndStartPlayer()
            return
        }

        runCatching { player.start() }
            .onSuccess {
                isPlaying = true
                playbackError = null
            }
            .onFailure {
                prepareAndStartPlayer()
            }
    }

    fun stop() {
        if (isPreparing) return

        val player = mediaPlayer ?: return
        runCatching { player.pause() }
            .onSuccess {
                isPlaying = false
                playbackError = null
            }
            .onFailure { error ->
                val message = error.message ?: unableToPauseMessage
                playbackError = message
                onError(message)
            }
    }

    fun togglePlayback() {
        if (isPlaying) {
            stop()
        } else {
            start()
        }
    }

    fun restartPlayback() {
        if (isPreparing) return

        val player = mediaPlayer
        if (player == null) {
            prepareAndStartPlayer()
            return
        }

        runCatching {
            player.pause()
            player.seekTo(0)
            isPlaying = false
            playbackError = null
        }.onFailure { error ->
            val message = error.message ?: unableToRestartMessage
            playbackError = message
            onError(message)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedAudioMediaCard(
    item: GeminiContent.EmbeddedMedia,
    audioData: StableByteArray,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val audioLabel = mediaTypeLabel(MediaType.AUDIO)
    val playContentDescription = stringResource(R.string.embedded_media_play)
    val pauseContentDescription = stringResource(R.string.embedded_media_pause)
    val restartContentDescription = stringResource(R.string.embedded_media_restart)
    val preparingAudioText = stringResource(R.string.embedded_media_status_preparing_audio)
    val playingText = stringResource(R.string.embedded_media_status_playing)
    val pausedText = stringResource(R.string.embedded_media_status_paused)
    val readyToPlayText = stringResource(R.string.embedded_media_status_ready)
    val audioPlayerState = rememberAudioPlayerState(
        itemId = item.id,
        audioData = audioData,
        unableToStartMessage = stringResource(R.string.embedded_media_error_unable_start_audio),
        playbackErrorMessage = stringResource(R.string.embedded_media_error_playback),
        unableToPlayFileMessage = stringResource(R.string.embedded_media_error_unable_play_file),
        unableToPauseMessage = stringResource(R.string.embedded_media_error_unable_pause_audio),
        unableToRestartMessage = stringResource(R.string.embedded_media_error_unable_restart_audio)
    )
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { audioLabel }

    DisposableEffect(item.id, item.data) {
        onDispose {
            audioPlayerState.release()
        }
    }

    val statusText = when {
        audioPlayerState.playbackError != null -> audioPlayerState.playbackError!!
        audioPlayerState.isPreparing -> preparingAudioText
        audioPlayerState.isPlaying -> playingText
        audioPlayerState.mediaPlayer != null -> pausedText
        else -> readyToPlayText
    }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .combinedClickable(
                    onClick = { audioPlayerState.togglePlayback() },
                    onLongClick = { showMenu = true }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.mimeType} - ${formatBytes(audioData.bytes.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (audioPlayerState.playbackError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { audioPlayerState.togglePlayback() },
                        enabled = !audioPlayerState.isPreparing
                    ) {
                        Icon(
                            imageVector = if (audioPlayerState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (audioPlayerState.isPlaying) {
                                pauseContentDescription
                            } else {
                                playContentDescription
                            }
                        )
                    }
                    IconButton(
                        onClick = { audioPlayerState.restartPlayback() },
                        enabled = !audioPlayerState.isPreparing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = restartContentDescription
                        )
                    }
                }
            }
        }

        MediaContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            url = item.url,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownload = { onDownloadMedia(item.url, audioData, item.mimeType) },
            extraItems = {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.embedded_media_hide_audio)) },
                    onClick = {
                        showMenu = false
                        onCollapseMedia(item.id)
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedBinaryMediaCard(
    item: GeminiContent.EmbeddedMedia,
    mediaType: MediaType,
    data: StableByteArray,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val mediaLabel = mediaTypeLabel(mediaType)
    val icon = when (mediaType) {
        MediaType.AUDIO -> Icons.Default.MusicNote
        MediaType.VIDEO -> Icons.Default.PlayCircle
        MediaType.UNKNOWN -> Icons.Default.BrokenImage
        MediaType.IMAGE -> Icons.Default.Image
    }
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { mediaLabel }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .combinedClickable(
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.embedded_media_loaded_format,
                            mediaLabel,
                            formatBytes(data.bytes.size)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        MediaContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            url = item.url,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownload = { onDownloadMedia(item.url, data, item.mimeType) },
            extraItems = {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.embedded_media_hide_media,
                                mediaLabel.lowercase(Locale.getDefault())
                            )
                        )
                    },
                    onClick = {
                        showMenu = false
                        onCollapseMedia(item.id)
                    }
                )
            }
        )
    }
}

@Composable
private fun ErrorMediaCard(
    item: GeminiContent.EmbeddedMedia,
    onRetryClick: () -> Unit
) {
    val mediaLabel = mediaTypeLabel(getMediaType(item.mimeType)).lowercase(Locale.getDefault())
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetryClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = stringResource(R.string.embedded_media_load_error_content_description),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.embedded_media_failed_to_load, mediaLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                item.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.embedded_media_tap_to_retry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class MediaType {
    IMAGE,
    AUDIO,
    VIDEO,
    UNKNOWN
}

@Composable
private fun mediaTypeLabel(mediaType: MediaType): String {
    return when (mediaType) {
        MediaType.IMAGE -> stringResource(R.string.embedded_media_type_image)
        MediaType.AUDIO -> stringResource(R.string.embedded_media_type_audio)
        MediaType.VIDEO -> stringResource(R.string.embedded_media_type_video)
        MediaType.UNKNOWN -> stringResource(R.string.embedded_media_type_file)
    }
}

private fun getMediaType(mimeType: String): MediaType {
    return when {
        mimeType.startsWith("image/") -> MediaType.IMAGE
        mimeType.startsWith("audio/") -> MediaType.AUDIO
        mimeType.startsWith("video/") -> MediaType.VIDEO
        else -> MediaType.UNKNOWN
    }
}

private fun buildImageMetadataText(
    mimeType: String,
    imageData: ByteArray,
    unknownSizeLabel: String
): String {
    val dimensions = extractImageDimensions(imageData)
    val dimensionsText = dimensions?.let { "${it.first} x ${it.second}" } ?: unknownSizeLabel
    return "$mimeType - $dimensionsText - ${formatBytes(imageData.size)}"
}

private fun extractImageDimensions(imageData: ByteArray): Pair<Int, Int>? {
    if (imageData.isEmpty()) return null

    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatBytes(size: Int): String {
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> String.format(Locale.US, "%.1fKB", size / 1024.0)
        else -> String.format(Locale.US, "%.1fMB", size / (1024.0 * 1024.0))
    }
}

private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0L || position >= data.size) return -1
        val start = position.toInt()
        val length = min(size, data.size - start)
        System.arraycopy(data, start, buffer, offset, length)
        return length
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}
