package com.example.readeptd.speech

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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
    private var serviceBound = false

    private var onSpeechStartCallback: (() -> Unit)? = null
    private var onSpeechDoneCallback: ((String?) -> Unit)? = null
    private var onRequestSpeechStartCallback: (() -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.LocalBinder
            ttsService = binder.getService()
            serviceBound = true
            
            // 注册监听器
            binder.registerListener(this@TtsViewModel)
            
            Log.d(TAG, "服务已连接")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            serviceBound = false
            _isSpeaking.value = false
            Log.d(TAG, "服务已断开")
        }
    }

    init {
        bindTtsService()
    }

    private fun bindTtsService() {
        val intent = Intent(getApplication(), TtsService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
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

    fun speak(text: String, utteranceId: String? = null) {
        if (serviceBound && ttsService != null) {
            ttsService?.speak(text, utteranceId)
        } else {
            Log.e(TAG, "TTS 服务未绑定,无法朗读")
        }
    }

    fun stop() {
        if (serviceBound) {
            ttsService?.stop()
            _isSpeaking.value = false
        }
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        if (serviceBound) {
            ttsService?.setSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch
        if (serviceBound) {
            ttsService?.setPitch(pitch)
        }
    }

    fun setLanguage(locale: Locale): Boolean {
        if (!serviceBound) return false
        val result = ttsService?.setLanguage(locale)
        return result != null && result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun getAvailableLanguages(): Set<Locale>? {
        return if (serviceBound) ttsService?.getAvailableLanguages() else null
    }

    fun checkIsSpeaking(): Boolean {
        return if (serviceBound) ttsService?.isSpeaking() ?: false else false
    }

    fun isReady(): Boolean {
        return if (serviceBound) ttsService?.isReady() ?: false else false
    }

    fun setOnSpeechStartListener(callback: () -> Unit) {
        onSpeechStartCallback = callback
    }

    fun setOnSpeechDoneListener(callback: (String?) -> Unit) {
        onSpeechDoneCallback = callback
    }

    fun setOnRequestSpeechStartListener(callback: () -> Unit) {
        onRequestSpeechStartCallback = callback
    }

    fun clearCallbacks() {
        onSpeechStartCallback = null
        onSpeechDoneCallback = null
        onRequestSpeechStartCallback = null
    }

    fun onEvent(event: TtsEvent) {
        when (event) {
            is TtsEvent.RequestAutoSpeak -> {
                if(!_isSpeaking.value) {
                    onRequestSpeechStartCallback?.invoke()
                }
            }
            is TtsEvent.StopSpeaking -> {
                stop()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearCallbacks()
        
        // 解绑服务
        if (serviceBound) {
            ttsService?.let { service ->
                val binder = service.binder
                binder.unregisterListener(this)
            }
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        
        Log.d(TAG, "ViewModel清理，服务已解绑")
    }

    companion object {
        private const val TAG = "TtsViewModel"
    }
}