package to.kuudere.anisuge.data.services

import android.os.StatFs
import android.os.Environment
import to.kuudere.anisuge.platform.androidAppContext
import java.io.File

actual fun getFontCacheDirectory(): String {
    val tmp = System.getProperty("java.io.tmpdir") ?: return ""
    val dir = File(tmp, "sub-fonts")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun getSettingsDirectory(): String {
    // DataStore files only — avoid scanning cache or external storage.
    val dir = File(androidAppContext.filesDir, "datastore")
    return dir.absolutePath
}

actual fun getTotalDiskSpace(): Long {
    return try {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.totalBytes
    } catch (e: Exception) {
        0L
    }
}

actual fun getFreeDiskSpace(): Long {
    return try {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.availableBytes
    } catch (e: Exception) {
        0L
    }
}
