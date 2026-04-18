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
        }
        
        // 加载 HTML 文件
        loadUrl("file:///android_asset/epub_reader.html")
    }
    
    /**
     * 加载 EPUB 文件
     * @param epubFilePath EPUB 文件的绝对路径
     */
    fun loadEpub(epubFilePath: String) {
        Log.d(TAG, "加载 EPUB 文件: $epubFilePath")
        
        // 等待页面加载完成后初始化
        postDelayed({
            val jsCode = "window.EpubReader.init('$epubFilePath');"
            evaluateJavascript(jsCode, null)
        }, 500)
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
