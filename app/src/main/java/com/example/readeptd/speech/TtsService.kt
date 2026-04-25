package com.example.readeptd.speech

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.readeptd.R

class TtsService : Service() {

    interface TtsListener {
        fun onInitSuccess()
        fun onInitFailure(errorCode: Int)
        fun onSpeechStart(utteranceId: String?)
        fun onSpeechDone(utteranceId: String?)
        fun onSpeechError(utteranceId: String?)
    }

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_service_channel"
        private const val CHANNEL_NAME = "TTS朗读服务"
        
        // Action 常量
        const val ACTION_PLAY = "com.example.readeptd.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.readeptd.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.readeptd.ACTION_STOP"
        const val ACTION_PREVIOUS = "com.example.readeptd.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.readeptd.ACTION_NEXT"
    }

    internal val binder = LocalBinder()
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var currentUtteranceId: String? = null
    
    // 回调监听器列表（支持多个监听器）
    private val listeners = mutableListOf<TtsListener>()
    
    // 当前播放状态
    private var isPlaying = false
    private var currentText: String = ""
    
    // 广播接收器（处理通知栏按钮点击）
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> handlePlay()
                ACTION_PAUSE -> handlePause()
                ACTION_STOP -> handleStop()
                ACTION_PREVIOUS -> handlePrevious()
                ACTION_NEXT -> handleNext()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
        
        // 注册监听器
        fun registerListener(listener: TtsListener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
        
        // 注销监听器
        fun unregisterListener(listener: TtsListener) {
            listeners.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeTts()
        createNotificationChannel()
        registerNotificationReceiver()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理从通知栏传来的动作
        when (intent?.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 初始化 TTS 引擎
     */
    private fun initializeTts() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(android.os.LocaleList.getDefault()[0])

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "不支持的语言")
                    notifyInitFailure(result ?: -1)
                } else {
                    isInitialized = true
                    textToSpeech?.setSpeechRate(1.0f)
                    textToSpeech?.setPitch(1.0f)
                    setupUtteranceProgressListener()
                    Log.d(TAG, "TTS 初始化成功")
                    notifyInitSuccess()
                }
            } else {
                Log.e(TAG, "TTS 初始化失败，错误码: $status")
                notifyInitFailure(status)
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
                isPlaying = true
                notifySpeechStart(utteranceId)
                updateNotification(true)
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "朗读完成: $utteranceId")
                isPlaying = false
                notifySpeechDone(utteranceId)
                updateNotification(false)
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "朗读错误: $utteranceId")
                isPlaying = false
                notifySpeechError(utteranceId)
                updateNotification(false)
            }
        })
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TTS朗读服务通知"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 注册通知广播接收器
     */
    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_STOP)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
            registerReceiver(notificationReceiver, filter)
        }
    }

    /**
     * 启动前台服务并显示通知
     */
    private fun startForegroundNotification() {
        val notification = buildNotification(isPlaying = false)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 构建通知
     */
    private fun buildNotification(isPlaying: Boolean): android.app.Notification {
        // 点击通知打开应用
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 播放/暂停按钮
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "暂停",
                createPendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "播放",
                createPendingIntent(ACTION_PLAY)
            ).build()
        }

        // 停止按钮
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "停止",
            createPendingIntent(ACTION_STOP)
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TTS朗读")
            .setContentText(if (isPlaying) "正在朗读..." else "已暂停")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    /**
     * 创建 PendingIntent
     */
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 更新通知
     */
    private fun updateNotification(isPlaying: Boolean) {
        val notification = buildNotification(isPlaying)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    //region 通知按钮处理

    private fun handlePlay() {
        Log.d(TAG, "通知栏：播放")
        if (currentText.isNotEmpty()) {
            speak(currentText, currentUtteranceId)
        }
    }

    private fun handlePause() {
        Log.d(TAG, "通知栏：暂停")
        stop()
    }

    private fun handleStop() {
        Log.d(TAG, "通知栏：停止")
        stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handlePrevious() {
        Log.d(TAG, "通知栏：上一章")
        // TODO: 实现上一章逻辑，通过回调通知ViewModel
        listeners.forEach { it.onSpeechError("PREVIOUS") }
    }

    private fun handleNext() {
        Log.d(TAG, "通知栏：下一章")
        // TODO: 实现下一章逻辑，通过回调通知ViewModel
        listeners.forEach { it.onSpeechError("NEXT") }
    }

    //endregion

    /**
     * 朗读文本
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

        currentText = text
        currentUtteranceId = utteranceId ?: System.currentTimeMillis().toString()
        
        // 启动前台服务
        startForegroundNotification()

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
        isPlaying = false
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
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.1f, 3.0f)
        textToSpeech?.setSpeechRate(clampedRate)
        Log.d(TAG, "设置语速: $clampedRate")
    }

    /**
     * 设置音调
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.1f, 2.0f)
        textToSpeech?.setPitch(clampedPitch)
        Log.d(TAG, "设置音调: $clampedPitch")
    }

    /**
     * 设置语言
     */
    fun setLanguage(locale: java.util.Locale): Int {
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
    fun getAvailableLanguages(): Set<java.util.Locale>? {
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
        isPlaying = false
        Log.d(TAG, "TTS 服务已关闭")
    }

    /**
     * 检查 TTS 是否已初始化
     */
    fun isReady(): Boolean {
        return isInitialized && textToSpeech != null
    }

    //region 通知监听器

    private fun notifyInitSuccess() {
        listeners.forEach { it.onInitSuccess() }
    }

    private fun notifyInitFailure(errorCode: Int) {
        listeners.forEach { it.onInitFailure(errorCode) }
    }

    private fun notifySpeechStart(utteranceId: String?) {
        listeners.forEach { it.onSpeechStart(utteranceId) }
    }

    private fun notifySpeechDone(utteranceId: String?) {
        listeners.forEach { it.onSpeechDone(utteranceId) }
    }

    private fun notifySpeechError(utteranceId: String?) {
        listeners.forEach { it.onSpeechError(utteranceId) }
    }

    //endregion
}