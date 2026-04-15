package com.example.runner

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Runner"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var webAppInterface: WebAppInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        // 创建 WebAppInterface（需要传入 WebView 用于 JS 回调）
        webAppInterface = WebAppInterface(this, webView)

        Log.d(TAG, "MainActivity onCreate")
        setupWebView()
        loadUrl()

        // 检查是否有同步 action
        if (intent.action == "com.example.runner.ACTION_SYNC") {
            Log.d(TAG, "收到同步 Action，触发同步")
            webAppInterface.syncKeepData()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: ${intent?.action}")
        if (intent?.action == "com.example.runner.ACTION_SYNC") {
            Log.d(TAG, "收到同步 Action，触发同步")
            webAppInterface.syncKeepData()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // 启用 JavaScript
            javaScriptEnabled = true
            // 支持 DOM Storage
            domStorageEnabled = true
            // 支持 Database Storage (预留)
            databaseEnabled = true
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
            // 支持 Content Zoom
            builtInZoomControls = false
            setSupportZoom(false)
            // 设置 User-Agent
            userAgentString = buildUserAgent()
            // 允许文件访问
            allowFileAccess = true
            allowContentAccess = true
            // 设置文本编码
            defaultTextEncodingName = "UTF-8"
            // 自动播放媒体
            mediaPlaybackRequiresUserGesture = false
        }

        // 设置 WebViewClient 处理页面跳转
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.isVisible = false
            }
        }

        // 设置 WebChromeClient 处理进度条等
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.isVisible = newProgress < 100
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        // 添加 JavaScript 接口
        webView.addJavascriptInterface(webAppInterface, "AndroidApp")
    }

    private fun buildUserAgent(): String {
        val androidVersion = Build.VERSION.RELEASE
        val model = Build.MODEL
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName
        return "Mozilla/5.0 (Linux; Android $androidVersion; $model) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/114.0.5735.130 Mobile Safari/537.36 Runner/$appVersion"
    }

    private fun loadUrl() {
        // 默认加载本地 HTML 或远程 URL
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
