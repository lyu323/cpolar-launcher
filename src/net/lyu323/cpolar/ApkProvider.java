package net.lyu323.cpolar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * v1.3.0：给「本地下载」提供 APK 的 content:// Uri（Android 7+ 不能用 file://）。
 * 只暴露 App 私有外部下载目录下、文件名合法的 .apk，exported=false + 临时授权。
 */
public final class ApkProvider extends ContentProvider {
    public static final String SUFFIX = ".apkprovider";
    public static final String MIME = "application/vnd.android.package-archive";

    public static String authority(Context c) { return c.getPackageName() + SUFFIX; }

    public static Uri uri(Context c, String name) {
        return Uri.parse("content://" + authority(c) + "/apk/" + name);
    }

    /** 防路径穿越：只允许 ASCII 文件名且必须 .apk 结尾。 */
    public static boolean safeName(String n) {
        return n != null && n.matches("[A-Za-z0-9._-]+\\.apk") && !n.contains("..");
    }

    public static File dir(Context c) {
        File d = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (d == null) d = new File(c.getFilesDir(), "downloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode)
            throws FileNotFoundException {
        String n = u.getLastPathSegment();
        if (!safeName(n)) throw new FileNotFoundException("非法文件名");
        File f = new File(dir(getContext()), n);
        if (!f.isFile()) throw new FileNotFoundException("文件不存在");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri u, String[] proj, String sel,
                                  String[] args, String sort) {
        String n = u.getLastPathSegment();
        if (!safeName(n)) return null;
        File f = new File(dir(getContext()), n);
        MatrixCursor cur = new MatrixCursor(new String[]{"_display_name", "_size"});
        cur.addRow(new Object[]{n, f.length()});
        return cur;
    }

    @Override public String getType(Uri u) { return MIME; }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
