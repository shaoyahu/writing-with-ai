package com.yy.writingwithai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * ui-redesign-v2 · Material 3 Shapes(extraSmall/small/medium/large/extraLarge)。
 *
 * fix M53 (full-review):值与 [CornerRadius.xs/sm/md/lg/xl] 一一对应,过去是手写 .dp,
 * 两边独立维护容易漂移;改为读取默认 [CornerRadius] 实例的字段,改一处自动同步。
 * Compose Shapes 是 top-level val,无法直接走 [LocalCornerRadius](在 Composition 之外),
 * 但用静态默认值是安全的——[CornerRadius] 默认值与 theme 提供值一致,且 CornerRadius
 * 字段都是 val,启动期不可变。
 */
internal val Shapes: Shapes = run {
    val r = CornerRadius()
    Shapes(
        extraSmall = RoundedCornerShape(r.xs),
        small = RoundedCornerShape(r.sm),
        medium = RoundedCornerShape(r.md),
        large = RoundedCornerShape(r.lg),
        extraLarge = RoundedCornerShape(r.xl)
    )
}
