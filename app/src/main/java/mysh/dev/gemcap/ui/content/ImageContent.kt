package mysh.dev.gemcap.ui.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.util.ImageUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageContent(
    item: GeminiContent.Image,
    onOpenImageInNewTab: (String) -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Box {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.data.bytes)
                .crossfade(true)
                .build(),
            contentDescription = "Image from ${item.url}",
            modifier = Modifier
                .widthIn(max = 600.dp)
                .combinedClickable(
                    onClick = { },
                    onLongClick = { showMenu = true }
                ),
            contentScale = ContentScale.Fit
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open image in new tab") },
                onClick = {
                    showMenu = false
                    onOpenImageInNewTab(item.url)
                }
            )
            DropdownMenuItem(
                text = { Text("Copy image") },
                onClick = {
                    showMenu = false
                    ImageUtils.copyImageToClipboard(context, item.data.bytes, item.mimeType)
                }
            )
            DropdownMenuItem(
                text = { Text("Download image") },
                onClick = {
                    showMenu = false
                    val filename = item.url.substringAfterLast("/").ifEmpty { "image" }
                    ImageUtils.downloadImage(context, item.data.bytes, item.mimeType, filename)
                }
            )
        }
    }
}
