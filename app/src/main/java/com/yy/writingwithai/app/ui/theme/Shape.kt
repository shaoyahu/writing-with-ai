package com.yy.writingwithai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * ui-redesign-v2 · Material 3 Shapes(extraSmall=4dp / small=8dp / medium=12dp / large=16dp / extraLarge=24dp)。
 * 注意:值与 CornerRadius.xs/sm/md/lg/xl 一一对应,但 Shapes 是 top-level val,
 * 无法读 LocalCornerRadius,值独立维护。修改时需同步两边。
 * 业务代码需要自定义圆角请走 [LocalCornerRadius.current],不要在这里加。
 */
internal val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
