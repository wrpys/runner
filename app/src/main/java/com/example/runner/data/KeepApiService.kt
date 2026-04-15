package com.example.runner.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Keep API 服务接口
 * Base URL: https://api.gotokeep.com
 */
interface KeepApiService {

    /**
     * 获取账号信息（验证 Token）
     */
    @GET("user/v1/{userId}/profile")
    suspend fun getAccount(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): ResponseBody

    /**
     * 获取运动数据列表 - 尝试 fitness API 路径
     * @param userId 用户 ID
     * @param start 开始时间戳（秒）
     * @param end 结束时间戳（秒）
     * @param limit 限制数量
     */
    @GET("fitness/v1/{userId}/moves")
    suspend fun getOutdoorMove(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Query("start") start: Long,
        @Query("end") end: Long,
        @Query("limit") limit: Int = 100
    ): ResponseBody

    /**
     * 获取运动详情
     */
    @GET("fitness/v1/{userId}/moves/{moveId}")
    suspend fun getMoveDetail(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Path("moveId") moveId: String
    ): ResponseBody

    /**
     * 备选方案：使用 POST 方式获取运动数据
     */
    @POST("data/v1/moves/query")
    suspend fun queryMoves(
        @Header("Authorization") token: String,
        @Body request: MovesQueryRequest
    ): ResponseBody
}

/**
 * 运动数据查询请求
 */
data class MovesQueryRequest(
    val userId: String,
    val startDate: Long,
    val endDate: Long,
    val limit: Int = 100,
    val types: List<Int> = listOf(0, 1) // 0=跑步，1=户外跑步
)
