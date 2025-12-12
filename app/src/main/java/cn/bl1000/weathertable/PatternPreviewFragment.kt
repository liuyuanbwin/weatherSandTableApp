package cn.bl1000.weathertable

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cn.bl1000.weathertable.databinding.FragmentPatternPreviewBinding
import kotlin.math.*

class PatternPreviewFragment : Fragment() {
    private var _binding: FragmentPatternPreviewBinding? = null
    private val binding get() = _binding!!
    
    private var patternType: Int = 0
    
    // 用于动画绘制的变量
    private val handler = Handler(Looper.getMainLooper())
    private var points: List<PointF>? = null
    private var currentIndex = 0
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    
    // 定义256*256的画板尺寸
    private val boardSize = 256
    
    data class PointF(val x: Float, val y: Float)
    
    companion object {
        const val TAG = "PatternPreviewFragment"
        
        fun newInstance(patternType: Int): PatternPreviewFragment {
            val fragment = PatternPreviewFragment()
            val args = Bundle()
            args.putInt("pattern_type", patternType)
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        patternType = arguments?.getInt("pattern_type") ?: 0
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPatternPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化bitmap和canvas
        bitmap = Bitmap.createBitmap(boardSize, boardSize, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        
        // 设置关闭按钮点击事件
        binding.btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // 开始绘制图案
        startDrawingPattern()
    }
    
    private fun startDrawingPattern() {
        when (patternType) {
            0 -> drawAnimatedSpiralPattern()
            1 -> drawAnimatedRasterScanPattern()
            2 -> drawAnimatedSierpinskiTriangle()
            3 -> drawAnimatedKochSnowflake()
            4 -> drawAnimatedFractalTree()
            else -> drawAnimatedSpiralPattern()
        }
    }
    
    private fun drawAnimatedSpiralPattern() {
        // 如果points还没有生成，则生成点集
        if (points == null) {
            points = generateSquareSpiralPoints()
            currentIndex = 0
            // 清空bitmap
            bitmap?.eraseColor(Color.WHITE)
            // 开始动画绘制
            animateDrawing()
        }
    }
    
    private fun drawAnimatedRasterScanPattern() {
        // 如果points还没有生成，则生成点集
        if (points == null) {
            points = generateRasterScanPoints()
            currentIndex = 0
            // 清空bitmap
            bitmap?.eraseColor(Color.WHITE)
            // 开始动画绘制
            animateDrawing()
        }
    }
    
    private fun drawAnimatedSierpinskiTriangle() {
        // 如果points还没有生成，则生成点集
        if (points == null) {
            points = generateSierpinskiTrianglePoints()
            currentIndex = 0
            // 清空bitmap
            bitmap?.eraseColor(Color.WHITE)
            // 开始动画绘制
            animateDrawing()
        }
    }
    
    private fun drawAnimatedKochSnowflake() {
        // 如果points还没有生成，则生成点集
        if (points == null) {
            points = generateKochSnowflakePoints()
            currentIndex = 0
            // 清空bitmap
            bitmap?.eraseColor(Color.WHITE)
            // 开始动画绘制
            animateDrawing()
        }
    }
    
    private fun drawAnimatedFractalTree() {
        // 如果points还没有生成，则生成点集
        if (points == null) {
            points = generateFractalTreePoints()
            currentIndex = 0
            // 清空bitmap
            bitmap?.eraseColor(Color.WHITE)
            // 开始动画绘制
            animateDrawing()
        }
    }
    
    private fun generateSquareSpiralPoints(): List<PointF> {
        val points = mutableListOf<PointF>()
        
        // 方形螺旋参数
        var x = boardSize / 2
        var y = boardSize / 2
        
        // 添加起始点
        points.add(PointF(x.toFloat(), y.toFloat()))
        
        // 步长，从1开始，每次增加2（因为是方形螺旋）
        var step = 1
        
        // 方向向量: 右(1,0) 下(0,1) 左(-1,0) 上(0,-1)
        val directions = arrayOf(
            intArrayOf(1, 0),   // 右
            intArrayOf(0, 1),   // 下
            intArrayOf(-1, 0),  // 左
            intArrayOf(0, -1)   // 上
        )
        var directionIndex = 0
        
        while (points.size < boardSize * boardSize) {
            // 每个step执行两次（除了第一次）
            for (i in 0 until 2) {
                val dx = directions[directionIndex][0]
                val dy = directions[directionIndex][1]
                
                // 沿当前方向走step步
                for (j in 0 until step) {
                    x += dx
                    y += dy
                    
                    // 检查边界
                    if (x >= 0 && x < boardSize && y >= 0 && y < boardSize) {
                        points.add(PointF(x.toFloat(), y.toFloat()))
                        
                        // 如果点数已满，直接退出
                        if (points.size >= boardSize * boardSize) {
                            break
                        }
                    }
                }
                
                // 改变方向
                directionIndex = (directionIndex + 1) % 4
                
                if (points.size >= boardSize * boardSize) {
                    break
                }
            }
            
            // 步长增加
            step++
        }
        
        return points
    }
    
    private fun generateRasterScanPoints(): List<PointF> {
        val points = mutableListOf<PointF>()
        
        val step = 4f  // 扫描线间距
        var y = step
        
        while (y < boardSize) {
            // 从左到右
            var x = 0f
            while (x < boardSize) {
                points.add(PointF(x, y))
                x += 1f
            }
            
            y += step
            
            // 从右到左
            x = boardSize - 1f
            while (x >= 0f) {
                points.add(PointF(x, y))
                x -= 1f
            }
            
            y += step
        }
        
        return points
    }
    
    private fun generateSierpinskiTrianglePoints(): List<PointF> {
        val points = mutableListOf<PointF>()
        
        // 谢尔宾斯基三角形的三个顶点
        val vertices = arrayOf(
            PointF(boardSize / 2f, 10f),           // 顶点
            PointF(10f, boardSize - 10f),          // 左下角
            PointF(boardSize - 10f, boardSize - 10f) // 右下角
        )
        
        // 初始点随机选择
        var currentPoint = PointF(boardSize / 2f, boardSize / 2f)
        points.add(currentPoint)
        
        // 迭代生成点
        for (i in 0 until 20000) {
            // 随机选择一个顶点
            val vertex = vertices[(0..2).random()]
            
            // 计算中点
            val newX = (currentPoint.x + vertex.x) / 2
            val newY = (currentPoint.y + vertex.y) / 2
            
            currentPoint = PointF(newX, newY)
            points.add(currentPoint)
        }
        
        return points
    }
    
    private fun generateKochSnowflakePoints(): List<PointF> {
        val points = mutableListOf<PointF>()
        
        // 初始三角形的三个顶点
        val initialPoints = listOf(
            PointF(boardSize / 2f, 20f),
            PointF(20f, boardSize - 20f),
            PointF(boardSize - 20f, boardSize - 20f),
            PointF(boardSize / 2f, 20f) // 回到起点封闭图形
        )
        
        // 生成科赫雪花曲线
        var currentPoints = initialPoints
        for (iteration in 0 until 3) {
            val newPoints = mutableListOf<PointF>()
            for (i in 0 until currentPoints.size - 1) {
                val p1 = currentPoints[i]
                val p2 = currentPoints[i + 1]
                
                // 添加第一个点
                newPoints.add(p1)
                
                // 计算四个分割点
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                
                // 添加科赫曲线的中间三个点
                newPoints.add(PointF(p1.x + dx / 3, p1.y + dy / 3))
                newPoints.add(PointF(p1.x + dx / 2 - dy / (2 * sqrt(3.0)).toFloat(), 
                                   p1.y + dy / 2 + dx / (2 * sqrt(3.0)).toFloat()))
                newPoints.add(PointF(p1.x + 2 * dx / 3, p1.y + 2 * dy / 3))
            }
            newPoints.add(currentPoints.last())
            currentPoints = newPoints
        }
        
        return currentPoints
    }
    
    private fun generateFractalTreePoints(): List<PointF> {
        val points = mutableListOf<PointF>()
        
        // 绘制分形树
        drawTree(points, boardSize / 2f, boardSize - 20f, -90f, 80f, 8)
        
        return points
    }
    
    private fun drawTree(
        points: MutableList<PointF>,
        x: Float,
        y: Float,
        angle: Float,
        length: Float,
        depth: Int
    ) {
        if (depth == 0) return
        
        // 计算终点
        val endX = x + length * cos(Math.toRadians(angle.toDouble())).toFloat()
        val endY = y + length * sin(Math.toRadians(angle.toDouble())).toFloat()
        
        // 添加线段
        points.add(PointF(x, y))
        points.add(PointF(endX, endY))
        
        // 递归绘制左右子树
        drawTree(points, endX, endY, angle - 30, length * 0.7f, depth - 1)
        drawTree(points, endX, endY, angle + 30, length * 0.7f, depth - 1)
    }
    
    private fun animateDrawing() {
        if (points == null || currentIndex >= points!!.size - 1) return
        
        val currentPoint = points!![currentIndex]
        val nextPoint = points!![currentIndex + 1]
        
        // 创建画笔，使用与一笔画相同的颜色计算方式
        val drawPaint = Paint().apply {
            color = getColorForIndex(currentIndex, points!!.size)
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        // 创建路径片段
        val segmentPath = Path().apply {
            moveTo(currentPoint.x, currentPoint.y)
            lineTo(nextPoint.x, nextPoint.y)
        }
        
        // 在bitmap上绘制这一段路径
        canvas?.drawPath(segmentPath, drawPaint)
        
        // 更新ImageView
        binding.imageView.setImageBitmap(bitmap)
        
        currentIndex++
        
        // 继续动画
        handler.postDelayed({
            animateDrawing()
        }, 1) // 1ms延迟，形成流畅的动画效果
    }
    
    /**
     * 根据索引获取颜色（彩虹色效果）
     * 修改算法使颜色变化更加明显
     */
    private fun getColorForIndex(index: Int, total: Int): Int {
        // 使用更明显的颜色变化算法
        // 增加倍数使颜色变化更明显，每100个点完成一次完整的彩虹循环
        val hue = (index * 360.0f / 100.0f) % 360.0f
        return Color.HSVToColor(floatArrayOf(hue, 1.0f, 1.0f))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // 清理资源
        handler.removeCallbacksAndMessages(null)
        bitmap?.recycle()
    }
}