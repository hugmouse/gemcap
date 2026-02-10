package mysh.dev.gemcap.ui.components.controlBarComponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.collections.immutable.ImmutableList
import mysh.dev.gemcap.domain.HistoryEntry

// Thanks to this lad the search is now scrollable!
// https://stackoverflow.com/a/69634146
// https://stackoverflow.com/users/916826/sergei-s
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBar(
    url: String,
    onUrlChange: (String) -> Unit,
    onGo: () -> Unit,
    hasSecureConnection: Boolean,
    onConnectionInfoClick: () -> Unit,
    suggestions: ImmutableList<HistoryEntry>,
    showSuggestions: Boolean,
    onSuggestionClick: (HistoryEntry) -> Unit,
    onSuggestionsDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()
    val textFieldState = rememberTextFieldState(
        initialText = url,
        initialSelection = TextRange(url.length)
    )
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(url, isFocused) {
        val currentText = textFieldState.text.toString()
        if (url != currentText) {
            val nextSelection = if (isFocused) {
                val start = textFieldState.selection.start.coerceIn(0, url.length)
                val end = textFieldState.selection.end.coerceIn(0, url.length)
                TextRange(start, end)
            } else {
                TextRange(url.length)
            }
            textFieldState.edit {
                replace(0, length, url)
                selection = nextSelection
            }
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collectLatest { newText ->
                onUrlChange(newText)
            }
    }

    val shape = RoundedCornerShape(20.dp)
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary
    )

    Box(modifier = modifier) {
        BasicTextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        onSuggestionsDismiss()
                    }
                },
            enabled = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go
            ),
            onKeyboardAction = { _ ->
                keyboardController?.hide()
                focusManager.clearFocus()
                onSuggestionsDismiss()
                onGo()
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            scrollState = scrollState,
            decorator = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = textFieldState.text.toString(),
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = false,
                    leadingIcon = {
                        Icon(
                            imageVector = if (hasSecureConnection) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.LockOpen
                            },
                            contentDescription = "Connection security",
                            tint = if (hasSecureConnection) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    enabled = hasSecureConnection,
                                    onClick = onConnectionInfoClick
                                )
                                .padding(6.dp)
                        )
                    },
                    colors = colors,
                    // Reduced vertical padding to avoid clipping in a 40.dp height field.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 8.dp,
                        top = 0.dp,
                        end = 8.dp,
                        bottom = 0.dp
                    ),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = shape,
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(LocalDensity.current) { 44.dp.roundToPx() }),
                onDismissRequest = onSuggestionsDismiss,
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    LazyColumn {
                        items(suggestions, key = { "${it.url}_${it.visitedAt}" }) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionClick(entry) }
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
                    }
                }
            }
        }
    }
}
