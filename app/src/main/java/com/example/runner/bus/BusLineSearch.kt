package com.example.runner.bus

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 公交线路查询服务
 * 使用高德地图 Web API 进行公交线路查询
 */
class BusLineSearch(private val context: Context) {

    companion object {
        private const val TAG = "BusLineSearch"

        // 高德地图 Web 服务 Key
        private const val AMAP_KEY = "ed65976db5bb28d0cdb9db7669a9515b"

        // 公交线路查询 API 端点
        private const val BUS_LINE_API = "https://restapi.amap.com/v3/busline"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 查询公交线路信息
     * @param lineName 公交线路名称（如：140 路）
     * @param city 城市代码（如：福州为 350100）
     * @return JSON 格式的线路结果
     */
    suspend fun searchBusLine(
        lineName: String,
        city: String = "350100"
    ): Result<String> {
        return try {
            // 构建 API 请求 URL
            val url = StringBuilder(BUS_LINE_API)
            url.append("?key=$AMAP_KEY")
            url.append("&city=$city")
            url.append("&name=$lineName")
            url.append("&extensions=all")  // 返回详细信息，包括所有站点

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
