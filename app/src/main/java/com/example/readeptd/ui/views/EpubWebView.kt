package com.example.readeptd.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 基于 epub.js 的 EPUB 阅读器 WebView
 */
@SuppressLint("SetJavaScriptEnabled")
class EpubWebView(val epubFilePath: String, context: Context) : WebView(context) {
    
    private var onPageChangedListener: ((EpubLocation) -> Unit)? = null
    private var onLoadCompleteListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var startCfi: String? = null
    
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowUniversalAccessFromFileURLs = true
            settings.allowFileAccessFromFileURLs = true
        }
        
        // 混合内容模式（如果需要）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
        webChromeClient = android.webkit.WebChromeClient()
        
        // 启用调试模式（开发时使用）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
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
        Log.d(TAG, "文件是否存在: ${java.io.File(epubFilePath).exists()}")

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
     * 上一页
     */
    fun prevPage() {
        Log.d(TAG, "执行 JavaScript 上一页...")
        executeJs("window.EpubReader.prevPage()")
    }
    
    /**
     * 下一页
     */
    fun nextPage() {
        Log.d(TAG, "执行 JavaScript 下一页...")
        executeJs("window.EpubReader.nextPage()")
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
     * ✅ 使用协程执行 JavaScript（自动切换到主线程）
     * @param script JavaScript 代码
     * @param callback 可选的回调，接收执行结果
     */
    private fun executeJs(script: String, callback: ((String?) -> Unit)? = null) {
        val jsCode = if (script.endsWith(";")) script else "$script;"
        
        // ✅ 启动协程，自动切换到主线程
        scope.launch(Dispatchers.Main) {
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
     * JavaScript 桥接类
     */
    inner class AndroidBridge {
        
        @JavascriptInterface
        fun onPageChanged(locationJson: String) {
            Log.d(TAG, "页面位置变化: $locationJson")
            try {
                val pageInfo = parseLocationInfo(locationJson)
                if(pageInfo.percentage > 0) {
                    onPageChangedListener?.invoke(pageInfo)
                } else {
                    Log.w(TAG, "无效的页面位置信息，跳过回调: $locationJson")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析页面位置信息失败", e)
            }
        }
        
        @JavascriptInterface
        fun onLoadComplete() {
            Log.d(TAG, "加载完成")
            onLoadCompleteListener?.invoke()
        }

        @JavascriptInterface
        fun onError(message: String) {
            Log.e(TAG, "错误: $message")
            onErrorListener?.invoke(message)
        }

        @JavascriptInterface
        fun onHtmlReady() {
            Log.d(TAG, "HTML 准备就绪，开始加载 EPUB 文件")
            loadEpub(epubFilePath)
        }
    }
    
    /**
     * 解析页面信息 JSON
     */
    private fun parseLocationInfo(jsonString: String): EpubLocation {
        val json = JSONObject(jsonString)
        return EpubLocation(
            cfi = json.getString("cfi"),
            percentage = (json.optDouble("percentage", 0.0)).toFloat(),
            currentPage = json.optInt("currentPage", 1),
            totalPages = json.optInt("totalPages", 1)
        )
    }
    
    companion object {
        private const val TAG = "EpubWebView"
    }
}

/**
 * EPUB 页面信息数据类
 * 对应 epub.js relocated 事件返回的位置信息
 */
data class EpubLocation(
    val cfi: String,              // 起始位置 CFI
    val percentage: Float,    // 起始位置百分比 (0.0 - 1.0)
    val currentPage: Int,           // 当前页码
    val totalPages: Int             // 总页数
)
