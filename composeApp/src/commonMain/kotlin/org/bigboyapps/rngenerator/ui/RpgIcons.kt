package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── RPG Icon Composables ────────────────────────────────────────
// Hand-drawn vector icons via Canvas. No external dependencies.

@Composable
fun HeartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.hpRed
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.85f)
            cubicTo(w * 0.15f, h * 0.55f, -w * 0.05f, h * 0.25f, w * 0.25f, h * 0.15f)
            cubicTo(w * 0.38f, h * 0.08f, w * 0.5f, h * 0.2f, w * 0.5f, h * 0.3f)
            cubicTo(w * 0.5f, h * 0.2f, w * 0.62f, h * 0.08f, w * 0.75f, h * 0.15f)
            cubicTo(w * 1.05f, h * 0.25f, w * 0.85f, h * 0.55f, w * 0.5f, h * 0.85f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun DropletIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.manaBlue
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            cubicTo(w * 0.5f, h * 0.1f, w * 0.15f, h * 0.55f, w * 0.15f, h * 0.65f)
            cubicTo(w * 0.15f, h * 0.85f, w * 0.3f, h * 0.95f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.7f, h * 0.95f, w * 0.85f, h * 0.85f, w * 0.85f, h * 0.65f)
            cubicTo(w * 0.85f, h * 0.55f, w * 0.5f, h * 0.1f, w * 0.5f, h * 0.1f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun SwordIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.bronze
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Blade
        drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.7f), strokeWidth = w * 0.1f)
        // Blade edge highlight
        drawLine(color.copy(alpha = 0.4f), Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.65f), strokeWidth = w * 0.04f)
        // Guard
        drawLine(color, Offset(w * 0.25f, h * 0.68f), Offset(w * 0.75f, h * 0.68f), strokeWidth = w * 0.1f)
        // Grip
        drawLine(color, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.5f, h * 0.88f), strokeWidth = w * 0.08f)
        // Pommel
        drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.93f))
    }
}

@Composable
fun ShieldIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.bronze
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.88f, h * 0.2f)
            lineTo(w * 0.85f, h * 0.55f)
            cubicTo(w * 0.8f, h * 0.75f, w * 0.6f, h * 0.88f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.4f, h * 0.88f, w * 0.2f, h * 0.75f, w * 0.15f, h * 0.55f)
            lineTo(w * 0.12f, h * 0.2f)
            close()
        }
        drawPath(path, color, style = Stroke(width = w * 0.07f, join = StrokeJoin.Round))
        drawPath(path, color.copy(alpha = 0.2f))
        // Cross on shield
        drawLine(color, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), strokeWidth = w * 0.06f)
        drawLine(color, Offset(w * 0.3f, h * 0.45f), Offset(w * 0.7f, h * 0.45f), strokeWidth = w * 0.06f)
    }
}

@Composable
fun BowIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.bronze
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        // Bow arc
        val bowPath = Path().apply {
            moveTo(w * 0.3f, h * 0.1f)
            cubicTo(w * 0.1f, h * 0.35f, w * 0.1f, h * 0.65f, w * 0.3f, h * 0.9f)
        }
        drawPath(bowPath, color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
        // String
        drawLine(color, Offset(w * 0.3f, h * 0.1f), Offset(w * 0.3f, h * 0.9f), strokeWidth = w * 0.03f)
        // Arrow
        drawLine(color, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = w * 0.05f)
        // Arrowhead
        val arrowPath = Path().apply {
            moveTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.78f, h * 0.4f)
            moveTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.78f, h * 0.6f)
        }
        drawPath(arrowPath, color, style = stroke)
    }
}

@Composable
fun ScrollIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.gold
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Main scroll body
        drawRoundRect(
            color = color.copy(alpha = 0.3f),
            topLeft = Offset(w * 0.2f, h * 0.15f),
            size = Size(w * 0.6f, h * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f)
        )
        // Top roll
        drawOval(color, topLeft = Offset(w * 0.12f, h * 0.08f), size = Size(w * 0.76f, h * 0.16f))
        // Bottom roll
        drawOval(color, topLeft = Offset(w * 0.12f, h * 0.76f), size = Size(w * 0.76f, h * 0.16f))
        // Text lines
        val lineColor = color.copy(alpha = 0.5f)
        drawLine(lineColor, Offset(w * 0.32f, h * 0.35f), Offset(w * 0.68f, h * 0.35f), strokeWidth = w * 0.04f)
        drawLine(lineColor, Offset(w * 0.32f, h * 0.48f), Offset(w * 0.68f, h * 0.48f), strokeWidth = w * 0.04f)
        drawLine(lineColor, Offset(w * 0.32f, h * 0.61f), Offset(w * 0.55f, h * 0.61f), strokeWidth = w * 0.04f)
    }
}

@Composable
fun CoinIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.gold
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Outer ring
        drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f))
        // Inner ring
        drawCircle(color.copy(alpha = 0.4f), radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f))
        // Inner circle
        drawCircle(color.copy(alpha = 0.7f), radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.5f))
        // G mark (simplified)
        drawArc(
            color = AppColors.leatherDark,
            startAngle = -30f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.35f, h * 0.35f),
            size = Size(w * 0.3f, h * 0.3f),
            style = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ChestIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.bronze
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Lid (rounded top)
        val lidPath = Path().apply {
            moveTo(w * 0.1f, h * 0.45f)
            lineTo(w * 0.1f, h * 0.3f)
            cubicTo(w * 0.1f, h * 0.12f, w * 0.9f, h * 0.12f, w * 0.9f, h * 0.3f)
            lineTo(w * 0.9f, h * 0.45f)
            close()
        }
        drawPath(lidPath, color)
        // Body
        drawRect(color.copy(alpha = 0.8f), topLeft = Offset(w * 0.1f, h * 0.45f), size = Size(w * 0.8f, h * 0.42f))
        // Lock
        drawCircle(AppColors.gold, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.55f))
        // Bands
        drawLine(color.copy(alpha = 0.5f), Offset(w * 0.1f, h * 0.45f), Offset(w * 0.9f, h * 0.45f), strokeWidth = w * 0.04f)
    }
}

