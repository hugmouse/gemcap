package mysh.dev.gemcap.ui.content

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.indicator.PositionAndDurationText
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mysh.dev.gemcap.R
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.StableByteArray
import mysh.dev.gemcap.media.GemcapPlayerManager
import java.util.Locale

@Composable
fun EmbeddedMediaContent(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    playerManager: GemcapPlayerManager,
    onLoadMedia: (Int) -> Unit,
    onPlayMedia: (Int) -> Unit,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit,
    onFullscreen: (Player) -> Unit
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
                styles = styles,
                playerManager = playerManager,
                onPlayMedia = onPlayMedia,
                onCollapseMedia = onCollapseMedia,
                onOpenInNewTab = onOpenInNewTab,
                onCopyLink = onCopyLink,
                onDownloadMedia = onDownloadMedia,
                onFullscreen = onFullscreen
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
                    tint = styles.linkIconColor
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayText,
                        style = styles.linkStyle,
                        color = styles.linkTextColor,
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
            val progress = item.downloadProgress
            if (progress != null && progress.fraction > 0f) {
                CircularProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = styles.linkIconColor
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = styles.linkIconColor
                )
            }
            val loadingText = if (progress != null && progress.bytesRead > 0L) {
                stringResource(R.string.embedded_media_loading_with_size, mediaLabel, formatBytesLong(progress.bytesRead))
            } else {
                stringResource(R.string.embedded_media_loading, mediaLabel)
            }
            Text(
                text = loadingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadedMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    playerManager: GemcapPlayerManager,
    onPlayMedia: (Int) -> Unit,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit,
    onFullscreen: (Player) -> Unit
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
            styles = styles,
            playerManager = playerManager,
            onPlayMedia = onPlayMedia,
            onCollapseMedia = onCollapseMedia,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownloadMedia = onDownloadMedia
        )
        return
    }

    if (mediaType == MediaType.VIDEO) {
        LoadedVideoMediaCard(
            item = item,
            styles = styles,
            playerManager = playerManager,
            onPlayMedia = onPlayMedia,
            onCollapseMedia = onCollapseMedia,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownloadMedia = onDownloadMedia,
            onFullscreen = onFullscreen
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

private fun formatBytesLong(size: Long): String {
    return when {
        size < 1024L -> "${size}B"
        size < 1024L * 1024L -> String.format(Locale.US, "%.1fKB", size / 1024.0)
        else -> String.format(Locale.US, "%.1fMB", size / (1024.0 * 1024.0))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedInlineImage(
    item: GeminiContent.EmbeddedMedia,
    imageData: StableByteArray,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit
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
                onDownload = { onDownloadMedia(item.url, imageData, null, item.mimeType) },
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

@OptIn(UnstableApi::class)
@Composable
private fun LoadedAudioMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    playerManager: GemcapPlayerManager,
    onPlayMedia: (Int) -> Unit,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val audioLabel = mediaTypeLabel(MediaType.AUDIO)
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { audioLabel }

    val isActiveItem = playerManager.currentItemId == item.id
    val player = if (isActiveItem) playerManager.player else null

    val sizeText = if (item.dataFilePath != null) {
        formatBytesLong(java.io.File(item.dataFilePath).length())
    } else {
        formatBytes(item.data?.bytes?.size ?: 0)
    }

    // Reset playback error when this item becomes active again
    LaunchedEffect(isActiveItem) {
        if (isActiveItem) playbackError = null
    }

    // Listen for playback errors only when this is the active item
    DisposableEffect(player) {
        val listener = if (player != null) {
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.localizedMessage ?: error.errorCodeName
                }
            }.also { player.addListener(it) }
        } else null

        onDispose {
            if (player != null && listener != null) {
                player.removeListener(listener)
            }
        }
    }

    Box {
        Column(
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // File info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = styles.linkIconColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = styles.bodyMedium,
                        color = styles.bodyColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.mimeType} - $sizeText",
                        style = styles.bodySmall,
                        color = styles.bodyColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Media3 controls — only shown for the active item
            // Wrap in CompositionLocalProvider so Media3 composables pick up adaptive color
            CompositionLocalProvider(LocalContentColor provides styles.bodyColor) {
                if (player != null) {
                    PlayerProgressSlider(
                        player = player,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SeekBackButton(player, modifier = Modifier.size(36.dp))
                        PlayPauseButton(player, modifier = Modifier.size(40.dp))
                        SeekForwardButton(player, modifier = Modifier.size(36.dp))
                        PositionAndDurationText(
                            player,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    // Not the active item — show a play button to switch playback here
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { onPlayMedia(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = stringResource(R.string.embedded_media_action_load_audio),
                                modifier = Modifier.size(40.dp),
                                tint = styles.linkIconColor
                            )
                        }
                    }
                }
            }

            // Error display
            playbackError?.let { error ->
                Text(
                    text = stringResource(R.string.embedded_media_playback_error, error),
                    style = styles.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        MediaContextMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            url = item.url,
            onOpenInNewTab = onOpenInNewTab,
            onCopyLink = onCopyLink,
            onDownload = { onDownloadMedia(item.url, item.data, item.dataFilePath, item.mimeType) },
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

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
private fun LoadedVideoMediaCard(
    item: GeminiContent.EmbeddedMedia,
    styles: CachedTextStyles,
    playerManager: GemcapPlayerManager,
    onPlayMedia: (Int) -> Unit,
    onCollapseMedia: (Int) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit,
    onFullscreen: (Player) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val videoLabel = mediaTypeLabel(MediaType.VIDEO)
    val name = item.linkText.takeIf { it.isNotBlank() }
        ?: item.url.substringAfterLast("/").ifEmpty { videoLabel }

    val isActiveItem = playerManager.currentItemId == item.id
    val player = if (isActiveItem) playerManager.player else null

    val sizeText = if (item.dataFilePath != null) {
        formatBytesLong(java.io.File(item.dataFilePath).length())
    } else {
        formatBytes(item.data?.bytes?.size ?: 0)
    }

    // Reset playback error when this item becomes active again
    LaunchedEffect(isActiveItem) {
        if (isActiveItem) playbackError = null
    }

    // Listen for playback errors only when this is the active item
    DisposableEffect(player) {
        val listener = if (player != null) {
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackError = error.localizedMessage ?: error.errorCodeName
                }
            }.also { player.addListener(it) }
        } else null

        onDispose {
            if (player != null && listener != null) {
                player.removeListener(listener)
            }
        }
    }

    // Pause video when app goes to background; audio is intentionally allowed
    // to continue playing in the background via MediaSessionService
    if (isActiveItem) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    playerManager.player?.pause()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    Box {
        Column(
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
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Video surface — only attach when this is the active item
            if (player != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.scrim),
                    contentAlignment = Alignment.Center
                ) {
                    ContentFrame(
                        player = player,
                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Not the active item — show a play overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(MaterialTheme.colorScheme.scrim)
                        .clickable { onPlayMedia(item.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.embedded_media_action_load_video),
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // Info and controls below video
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // File info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = styles.bodyMedium,
                            color = styles.bodyColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.mimeType} - $sizeText",
                            style = styles.bodySmall,
                            color = styles.bodyColor.copy(alpha = 0.7f)
                        )
                    }
                    // Fullscreen button
                    if (player != null) {
                        IconButton(onClick = { onFullscreen(player) }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.embedded_media_fullscreen),
                                tint = styles.bodyColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Controls — only for active item
                CompositionLocalProvider(LocalContentColor provides styles.bodyColor) {
                    if (player != null) {
                        PlayerProgressSlider(
                            player = player,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PlayPauseButton(player, modifier = Modifier.size(40.dp))
                            PositionAndDurationText(
                                player,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Error display
                playbackError?.let { error ->
                    Text(
                        text = stringResource(R.string.embedded_media_playback_error, error),
                        style = styles.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
            onDownload = { onDownloadMedia(item.url, item.data, item.dataFilePath, item.mimeType) },
            extraItems = {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.embedded_media_hide_video)) },
                    onClick = {
                        showMenu = false
                        onCollapseMedia(item.id)
                    }
                )
            }
        )
    }
}

/**
 * A progress slider that tracks and allows seeking of the player's current position.
 * Built using a standard Material3 Slider since media3-ui-compose-material3 1.9.2 does not
 * include a dedicated progress slider composable.
 */
@Composable
private fun PlayerProgressSlider(
    player: Player,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // Periodically update position while not seeking
    LaunchedEffect(player) {
        while (isActive) {
            if (!isSeeking) {
                val duration = player.duration.coerceAtLeast(1L)
                val position = player.currentPosition
                sliderPosition = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(200L)
        }
    }

    Slider(
        value = sliderPosition,
        onValueChange = { value ->
            isSeeking = true
            sliderPosition = value
        },
        onValueChangeFinished = {
            val duration = player.duration
            if (duration > 0) {
                player.seekTo((sliderPosition * duration).toLong())
            }
            isSeeking = false
        },
        modifier = modifier.height(24.dp)
    )
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
    onDownloadMedia: (String, StableByteArray?, String?, String) -> Unit
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
                    onClick = { onCollapseMedia(item.id) },
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
            onDownload = { onDownloadMedia(item.url, data, item.dataFilePath, item.mimeType) },
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
