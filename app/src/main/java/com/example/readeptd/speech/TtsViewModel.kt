package com.example.readeptd.speech

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TtsViewModel(application: Application) : AndroidViewModel(application),
    TtsService.TtsListener {

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

    // 自动朗读回调(当 TTS 开始/完成时触发,用于获取下一页文本)
    private var onSpeechStartCallback: (() -> Unit)? = null
    private var onSpeechDoneCallback: ((String?) -> Unit)? = null
    private var onRequestSpeechStartCallback: (() -> Unit)? = null
    private var onRequestNextPageCallback: (() -> Unit)? = null
    private var onRequestPreviousPageCallback: (() -> Unit)? = null

    private var countDownTimer: TtsCountDownTimer? = null
    private var countDownTimerFinishedDelayFlag = false
    private val _remainingMillisTime = MutableStateFlow(0L)
    val remainingMillisTime: StateFlow<Long> = _remainingMillisTime.asStateFlow()


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
    override fun onRequestNextPage(){
        onRequestNextPageCallback?.invoke()
    }
    override fun onRequestPreviousPage(){
        onRequestPreviousPageCallback?.invoke()
    }

    //endregion

    /**
     * 朗读文本
     */
    fun speak(text: String, utteranceId: String? = null) {
        if (serviceBound && ttsService != null) {
            if (countDownTimerFinishedDelayFlag) {
                Log.d(TAG, "定时器触发了停止标记还在")
                return
            }
            if (_isInitialized.value) {
                ttsService?.speak(text, utteranceId)
            } else {
                Log.e(TAG, "TTS 服务未绑定,无法朗读")
            }
        } else {
            Log.e(TAG, "TTS 服务未绑定,无法朗读")
        }
    }

    /**
     * 停止朗读
     */
    fun stop() {
        if (serviceBound) {
            ttsService?.stop()
            _isSpeaking.value = false
        }
    }

    /**
     * 设置语速
     */
    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        if (serviceBound) {
            ttsService?.setSpeechRate(rate)
        }
    }

    /**
     * 设置音调
     */
    fun setPitch(pitch: Float) {
        _pitch.value = pitch
        if (serviceBound) {
            ttsService?.setPitch(pitch)
        }
    }

    /**
     * 设置语言
     */
    fun setLanguage(locale: Locale): Boolean {
        if (!serviceBound) return false
        val result = ttsService?.setLanguage(locale)
        return result != null && result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * 获取支持的语言列表
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return if (serviceBound) ttsService?.getAvailableLanguages() else null
    }

    /**
     * 检查是否正在朗读
     */
    fun checkIsSpeaking(): Boolean {
        return if (serviceBound) ttsService?.isSpeaking() ?: false else false
    }

    /**
     * 检查 TTS 是否就绪
     */
    fun isReady(): Boolean {
        return if (serviceBound) ttsService?.isReady() ?: false else false
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
    fun setOnRequestNextPageListener(callback: () -> Unit) {
        onRequestNextPageCallback = callback
    }
    fun setOnRequestPreviousPageListener(callback: () -> Unit) {
        onRequestPreviousPageCallback = callback
    }

    /**
     * 清除回调
     */
    fun clearCallbacks() {
        onSpeechStartCallback = null
        onSpeechDoneCallback = null
        onRequestSpeechStartCallback = null
        onRequestNextPageCallback = null
        onRequestPreviousPageCallback = null
    }

    /**
     * 处理 TTS 事件
     */
    fun onEvent(event: TtsEvent) {
        when (event) {
            is TtsEvent.RequestAutoSpeak -> {
                if (!_isSpeaking.value) {
                    // 请求开始自动朗读,触发回调获取文本并开始朗读
                    onRequestSpeechStartCallback?.invoke()
                }
            }

            is TtsEvent.StartCountDownTimer -> {
                Log.d(TAG, "开始定时器,剩余时间:${event.millisInFuture / 1000f / 60f}分钟")
                countDownTimerFinishedDelayFlag = false
                countDownTimer?.cancel()
                countDownTimer = null
                _remainingMillisTime.value = event.millisInFuture
                countDownTimer = TtsCountDownTimer(
                    millisInFuture = event.millisInFuture,
                    onTickCallback = { millisRemainingTime ->
                        _remainingMillisTime.value = millisRemainingTime
                    },
                    onFinishCallback = {
                        Log.d(TAG, "定时器触发了")
                        stop()
                        _remainingMillisTime.value = 0L
                        countDownTimerFinishedDelayFlag = true
                        viewModelScope.launch {
                            // 延迟5秒后重置标记
                            delay(5000)
                            if (countDownTimerFinishedDelayFlag) {
                                countDownTimerFinishedDelayFlag = false
                            }
                        }
                    }
                )
                countDownTimer?.start()
            }

            is TtsEvent.RemoveCountDownTimer -> {
                removeCountDownTimer()
            }
        }
    }

    private fun removeCountDownTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _remainingMillisTime.value = 0L
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        removeCountDownTimer()
        // 清除回调，避免内存泄漏
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