package com.example.runner

import android.content.Context
import android.util.Log
import com.example.runner.data.RunData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keep 数据同步服务
 * 后续实现 Keep 同步功能时使用
 *
 * TODO: 需要实现以下功能：
 * 1. Keep 账号登录（获取 token）
 * 2. 跑步记录列表获取
 * 3. 跑步详情获取（包含轨迹）
 * 4. 本地数据缓存（Room 数据库）
 */
class KeepSyncService(private val context: Context) {

    companion object {
        private const val TAG = "KeepSyncService"
    }

    /**
     * 同步跑步数据
     * @return 同步结果
     */
    suspend fun syncRunData(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // TODO: 实现 Keep API 调用
            // 1. 检查登录状态
            // 2. 获取跑步记录列表
            // 3. 获取跑步详情
            // 4. 保存到本地数据库
            Log.d(TAG, "同步 Keep 数据 - 功能待实现")
            SyncResult.NotImplemented
        } catch (e: Exception) {
            Log.e(TAG, "同步 Keep 数据失败", e)
            SyncResult.Error(e.message ?: "未知错误")
        }
    }

    /**
     * 获取本地缓存的跑步数据
     */
    suspend fun getLocalRunData(): List<RunData> {
        // TODO: 从 Room 数据库读取
        return emptyList()
    }

    /**
     * 检查是否已登录 Keep
     */
    fun isLoggedIn(): Boolean {
        // TODO: 检查本地存储的 token
        return false
    }

    /**
     * 退出登录
     */
    fun logout() {
        // TODO: 清除本地 token
    }
}

sealed class SyncResult {
    object NotImplemented : SyncResult()
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
    data class NeedLogin(val redirectUrl: String) : SyncResult()
}
