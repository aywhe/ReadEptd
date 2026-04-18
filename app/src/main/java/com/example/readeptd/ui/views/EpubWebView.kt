package com.example.readeptd.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 基于 epub.js 的 EPUB 阅读器 WebView
 */
@SuppressLint("SetJavaScriptEnabled")
class EpubWebView(context: Context) : WebView(context) {
    
    private var onPageChangedListener: ((String) -> Unit)? = null
    private var onLoadCompleteListener: ((Int) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    init {
        setupWebView()
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
                Log.d(TAG, "页面加载完成: $url")
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
     * 加载 EPUB 文件
     * @param epubFilePath EPUB 文件的绝对路径
     */
    fun loadEpub(epubFilePath: String) {
        Log.d(TAG, "========== 开始加载 EPUB ==========")
        Log.d(TAG, "EPUB 文件路径: $epubFilePath")
        Log.d(TAG, "文件是否存在: ${java.io.File(epubFilePath).exists()}")
        
        // 等待页面加载完成后初始化
        postDelayed({
            Log.d(TAG, "执行 JavaScript 初始化...")
            val jsCode = "window.EpubReader.init('$epubFilePath');"
            evaluateJavascript(jsCode) { result ->
                Log.d(TAG, "JavaScript 执行结果: $result")
            }
        }, 1000) // 增加等待时间到 1 秒，确保 HTML 完全加载
    }
    
    /**
     * 跳转到指定位置
     * @param cfi CFIs 位置标识符
     */
    fun goToLocation(cfi: String) {
        val jsCode = "window.EpubReader.goToLocation('$cfi');"
        evaluateJavascript(jsCode, null)
    }
    
    /**
     * 上一页
     */
    fun prevPage() {
        evaluateJavascript("window.EpubReader.prevPage();", null)
    }
    
    /**
     * 下一页
     */
    fun nextPage() {
        evaluateJavascript("window.EpubReader.nextPage();", null)
    }
    
    /**
     * 跳转到百分比位置
     * @param percentage 百分比 (0.0 - 1.0)
     */
    fun goToPercentage(percentage: Double) {
        val jsCode = "window.EpubReader.goToPercentage($percentage);"
        evaluateJavascript(jsCode, null)
    }
    
    /**
     * 设置页面变化监听器
     */
    fun setOnPageChangedListener(listener: (String) -> Unit) {
        onPageChangedListener = listener
    }
    
    /**
     * 设置加载完成监听器
     */
    fun setOnLoadCompleteListener(listener: (Int) -> Unit) {
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
            Log.d(TAG, "页面变化: $locationJson")
            onPageChangedListener?.invoke(locationJson)
        }
        
        @JavascriptInterface
        fun onLoadComplete(totalPages: Int) {
            Log.d(TAG, "加载完成，总页数: $totalPages")
            onLoadCompleteListener?.invoke(totalPages)
        }
        
        @JavascriptInterface
        fun onError(message: String) {
            Log.e(TAG, "错误: $message")
            onErrorListener?.invoke(message)
        }
    }
    
    companion object {
        private const val TAG = "EpubWebView"
    }
}
