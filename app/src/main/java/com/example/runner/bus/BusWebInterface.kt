package com.example.runner.bus

import android.app.Activity
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 公交查询 Web 接口
 * 提供 JavaScript 可调用的原生公交查询方法
 */
class BusWebInterface(
    private val context: Context,
    private val webView: WebView
) {

    companion object {
        private const val TAG = "BusWebInterface"
    }

    private val busRouteSearch = BusRouteSearch(context)
    private val busLineSearch = BusLineSearch(context)
    private var searchJob: Job? = null

    /**
     * 查询公交路线
     * @param fromLat 起点纬度
     * @param fromLon 起点经度
     * @param toLat 终点纬度
     * @param toLon 终点经度
     * @param mode 查询模式 (未使用)
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
        Log.d(TAG, "查询公交路线：from=($fromLat,$fromLon), to=($toLat,$toLon)")

        searchJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(context, "正在查询公交路线...", Toast.LENGTH_SHORT).show()

                val result = busRouteSearch.searchBusRoute(
                    fromLat = fromLat,
                    fromLon = fromLon,
                    toLat = toLat,
                    toLon = toLon
                )

                result.onSuccess { responseJson ->
                    try {
                        // 解析高德 Web API 响应
                        val jsonResponse = JSONObject(responseJson)
                        val status = jsonResponse.optString("status", "")
                        val info = jsonResponse.optString("info", "")

                        if (status == "1" && info == "OK") {
                            // 高德公交规划 API 返回的数据结构是 route.transits
                            val routeData = jsonResponse.getJSONObject("route")
                            val routes = routeData.optJSONArray("transits") ?: JSONArray()

                            val response = JSONObject().apply {
                                put("success", true)
                                put("routes", routes)
                                put("count", routes.length())
                            }

                            Log.d(TAG, "查询成功：${routes.length()} 条路线")

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "查询成功，找到 ${routes.length()} 条路线", Toast.LENGTH_SHORT).show()
                                callJsCallback(callback, response.toString())
                            }
                        } else {
                            val error = JSONObject().apply {
                                put("success", false)
                                put("error", "API 错误：$info")
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "查询失败：$info", Toast.LENGTH_LONG).show()
                                callJsCallback(callback, error.toString())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败", e)
                        val error = JSONObject().apply {
                            put("success", false)
                            put("error", "解析失败：${e.message}")
                        }
                        withContext(Dispatchers.Main) {
                            callJsCallback(callback, error.toString())
                        }
                    }
                }

                result.onFailure { error ->
                    val response = JSONObject().apply {
                        put("success", false)
                        put("error", error.message ?: "未知错误")
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "查询失败：${error.message}", Toast.LENGTH_LONG).show()
                        callJsCallback(callback, response.toString())
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "查询异常", e)
                val response = JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "未知异常")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "查询异常：${e.message}", Toast.LENGTH_LONG).show()
                    callJsCallback(callback, response.toString())
                }
            }
        }
    }

    /**
     * 查询公交线路信息
     * @param lineName 公交线路名称（如：140 路）
     * @param callback JS 回调函数名
     */
    @JavascriptInterface
    fun searchBusLine(
        lineName: String,
        callback: String
    ) {
        Log.d(TAG, "查询公交线路：$lineName")

        searchJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(context, "正在查询 $lineName...", Toast.LENGTH_SHORT).show()

                val result = busLineSearch.searchBusLine(lineName)

                result.onSuccess { responseJson ->
                    try {
                        val jsonResponse = JSONObject(responseJson)
                        val status = jsonResponse.optString("status", "")
                        val info = jsonResponse.optString("info", "")

                        if (status == "1" && info == "OK") {
                            // 高德公交线 API 返回的数据是 buslines 数组
                            val busLines = jsonResponse.optJSONArray("buslines") ?: JSONArray()

                            if (busLines.length() > 0) {
                                // 取第一条匹配的线路
                                val busLine = busLines.getJSONObject(0)

                                val response = JSONObject().apply {
                                    put("success", true)
                                    put("lineName", lineName)
                                    put("busLine", busLine)
                                }

                                val stops = busLine.optJSONArray("stops") ?: JSONArray()
                                Log.d(TAG, "查询成功：${stops.length()} 个站点")

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "查询成功，共 ${stops.length()} 个站点", Toast.LENGTH_SHORT).show()
                                    callJsCallback(callback, response.toString())
                                }
                            } else {
                                val error = JSONObject().apply {
                                    put("success", false)
                                    put("error", "未找到线路：$lineName")
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "未找到线路：$lineName", Toast.LENGTH_LONG).show()
                                    callJsCallback(callback, error.toString())
                                }
                            }
                        } else {
                            val error = JSONObject().apply {
                                put("success", false)
                                put("error", "API 错误：$info")
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "查询失败：$info", Toast.LENGTH_LONG).show()
                                callJsCallback(callback, error.toString())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败", e)
                        val error = JSONObject().apply {
                            put("success", false)
                            put("error", "解析失败：${e.message}")
                        }
                        withContext(Dispatchers.Main) {
                            callJsCallback(callback, error.toString())
                        }
                    }
                }

                result.onFailure { error ->
                    val response = JSONObject().apply {
                        put("success", false)
                        put("error", error.message ?: "未知错误")
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "查询失败：${error.message}", Toast.LENGTH_LONG).show()
                        callJsCallback(callback, response.toString())
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "查询异常", e)
                val response = JSONObject().apply {
                    put("success", false)
                    put("error", e.message ?: "未知异常")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "查询异常：${e.message}", Toast.LENGTH_LONG).show()
                    callJsCallback(callback, response.toString())
                }
            }
        }
    }

    /**
     * 调用 JavaScript 回调函数
     */
    private fun callJsCallback(callbackName: String, data: String) {
        if (context is Activity) {
            context.runOnUiThread {
                // 需要将 JSON 字符串转义后传递给 JS
                val escapedData = data.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                webView.evaluateJavascript("javascript:$callbackName(\"$escapedData\")") {
                    Log.d(TAG, "JS 回调结果：$it")
                }
            }
        }
    }
}
