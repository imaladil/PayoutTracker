package com.payouttracker.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A plain tennis ball (round outline with the two curved seams), so Tennis differs from Table Tennis. */
val TennisBallIcon: ImageVector = ImageVector.Builder(
    name = "TennisBall",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
        // the ball
        moveTo(12f, 2.5f)
        arcTo(9.5f, 9.5f, 0f, true, true, 12f, 21.5f)
        arcTo(9.5f, 9.5f, 0f, true, true, 12f, 2.5f)
        close()
        // left seam
        moveTo(5.2f, 5.4f)
        curveTo(9.6f, 9f, 9.6f, 15f, 5.2f, 18.6f)
        // right seam
        moveTo(18.8f, 5.4f)
        curveTo(14.4f, 9f, 14.4f, 15f, 18.8f, 18.6f)
    }
}.build()
