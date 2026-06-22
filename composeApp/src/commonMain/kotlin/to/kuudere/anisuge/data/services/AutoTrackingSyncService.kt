package to.kuudere.anisuge.data.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import to.kuudere.anisuge.data.models.AutoSyncStatus
import to.kuudere.anisuge.data.models.TrackerSyncAction
import to.kuudere.anisuge.data.models.TrackerSyncJob
import to.kuudere.anisuge.utils.currentTimeMillis

class AutoTrackingSyncService(
    private val settingsStore: SettingsStore,
    private val trackingService: TrackingService,
    private val idCache: MalAnilistIdCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(AutoSyncStatus())
    val status: StateFlow<AutoSyncStatus> = _status

    init {
        scope.launch {
            settingsStore.trackerAutoSyncFlow.collect { enabled ->
                refreshStatus(enabled)
                if (enabled) retryPending()
            }
        }
        scope.launch {
            settingsStore.trackerSyncQueueFlow.collect { refreshStatus() }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        settingsStore.setTrackerAutoSync(enabled)
        if (enabled) retryPending()
    }

    suspend fun isEnabled(): Boolean = settingsStore.trackerAutoSyncFlow.first()

    suspend fun enqueueUpsert(
        animeId: String,
        malId: Int?,
        anilistId: Int?,
        status: String?,
        progress: Int? = null,
        totalEpisodes: Int? = null,
        startedAt: String? = null,
        completedAt: String? = null,
    ) {
        if (!settingsStore.trackerAutoSyncFlow.first()) return
        enqueue(
            TrackerSyncJob(
                key = syncKey(animeId, malId, anilistId),
                action = TrackerSyncAction.UPSERT,
                animeId = animeId,
                malId = malId?.takeIf { it > 0 },
                anilistId = anilistId?.takeIf { it > 0 },
                status = status,
                progress = progress,
                totalEpisodes = totalEpisodes,
                startedAt = startedAt,
                completedAt = completedAt,
                pendingMal = !settingsStore.getMalAccessToken().isNullOrBlank(),
                pendingAnilist = !settingsStore.getAnilistAccessToken().isNullOrBlank(),
                updatedAt = currentTimeMillis(),
            )
        )
    }

    suspend fun enqueueDelete(animeId: String, malId: Int?, anilistId: Int?) {
        if (!settingsStore.trackerAutoSyncFlow.first()) return
        enqueue(
            TrackerSyncJob(
                key = syncKey(animeId, malId, anilistId),
                action = TrackerSyncAction.DELETE,
                animeId = animeId,
                malId = malId?.takeIf { it > 0 },
                anilistId = anilistId?.takeIf { it > 0 },
                pendingMal = !settingsStore.getMalAccessToken().isNullOrBlank(),
                pendingAnilist = !settingsStore.getAnilistAccessToken().isNullOrBlank(),
                updatedAt = currentTimeMillis(),
            )
        )
    }

    suspend fun retryPending() {
        mutex.withLock {
            if (!settingsStore.trackerAutoSyncFlow.first()) return
            val jobs = settingsStore.trackerSyncQueueFlow.first().toMutableList()
            for (index in jobs.indices) {
                jobs[index] = process(jobs[index])
                settingsStore.setTrackerSyncQueue(jobs.filter { it.pendingMal || it.pendingAnilist })
            }
            refreshStatus()
        }
    }

    private suspend fun enqueue(job: TrackerSyncJob) {
        mutex.withLock {
            val jobs = settingsStore.trackerSyncQueueFlow.first().toMutableList()
            val old = jobs.firstOrNull { it.key == job.key }
            jobs.removeAll { it.key == job.key }
            jobs += coalesceTrackerJobs(old, job)
            settingsStore.setTrackerSyncQueue(jobs)
        }
        scope.launch { retryPending() }
    }

    private suspend fun process(input: TrackerSyncJob): TrackerSyncJob {
        var job = resolveIds(input)
        if (job.pendingMal) {
            val ok = job.malId?.let { id ->
                if (job.action == TrackerSyncAction.DELETE) trackingService.deleteMalEntry(id)
                else trackingService.updateMalEntry(
                    id, job.status, job.progress, job.totalEpisodes, job.startedAt, job.completedAt
                )
            } ?: false
            job = job.copy(pendingMal = !ok, malError = if (ok) null else "MAL sync pending")
        }
        if (job.pendingAnilist) {
            val ok = job.anilistId?.let { id ->
                if (job.action == TrackerSyncAction.DELETE) trackingService.deleteAnilistEntry(id)
                else trackingService.updateAnilistEntry(
                    id, job.status, job.progress, job.totalEpisodes, job.startedAt, job.completedAt
                )
            } ?: false
            job = job.copy(pendingAnilist = !ok, anilistError = if (ok) null else "AniList sync pending")
        }
        if (!job.pendingMal && !job.pendingAnilist) {
            settingsStore.setTrackerSyncLastSuccess(currentTimeMillis())
        }
        return job
    }

    private suspend fun resolveIds(input: TrackerSyncJob): TrackerSyncJob {
        var malId = input.malId
        var anilistId = input.anilistId
        if (anilistId == null && malId != null) {
            anilistId = idCache.getAll()[malId] ?: trackingService.lookupAnilistIdFromMal(malId)
        }
        if (malId == null && anilistId != null) {
            malId = idCache.getMalForAnilist(anilistId)
        }
        if (malId != null && anilistId != null) idCache.put(malId, anilistId)
        return input.copy(malId = malId, anilistId = anilistId)
    }

    private suspend fun refreshStatus(enabledOverride: Boolean? = null) {
        val jobs = settingsStore.trackerSyncQueueFlow.first()
        _status.update {
            AutoSyncStatus(
                enabled = enabledOverride ?: settingsStore.trackerAutoSyncFlow.first(),
                pendingCount = jobs.size,
                lastSuccessAt = settingsStore.trackerSyncLastSuccessFlow.first(),
                malError = jobs.firstNotNullOfOrNull { it.malError },
                anilistError = jobs.firstNotNullOfOrNull { it.anilistError },
            )
        }
    }

    private fun syncKey(animeId: String, malId: Int?, anilistId: Int?): String =
        anilistId?.takeIf { it > 0 }?.let { "anilist:$it" }
            ?: malId?.takeIf { it > 0 }?.let { "mal:$it" }
            ?: "anime:${animeId.lowercase()}"
}

internal fun coalesceTrackerJobs(
    old: TrackerSyncJob?,
    latest: TrackerSyncJob,
): TrackerSyncJob = latest.copy(
    malId = latest.malId ?: old?.malId,
    anilistId = latest.anilistId ?: old?.anilistId,
    progress = latest.progress ?: old?.progress,
    totalEpisodes = latest.totalEpisodes ?: old?.totalEpisodes,
    startedAt = latest.startedAt ?: old?.startedAt,
    completedAt = latest.completedAt ?: old?.completedAt,
)
