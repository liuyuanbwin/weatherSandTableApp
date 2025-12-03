package cn.bl1000.weathertable

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import cn.bl1000.weathertable.databinding.ItemServiceCharacteristicBinding
import android.content.Context
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR

class ServicesAdapter(
    private val context: Context,
    private val services: List<BluetoothGattService>
) : RecyclerView.Adapter<ServicesAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemServiceCharacteristicBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(service: BluetoothGattService) {
            binding.serviceUuid.text = "UUID: ${service.uuid}"
            binding.serviceCharacteristicsCount.text = 
                "特征数量: ${service.characteristics.size}"
                
            // 设置点击事件
            binding.root.setOnClickListener {
                showCharacteristics(service)
            }
        }
        
        private fun showCharacteristics(service: BluetoothGattService) {
            val characteristics = service.characteristics
            
            // 创建底部抽屉对话框
            val characteristicSheetDialog = BottomSheetDialog(context)
            
            // 加载布局
            val bottomSheetView = LayoutInflater.from(context).inflate(
                R.layout.bottom_sheet_characteristics, null
            )
            
            // 设置服务信息
            val serviceInfoTextView = bottomSheetView.findViewById<android.widget.TextView>(R.id.service_info)
            serviceInfoTextView.text = "服务: ${service.uuid}\n特征数量: ${characteristics.size}"
            
            // 设置RecyclerView
            val recyclerView = bottomSheetView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.characteristics_recycler_view)
            val characteristicsAdapter = CharacteristicsAdapter(characteristics)
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = characteristicsAdapter
            
            // 设置视图并显示
            characteristicSheetDialog.setContentView(bottomSheetView)
            characteristicSheetDialog.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServiceCharacteristicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(services[position])
    }

    override fun getItemCount(): Int = services.size
}