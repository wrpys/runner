package com.example.runner.data

/**
 * 跑步数据模型
 * 用于存储从 Keep 同步的跑步记录
 */
data class RunData(
    val id: Long,
    val distance: Double,        // 距离（米）
    val duration: Long,          // 时长（秒）
    val pace: Double,            // 配速（秒/公里）
    val calories: Int,           // 卡路里
    val createTime: Long,        // 跑步开始时间
    val latitude: Double?,       // 起点纬度
    val longitude: Double?,      // 起点经度
    val routePoints: List<RoutePoint>? // 路线轨迹点
)

/**
 * 轨迹点
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val distance: Double?
)
