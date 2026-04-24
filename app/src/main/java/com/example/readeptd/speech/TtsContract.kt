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
     * 停止朗读
     */
    object StopSpeaking : TtsEvent
}
