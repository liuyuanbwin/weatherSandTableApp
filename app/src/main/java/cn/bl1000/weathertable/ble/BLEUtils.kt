package cn.bl1000.weathertable.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * BLE工具类，提供常用静态方法
 */
object BLEUtils {
    
    /**
     * 检查位置服务是否启用（Android 6.0-11需要）
     */
    fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 从Android 9开始，使用新的API检查位置服务
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        } else {
            // Android 9之前的版本
            val locationMode = try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }
            locationMode != Settings.Secure.LOCATION_MODE_OFF
        }
    }
    
    /**
     * 获取启用蓝牙的Intent
     */
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
    
    /**
     * 检查是否需要位置权限（Android 6.0-11）
     */
    fun needLocationPermissions(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S && 
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
    }
}