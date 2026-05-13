package to.kuudere.anisuge.data.services

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun getFontCacheDirectory(): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val base = paths.firstOrNull()?.path ?: "./"
    return "$base/Fonts"
}

@OptIn(ExperimentalForeignApi::class)
actual fun getSettingsDirectory(): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val base = paths.firstOrNull()?.path ?: "./"
    val dir = "$base/Settings"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
actual fun getTotalDiskSpace(): Long {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val url = paths.firstOrNull() ?: return 0L
    val values = url.resourceValuesForKeys(listOf("NSURLVolumeTotalCapacityKey"), null)
    return (values?.get("NSURLVolumeTotalCapacityKey") as? Long) ?: 0L
}

@OptIn(ExperimentalForeignApi::class)
actual fun getFreeDiskSpace(): Long {
    val paths = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val url = paths.firstOrNull() ?: return 0L
    val values = url.resourceValuesForKeys(listOf("NSURLVolumeAvailableCapacityKey"), null)
    return (values?.get("NSURLVolumeAvailableCapacityKey") as? Long) ?: 0L
}
