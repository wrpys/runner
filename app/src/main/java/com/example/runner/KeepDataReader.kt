package com.example.runner

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.runner.data.RunData
import com.example.runner.data.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root 方式读取 Keep 数据库
 *
 * Keep 数据存储位置：
 * - 主数据库：/data/data/com.gotokeep.keep/databases/keep.db
 * - 运动数据：/data/data/com.gotokeep.keep/databases/sport_data.db
 *
 * 需要手机已 Root 并授权本应用 SU 权限
 */
class KeepDataReader(private val context: Context) {

    companion object {
        private const val TAG = "KeepDataReader"
        private const val KEEP_PACKAGE = "com.gotokeep.keep"
        private const val KEEP_DATA_PATH = "/data/data/$KEEP_PACKAGE"
        private const val SPORT_DB_PATH = "$KEEP_DATA_PATH/databases/sport_data.db"
    }

    /**
     * 检查是否已 Root 并有访问权限
     */
    fun isRootAvailable(): Boolean {
        return executeRootCommand("id") != null
    }

    /**
     * 读取所有跑步记录
     */
    fun readRunRecords(): List<RunData> {
        val records = mutableListOf<RunData>()

        // 尝试复制数据库到可访问位置
        val tempDb = "/data/local/tmp/keep_sport.db"
        val copyResult = executeRootCommand("cp $SPORT_DB_PATH $tempDb && chmod 644 $tempDb")

        if (copyResult == null) {
            Log.e(TAG, "无法复制数据库文件")
            return emptyList()
        }

        // 使用 strings 命令提取可读数据（简单解析）
        val dataResult = executeRootCommand("strings $tempDb | grep -E '\"distance\"|\"duration\"|\"calories\"' | head -100")
        if (dataResult != null) {
            records.addAll(parseSimpleData(dataResult))
        }

        // 清理临时文件
        executeRootCommand("rm $tempDb")

        return records
    }

    /**
     * 简单解析数据
     */
    private fun parseSimpleData(output: String): List<RunData> {
        val records = mutableListOf<RunData>()
        val lines = output.split("\n")

        var distance = 0.0
        var duration: Long = 0
        var calories = 0

        for (line in lines) {
            if (line.contains("distance")) {
                val match = Regex("\"distance\"\\s*:\\s*(\\d+\\.?\\d*)").find(line)
                distance = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            } else if (line.contains("duration")) {
                val match = Regex("\"duration\"\\s*:\\s*(\\d+)").find(line)
                duration = match?.groupValues?.get(1)?.toLongOrNull() ?: 0
            } else if (line.contains("calories")) {
                val match = Regex("\"calories\"\\s*:\\s*(\\d+)").find(line)
                calories = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            // 当有完整数据时创建记录
            if (distance > 0 && duration > 0) {
                records.add(
                    RunData(
                        id = System.currentTimeMillis() + records.size,
                        distance = distance,
                        duration = duration,
                        pace = if (distance > 0) (duration / (distance / 1000)) else 0.0,
                        calories = calories,
                        createTime = System.currentTimeMillis(),
                        latitude = null,
                        longitude = null,
                        routePoints = null
                    )
                )
                distance = 0.0
                duration = 0
                calories = 0
            }
        }

        return records
    }

    /**
     * 执行 Root 命令
     */
    fun executeRootCommand(command: String): String? {
        Log.d(TAG, "尝试执行 Root 命令：$command")
        var process: Process? = null
        var reader: BufferedReader? = null
        var os: DataOutputStream? = null

        return try {
            Log.d(TAG, "执行 Runtime.getRuntime().exec(\"su\")")
            process = Runtime.getRuntime().exec("su")
            Log.d(TAG, "su 进程已启动")

            os = DataOutputStream(process.outputStream)
            Log.d(TAG, "准备写入命令到进程")

            // 写入命令
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            Log.d(TAG, "命令已写入：$command")

            // 读取输出
            reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText().trim()
            Log.d(TAG, "命令输出：$result")

            // 等待进程结束
            try {
                val exitCode = process.waitFor()
                Log.d(TAG, "进程结束，退出码：$exitCode")
            } catch (e: Exception) {
                Log.e(TAG, "等待进程异常", e)
            }

            if (result.isNotEmpty() && !result.contains("denied") && !result.contains("not found")) {
                Log.d(TAG, "Root 命令成功：$command")
                result
            } else {
                Log.w(TAG, "Root 命令无有效输出：$command, 输出：$result")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行 Root 命令异常：$command, 错误：${e.message}", e)
            null
        } finally {
            try { reader?.close() } catch (e: Exception) {}
            try { os?.close() } catch (e: Exception) {}
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    /**
     * 检查 Keep 是否已安装（使用 Android 原生 API）
     */
    fun isKeepInstalled(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(KEEP_PACKAGE, PackageManager.GET_ACTIVITIES)
            Log.d(TAG, "Keep 应用已安装，版本：${packageInfo.versionName}")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Keep 应用未安装，包名：$KEEP_PACKAGE")
            Log.w(TAG, "已安装的应用包名列表（包含 keep）:")
            val installedPackages = context.packageManager.getInstalledPackages(0)
            installedPackages.filter { it.packageName.contains("keep", ignoreCase = true) }
                .forEach { Log.w(TAG, "  - ${it.packageName}") }
            false
        }
    }
}
