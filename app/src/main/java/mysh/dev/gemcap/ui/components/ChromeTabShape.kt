package mysh.dev.gemcap.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Custom shape for Chrome-style browser tabs with curved edges.
 */
class SimpleChromeTabShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(0f, size.height)
            cubicTo(
                size.height * 0.9f, size.height,
                size.height * 0.05f, 0f,
                size.height, 0f
            )

            // Line across top
            lineTo(size.width - size.height, 0f)
            // Curve down and right
            cubicTo(
                size.width - size.height * 0.05f, 0f,
                size.width - size.height * 0.9f, size.height,
                size.width, size.height
            )
            close()
        }
        return Outline.Generic(path)
    }
}
