package net.lyu323.cpolar;

/**
 * 启动特效用的纯 3D 数学：旋转矩阵、透视投影、二十面体线框数据。
 * 不依赖任何 Android 类，可离线单元测试。
 */
public final class Fx3d {
    private Fx3d() {}

    /** 依次绕 X / Y / Z 轴旋转，返回 {x,y,z}。 */
    public static float[] rotate(float x, float y, float z,
                                 float ax, float ay, float az) {
        float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
        float cy = (float) Math.cos(ay), sy = (float) Math.sin(ay);
        float cz = (float) Math.cos(az), sz = (float) Math.sin(az);
        float y1 = y * cx - z * sx;
        float z1 = y * sx + z * cx;
        float x2 = x * cy + z1 * sy;
        float z2 = -x * sy + z1 * cy;
        float x3 = x2 * cz - y1 * sz;
        float y3 = x2 * sz + y1 * cz;
        return new float[]{x3, y3, z2};
    }

    /** 透视投影，返回 {sx, sy, scale, depth}；depth 恒为正，越大越远。 */
    public static float[] project(float x, float y, float z,
                                  float focal, float camera) {
        float d = camera + z;
        if (d < 0.25f) d = 0.25f;
        float s = focal / d;
        return new float[]{x * s, y * s, s, d};
    }

    /** 未归一化的二十面体 12 顶点（黄金比构造，边长为 2）。 */
    public static float[][] rawVertices() {
        float p = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        return new float[][]{
            {0, 1, p}, {0, -1, p}, {0, 1, -p}, {0, -1, -p},
            {1, p, 0}, {-1, p, 0}, {1, -p, 0}, {-1, -p, 0},
            {p, 0, 1}, {-p, 0, 1}, {p, 0, -1}, {-p, 0, -1},
        };
    }

    /** 归一化到单位球，渲染时再乘半径。 */
    public static float[][] vertices() {
        float[][] r = rawVertices(), v = new float[r.length][3];
        for (int i = 0; i < r.length; i++) {
            float n = (float) Math.sqrt(r[i][0] * r[i][0] + r[i][1] * r[i][1]
                    + r[i][2] * r[i][2]);
            v[i][0] = r[i][0] / n; v[i][1] = r[i][1] / n; v[i][2] = r[i][2] / n;
        }
        return v;
    }

    /** 距离平方≈4（边长 2）的两点连一条边，共 30 条。 */
    public static int[][] edges() {
        float[][] r = rawVertices();
        java.util.ArrayList<int[]> out = new java.util.ArrayList<int[]>();
        for (int i = 0; i < r.length; i++)
            for (int j = i + 1; j < r.length; j++) {
                float dx = r[i][0] - r[j][0], dy = r[i][1] - r[j][1],
                      dz = r[i][2] - r[j][2];
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > 3.5f && d2 < 4.5f) out.add(new int[]{i, j});
            }
        int[][] e = new int[out.size()][];
        for (int i = 0; i < e.length; i++) e[i] = out.get(i);
        return e;
    }

    /** 立方体 8 顶点 / 12 边，用作外层反向线框。 */
    public static float[][] cubeVertices() {
        return new float[][]{
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
            {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1},
        };
    }

    public static int[][] cubeEdges() {
        return new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7},
        };
    }

    public static boolean finite(float[] a) {
        if (a == null) return false;
        for (float v : a) if (Float.isNaN(v) || Float.isInfinite(v)) return false;
        return true;
    }
}
