package cn.bl1000.weathertable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.bl1000.weathertable.databinding.ItemBleDeviceBinding
import android.util.Log
import java.util.UUID

class BLEDevicesAdapter(
    private val devices: List<BLEDevice>,
    private val onItemClick: (BLEDevice) -> Unit
) : RecyclerView.Adapter<BLEDevicesAdapter.ViewHolder>() {
    
    companion object {
        const val TAG = "BLEDevicesAdapter"
    }

    inner class ViewHolder(private val binding: ItemBleDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BLEDevice) {
            binding.deviceName.text = device.device.name ?: "未知设备"
            binding.deviceAddress.text = device.device.address
            binding.deviceRssi.text = "${device.rssi} dBm"
            
            // 显示连接状态
            if (device.isConnected) {
                binding.deviceName.text = "${device.device.name ?: "未知设备"} (已连接)"
            }
            
            // 显示设备广播的服务UUID
            val serviceUuidsText = if (device.serviceUuids.isEmpty()) {
                "Service UUIDs: None"
            } else {
                "Service UUIDs: ${device.serviceUuids.joinToString(", ") { it.toString() }}"
            }
            binding.deviceServiceUuids.text = serviceUuidsText
            
            // 显示设备广播的厂商特定数据
            val manufacturerDataText = if (device.manufacturerData.isEmpty()) {
                "Manufacturer Data: None"
            } else {
                val dataStrings = device.manufacturerData.map { entry ->
                    val companyId = entry.key
                    val data = entry.value
                    val dataHex = data.joinToString("") { String.format("%02X", it) }
                    "ID:${String.format("0x%04X", companyId)} Data:$dataHex"
                }
                "Manufacturer Data: ${dataStrings.joinToString("; ")}"
            }
            binding.deviceManufacturerData.text = manufacturerDataText
            
            binding.root.setOnClickListener {









































































































                Log.d(TAG, "点击设备项: ${device.device.name} (${device.device.address})")
                onItemClick(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBleDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size
}