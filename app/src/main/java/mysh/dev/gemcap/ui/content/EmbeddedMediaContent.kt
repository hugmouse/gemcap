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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.DisableSelection
import coil.compose.AsyncImage
import coil.request.ImageRequest
import mysh.dev.gemcap.domain.GeminiContent
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
    onDownloadMedia: (String, ByteArray, String) -> Unit
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
        MediaType.IMAGE -> "Tap to view image"
        MediaType.AUDIO -> "Tap to load audio"
        MediaType.VIDEO -> "Tap to load video"
        MediaType.UNKNOWN -> "Tap to load file"
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
                    contentDescription = "Load media",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DisableSelection {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open in new tab") },
                    onClick = {
                        showMenu = false
                        onOpenInNewTab(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy link address") },
                    onClick = {
                        showMenu = false
                        onCopyLink(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles
) {
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
                text = "Loading ${getMediaType(item.mimeType).label.lowercase()}...",
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
    onDownloadMedia: (String, ByteArray, String) -> Unit
) {
    val mediaType = getMediaType(item.mimeType)
    val data = item.data?.bytes ?: ByteArray(0)

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
        onOpenInNewTab = onOpenInNewTab,
        onCopyLink = onCopyLink,
        onDownloadMedia = onDownloadMedia
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedInlineImage(
    item: GeminiContent.EmbeddedMedia,
    imageData: ByteArray,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val imageMetadata = remember(item.mimeType, imageData) {
        buildImageMetadataText(item.mimeType, imageData)
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
                        .data(imageData)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Image from ${item.url}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    contentScale = ContentScale.Fit
                )
            }

            DisableSelection {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in new tab") },
                        onClick = {
                            showMenu = false
                            onOpenInNewTab(item.url)
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link address") },
                        onClick = {
                            showMenu = false
                            onCopyLink(item.url)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownloadMedia(item.url, imageData, item.mimeType)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hide image") },
                        onClick = {
                            showMenu = false
                            onCollapseMedia(item.id)
                        }
                    )
                }
            }
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
private fun LoadedAudioMediaCard(
    item: GeminiContent.EmbeddedMedia,
    audioData: ByteArray,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, ByteArray, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var mediaPlayer by remember(item.id, item.data) { mutableStateOf<MediaPlayer?>(null) }
    var isPreparing by remember(item.id, item.data) { mutableStateOf(false) }
    var isPlaying by remember(item.id, item.data) { mutableStateOf(false) }
    var playbackError by remember(item.id, item.data) { mutableStateOf<String?>(null) }
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { "Audio" }

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
                runCatching { preparedPlayer.start() }
                    .onSuccess { isPlaying = true }
                    .onFailure { error ->
                        isPlaying = false
                        playbackError = error.message ?: "Unable to start audio"
                    }
            }
            player.setOnCompletionListener { completedPlayer ->
                isPlaying = false
                runCatching { completedPlayer.seekTo(0) }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                runCatching { failedPlayer.release() }
                mediaPlayer = null
                isPreparing = false
                isPlaying = false
                playbackError = "Playback error"
                true
            }
            player.setDataSource(ByteArrayMediaDataSource(audioData))
            mediaPlayer = player
            isPreparing = true
            player.prepareAsync()
        } catch (error: Exception) {
            runCatching { player.release() }
            mediaPlayer = null
            isPreparing = false
            isPlaying = false
            playbackError = error.message ?: "Unable to play this audio file"
        }
    }

    fun togglePlayback() {
        if (isPreparing) return

        val player = mediaPlayer
        if (player == null) {
            prepareAndStartPlayer()
            return
        }

        if (isPlaying) {
            runCatching { player.pause() }
                .onSuccess {
                    isPlaying = false
                    playbackError = null
                }
                .onFailure { error ->
                    playbackError = error.message ?: "Unable to pause audio"
                }
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
            playbackError = error.message ?: "Unable to restart audio"
        }
    }

    DisposableEffect(item.id, item.data) {
        onDispose {
            releasePlayer()
        }
    }

    val statusText = when {
        playbackError != null -> playbackError!!
        isPreparing -> "Preparing audio..."
        isPlaying -> "Playing"
        mediaPlayer != null -> "Paused"
        else -> "Ready to play"
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
                    onClick = { togglePlayback() },
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
                        text = "${item.mimeType} - ${formatBytes(audioData.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playbackError != null) {
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
                        onClick = { togglePlayback() },
                        enabled = !isPreparing
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                    IconButton(
                        onClick = { restartPlayback() },
                        enabled = !isPreparing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Restart"
                        )
                    }
                }
            }
        }

        DisableSelection {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open in new tab") },
                    onClick = {
                        showMenu = false
                        onOpenInNewTab(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy link address") },
                    onClick = {
                        showMenu = false
                        onCopyLink(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        showMenu = false
                        onDownloadMedia(item.url, audioData, item.mimeType)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedBinaryMediaCard(
    item: GeminiContent.EmbeddedMedia,
    mediaType: MediaType,
    data: ByteArray,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, ByteArray, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val icon = when (mediaType) {
        MediaType.AUDIO -> Icons.Default.MusicNote
        MediaType.VIDEO -> Icons.Default.PlayCircle
        MediaType.UNKNOWN -> Icons.Default.BrokenImage
        MediaType.IMAGE -> Icons.Default.Image
    }
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { mediaType.label }

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
                        text = "${mediaType.label} loaded (${formatBytes(data.size)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DisableSelection {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open in new tab") },
                    onClick = {
                        showMenu = false
                        onOpenInNewTab(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy link address") },
                    onClick = {
                        showMenu = false
                        onCopyLink(item.url)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        showMenu = false
                        onDownloadMedia(item.url, data, item.mimeType)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun ErrorMediaCard(
    item: GeminiContent.EmbeddedMedia,
    onRetryClick: () -> Unit
) {
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
                contentDescription = "Load error",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Failed to load ${getMediaType(item.mimeType).label.lowercase()}",
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
                    text = "Tap to retry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class MediaType(val label: String) {
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video"),
    UNKNOWN("File")
}

private fun getMediaType(mimeType: String): MediaType {
    return when {
        mimeType.startsWith("image/") -> MediaType.IMAGE
        mimeType.startsWith("audio/") -> MediaType.AUDIO
        mimeType.startsWith("video/") -> MediaType.VIDEO
        else -> MediaType.UNKNOWN
    }
}

private fun buildImageMetadataText(mimeType: String, imageData: ByteArray): String {
    val dimensions = extractImageDimensions(imageData)
    val dimensionsText = dimensions?.let { "${it.first} x ${it.second}" } ?: "unknown size"
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
