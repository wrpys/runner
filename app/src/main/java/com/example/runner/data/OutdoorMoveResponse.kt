package com.example.runner.data

import org.json.JSONObject

/**
 * Keep 户外运动数据响应
 */
data class OutdoorMoveResponse(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val distance: Double,
    val duration: Double,
    val calories: Double,
    val avgPace: Double,
    val bestPace: Double,
    val avgHeartRate: Double?,
    val type: Int,
    val geoPoints: List<GeoPoint>?
)

/**
 * 轨迹点数据
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val distance: Double?,
    val speed: Double?
)

/**
 * 解析 Keep API 返回的户外运动数据
 */
fun parseOutdoorMoveResponse(json: JSONObject): List<OutdoorMoveResponse> {
    val result = json.optJSONObject("result") ?: return emptyList()
    val dataList = result.optJSONArray("dataList") ?: return emptyList()

    val moves = mutableListOf<OutdoorMoveResponse>()
    for (i in 0 until dataList.length()) {
        val item = dataList.optJSONObject(i) ?: continue
        try {
            moves.add(
                OutdoorMoveResponse(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    startTime = item.optLong("startTime"),
                    endTime = item.optLong("endTime"),
                    distance = item.optDouble("distance", 0.0),
                    duration = item.optDouble("duration", 0.0),
                    calories = item.optDouble("calories", 0.0),
                    avgPace = item.optDouble("avgPace", 0.0),
                    bestPace = item.optDouble("bestPace", 0.0),
                    avgHeartRate = if (item.has("avgHeartRate")) item.optDouble("avgHeartRate", 0.0) else null,
                    type = item.optInt("type", 0),
                    geoPoints = parseGeoPoints(item.optJSONObject("geoPoints"))
                )
            )
        } catch (e: Exception) {
            // 跳过解析失败的数据
        }
    }
    return moves
}

/**
 * 解析轨迹点数据
 */
fun parseGeoPoints(geoPointsObj: JSONObject?): List<GeoPoint>? {
    if (geoPointsObj == null) return null

    val points = mutableListOf<GeoPoint>()

    // 轨迹点数据格式：{distance: [], lat: [], lon: [], altitude: [], speed: []}
    val distances = toJsonArray(geoPointsObj.opt("distance"))
    val latitudes = toJsonArray(geoPointsObj.opt("lat"))
    val longitudes = toJsonArray(geoPointsObj.opt("lon"))
    val altitudes = toJsonArray(geoPointsObj.opt("altitude"))
    val speeds = toJsonArray(geoPointsObj.opt("speed"))

    val size = maxOf(
        distances?.length() ?: 0,
        latitudes?.length() ?: 0,
        longitudes?.length() ?: 0
    )

    for (i in 0 until size) {
        points.add(
            GeoPoint(
                latitude = latitudes?.optDouble(i, 0.0) ?: 0.0,
                longitude = longitudes?.optDouble(i, 0.0) ?: 0.0,
                altitude = altitudes?.optDouble(i, 0.0),
                distance = distances?.optDouble(i, 0.0),
                speed = speeds?.optDouble(i, 0.0)
            )
        )
    }

    return points
}

fun toJsonArray(any: Any?): org.json.JSONArray? {
    return any as? org.json.JSONArray
}
