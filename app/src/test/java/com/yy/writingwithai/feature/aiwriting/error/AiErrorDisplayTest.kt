package com.yy.writingwithai.feature.aiwriting.error

import android.content.Context
import com.yy.writingwithai.R
import com.yy.writingwithai.core.ai.api.AiError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AiErrorDisplayTest {
    private val context: Context = mockk()

    @Test
    fun network_maps_to_network_string() {
        every { context.getString(R.string.aiwriting_error_network) } returns "网络连接失败"
        assertEquals("网络连接失败", AiError.Network(500, "timeout").toDisplayMessage(context))
    }

    @Test
    fun auth_maps_to_auth_string() {
        every { context.getString(R.string.aiwriting_error_auth) } returns "认证失败"
        assertEquals("认证失败", AiError.Auth(401, "bad key").toDisplayMessage(context))
    }

    @Test
    fun insufficient_balance_maps_to_balance_string() {
        every { context.getString(R.string.aiwriting_error_balance) } returns "余额不足"
        assertEquals("余额不足", AiError.InsufficientBalance("no money").toDisplayMessage(context))
    }

    @Test
    fun timeout_maps_to_timeout_string() {
        every { context.getString(R.string.aiwriting_error_timeout) } returns "请求超时"
        assertEquals("请求超时", AiError.Timeout("30s").toDisplayMessage(context))
    }

    @Test
    fun unknown_maps_to_unknown_string() {
        every { context.getString(R.string.aiwriting_error_unknown) } returns "出错了"
        assertEquals("出错了", AiError.Unknown(null, "weird").toDisplayMessage(context))
    }

    @Test
    fun deserialization_maps_to_unknown_string() {
        every { context.getString(R.string.aiwriting_error_unknown) } returns "出错了"
        assertEquals("出错了", AiError.Deserialization("bad json").toDisplayMessage(context))
    }
}
