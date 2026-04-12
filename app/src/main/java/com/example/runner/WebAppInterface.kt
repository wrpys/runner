package com.example.runner

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.example.runner.data.RunData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * WebView 与 JavaScript 通信的接口类
 * Vue 应用中可以通过 window.AndroidApp.xxx() 调用原生方法
 */
class WebAppInterface(private val context: Context) {

    companion object {
        private const val TAG = "WebAppInterface"
    }

    // 跑步数据存储
    private val runDataList = MutableStateFlow<List<RunData>>(emptyList())
    private val keepDataReader = KeepDataReader(context)
    private var syncJob: Job? = null

    /**
     * 从 Web 页面接收消息
     */
    @JavascriptInterface
    fun postMessage(message: String) {
        Log.d(TAG, "收到消息：$message")
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "收到消息：$message", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取跑步数据
     * @return JSON 格式的跑步数据
     */
    @JavascriptInterface
    fun getRunData(): String {
        val data = runDataList.value
        if (data.isEmpty()) {
            return """{"status":"empty","message":"暂无数据","data":[]}"""
        }
        return """{"status":"success","data":${data.toJson()}}"""
    }

    /**
     * 同步 Keep 数据（Root 方式）
     * 直接读取 Keep 数据库文件
     */
    @JavascriptInterface
    fun syncKeepData() {
        Log.d(TAG, "=== syncKeepData 被调用 ===")
        Log.d(TAG, "开始同步 Keep 数据")

        syncJob = CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "协程启动，开始检查 Root 权限")

            // 检查 Root 权限
            if (!keepDataReader.isRootAvailable()) {
                Log.e(TAG, "Root 权限检查失败")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Root 权限不可用，请确保手机已 Root 并授权本应用", Toast.LENGTH_LONG).show()
                    // 打开超级用户设置
                    try {
                        val intent = Intent("me.wearthu.superuser")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 尝试打开 Magisk
                        try {
                            val magiskIntent = context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
                            magiskIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            magiskIntent?.let { context.startActivity(it) }
                        } catch (e2: Exception) {
                            Toast.makeText(context, "无法打开超级用户应用", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                return@launch
            }

            Log.d(TAG, "Root 权限检查通过")

            // 检查 Keep 是否安装
            if (!keepDataReader.isKeepInstalled()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "未检测到 Keep 应用，请先安装 Keep", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在读取 Keep 数据...", Toast.LENGTH_SHORT).show()
            }

            // 执行同步
            try {
                val records = keepDataReader.readRunRecords()
                if (records.isNotEmpty()) {
                    runDataList.value = records
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "同步成功！共 ${records.size} 条记录", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "未找到跑步数据", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "同步失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 获取统计信息
     */
    @JavascriptInterface
    fun getStats(): String {
        val data = runDataList.value
        val totalRuns = data.size
        val totalDistance = data.sumOf { r -> r.distance } / 1000.0
        val totalCalories = data.sumOf { r -> r.calories }

        return """{"totalRuns":$totalRuns,"totalDistance":${String.format(Locale.US, "%.2f", totalDistance)},"totalCalories":$totalCalories}"""
    }

    /**
     * 退出应用
     */
    @JavascriptInterface
    fun exitApp() {
        if (context is android.app.Activity) {
            context.finish()
        }
    }
}

// 简单的 JSON 序列化辅助
private fun List<RunData>.toJson(): String {
    return "[" + joinToString(",") { run -> run.toJson() } + "]"
}

private fun RunData.toJson(): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(java.util.Date(createTime))
    return """{"id":$id,"distance":$distance,"duration":$duration,"pace":$pace,"calories":$calories,"createTime":"$dateStr"}"""
}
