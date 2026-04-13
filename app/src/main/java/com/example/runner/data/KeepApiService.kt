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
    @GET("account/1/account")
    suspend fun getAccount(@Header("Authorization") token: String): ResponseBody

    /**
     * 获取运动数据列表
     * @param userId 用户 ID
     * @param start 开始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @param limit 限制数量
     */
    @GET("fitnessdata/v1/user/{userId}/outdoormove")
    suspend fun getOutdoorMove(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Query("start") start: Long,
        @Query("end") end: Long,
        @Query("limit") limit: Int = 100
    ): ResponseBody

    /**
     * 获取跑步详情（GPX 数据）
     */
    @GET("fitnessdata/v1/user/{userId}/outdoormove/{moveId}/detail")
    suspend fun getMoveDetail(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
        @Path("moveId") moveId: String
    ): ResponseBody
}

/**
 * 请求体包装类
 */
data class KeepApiRequest(
    val app_version: String = "8.2.11",
    val client_id: String = "ios",
    val os_type: String = "android"
)
