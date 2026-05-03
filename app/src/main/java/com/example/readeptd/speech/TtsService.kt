package com.example.readeptd.speech

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.readeptd.ContentActivity
import com.example.readeptd.data.FileDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TtsService : Service() {

    interface TtsListener {
        fun onInitSuccess()
        fun onInitFailure(errorCode: Int)
        fun onSpeechStart(utteranceId: String?)
        fun onSpeechDone(utteranceId: String?)
        fun onSpeechError(utteranceId: String?)
        fun onRequestNextPage()
        fun onRequestPreviousPage()
    }

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 5981
        private const val CHANNEL_ID = "tts_service_channel"
        private const val CHANNEL_NAME = "TTS朗读服务"
        
        // Action 常量
        const val ACTION_TOGGLE_PLAY = "com.example.readeptd.ACTION_TOGGLE_PLAY"
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
    
    // ContentActivity 是否可见
    private var isContentActivityVisible = true
    
    // 是否显示通知（从配置读取）
    private var showTtsNotification: Boolean = true

    private var speakQueueManager = SpeakTextSplitManager()

    // 通知管理器（提取为成员变量，避免重复获取）
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private val scope: CoroutineScope = MainScope()

    // Activity 生命周期回调
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {
            if (activity is ContentActivity) {
                Log.d(TAG, "ContentActivity 恢复可见")
                isContentActivityVisible = true
                // 用户在 ContentActivity 界面时，隐藏通知
                hideNotification()
            }
        }
        override fun onActivityPaused(activity: Activity) {
            if (activity is ContentActivity) {
                Log.d(TAG, "ContentActivity 暂停，变为不可见")
                isContentActivityVisible = false
                // 用户离开 ContentActivity 且正在播放时，才显示通知
                if (isPlaying) {
                    showNotification()
                } else {
                    Log.d(TAG, "ContentActivity 不可见但未播放，不显示通知")
                }
            }
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    // 广播接收器（处理通知栏按钮点击）
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAY -> handleTogglePlay()
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
        // 加载配置
        loadConfigure()
        // 注册 Activity 生命周期监听
        (application as Application).registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> handleTogglePlay()
            ACTION_STOP -> handleStop()
            ACTION_PREVIOUS -> handlePrevious()
            ACTION_NEXT -> handleNext()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            // 接收器未注册
        }

        // 注销 Activity 生命周期监听
        (application as Application).unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        shutdown()
        // 清理通知
        notificationManager.cancel(NOTIFICATION_ID)
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
     * 加载配置
     */
    private fun loadConfigure() {
        scope.launch {
            try {
                val dataStore = FileDataStore(applicationContext)
                val configure = dataStore.getConfigure()
                showTtsNotification = configure.showTtsNotification
                Log.d(TAG, "加载配置: showTtsNotification=$showTtsNotification")
            } catch (e: Exception) {
                Log.e(TAG, "加载配置失败: ${e.message}")
                showTtsNotification = true // 默认值
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
                currentText = speakQueueManager.getText(utteranceId?: "")?: ""
                currentUtteranceId = utteranceId
                notifySpeechStart(utteranceId)
                showNotification()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "朗读完成: $utteranceId")
                isPlaying = false
                if (utteranceId == null || speakQueueManager.isLast(utteranceId)) {
                    notifySpeechDone(speakQueueManager.getOriginalUtteranceId())
                }
                //notifySpeechDone(utteranceId)
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "朗读错误: $utteranceId")
                isPlaying = false
                notifySpeechError(utteranceId)
                // 延迟后隐藏通知
                scope.launch {
                    delay(2000)
                    hideNotification()
                }
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

        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 注册通知广播接收器
     */
    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY)
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
     * 显示通知（如果已经在运行则更新）
     */
    private fun showNotification() {
        if(isContentActivityVisible || !showTtsNotification){return}
        val notification = buildNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "已显示/更新通知")
    }

    /**
     * 隐藏通知
     */
    private fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "已隐藏通知")
    }

    /**
     * 构建通知
     */
    private fun buildNotification(): android.app.Notification {
        Log.d(TAG, "构建通知，isPlaying=$isPlaying")

        // 点击通知打开应用
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 播放/暂停按钮（同一个按钮，根据状态显示不同图标和文字）
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "停止",
                createPendingIntent(ACTION_TOGGLE_PLAY)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "播放",
                createPendingIntent(ACTION_TOGGLE_PLAY)
            ).build()
        }

        // 上一章按钮
        val previousAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            "上一页",
            createPendingIntent(ACTION_PREVIOUS)
        ).build()

        // 下一章按钮
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            "下一页",
            createPendingIntent(ACTION_NEXT)
        ).build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(currentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setShowWhen(false)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setAutoCancel(true)
            .build()

        Log.d(TAG, "通知构建完成，包含上一章、播放/暂停、下一章按钮")
        return notification
    }

    /**
     * 创建 PendingIntent
     */
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    //region 通知按钮处理

    private fun handleTogglePlay() {
        Log.d(TAG, "通知栏：播放/暂停切换")
        if (isPlaying) {
            // 当前正在播放，执行暂停
            stop()
            showNotification()
        } else {
            // 当前未播放，执行播放
            if (currentUtteranceId?.isNotEmpty()?: false) {
                speakQueue(currentUtteranceId)
            }
        }
    }

    private fun handleStop() {
        Log.d(TAG, "通知栏：停止")
        stop()
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun handlePrevious() {
        Log.d(TAG, "通知栏：上一页")
        listeners.forEach { it.onRequestPreviousPage() }
    }

    private fun handleNext() {
        Log.d(TAG, "通知栏：下一页")
        listeners.forEach { it.onRequestNextPage() }
    }

    fun speak(text: String, utteranceId: String? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS 未初始化")
            return
        }
        if (text.isBlank()) {
            return
        }
        stop()
        val baseCurrentUtteranceId = utteranceId ?: System.currentTimeMillis().toString()
        val baseCurrentText = text
        speakQueueManager.reset(baseCurrentText, baseCurrentUtteranceId)
        Log.d(TAG, "开始朗读文本: ${text.take(50)}...")
        speakQueue()
    }
    private fun speakQueue(startFromUtteranceId: String? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS 未初始化")
            return
        }
        // 从指定 utteranceId 开始往后读
        val startIndex = if(startFromUtteranceId != null) speakQueueManager.indexOfFirst { it.utteranceId == startFromUtteranceId } else 0
        for (i in startIndex until speakQueueManager.size) {
            val item = speakQueueManager[i]
            //currentText = item.text
            //currentUtteranceId = item.utteranceId
            textToSpeech?.speak(
                item.text,
                TextToSpeech.QUEUE_ADD,
                null,
                item.utteranceId
            )
        }
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

}

