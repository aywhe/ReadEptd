package com.example.readeptd.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.readeptd.service.TtsService
import com.example.readeptd.ui.TtsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TTS ViewModel
 * 管理 TTS 服务的状态和生命周期
 */
class TtsViewModel(application: Application) : AndroidViewModel(application), TtsService.TtsListener {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private var ttsService: TtsService? = null
    
    // 自动朗读回调(当 TTS 开始/完成时触发,用于获取下一页文本)
    private var onSpeechStartCallback: (() -> Unit)? = null
    private var onSpeechDoneCallback: ((String?) -> Unit)? = null
    private var onRequestSpeechStartCallback: (() -> Unit)? = null

    init {
        initializeTts()
    }

    /**
     * 初始化 TTS 服务
     */
    private fun initializeTts() {
        ttsService = TtsService(
            context = getApplication(),
            listener = this
        )
    }

    //region TtsListener 回调实现

    override fun onInitSuccess() {
        _isInitialized.value = true
    }

    override fun onInitFailure(errorCode: Int) {
        _isInitialized.value = false
    }

    override fun onSpeechStart(utteranceId: String?) {
        _isSpeaking.value = true
        onSpeechStartCallback?.invoke()
    }

    override fun onSpeechDone(utteranceId: String?) {
        _isSpeaking.value = false
        onSpeechDoneCallback?.invoke(utteranceId)
    }

    override fun onSpeechError(utteranceId: String?) {
        _isSpeaking.value = false
    }

    //endregion

    /**
     * 朗读文本
     */
    fun speak(text: String) {
        Log.d(TAG, "TtsViewModel.speak() 被调用, 文本长度: ${text.length}, isInitialized: ${_isInitialized.value}")
        if (_isInitialized.value) {
            Log.d(TAG, "调用 ttsService?.speak()")
            ttsService?.speak(text)
        } else {
            Log.e(TAG, "TTS 未初始化,无法朗读")
        }
    }

    /**
     * 停止朗读
     */
    fun stop() {
        ttsService?.stop()
    }

    /**
     * 设置语速
     */
    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        ttsService?.setSpeechRate(rate)
    }

    /**
     * 设置音调
     */
    fun setPitch(pitch: Float) {
        _pitch.value = pitch
        ttsService?.setPitch(pitch)
    }

    /**
     * 设置语言
     */
    fun setLanguage(locale: Locale): Boolean {
        val result = ttsService?.setLanguage(locale)
        return result != null && result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * 获取支持的语言列表
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return ttsService?.getAvailableLanguages()
    }

    /**
     * 检查是否正在朗读
     */
    fun checkIsSpeaking(): Boolean {
        return ttsService?.isSpeaking() ?: false
    }

    /**
     * 检查 TTS 是否就绪
     */
    fun isReady(): Boolean {
        return ttsService?.isReady() ?: false
    }
    
    /**
     * 设置自动朗读开始回调(TTS 开始朗读时触发,用于获取当前页文本)
     */
    fun setOnSpeechStartListener(callback: () -> Unit) {
        onSpeechStartCallback = callback
    }
    
    /**
     * 设置自动朗读完成回调(TTS 朗读完成时触发,用于翻页并获取下一页文本)
     */
    fun setOnSpeechDoneListener(callback: (String?) -> Unit) {
        onSpeechDoneCallback = callback
    }

    /**
     * 获取自动朗读请求回调(当请求自动朗读时触发,用于获取当前页文本)
     */
    fun setOnRequestSpeechStartListener(callback: () -> Unit) {
        onRequestSpeechStartCallback = callback
    }
    
    /**
     * 清除回调
     */
    fun clearCallbacks() {
        onSpeechStartCallback = null
        onSpeechDoneCallback = null
        onRequestSpeechStartCallback = null
    }

    /**
     * 处理 TTS 事件
     */
    fun onEvent(event: TtsEvent) {
        when (event) {
            is TtsEvent.RequestAutoSpeak -> {
                // 请求开始自动朗读,触发回调获取文本并开始朗读
                onRequestSpeechStartCallback?.invoke()
            }
            is TtsEvent.StopSpeaking -> {
                stop()
            }
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        // 清除回调，避免内存泄漏
        clearCallbacks()
        ttsService?.shutdown()
        ttsService = null
    }

    companion object {
        private const val TAG = "TtsViewModel"
    }
}
