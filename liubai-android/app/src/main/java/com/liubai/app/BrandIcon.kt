package com.liubai.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 品牌图标「留白之环」：60° 缺口朝上。
 * material-icons-core 不含计时器图标，这里自绘以保持零额外依赖。
 */
val IconMarginRing: ImageVector by lazy {
    ImageVector.Builder(
        name = "MarginRing",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        // 缺口留在正上方：从 300° 起绕 300°
        moveTo(18.6f, 5.6f)
        arcTo(
            horizontalEllipseRadius = 9.5f,
            verticalEllipseRadius = 9.5f,
            theta = 0f,
            isMoreThanHalf = true,
            isPositiveArc = true,
            x1 = 5.4f,
            y1 = 5.6f,
        )
    }.build()
}