data class QueueItem(val text: String, val utteranceId: String?)

class SpeakTextSplitManager() : MutableList<QueueItem> by mutableListOf() {
    private var originalText: String = ""
    private var baseUtteranceId: String? = null
    
    fun reset(text: String, utteranceId: String? = null){
        clear()
        originalText = text
        baseUtteranceId = utteranceId
        splitText()
    }
    
    private fun splitText(){
        val sentences = mutableListOf<String>()
        val regex = Regex("[.。!！?？;；,，、]")
        
        var start = 0
        for (match in regex.findAll(originalText)) {
            val end = match.range.last + 1
            val sentence = originalText.substring(start, end).trim()
            if (sentence.isNotEmpty()) {
                sentences.add(sentence)
            }
            start = end
        }
        
        // 处理剩余部分
        if (start < originalText.length) {
            val remaining = originalText.substring(start).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(remaining)
            }
        }
        
        // 如果分割结果为空，使用原文本
        val finalSentences = if (sentences.isEmpty()) listOf(originalText) else sentences
        
        // 构建队列
        finalSentences.forEachIndexed { index, sentence ->
            val utteranceId = "${baseUtteranceId}_part_${index}"
            add(QueueItem(sentence, utteranceId))
        }
    }
        
    fun getOriginalUtteranceId(): String?{
        return baseUtteranceId
    }
    
    fun getText(utteranceId: String): String?{
        return firstOrNull { it.utteranceId == utteranceId }?.text
    }
    
    fun isLast(utteranceId: String): Boolean{
        return utteranceId == lastOrNull()?.utteranceId
    }
    
}