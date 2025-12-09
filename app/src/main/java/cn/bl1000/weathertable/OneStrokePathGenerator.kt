package cn.bl1000.weathertable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.get
import androidx.core.graphics.set
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 一笔画路径生成器
 * 将图像转换为可以一笔画完成的路径
 */
class OneStrokePathGenerator {
    
    /**
     * 点数据类
     */
    data class Point(val x: Int, val y: Int)
    
    /**
     * 路径结果数据类
     */
    data class PathResult(
        val path: List<Point>,
        val mask: Array<BooleanArray>,
        val bridges: List<List<Point>>
    )
    
    companion object {
        private val NEIGHBORS = arrayOf(
            intArrayOf(1, 0), 
            intArrayOf(-1, 0), 
            intArrayOf(0, 1), 
            intArrayOf(0, -1)
        )
        
        private fun inBounds(x: Int, y: Int, width: Int, height: Int): Boolean {
            return x >= 0 && x < width && y >= 0 && y < height
        }
    }

    /**
     * 从Emoji生成Mask
     */
    fun emojiToMask(emoji: String, size: Int = 128): Array<BooleanArray>? {
        // 在Android上我们不能直接使用PIL库，所以这里简化实现
        // 实际项目中可能需要引入专门的字体渲染库
        
        // 创建一个简单的示例mask（模拟雪花形状）
        val mask = Array(size) { BooleanArray(size) }
        
        // 简化的雪花图案
        val centerX = size / 2
        val centerY = size / 2
        val maxSize = size / 2 - 5
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toInt()
                
                // 创建一个简单的雪花形状
                if (distance < maxSize) {
                    // 主圆盘
                    if (distance < maxSize * 0.8) {
                        mask[y][x] = true
                    }
                    // 六角分支
                    else if ((abs(dx) > maxSize * 0.6 || abs(dy) > maxSize * 0.6) && 
                             (abs(dx) < maxSize * 0.9 && abs(dy) < maxSize * 0.9)) {
                        mask[y][x] = true
                    }
                }
            }
        }
        
        return mask
    }

    /**
     * 提取连通分量
     */
    fun extractComponents(mask: Array<BooleanArray>): List<List<Point>> {
        val height = mask.size
        val width = mask[0].size
        val visited = Array(height) { BooleanArray(width) }
        val components = mutableListOf<List<Point>>()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x] && !visited[y][x]) {
                    val queue = ArrayDeque<Point>()
                    queue.add(Point(x, y))
                    visited[y][x] = true
                    val pixels = mutableListOf<Point>()
                    
                    while (queue.isNotEmpty()) {
                        val current = queue.removeFirst()
                        pixels.add(current)
                        
                        for (neighbor in NEIGHBORS) {
                            val nx = current.x + neighbor[0]
                            val ny = current.y + neighbor[1]
                            
                            if (inBounds(nx, ny, width, height) && 
                                mask[ny][nx] && !visited[ny][nx]) {
                                visited[ny][nx] = true
                                queue.add(Point(nx, ny))
                            }
                        }
                    }
                    
                    components.add(pixels)
                }
            }
        }
        
        return components
    }
    
    /**
     * 连接组件：贪心最近桥
     */
    fun connectComponentsWithBridges(
        mask: Array<BooleanArray>, 
        components: List<List<Point>>,
        bridgeWidth: Int = 1
    ): Pair<Array<BooleanArray>, List<List<Point>>> {
        val height = mask.size
        val width = mask[0].size
        
        // 如果只有一个组件，则无需连接
        if (components.size <= 1) {
            return Pair(mask, emptyList())
        }
        
        // 复制mask
        val maskCopy = Array(height) { y -> BooleanArray(width) { x -> mask[y][x] } }
        
        // 构建初始集合：将组件视为节点，我们将以类似于Prim的方式贪婪地连接它们
        val remaining = mutableSetOf<Int>()
        for (i in components.indices) {
            remaining.add(i)
        }
        
        val connected = mutableSetOf(remaining.first())
        remaining.remove(remaining.first())
        
        val bridges = mutableListOf<List<Point>>()
        
        // 预计算组件边界框（可选加速）
        val bboxes = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>() // minX to minY, maxX to maxY
        for (component in components) {
            if (component.isEmpty()) continue
            
            var minX = component[0].x
            var minY = component[0].y
            var maxX = component[0].x
            var maxY = component[0].y
            
            for (point in component) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }
            
            bboxes.add(Pair(Pair(minX, minY), Pair(maxX, maxY)))
        }
        
        // 贪婪循环：当存在剩余组件时，找到最近的一对（a在connected中，b在remaining中）
        while (remaining.isNotEmpty()) {
            var best: Triple<Double, Int, Int>? = null // distance, ai, bi
            var bestPoints: Pair<Point, Point>? = null // pointA, pointB
            
            for (ai in connected) {
                val aComponent = components[ai]
                val (aMin, aMax) = bboxes[ai]
                
                for (bi in remaining) {
                    val bComponent = components[bi]
                    val (bMin, bMax) = bboxes[bi]
                    
                    // 边界框下界
                    var dx = 0
                    if (aMax.first < bMin.first) {
                        dx = bMin.first - aMax.first
                    } else if (bMax.first < aMin.first) {
                        dx = aMin.first - bMax.first
                    }
                    
                    var dy = 0
                    if (aMax.second < bMin.second) {
                        dy = bMin.second - aMax.second
                    } else if (bMax.second < aMin.second) {
                        dy = aMin.second - bMax.second
                    }
                    
                    val lb = dx * dx + dy * dy
                    
                    // 如果我们已经有了一个更小下界的最佳值，则跳过昂贵的检查
                    if (best != null && lb >= best.first) {
                        continue
                    }
                    
                    // 蛮力计算A和B之间的最近点对
                    var minDistance = Double.MAX_VALUE
                    var bestA: Point? = null
                    var bestB: Point? = null
                    
                    for (pointA in aComponent) {
                        for (pointB in bComponent) {
                            val distance = (pointA.x - pointB.x) * (pointA.x - pointB.x) + 
                                         (pointA.y - pointB.y) * (pointA.y - pointB.y)
                            
                            if (distance < minDistance) {
                                minDistance = distance.toDouble()
                                bestA = pointA
                                bestB = pointB
                            }
                        }
                    }
                    
                    if (bestA != null && bestB != null && 
                        (best == null || minDistance < best.first)) {
                        best = Triple(minDistance, ai, bi)
                        bestPoints = Pair(bestA, bestB)
                    }
                }
            }
            
            if (best == null || bestPoints == null) {
                break
            }
            
            val (_, ai, bi) = best
            val (pointA, pointB) = bestPoints
            
            // 绘制桥接（两点之间的直线）
            val line = bresenhamLine(pointA.x, pointA.y, pointB.x, pointB.y)
            
            // 可选择加宽桥梁
            for ((lx, ly) in line) {
                for (ox in -bridgeWidth / 2..bridgeWidth / 2) {
                    for (oy in -bridgeWidth / 2..bridgeWidth / 2) {
                        val nx = lx + ox
                        val ny = ly + oy
                        
                        if (inBounds(nx, ny, width, height)) {
                            maskCopy[ny][nx] = true
                        }
                    }
                }
            }
            
            bridges.add(line)
            
            // 合并：将bi从未连接移动到已连接
            connected.add(bi)
            remaining.remove(bi)
        }
        
        return Pair(maskCopy, bridges)
    }
    
    /**
     * Bresenham线算法（两点之间的整数像素列表）
     */
    private fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int): List<Point> {
        val points = mutableListOf<Point>()
        var dx = abs(x1 - x0)
        var dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var x = x0
        var y = y0
        
        while (true) {
            points.add(Point(x, y))
            if (x == x1 && y == y1) {
                break
            }
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
        
        return points
    }
    
    /**
     * 计算组件的最佳方向（使用主成分分析PCA的思想简化实现）
     */
    fun bestDirectionForComponent(points: List<Point>): Pair<Double, Double> {
        if (points.size < 3) {
            return Pair(1.0, 0.0)
        }
        
        // 计算点集的协方差矩阵并找到主成分方向
        var sumX = 0.0
        var sumY = 0.0
        for (point in points) {
            sumX += point.x
            sumY += point.y
        }
        
        val meanX = sumX / points.size
        val meanY = sumY / points.size
        
        var covXX = 0.0
        var covXY = 0.0
        var covYY = 0.0
        
        for (point in points) {
            val dx = point.x - meanX
            val dy = point.y - meanY
            covXX += dx * dx
            covXY += dx * dy
            covYY += dy * dy
        }
        
        covXX /= points.size
        covXY /= points.size
        covYY /= points.size
        
        // 计算特征值和特征向量（简化版PCA）
        val trace = covXX + covYY
        val det = covXX * covYY - covXY * covXY
        val discriminant = sqrt(trace * trace - 4 * det)
        val lambda1 = (trace + discriminant) / 2
        // val lambda2 = (trace - discriminant) / 2
        
        // 计算对应于最大特征值的特征向量
        var vx = covXY
        var vy = lambda1 - covXX
        
        // 归一化向量
        val norm = sqrt(vx * vx + vy * vy)
        if (norm == 0.0) {
            return Pair(1.0, 0.0)
        }
        
        return Pair(vx / norm, vy / norm)
    }
    
    /**
     * 优化的Zigzag排序 - 减少折返和重复
     */
    fun optimizedZigzagOrder(points: List<Point>, direction: Pair<Double, Double>, spacing: Int = 3): List<Point> {
        // 将点转换为浮点型以进行计算
        val pts = points.map { Pair(it.x.toDouble(), it.y.toDouble()) }
        val (dx, dy) = direction
        
        // 计算法向量
        var normal = Pair(-dy, dx)
        val nrm = sqrt(normal.first * normal.first + normal.second * normal.second)
        
        if (nrm == 0.0) {
            normal = Pair(0.0, 1.0)
        } else {
            normal = Pair(normal.first / nrm, normal.second / nrm)
        }
        
        // 投影点到法向量上
        val projections = pts.map { it.first * normal.first + it.second * normal.second }
        val lo = projections.minOrNull() ?: 0.0
        val hi = projections.maxOrNull() ?: 0.0
        
        // 创建层结构，按投影值分组
        val layers = mutableMapOf<Int, MutableList<Pair<Double, Double>>>()
        for (i in pts.indices) {
            val p = pts[i]
            val proj = projections[i]
            val layerIndex = ((proj - lo) / spacing).toInt()
            if (layerIndex >= 0) {
                if (!layers.containsKey(layerIndex)) {
                    layers[layerIndex] = mutableListOf()
                }
                layers[layerIndex]!!.add(p)
            }
        }
        
        // 对每层内的点按主方向排序
        val sortedLayers = mutableMapOf<Int, List<Pair<Double, Double>>>()
        for ((layerIndex, layerPts) in layers) {
            val orderValues = layerPts.map { it.first * dx + it.second * dy }
            val sortedIndices = orderValues.indices.sortedBy { orderValues[it] }
            sortedLayers[layerIndex] = sortedIndices.map { layerPts[it] }
        }
        
        // 按层顺序收集点，交替方向以减少折返
        val result = mutableListOf<Pair<Double, Double>>()
        var flip = false
        
        val sortedLayerKeys = layers.keys.sorted()
        for (layerIndex in sortedLayerKeys) {
            val layerPts = sortedLayers[layerIndex] ?: continue
            val orderedLayer = if (flip) layerPts.reversed() else layerPts
            result.addAll(orderedLayer)
            flip = !flip
        }
        
        // 去重并保持顺序
        val seen = mutableSetOf<Pair<Int, Int>>()
        val sequence = mutableListOf<Point>()
        
        for (p in result) {
            val intPoint = Point(p.first.toInt(), p.second.toInt())
            val key = Pair(intPoint.x, intPoint.y)
            
            // 不再去重，允许重复访问点
            sequence.add(intPoint)
        }
        
        return sequence
    }
    
    /**
     * BFS最短路径算法
     */
    fun bfsShortestPath(mask: Array<BooleanArray>, start: Point, end: Point): List<Point>? {
        if (start == end) return listOf(start)
        
        val height = mask.size
        val width = mask[0].size
        
        val queue = ArrayDeque<Point>()
        queue.add(start)
        
        val previous = mutableMapOf<Point, Point?>()
        previous[start] = null
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            
            for (neighbor in NEIGHBORS) {
                val nx = current.x + neighbor[0]
                val ny = current.y + neighbor[1]
                
                if (inBounds(nx, ny, width, height) && 
                    mask[ny][nx] && 
                    Point(nx, ny) !in previous) {
                    
                    previous[Point(nx, ny)] = current
                    
                    if (nx == end.x && ny == end.y) {
                        // 回溯路径
                        val path = mutableListOf<Point>()
                        var cur: Point? = Point(nx, ny)
                        
                        while (cur != null) {
                            path.add(cur)
                            cur = previous[cur]
                        }
                        
                        return path.reversed()
                    }
                    
                    queue.add(Point(nx, ny))
                }
            }
        }
        
        return null
    }
    
    /**
     * 构建空图
     */
    fun buildEmptyGraph(mask: Array<BooleanArray>): MutableMap<Point, MutableList<Point>> {
        val graph = mutableMapOf<Point, MutableList<Point>>()
        
        for (y in mask.indices) {
            for (x in mask[y].indices) {
                if (mask[y][x]) {
                    graph[Point(x, y)] = mutableListOf()
                }
            }
        }
        
        return graph
    }
    
    /**
     * 添加优化的Zigzag到图中 - 减少不必要的连接
     */
    fun addOptimizedZigzagToGraph(
        graph: MutableMap<Point, MutableList<Point>>,
        mask: Array<BooleanArray>,
        sequence: List<Point>
    ) {
        // 直接按顺序连接点，避免不必要的BFS搜索
        for (i in 0 until sequence.size - 1) {
            val a = sequence[i]
            val b = sequence[i + 1]
            
            // 检查两点是否相邻
            val dx = abs(a.x - b.x)
            val dy = abs(a.y - b.y)
            
            // 如果相邻则直接连接
            if (dx <= 1 && dy <= 1 && (dx + dy > 0)) {
                if (b !in graph[a]!!) {
                    graph[a]!!.add(b)
                }
                if (a !in graph[b]!!) {
                    graph[b]!!.add(a)
                }
            } else {
                // 如果不相邻，使用BFS找到最短路径
                val path = bfsShortestPath(mask, a, b)
                if (path != null) {
                    for (j in 0 until path.size - 1) {
                        val u = path[j]
                        val v = path[j + 1]
                        if (v !in graph[u]!!) {
                            graph[u]!!.add(v)
                        }
                        if (u !in graph[v]!!) {
                            graph[v]!!.add(u)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 欧拉图化
     */
    fun eulerizeGraph(
        graph: MutableMap<Point, MutableList<Point>>,
        mask: Array<BooleanArray>
    ) {
        val oddVertices = graph.filter { it.value.size % 2 == 1 }.keys.toList()
        
        // 贪心配对
        val remainingOdd = oddVertices.toMutableList()
        
        while (remainingOdd.isNotEmpty()) {
            val a = remainingOdd.removeAt(0)
            
            // BFS查找最近的奇度顶点
            val height = mask.size
            val width = mask[0].size
            
            val queue = ArrayDeque<Point>()
            queue.add(a)
            
            val previous = mutableMapOf<Point, Point?>()
            previous[a] = null
            var foundB: Point? = null
            
            while (queue.isNotEmpty() && foundB == null) {
                val current = queue.removeFirst()
                
                for (neighbor in NEIGHBORS) {
                    val nx = current.x + neighbor[0]
                    val ny = current.y + neighbor[1]
                    
                    if (inBounds(nx, ny, width, height) && 
                        mask[ny][nx] && 
                        Point(nx, ny) !in previous) {
                        
                        previous[Point(nx, ny)] = current
                        
                        if (Point(nx, ny) in remainingOdd) {
                            foundB = Point(nx, ny)
                            break
                        }
                        
                        queue.add(Point(nx, ny))
                    }
                }
            }
            
            if (foundB != null) {
                // 回溯路径
                val path = mutableListOf<Point>()
                var cur: Point? = foundB
                
                while (cur != null) {
                    path.add(cur)
                    cur = previous[cur]
                }
                
                path.reverse()
                
                for (i in 0 until path.size - 1) {
                    val u = path[i]
                    val v = path[i + 1]
                    if (v !in graph[u]!!) {
                        graph[u]!!.add(v)
                    }
                    if (u !in graph[v]!!) {
                        graph[v]!!.add(u)
                    }
                }
                
                // 从剩余奇度顶点中移除找到的b
                remainingOdd.removeAll { it == foundB }
            }
        }
    }
    
    /**
     * Hierholzer算法
     */
    fun hierholzer(graph: Map<Point, List<Point>>, start: Point): List<Point> {
        val g = graph.mapValues { it.value.toMutableList() }.toMutableMap()
        val stack = mutableListOf(start)
        val circuit = mutableListOf<Point>()
        
        while (stack.isNotEmpty()) {
            val v = stack.last()
            
            if (g[v]?.isNotEmpty() == true) {
                val u = g[v]!!.removeAt(0)
                g[u]?.removeIf { it == v }
                stack.add(u)
            } else {
                circuit.add(v)
                stack.removeLast()
            }
        }
        
        return circuit.reversed()
    }
    
    /**
     * 构建带自动桥梁的一笔画优化Zigzag路径
     */
    fun buildOptimizedOneStrokePath(
        mask: Array<BooleanArray>,
        spacing: Int = 3,
        bridgeWidth: Int = 1
    ): PathResult {
        // 提取组件
        val components = extractComponents(mask)
        
        val finalMask: Array<BooleanArray>
        val bridges: List<List<Point>>
        
        if (components.size > 1) {
            // 自动连接组件与最小桥梁
            val (maskCopy, bridgeList) = connectComponentsWithBridges(mask, components, bridgeWidth)
            finalMask = maskCopy
            bridges = bridgeList
        } else {
            finalMask = mask
            bridges = emptyList()
        }
        
        val finalComponents = extractComponents(finalMask)
        val graph = buildEmptyGraph(finalMask)
        
        // 使用优化的Zigzag排序和连接方法
        for ((index, component) in finalComponents.withIndex()) {
            val direction = bestDirectionForComponent(component)
            val sequence = optimizedZigzagOrder(component, direction, spacing)
            addOptimizedZigzagToGraph(graph, finalMask, sequence)
        }
        
        // 欧拉图化
        eulerizeGraph(graph, finalMask)
        
        // 选择起始点
        val start = graph.keys.firstOrNull() ?: Point(0, 0)
        val path = hierholzer(graph, start)
        
        return PathResult(path, finalMask, bridges)
    }
    
    /**
     * 将布尔型mask转换为可视化Bitmap
     */
    fun maskToBitmap(mask: Array<BooleanArray>, color: Int = Color.BLACK): Bitmap {
        val height = mask.size
        val width = mask[0].size
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap[x, y] = if (mask[y][x]) color else Color.TRANSPARENT
            }
        }
        
        return bitmap
    }
    
    /**
     * 将路径绘制到Bitmap上（允许重复绘制）
     */
    fun pathToBitmapWithPathRevisits(path: List<Point>, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        if (path.isNotEmpty()) {
            val androidPath = Path()
            androidPath.moveTo(path[0].x.toFloat(), path[0].y.toFloat())
            
            // 绘制完整路径，包括重复访问的点
            for (i in 1 until path.size) {
                androidPath.lineTo(path[i].x.toFloat(), path[i].y.toFloat())
            }
            
            canvas.drawPath(androidPath, paint)
        }
        
        return bitmap
    }
}