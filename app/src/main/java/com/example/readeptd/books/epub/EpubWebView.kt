package com.example.readeptd.books.epub

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 基于 epub.js 的 EPUB 阅读器 WebView
 */
@SuppressLint("SetJavaScriptEnabled")
class EpubWebView(val epubFilePath: String, context: Context) : WebView(context) {
    
    private var onPageChangedListener: ((EpubLocation) -> Unit)? = null
    private var onLoadCompleteListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var startCfi: String? = null
    private var currentTheme: EpubTheme = EpubTheme.Light
    private var currentFlowMode: EpubFlowMode = EpubFlowMode.Paginated

    // ✅ 添加翻页完成的临时回调
    private var pageActionPendingCallback: (() -> Unit)? = null
    private var locationRetrievedCallback: ((EpubLocation) -> Unit)? = null
    private var currentPageTextCallback: ((String) -> Unit)? = null
    private var onSearchingResultCallback: ((EpubSearchResult?) -> Unit)? = null
    private var onSearchCompletedCallback: (() -> Unit)? = null

    private var onDoubleClickListener: (() -> Unit)? = null
    // ✅ 协程作用域，绑定到主线程
    private val scope: CoroutineScope = MainScope()
    
    init {
        setupWebView()
    }

    override fun destroy() {
        Log.d(TAG, "EpubWebView 销毁")
        cleanUpNative()
        
        // ✅ 取消所有协程，避免内存泄漏
        scope.cancel()
        
        // ✅ 清理所有回调引用
        onPageChangedListener = null
        onLoadCompleteListener = null
        onErrorListener = null
        onDoubleClickListener = null
        pageActionPendingCallback = null
        locationRetrievedCallback = null
        currentPageTextCallback = null
        onSearchingResultCallback = null
        onSearchCompletedCallback = null
        
        super.destroy()
    }
    
    private fun setupWebView() {
        // 启用 JavaScript
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        
        // ✅ 关键修复：设置 WebView 背景色为透明，避免与 HTML 背景色冲突导致闪烁
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setBackgroundResource(0)
        
        // 关键：禁用滚动条，让 epub.js 处理滚动
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        
        // 允许从 file URL 加载内容（关键配置）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowUniversalAccessFromFileURLs = true
            settings.allowFileAccessFromFileURLs = true
        }
        
        // 混合内容模式（如果需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        // 添加 JavaScript 接口
        addJavascriptInterface(AndroidBridge(), "Android")
        
