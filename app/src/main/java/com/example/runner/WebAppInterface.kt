package com.example.runner

import android.app.Activity
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.example.runner.bus.BusWebInterface
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
class WebAppInterface(
    private val context: Context,
    private val webView: WebView
) {

    companion object {
        private const val TAG = "WebAppInterface"
    }

    // 跑步数据存储
    private val runDataList = MutableStateFlow<List<RunData>>(emptyList())
    private val keepDataReader = KeepDataReader(context)
    private val keepApiClient = KeepApiClient(context)
    private var syncJob: Job? = null

    // 公交查询接口
    private val busWebInterface = BusWebInterface(context, webView)

    /**
     * 查询公交线路（按线路名称）
     * @param lineName 公交线路名称（如：140 路）
     * @param callback JS 回调函数名
     */
    @JavascriptInterface
    fun searchBusLine(
        lineName: String,
        callback: String
    ) {
        Log.d(TAG, "searchBusLine: lineName=$lineName, callback=$callback")
        busWebInterface.searchBusLine(lineName, callback)
    }

    /**
     * 调用 JavaScript 回调函数
     */
    private fun callJsCallback(callbackName: String, data: String) {
        if (context is Activity) {
            context.runOnUiThread {
                webView.evaluateJavascript("javascript:$callbackName($data)") {
                    Log.d(TAG, "JS 回调结果：$it")
                }
            }
        }
    }

    /**
     * 查询公交路线
     * @param fromLat 起点纬度
     * @param fromLon 起点经度
     * @param toLat 终点纬度
     * @param toLon 终点经度
     * @param mode 查询模式 (0-速度优先，1-距离优先，2-最少换乘，3-最少步行)
     * @param callback JS 回调函数名
     */
    @JavascriptInterface
    fun searchBusRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        mode: Int,
        callback: String
    ) {
        Log.d(TAG, "searchBusRoute: from=($fromLat,$fromLon), to=($toLat,$toLon), mode=$mode, callback=$callback")
        busWebInterface.searchBusRoute(fromLat, fromLon, toLat, toLon, mode, callback)
    }

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
                Log.w(TAG, "无法获取 Token，尝试从数据库读取")
                // Token 获取失败，尝试直接从数据库读取
                val runData = keepDataReader.readRunRecords()
                if (runData.isNotEmpty()) {
                    runDataList.value = runData
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "从数据库读取成功！共 ${runData.size} 条记录", Toast.LENGTH_LONG).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "无法获取 Keep 数据", Toast.LENGTH_LONG).show()
                    }
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
                    Log.e(TAG, "API 同步失败：${error.message}，尝试从数据库读取")
                    // API 失败时尝试从数据库读取
                    val runData = keepDataReader.readRunRecords()
                    if (runData.isNotEmpty()) {
                        runDataList.value = runData
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "从数据库读取成功！共 ${runData.size} 条记录", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "同步失败：${error.message}", Toast.LENGTH_LONG).show()
                        }
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
     * 使用 Root 权限读取 MMKV 文件
     */
    private fun getKeepAuthToken(): Pair<String, String>? {
        // 使用 Root 方式读取 Keep 的 MMKV 文件
        return getKeepTokenFromMMKV()
    }

    /**
     * 从 Keep 的 MMKV 文件中解析 Token
     * 使用 Root 命令读取 Keep 应用私有目录下的 MMKV 文件
     */
    private fun getKeepTokenFromMMKV(): Pair<String, String>? {
        val dataReader = KeepDataReader(context)

        // Keep 的 user_info MMKV 文件路径（包含 authtoken 和 userid）
        val userInfoFile = "/data/data/com.gotokeep.keep/files/mmkv/user_info"

        // 使用 strings 命令提取可读字符串
        val stringsResult = dataReader.executeRootCommand("strings $userInfoFile")
        if (stringsResult == null || stringsResult.isEmpty()) {
            Log.w(TAG, "user_info 文件读取失败")
            return null
        }

        Log.d(TAG, "user_info 文件大小：${stringsResult.length} 字节")

        // 解析 strings 输出
        return parseUserInfoOutput(stringsResult)
    }

    /**
     * 解析 user_info MMKV 文件输出获取 Token
     * MMKV 格式：key 后跟 value，每行一个
     * 示例：
     * authtoken
     * eyJ0eXAiOiJKV1QiLCJhbG...
     * userid
     * 5777b164d04ce428051adacf
     */
    private fun parseUserInfoOutput(output: String): Pair<String, String>? {
        var token: String? = null
        var userId: String? = null

        val lines = output.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        for (i in lines.indices) {
            val line = lines[i]

            // 查找 authtoken 的 value（下一行）
            if (line == "authtoken" && i + 1 < lines.size) {
                val nextLine = lines[i + 1]
                // JWT Token 以 eyJ 开头
                if (nextLine.startsWith("eyJ")) {
                    token = nextLine
                    Log.d(TAG, "找到 Token: ${token.take(20)}...")
                }
            }

            // 查找 userid 的 value（下一行）
            if (line == "userid" && i + 1 < lines.size) {
                val nextLine = lines[i + 1]
                // UID 是 16 进制字符串
                if (nextLine.matches(Regex("[a-fA-F0-9]+"))) {
                    userId = nextLine
                    Log.d(TAG, "找到 UID: $userId")
                }
            }

            if (token != null && userId != null) {
                break
            }
        }

        return if (token != null && userId != null) {
            Pair(token, userId)
        } else {
            Log.w(TAG, "未能完整解析 Token 和 UID. token=${token != null}, userId=${userId != null}")
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
