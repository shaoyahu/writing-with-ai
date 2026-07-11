package com.yy.writingwithai.core.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ai-usage-statistics §4:`ProviderCostStore` 接口(成本费率,只本机存,明文 SharedPreferences)。
 *
 * 设计备忘:
 * - 数字,无敏感信息,**不**走 EncryptedSharedPreferences(CLAUDE.md "v1 备份策略")。
 * - 默认 `(0.0, 0.0)` —— 完全 opt-in;UI 层据此分支走"未配置成本费率"。
 * - 写用 `Dispatchers.IO`(suspend),避免主线程 commit。
 */
interface ProviderCostStore {
    /** `(inputUsdPer1k, outputUsdPer1k)`;未设则 `(0.0, 0.0)`。 */
    fun getCostRate(providerId: String): Pair<Double, Double>

    suspend fun setCostRate(providerId: String, input: Double, output: Double)
}

@Singleton
class ProviderCostStoreImpl
@Inject
constructor(
    @ApplicationContext context: Context
) : ProviderCostStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getCostRate(providerId: String): Pair<Double, Double> {
        // 没有 key 时 prefs.getFloat 默认返 0f;UI 把 (0, 0) 当"未配置"。
        val input = prefs.getFloat(keyInput(providerId), 0f).toDouble()
        val output = prefs.getFloat(keyOutput(providerId), 0f).toDouble()
        return input to output
    }

    override suspend fun setCostRate(providerId: String, input: Double, output: Double) {
        // fix-review-r1 F5:NaN/Infinity / 负数 防御。prefs.putFloat 对 NaN 静默丢弃
        // (Android 实现把它直接 as Float 0f),对 Infinity 抛 IllegalArgumentException
        // 路径不一致;UI 端把成本乘到 token 数上算 USD,负数会让"显示成本 < 0"误导用户。
        // 统一在入口 require,把脏值挡在写入前。
        require(input.isFinite() && input >= 0.0) {
            "input rate must be finite and non-negative, got $input"
        }
        require(output.isFinite() && output >= 0.0) {
            "output rate must be finite and non-negative, got $output"
        }
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putFloat(keyInput(providerId), input.toFloat())
                .putFloat(keyOutput(providerId), output.toFloat())
                .apply()
        }
    }

    private fun keyInput(providerId: String) = "rate.in.$providerId"
    private fun keyOutput(providerId: String) = "rate.out.$providerId"

    private companion object {
        const val PREFS_NAME = "provider_cost"
    }
}
