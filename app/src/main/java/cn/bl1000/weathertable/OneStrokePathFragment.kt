package cn.bl1000.weathertable

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import cn.bl1000.weathertable.OneStrokePathGenerator.Point
import cn.bl1000.weathertable.databinding.FragmentOneStrokePathBinding
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 一口画功能Fragment
 * 展示一口画路径生成功能
 */
class OneStrokePathFragment : Fragment() {

    private var _binding: FragmentOneStrokePathBinding? = null
    private val binding get() = _binding!!
    private lateinit var pathGenerator: OneStrokePathGenerator
    
    // 图片处理相关变量
    private var selectedBitmap: Bitmap? = null
    private var binaryMask: Array<BooleanArray>? = null
    private var invertedMask: Array<BooleanArray>? = null
    private var connectedMask: Array<BooleanArray>? = null
    private var pathResult: OneStrokePathGenerator.PathResult? = null

    companion object {
        private const val PROCESS_SIZE = 256  // 处理分辨率改为256*256
    }

    // 使用Activity Result API替代已弃用的startActivityForResult
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleSelectedImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOneStrokePathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化一口画路径生成器
        pathGenerator = OneStrokePathGenerator()
        
        // 设置选择图片按钮点击事件
        binding.btnSelectImage.setOnClickListener {
            openImageChooser()
        }
        
        // 设置处理步骤按钮点击事件
        binding.btnBinarize.setOnClickListener {
            performBinarization()
        }
        
        binding.btnInvert.setOnClickListener {
            performInvert()
        }
        
        binding.btnConnectComponents.setOnClickListener {
            performConnectComponents()
        }
        
        binding.btnGeneratePath.setOnClickListener {
            generateOneStrokePath()
        }
        
