package to.kuudere.anisuge.screens.update

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.services.UpdateService
import to.kuudere.anisuge.platform.AppVersion

data class UpdateState(
    val currentVersion: String = AppVersion,
    val newVersion: String = "",
    val changelog: List<String> = emptyList(),
    val isUpdateAvailable: Boolean? = null, // null = checking, true = yes, false = no
    val downloadUrl: String? = null,
    val isCritical: Boolean = false,
    val isRequired: Boolean = false,
)

class UpdateViewModel(private val updateService: UpdateService) : ViewModel() {
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        checkUpdate()
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        val remoteClean = remote.substringBefore('-').trim()
        val localClean = local.substringBefore('-').trim()
        val remoteParts = remoteClean.split('.').mapNotNull { it.toIntOrNull() }
        val localParts = localClean.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val remoteVal = remoteParts.getOrElse(i) { 0 }
            val localVal = localParts.getOrElse(i) { 0 }
            if (remoteVal > localVal) return true
            if (remoteVal < localVal) return false
        }
        return false
    }

    private fun checkUpdate() = viewModelScope.launch {
        val response = updateService.checkUpdate()

        if (response == null || response.success == false) {
            _state.value = _state.value.copy(isUpdateAvailable = false)
            return@launch
        }

        val remoteVersion = response.latestVersion ?: response.version ?: ""
        val isAvailable = isNewerVersion(remoteVersion, AppVersion)
        val releaseNotes = response.changelog
            ?: response.message
            ?: response.releaseNotes
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
            ?: emptyList()

        _state.value = _state.value.copy(
            newVersion = remoteVersion,
            changelog = releaseNotes,
            isUpdateAvailable = isAvailable,
            downloadUrl = response.downloadUrl,
            isCritical = response.critical == true || response.required == true,
            isRequired = response.required == true,
        )
    }
}
