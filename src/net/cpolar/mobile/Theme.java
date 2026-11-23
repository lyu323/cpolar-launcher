package net.cpolar.mobile;

import android.content.Context;
import android.content.res.Configuration;

/** 主题注册表：色板集中定义，Ui.apply(id) 把它们刷进静态色值。21 套，带分组。 */
public final class Theme {
    private Theme() {}

    /** id 9 固定为「跟随系统」，兼容 v2.3 已保存的偏好。 */
    public static final int SYSTEM = 9;

    public static final class Def {
        public String name, desc, group = "浅色";
        public boolean dark, glass;
        public int bg, card, line, text, muted, blue, blueDark, blueLight;
        public int green, red, logBg, logFg, inputBg, previewBg, barBg, warn;
        public int headerStart, headerEnd;
        public int glassCard, glassStroke, spot1, spot2, spot3, spotBg;

        Def n(String n, String d, boolean dk, boolean gl, String gr) {
            name = n; desc = d; dark = dk; glass = gl; group = gr; return this;
        }
        Def c(int b, int ca, int li, int tx, int mu) {
            bg = b; card = ca; line = li; text = tx; muted = mu; return this;
        }
        Def a(int bl, int bd, int bll, int gr, int rd, int wn) {
            blue = bl; blueDark = bd; blueLight = bll;
            green = gr; red = rd; warn = wn; return this;
        }
        Def x(int lb, int lf, int ib, int pb, int bb) {
            logBg = lb; logFg = lf; inputBg = ib; previewBg = pb; barBg = bb; return this;
        }
        Def h(int hs, int he) { headerStart = hs; headerEnd = he; return this; }
        Def g(int gc, int gs, int s1, int s2, int s3, int sb) {
            glassCard = gc; glassStroke = gs;
            spot1 = s1; spot2 = s2; spot3 = s3; spotBg = sb; return this;
        }
    }

    private static Def base() {
        return new Def()
                .c(0xFFF3F6FA, 0xFFFFFFFF, 0xFFDDE4ED, 0xFF263238, 0xFF78909C)
                .a(0xFF1565C0, 0xFF0D47A1, 0xFFE3F2FD, 0xFF2E7D32, 0xFFC62828, 0xFFED6C02)
                .x(0xFF1E272C, 0xFFCFD8DC, 0xFFFAFCFF, 0xFFF5F8FC, 0xFFFFFFFF)
                .h(0xFF0D47A1, 0xFF1565C0);
    }

    private static Def plain() { return base().g(0, 0, 0, 0, 0, 0); }

