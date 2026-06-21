package com.yy.writingwithai.core.widget

import android.os.Build

/**
 * widget-rome-compat · 国产 Android ROM 识别。
 *
 * 用 [Build.MANUFACTURER] + [Build.BRAND] 白名单命中 4 大国产 ROM;
 * 兜底 [AOSP](Pixel / 三星国际 / 一加海外 / 索尼 / 摩托)。
 *
 * 子品牌(Redmi / Honor / realme / iQOO)通过 [Build.BRAND] 兜底命中。
 */
enum class RomVendor {
    MIUI,
    EMUI,
    COLOROS,
    ORIGINOS,
    AOSP
}

object RomDetector {
    fun current(): RomVendor {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return when {
            manufacturer.equals("Xiaomi", ignoreCase = true) ||
                brand.contains("Redmi", ignoreCase = true) -> RomVendor.MIUI

            manufacturer.equals("HUAWEI", ignoreCase = true) ||
                brand.contains("Honor", ignoreCase = true) -> RomVendor.EMUI

            manufacturer.equals("OPPO", ignoreCase = true) ||
                brand.contains("realme", ignoreCase = true) -> RomVendor.COLOROS

            manufacturer.equals("vivo", ignoreCase = true) ||
                brand.contains("iQOO", ignoreCase = true) -> RomVendor.ORIGINOS

            else -> RomVendor.AOSP
        }
    }
}
