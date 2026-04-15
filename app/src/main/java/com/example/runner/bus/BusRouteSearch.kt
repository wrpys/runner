package com.example.runner.bus

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 公交路线查询服务
 * 使用高德地图 Web API 进行公交路线规划
 */
class BusRouteSearch(private val context: Context) {

    companion object {
        private const val TAG = "BusRouteSearch"

        // 高德地图 Web 服务 Key
        private const val AMAP_KEY = "ed65976db5bb28d0cdb9db7669a9515b"

        // 公交路径规划 API 端点
        private const val BUS_ROUTE_API = "https://restapi.amap.com/v3/direction/transit/integrated"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 查询公交路线（使用 Web API）
     * @param fromLat 起点纬度
     * @param fromLon 起点经度
     * @param toLat 终点纬度
     * @param toLon 终点经度
     * @param cityCode 城市代码
     * @return JSON 格式的路线结果
     */
    suspend fun searchBusRoute(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        cityCode: String = ""
    ): Result<String> {
        return try {
            // 构建 API 请求 URL
            val origin = "$fromLon,$fromLat"
            val destination = "$toLon,$toLat"
            val url = StringBuilder(BUS_ROUTE_API)
            url.append("?key=$AMAP_KEY")
            url.append("&origin=$origin")
            url.append("&destination=$destination")
            if (cityCode.isNotEmpty()) {
                url.append("&city=$cityCode")
            }

            Log.d(TAG, "请求 URL: $url")
            Log.d(TAG, "当前应用包名：${context.packageName}")

            val request = okhttp3.Request.Builder()
                .url(url.toString())
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "响应：$responseBody")
                Result.success(responseBody ?: "")
            } else {
                Result.failure(Exception("HTTP 错误：${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询异常", e)
            Result.failure(e)
        }
    }
}