    public static final Def[] ALL = new Def[]{
        // ---- 0..9：v2.3 原有，id 保持不变，老用户偏好不失效 ----
        plain().n("白间", "清爽浅色 · 默认", false, false, "浅色"),

        new Def().n("夜间", "护眼深色", true, false, "深色")
              .c(0xFF101720, 0xFF1C2631, 0xFF33404E, 0xFFECEFF4, 0xFF9AAAB8)
              .a(0xFF64B5F6, 0xFF1E88E5, 0xFF17324C, 0xFF81C784, 0xFFEF9A9A, 0xFFFFB74D)
              .x(0xFF0C1116, 0xFFCFD8DC, 0xFF172230, 0xFF172230, 0xFF1C2631)
              .h(0xFF0A2540, 0xFF1565C0).g(0, 0, 0, 0, 0, 0),

        new Def().n("液态玻璃", "半透明 · 光斑 · 高光描边", false, true, "玻璃")
              .c(0x00000000, 0xB8FFFFFF, 0x66FFFFFF, 0xFF1B2A3A, 0xFF5D7488)
              .a(0xFF2A6FE0, 0xFF1550B8, 0x55CBE2FF, 0xFF1E9A62, 0xFFD64550, 0xFFE08A18)
              .x(0xE0152230, 0xFFD6E6F2, 0x9AFFFFFF, 0x78FFFFFF, 0x9AFFFFFF)
              .h(0xE02262C8, 0xB84FA8F0)
              .g(0x9CFFFFFF, 0xDBFFFFFF, 0x8860B8FF, 0x88F2A0D8, 0x8878E8D8, 0xFFEAF1FB),

        new Def().n("暗夜玻璃", "深色玻璃 · 霓虹光斑", true, true, "玻璃")
              .c(0x00000000, 0x99223140, 0x40FFFFFF, 0xFFF0F5FB, 0xFFA8BCD0)
              .a(0xFF82C4FF, 0xFF3A86E8, 0x44A0D8FF, 0xFF6FD8A8, 0xFFFF9AA2, 0xFFFFC46B)
              .x(0xD008101A, 0xFFCFE0EE, 0x70FFFFFF, 0x40FFFFFF, 0x99223140)
              .h(0xD0102350, 0xB84A2F90)
              .g(0x8C1A2A3C, 0x55FFFFFF, 0x774A80FF, 0x66C44AF0, 0x5528D8C8, 0xFF070D18),

        new Def().n("深海蓝", "深蓝沉浸", true, false, "深色")
              .c(0xFF0A1626, 0xFF122642, 0xFF26456B, 0xFFE6F0FF, 0xFF8FB0D0)
              .a(0xFF4FB0FF, 0xFF2A7FD4, 0xFF183A60, 0xFF4FD8A8, 0xFFFF8A95, 0xFFFFC46B)
              .x(0xFF081320, 0xFFBFD8F0, 0xFF102038, 0xFF102038, 0xFF122642)
              .h(0xFF062B52, 0xFF1670B8).g(0, 0, 0, 0, 0, 0),

        new Def().n("暖阳橙", "暖色柔和", false, false, "浅色")
              .c(0xFFFFF6EE, 0xFFFFFFFF, 0xFFF0DFCB, 0xFF3A2A1C, 0xFF987860)
              .a(0xFFE06A1E, 0xFFB84A10, 0xFFFFE9D6, 0xFF2E8B57, 0xFFC63838, 0xFFD98324)
              .x(0xFF2A1C12, 0xFFF5E2CE, 0xFFFFFBF6, 0xFFFFF1E4, 0xFFFFFFFF)
              .h(0xFFB84A10, 0xFFF08A2C).g(0, 0, 0, 0, 0, 0),

        new Def().n("樱花粉", "柔粉淡雅", false, false, "浅色")
              .c(0xFFFFF4F8, 0xFFFFFFFF, 0xFFF5DCE7, 0xFF3B2330, 0xFFA07789)
              .a(0xFFD6558C, 0xFFA83268, 0xFFFFE6F0, 0xFF2E8B6B, 0xFFCC4455, 0xFFD9834A)
              .x(0xFF2A1622, 0xFFF6DEE9, 0xFFFFFAFC, 0xFFFFEFF5, 0xFFFFFFFF)
              .h(0xFFA83268, 0xFFE87AA8).g(0, 0, 0, 0, 0, 0),

        new Def().n("薄荷绿", "清新冷调", false, false, "浅色")
              .c(0xFFF0FAF6, 0xFFFFFFFF, 0xFFD5EBE0, 0xFF1C3A30, 0xFF6F9484)
              .a(0xFF128A6A, 0xFF0A6650, 0xFFDDF5EA, 0xFF1F9A5C, 0xFFC84040, 0xFFD08020)
              .x(0xFF0E201A, 0xFFD2EDE0, 0xFFFAFFFC, 0xFFEAF8F1, 0xFFFFFFFF)
              .h(0xFF0A6650, 0xFF22A87E).g(0, 0, 0, 0, 0, 0),

        new Def().n("OLED 纯黑", "纯黑省电 · 高对比", true, false, "深色")
              .c(0xFF000000, 0xFF000000, 0xFF303030, 0xFFFFFFFF, 0xFF9E9E9E)
              .a(0xFF82C4FF, 0xFF42A5F5, 0xFF142433, 0xFF69F0AE, 0xFFFF8A80, 0xFFFFD54F)
              .x(0xFF000000, 0xFFE0E0E0, 0xFF0A0A0A, 0xFF0A0A0A, 0xFF000000)
              .h(0xFF0A2540, 0xFF1565C0).g(0, 0, 0, 0, 0, 0),

        plain().n("跟随系统", "按系统深色模式自动切换", false, false, "浅色"),

        // ---- 10..19：v2.5 新增 ----
        new Def().n("极光紫", "紫蓝渐变 · 沉浸", true, false, "深色")
              .c(0xFF14102A, 0xFF211A3D, 0xFF3D3161, 0xFFF0EAFF, 0xFFAEA0CE)
              .a(0xFFB388FF, 0xFF7C4DFF, 0xFF2E2150, 0xFF69F0C0, 0xFFFF8AA8, 0xFFFFCB6B)
              .x(0xFF0E0A1E, 0xFFDDD0F5, 0xFF1B1533, 0xFF1B1533, 0xFF211A3D)
              .h(0xFF4A148C, 0xFF7B1FA2).g(0, 0, 0, 0, 0, 0),

        new Def().n("玫瑰金玻璃", "粉金光晕 · 半透明", false, true, "玻璃")
              .c(0x00000000, 0xA0FFF6F3, 0x66FFFFFF, 0xFF3A2430, 0xFF8E6E78)
              .a(0xFFC2185B, 0xFFAD1457, 0x55FFD9E8, 0xFF2E9E7B, 0xFFD0455A, 0xFFD9834A)
              .x(0xE02A1620, 0xFFF7E4EC, 0x9AFFFFFF, 0x70FFFFFF, 0xAAFFF4F2)
              .h(0xE0B04A6A, 0xB8E0A0B8)
              .g(0xA0FFF6F3, 0xE6FFFFFF, 0x88FFB0C8, 0x77FFD8A0, 0x66C8B0FF, 0xFFFFF0F5),

        new Def().n("午夜蓝玻璃", "深蓝霓虹 · 半透明", true, true, "玻璃")
              .c(0x00000000, 0x8C0E2240, 0x40FFFFFF, 0xFFEAF2FF, 0xFF9CB6D4)
              .a(0xFF64B5FF, 0xFF2A7FD4, 0x4482C4FF, 0xFF5FD8B0, 0xFFFF9AA8, 0xFFFFC46B)
              .x(0xD005101F, 0xFFCFE0F2, 0x70FFFFFF, 0x40FFFFFF, 0x99102840)
              .h(0xD0072247, 0xB82A4A8A)
              .g(0x8C0E2240, 0x55FFFFFF, 0x773A70FF, 0x5528B8E0, 0x4470D8C0, 0xFF050B18),

        new Def().n("赛博霓虹", "青紫高对比 · 极客", true, false, "深色")
              .c(0xFF080812, 0xFF12121F, 0xFF2E2E48, 0xFFF2F2FF, 0xFF8E8EB8)
              .a(0xFF00E5FF, 0xFF00B8D4, 0xFF14283A, 0xFF39FF14, 0xFFFF2D6F, 0xFFFFD400)
              .x(0xFF05050A, 0xFFD8D8F0, 0xFF0E0E1A, 0xFF0E0E1A, 0xFF12121F)
              .h(0xFF12003A, 0xFF006064).g(0, 0, 0, 0, 0, 0),

        new Def().n("石墨灰", "中性无彩色 · 耐看", false, false, "浅色")
              .c(0xFFF0F1F3, 0xFFFFFFFF, 0xFFD8DBDF, 0xFF232629, 0xFF71767C)
              .a(0xFF37474F, 0xFF263238, 0xFFECEFF1, 0xFF2E7D52, 0xFFB33939, 0xFFB26A00)
              .x(0xFF1C1F22, 0xFFDCE1E5, 0xFFF7F8F9, 0xFFF1F3F5, 0xFFFFFFFF)
              .h(0xFF37474F, 0xFF546E7A).g(0, 0, 0, 0, 0, 0),

        new Def().n("奶油纸墨", "米黄纸感 · 低刺激", false, false, "浅色")
              .c(0xFFFAF4E6, 0xFFFFFBF0, 0xFFE8DCC0, 0xFF3B3226, 0xFF8B7C63)
              .a(0xFF8A5A2B, 0xFF6D4420, 0xFFF3E4C8, 0xFF4F7A45, 0xFFA9413A, 0xFFB8791F)
              .x(0xFF2A2014, 0xFFF0E2C6, 0xFFFFFDF6, 0xFFFBF2DE, 0xFFFFFBF0)
              .h(0xFF6D4420, 0xFFA97A45).g(0, 0, 0, 0, 0, 0),

        new Def().n("森林墨绿", "深绿沉静", true, false, "深色")
              .c(0xFF0C1712, 0xFF14241C, 0xFF2A4034, 0xFFE6F2E9, 0xFF8FB29A)
              .a(0xFF66D9A0, 0xFF2FA36C, 0xFF173528, 0xFF8FE388, 0xFFFF9E9E, 0xFFE8C56B)
              .x(0xFF08120D, 0xFFD6EEDD, 0xFF102018, 0xFF102018, 0xFF14241C)
              .h(0xFF0B3D2A, 0xFF1B7A52).g(0, 0, 0, 0, 0, 0),

        new Def().n("日落渐变", "橙粉暖调", false, false, "浅色")
              .c(0xFFFFF3EE, 0xFFFFFFFF, 0xFFF3DCD2, 0xFF3A2630, 0xFF9A7382)
              .a(0xFFE0533F, 0xFFB83A32, 0xFFFFE6DC, 0xFF2E9E77, 0xFFC2384A, 0xFFD98324)
              .x(0xFF2A1A1E, 0xFFF8E2DA, 0xFFFFFAF8, 0xFFFFF0EA, 0xFFFFFFFF)
              .h(0xFFD2453C, 0xFFF2975B).g(0, 0, 0, 0, 0, 0),

        new Def().n("青柠苏打", "清透青蓝", false, false, "浅色")
              .c(0xFFF0FAFA, 0xFFFFFFFF, 0xFFD3EAEA, 0xFF1F3A3A, 0xFF6E9696)
              .a(0xFF0E8C96, 0xFF0A6C74, 0xFFDDF4F4, 0xFF1FA36B, 0xFFC64040, 0xFFC08020)
              .x(0xFF0C2020, 0xFFD4EDEE, 0xFFFAFFFF, 0xFFE9F8F8, 0xFFFFFFFF)
              .h(0xFF0A6C74, 0xFF28B6B0).g(0, 0, 0, 0, 0, 0),

        new Def().n("高对比无障碍", "纯黑底 · 亮黄描边 · 大字", true, false, "无障碍")
              .c(0xFF000000, 0xFF000000, 0xFFFFEB3B, 0xFFFFFFFF, 0xFFD8D8D8)
              .a(0xFFFFEB3B, 0xFFFFC107, 0xFF332E00, 0xFF00E676, 0xFFFF5252, 0xFFFFAB00)
              .x(0xFF000000, 0xFFFFFFFF, 0xFF0A0A0A, 0xFF0A0A0A, 0xFF000000)
              .h(0xFF000000, 0xFF3A3000).g(0, 0, 0, 0, 0, 0),

        // ---- 20：v2.7 新增，对齐 OriginOS「高级功能」那套系统玻璃 ----
        new Def().n("OriginOS 玻璃", "浅灰底 · 大圆角白卡 · 细线分组 · 动态光晕", false, true, "玻璃")
              .c(0xFFEFF1F5, 0xF2FFFFFF, 0xFFDDE2EA, 0xFF1B1F26, 0xFF7C8794)
              .a(0xFF4A63D8, 0xFF2F44B8, 0xFFE7EAFB, 0xFF1E9A62, 0xFFD64550, 0xFFE08A18)
              .x(0xFF1B2029, 0xFFD6DEEA, 0xEEFFFFFF, 0xD8F2F5FA, 0xEEFFFFFF)
              .h(0xFF5B72D6, 0xFF8FA6EE)
              .g(0xEEFFFFFF, 0xFFFFFFFF, 0x6678A6FF, 0x55B9A0F0, 0x4496D8E8, 0xFFEDF1F8),
    };

    public static int count() { return ALL.length; }

    public static final String[] GROUPS = {"浅色", "深色", "玻璃", "无障碍"};

    /** SYSTEM 会解析成白间 / 夜间，并把 resolved id 返回。 */
    public static int resolve(Context c, int id) {
        if (id != SYSTEM) return clamp(id);
        int mask = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES ? 1 : 0;
    }

    public static int clamp(int id) {
        return id < 0 || id >= ALL.length ? 0 : id;
    }

    public static Def of(int id) { return ALL[clamp(id)]; }
}
