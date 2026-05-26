package com.example.readeptd.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 系统 UI 工具类
 * 提供状态栏和导航栏控制的实用函数
 */
object SystemUiUtils {

    /**
     * ✅ 更新系统栏颜色（状态栏和导航栏）
     *
     * @param window 窗口实例
     * @param isNightMode 是否为夜间模式
     */
    fun updateSystemBarColors(window: Window, isNightMode: Boolean) {
        if (isNightMode) {
            // 夜间模式：使用深色状态栏和导航栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                // ✅ 设置浅色图标（白色）以适配深色背景
                // 关键：必须清除 LIGHT_STATUS_BAR 标志，否则图标仍然是深色
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    // 清除 LIGHT_STATUS_BAR 标志，使状态栏图标变为白色
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    window.decorView.systemUiVisibility = flags
                }
                
                // Android 11+ 使用新的 API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        0, // 不设置任何外观标志（即使用浅色图标）
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            // 日间模式：使用透明/浅色状态栏和导航栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                // ✅ 设置深色图标（黑色）以适配浅色背景
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
                
                // Android 11+ 支持浅色导航栏图标
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        }
    }


    /**
     * 检查并请求通知权限（Android 13+）
     * @param activity Activity 实例
     * @return 是否已拥有权限
     */
    fun checkAndRequestNotificationPermission(activity: ComponentActivity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                Log.d("Utils", "请求通知权限")
                return false
            }
            Log.d("Utils", "已拥有通知权限")
            return true
        }
        // Android 13 以下不需要运行时权限
        return true
    }
}
