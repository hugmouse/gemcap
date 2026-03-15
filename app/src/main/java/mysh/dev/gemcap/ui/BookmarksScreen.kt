package mysh.dev.gemcap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.ui.components.EmptyStateBox
import mysh.dev.gemcap.ui.components.ScreenHeader
import mysh.dev.gemcap.ui.components.cards.BookmarkCard

@Composable
fun BookmarksScreen(
    bookmarks: ImmutableList<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = this.maxWidth > 600.dp
        val gridMinSize = if (isTablet) 180.dp else 140.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            ScreenHeader(title = "Bookmarks", onDismiss = onDismiss)

            if (bookmarks.isEmpty()) {
                EmptyStateBox(message = "No bookmarks yet")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookmarks, key = { it.url }) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            onSelected = { onBookmarkClick(bookmark) },
                            onDelete = { onBookmarkDelete(bookmark.url) }
                        )
                    }
                }
            }
        }
    }
}
