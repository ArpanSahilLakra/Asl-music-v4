package racra.compose.smooth_corner_rect_library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight drop-in replacement for AbsoluteSmoothCornerShape that delegates to
 * RoundedCornerShape without pulling in the transitive Compose Material 2 library.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun AbsoluteSmoothCornerShape(
    cornerRadius: Dp,
    smoothnessAsPercent: Int = 60,
): Shape = RoundedCornerShape(cornerRadius)

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun AbsoluteSmoothCornerShape(
    cornerRadiusTL: Dp = 0.dp,
    smoothnessAsPercentTL: Int = 60,
    cornerRadiusTR: Dp = 0.dp,
    smoothnessAsPercentTR: Int = 60,
    cornerRadiusBR: Dp = 0.dp,
    smoothnessAsPercentBR: Int = 60,
    cornerRadiusBL: Dp = 0.dp,
    smoothnessAsPercentBL: Int = 60,
): Shape = RoundedCornerShape(
    topStart = cornerRadiusTL,
    topEnd = cornerRadiusTR,
    bottomEnd = cornerRadiusBR,
    bottomStart = cornerRadiusBL,
)
