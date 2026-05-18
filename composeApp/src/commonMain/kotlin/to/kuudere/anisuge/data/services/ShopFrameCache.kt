package to.kuudere.anisuge.data.services

import to.kuudere.anisuge.platform.KmpFileSystem
import to.kuudere.anisuge.utils.getCacheDirectory

object ShopFrameCache {
    private fun dir(): String = "${getCacheDirectory()}/shop-frames"

    fun pathFor(itemId: String): String = "${dir()}/${itemId.replace(Regex("[^a-zA-Z0-9-]"), "")}.png"

    fun save(itemId: String, bytes: ByteArray) {
        val base = dir()
        if (!KmpFileSystem.exists(base)) {
            KmpFileSystem.createDirectories(base, mustCreate = true)
        }
        KmpFileSystem.write(pathFor(itemId), bytes)
    }

    fun localPathIfExists(itemId: String): String? {
        val path = pathFor(itemId)
        return if (KmpFileSystem.exists(path)) path else null
    }
}
