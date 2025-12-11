package cn.bl1000.weathertable

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class PatternDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 全屏显示
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()
        
        val patternType = intent.getIntExtra("pattern_type", 0)
        val patternView = PatternView(this, patternType)
        setContentView(patternView)
    }
}

class PatternView(context: Context, private val patternType: Int) : View(context) {
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    // 用于动画绘制的变量
    private val handler = Handler(Looper.getMainLooper())
    private var points: List<PointF>? = null
    private var currentIndex = 0
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    
    // 定义256*256的画板尺寸
    private val boardSize = 256
    
    data class PointF(val x: Float, val y: Float)
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        
        // 初始化bitmap和canvas（仅一次）
        if (this.bitmap == null) {
            this.bitmap = Bitmap.createBitmap(boardSize, boardSize, Bitmap.Config.ARGB_8888)
            this.canvas = Canvas(this.bitmap!!)
        }
        
        when (patternType) {
            0 -> drawAnimatedSpiralPattern()
            1 -> drawAnimatedRasterScanPattern()
            else -> drawAnimatedSpiralPattern()
        }
        
        // 绘制bitmap到主canvas
        if (bitmap != null) {
            // 居中显示在屏幕中
            val scale = minOf(width.toFloat() / boardSize, height.toFloat() / boardSize)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, 
                (boardSize * scale).toInt(), 
                (boardSize * scale).toInt(), 
                false)
            
            val left = (width - scaledBitmap.width) / 2f
            val top = (height - scaledBitmap.height) / 2f
            
            canvas.drawBitmap(scaledBitmap, left, top, Paint())
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
        
        // 强制重绘
        invalidate()
        
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
}