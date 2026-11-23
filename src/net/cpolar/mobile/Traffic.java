package net.cpolar.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Process;

/**
 * 隧道流量统计。
 *
 * 用 TrafficStats 按本应用 UID 采样（frpc 跑在本进程，覆盖其与节点之间的流量），
 * 1 秒一次算出实时上/下行速度、本次会话累计、峰值；服务结束时把会话累计并入历史总计。
 *
 * 说明：这是按 UID 统计的近似值，包含 frpc 的全部网络流量，不含其它应用，
 * 与面板「↑/↓ Bytes」口径可能略有差异（面板由服务端统计）。
 */
public final class Traffic {
    private Traffic() {}

    public static volatile long sessionRx = 0, sessionTx = 0;   // 本次会话累计字节
    public static volatile long totalRx = 0, totalTx = 0;       // 历史累计（含本次）
    public static volatile double rxSpeed = 0, txSpeed = 0;     // B/s
    public static volatile double rxPeak = 0, txPeak = 0;
    public static volatile boolean available = true;
    public static volatile boolean active = false;
    public static volatile long lastSampleAt = 0;

    private static long baseRx = -1, baseTx = -1;
    private static long histRx = 0, histTx = 0;       // 落盘的历史累计（不含本次）
    private static long prevRx = -1, prevTx = -1, prevAt = 0;
    private static Thread sampler;

    private static final int HIST = 60;
    private static final double[] rxHist = new double[HIST];
    private static final double[] txHist = new double[HIST];
    private static int histIdx = 0, histLen = 0;

    /** 返回 {上行序列, 下行序列}，按时间从旧到新。 */
    public static synchronized double[][] history() {
        double[] up = new double[histLen], down = new double[histLen];
        int start = (histIdx - histLen + HIST) % HIST;
        for (int i = 0; i < histLen; i++) {
            int k = (start + i) % HIST;
            up[i] = txHist[k];
            down[i] = rxHist[k];
        }
        return new double[][]{up, down};
    }

    private static synchronized void push(double up, double down) {
        txHist[histIdx] = up;
        rxHist[histIdx] = down;
        histIdx = (histIdx + 1) % HIST;
        if (histLen < HIST) histLen++;
    }

    private static synchronized void clearHistory() {
        histIdx = 0;
        histLen = 0;
        for (int i = 0; i < HIST; i++) { txHist[i] = 0; rxHist[i] = 0; }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cpolar", Context.MODE_PRIVATE);
    }

    /** 会话开始。重复调用不会重置已在进行中的统计（重连不打断累计）。 */
    public static synchronized void begin(Context c) {
        histRx = prefs(c).getLong("trafficTotalRx", 0);
        histTx = prefs(c).getLong("trafficTotalTx", 0);
        totalRx = histRx;
        totalTx = histTx;
        if (sampler != null && sampler.isAlive() && active) return;
        sessionRx = 0;
        sessionTx = 0;
        rxPeak = 0;
        txPeak = 0;
        rxSpeed = 0;
        txSpeed = 0;
        clearHistory();
        baseRx = -1;
        baseTx = -1;
        prevRx = -1;
        prevTx = -1;
        prevAt = 0;
        active = true;
        available = true;
        sampler = new Thread(new Runnable() {
            @Override public void run() { sampleLoop(); }
        }, "frp-traffic");
        sampler.setDaemon(true);
        sampler.start();
    }

    public static synchronized void finish(Context c) {
        active = false;
        Thread s = sampler;
        if (s != null && s.isAlive()) s.interrupt();
        sampler = null;
        // 会话累计并入历史总计并落盘
        long nr = histRx + sessionRx;
        long nt = histTx + sessionTx;
        prefs(c).edit().putLong("trafficTotalRx", nr)
                .putLong("trafficTotalTx", nt).commit();
        histRx = nr;
        histTx = nt;
        totalRx = nr;
        totalTx = nt;
        rxSpeed = 0;
        txSpeed = 0;
    }

    /** 清零：会话 + 峰值 + 历史总计。 */
    public static synchronized void reset(Context c) {
        sessionRx = 0;
        sessionTx = 0;
        totalRx = 0;
        totalTx = 0;
        rxPeak = 0;
        txPeak = 0;
        rxSpeed = 0;
        txSpeed = 0;
        clearHistory();
        baseRx = -1;
        baseTx = -1;
        prevRx = -1;
        prevTx = -1;
        prevAt = 0;
        histRx = 0;
        histTx = 0;
        prefs(c).edit().putLong("trafficTotalRx", 0)
                .putLong("trafficTotalTx", 0).commit();
    }

    /** 读取一次 UID 计数，返回 {rx, tx}；不支持时返回 {-1,-1}。 */
    private static long[] read() {
        try {
            int uid = Process.myUid();
            long rx = TrafficStats.getUidRxBytes(uid);
            long tx = TrafficStats.getUidTxBytes(uid);
            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
                return new long[]{-1, -1};
            }
            return new long[]{rx, tx};
        } catch (Throwable t) {
            return new long[]{-1, -1};
        }
    }

    private static void sampleLoop() {
        while (active) {
            long now = System.currentTimeMillis();
            long[] v = read();
            if (v[0] < 0) {
                available = false;
            } else {
                available = true;
                if (baseRx < 0) {
                    baseRx = v[0];
                    baseTx = v[1];
                    prevRx = v[0];
                    prevTx = v[1];
                    prevAt = now;
                } else {
                    // 计数器被系统重置时重设基准，避免负数
                    if (v[0] < prevRx) baseRx = v[0];
                    if (v[1] < prevTx) baseTx = v[1];
                    if (v[0] < prevRx) prevRx = v[0];
                    if (v[1] < prevTx) prevTx = v[1];

                    long sRx = Math.max(0, v[0] - baseRx);
                    long sTx = Math.max(0, v[1] - baseTx);
                    sessionRx = sRx;
                    sessionTx = sTx;
                    totalRx = histRx + sRx;
                    totalTx = histTx + sTx;

                    long dt = now - prevAt;
                    if (dt > 0) {
                        double up = Math.max(0, v[1] - prevTx) * 1000d / dt;
                        double down = Math.max(0, v[0] - prevRx) * 1000d / dt;
                        // 轻度平滑，避免数字乱跳
                        txSpeed = txSpeed * 0.35 + up * 0.65;
                        rxSpeed = rxSpeed * 0.35 + down * 0.65;
                        push(txSpeed, rxSpeed);
                        if (txSpeed > txPeak) txPeak = txSpeed;
                        if (rxSpeed > rxPeak) rxPeak = rxSpeed;
                    }
                    prevRx = v[0];
                    prevTx = v[1];
                    prevAt = now;
                }
                lastSampleAt = now;
            }
            try { Thread.sleep(1000); } catch (Exception e) { break; }
        }
    }

    /** 供 UI 显示的历史总计（含本次会话）。 */
    public static long[] grandTotal() {
        return new long[]{
                Math.max(totalRx, sessionRx),
                Math.max(totalTx, sessionTx)
        };
    }
}
