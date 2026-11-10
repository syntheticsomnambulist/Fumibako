package dev.aiharness.app.ui.paper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line-drawn stationery icons for the letter's top bar, on a 24 by 24 grid: the pen that writes
 * (the model), your seal (who you're writing as), a polaroid (draw a picture) and three ink dots
 * (more). Drawn in black; [androidx.compose.material3.Icon] tints them.
 */
object StationeryIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(width: Float = 1.6f, d: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = d,
    )

    private fun ImageVector.Builder.dot(x: Float, y: Float, r: Float) = path(fill = SolidColor(Color.Black)) {
        moveTo(x - r, y)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x + r, y)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x - r, y)
        close()
    }

    private fun PathBuilder.circle(x: Float, y: Float, r: Float) {
        moveTo(x - r, y)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x + r, y)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x - r, y)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, c: Float) {
        moveTo(l + c, t)
        lineTo(r - c, t)
        quadTo(r, t, r, t + c)
        lineTo(r, b - c)
        quadTo(r, b, r - c, b)
        lineTo(l + c, b)
        quadTo(l, b, l, b - c)
        lineTo(l, t + c)
        quadTo(l, t, l + c, t)
        close()
    }

    /** A loupe: search the letters. */
    val Loupe: ImageVector = icon("Loupe") {
        line(1.7f) { circle(10f, 10f, 6.2f) }
        line(2.1f) {
            moveTo(14.7f, 14.7f)
            lineTo(19.8f, 19.8f)
        }
    }

    /** A fountain pen nib: which model writes the letters. */
    val Nib: ImageVector = icon("Nib") {
        group(rotate = 18f, pivotX = 12f, pivotY = 12f) {
            line {
                moveTo(7.5f, 3f)
                lineTo(16.5f, 3f)
                lineTo(16.5f, 9f)
                quadTo(16.5f, 11f, 15.2f, 13.2f)
                lineTo(12f, 21f)
                lineTo(8.8f, 13.2f)
                quadTo(7.5f, 11f, 7.5f, 9f)
                close()
            }
            line { moveTo(7.5f, 6f); lineTo(16.5f, 6f) }
            line { moveTo(12f, 20.5f); lineTo(12f, 12.4f) }
            line(1.4f) { circle(12f, 10.6f, 1.25f) }
        }
    }

    /** A rubber stamp over its print: your seal, who you're writing as. */
    val Seal: ImageVector = icon("Seal") {
        line { circle(12f, 4.9f, 2.4f) }
        line { moveTo(10.6f, 7.1f); lineTo(10.2f, 10.8f); moveTo(13.4f, 7.1f); lineTo(13.8f, 10.8f) }
        line { roundRect(5f, 10.8f, 19f, 15.6f, 1.4f) }
        line(2f) { moveTo(6.5f, 19.6f); lineTo(17.5f, 19.6f) }
    }

    /** A polaroid, a little crooked: draw a picture. */
    val Polaroid: ImageVector = icon("Polaroid") {
        group(rotate = -7f, pivotX = 12f, pivotY = 12f) {
            line { roundRect(4.6f, 3.2f, 19.4f, 21f, 1f) }
            line(1.3f) { roundRect(7f, 5.6f, 17f, 15.4f, 0.4f) }
            line(1.3f) {
                moveTo(7.4f, 15f)
                lineTo(10.4f, 11.4f)
                lineTo(12.4f, 13.4f)
                lineTo(13.9f, 11.9f)
                lineTo(16.6f, 15f)
            }
            line(1.2f) { circle(14.6f, 8.4f, 1.1f) }
        }
    }

    /** Three ink dots, not quite in a line: more. */
    val InkDots: ImageVector = icon("InkDots") {
        dot(12.3f, 5.4f, 1.85f)
        dot(11.8f, 12f, 1.7f)
        dot(12.2f, 18.6f, 1.9f)
    }
}
