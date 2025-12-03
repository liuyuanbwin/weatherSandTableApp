package cn.bl1000.weathertable

import android.bluetooth.BluetoothGattCharacteristic
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.bl1000.weathertable.databinding.ItemCharacteristicBinding

class CharacteristicsAdapter(
    private val characteristics: List<BluetoothGattCharacteristic>
) : RecyclerView.Adapter<CharacteristicsAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemCharacteristicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(characteristic: BluetoothGattCharacteristic) {
            binding.characteristicUuid.text = "UUID: ${characteristic.uuid}"
            
            // 解析特征属性
            val properties = parseProperties(characteristic.properties)
            binding.characteristicProperties.text = "属性: $properties"
        }
        
        private fun parseProperties(properties: Int): String {
            val props = mutableListOf<String>()
            
            if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) {
                props.add("广播")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                props.add("读取")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                props.add("无响应写入")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                props.add("写入")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                props.add("通知")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                props.add("指示")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
                props.add("签名写入")
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
                props.add("扩展属性")
            }
            
            return if (props.isEmpty()) "无" else props.joinToString(", ")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacteristicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(characteristics[position])
    }

    override fun getItemCount(): Int = characteristics.size
}