package com.yy.writingwithai.core.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * language-switcher · locale 解析 + Context wrap 工具。
 *
 * 纯 object 无依赖，被 [com.yy.writingwithai.app.WritingApp] 冷启动 attachBaseContext
 * 调用，也供测试独立 verify。
 */
enum class LocaleSelection(val key: String) {
    SYSTEM("system"),
    ZH("zh"),
    EN("en");

    companion object {
        fun fromKey(key: String?): LocaleSelection = when (key) {
            "zh" -> ZH
            "en" -> EN
            else -> SYSTEM
        }
    }
}

object LocaleHelper {
    /**
     * 把 [selection] 解析成具体 [Locale];SYSTEM 走系统 locale(由 caller 传)。
     */
    fun resolveLocale(selection: LocaleSelection, systemLocale: Locale): Locale = when (selection) {
        LocaleSelection.SYSTEM -> systemLocale
        LocaleSelection.ZH -> Locale("zh")
        LocaleSelection.EN -> Locale("en")
    }

    /**
     * 用 [locale] wrap [base] Context，新 Context 拿资源时会走对应 values/ 或 values-XX/ 资源。
     */
    fun wrap(base: Context, locale: Locale): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
