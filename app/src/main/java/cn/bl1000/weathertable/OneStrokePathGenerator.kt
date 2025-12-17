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
        
        // 8方向邻居（包括对角线方向）
        private val NEIGHBORS_8 = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(-1, 0),
            intArrayOf(0, 1),
            intArrayOf(0, -1),
            intArrayOf(1, 1),
            intArrayOf(1, -1),
            intArrayOf(-1, 1),
            intArrayOf(-1, -1)
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
                minX = Math.min(minX, point.x)
                minY = Math.min(minY, point.y)
                maxX = Math.max(maxX, point.x)
                maxY = Math.max(maxY, point.y)
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
            
            // 检查是否到达终点
            if (current.x == end.x && current.y == end.y) {
                // 回溯路径
                val path = mutableListOf<Point>()
                var cur: Point? = current
                
                while (cur != null) {
                    path.add(cur)
                    cur = previous[cur]
                }
                
                return path.reversed()
            }
            
            for (neighbor in NEIGHBORS_8) {
                val nx = current.x + neighbor[0]
                val ny = current.y + neighbor[1]
                
                if (inBounds(nx, ny, width, height) && 
                    mask[ny][nx] && 
                    Point(nx, ny) !in previous) {
                    
                    previous[Point(nx, ny)] = current
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
     * 进度回调接口
     */
    interface ProgressCallback {
        fun onProgress(progress: Int, message: String)
    }

    /**
     * 构建带自动桥梁的一笔画优化Zigzag路径
     */
    fun buildOptimizedOneStrokePath(
        mask: Array<BooleanArray>,
        spacing: Int = 3,
        bridgeWidth: Int = 1,
        progressCallback: ProgressCallback? = null
    ): PathResult {
        progressCallback?.onProgress(0, "开始生成路径...")
        
        // 提取组件
        val components = extractComponents(mask)
        progressCallback?.onProgress(10, "提取组件完成，共${components.size}个组件")
        
        val finalMask: Array<BooleanArray>
        val bridges: List<List<Point>>
        
        if (components.size > 1) {
            // 自动连接组件与最小桥梁
            progressCallback?.onProgress(20, "开始连接组件...")
            val (maskCopy, bridgeList) = connectComponentsWithBridges(mask, components, bridgeWidth)
            finalMask = maskCopy
            bridges = bridgeList
            progressCallback?.onProgress(40, "连接组件完成，添加了${bridgeList.size}座桥")
        } else {
            finalMask = mask
            bridges = emptyList()
            progressCallback?.onProgress(40, "图像已经是连通的，无需添加桥")
        }
        
        // 使用改进的算法生成完整的一笔画路径，确保所有区域都被覆盖
        progressCallback?.onProgress(50, "开始生成完整路径...")
        val path = generateCompleteOneStrokePath(finalMask, components, progressCallback)
        progressCallback?.onProgress(90, "路径生成完成，共${path.size}个点")
        
        return PathResult(path, finalMask, bridges)
    }
    
    /**
     * 生成完整的一笔画路径，确保所有连通分量都被覆盖
     */
    private fun generateCompleteOneStrokePath(
        mask: Array<BooleanArray>,
        components: List<List<Point>>,
        progressCallback: ProgressCallback? = null
    ): List<Point> {
        val path = mutableListOf<Point>()
        val height = mask.size
        val width = mask[0].size

        // 创建访问标记数组
        val visited = Array(height) { BooleanArray(width) }

        // 获取所有未访问的点
        val unvisitedPoints = mutableSetOf<Point>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    unvisitedPoints.add(Point(x, y))
                }
            }
        }

        // 如果没有点需要访问，直接返回空路径
        if (unvisitedPoints.isEmpty()) {
            return path
        }

        progressCallback?.onProgress(55, "开始遍历所有点...")

        // 找到左上角和右下角的有效点
        var startPoint: Point? = null
        var endPoint: Point? = null

        // 从左上角开始寻找起始点
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    startPoint = Point(x, y)
                    break@outer
                }
            }
        }

        // 从右下角开始寻找结束点
        outer@ for (y in height - 1 downTo 0) {
            for (x in width - 1 downTo 0) {
                if (mask[y][x]) {
                    endPoint = Point(x, y)
                    break@outer
                }
            }
        }

        // 如果没有找到合适的起始或结束点，使用默认逻辑
        if (startPoint == null) {
            startPoint = unvisitedPoints.first()
        }

        if (endPoint == null) {
            endPoint = unvisitedPoints.last()
        }

        // 从起始点开始
        var currentPoint = startPoint!!
        path.add(currentPoint)
        visited[currentPoint.y][currentPoint.x] = true
        unvisitedPoints.remove(currentPoint)

        var processedCount = 1 // 已经处理了第一个点
        val totalCount = unvisitedPoints.size + 1 // 总点数
        var lastReportedProgress = 55
        var lastProgressUpdateTime = System.currentTimeMillis()

        // 当还有未访问的点时继续
        while (unvisitedPoints.isNotEmpty()) {
            // 更新进度（限制更新频率，避免UI卡顿）
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressUpdateTime > 100) { // 每100毫秒最多更新一次进度
                // 进度范围从55到90
                val progress = 55 + ((processedCount * 35) / totalCount)
                // 确保进度不超过90
                val clampedProgress = Math.min(progress, 90)
                if (clampedProgress > lastReportedProgress) {
                    lastReportedProgress = clampedProgress
                    progressCallback?.onProgress(clampedProgress, "已处理 $processedCount/$totalCount 个点")
                }
                lastProgressUpdateTime = currentTime
            }

            // 寻找最近的未访问邻居点
            var nearestPoint: Point? = null
            var minDistance = Int.MAX_VALUE

            // 先检查直接相邻的点
            for (neighbor in NEIGHBORS) {
                val nx = currentPoint.x + neighbor[0]
                val ny = currentPoint.y + neighbor[1]

                if (inBounds(nx, ny, width, height) &&
                    mask[ny][nx] &&
                    !visited[ny][nx]) {
                    nearestPoint = Point(nx, ny)
                    minDistance = 1 // 相邻点距离为1
                    break
                }
            }

            // 如果没有找到相邻点，检查对角线方向
            if (nearestPoint == null) {
                for (neighbor in NEIGHBORS_8) {
                    val nx = currentPoint.x + neighbor[0]
                    val ny = currentPoint.y + neighbor[1]

                    if (inBounds(nx, ny, width, height) &&
                        mask[ny][nx] &&
                        !visited[ny][nx]) {
                        val distance = kotlin.math.abs(neighbor[0]) + kotlin.math.abs(neighbor[1])
                        if (distance < minDistance) {
                            nearestPoint = Point(nx, ny)
                            minDistance = distance
                        }
                    }
                }
            }

            // 如果找到了相邻点，直接移动到该点
            if (nearestPoint != null) {
                currentPoint = nearestPoint
                path.add(currentPoint)
                visited[currentPoint.y][currentPoint.x] = true
                unvisitedPoints.remove(currentPoint)
                processedCount++
                continue
            }

            // 如果没有找到相邻点，使用BFS寻找最近的未访问点
            val queue = ArrayDeque<Pair<Point, Int>>() // 点和距离的对
            val bfsVisited = Array(height) { BooleanArray(width) }
            queue.add(Pair(currentPoint, 0))
            bfsVisited[currentPoint.y][currentPoint.x] = true

            var foundTarget: Point? = null
            var targetPath: List<Point>? = null
            bfsLoop@ while (queue.isNotEmpty()) {
                val (point, distance) = queue.removeFirst()

                // 检查这个点是否是我们要找的未访问点
                if (unvisitedPoints.contains(point)) {
                    // 找到目标点，现在需要找到从当前点到这个点的实际路径
                    foundTarget = point
                    targetPath = bfsShortestPath(mask, currentPoint, foundTarget)
                    break@bfsLoop
                }

                // 限制BFS搜索深度，防止在大型图像上花费太多时间
                if (distance > 100) {
                    continue
                }

                // 探索邻居点
                for (neighbor in NEIGHBORS_8) {
                    val nx = point.x + neighbor[0]
                    val ny = point.y + neighbor[1]

                    if (inBounds(nx, ny, width, height) &&
                        mask[ny][nx] &&
                        !bfsVisited[ny][nx]) {
                        bfsVisited[ny][nx] = true
                        queue.add(Pair(Point(nx, ny), distance + 1))
                    }
                }
            }

            // 如果找到了目标点，沿着路径移动
            if (foundTarget != null && targetPath != null && targetPath.isNotEmpty()) {
                // 添加路径中的所有点（除了第一个点，因为它已经是当前点）
                for (i in 1 until targetPath.size) {
                    val point = targetPath[i]
                    path.add(point)
                    visited[point.y][point.x] = true
                    unvisitedPoints.remove(point)
                }
                currentPoint = foundTarget
                processedCount++
            } else {
                // 如果BFS也没有找到路径，选择任意一个未访问点（这种情况不应该发生）
                // 但我们仍需要确保能继续处理
                if (unvisitedPoints.isNotEmpty()) {
                    currentPoint = unvisitedPoints.first()
                    path.add(currentPoint)
                    visited[currentPoint.y][currentPoint.x] = true
                    unvisitedPoints.remove(currentPoint)
                    processedCount++
                }
            }
        }

        // 确保路径结束在指定的结束点
        if (currentPoint != endPoint && endPoint != null) {
            val finalPath = bfsShortestPath(mask, currentPoint, endPoint!!)
            if (finalPath != null && finalPath.isNotEmpty()) {
                // 添加路径中的所有点（除了第一个点，因为它已经是当前点）
                for (i in 1 until finalPath.size) {
                    val point = finalPath[i]
                    path.add(point)
                    visited[point.y][point.x] = true
                    unvisitedPoints.remove(point)
                }
            }
        }

        return path
    }

    /**
     * 使用扫描线算法生成一笔画路径
     * 这种方法会产生更加规整、有序的路径
     */
    fun scanlinePathGeneration(mask: Array<BooleanArray>): List<Point> {
        val path = mutableListOf<Point>()
        val height = mask.size
        val width = mask[0].size
        
        var direction = 1  // 1 表示从左到右，-1 表示从右到左
        
        // 按行扫描
        for (y in 0 until height) {
            val rowPoints = mutableListOf<Point>()
            
            // 收集当前行的所有有效点
            for (x in 0 until width) {
                if (mask[y][x]) {
                    rowPoints.add(Point(x, y))
                }
            }
            
            // 根据方向确定排序顺序
            if (direction == -1) {
                rowPoints.reverse()
            }
            
            // 将当前行的点添加到路径中
            path.addAll(rowPoints)
            
            // 切换方向
            direction *= -1
        }
        
        return path
    }
    
    /**
     * 使用改进的Flood Fill算法生成一笔画路径，确保笔不离纸
     * 这种方法会产生连续无跳跃的路径
     */
    fun floodFillPathGeneration(mask: Array<BooleanArray>): List<Point> {
        val path = mutableListOf<Point>()
        val height = mask.size
        val width = mask[0].size

        // 创建访问标记数组
        val visited = Array(height) { BooleanArray(width) }

        // 找到第一个为true的点作为起始点
        var startX = -1
        var startY = -1
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y][x]) {
                    startX = x
                    startY = y
                    break@outer
                }
            }
        }

        // 如果没有找到起始点，返回空路径
        if (startX == -1 || startY == -1) {
            return path
        }

        // 使用栈进行深度优先搜索，确保路径连续
        val stack = ArrayDeque<Point>()
        stack.add(Point(startX, startY))
        visited[startY][startX] = true
        path.add(Point(startX, startY))

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()

            // 优先检查未访问的相邻点（4方向）
            var foundNext = false
            for (neighbor in NEIGHBORS) {
                val nx = current.x + neighbor[0]
                val ny = current.y + neighbor[1]

                // 检查边界和是否已访问
                if (inBounds(nx, ny, width, height) &&
                    mask[ny][nx] &&
                    !visited[ny][nx]) {

                    visited[ny][nx] = true
                    stack.add(Point(nx, ny))
                    path.add(Point(nx, ny))
                    foundNext = true
                    break // 找到第一个相邻点就跳出循环
                }
            }

            // 如果没有找到相邻的未访问点，则检查对角线方向
            if (!foundNext) {
                for (neighbor in NEIGHBORS_8) {
                    // 跳过已经在NEIGHBORS中检查过的4个方向
                    if ((neighbor[0] == 0 && neighbor[1] != 0) ||
                        (neighbor[0] != 0 && neighbor[1] == 0)) {
                        continue
                    }

                    val nx = current.x + neighbor[0]
                    val ny = current.y + neighbor[1]

                    // 检查边界和是否已访问
                    if (inBounds(nx, ny, width, height) &&
                        mask[ny][nx] &&
                        !visited[ny][nx]) {

                        visited[ny][nx] = true
                        stack.add(Point(nx, ny))
                        path.add(Point(nx, ny))
                        foundNext = true
                        break // 找到第一个相邻点就跳出循环
                    }
                }
            }

            // 如果还是没有找到下一个点，继续弹出栈顶元素进行回溯
            // 直到找到有未访问邻居的点或者栈为空
            // 这样可以确保访问所有可达的点，实现完整的欧拉路径
        }

        return path
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