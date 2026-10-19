package com.liubai.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「留白之环」——品牌核心图形。
 * 60° 的缺口 = 留给生活的「白」；环上光点随进度移动，像一天中走过的路。
 *
 * @param progress 0f..1f（超出 1 表示超额，光点变为警示色）
 * @param gapDegrees 缺口角度，默认 60°（正上方居中）
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 220.dp,
    strokeWidth: Dp = 16.dp,
    trackColor: Color = Color.LightGray,
    progressColor: Color = Color(0xFF0A7A61),
    overageColor: Color = Color(0xFFD93940),
    gapDegrees: Float = 60f,
    glow: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 640),
        label = "ring",
    )
    val overage = progress > 1f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val gap = gapDegrees
            val startAngle = 90f + gap / 2f
            val sweep = 360f - gap

            // 底环
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // 进度弧
            if (animated > 0f) {
                drawArc(
                    color = if (overage) overageColor else progressColor,
                    startAngle = startAngle,
                    sweepAngle = sweep * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // 光点：进度末端
                if (glow && animated < 0.995f) {
                    val angleDeg = startAngle + sweep * animated
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val radius = (arcSize.width / 2f)
                    val cx = size.width / 2f + radius * kotlin.math.cos(rad).toFloat()
                    val cy = size.height / 2f + radius * kotlin.math.sin(rad).toFloat()
                    drawCircle(
                        color = if (overage) overageColor else progressColor,
                        radius = stroke * 0.42f,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = stroke * 0.16f,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
    }
}
