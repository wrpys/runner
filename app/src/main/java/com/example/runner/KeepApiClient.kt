package com.example.runner

import android.content.Context
import android.util.Log
import com.example.runner.data.KeepApiService
import com.example.runner.data.OutdoorMoveResponse
import com.example.runner.data.RunData
import com.example.runner.data.parseOutdoorMoveResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Keep API 客户端
 * 通过 Keep 开放 API 获取运动数据
 */
class KeepApiClient(private val context: Context) {

    companion object {
        private const val TAG = "KeepApiClient"
        private const val BASE_URL = "https://api.gotokeep.com/"
        private const val APP_VERSION = "8.2.11"
    }

    private val apiService: KeepApiService
    private var authToken: String? = null
    private var userId: String? = null

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Keep/$APP_VERSION Android")
                    .header("Content-Type", "application/json")
                    .apply {
                        authToken?.let { header("Authorization", "Bearer $it") }
                    }
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(KeepApiService::class.java)
    }

    /**
     * 设置认证 Token
     * Token 可以从 Keep App 的共享偏好中获取
     */
    fun setAuthToken(token: String, uid: String) {
        authToken = token
        userId = uid
        Log.d(TAG, "设置 Token: ${token.take(20)}..., UID: $uid")
    }

    /**
     * 获取用户的运动数据
     * @param days 获取最近多少天的数据
     */
    suspend fun getOutdoorMoves(days: Int = 30): Result<List<OutdoorMoveResponse>> = withContext(Dispatchers.IO) {
        if (authToken == null || userId == null) {
            return@withContext Result.failure(IllegalStateException("未设置认证 Token"))
        }

        try {
            // 时间戳转换为秒（Keep API 使用秒级时间戳）
            val endTime = System.currentTimeMillis() / 1000
            val startTime = endTime - (days.toLong() * 24 * 60 * 60)

            Log.d(TAG, "获取运动数据：start=$startTime, end=$endTime")

            val response = apiService.getOutdoorMove(
                token = "Bearer $authToken",
                userId = userId!!,
                start = startTime,
                end = endTime,
                limit = 100
            )

            val jsonString = response.string()
            Log.d(TAG, "API 响应：$jsonString")

            // 尝试解析响应
            try {
                val json = JSONObject(jsonString)
                val errorCode = json.optInt("errorCode", -1)

                if (errorCode == 0) {
                    val moves = parseOutdoorMoveResponse(json)
                    Log.d(TAG, "解析到 ${moves.size} 条运动记录")
                    Result.success(moves)
                } else {
                    val errorMsg = json.optString("error", "未知错误")
                    Log.e(TAG, "API 错误：$errorMsg")
                    Result.failure(Exception("API 错误：$errorMsg"))
                }
            } catch (e: Exception) {
                // 尝试另一种响应格式
                Log.w(TAG, "标准格式解析失败，尝试备用解析")
                Result.failure(Exception("响应格式未知：$jsonString"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取运动数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 将 API 数据转换为 RunData 列表
     */
    fun convertToRunData(moves: List<OutdoorMoveResponse>): List<RunData> {
        return moves.filter { it.type == 0 || it.type == 1 } // 0=跑步，1=户外跑步
            .map { move ->
                RunData(
                    id = move.id.hashCode().toLong(),
                    distance = move.distance,
                    duration = move.duration.toLong(),
                    pace = move.avgPace,
                    calories = move.calories.toInt(),
                    createTime = move.startTime,
                    latitude = move.geoPoints?.firstOrNull()?.latitude,
                    longitude = move.geoPoints?.firstOrNull()?.longitude,
                    routePoints = move.geoPoints?.map { point ->
                        com.example.runner.data.RoutePoint(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            altitude = point.altitude,
                            distance = point.distance
                        )
                    }
                )
            }
    }
}
