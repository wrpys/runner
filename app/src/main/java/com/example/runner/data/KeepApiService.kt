package com.example.runner.data

/**
 * Keep API 服务接口定义
 * 后续实现 Keep 同步功能时使用
 */
interface KeepApiService {

    /**
     * 登录 Keep 账号
     * @param username 用户名/手机号/邮箱
     * @param password 密码
     * @return 登录响应，包含 token
     */
    // suspend fun login(username: String, password: String): LoginResponse

    /**
     * 获取跑步记录列表
     * @param page 页码
     * @param pageSize 每页数量
     * @return 跑步记录列表
     */
    // suspend fun getRunRecords(page: Int, pageSize: Int): RunRecordsResponse

    /**
     * 获取单次跑步详情
     * @param runId 跑步 ID
     * @return 跑步详情
     */
    // suspend fun getRunDetail(runId: String): RunDetailResponse
}

/**
 * 登录响应
 */
data class LoginResponse(
    val token: String,
    val userId: String,
    val username: String
)

/**
 * 跑步记录列表响应
 */
data class RunRecordsResponse(
    val total: Int,
    val records: List<RunRecord>
)

data class RunRecord(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val distance: Double,
    val duration: Long,
    val calories: Int
)
