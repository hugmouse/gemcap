package mysh.dev.gemcap.ui.components.controlBarComponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.HistoryEntry

private object AddressBarSuggestionsDefaults {
    val PopupOffset = 44.dp
    val MaxHeight = 200.dp
    val CornerRadius = 8.dp
    val Elevation = 4.dp
}

@Composable
fun AddressBarSuggestions(
    suggestions: ImmutableList<HistoryEntry>,
    onSuggestionClick: (HistoryEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = 0,
            y = with(LocalDensity.current) {
                AddressBarSuggestionsDefaults.PopupOffset.roundToPx()
            }
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            modifier = modifier
                .heightIn(max = AddressBarSuggestionsDefaults.MaxHeight),
            shape = RoundedCornerShape(AddressBarSuggestionsDefaults.CornerRadius),
            tonalElevation = AddressBarSuggestionsDefaults.Elevation,
            shadowElevation = AddressBarSuggestionsDefaults.Elevation
        ) {
            LazyColumn {
                items(
                    items = suggestions,
                    key = { "${it.url}_${it.visitedAt}" }
                ) { entry ->
                    AddressBarSuggestionItem(
                        entry = entry,
                        onClick = { onSuggestionClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressBarSuggestionItem(
    entry: HistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = entry.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
