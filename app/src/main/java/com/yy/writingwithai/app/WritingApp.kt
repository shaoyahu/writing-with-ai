package com.yy.writingwithai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * writing-with-ai · Application 入口。
 *
 * 由 `@HiltAndroidApp` 触发 Hilt 组件编译期生成;
 * M2 起在 onCreate 里完成 Room / DataStore / OkHttp 初始化,M0 仅做 Hilt 接入验证。
 */
@HiltAndroidApp
class WritingApp : Application()
