import cv2
import numpy as np
from PIL import Image, ImageFont, ImageDraw
from sklearn.decomposition import PCA
from collections import deque
import numpy as np
import math

# -------------------------------
# emoji_to_mask（你的函数）
# -------------------------------
def emoji_to_mask(emoji="❄", font_path="libs/NotoEmoji-Regular.ttf", size=130):
    canvas = size + 40
    img = Image.new('RGBA', (canvas, canvas), (0, 0, 0, 0))
    try:
        font = ImageFont.truetype(font_path, size)
    except Exception as e:
        print("❌ 字体不存在或加载失败：", e)
        return None

    draw = ImageDraw.Draw(img)
    bbox = draw.textbbox((0, 0), emoji, font=font)
    x = (canvas - (bbox[2] - bbox[0])) // 2 - bbox[0]
    y = (canvas - (bbox[3] - bbox[1])) // 2 - bbox[1]
    draw.text((x, y), emoji, font=font, fill=(255, 255, 255, 255))

    alpha = np.array(img)[:, :, 3]
    _, binary = cv2.threshold(alpha, 50, 255, cv2.THRESH_BINARY)
    binary[binary > 0] = 255
    return binary

# -------------------------------
# 常量与辅助
# -------------------------------
NEIGH = [(1,0),(-1,0),(0,1),(0,-1)]
def in_bounds(x,y,w,h): return 0<=x<w and 0<=y<h

# Bresenham line (integer pixel list between two points)
def bresenham_line(x0, y0, x1, y1):
    points = []
    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    x, y = x0, y0
    while True:
        points.append((x, y))
        if x == x1 and y == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x += sx
        if e2 <= dx:
            err += dx
            y += sy
    return points

# -------------------------------
# 1) 提取连通分量（像素集合）
# -------------------------------
def extract_components(mask):
    h,w = mask.shape
    vis = np.zeros_like(mask, dtype=bool)
    comps = []
    for y in range(h):
        for x in range(w):
            if mask[y,x]==255 and not vis[y,x]:
                q = deque()
                q.append((x,y))
                vis[y,x] = True
                pixels = []
                while q:
                    cx,cy = q.popleft()
                    pixels.append((cx,cy))
                    for dx,dy in NEIGH:
                        nx,ny = cx+dx, cy+dy
                        if in_bounds(nx,ny,w,h) and mask[ny,nx]==255 and not vis[ny,nx]:
                            vis[ny,nx] = True
                            q.append((nx,ny))
                comps.append(np.array(pixels, dtype=np.int32))
    return comps

