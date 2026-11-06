package dev.aiharness.app.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** The seasons' small pictures, one for each stretch of solar terms, on personas' slips and a day's page (placeholders, like the app's other drawings). */
enum class Motif { PLUM, CHERRY, HYDRANGEA, MORNING_GLORY, MOON, MAPLE, CAMELLIA, SNOW }

/** The motif of a solar term, by its index from 立春. */
fun motifOf(termIndex: Int): Motif = when (termIndex) {
    in 0..2 -> Motif.PLUM
    in 3..5 -> Motif.CHERRY
    in 6..9 -> Motif.HYDRANGEA
    in 10..11 -> Motif.MORNING_GLORY
    in 12..14 -> Motif.MOON
    in 15..17 -> Motif.MAPLE
    // 立冬's camellias; from 小雪 (light snow), the snow ring.
    18 -> Motif.CAMELLIA
    else -> Motif.SNOW
}

@Composable
fun SeasonMotif(termIndex: Int, modifier: Modifier = Modifier) {
    val motif = motifOf(termIndex)
    Canvas(modifier) {
        val s = size.minDimension / 24f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        when (motif) {
            Motif.PLUM -> petals(o(12f, 12f), 5, 4.6f * s, 5.2f * s, Color(0xFFF3C4D0), Color(0xFFD9768F))
            Motif.CHERRY -> {
                petals(o(12f, 12f), 5, 4.4f * s, 5.6f * s, Color(0xFFF7D3DC), Color(0xFFE8A0B4))
                // The notch at each petal's tip.
                for (i in 0 until 5) {
                    val a = Math.toRadians(-90.0 + i * 72.0)
                    drawCircle(Color(0xFFFFFBF8), 1.3f * s, o(12f + (10.6f * cos(a)).toFloat(), 12f + (10.6f * sin(a)).toFloat()))
                }
            }
            Motif.HYDRANGEA -> {
                val florets = listOf(Offset(8f, 8f), Offset(16f, 8f), Offset(12f, 13f), Offset(7f, 16f), Offset(17f, 16f))
                florets.forEachIndexed { i, f ->
                    val c = if (i % 2 == 0) Color(0xFF8FA3E2) else Color(0xFFAA90D8)
                    for (k in 0 until 4) {
                        val a = Math.toRadians(45.0 + k * 90.0)
                        drawCircle(c, 2.1f * s, o(f.x + (2.1f * cos(a)).toFloat(), f.y + (2.1f * sin(a)).toFloat()))
                    }
                    drawCircle(Color(0xFFF4F0FA), 0.8f * s, o(f.x, f.y))
                }
            }
            Motif.MORNING_GLORY -> {
                drawCircle(Color(0xFF5B6FD6), 10f * s, o(12f, 12f))
                // The white star at its throat.
                val star = Path()
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 6.5f else 2.2f
                    val a = Math.toRadians(-90.0 + k * 36.0)
                    val p = o(12f + (r * cos(a)).toFloat(), 12f + (r * sin(a)).toFloat())
                    if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
                }
                star.close()
                drawPath(star, Color(0xFFE9EDFB))
                drawCircle(Color(0xFFF6F1D8), 1.6f * s, o(12f, 12f))
            }
            Motif.MOON -> {
                drawCircle(Color(0xFFF1D47E), 7.5f * s, o(14f, 9f))
                // Pampas grass (susuki) bending across it.
                val grass = Color(0xFFA48A58)
                listOf(Triple(4f, 23f, 9f), Triple(7f, 23f, 14f), Triple(10f, 23f, 6f)).forEach { (x0, y0, tipX) ->
                    val p = Path().apply { moveTo(x0 * s, y0 * s); quadraticTo((x0 + 2f) * s, 12f * s, tipX * s, 3f * s) }
                    drawPath(p, grass, style = Stroke(1.1f * s, cap = StrokeCap.Round))
                }
            }
            Motif.MAPLE -> {
                drawLine(Color(0xFF8A5A2E), o(12f, 15f), o(12f, 22f), 1.1f * s, StrokeCap.Round)
                val leaf = Path()
                val pts = listOf(12f to 3f, 13.69f to 10.38f, 19.66f to 7.57f, 15.86f to 12.96f, 19.88f to 15.39f, 15.03f to 15.75f, 15.21f to 17.83f, 12.52f to 15.93f, 12f to 15.2f, 11.48f to 15.93f, 8.79f to 17.83f, 8.97f to 15.75f, 4.12f to 15.39f, 8.14f to 12.96f, 4.34f to 7.57f, 10.31f to 10.38f)
                pts.forEachIndexed { i, (x, y) -> if (i == 0) leaf.moveTo(x * s, y * s) else leaf.lineTo(x * s, y * s) }
                leaf.close()
                drawPath(leaf, Color(0xFFD9692E))
            }
            Motif.CAMELLIA -> {
                rotate(-32f, o(5.5f, 19f)) { drawOval(Color(0xFF008B57), o(1.3f, 17f), Size(8.4f * s, 4f * s)) }
                rotate(32f, o(18.5f, 19f)) { drawOval(Color(0xFF008B57), o(14.3f, 17f), Size(8.4f * s, 4f * s)) }
                listOf(12f to 7.4f, 16.4f to 10.6f, 14.7f to 15.7f, 9.3f to 15.7f, 7.6f to 10.6f).forEach { (x, y) -> drawCircle(Color(0xFFC63368), 4.6f * s, o(x, y)) }
                drawCircle(Color(0xFFE3940B), 2.6f * s, o(12f, 12f))
            }
            Motif.SNOW -> {
                drawCircle(Color(0xFF8D9FD8), 10f * s, o(12f, 12f))
                // The six bites of a snow ring (雪輪).
                for (k in 0 until 6) {
                    val a = Math.toRadians(-90.0 + k * 60.0)
                    drawCircle(Color(0xFFF7F2EC), 2.6f * s, o(12f + (10f * cos(a)).toFloat(), 12f + (10f * sin(a)).toFloat()))
                }
                drawCircle(Color(0xFFF7F2EC), 4f * s, o(12f, 12f))
            }
        }
    }
}

/** [n] round petals about [center], with a small heart. */
private fun DrawScope.petals(center: Offset, n: Int, radius: Float, reach: Float, petal: Color, heart: Color) {
    for (i in 0 until n) {
        val a = Math.toRadians(-90.0 + i * 360.0 / n)
        drawCircle(petal, radius, Offset(center.x + (reach * cos(a)).toFloat(), center.y + (reach * sin(a)).toFloat()))
    }
    drawCircle(heart, radius * 0.55f, center)
}
