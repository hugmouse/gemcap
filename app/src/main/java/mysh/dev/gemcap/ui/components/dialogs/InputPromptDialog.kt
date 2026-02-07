package mysh.dev.gemcap.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import mysh.dev.gemcap.domain.InputPromptState

@Composable
fun InputPromptDialog(
    promptState: InputPromptState,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (promptState.isSensitive) "Sensitive Input Required" else "Input Required")
        },
        text = {
            Column {
                if (promptState.promptText.isNotBlank()) {
                    Text(text = promptState.promptText)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (promptState.isSensitive) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    label = { Text(if (promptState.isSensitive) "Password" else "Input") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
