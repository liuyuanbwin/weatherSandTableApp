package cn.bl1000.weathertable

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import android.view.Menu
import android.view.MenuItem
import android.content.SharedPreferences
import cn.bl1000.weathertable.ble.BLEManager
import cn.bl1000.weathertable.databinding.ActivityMainBinding
import java.util.UUID

data class BLEDevice(
    val device: BluetoothDevice, 
    var rssi: Int,
    val serviceUuids: List<UUID> = emptyList(),
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    var isConnected: Boolean = false // 添加连接状态字段
)

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BLEManager
    private lateinit var bleDevices: MutableList<BLEDevice>
    private lateinit var bleDevicesAdapter: BLEDevicesAdapter
    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var preferences: SharedPreferences
    private var connectionStatusMenuItem: MenuItem? = null
    
    // 保存连接过的设备地址
    private val connectedDeviceAddresses = mutableSetOf<String>()
    private var lastConnectedDevice: BluetoothDevice? = null
    
    // 超时处理相关
    private val autoReconnectTimeoutMillis = 10000L // 10秒超时
    private var autoReconnectTimer: android.os.CountDownTimer? = null
    
    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 初始化SharedPreferences
        preferences = getSharedPreferences("ble_devices", Context.MODE_PRIVATE)
        
        // 初始化BLE管理器
        bleManager = BLEManager.getInstance(this)
        
        // 初始化设备列表
        bleDevices = mutableListOf()
        
        // 初始化连接状态栏
        initConnectionStatusBar()
        
        // 恢复之前连接过的设备信息
        restoreConnectedDevices()
        
        // 设置FloatingActionButton点击事件
        binding.fab.setOnClickListener { view ->
            if (checkBLEPermissions()) {
                if (bleManager.isBluetoothEnabled()) {
                    // 开始扫描BLE设备
                    startBLEScan()
                } else {
                    // 请求启用蓝牙
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            } else {
                // 请求必要权限
                requestPermissionLauncher.launch(bleManager.getRequiredPermissions())
            }
        }
    }

    private fun checkBLEPermissions(): Boolean {
        return bleManager.hasPermissions()
    }

    // 启动蓝牙请求的ActivityResultLauncher
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 用户启用了蓝牙，开始扫描
            startBLEScan()
        } else {
            // 用户拒绝启用蓝牙
            Toast.makeText(this, "需要启用蓝牙才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }

    // 请求权限的ActivityResultLauncher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 所有权限都被授予
            if (bleManager.isBluetoothEnabled()) {
                startBLEScan()
            } else {
                // 请求启用蓝牙
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            // 有些权限被拒绝
            Toast.makeText(this, "需要相应权限才能扫描蓝牙设备", Toast.LENGTH_SHORT).show()
            
            // 检查是否有权限被永久拒绝
            val shouldShowRequestPermissionRationale = permissions.keys.any { permission ->
                !shouldShowRequestPermissionRationale(permission)
            }
            
            if (shouldShowRequestPermissionRationale) {
                // 引导用户去设置页面手动开启权限
                Snackbar.make(
                    binding.root, 
                    "需要手动开启权限才能使用蓝牙功能", 
                    Snackbar.LENGTH_LONG
                ).setAction("去设置") {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri: Uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }.show()
            }
        }
    }

    private fun startBLEScan() {
        Log.d(TAG, "开始BLE扫描")
        
        // 清空现有设备列表
        bleDevices.clear()
        
        // 创建并显示底部抽屉
        showBLEDeviceList()
        
        // 设置BLE管理器的回调
        bleManager.setOnDeviceFoundListener { device, rssi, scanRecord ->
            runOnUiThread {
                Log.d(TAG, "收到设备发现回调: ${device.name} (${device.address})")
                // 解析广播数据中的服务UUID
                val serviceUuids = parseServiceUuidsFromScanRecord(scanRecord)
                Log.d(TAG, "设备 ${device.name} 解析到 ${serviceUuids.size} 个服务UUID")
                // 解析广播数据中的厂商特定数据
                val manufacturerData = parseManufacturerDataFromScanRecord(scanRecord)
                
                // 检查设备是否已经在列表中
                val existingDevice = bleDevices.find { it.device.address == device.address }
                if (existingDevice != null) {
                    // 更新RSSI值和服务UUID
                    existingDevice.rssi = rssi
                    val index = bleDevices.indexOf(existingDevice)
                    bleDevicesAdapter.notifyItemChanged(index)
                } else {
                    // 检查是否是之前连接过的设备
                    val isConnected = connectedDeviceAddresses.contains(device.address)
                    
                    // 添加新设备
                    val bleDevice = BLEDevice(device, rssi, serviceUuids, manufacturerData, isConnected)
                    bleDevices.add(bleDevice)
                    bleDevicesAdapter.notifyItemInserted(bleDevices.size - 1)
                    Log.d(TAG, "添加新设备到列表: ${device.name} (${device.address})")
                    
                    // 检查是否是之前连接过的设备，如果是则自动连接
                    if (isConnected) {
                        Log.d(TAG, "发现之前连接过的设备: ${device.name}，自动连接...")
                        cancelAutoReconnectTimeout() // 取消超时计时
                        connectToDevice(device)
                    }
                }
            }
        }
        
        // 启动自动重连超时计时器
        startAutoReconnectTimeout()
        
        // 开始扫描
        // bleManager.startScan() // 扫描所有设备
        bleManager.startScanWithServiceUUID(BLEManager.SERVICE_UUID) // 只扫描包含特定服务UUID的设备
        Toast.makeText(this, "开始扫描BLE设备...", Toast.LENGTH_SHORT).show()
    }

    private fun parseServiceUuidsFromScanRecord(scanRecordBytes: ByteArray): List<UUID> {
        if (scanRecordBytes.isEmpty()) return emptyList()
        
        val uuids = mutableListOf<UUID>()
        var i = 0
        while (i < scanRecordBytes.size - 2) {
            val length = scanRecordBytes[i].toInt() and 0xFF
            if (length == 0) break
            
            val type = scanRecordBytes[i + 1].toInt() and 0xFF
            when (type) {
                // 部分16位服务UUID列表 (ESP32使用的就是这种类型)
                0x02 -> {
                    for (j in i + 2 until i + length + 1 step 2) {
                        if (j + 1 < scanRecordBytes.size) {
                            val uuid16 = (scanRecordBytes[j + 1].toInt() and 0xFF shl 8) or 
                                         (scanRecordBytes[j].toInt() and 0xFF)
                            val uuid = UUID.fromString(
                                String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb",
                                    uuid16
                                )
                            )
                            uuids.add(uuid)
                        }
                    }
                }
                // 完整的16位服务UUID列表
                0x03 -> {
                    for (j in i + 2 until i + length + 1 step 2) {
                        if (j + 1 < scanRecordBytes.size) {
                            val uuid16 = (scanRecordBytes[j + 1].toInt() and 0xFF shl 8) or 
                                         (scanRecordBytes[j].toInt() and 0xFF)
                            val uuid = UUID.fromString(
                                String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb",
                                    uuid16
                                )
                            )
                            uuids.add(uuid)
                        }
                    }
                }
                // 部分32位服务UUID
                0x04 -> {
                    for (j in i + 2 until i + length + 1 step 4) {
                        if (j + 3 < scanRecordBytes.size) {
                            val uuid32 = (scanRecordBytes[j + 3].toInt() and 0xFF shl 24) or
                                         (scanRecordBytes[j + 2].toInt() and 0xFF shl 16) or
                                         (scanRecordBytes[j + 1].toInt() and 0xFF shl 8) or
                                         (scanRecordBytes[j].toInt() and 0xFF)
                            val uuid = UUID.fromString(
                                String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb",
                                    uuid32
                                )
                            )
                            uuids.add(uuid)
                        }
                    }
                }
                // 完整的32位服务UUID
                0x05 -> {
                    for (j in i + 2 until i + length + 1 step 4) {
                        if (j + 3 < scanRecordBytes.size) {
                            val uuid32 = (scanRecordBytes[j + 3].toInt() and 0xFF shl 24) or
                                         (scanRecordBytes[j + 2].toInt() and 0xFF shl 16) or
                                         (scanRecordBytes[j + 1].toInt() and 0xFF shl 8) or
                                         (scanRecordBytes[j].toInt() and 0xFF)
                            val uuid = UUID.fromString(
                                String.format(
                                    "%08x-0000-1000-8000-00805f9b34fb",
                                    uuid32
                                )
                            )
                            uuids.add(uuid)
                        }
                    }
                }
                // 部分的128位服务UUID (根据您的广播包分析，ESP32使用的是这种类型)
                0x06 -> {
                    if (length >= 17) { // 16 bytes for UUID + 1 for length
                        try {
                            val uuidBytes = ByteArray(16)
                            for (j in 0 until 16) {
                                uuidBytes[15 - j] = scanRecordBytes[i + 2 + j] // Reverse byte order
                            }
                            val msb = (0..7).fold(0L) { acc, j -> 
                                acc shl 8 or (uuidBytes[j].toLong() and 0xFF) 
                            }
                            val lsb = (8..15).fold(0L) { acc, j -> 
                                acc shl 8 or (uuidBytes[j].toLong() and 0xFF) 
                            }
                            val uuid = UUID(msb, lsb)
                            Log.d(TAG, "解析到部分128位UUID: $uuid")
                            uuids.add(uuid)
                        } catch (e: Exception) {
                            Log.e(TAG, "解析128位UUID失败", e)
                        }
                    }
                }
                // 完整的128位服务UUID
                0x07 -> {
                    if (length >= 17) { // 16 bytes for UUID + 1 for length
                        try {
                            val uuidBytes = ByteArray(16)
                            for (j in 0 until 16) {
                                uuidBytes[15 - j] = scanRecordBytes[i + 2 + j] // Reverse byte order
                            }
                            val msb = (0..7).fold(0L) { acc, j -> 
                                acc shl 8 or (uuidBytes[j].toLong() and 0xFF) 
                            }
                            val lsb = (8..15).fold(0L) { acc, j -> 
                                acc shl 8 or (uuidBytes[j].toLong() and 0xFF) 
                            }
                            val uuid = UUID(msb, lsb)
                            Log.d(TAG, "解析到完整128位UUID: $uuid")
                            uuids.add(uuid)
                        } catch (e: Exception) {
                            Log.e(TAG, "解析128位UUID失败", e)
                        }
                    }
                }
            }
            i += length + 1
        }
        Log.d(TAG, "解析到服务UUID: ${uuids.joinToString(", ")}")
        return uuids
    }
    
    private fun parseManufacturerDataFromScanRecord(scanRecordBytes: ByteArray): Map<Int, ByteArray> {
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        
        if (scanRecordBytes.isEmpty()) return manufacturerData
        
        var i = 0
        while (i < scanRecordBytes.size - 2) {
            val length = scanRecordBytes[i].toInt() and 0xFF
            if (length == 0) break
            
            val type = scanRecordBytes[i + 1].toInt() and 0xFF
            // 0xFF 是厂商特定数据的类型标识
            if (type == 0xFF && length >= 3) {
                // 提取厂商ID（little-endian格式）
                val companyId = (scanRecordBytes[i + 3].toInt() and 0xFF shl 8) or 
                              (scanRecordBytes[i + 2].toInt() and 0xFF)
                
                // 提取厂商数据
                val dataLength = length - 3
                if (i + 4 + dataLength <= scanRecordBytes.size) {
                    val data = ByteArray(dataLength)
                    System.arraycopy(scanRecordBytes, i + 4, data, 0, dataLength)
                    manufacturerData[companyId] = data
                }
            }
            i += length + 1
        }
        
        return manufacturerData
    }

    private fun showBLEDeviceList() {
        // 创建底部抽屉对话框
        bottomSheetDialog = BottomSheetDialog(this)
        
        // 加载布局
        val bottomSheetView = LayoutInflater.from(this).inflate(
            R.layout.bottom_sheet_ble_devices, null
        )
        
        // 设置RecyclerView
        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.ble_devices_recycler_view)
        bleDevicesAdapter = BLEDevicesAdapter(bleDevices) { device ->
            // 处理设备点击事件 - 连接设备
            Log.d(TAG, "用户点击设备: ${device.device.name} (${device.device.address})")
            bottomSheetDialog?.dismiss()
            connectToDevice(device.device)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = bleDevicesAdapter
        
        // 设置视图并显示
        bottomSheetDialog?.setContentView(bottomSheetView)
        bottomSheetDialog?.show()
        
        // 监听底部抽屉的关闭事件，以便在需要时清理资源
        bottomSheetDialog?.setOnDismissListener {
            Log.d(TAG, "设备列表底部抽屉已关闭")
            // 这里可以根据需要添加清理逻辑
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "连接到设备: ${device.name} (${device.address})")
        Toast.makeText(this, "正在连接到设备: ${device.name}", Toast.LENGTH_SHORT).show()
        
        // 设置连接状态变更监听器
        bleManager.setOnConnectionStateChangeListener { connected ->
            runOnUiThread {
                if (connected) {
                    Toast.makeText(this, "已连接到设备: ${device.name}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "已连接到设备: ${device.name} (${device.address})")
                    // 保存连接过的设备地址
                    connectedDeviceAddresses.add(device.address)
                    lastConnectedDevice = device
                    updateConnectionStatusBar(true, device.name ?: "")
                    cancelAutoReconnectTimeout() // 连接成功，取消超时计时
                    
                    // 更新设备列表中的连接状态
                    updateDeviceConnectionStatus(device, true)
                } else {
                    Toast.makeText(this, "与设备断开连接: ${device.name}", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "与设备断开连接: ${device.name} (${device.address})")
                    updateConnectionStatusBar(false, "")
                    
                    // 更新设备列表中的连接状态
                    updateDeviceConnectionStatus(device, false)
                }
            }
        }
        
        // 设置服务发现监听器
        bleManager.setOnServicesDiscoveredListener { services ->
            runOnUiThread {
                Log.d(TAG, "服务发现完成，发现 ${services.size} 个服务")
                showDeviceServices(device, services)
            }
        }
        
        // 连接设备
        bleManager.connectToDevice(device)
    }

    private fun showDeviceServices(device: BluetoothDevice, services: List<BluetoothGattService>) {
        Log.d(TAG, "显示设备服务，设备: ${device.name}，服务数: ${services.size}")
        
        // 创建底部抽屉对话框显示服务和特征
        val serviceSheetDialog = BottomSheetDialog(this)
        
        // 加载布局
        val bottomSheetView = LayoutInflater.from(this).inflate(
            R.layout.bottom_sheet_services, null
        )
        
        // 设置设备名称
        val deviceNameTextView = bottomSheetView.findViewById<android.widget.TextView>(R.id.device_name)
        deviceNameTextView.text = device.name ?: "未知设备"
        
        // 获取连接状态指示器和连接按钮
        val connectionStatusIndicator = bottomSheetView.findViewById<View>(R.id.connection_status_indicator)
        val connectButton = bottomSheetView.findViewById<Button>(R.id.connect_button)
        
        // 检查当前设备是否已连接
        val isConnected = bleManager.isConnectedToDevice(device)
        updateConnectionStatusIndicator(connectionStatusIndicator, isConnected)
        
        if (isConnected) {
            connectButton.visibility = View.GONE
        } else {
            connectButton.visibility = View.VISIBLE
            connectButton.setOnClickListener {
                connectToDevice(device)
                // 如果希望设备在系统蓝牙设置中显示，可以调用配对方法
                // bleManager.pairDevice(device)
                serviceSheetDialog.dismiss()
            }
        }
        
        // 设置RecyclerView
        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.services_recycler_view)
        val servicesAdapter = ServicesAdapter(this, services)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = servicesAdapter
        
        // 设置视图并显示
        serviceSheetDialog.setContentView(bottomSheetView)
        serviceSheetDialog.show()
        
        // 监听服务详情弹窗的关闭事件
        serviceSheetDialog.setOnDismissListener {
            Log.d(TAG, "服务详情弹窗已关闭")
            // 这里可以选择是否保持连接或断开连接
            // 如果需要在关闭弹窗时断开连接，可以取消下面的注释
            // bleManager.disconnect()
        }
        
        // 注意：我们不再在连接设备后停止扫描，这样可以继续发现其他设备
        // bleManager.stopScan()
    }
    
    private fun initConnectionStatusBar() {
        // 不再需要初始化底部的连接状态栏，改为使用ActionBar上的按钮
    }
    
    /**
     * 更新ActionBar上的连接状态按钮
     */
    private fun updateConnectionStatusButton(connected: Boolean, deviceName: String) {
        runOnUiThread {
            connectionStatusMenuItem?.apply {
                if (connected) {
                    // 连接状态 - 显示设备名称，无图标
                    setTitle(deviceName)
                    icon = null
                } else {
                    // 未连接状态 - 显示黄色圆点图标
                    setTitle("")
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_not_connected)
                }
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
    }
    
    private fun updateConnectionStatusBar(connected: Boolean, deviceName: String?) {
        runOnUiThread {
            updateConnectionStatusButton(connected, deviceName ?: "")
            
            // 移除了原来对页面上connection_status_bar的操作
        }
    }
    
    private fun restoreConnectedDevices() {
        // 从SharedPreferences恢复连接过的设备信息
        val savedAddresses = preferences.getStringSet("connected_devices", emptySet()) ?: emptySet()
        connectedDeviceAddresses.addAll(savedAddresses)
        Log.d(TAG, "恢复了 ${connectedDeviceAddresses.size} 个已连接设备")
    }
    
    private fun saveConnectedDevices() {
        // 将连接过的设备信息保存到SharedPreferences中
        preferences.edit()
            .putStringSet("connected_devices", connectedDeviceAddresses)
            .apply()
        Log.d(TAG, "保存了 ${connectedDeviceAddresses.size} 个已连接设备")
    }
    
    private fun updateConnectionStatusIndicator(indicator: View, isConnected: Boolean) {
        // 此方法现在不再使用，因为连接状态显示已移动到ActionBar上
    }
    
    /**
     * 更新设备列表中的连接状态
     */
    private fun updateDeviceConnectionStatus(device: BluetoothDevice, connected: Boolean) {
        val index = bleDevices.indexOfFirst { it.device.address == device.address }
        if (index != -1) {
            // 如果设备在列表中，更新其连接状态
            bleDevices[index] = bleDevices[index].copy(isConnected = connected)
            bleDevicesAdapter.notifyItemChanged(index)
            Log.d(TAG, "更新设备 ${device.name} 的连接状态为: $connected")
        }
    }
    
    private fun startAutoReconnectTimeout() {
        // 如果没有需要自动重连的设备，则不需要超时处理
        if (connectedDeviceAddresses.isEmpty()) {
            return
        }
        
        autoReconnectTimer?.cancel()
        autoReconnectTimer = object : android.os.CountDownTimer(autoReconnectTimeoutMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d(TAG, "自动重连倒计时: ${millisUntilFinished / 1000}秒")
            }
            
            override fun onFinish() {
                Log.d(TAG, "自动重连超时，未找到之前连接的设备")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "自动重连超时，未找到之前连接的设备", Toast.LENGTH_SHORT).show()
                    // 可以在这里添加其他超时处理逻辑
                }
            }
        }.start()
    }
    
    private fun cancelAutoReconnectTimeout() {
        autoReconnectTimer?.cancel()
        autoReconnectTimer = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        connectionStatusMenuItem = menu.findItem(R.id.action_connection_status)
        updateConnectionStatusButton(false, "")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_connection_status -> {
                // 如果已经连接设备，则断开连接
                if (bleManager.isConnectedToDevice(bleManager.connectedDevice)) {
                    bleManager.disconnect()
                    updateConnectionStatusBar(false, "")
                    Toast.makeText(this, "已断开与设备的连接", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity销毁")
        bottomSheetDialog?.dismiss()
        bleManager.stopScan()
        cancelAutoReconnectTimeout() // 取消超时计时
        // 保存连接过的设备信息
        saveConnectedDevices()
        // 注意：这里会断开所有连接，如果需要保持后台连接可以注释掉下面一行
        bleManager.disconnect()
    }
}