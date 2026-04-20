package com.example.readeptd.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readeptd.service.TtsService
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
    }

    override fun onSpeechDone(utteranceId: String?) {
        _isSpeaking.value = false
    }

    override fun onSpeechError(utteranceId: String?) {
        _isSpeaking.value = false
    }

    //endregion

    /**
     * 朗读文本
     */
    fun speak(text: String) {
        if (_isInitialized.value) {
            ttsService?.speak(text)
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
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        ttsService?.shutdown()
        ttsService = null
    }
}
