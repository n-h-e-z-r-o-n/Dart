package com.zadhron.adaa.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AdaaIcons {
    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "TrendingUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(16.0f, 6.0f)
                lineTo(18.29f, 8.29f)
                lineTo(13.41f, 13.17f)
                lineTo(9.41f, 9.17f)
                curveTo(8.63f, 8.39f, 7.37f, 8.39f, 6.59f, 9.17f)
                lineTo(2.71f, 13.05f)
                curveTo(2.32f, 13.44f, 2.32f, 14.07f, 2.71f, 14.46f)
                curveTo(3.1f, 14.85f, 3.73f, 14.85f, 4.12f, 14.46f)
                lineTo(8.0f, 10.58f)
                lineTo(12.0f, 14.58f)
                curveTo(12.78f, 15.36f, 14.04f, 15.36f, 14.82f, 14.58f)
                lineTo(20.41f, 9.0f)
                lineTo(22.7f, 11.29f)
                curveTo(23.01f, 11.6f, 23.55f, 11.38f, 23.55f, 10.94f)
                lineTo(23.55f, 6.55f)
                curveTo(23.55f, 6.25f, 23.3f, 6.0f, 23.0f, 6.0f)
                lineTo(18.61f, 6.0f)
                curveTo(18.17f, 6.0f, 17.95f, 6.54f, 18.26f, 6.85f)
                close()
            }
        }.build()
    }

    val EventNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "EventNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(19.0f, 3.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineTo(2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(3.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(11.0f)
                close()
                moveTo(7.0f, 10.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(7.0f)
                close()
                moveTo(7.0f, 14.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(7.0f)
                close()
            }
        }.build()
    }
}