@Composable
fun MapIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.bronze
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Folded map shape
        val mapPath = Path().apply {
            moveTo(w * 0.1f, h * 0.15f)
            lineTo(w * 0.38f, h * 0.22f)
            lineTo(w * 0.62f, h * 0.12f)
            lineTo(w * 0.9f, h * 0.2f)
            lineTo(w * 0.9f, h * 0.85f)
            lineTo(w * 0.62f, h * 0.78f)
            lineTo(w * 0.38f, h * 0.88f)
            lineTo(w * 0.1f, h * 0.8f)
            close()
        }
        drawPath(mapPath, color.copy(alpha = 0.3f))
        drawPath(mapPath, color, style = Stroke(width = w * 0.05f, join = StrokeJoin.Round))
        // Fold lines
        drawLine(color.copy(alpha = 0.5f), Offset(w * 0.38f, h * 0.22f), Offset(w * 0.38f, h * 0.88f), strokeWidth = w * 0.03f)
        drawLine(color.copy(alpha = 0.5f), Offset(w * 0.62f, h * 0.12f), Offset(w * 0.62f, h * 0.78f), strokeWidth = w * 0.03f)
        // X mark
        drawLine(AppColors.hpRed, Offset(w * 0.48f, h * 0.42f), Offset(w * 0.58f, h * 0.55f), strokeWidth = w * 0.05f)
        drawLine(AppColors.hpRed, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.48f, h * 0.55f), strokeWidth = w * 0.05f)
    }
}

@Composable
fun MicrophoneIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = AppColors.parchmentLight
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        // Mic body
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.35f, h * 0.12f),
            size = Size(w * 0.3f, h * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f)
        )
        // Arc
        val arcPath = Path().apply {
            moveTo(w * 0.22f, h * 0.42f)
            cubicTo(w * 0.22f, h * 0.68f, w * 0.78f, h * 0.68f, w * 0.78f, h * 0.42f)
        }
        drawPath(arcPath, color, style = stroke)
        // Stand
        drawLine(color, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.07f)
        // Base
        drawLine(color, Offset(w * 0.33f, h * 0.82f), Offset(w * 0.67f, h * 0.82f), strokeWidth = w * 0.07f)
    }
}

@Composable
fun StarIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.gold
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.62f, h * 0.38f)
            lineTo(w * 0.95f, h * 0.38f)
            lineTo(w * 0.68f, h * 0.58f)
            lineTo(w * 0.78f, h * 0.92f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.22f, h * 0.92f)
            lineTo(w * 0.32f, h * 0.58f)
            lineTo(w * 0.05f, h * 0.38f)
            lineTo(w * 0.38f, h * 0.38f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun CloseIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = AppColors.inkMedium
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = w * 0.1f
        drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), strokeWidth = strokeW)
        drawLine(color, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), strokeWidth = strokeW)
    }
}

// ── Ornamental Drawing Helpers ───────────────────────────────────

/**
 * Draw an ornamental horizontal divider with a diamond at center.
 */
@Composable
fun OrnamentalDivider(
    modifier: Modifier = Modifier,
    color: Color = AppColors.parchmentEdge
) {
    Canvas(modifier = modifier.size(width = 300.dp, height = 20.dp)) {
        val w = this.size.width
        val h = this.size.height
        val cy = h * 0.5f
        val lineY = cy
        val strokeW = 1.2f

        // Left line
        drawLine(color, Offset(w * 0.1f, lineY), Offset(w * 0.42f, lineY), strokeWidth = strokeW)
        // Right line
        drawLine(color, Offset(w * 0.58f, lineY), Offset(w * 0.9f, lineY), strokeWidth = strokeW)

        // Center diamond
        val diamondPath = Path().apply {
            moveTo(w * 0.5f, cy - h * 0.3f)
            lineTo(w * 0.5f + h * 0.3f, cy)
            lineTo(w * 0.5f, cy + h * 0.3f)
            lineTo(w * 0.5f - h * 0.3f, cy)
            close()
        }
        drawPath(diamondPath, color)

        // Small dots
        drawCircle(color, radius = 2f, center = Offset(w * 0.15f, lineY))
        drawCircle(color, radius = 2f, center = Offset(w * 0.85f, lineY))
    }
}

/**
 * Draw corner ornament flourish for HUD panels.
 */
fun DrawScope.drawCornerOrnament(x: Float, y: Float, size: Float, color: Color, flipX: Boolean = false, flipY: Boolean = false) {
    val sx = if (flipX) -1f else 1f
    val sy = if (flipY) -1f else 1f
    val path = Path().apply {
        moveTo(x, y + sy * size * 0.5f)
        cubicTo(
            x + sx * size * 0.1f, y + sy * size * 0.2f,
            x + sx * size * 0.2f, y + sy * size * 0.1f,
            x + sx * size * 0.5f, y
        )
    }
    drawPath(path, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
    // Small curl at end
    drawCircle(color, radius = 2f, center = Offset(x + sx * size * 0.5f, y))
    drawCircle(color, radius = 2f, center = Offset(x, y + sy * size * 0.5f))
}
