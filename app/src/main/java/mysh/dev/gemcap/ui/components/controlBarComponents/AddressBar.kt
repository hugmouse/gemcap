package mysh.dev.gemcap.ui.components.controlBarComponents

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import mysh.dev.gemcap.domain.HistoryEntry

// Thanks to this lad the search is now scrollable!
// https://stackoverflow.com/a/69634146
// https://stackoverflow.com/users/916826/sergei-s

private object AddressBarDefaults {
    val Height = 40.dp
    val CornerRadius = 20.dp
    val IconSize = 32.dp
    val IconPadding = 6.dp
    val BorderThickness = 1.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBar(
    url: String,
    onUrlChange: (String) -> Unit,
    onGo: (String) -> Unit,
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
        if (isFocused) return@LaunchedEffect
        val currentText = textFieldState.text.toString()
        if (url != currentText) {
            textFieldState.edit {
                replace(0, length, url)
                selection = TextRange(url.length)
            }
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { newText ->
                if (isFocused) {
                    onUrlChange(newText)
                }
            }
    }

    val shape = remember { RoundedCornerShape(AddressBarDefaults.CornerRadius) }
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary
    )

    val density = LocalDensity.current
    var barWidth by remember { mutableStateOf(0.dp) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            barWidth = with(density) { coordinates.size.width.toDp() }
        }
    ) {
        BasicTextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .height(AddressBarDefaults.Height)
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
            onKeyboardAction = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSuggestionsDismiss()
                onGo(textFieldState.text.toString())
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
                        CertificateValidityIcon(
                            hasSecureConnection = hasSecureConnection,
                            onConnectionInfoClick = onConnectionInfoClick
                        )
                    },
                    colors = colors,
                    contentPadding = PaddingValues(
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
                            focusedBorderThickness = AddressBarDefaults.BorderThickness,
                            unfocusedBorderThickness = AddressBarDefaults.BorderThickness
                        )
                    }
                )
            }
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            AddressBarSuggestions(
                suggestions = suggestions,
                onSuggestionClick = { entry ->
                    onSuggestionClick(entry)
                    focusManager.clearFocus()
                    onSuggestionsDismiss()
                },
                onDismiss = onSuggestionsDismiss,
                modifier = Modifier.width(barWidth)
            )
        }
    }
}

@Composable
private fun CertificateValidityIcon(
    hasSecureConnection: Boolean,
    onConnectionInfoClick: () -> Unit,
) {
    IconButton(
        onClick = onConnectionInfoClick,
        enabled = hasSecureConnection
    ) {
        Icon(
            imageVector = if (hasSecureConnection) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (hasSecureConnection) "Secure connection" else "Insecure connection",
            tint = if (hasSecureConnection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = 0.5f
            )
        )
    }
}
