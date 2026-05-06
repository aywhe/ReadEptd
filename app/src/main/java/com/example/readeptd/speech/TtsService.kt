package com.example.readeptd.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * TTS (Text-to-Speech) 服务
 * 提供文本转语音功能
 */
class TtsService(
    private val context: Context,
    private val listener: TtsListener? = null
) {

    interface TtsListener {
        fun onInitSuccess()
        fun onInitFailure(errorCode: Int)
        fun onSpeechStart(utteranceId: String?)
        fun onSpeechDone(utteranceId: String?)
        fun onSpeechError(utteranceId: String?)
    }

    companion object {
        private const val TAG = "TtsService"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var currentUtteranceId: String? = null

    init {
        initializeTts()
    }

    /**
     * 初始化 TTS 引擎
     */
    private fun initializeTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // ✅ 使用系统默认语言,而不是硬编码中文
                val result = textToSpeech?.setLanguage(Locale.getDefault())

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "不支持的语言: ${Locale.getDefault()}")
                    listener?.onInitFailure(result ?: -1)
                } else {
                    isInitialized = true

                    // 设置语速和音调（可选）
                    textToSpeech?.setSpeechRate(1.0f)  // 正常语速
                    textToSpeech?.setPitch(1.0f)       // 正常音调

                    // 设置进度监听器
                    setupUtteranceProgressListener()

                    Log.d(TAG, "TTS 初始化成功")
                    listener?.onInitSuccess()
                }
            } else {
                Log.e(TAG, "TTS 初始化失败，错误码: $status")
                listener?.onInitFailure(status)
            }
        }
    }

    /**
     * 设置语音进度监听器
     */
    private fun setupUtteranceProgressListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "开始朗读: $utteranceId")
                listener?.onSpeechStart(utteranceId)
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "朗读完成: $utteranceId")
                listener?.onSpeechDone(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "朗读错误: $utteranceId")
                listener?.onSpeechError(utteranceId)
            }
        })
    }

    /**
     * 朗读文本
     * @param text 要朗读的文本
     * @param utteranceId 语音段ID，用于追踪
     */
    fun speak(text: String, utteranceId: String? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS 未初始化")
            return
        }

        if (text.isBlank()) {
            Log.w(TAG, "文本为空")
            return
        }

        currentUtteranceId = utteranceId ?: System.currentTimeMillis().toString()

        // 使用 QUEUE_FLUSH 模式：清除队列中的其他语音，立即播放当前文本
        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            currentUtteranceId
        )

        Log.d(TAG, "开始朗读文本: ${text.take(50)}...")
    }

    /**
     * 停止朗读
     */
    fun stop() {
        textToSpeech?.stop()
        Log.d(TAG, "停止朗读")
    }

    /**
     * 检查是否正在朗读
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * 设置语速
     * @param rate 语速倍数，1.0 为正常速度，范围 0.1 - 3.0
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.1f, 3.0f)
        textToSpeech?.setSpeechRate(clampedRate)
        Log.d(TAG, "设置语速: $clampedRate")
    }

    /**
     * 设置音调
     * @param pitch 音调倍数，1.0 为正常音调，范围 0.1 - 2.0
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.1f, 2.0f)
        textToSpeech?.setPitch(clampedPitch)
        Log.d(TAG, "设置音调: $clampedPitch")
    }

    /**
     * 设置语言
     * @param locale 语言区域
     */
    fun setLanguage(locale: Locale): Int {
        val result = textToSpeech?.setLanguage(locale) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e(TAG, "不支持的语言: $locale")
        } else {
            Log.d(TAG, "设置语言: $locale")
        }
        return result
    }

    /**
     * 获取支持的语言列表
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return textToSpeech?.availableLanguages
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        Log.d(TAG, "TTS 服务已关闭")
    }

    /**
     * 检查 TTS 是否已初始化
     */
    fun isReady(): Boolean {
        return isInitialized && textToSpeech != null
    }
}