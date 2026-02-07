package mysh.dev.gemcap.ui.content

import androidx.compose.runtime.Composable
import mysh.dev.gemcap.domain.GeminiContent

/**
 * Dispatcher composable that renders the appropriate content type
 */
@Composable
fun ContentItem(
    item: GeminiContent,
    styles: CachedTextStyles,
    searchQuery: String,
    highlight: Boolean,
    onLinkClick: (String) -> Unit,
    onOpenImageInNewTab: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit
) {
    when (item) {
        is GeminiContent.Heading -> HeadingContent(item, styles, searchQuery)
        is GeminiContent.Link -> LinkContent(
            item,
            styles,
            searchQuery,
            onLinkClick,
            onCopyLink,
            onOpenInNewTab
        )

        is GeminiContent.Text -> TextContent(item, searchQuery)
        is GeminiContent.ListItem -> ListItemContent(item, styles, searchQuery)
        is GeminiContent.Quote -> QuoteContent(item, styles, searchQuery)
        is GeminiContent.Preformatted -> PreformattedContent(item, styles, searchQuery, highlight)
        is GeminiContent.Image -> ImageContent(item, onOpenImageInNewTab)
    }
}