# -------------------------------
# 连接组件：贪心最近桥（把桥像素写回 mask）
# 返回修改后的 mask 和桥的列表（每个桥是点列表）
# -------------------------------
def connect_components_with_bridges(mask, comps, bridge_width=1):
    """
    mask: uint8 HxW (0/255)
    comps: list of numpy arrays of pixels
    strategy: 贪心：每次把已连接集合与最近未连接组件配对并用最短像素直线连通
    bridge_width: int, 用于把桥加宽为多像素（简单处理：在直线上把周围像素也设为白）
    """
    h,w = mask.shape
    # if only one comp, nothing to do
    if len(comps) <= 1:
        return mask, []

    # Build initial sets: treat comps as nodes; we'll connect them greedily similar to Prim
    remaining = {i for i in range(len(comps))}
    connected = {remaining.pop()}  # start from one arbitrary component
    bridges = []

    # Precompute component bounding boxes (optional speed up)
    bboxes = []
    for pts in comps:
        xs = pts[:,0]; ys = pts[:,1]
        bboxes.append((xs.min(), ys.min(), xs.max(), ys.max()))

    # Greedy loop: while remaining components exist, find nearest pair (a in connected, b in remaining)
    while remaining:
        best = None  # (dist, ai, bi, ax,ay, bx,by)
        for ai in connected:
            A = comps[ai]
            # accelerate by using bbox distance lower bound
            ax0,ay0,ax1,ay1 = bboxes[ai]
            for bi in remaining:
                B = comps[bi]
                bx0,by0,bx1,by1 = bboxes[bi]
                # bbox lower bound
                dx = 0
                if ax1 < bx0:
                    dx = bx0 - ax1
                elif bx1 < ax0:
                    dx = ax0 - bx1
                dy = 0
                if ay1 < by0:
                    dy = by0 - ay1
                elif by1 < ay0:
                    dy = ay0 - by1
                lb = dx*dx + dy*dy
                # if we already have a best with smaller lower bound, skip expensive check
                if best and lb >= best[0]:
                    continue
                # brute force compute closest pair between A and B (could be heavy but manageable for emoji-sized)
                # use squared distance
                # vectorized approach
                Ax = A[:,0][:,None]
                Ay = A[:,1][:,None]
                Bx = B[:,0][None,:]
                By = B[:,1][None,:]
                dxm = Ax - Bx
                dym = Ay - By
                dist2 = (dxm*dxm + dym*dym)
                idx = np.argmin(dist2)
                minval = int(dist2.flatten()[idx])
                # convert flat idx to coordinates
                idx_a, idx_b = np.unravel_index(idx, dist2.shape)
                ax, ay = int(A[idx_a,0]), int(A[idx_a,1])
                bx, by = int(B[idx_b,0]), int(B[idx_b,1])
                if (best is None) or (minval < best[0]):
                    best = (minval, ai, bi, ax, ay, bx, by)
        if best is None:
            break
        _, ai, bi, ax, ay, bx, by = best

        # draw a bridge (bresenham line) between (ax,ay) and (bx,by)
        line = bresenham_line(ax, ay, bx, by)
        # optionally widen the bridge
        for (lx,ly) in line:
            for ox in range(-bridge_width//2, bridge_width//2 + 1):
                for oy in range(-bridge_width//2, bridge_width//2 + 1):
                    nx, ny = lx+ox, ly+oy
                    if in_bounds(nx, ny, w, h):
                        mask[ny, nx] = 255
        bridges.append(line)

        # merge: move bi from remaining to connected
        connected.add(bi)
        if bi in remaining:
            remaining.remove(bi)
        # Also update comps[ai] and comps[bi] could be left as-is; we will not recompute comps list here
        # But to speed next nearest search, we can optionally append the bridge pixels to comps[ai]
        # find the comp object for ai and extend it with the bridge points (so future nearest checks include new bridge)
        # We'll append bridge pixels to comps[ai]
        comps[ai] = np.vstack([comps[ai], np.array(line, dtype=np.int32)])
        # update bbox
        xs = comps[ai][:,0]; ys = comps[ai][:,1]
        bboxes[ai] = (xs.min(), ys.min(), xs.max(), ys.max())

    return mask, bridges

# -------------------------------
# PCA + zigzag + BFS shortest path 插边 + Eulerize (重用之前最强版本的大体流程)
# -------------------------------
def best_direction_for_component(points):
    if len(points) < 3:
        return np.array([1.0, 0.0])
    pca = PCA(n_components=2)
    pca.fit(points)
    v = pca.components_[0]
    n = np.linalg.norm(v)
    if n == 0:
        return np.array([1.0,0.0])
    return v / n

def zigzag_order(points, direction, spacing=3):
    pts = points.astype(np.float64)
    dx,dy = direction
    normal = np.array([-dy, dx])
    nrm = np.linalg.norm(normal)
    if nrm==0:
        normal = np.array([0.0,1.0])
    else:
        normal = normal / nrm

    proj = pts @ normal
    lo, hi = proj.min(), proj.max()
    layers = []
    d = lo
    while d <= hi + 1e-6:
        mask = np.abs(proj - d) < spacing
        layer_pts = pts[mask]
        if layer_pts.shape[0] > 0:
            order_vals = layer_pts @ direction
            idx = np.argsort(order_vals)
            ordered = layer_pts[idx]
            layers.append([tuple(map(int,p)) for p in ordered])
        d += spacing

    out = []
    flip=False
    for layer in layers:
        if flip:
            layer = layer[::-1]
        out.extend(layer)
        flip = not flip
    # unique preserve order
    seen=set(); seq=[]
    for p in out:
        if p not in seen:
            seq.append(p); seen.add(p)
    return seq

def bfs_shortest_path(mask, s, t):
    if s==t: return [s]
    h,w = mask.shape
    q = deque([s])
    prev = {s: None}
    while q:
        x,y = q.popleft()
        for dx,dy in NEIGH:
            nx,ny = x+dx, y+dy
            if in_bounds(nx,ny,w,h) and mask[ny,nx]==255 and (nx,ny) not in prev:
                prev[(nx,ny)] = (x,y)
                if (nx,ny) == t:
                    # backtrack
                    path=[(nx,ny)]
                    cur=(nx,ny)
                    while prev[cur] is not None:
                        cur=prev[cur]; path.append(cur)
                    return path[::-1]
                q.append((nx,ny))
    return None

def build_empty_graph(mask):
    h,w = mask.shape
    graph = {}
    for y in range(h):
        for x in range(w):
            if mask[y,x]==255:
                graph[(x,y)] = []
    return graph

def add_zigzag_to_graph(graph, mask, seq):
    for i in range(len(seq)-1):
        a = seq[i]; b = seq[i+1]
        path = bfs_shortest_path(mask, a, b)
        if path is None:
            continue
        for j in range(len(path)-1):
            u = path[j]; v = path[j+1]
            graph[u].append(v); graph[v].append(u)

def eulerize_graph(graph, mask):
    def degree(node): return len(graph[node])
    odd = [n for n in graph if degree(n)%2==1]
    # greedy pair
    while odd:
        a = odd.pop(0)
        # BFS to nearest odd
        h,w = mask.shape
        q = deque([a])
        prev = {a: None}
        found_b = None
        while q and found_b is None:
            cur = q.popleft()
            for dx,dy in NEIGH:
                nx,ny = cur[0]+dx, cur[1]+dy
                if in_bounds(nx,ny,w,h) and mask[ny,nx]==255 and (nx,ny) not in prev:
                    prev[(nx,ny)] = cur
                    if (nx,ny) in odd:
                        found_b = (nx,ny); break
                    q.append((nx,ny))
        if found_b is None:
            break
        # backtrack path
        path=[]
        cur = found_b
        while cur is not None:
            path.append(cur); cur = prev[cur]
        path = path[::-1]
        for i in range(len(path)-1):
            u=path[i]; v=path[i+1]
            graph[u].append(v); graph[v].append(u)
        # remove found_b from odd
        odd = [x for x in odd if x!=found_b]
    return graph

def hierholzer(graph, start):
    g = {k:list(v) for k,v in graph.items()}
    stack=[start]; circuit=[]
    while stack:
        v = stack[-1]
        if g[v]:
            u = g[v].pop()
            try:
                g[u].remove(v)
            except ValueError:
                pass
            stack.append(u)
        else:
            circuit.append(v); stack.pop()
    return circuit[::-1]

def build_one_stroke_zigzag_path_with_auto_bridges(mask, spacing=3, bridge_width=1):
    # extract components
    comps = extract_components(mask)
    print(f"Found {len(comps)} components.")
    if len(comps) > 1:
        print("Auto-connecting components with minimal bridges...")
        mask_copy = mask.copy()
        mask_copy, bridges = connect_components_with_bridges(mask_copy, comps, bridge_width=bridge_width)
        # re-extract components on new mask to get updated sets (should be one)
        comps = extract_components(mask_copy)
        print(f"After bridging, components: {len(comps)}")
        mask = mask_copy
    else:
        bridges = []

    graph = build_empty_graph(mask)

    for idx, comp in enumerate(comps):
        direction = best_direction_for_component(comp)
        seq = zigzag_order(comp, direction, spacing=spacing)
        print(f" Component {idx}: points={len(comp)}, zigzag_seq={len(seq)}")
        add_zigzag_to_graph(graph, mask, seq)

    print("Eulerizing graph...")
    graph = eulerize_graph(graph, mask)

    # pick start
    start = next(iter(graph.keys())) if graph else None
    if start is None:
        return []

    path = hierholzer(graph, start)
    return path, mask, bridges

def animate_path(mask, path, window_name="ONE-STROKE-ZIGZAG", speed=1):
    h,w = mask.shape
    canvas = np.zeros((h,w,3),dtype=np.uint8)
    canvas[mask==255] = (30,30,30)

    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window_name, 600, 600)

    total = len(path)
    for i,(x,y) in enumerate(path):
        hue = int((i*180/ max(1,total)) % 180)
        hsv = np.array([[[hue,255,255]]], dtype=np.uint8)
        rgb = cv2.cvtColor(hsv, cv2.COLOR_HSV2RGB)[0,0]
        canvas[y,x] = (int(rgb[0]), int(rgb[1]), int(rgb[2]))

        if i % speed == 0 or i==total-1:
            disp = canvas.copy()
            cv2.rectangle(disp, (0,0), (360,22), (0,0,0), -1)
            cv2.putText(disp, f"{i+1}/{total}", (6,16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,255,0),1)
            cv2.imshow(window_name, disp)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
    cv2.waitKey(0)
    cv2.destroyAllWindows()

# -------------------------------
# Demo run
# -------------------------------
if __name__ == "__main__":
    mask = emoji_to_mask(emoji="❄", font_path="libs/NotoEmoji-Regular.ttf", size=130)
    if mask is None:
        print("请确保你有合适的 emoji 字体文件，或改用已有黑白图作为 mask。")
        exit(1)

    cv2.imshow("mask debug (white=target)", cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR))
    print("确认是黑白图，按任意键继续...")
    cv2.waitKey(0)
    cv2.destroyAllWindows()

    # 调整 spacing（层间距）与 bridge_width（桥宽）
    path, bridged_mask, bridges = build_one_stroke_zigzag_path_with_auto_bridges(mask, spacing=3, bridge_width=1)
    print("最终路径点数:", len(path))
    print("桥的数量:", len(bridges))

    animate_path(bridged_mask, path, speed=30)

    # 输出 numpy 版本（便于调试）
    np.save("points/path.npy", np.array(path, dtype=np.int16))

    print(f"[✔] path.npy 已保存，共 {len(path)} 个点")

    # 输出 ESP32 的 C 数组
    with open("points/points.h", "w") as f:
        f.write(f"// Auto-generated path, total {len(path)} points\n")
        f.write("const int PATH_LEN = " + str(len(path)) + ";\n")
        f.write("const int16_t PATH[][2] = {\n")
        for (x, y) in path:
            f.write(f"    {{ {x}, {y} }},\n")
        f.write("};\n")

    print(f"[✔] points/points.h 已生成，可直接在 ESP32 上使用！")

    # 保存最后结果图
    h,w = bridged_mask.shape
    canvas = np.zeros((h,w,3),dtype=np.uint8)
    canvas[bridged_mask==255] = (30,30,30)
    for i,(x,y) in enumerate(path):
        hue = int((i*180/ max(1,len(path))) % 180)
        hsv = np.array([[[hue,255,255]]], dtype=np.uint8)
        rgb = cv2.cvtColor(hsv, cv2.COLOR_HSV2RGB)[0,0]
        canvas[y,x] = (int(rgb[0]), int(rgb[1]), int(rgb[2]))
    cv2.imwrite("points/one_stroke_with_bridges.png", canvas)
    print("Saved one_stroke_with_bridges.png")
