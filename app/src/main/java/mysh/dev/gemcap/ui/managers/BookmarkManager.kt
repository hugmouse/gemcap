package mysh.dev.gemcap.ui.managers

import android.app.Application
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import mysh.dev.gemcap.data.BrowserRepository
import mysh.dev.gemcap.domain.Bookmark
import mysh.dev.gemcap.ui.model.PanelState
import mysh.dev.gemcap.util.ScreenshotUtils

class BookmarkManager(
    private val application: Application,
    private val repository: BrowserRepository,
    private val getPanelState: () -> PanelState,
    private val updatePanelState: (PanelState) -> Unit
) {
    var bookmarks by mutableStateOf<ImmutableList<Bookmark>>(persistentListOf())
        private set

    var isCurrentPageBookmarked by mutableStateOf(false)
        private set

    fun refresh() {
        bookmarks = repository.getBookmarks().toImmutableList()
    }

    fun updateBookmarkStatus(url: String?) {
        isCurrentPageBookmarked = url?.let { repository.isBookmarked(it) } ?: false
    }

    fun toggle(url: String, title: String, view: View?, density: Density?) {
        if (isCurrentPageBookmarked) {
            repository.removeBookmark(url)
        } else {
            val previewPath = if (view != null && density != null) {
                val bookmarkId = url.hashCode().toString()
                ScreenshotUtils.saveBookmarkPreview(application, view, density, bookmarkId)
            } else null

            repository.addBookmark(
                Bookmark(
                    url = url,
                    title = title.ifEmpty { url },
                    previewPath = previewPath
                )
            )
        }
        refresh()
        updateBookmarkStatus(url)
    }

    fun remove(url: String, currentUrl: String?) {
        repository.removeBookmark(url)
        refresh()
        updateBookmarkStatus(currentUrl)
    }

    fun showScreen() {
        refresh()
        updatePanelState(getPanelState().copy(showBookmarks = true, showMenu = false))
    }

    fun dismissScreen() {
        updatePanelState(getPanelState().copy(showBookmarks = false))
    }
}
