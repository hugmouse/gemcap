package mysh.dev.gemcap.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.media3.common.Player
import mysh.dev.gemcap.domain.GeminiContent
import mysh.dev.gemcap.domain.StableByteArray
import mysh.dev.gemcap.media.GemcapPlayerManager

@Stable
class ContentActions(
    val onLinkClick: (String) -> Unit,
    val onOpenImageInNewTab: (String) -> Unit,
    val onCopyLink: (String) -> Unit,
    val onOpenInNewTab: (String) -> Unit,
    val onLoadEmbeddedMedia: (Int) -> Unit,
    val onPlayEmbeddedMedia: (Int) -> Unit,
    val onCollapseEmbeddedMedia: (Int) -> Unit,
    val onDownloadEmbeddedMedia: (String, StableByteArray?, String?, String) -> Unit,
    val playerManager: GemcapPlayerManager,
    val onFullscreen: (Player) -> Unit,
    val activeTabId: String
)

/**
 * Dispatcher composable that renders the appropriate content type
 */
@Composable
fun ContentItem(
    item: GeminiContent,
    styles: CachedTextStyles,
    searchQuery: String,
    highlight: Boolean,
    actions: ContentActions
) {
    when (item) {
        is GeminiContent.Heading -> HeadingContent(item, styles, searchQuery)
        is GeminiContent.Link -> LinkContent(
            item,
            styles,
            searchQuery,
            actions.onLinkClick,
            actions.onCopyLink,
            actions.onOpenInNewTab
        )

        is GeminiContent.Text -> TextContent(item, styles, searchQuery)
        is GeminiContent.ListItem -> ListItemContent(item, styles, searchQuery)
        is GeminiContent.Quote -> QuoteContent(item, styles, searchQuery)
        is GeminiContent.Preformatted -> PreformattedContent(item, styles, searchQuery, highlight)
        is GeminiContent.Image -> ImageContent(item, actions.onOpenImageInNewTab)
        is GeminiContent.EmbeddedMedia -> EmbeddedMediaContent(
            item = item,
            styles = styles,
            playerManager = actions.playerManager,
            activeTabId = actions.activeTabId,
            onLoadMedia = actions.onLoadEmbeddedMedia,
            onPlayMedia = actions.onPlayEmbeddedMedia,
            onCollapseMedia = actions.onCollapseEmbeddedMedia,
            onOpenInNewTab = actions.onOpenInNewTab,
            onCopyLink = actions.onCopyLink,
            onDownloadMedia = actions.onDownloadEmbeddedMedia,
            onFullscreen = actions.onFullscreen
        )
    }
}
