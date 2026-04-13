package com.example.runner

import android.content.Context
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
    private val keepApiClient = KeepApiClient(context)
    private var syncJob: Job? = null

    /**
     * 从 Keep App 中提取 Token 并同步数据
     * 使用 API 方式获取云端数据
     */
    @JavascriptInterface
    fun syncKeepData() {
        Log.d(TAG, "=== syncKeepData 被调用 ===")
        Log.d(TAG, "开始同步 Keep 数据（API 方式）")

        syncJob = CoroutineScope(Dispatchers.Main).launch {
            // 首先尝试从 Keep 共享配置中获取 Token
            val tokenInfo = getKeepAuthToken()

            if (tokenInfo == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无法获取 Keep 认证信息，请确保 Keep 已登录", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            Log.d(TAG, "获取到 Token: ${tokenInfo.first.take(20)}..., UID: ${tokenInfo.second}")
            keepApiClient.setAuthToken(tokenInfo.first, tokenInfo.second)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在从云端同步 Keep 数据...", Toast.LENGTH_SHORT).show()
            }

            try {
                // 获取最近 90 天的运动数据
                val result = keepApiClient.getOutdoorMoves(days = 90)

                result.onSuccess { moves ->
                    Log.d(TAG, "获取到 ${moves.size} 条运动记录")

                    if (moves.isNotEmpty()) {
                        // 过滤跑步数据（type 0 或 1）
                        val runData = keepApiClient.convertToRunData(moves)
                        runDataList.value = runData

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "同步成功！共 ${runData.size} 条记录", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "未找到跑步数据", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                result.onFailure { error ->
                    Log.e(TAG, "同步失败", error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "同步失败：${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步异常", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "同步异常：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 从 Keep 的 Shared Preferences 中获取认证 Token
     */
    private fun getKeepAuthToken(): Pair<String, String>? {
        return try {
            val sharedPrefs = context.createPackageContext("com.gotokeep.keep", Context.CONTEXT_IGNORE_SECURITY)
                .getSharedPreferences("Unicorn.a9e4b7e56e7697705b6e668c0b81a239", Context.MODE_WORLD_READABLE)

            val token = sharedPrefs.getString("AUTH_TOKEN", null)
            val userId = sharedPrefs.getString("YSF_ID_MP/5777b164d04ce428051adacf", null)
                ?: sharedPrefs.getString("YSF_ID_YX", null)

            if (token != null && userId != null) {
                Pair(token, userId)
            } else {
                // 尝试从 MMKV 文件读取
                getKeepTokenFromMMKV()
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从 SharedPrefs 获取 Token", e)
            getKeepTokenFromMMKV()
        }
    }

    /**
     * 从 Keep 的 MMKV 文件中解析 Token
     */
    private fun getKeepTokenFromMMKV(): Pair<String, String>? {
        return try {
            val mmkvFile = context.filesDir.parentFile?.let {
                java.io.File(it, "mmkv/5777b164d04ce428051adacf")
            } ?: java.io.File(context.filesDir, "mmkv/5777b164d04ce428051adacf")

            if (!mmkvFile.exists()) {
                // 尝试从系统目录读取
                val keepContext = context.createPackageContext("com.gotokeep.keep", Context.CONTEXT_IGNORE_SECURITY)
                val mmkvDir = java.io.File(keepContext.filesDir, "mmkv")
                val files = mmkvDir.listFiles()?.filter { it.name.startsWith("5777") }
                if (!files.isNullOrEmpty()) {
                    parseMMKVFile(files.first())
                } else {
                    null
                }
            } else {
                parseMMKVFile(mmkvFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从 MMKV 获取 Token", e)
            null
        }
    }

    /**
     * 解析 MMKV 文件获取 Token
     */
    private fun parseMMKVFile(file: java.io.File): Pair<String, String>? {
        val content = file.readText()
        // MMKV 格式：key\0value\0key\0value...
        var token: String? = null
        var userId: String? = null

        // 查找 authtoken
        val authPattern = Regex("authtoken\\x00([eyJ0-9_.]+)")
        val userPattern = Regex("userid\\x00([a-f0-9]+)")

        token = authPattern.find(content)?.groupValues?.get(1)
        userId = userPattern.find(content)?.groupValues?.get(1)

        return if (token != null && userId != null) {
            Pair(token, userId)
        } else {
            null
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