        // 设置 WebViewClient
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "文件加载完成: $url")
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView 错误: $errorCode - $description")
            }
        }
        
        // 设置 WebChromeClient（某些情况下需要）
        webChromeClient = WebChromeClient()
        
        // 启用调试模式（开发时使用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setWebContentsDebuggingEnabled(true)
        }
        
        // 加载 HTML 文件
        loadUrl("file:///android_asset/epub_reader.html")
    }
    
    /**
     * 设置起始位置 CFI（用于恢复上次阅读进度）
     * 必须在 HTML 加载完成前调用
     * @param cfi 起始位置 CFI
     */
    fun setStartCfi(cfi: String?) {
        this.startCfi = cfi
        Log.d(TAG, "设置起始位置 CFI: ${cfi ?: "(无)"}")
    }

    private fun setLastReadingCfi(cfi: String?) {
        Log.d(TAG, "设置最后阅读位置 CFI: ${cfi ?: "(无)"}")
        executeJs("window.EpubReader.setLastReadingCfi('$cfi')") { result ->
            Log.d(TAG, "JavaScript 执行结果: $result")
        }
    }
    
    /**
     * 加载 EPUB 文件
     * @param epubFilePath EPUB 文件的绝对路径
     */
    private fun initEpubWebSite(epubFilePath: String) {
        Log.d(TAG, "========== 开始加载 EPUB ==========")
        Log.d(TAG, "EPUB 文件路径: $epubFilePath")
        Log.d(TAG, "起始位置 CFI: ${startCfi ?: "(无，将显示首页)"}")
        Log.d(TAG, "文件是否存在: ${File(epubFilePath).exists()}")

        Log.d(TAG, "执行 JavaScript 初始化...")
        
        // 构建 JavaScript 调用，安全地处理 CFI 参数
        val cfiParam = if (startCfi != null && startCfi!!.isNotBlank()) {
            "'$startCfi'"
        } else {
            "null"
        }
        
        executeJs("window.EpubReader.init('$epubFilePath')") { result ->
            Log.d(TAG, "JavaScript 执行结果: $result")
        }
    }

    fun startEpubWebsite(){
        setLastReadingCfi(startCfi)
        updateFlowMode(currentFlowMode)
        setHtmlTheme(currentTheme) // 设置当前主题
        initEpubWebSite(epubFilePath)
    }

    fun setFlowMode(epubFlowMode: EpubFlowMode){
        currentFlowMode = epubFlowMode
        Log.d(TAG, "设置流式模式: $epubFlowMode")
    }

    private fun updateFlowMode(epubFlowMode: EpubFlowMode) {
        currentFlowMode = epubFlowMode
        val flowMode = when (epubFlowMode) {
            EpubFlowMode.Paginated -> "paginated"
            EpubFlowMode.Scrolled -> "scrolled"
        }
        
        // ✅ 构建 JSON 配置对象
        val configJson = """{"flowMode":"$flowMode"}"""
        
        Log.d(TAG, "执行 JavaScript 设置流式模式: $flowMode")
        executeJs("window.EpubReader.updateConfig('$configJson')")
    }

    /**
     * 设置主题
     * @param epubTheme 主题名称
     */
    fun setTheme(epubTheme: EpubTheme) {
        currentTheme = epubTheme
        Log.d(TAG, "设置主题: $epubTheme")
    }

    private fun setHtmlTheme(epubTheme: EpubTheme) {
        currentTheme = epubTheme
        val theme = when (epubTheme) {
            EpubTheme.Night -> "dark"
            EpubTheme.Light -> "light"
            EpubTheme.EyeCare -> "eye-care"
        }
        Log.d(TAG, "执行 JavaScript 设置主题...")
        executeJs("window.EpubReader.setTheme('$theme')")
    }
    
    /**
     * 跳转到指定位置
     * @param cfi CFIs 位置标识符
     */
    fun goToLocation(cfi: String) {
        Log.d(TAG, "执行 JavaScript 跳转...")
        executeJs("window.EpubReader.goToLocation('$cfi')")
    }
    
    /**
     * 下一页
     */
    fun nextPage(callback: (() -> Unit)? = null) {
        Log.d(TAG, "执行 JavaScript 下一页...")
        
        // ✅ 如果有回调，先保存起来，等 JS 通知完成时再调用
        if (callback != null) {
            pageActionPendingCallback = callback
        }
        
        executeJs("window.EpubReader.nextPage()")
    }
    
    /**
     * 上一页
     */
    fun prevPage(callback: (() -> Unit)? = null) {
        Log.d(TAG, "执行 JavaScript 上一页...")
        
        if (callback != null) {
            pageActionPendingCallback = callback
        }
        
        executeJs("window.EpubReader.prevPage()")
    }
    
    /**
     * 跳转到百分比位置
     * @param percentage 百分比 (0.0 - 1.0)
     */
    fun goToPercentage(percentage: Double) {
        Log.d(TAG, "跳转到百分比位置: $percentage")
        executeJs("window.EpubReader.goToPercentage($percentage)")
    }

    /**
     * 清理 WebView 资源
     */
    private fun cleanUpNative() {
        Log.d(TAG, "清理 WebView 资源")
        executeJs("window.EpubReader.cleanUp()")
    }

    /**
     * 获取当前页文本（异步）
     * @param callback 回调函数，接收提取的文本
     */
    fun getCurrentPageText(callback: (String) -> Unit) {
        Log.d(TAG, "执行 JavaScript 获取当前页文本...")
        currentPageTextCallback = callback
        executeJs("window.EpubReader.getCurrentPageText()")
    }

    /**
     * 获取当前位置（异步回调方式）
     * @param callback 回调函数，接收位置信息
     */
    fun getCurrentLocation(callback: (EpubLocation) -> Unit) {
        Log.d(TAG, "执行 JavaScript 获取当前位置...")
        locationRetrievedCallback = callback
        executeJs("window.EpubReader.getCurrentLocation()")
    }

    fun toggleNavPanel(){
        Log.d(TAG, "执行 JavaScript 切换导航面板...")
        executeJs("window.EpubReader.toggleNavPanel()")
    }

    fun search(keyword: String,
               resultCallback: (EpubSearchResult?) -> Unit,
               completedCallback: () -> Unit) {
        Log.d(TAG, "执行 JavaScript 搜索文本..")
        onSearchingResultCallback = resultCallback
        onSearchCompletedCallback = completedCallback
        // ✅ 给关键词加上引号，避免 JS 将其当作变量名
        executeJs("window.EpubReader.search('$keyword')")
    }

    fun highlight(cfi: String, isRemove: Boolean){
        executeJs("window.EpubReader.highlight('$cfi', $isRemove)")
    }

    /**
     * ✅ 使用协程执行 JavaScript（自动切换到主线程）
     * @param script JavaScript 代码
     * @param callback 可选的回调，接收执行结果
     */
    private fun executeJs(script: String, callback: ((String?) -> Unit)? = null) {
        val jsCode = if (script.endsWith(";")) script else "$script;"
        
        // ✅ 启动协程，自动切换到主线程
        scope.launch(Dispatchers.Main) {
            Log.d(TAG, "执行 JavaScript: $jsCode")
            evaluateJavascript(jsCode, callback)
        }
    }

    /**
     * 设置页面变化监听器
     */
    fun setOnPageChangedListener(listener: (EpubLocation) -> Unit) {
        onPageChangedListener = listener
    }
    
    /**
     * 设置加载完成监听器
     */
    fun setOnLoadCompleteListener(listener: () -> Unit) {
        onLoadCompleteListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    /**
     * 设置双击监听器
     */
    fun setOnDoubleClickListener(listener: () -> Unit) {
        onDoubleClickListener = listener
    }
    
    /**
     * JavaScript 桥接类
     */
    inner class AndroidBridge {
        
        @JavascriptInterface
        fun onPageChanged(locationJson: String) {
            Log.d(TAG, "页面位置变化: $locationJson")
            try {
                val epubLocation = EpubLocation.fromJson(locationJson)
                if(epubLocation.start.percentage > 0) {
                    runOnMain { onPageChangedListener?.invoke(epubLocation) }
                } else {
                    Log.w(TAG, "无效的页面位置信息，跳过回调: $locationJson")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析页面位置信息失败", e)
            }
        }
        
        @JavascriptInterface
        fun onLocationRetrieved(locationJson: String) {
            Log.d(TAG, "获取到位置信息: $locationJson")
            try {
                val epubLocation = EpubLocation.fromJson(locationJson)
                runOnMain {
                    if(epubLocation.start.percentage > 0) {
                        locationRetrievedCallback?.invoke(epubLocation)
                    } else {
                        Log.w(TAG, "无效的位置信息")
                        locationRetrievedCallback?.invoke(EpubLocation.default())
                    }
                    locationRetrievedCallback = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析位置信息失败", e)
                runOnMain {
                    locationRetrievedCallback?.invoke(EpubLocation.default())
                    locationRetrievedCallback = null
                }
            }
        }
        @JavascriptInterface
        fun onSearchingResult(result: String) {
            Log.d(TAG, "搜索结果: $result")
            val searchResult = try {
                EpubSearchResult.fromJson(result)
            } catch (e: Exception) {
                Log.e(TAG, "解析搜索结果失败", e)
                null
            }
            runOnMain { onSearchingResultCallback?.invoke(searchResult) }
        }
        @JavascriptInterface
        fun onSearchCompleted() {
            runOnMain {
                onSearchCompletedCallback?.invoke()
                onSearchingResultCallback = null
                onSearchCompletedCallback = null
            }
        }
        
        @JavascriptInterface
        fun onLoadComplete() {
            Log.d(TAG, "加载完成")
            runOnMain { onLoadCompleteListener?.invoke() }
        }

        @JavascriptInterface
        fun onError(message: String) {
            Log.e(TAG, "错误: $message")
            runOnMain { onErrorListener?.invoke(message) }
        }

        @JavascriptInterface
        fun onPageActionComplete(action: String) {
            Log.d(TAG, "页面操作完成: $action")
            runOnMain {
                pageActionPendingCallback?.invoke()
                pageActionPendingCallback = null
            }
        }

        @JavascriptInterface
        fun onHtmlReady() {
            Log.d(TAG, "HTML 准备就绪，开始加载 EPUB 文件")
            startEpubWebsite()
        }

        @JavascriptInterface
        fun onDoubleClick(){
            Log.d(TAG, "检测到双击事件")
            runOnMain { onDoubleClickListener?.invoke() }
        }

        @JavascriptInterface
        fun onPageTextRetrieved(text: String) {
            Log.d(TAG, "获取到页面文本，长度: ${text.length}")
            runOnMain {
                currentPageTextCallback?.invoke(text)
                currentPageTextCallback = null
            }
        }
        
        /**
         * ✅ 在主线程执行代码块
         */
        private fun runOnMain(block: () -> Unit) {
            scope.launch(Dispatchers.Main) {
                block()
            }
        }
    }
    
    companion object {
        private const val TAG = "EpubWebView"
    }
}

