package mysh.dev.gemcap.ui.components.controlBarComponents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TabCounterButton(
    tabCount: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
        ) {
            Text(
                text = tabCount.toString(),
                style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}
