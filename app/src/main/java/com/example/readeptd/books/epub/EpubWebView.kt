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
import org.json.JSONObject
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
    
    /**
     * 加载 EPUB 文件
     * @param epubFilePath EPUB 文件的绝对路径
     */
    fun loadEpub(epubFilePath: String) {
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
        
        executeJs("window.EpubReader.init('$epubFilePath', $cfiParam)") { result ->
            Log.d(TAG, "JavaScript 执行结果: $result")
        }
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
            loadEpub(epubFilePath)
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

/**
 * EPUB 页面信息数据类
 * 对应 epub.js relocated 事件返回的完整位置信息
 */
data class EpubLocation(
    val start: Position,
    val end: Position,
    val rawJson: String = ""
) {
    data class Position(
        val index: Int,
        val href: String,
        val cfi: String,
        val displayed: Displayed,
        val location: Int,
        val percentage: Float
    ) {
        companion object {
            /**
             * 从 JSON 对象解析 Position
             */
            fun fromJson(positionJson: JSONObject?): Position {
                if (positionJson == null) {
                    return default()
                }
                
                val displayedJson = positionJson.optJSONObject("displayed")
                val displayed = Displayed.fromJson(displayedJson)
                
                return Position(
                    index = positionJson.optInt("index", 0),
                    href = positionJson.optString("href", ""),
                    cfi = positionJson.optString("cfi", ""),
                    displayed = displayed,
                    location = positionJson.optInt("location", 0),
                    percentage = positionJson.optDouble("percentage", 0.0).toFloat()
                )
            }
            
            /**
             * 创建默认的 Position 对象
             */
            fun default(): Position {
                return Position(
                    index = 0,
                    href = "",
                    cfi = "",
                    displayed = Displayed.default(),
                    location = 0,
                    percentage = 0f
                )
            }
        }
    }
    
    data class Displayed(
        val page: Int,
        val total: Int
    ) {
        companion object {
            /**
             * 从 JSON 对象解析 Displayed
             */
            fun fromJson(displayedJson: JSONObject?): Displayed {
                if (displayedJson == null) {
                    return default()
                }
                
                return Displayed(
                    page = displayedJson.optInt("page", 1),
                    total = displayedJson.optInt("total", 1)
                )
            }
            
            /**
             * 创建默认的 Displayed 对象
             */
            fun default(): Displayed {
                return Displayed(page = 0, total = 0)
            }
        }
    }
    
    companion object {
        /**
         * 从 JSON 字符串解析 EpubLocation
         */
        fun fromJson(jsonString: String): EpubLocation {
            val json = JSONObject(jsonString)
            
            val startJson = json.optJSONObject("start")
            val endJson = json.optJSONObject("end")
            
            val startPosition = Position.fromJson(startJson)
            val endPosition = Position.fromJson(endJson)
            
            return EpubLocation(
                start = startPosition,
                end = endPosition,
                rawJson = jsonString
            )
        }
        
        /**
         * 创建默认的 EpubLocation 对象
         */
        fun default(): EpubLocation {
            val defaultPosition = Position.default()
            return EpubLocation(
                start = defaultPosition,
                end = defaultPosition
            )
        }
    }
}

data class EpubClickInfo(
    val x: Float,
    val y: Float,
    val href: String
)

/**
 * EPUB 搜索结果数据类
 * 对应 epub.js search 返回的匹配项信息
 */
data class EpubSearchResult(
    val href: String = "",                    // 章节文件路径
    val sectionIndex: Int = -1,               // 章节索引（在 spine 中的位置）
    val cfi: String = "",                     // CFI 定位符
    val excerpt: String = "",                 // 摘要文本
    val chapterTitle: String = "",            // 章节标题（来自目录）
    val idref: String = "",                   // 章节 ID 引用
    val matchIndex: Int = -1,                 // 当前匹配在章节中的索引
    val sectionBaseCfi: String = "",          // CFI 基础路径
    val position: Int = -1,                   // 在文本中的位置
    val query: String = ""                    // 搜索关键词
) {
    companion object {
        /**
         * 从 JSON 字符串解析 EpubSearchResult
         */
        fun fromJson(jsonString: String): EpubSearchResult {
            val json = JSONObject(jsonString)
            
            return EpubSearchResult(
                href = json.optString("href", ""),
                sectionIndex = json.optInt("sectionIndex", -1),
                cfi = json.optString("cfi", ""),
                excerpt = json.optString("excerpt", ""),
                chapterTitle = json.optString("chapterTitle", ""),
                idref = json.optString("idref", ""),
                matchIndex = json.optInt("matchIndex", -1),
                sectionBaseCfi = json.optString("sectionBaseCfi", ""),
                position = json.optInt("position", -1),
                query = json.optString("query", "")
            )
        }
    }
}
