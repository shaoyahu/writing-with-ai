package com.yy.writingwithai.core.media

import javax.inject.Inject
import javax.inject.Singleton

/**
 * voice-insert · 音频录制器骨架。
 * B6c 实现: MediaRecorder API + RECORD_AUDIO 权限 + ASR 集成。
 * 当前为空实现。
 */
@Singleton
class AudioRecorder @Inject constructor() {
    fun start(outputPath: String) {
        // B6c: 实现 MediaRecorder 录音
    }

    fun stop(): String? {
        // B6c: 返回录音文件路径
        return null
    }
}
