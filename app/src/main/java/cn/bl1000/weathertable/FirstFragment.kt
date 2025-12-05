package cn.bl1000.weathertable

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import cn.bl1000.weathertable.ble.BLEManager
import cn.bl1000.weathertable.databinding.FragmentFirstBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import android.bluetooth.BluetoothDevice
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import cn.bl1000.weathertable.OneStrokePathGenerator


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var imageView: ImageView
    private lateinit var processedImageView: ImageView
    private var currentPhotoPath: String = ""
    private var selectedBitmap: Bitmap? = null
    private var currentRotation: Float = 0f
    private var processedBitmap: Bitmap? = null
    private lateinit var bleManager: BLEManager
    private var currentMtu: Int = BLEManager.DEFAULT_MTU
    private lateinit var pathGenerator: OneStrokePathGenerator

    companion object {
        private const val TAG = "FirstFragment"
        private const val COMMAND_START = 0x01.toByte()
        private const val COMMAND_DATA = 0x02.toByte()
        private const val COMMAND_END = 0x03.toByte()
        private const val SEND_DELAY_MS = 5L // 减少发送延迟以提高速度
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = binding.imageDisplay
        processedImageView = binding.processedImageView
        
        // 初始化BLE管理器
        bleManager = BLEManager.getInstance(requireContext())
        // 初始化一笔画路径生成器
        pathGenerator = OneStrokePathGenerator()

        binding.selectImageButton.setOnClickListener {
            showImagePickerOptions()
        }

        binding.selectImageButton.setOnClickListener {
            showImagePickerOptions()
        }

        binding.processImageButton.setOnClickListener {
            processSelectedImage()
        }
        
        binding.rotateButton.setOnClickListener {
            rotateImage()
        }
        
        binding.cropButton.setOnClickListener {
            cropImage()
        }
        
        binding.sendBleButton.setOnClickListener {
            sendImageViaBLE()
        }
        
        // 添加一键画路径生成功能按钮
        binding.generatePathButton.setOnClickListener {
            generateOneStrokePath()
        }
    }

    private fun showImagePickerOptions() {
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 请求相机权限
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            openImagePicker()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(context, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImagePicker() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        
        // 创建临时文件用于保存照片
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            null
        }
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                it
            )
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        }

        val chooserIntent = Intent.createChooser(galleryIntent, "选择图片来源")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        
        imagePickerLauncher.launch(chooserIntent)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = requireContext().getExternalFilesDir(null)
        val image = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )
        currentPhotoPath = image.absolutePath
        return image
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data != null) {
                // 从相册选择图片
                val selectedImageUri: Uri? = data.data
                selectedImageUri?.let {
                    displayImage(it)
                }
            } else if (currentPhotoPath.isNotEmpty()) {
                // 拍照获取图片
                val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
                selectedBitmap = bitmap
                imageView.setImageBitmap(bitmap)
                binding.editButtonsLayout.visibility = View.VISIBLE
                binding.processImageButton.visibility = View.VISIBLE
                binding.generatePathButton.visibility = View.VISIBLE
                currentRotation = 0f
            }
        }
    }

    private fun displayImage(imageUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            selectedBitmap = bitmap
            imageView.setImageBitmap(bitmap)
            binding.editButtonsLayout.visibility = View.VISIBLE
            binding.processImageButton.visibility = View.VISIBLE
            binding.generatePathButton.visibility = View.VISIBLE
            
            // 如果之前处理过图片，隐藏处理后的图片
            processedImageView.visibility = View.GONE
            binding.sendBleButton.visibility = View.GONE
            binding.sendProgress.visibility = View.GONE
            processedBitmap = null
            
            // 显示原始图片
            imageView.visibility = View.VISIBLE
            
            // 重置旋转角度
            currentRotation = 0f
        } catch (e: Exception) {
            Toast.makeText(context, "无法加载图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotateImage() {
        selectedBitmap?.let { originalBitmap ->
            currentRotation = (currentRotation + 90) % 360
            
            val matrix = Matrix()
            matrix.postRotate(currentRotation)
            
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )
            
            selectedBitmap = rotatedBitmap
            imageView.setImageBitmap(rotatedBitmap)
        }
    }

    private fun cropImage() {
        selectedBitmap?.let { originalBitmap ->
            try {
                // 使用自定义裁剪方法
                performSimpleCrop()
            } catch (e: Exception) {
                Toast.makeText(context, "裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun performSimpleCrop() {
        selectedBitmap?.let { originalBitmap ->
            try {
                // 简单的中心裁剪实现
                val width = originalBitmap.width
                val height = originalBitmap.height
                val size = min(width, height)
                val x = (width - size) / 2
                val y = (height - size) / 2
                
                val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
                selectedBitmap = croppedBitmap
                imageView.setImageBitmap(croppedBitmap)
                Toast.makeText(context, "已完成中心裁剪", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "裁剪失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 重新加载裁剪后的图片
            selectedBitmap?.recycle()
            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            selectedBitmap = bitmap
            imageView.setImageBitmap(bitmap)
            currentRotation = 0f
        }
    }
    
    private fun getImageUri(bitmap: Bitmap): Uri {
        val file = createTempImageFile()
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()
        return Uri.fromFile(file)
    }
    
    private fun createTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().cacheDir
        return File.createTempFile(
            "TEMP_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun processSelectedImage() {
        selectedBitmap?.let { originalBitmap ->
            // 隐藏原始图片和处理按钮
            imageView.visibility = View.GONE
            binding.editButtonsLayout.visibility = View.GONE
            binding.processImageButton.visibility = View.GONE
            binding.generatePathButton.visibility = View.GONE
            
            // 严格按照128*128分辨率处理
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 128, 128, true)
            
            // 创建一个更大尺寸的Bitmap用于显示，保持128*128的点数但进一步放大显示
            val displayWidth = 128 * 8  // 1024 pixels wide
            val displayHeight = 128 * 8 // 1024 pixels high
            val displayBitmap = Bitmap.createBitmap(displayWidth, displayHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(displayBitmap)
            val paint = Paint()
            paint.isAntiAlias = true // 开启抗锯齿以获得平滑的圆形
            
            // 计算每个点在显示区域中的大小，让圆点更大并减少间隙
            val cellWidth = displayWidth.toFloat() / 128  // 每个点的宽度
            val cellHeight = displayHeight.toFloat() / 128 // 每个点的高度
            val circleRadius = minOf(cellWidth, cellHeight) / 2 * 0.95f // 增大圆的半径，几乎填满格子
            
            // 绘制128*128个点，每个点放大显示
            for (y in 0 until 128) {
                for (x in 0 until 128) {
                    // 获取该位置的颜色
                    val pixel = scaledBitmap.getPixel(x, y)
                    paint.color = pixel
                    
                    // 计算圆心位置（在每个格子的中心）
                    val cx = x * cellWidth + cellWidth / 2
                    val cy = y * cellHeight + cellHeight / 2
                    
                    // 绘制圆点
                    canvas.drawCircle(cx, cy, circleRadius, paint)
                }
            }
            
            // 保存处理后的图片用于发送（使用128*128的原始数据）
            processedBitmap = scaledBitmap
            
            // 显示处理后的图片
            processedImageView.setImageBitmap(displayBitmap)
            processedImageView.visibility = View.VISIBLE
            
            // 显示发送按钮
            binding.sendBleButton.visibility = View.VISIBLE
        }
    }
    
    /**
     * 生成一笔画路径
     */
    private fun generateOneStrokePath() {
        // 生成一个示例emoji mask
        val mask = pathGenerator.emojiToMask("❄", 128)
        
        if (mask != null) {
            // 生成一笔画路径
            val result = pathGenerator.buildOneStrokeZigzagPathWithAutoBridges(mask)
            
            // 显示mask
            val maskBitmap = pathGenerator.maskToBitmap(result.mask)
            imageView.setImageBitmap(maskBitmap)
            imageView.visibility = View.VISIBLE
            
            // 显示路径
            val pathBitmap = pathGenerator.pathToBitmap(result.path, 128, 128)
            processedImageView.setImageBitmap(pathBitmap)
            processedImageView.visibility = View.VISIBLE
            
            // 显示按钮
            binding.sendBleButton.visibility = View.VISIBLE
            
            Toast.makeText(context, "生成了一笔画路径，共${result.path.size}个点", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "无法生成emoji mask", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendImageViaBLE() {
        // 检查BLE连接状态
        if (!bleManager.isConnectedToDevice(bleManager.connectedDevice)) {
            Toast.makeText(context, "BLE设备未连接，请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }
        
        processedBitmap?.let { bitmap ->
            // 显示进度条
            binding.sendProgress.visibility = View.VISIBLE
            binding.sendProgress.progress = 0
            binding.sendBleButton.isEnabled = false
            
            // 开始发送图片数据
            sendImageData(bitmap)
        } ?: run {
            Toast.makeText(context, "没有可发送的图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendImageData(bitmap: Bitmap) {
        Thread {
            try {
                // 1. 请求交换MTU大小以提高传输速度
                val mtuFuture = bleManager.requestMtu(BLEManager.MAX_MTU)
                currentMtu = mtuFuture.get(5, TimeUnit.SECONDS)
                Log.d(TAG, "协商MTU完成: $currentMtu")
                
                // 2. 发送开始命令（不包含宽高信息，因为是固定的128*128）
                val startPacket = byteArrayOf(COMMAND_START)
                
                val startFuture = bleManager.sendDataAsync(BLEManager.SERVICE_UUID, BLEManager.CHARACTERISTIC_UUID, startPacket)
                val startResult = startFuture.get(5, TimeUnit.SECONDS) // 等待5秒
                
                if (!startResult) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "发送开始命令失败", Toast.LENGTH_SHORT).show()
                        resetSendUI()
                    }
                    return@Thread
                }
                
                // 等待一小段时间确保设备准备好接收数据
                Thread.sleep(50) // 减少等待时间
                
                // 3. 发送图像数据
                var totalSent = 0
                val totalPixels = bitmap.width * bitmap.height
                val pixels = IntArray(totalPixels)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                
                // 根据MTU计算每个数据包可以发送的像素数
                // MTU - 1字节命令 - 3字节ATT开销
                val maxPixelsPerPacket = (currentMtu - 4) / 3
                Log.d(TAG, "每个数据包最多可发送像素数: $maxPixelsPerPacket")
                
                // 分批发送数据
                var i = 0
                while (i < totalPixels) {
                    // 计算本次发送的数据量
                    val remainingPixels = totalPixels - i
                    val pixelsToSend = minOf(remainingPixels, maxPixelsPerPacket)
                    
                    // 构造数据包
                    val packetSize = 1 + pixelsToSend * 3 // 1字节命令 + RGB数据
                    val packet = ByteArray(packetSize)
                    packet[0] = COMMAND_DATA
                    
                    // 填充RGB数据
                    for (j in 0 until pixelsToSend) {
                        val pixel = pixels[i + j]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        
                        packet[1 + j * 3] = r.toByte()
                        packet[1 + j * 3 + 1] = g.toByte()
                        packet[1 + j * 3 + 2] = b.toByte()
                    }
                    
                    // 发送数据包
                    val dataFuture = bleManager.sendDataAsync(BLEManager.SERVICE_UUID, BLEManager.CHARACTERISTIC_UUID, packet)
                    val dataResult = dataFuture.get(5, TimeUnit.SECONDS) // 等待5秒
                    
                    if (!dataResult) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(context, "发送图像数据失败", Toast.LENGTH_SHORT).show()
                            resetSendUI()
                        }
                        return@Thread
                    }
                    
                    // 更新进度
                    totalSent += pixelsToSend
                    val progress = (totalSent * 100) / totalPixels
                    requireActivity().runOnUiThread {
                        binding.sendProgress.progress = progress
                    }
                    
                    i += pixelsToSend
                    
                    // 控制发送速度，避免缓冲区溢出
                    if (i % 10 == 0) { // 每10个包等待一次
                        Thread.sleep(SEND_DELAY_MS)
                    }
                }
                
                // 4. 发送结束命令
                val endPacket = byteArrayOf(COMMAND_END)
                val endFuture = bleManager.sendDataAsync(BLEManager.SERVICE_UUID, BLEManager.CHARACTERISTIC_UUID, endPacket)
                val endResult = endFuture.get(5, TimeUnit.SECONDS) // 等待5秒
                
                if (!endResult) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "发送结束命令失败", Toast.LENGTH_SHORT).show()
                        resetSendUI()
                    }
                    return@Thread
                }
                
                // 发送完成
                requireActivity().runOnUiThread {
                    binding.sendProgress.progress = 100
                    Toast.makeText(context, "图片发送完成，使用MTU: $currentMtu", Toast.LENGTH_SHORT).show()
                    resetSendUI()
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送图片数据时出错", e)
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "发送图片数据时出错: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetSendUI()
                }
            }
        }.start()
    }
    
    private fun resetSendUI() {
        binding.sendBleButton.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 回收bitmap资源
        selectedBitmap?.recycle()
        processedBitmap?.recycle()
    }
}