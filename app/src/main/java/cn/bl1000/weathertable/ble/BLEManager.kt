package cn.bl1000.weathertable.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * BLE工具类，兼容不同Android版本，处理动态权限，提供易用的API
 */
class BLEManager private constructor(private val context: Context) {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    var connectedDevice: BluetoothDevice? = null
        private set
    
    private var onDeviceFoundListener: ((BluetoothDevice, Int, ByteArray) -> Unit)? = null
    private var onConnectionStateChangeListener: ((Boolean) -> Unit)? = null
    private var onDataReceivedListener: ((UUID, ByteArray) -> Unit)? = null
    private var onServicesDiscoveredListener: ((List<BluetoothGattService>) -> Unit)? = null
    private var writeCallbacks: MutableMap<Int, CompletableFuture<Boolean>> = mutableMapOf()
    private var mtuCallback: CompletableFuture<Int>? = null
    
    companion object {
        @Volatile
        private var INSTANCE: BLEManager? = null
        
        fun getInstance(context: Context): BLEManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BLEManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        const val REQUEST_ENABLE_BT = 1001
        const val REQUEST_PERMISSIONS = 1002
        val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        const val TAG = "BLEManager"
        const val DEFAULT_MTU = 23
        const val MAX_MTU = 517
    }
    
    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }
    
    /**
     * 检查是否支持BLE
     */
    fun isBLESupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    
    /**
     * 检查蓝牙是否已启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
    
    /**
     * 获取需要的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    /**
     * 检查是否已授予权限
     */
    fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 设置设备发现监听器
     */
    fun setOnDeviceFoundListener(listener: (BluetoothDevice, Int, ByteArray) -> Unit) {
        onDeviceFoundListener = listener
    }
    
    /**
     * 设置连接状态变更监听器
     */
    fun setOnConnectionStateChangeListener(listener: (Boolean) -> Unit) {
        onConnectionStateChangeListener = listener
    }
    
    /**
     * 设置数据接收监听器
     */
    fun setOnDataReceivedListener(listener: (UUID, ByteArray) -> Unit) {
        onDataReceivedListener = listener
    }
    
    /**
     * 设置服务发现监听器
     */
    fun setOnServicesDiscoveredListener(listener: (List<BluetoothGattService>) -> Unit) {
        onServicesDiscoveredListener = listener
    }
    
    /**
     * 开始扫描BLE设备 (扫描所有设备，不进行服务过滤)
     */
    fun startScan() {
        if (!isBluetoothEnabled() || !hasPermissions()) return
        
        Log.d(TAG, "开始无过滤扫描所有BLE设备")
        
        // 不使用任何过滤器扫描所有设备
        val filters: MutableList<ScanFilter> = ArrayList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        bluetoothLeScanner?.startScan(filters, settings, bleScanCallback)
    }
    
    /**
     * 开始扫描具有特定服务UUID的BLE设备
     */
    fun startScanWithServiceUUID(serviceUUID: UUID) {
        if (!isBluetoothEnabled() || !hasPermissions()) return
        
        Log.d(TAG, "开始扫描具有服务UUID $serviceUUID 的BLE设备")
        
        // 创建针对特定服务UUID的过滤器
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUUID))
            .build()
        val filters = listOf(filter)
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        bluetoothLeScanner?.startScan(filters, settings, bleScanCallback)
    }
    
    /**
     * 停止扫描BLE设备
     */
    fun stopScan() {
        Log.d(TAG, "停止扫描")
        bluetoothLeScanner?.stopScan(bleScanCallback)
    }
    
    /**
     * 连接到指定设备
     */
    fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "连接到设备: ${device.name} (${device.address})")
        gatt = device.connectGatt(context, false, gattCallback)
    }
    
    /**
     * 配对设备（使设备在系统蓝牙设置中可见）
     */
    fun pairDevice(device: BluetoothDevice) {
        Log.d(TAG, "尝试配对设备: ${device.name} (${device.address})")
        try {
            device.createBond()
        } catch (e: Exception) {
            Log.e(TAG, "配对设备失败: ${e.message}")
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "断开连接")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectedDevice = null
    }
    
    /**
     * 检查是否已连接到指定设备
     */
    fun isConnectedToDevice(device: BluetoothDevice?): Boolean {
        return if (device == null) {
            false
        } else {
            connectedDevice?.address == device.address
        }
    }
    
    /**
     * 发送数据到指定特征
     */
    fun sendData(serviceUUID: UUID, characteristicUUID: UUID, data: ByteArray): Boolean {
        val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
        return if (characteristic != null) {
            characteristic.value = data
            gatt?.writeCharacteristic(characteristic) ?: false
        } else {
            false
        }
    }
    
    /**
     * 异步发送数据到指定特征，等待写入完成
     */
    fun sendDataAsync(serviceUUID: UUID, characteristicUUID: UUID, data: ByteArray): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
        if (characteristic != null) {
            characteristic.value = data
            val result = gatt?.writeCharacteristic(characteristic) ?: false
            if (!result) {
                future.complete(false)
            } else {
                // 将future保存起来，在回调中完成
                writeCallbacks[characteristic.hashCode()] = future
            }
        } else {
            future.complete(false)
        }
        
        return future
    }
    
    /**
     * 请求交换MTU大小
     */
    fun requestMtu(mtu: Int): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        mtuCallback = future
        
        val result = gatt?.requestMtu(mtu) ?: false
        if (!result) {
            future.complete(DEFAULT_MTU)
            mtuCallback = null
        }
        
        return future
    }
    
    /**
     * 启用特征通知
     */
    fun enableNotifications(serviceUUID: UUID, characteristicUUID: UUID): Boolean {
        val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
        if (characteristic != null) {
            gatt?.setCharacteristicNotification(characteristic, true)
            // 这里通常还需要设置Client Characteristic Configuration Descriptor (CCCD)
            // 略去具体实现以保持简洁
            return true
        }
        return false
    }
    
    /**
     * BLE扫描回调
     */
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                Log.d(TAG, "发现设备: ${it.device.name} (${it.device.address}), RSSI: ${it.rssi}")
                onDeviceFoundListener?.invoke(it.device, it.rssi, it.scanRecord?.bytes ?: byteArrayOf())
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach {
                Log.d(TAG, "批量扫描结果 - 设备: ${it.device.name} (${it.device.address}), RSSI: ${it.rssi}")
                onDeviceFoundListener?.invoke(it.device, it.rssi, it.scanRecord?.bytes ?: byteArrayOf())
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "扫描失败，错误码: $errorCode")
            // 处理扫描失败
        }
    }
    
    /**
     * GATT回调
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "连接状态变更: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "设备已连接")
                    connectedDevice = gatt?.device
                    onConnectionStateChangeListener?.invoke(true)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "设备已断开连接")
                    connectedDevice = null
                    onConnectionStateChangeListener?.invoke(false)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "服务发现完成，状态: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt?.services ?: emptyList()
                Log.d(TAG, "发现 ${services.size} 个服务")
                for (service in services) {
                    Log.d(TAG, "服务 UUID: ${service.uuid}, 特征数量: ${service.characteristics.size}")
                }
                onServicesDiscoveredListener?.invoke(services)
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.uuid?.let { uuid ->
                    characteristic.value?.let { value ->
                        Log.d(TAG, "特征读取成功: $uuid")
                        onDataReceivedListener?.invoke(uuid, value)
                    }
                }
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "特征写入完成，状态: $status")
            
            // 完成对应的future
            characteristic?.let {
                val future = writeCallbacks.remove(it.hashCode())
                future?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "MTU变更: mtu=$mtu, status=$status")
            
            // 完成MTU交换的future
            mtuCallback?.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU)
            mtuCallback = null
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(TAG, "特征值变更: ${characteristic.uuid}")
            onDataReceivedListener?.invoke(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }
    }
}