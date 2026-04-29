package com.example.readeptd.speech

/**
 * TTS UI 事件密封类
 */
sealed interface TtsEvent {
    /**
     * 请求开始自动朗读(获取文本并朗读)
     */
    object RequestAutoSpeak : TtsEvent

    /**
     * 开始计时器
     */
    data class StartCountDownTimer(val millisInFuture: Long): TtsEvent

    /**
     * 停止计时器
     */
    object RemoveCountDownTimer: TtsEvent
}
