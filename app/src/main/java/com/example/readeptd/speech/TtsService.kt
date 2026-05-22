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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TtsService : Service() {

    /**
     * TTS 事件监听器接口
     */
    interface TtsListener {
        fun onInitSuccess()
        fun onInitFailure(errorCode: Int)
        fun onSpeechStart(utteranceId: String?)
        fun onSpeechDone(utteranceId: String?)
        fun onSpeechPause(utteranceId: String?)
        fun onSpeechError(utteranceId: String?)
        fun onRequestNextPage(utteranceId: String?)
        fun onRequestPreviousPage(utteranceId: String?)
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
        
        // PendingIntent 请求码
        private const val PENDING_INTENT_CONTENT_REQUEST_CODE = 0
        
        // 语速范围限制
        private const val MIN_SPEECH_RATE = 0.1f
        private const val MAX_SPEECH_RATE = 3.0f
        
        // 音调范围限制
        private const val MIN_PITCH = 0.1f
        private const val MAX_PITCH = 2.0f
        
        // 错误延迟隐藏通知时间（毫秒）
        private const val ERROR_NOTIFICATION_HIDE_DELAY_MS = 2000L
    }

    internal val binder = LocalBinder()
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var currentUtteranceId: String? = null

    // 回调监听器列表（支持多个监听器，使用 CopyOnWriteArrayList 保证线程安全）
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<TtsListener>()

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

    // 协程作用域（用于异步操作）
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
    
    // 标记广播接收器是否已注册，避免重复注销
    private var isReceiverRegistered = false

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
        Log.d(TAG, "TtsService 创建完成")
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
        Log.d(TAG, "TtsService 销毁中...")
        
        // 取消所有协程
        scope.coroutineContext.cancelChildren()
        
        // 注销广播接收器
        unregisterReceiverSafe()

        // 注销 Activity 生命周期监听
        (application as Application).unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)

        // 清理所有监听器
        listeners.clear()

        // 关闭 TTS
        shutdown()
        
        // 清理通知
        notificationManager.cancel(NOTIFICATION_ID)
        
        Log.d(TAG, "TtsService 已销毁")
    }
    
    /**
     * 安全地注销广播接收器
     */
    private fun unregisterReceiverSafe() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(notificationReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "广播接收器已注销")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "广播接收器未注册或已注销: ${e.message}")
            }
        }
    }

    /**
     * 初始化 TTS 引擎
     */
    private fun initializeTts() {
        Log.d(TAG, "开始初始化 TTS 引擎")
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(android.os.LocaleList.getDefault()[0])

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "不支持的语言，错误码: $result")
                    notifyInitFailure(result ?: -1)
                } else {
                    isInitialized = true
                    // 设置默认语速和音调
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
     * 异步加载配置
     */
    private fun loadConfigure() {
        scope.launch {
            try {
                val dataStore = FileDataStore(applicationContext)
                val configure = dataStore.getConfig()
                showTtsNotification = configure.showTtsNotification
                Log.d(TAG, "加载配置: showTtsNotification=$showTtsNotification")
            } catch (e: Exception) {
                Log.e(TAG, "加载配置失败，使用默认值: ${e.message}")
                showTtsNotification = true // 默认显示通知
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
                currentText = speakQueueManager.getText(utteranceId ?: "") ?: ""
                currentUtteranceId = utteranceId
                notifySpeechStart(utteranceId)
                showNotification()
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "朗读完成: $utteranceId")
                isPlaying = false
                
                // 如果是最后一项，通知完成
                if (utteranceId == null || speakQueueManager.isLast(utteranceId)) {
                    notifySpeechDone(speakQueueManager.getOriginalUtteranceId())
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "朗读错误: $utteranceId")
                isPlaying = false
                notifySpeechError(utteranceId)
                
                // 延迟后隐藏通知
                scope.launch {
                    delay(ERROR_NOTIFICATION_HIDE_DELAY_MS)
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
        Log.d(TAG, "通知渠道已创建")
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
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                registerReceiver(notificationReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "广播接收器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败: ${e.message}")
        }
    }

    /**
     * 显示通知（如果已经在运行则更新）
     */
    private fun showNotification() {
        // 如果 ContentActivity 可见或配置为不显示通知，则不显示
        if (isContentActivityVisible || !showTtsNotification) {
            return
        }
        
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
            PENDING_INTENT_CONTENT_REQUEST_CODE,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 播放/暂停按钮（同一个按钮，根据状态显示不同图标和文字）
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "暂停",
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

    /**
     * 处理播放/暂停切换
     */
    private fun handleTogglePlay() {
        Log.d(TAG, "通知栏：播放/暂停切换")
        if (isPlaying) {
            // 当前正在播放，执行暂停
            stop()
            notifySpeechPause(null)
            showNotification()
        } else {
            // 当前未播放，执行播放
            if (!currentUtteranceId.isNullOrEmpty()) {
                speakQueue(currentUtteranceId)
            }
        }
    }

    /**
     * 处理停止
     */
    private fun handleStop() {
        Log.d(TAG, "通知栏：停止")
        stop()
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    /**
     * 处理上一页
     */
    private fun handlePrevious() {
        Log.d(TAG, "通知栏：上一页")
        listeners.forEach { it.onRequestPreviousPage(speakQueueManager.getOriginalUtteranceId()) }
    }

    /**
     * 处理下一页
     */
    private fun handleNext() {
        Log.d(TAG, "通知栏：下一页")
        listeners.forEach { it.onRequestNextPage(speakQueueManager.getOriginalUtteranceId()) }
    }

    //endregion

    /**
     * 开始朗读文本
     * @param text 要朗读的文本内容
     * @param utteranceId 语音段ID，用于追踪，如果为null则自动生成
     */
    fun speak(text: String, utteranceId: String? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS 未初始化")
            return
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "尝试朗读空文本")
            return
        }
        
        // 停止当前朗读
        stop()
        
        val baseCurrentUtteranceId = utteranceId ?: System.currentTimeMillis().toString()
        speakQueueManager.reset(text, baseCurrentUtteranceId)
        
        Log.d(TAG, "开始朗读文本: ${text.take(50)}...")
        speakQueue()
    }
    /**
     * 从队列中朗读文本
     * @param startFromUtteranceId 起始语音ID，如果为null则从头开始
     */
    private fun speakQueue(startFromUtteranceId: String? = null) {
        if (!isInitialized) {
            Log.e(TAG, "TTS 未初始化")
            return
        }
        
        if (speakQueueManager.isEmpty()) {
            Log.w(TAG, "语音队列为空")
            return
        }
        
        // 查找起始位置
        val startIndex = if (startFromUtteranceId != null) {
            speakQueueManager.indexOfFirst { it.utteranceId == startFromUtteranceId }.coerceAtLeast(0)
        } else {
            0
        }
        
        // 将剩余项加入 TTS 队列
        for (i in startIndex until speakQueueManager.size) {
            val item = speakQueueManager[i]
            textToSpeech?.speak(
                item.text,
                TextToSpeech.QUEUE_ADD,
                null,
                item.utteranceId
            )
        }
        
        Log.d(TAG, "已将 ${speakQueueManager.size - startIndex} 项加入 TTS 队列")
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
     * @return 是否正在朗读
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * 设置语速
     * @param rate 语速倍数，范围: 0.1 - 3.0
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        textToSpeech?.setSpeechRate(clampedRate)
        Log.d(TAG, "设置语速: $clampedRate")
    }

    /**
     * 设置音调
     * @param pitch 音调倍数，范围: 0.1 - 2.0
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        textToSpeech?.setPitch(clampedPitch)
        Log.d(TAG, "设置音调: $clampedPitch")
    }

    /**
     * 设置语言
     * @param locale 语言区域
     * @return 设置结果码
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
     * @return 支持的语言集合，如果 TTS 未初始化则返回 null
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
     * 检查 TTS 是否已初始化并可用
     * @return 是否就绪
     */
    fun isReady(): Boolean {
        return isInitialized && textToSpeech != null
    }

    //region 通知监听器

    /**
     * 通知所有监听器 TTS 初始化成功
     */
    private fun notifyInitSuccess() {
        listeners.forEach { it.onInitSuccess() }
    }

    /**
     * 通知所有监听器 TTS 初始化失败
     * @param errorCode 错误码
     */
    private fun notifyInitFailure(errorCode: Int) {
        listeners.forEach { it.onInitFailure(errorCode) }
    }

    /**
     * 通知所有监听器语音开始
     * @param utteranceId 语音ID
     */
    private fun notifySpeechStart(utteranceId: String?) {
        listeners.forEach { it.onSpeechStart(utteranceId) }
    }

    /**
     * 通知所有监听器语音完成
     * @param utteranceId 语音ID
     */
    private fun notifySpeechDone(utteranceId: String?) {
        listeners.forEach { it.onSpeechDone(utteranceId) }
    }

    /**
     * 通知所有监听器语音暂停
     * @param utteranceId 语音ID
     */
    private fun notifySpeechPause(utteranceId: String?) {
        listeners.forEach { it.onSpeechPause(utteranceId) }
    }

    /**
     * 通知所有监听器语音错误
     * @param utteranceId 语音ID
     */
    private fun notifySpeechError(utteranceId: String?) {
        listeners.forEach { it.onSpeechError(utteranceId) }
    }

    //endregion

}

/**
 * 语音队列项数据类
 */
data class QueueItem(val text: String, val utteranceId: String?)

/**
 * 文本分割管理器
 * 负责将长文本按句子分割成多个语音队列项，支持断点续读
 */
class SpeakTextSplitManager : MutableList<QueueItem> by mutableListOf() {
    private var originalText: String = ""
    private var baseUtteranceId: String? = null
    
    companion object {
        // 句子分隔符正则表达式（支持中英文标点）
        private val SENTENCE_SPLITTER_REGEX = Regex("[.。!！?？;；,，、\\s]+")
        // 最大单句长度，避免超长文本导致TTS问题
        private const val MAX_SENTENCE_LENGTH = 500
    }

    /**
     * 重置并分割新文本
     * @param text 待分割的原始文本
     * @param utteranceId 基础语音ID，用于生成子项ID
     */
    fun reset(text: String, utteranceId: String? = null) {
        clear()
        originalText = text
        baseUtteranceId = utteranceId
        splitText()
    }

    /**
     * 按句子分割文本
     * 使用正则表达式匹配句子边界，处理边界情况
     */
    private fun splitText() {
        if (originalText.isBlank()) return
        
        val sentences = mutableListOf<String>()
        var start = 0
        
        // 查找所有句子分隔符
        for (match in SENTENCE_SPLITTER_REGEX.findAll(originalText)) {
            val end = match.range.last + 1
            val sentence = originalText.substring(start, end).trim()
            
            if (sentence.isNotEmpty()) {
                // 如果单句过长，进一步分割
                if (sentence.length > MAX_SENTENCE_LENGTH) {
                    splitLongSentence(sentence, sentences)
                } else {
                    sentences.add(sentence)
                }
            }
            start = end
        }

        // 处理剩余部分（最后一个句子可能没有结束标点）
        if (start < originalText.length) {
            val remaining = originalText.substring(start).trim()
            if (remaining.isNotEmpty()) {
                if (remaining.length > MAX_SENTENCE_LENGTH) {
                    splitLongSentence(remaining, sentences)
                } else {
                    sentences.add(remaining)
                }
            }
        }

        // 如果分割结果为空，使用原文本
        val finalSentences = if (sentences.isEmpty()) listOf(originalText) else sentences

        // 构建语音队列
        finalSentences.forEachIndexed { index, sentence ->
            val utteranceId = "${baseUtteranceId}_part_$index"
            add(QueueItem(sentence, utteranceId))
        }
    }
    
    /**
     * 分割超长句子
     * @param longSentence 超长句子
     * @param sentences 结果列表
     */
    private fun splitLongSentence(longSentence: String, sentences: MutableList<String>) {
        var currentIndex = 0
        while (currentIndex < longSentence.length) {
            val endIndex = minOf(currentIndex + MAX_SENTENCE_LENGTH, longSentence.length)
            val chunk = longSentence.substring(currentIndex, endIndex).trim()
            if (chunk.isNotEmpty()) {
                sentences.add(chunk)
            }
            currentIndex = endIndex
        }
    }

    /**
     * 获取原始的基础语音ID
     */
    fun getOriginalUtteranceId(): String? = baseUtteranceId

    /**
     * 根据语音ID获取对应文本
     * @param utteranceId 语音ID
     * @return 对应的文本，未找到返回null
     */
    fun getText(utteranceId: String): String? = 
        firstOrNull { it.utteranceId == utteranceId }?.text

    /**
     * 判断是否为最后一项
     * @param utteranceId 语音ID
     * @return 是否为最后一项
     */
    fun isLast(utteranceId: String): Boolean = 
        utteranceId == lastOrNull()?.utteranceId
}