package com.example.readeptd.ui

/**
 * TTS UI 事件密封类
 */
sealed interface TtsEvent {
    /**
     * 开始朗读当前页
     */
    object StartSpeaking : TtsEvent
    
    /**
     * 停止朗读
     */
    object StopSpeaking : TtsEvent
}
