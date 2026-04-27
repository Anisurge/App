package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.UpdateFileInfo
import to.kuudere.anisuge.data.models.UpdateResponse
import to.kuudere.anisuge.platform.AppBuildNumber
import to.kuudere.anisuge.platform.AppVersion
import to.kuudere.anisuge.platform.UpdateFileKey
import to.kuudere.anisuge.platform.UpdatePlatform
import to.kuudere.anisuge.platform.UpdateVariant

class UpdateService(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL = "https://www.anisurge.lol/api/app/updates"
        private const val CHANNEL = "stable"
    }

    suspend fun checkUpdate(): UpdateResponse? {
        return try {
            val response = httpClient.get(BASE_URL) {
                parameter("app", "anisurge")
                parameter("platform", UpdatePlatform)
                parameter("variant", UpdateVariant)
                parameter("channel", CHANNEL)
                parameter("currentBuild", AppBuildNumber)
                parameter("currentVersion", AppVersion)
            }.body<UpdateResponse>()

            response.copy(downloadUrl = response.pickDownloadUrl() ?: response.downloadUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun UpdateResponse.pickDownloadUrl(): String? {
        val keyedFiles = files.orEmpty()
        return keyedFiles[UpdateFileKey]?.url
            ?: keyedFiles["universal"]?.url
            ?: fileList.orEmpty().firstMatchingFile()?.url
    }

    private fun List<UpdateFileInfo>.firstMatchingFile(): UpdateFileInfo? {
        return firstOrNull { it.key == UpdateFileKey }
            ?: firstOrNull { it.arch == UpdateFileKey }
            ?: firstOrNull { it.key == "universal" }
            ?: firstOrNull { !it.url.isNullOrBlank() }
    }
}
