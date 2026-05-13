package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Sink
import okio.sink
import okio.buffer
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.Foundation.NSURL
import to.kuudere.anisuge.BuildConfig
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSBundle

actual val isDesktopPlatform: Boolean = false
actual val isAndroidTvPlatform: Boolean = false

actual val PlatformName: String = "iOS"
actual val UpdatePlatform: String = "ios"
actual val UpdateVariant: String = "ios"
actual val UpdateFileKey: String = "ipa"

actual val AppVersion: String by lazy {
    NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
        ?: BuildConfig.APP_VERSION
}

actual val AppBuildNumber: Int by lazy {
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull()
        ?: BuildConfig.APP_BUILD_NUMBER
}

@Composable
actual fun LockScreenOrientation(landscape: Boolean) {
    // iOS orientation handled via Info.plist / AppDelegate
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS back gesture handled by system
}

@Composable
actual fun SyncFullscreen(isFullscreen: Boolean) {
    // iOS fullscreen handled by AVPlayerViewController or system
}

@Composable
actual fun SyncCursorHidden(hidden: Boolean) {
    // No cursor on iOS
}

internal actual fun internalOpenUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>()) { _ -> }
}

actual fun isFolderWritable(path: String): Boolean = true

actual fun persistFolderPermission(path: String) {
    // No-op on iOS
}

actual fun updateDownloadNotification(
    activeTasksCount: Int,
    totalProgress: Float,
    isInitial: Boolean
) {
    // TODO: iOS background download notifications
}

actual fun clearDownloadNotification() {
    // TODO
}

actual fun updateSyncProgressNotification(
    title: String,
    statusText: String,
    progressCurrent: Int,
    progressMax: Int,
) {
    // TODO: iOS background sync notifications
}

actual fun clearSyncProgressNotification() {
    // TODO
}

actual object KmpFileSystem {
    @OptIn(ExperimentalForeignApi::class)
    actual fun exists(path: String): Boolean {
        return platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun createDirectories(path: String, mustCreate: Boolean) {
        platform.Foundation.NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    actual fun source(path: String): okio.Source {
        return platform.Foundation.NSInputStream.inputStreamWithFileAtPath(path)
            ?.source()
            ?: throw IllegalStateException("Could not open $path")
    }

    actual fun sink(path: String, append: Boolean): Sink {
        return platform.Foundation.NSOutputStream.outputStreamToFileAtPath(path, append)
            .sink()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String, mustExist: Boolean) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun write(path: String, data: ByteArray) {
        platform.Foundation.NSData.dataWithBytes(data.toUByteArray().asCPointer(), data.size.toULong())
            .writeToFile(path, atomically = true)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun listDir(path: String): List<String> {
        val fm = platform.Foundation.NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(path, null) ?: return emptyList()
        return (contents as List<*>).filterIsInstance<String>()
    }
}

actual fun startNotificationListenerService() {
    // No-op on iOS — FCM handled by APNs directly
}

actual fun stopNotificationListenerService() {
    // No-op
}

actual fun randomInstallUuid(): String {
    val stored = NSUserDefaults.standardUserDefaults.stringForKey("anisurge_install_uuid")
    if (stored != null) return stored
    val uuid = NSUUID().UUIDString()
    NSUserDefaults.standardUserDefaults.setObject(uuid, forKey = "anisurge_install_uuid")
    return uuid
}

actual fun analyticsPingOs(): String? {
    val device = UIDevice.currentDevice
    return "${device.systemName} ${device.systemVersion} ${device.model}".take(128)
}
