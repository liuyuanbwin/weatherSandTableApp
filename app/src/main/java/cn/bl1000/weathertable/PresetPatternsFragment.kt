package cn.bl1000.weathertable

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import cn.bl1000.weathertable.databinding.FragmentPresetPatternsBinding
import java.io.IOException

class PresetPatternsFragment : Fragment() {
    private var _binding: FragmentPresetPatternsBinding? = null
    private val binding get() = _binding!!

    // TAB状态
    private enum class TabType { 
        CURVES,     // 刷屏图案
        PRESETS     // 预设图片
    }
    
    private var currentTab = TabType.CURVES

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetPatternsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置TAB按钮点击事件
        binding.btnCurves.setOnClickListener {
            switchToTab(TabType.CURVES)
        }
        
        binding.btnPresets.setOnClickListener {
            switchToTab(TabType.PRESETS)
        }
        
        // 默认显示刷屏图案
        switchToTab(TabType.CURVES)
    }
    
    private fun switchToTab(tabType: TabType) {
        currentTab = tabType
        
        // 更新按钮状态
        updateTabButtons()
        
        // 根据TAB类型更新内容
        when (tabType) {
            TabType.CURVES -> showCurvePatterns()
            TabType.PRESETS -> showPresetImages()
        }
    }
    
    private fun updateTabButtons() {
        // 更新按钮选中状态
        binding.btnCurves.isSelected = currentTab == TabType.CURVES
        binding.btnPresets.isSelected = currentTab == TabType.PRESETS
        
        // 可以通过改变背景颜色等方式来表示选中状态
        binding.btnCurves.alpha = if (currentTab == TabType.CURVES) 1.0f else 0.6f
        binding.btnPresets.alpha = if (currentTab == TabType.PRESETS) 1.0f else 0.6f
    }
    
    private fun showCurvePatterns() {
        val patterns = listOf(
            "螺旋图案",
            "往复线扫描",
            "谢尔宾斯基三角形",
            "科赫雪花",
            "分形树"
        )
        
        val adapter = PresetPatternsAdapter(patterns) { position ->
            // 点击某个图案时的操作
            val action = R.id.action_PresetPatternsFragment_to_PatternPreviewFragment
            val bundle = Bundle().apply {
                putInt("pattern_type", position)
            }
            findNavController().navigate(action, bundle)
        }
        
        // 刷屏图案每行显示2个
        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter
    }

    private fun showPresetImages() {
        // 获取assets/presets目录下的图片文件名列表
        val presetImages = getPresetImageNames()
        
        val adapter = PresetImageAdapter(presetImages) { imageName ->
            // 点击某个预设图片时的操作
            val action = R.id.action_PresetPatternsFragment_to_OneStrokePathFragment
            val bundle = Bundle().apply {
                putString("preset_image_name", imageName)
            }
            findNavController().navigate(action, bundle)
        }
        
        // 预设图片每行显示4个
        binding.recyclerView.layoutManager = GridLayoutManager(context, 4)
        binding.recyclerView.adapter = adapter
    }
    
    private fun getPresetImageNames(): List<String> {
        val imageNames = mutableListOf<String>()
        try {
            val assetManager = requireContext().assets
            val files = assetManager.list("presets")
            if (files != null) {
                for (file in files) {
                    // 只包含PNG文件
                    if (file.lowercase().endsWith(".png")) {
                        imageNames.add(file)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return imageNames
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PresetPatternsAdapter(
    private val patterns: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PresetPatternsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset_pattern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 确保TextView可见而ImageView隐藏
        holder.textView.visibility = View.VISIBLE
        holder.imageView.visibility = View.GONE
        
        holder.textView.text = patterns[position]
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount() = patterns.size
}

class PresetImageAdapter(
    private val imageNames: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<PresetImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val textView: TextView = view.findViewById(R.id.textView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset_pattern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageName = imageNames[position]
        
        // 确保ImageView可见而TextView隐藏
        holder.imageView.visibility = View.VISIBLE
        holder.textView.visibility = View.GONE
        
        // 显示图片缩略图
        try {
            val assetManager = holder.imageView.context.assets
            val inputStream = assetManager.open("presets/$imageName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            holder.imageView.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
            // 如果加载图片失败，显示文件名
            holder.textView.text = imageName
            holder.textView.visibility = View.VISIBLE
            holder.imageView.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(imageName)
        }
    }

    override fun getItemCount() = imageNames.size
}