        // 检查是否有从预设图片传递过来的参数
        val presetImageName = arguments?.getString("preset_image_name")
        if (presetImageName != null) {
            loadPresetImage(presetImageName)
        }
    }
    
    /**
     * 加载预设图片
     */
    private fun loadPresetImage(imageName: String) {
        try {
            val assetManager = requireContext().assets
            val inputStream = assetManager.open("presets/$imageName")
            selectedBitmap = BitmapFactory.decodeStream(inputStream)?.let { 
                Bitmap.createScaledBitmap(it, PROCESS_SIZE, PROCESS_SIZE, true) 
            }
            inputStream.close()
            
            // 显示选中的图片
            binding.ivSelectedImage.setImageBitmap(selectedBitmap)
            
            // 显示处理按钮
            binding.buttonsLayout.visibility = View.VISIBLE
            
            // 清除之前的结果
            clearResults()
            
            Toast.makeText(context, "预设图片加载成功", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(context, "预设图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 打开图片选择器
     */
    private fun openImageChooser() {
        imagePickerLauncher.launch("image/*")
    }
    
    /**
     * 处理选中的图片
     */
    private fun handleSelectedImage(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            // 缩放到512*512进行处理
            selectedBitmap = BitmapFactory.decodeStream(inputStream)?.let { 
                Bitmap.createScaledBitmap(it, PROCESS_SIZE, PROCESS_SIZE, true) 
            }
            inputStream?.close()
            
            // 显示选中的图片
            binding.ivSelectedImage.setImageBitmap(selectedBitmap)
            
            // 显示处理按钮
            binding.buttonsLayout.visibility = View.VISIBLE
            
            // 清除之前的结果
            clearResults()
            
            Toast.makeText(context, "图片选择成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 清除之前的结果
     */
    private fun clearResults() {
        binaryMask = null
        invertedMask = null
        connectedMask = null
        pathResult = null
        binding.ivResult.setImageDrawable(null)
    }
    
    /**
     * 执行二值化处理
     */
    private fun performBinarization() {
        selectedBitmap?.let { bitmap ->
            // 将图片转换为灰度图并二值化 (处理512*512的图像)
            binaryMask = bitmapToBinaryMask(bitmap, PROCESS_SIZE)
            
            // 显示二值化结果（放大显示以便更好地观察）
            val maskBitmap = pathGenerator.maskToBitmap(binaryMask!!)
            val displayBitmap = createEnlargedDisplayBitmap(maskBitmap, 2) // 2倍放大显示
            binding.ivResult.setImageBitmap(displayBitmap)
            
            Toast.makeText(context, "二值化处理完成", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "请先选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 执行反色处理
     */
    private fun performInvert() {
        binaryMask?.let { mask ->
            // 对二值化图像进行反色处理
            invertedMask = invertMask(mask)
            
            // 显示反色结果（放大显示以便更好地观察）
            val maskBitmap = pathGenerator.maskToBitmap(invertedMask!!)
            val displayBitmap = createEnlargedDisplayBitmap(maskBitmap, 2) // 2倍放大显示
            binding.ivResult.setImageBitmap(displayBitmap)
            
            Toast.makeText(context, "反色处理完成", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(context, "请先进行二值化处理", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 将Bitmap转换为二值化mask
     */
    private fun bitmapToBinaryMask(bitmap: Bitmap, size: Int = PROCESS_SIZE): Array<BooleanArray> {
        // 缩放到指定大小 (现在固定为PROCESS_SIZE)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
        
        // 创建二值化mask
        val mask = Array(size) { BooleanArray(size) }
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val pixel = scaledBitmap.getPixel(x, y)
                
                // 获取RGB值
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                // 转换为灰度值
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                // 二值化处理 - 白色部分为true（需要填充的区域）
                mask[y][x] = gray > 128
            }
        }
        
        return mask
    }
    
    /**
     * 对mask进行反色处理
     */
    private fun invertMask(mask: Array<BooleanArray>): Array<BooleanArray> {
        val height = mask.size
        val width = mask[0].size
        
        // 创建反色后的mask
        val inverted = Array(height) { y -> 
            BooleanArray(width) { x -> 
                !mask[y][x] 
            } 
        }
        
        return inverted
    }
    
    /**
     * 创建放大的显示Bitmap（用于mask显示）
     */
    private fun createEnlargedDisplayBitmap(source: Bitmap, scale: Int): Bitmap {
        val width = source.width
        val height = source.height
        val displayWidth = width * scale
        val displayHeight = height * scale
        
        val displayBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(displayBitmap)
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        
        // 计算每个点在显示区域中的大小
        val cellWidth = displayWidth.toFloat() / width
        val cellHeight = displayHeight.toFloat() / height
        val circleRadius = Math.min(cellWidth, cellHeight) / 2 * 0.95f // 95%填充率
        
        // 绘制放大后的点阵
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                if (pixel != android.graphics.Color.TRANSPARENT) {
                    paint.color = pixel
                    
                    // 计算圆心位置
                    val cx = x * cellWidth + cellWidth / 2
                    val cy = y * cellHeight + cellHeight / 2
                    
                    // 绘制圆点
                    canvas.drawCircle(cx, cy, circleRadius, paint)
                }
            }
        }
        
        return displayBitmap
    }
    
    /**
     * 创建放大的路径显示Bitmap
     */
    private fun createEnlargedPathBitmap(source: Bitmap, scale: Int): Bitmap {
        val width = source.width
        val height = source.height
        val displayWidth = width * scale
        val displayHeight = height * scale
        
        // 先缩放原始路径图像
        val scaledBitmap = Bitmap.createScaledBitmap(source, displayWidth, displayHeight, false)
        
        // 创建新的Bitmap并绘制红色路径
        val displayBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(displayBitmap)
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true
        paint.color = android.graphics.Color.RED
        paint.strokeWidth = 2f * scale
        paint.style = android.graphics.Paint.Style.STROKE
        
        // 在放大后的图像上绘制路径
        canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        
        return displayBitmap
    }
    
    /**
     * 动态绘制路径动画（每个点都不同色的循环彩虹效果）
     */
    private fun animatePathDrawing(path: List<Point>, mask: Array<BooleanArray>) {
        val handler = Handler(Looper.getMainLooper())
        
        val bitmap = Bitmap.createBitmap(
            mask[0].size * 2, 
            mask.size * 2, 
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        
        // 先绘制基础mask
        val maskBitmap = pathGenerator.maskToBitmap(mask)
        val scaledMaskBitmap = Bitmap.createScaledBitmap(
            maskBitmap, 
            mask[0].size * 2, 
            mask.size * 2, 
            false
        )
        canvas.drawBitmap(scaledMaskBitmap, 0f, 0f, null)
        
        var currentIndex = 0
        
        val runnable = object : Runnable {
            override fun run() {
                if (currentIndex < path.size - 1) {
                    val currentPoint = path[currentIndex]
                    val nextPoint = path[currentIndex + 1]
                    
                    // 创建画笔并为每段路径设置独立颜色
                    val paint = Paint().apply {
                        strokeWidth = 6f * 2 // 2倍放大，更粗的笔画
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        color = getColorForIndex(currentIndex, path.size) // 每段路径独立着色
                    }
                    
                    // 创建只包含当前段的路径
                    val segmentPath = Path()
                    segmentPath.moveTo(
                        currentPoint.x.toFloat() * 2 + 1f,
                        currentPoint.y.toFloat() * 2 + 1f
                    )
                    
                    // 检查当前点与下一个点是否相邻（包括对角线）
                    val dx = Math.abs(nextPoint.x - currentPoint.x)
                    val dy = Math.abs(nextPoint.y - currentPoint.y)
                    
                    // 移除相邻检查，始终绘制连线以确保"笔不离纸"效果
                    segmentPath.lineTo(
                        nextPoint.x.toFloat() * 2 + 1f,
                        nextPoint.y.toFloat() * 2 + 1f
                    )
                    
                    // 在画布上绘制这一段路径
                    canvas.drawPath(segmentPath, paint)
                    
                    // 显示当前绘制结果
                    binding.ivResult.setImageBitmap(bitmap)
                    
                    // 更新进度提示
                    if (currentIndex % 50 == 0 || currentIndex > path.size * 0.95) {
                        val progress = (currentIndex * 100 / path.size)
                        binding.tvProgress.text = "绘制进度: $progress%"
                    }
                    
                    currentIndex++
                    // 如果接近完成，增加绘制速度
                    if (currentIndex > path.size * 0.95) {
                        handler.postDelayed(this, 0) // 最后5%的点使用0ms间隔
                    } else {
                        handler.postDelayed(this, 1) // 1ms间隔，加快绘制速度
                    }
                } else {
                    // 绘制完成
                    // 显示最终结果
                    binding.ivResult.setImageBitmap(bitmap)
                    binding.tvProgress.visibility = View.GONE
                    
                    Toast.makeText(
                        context, 
                        "一口画路径绘制完成，共${path.size}个点", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        handler.post(runnable)
    }
    
    /**
     * 根据索引获取颜色（更丰富的彩虹色效果）
     */
    private fun getColorForIndex(index: Int, total: Int): Int {
        // 使用HSV颜色空间创建更平滑的彩虹效果
        val hue = (index % total).toFloat() / total.toFloat() * 360.0f
        return Color.HSVToColor(floatArrayOf(hue, 1.0f, 1.0f))
    }
    
    /**
     * 执行连接组件处理（加桥）
     */
    private fun performConnectComponents() {
        // 优先使用反色后的mask，如果没有则使用原始二值化mask
        val mask = invertedMask ?: binaryMask
        
        mask?.let { 
            // 在后台线程执行耗时操作
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "正在连接组件，请稍候...", Toast.LENGTH_SHORT).show()
                
                val (components, connectedMaskResult, bridges) = withContext(Dispatchers.IO) {
                    // 提取连通分量
                    val components = pathGenerator.extractComponents(it)
                    
                    var connectedMaskResult: Array<BooleanArray>? = null
                    var bridges: List<List<Point>> = emptyList()
                    
                    if (components.size > 1) {
                        // 连接组件
                        val result = pathGenerator.connectComponentsWithBridges(it, components)
                        connectedMaskResult = result.first
                        bridges = result.second
                    } else {
                        connectedMaskResult = it
                    }
                    
                    Triple(components, connectedMaskResult, bridges)
                }
                
                connectedMask = connectedMaskResult
                
                // 显示连接后的结果（放大显示以便更好地观察）
                val maskBitmap = pathGenerator.maskToBitmap(connectedMaskResult)
                val displayBitmap = createEnlargedDisplayBitmap(maskBitmap, 2) // 2倍放大显示
                binding.ivResult.setImageBitmap(displayBitmap)
                
                if (components.size > 1) {
                    Toast.makeText(context, "连接组件完成，添加了${bridges.size}座桥", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "图像已经是连通的，无需添加桥", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(context, "请先进行二值化处理", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 生成一口画路径
     */
    private fun generateOneStrokePath() {
        connectedMask?.let { mask ->
            // 在后台线程执行耗时操作
            CoroutineScope(Dispatchers.Main).launch {
                binding.tvProgress.visibility = View.VISIBLE
                binding.tvProgress.text = "正在生成一口画路径，请稍候..."
                
                pathResult = withContext(Dispatchers.IO) {
                    // 使用优化的路径生成算法，带进度回调
                    pathGenerator.buildOptimizedOneStrokePath(mask, progressCallback = object : OneStrokePathGenerator.ProgressCallback {
                        override fun onProgress(progress: Int, message: String) {
                            // 在主线程更新UI
                            CoroutineScope(Dispatchers.Main).launch {
                                binding.tvProgress.text = "生成路径中($progress%): $message"
                            }
                        }
                    })
                }
                
                binding.tvProgress.text = "正在动态绘制一口画路径，共${pathResult!!.path.size}个点..."
                // 动态显示绘制过程
                animatePathDrawing(pathResult!!.path, mask)
            }
        } ?: run {
            Toast.makeText(context, "请先进行连接组件处理", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 回收bitmap资源
        selectedBitmap?.recycle()
    }
}