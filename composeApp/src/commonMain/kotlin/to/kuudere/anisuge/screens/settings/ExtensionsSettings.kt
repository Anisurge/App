package to.kuudere.anisuge.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.extensions.CloudflareBypassDialog
import to.kuudere.anisuge.extensions.CloudflareBypassButton
import to.kuudere.anisuge.extensions.ExtensionEngine
import to.kuudere.anisuge.extensions.ExtensionManager
import to.kuudere.anisuge.extensions.ExtensionRepository
import to.kuudere.anisuge.extensions.ExtensionSource
import to.kuudere.anisuge.extensions.ExtensionRuntimeState
import to.kuudere.anisuge.theme.AppColors

private const val DeleteAnimDurationMs = 280L

private fun extensionItemKey(source: ExtensionSource): String = "${source.engine.name}:${source.id}"

private val EngineAccent = mapOf(
    ExtensionEngine.ANIYOMI to Color(0xFF3F51B5),
    ExtensionEngine.CLOUDSTREAM to Color(0xFF42A5F5),
    ExtensionEngine.MANGAYOMI to Color(0xFFE53935),
    ExtensionEngine.SORA to Color(0xFF8D6E63),
)

@Composable
fun ExtensionsSettings(
    modifier: Modifier = Modifier,
    sortDialogVisible: Boolean? = null,
    onSortDialogVisibleChange: ((Boolean) -> Unit)? = null,
    runtimeDialogVisible: Boolean? = null,
    onRuntimeDialogVisibleChange: ((Boolean) -> Unit)? = null,
    repoDialogVisible: Boolean? = null,
    onRepoDialogVisibleChange: ((Boolean) -> Unit)? = null,
) {
    val manager = AppComponent.extensionManager
    val config by manager.config.collectAsState()
    val available by manager.available.collectAsState()
    val installed by manager.installed.collectAsState()
    val runtime by manager.runtime.state.collectAsState()
    val busy by manager.busy.collectAsState()
    val error by manager.error.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var localRepoDialog by remember { mutableStateOf(false) }
    var localRuntimeDialog by remember { mutableStateOf(false) }
    var localSortDialog by remember { mutableStateOf(false) }
    var localBypassDialog by remember { mutableStateOf(false) }
    var filterEngine by remember { mutableStateOf<ExtensionEngine?>(null) }
    var filterInstalled by remember { mutableStateOf<Boolean?>(null) }
    var showOnlyNsfw by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var animatingOutIds by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val chipScrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        runCatching { manager.refresh() }
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            operationMessage = null
        }
    }

    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            operationError = null
        }
    }

    val actualRepoDialog = repoDialogVisible ?: localRepoDialog
    val actualRuntimeDialog = runtimeDialogVisible ?: localRuntimeDialog
    val actualSortDialog = sortDialogVisible ?: localSortDialog
    fun changeRepoDialog(v: Boolean) {
        if (onRepoDialogVisibleChange != null) onRepoDialogVisibleChange(v) else localRepoDialog = v
    }
    fun changeRuntimeDialog(v: Boolean) {
        if (onRuntimeDialogVisibleChange != null) onRuntimeDialogVisibleChange(v) else localRuntimeDialog = v
    }
    fun changeSortDialog(v: Boolean) {
        if (onSortDialogVisibleChange != null) onSortDialogVisibleChange(v) else localSortDialog = v
    }

    fun guarded(action: suspend () -> Unit) {
        scope.launch {
            operationError = null
            operationMessage = null
            runCatching { action() }.onFailure { e ->
                val cause = generateSequence(e) { it.cause }.last()
                operationError = cause.message?.takeIf { it.isNotBlank() }
                    ?: cause::class.simpleName ?: "Extension operation failed"
            }
        }
    }

    fun uninstallWithAnimation(source: ExtensionSource) {
        val key = extensionItemKey(source)
        scope.launch {
            operationError = null
            operationMessage = null
            animatingOutIds = animatingOutIds + key
            delay(DeleteAnimDurationMs)
            runCatching { manager.uninstall(source) }.onFailure { e ->
                val cause = generateSequence(e) { it.cause }.last()
                operationError = cause.message?.takeIf { it.isNotBlank() }
                    ?: cause::class.simpleName ?: "Uninstall failed"
            }
            animatingOutIds = animatingOutIds - key
        }
    }

    if (actualRepoDialog) {
        RepoManagementDialog(
            config = config.repositories,
            runtimeState = runtime,
            manager = manager,
            onDismiss = { changeRepoDialog(false) },
            onRemoveRepo = { repo -> runCatching { manager.removeRepository(repo) } },
            onInstallRuntime = { guarded { manager.runtime.install(force = runtime.installed) } },
            onRemoveRuntime = { guarded { manager.runtime.remove() } },
        )
    }

    if (actualRuntimeDialog) {
        RuntimeStatusDialog(
            state = runtime,
            onDismiss = { changeRuntimeDialog(false) },
            onInstall = { guarded { manager.runtime.install(force = runtime.installed) } },
            onRemove = { guarded { manager.runtime.remove() } },
            onBypass = { localBypassDialog = true },
            logs = if (runtime.ready) manager.runtime.logs() else emptyList(),
        )
    }

    if (localBypassDialog) {
        CloudflareBypassDialog(
            onDismiss = { localBypassDialog = false },
            onBypass = { url ->
                localBypassDialog = false
                guarded { manager.runtime.openCloudflareBypass(url) }
            },
        )
    }

    if (actualSortDialog) {
        SortFilterDialog(
            currentEngine = filterEngine,
            currentInstalled = filterInstalled,
            showNsfw = showOnlyNsfw,
            onDismiss = { changeSortDialog(false) },
            onApply = { engine, installedFilter, nsfw ->
                filterEngine = engine
                filterInstalled = installedFilter
                showOnlyNsfw = nsfw
                changeSortDialog(false)
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("Search extensions", color = AppColors.textMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textMuted) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "Clear", tint = AppColors.textMuted)
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.accent,
                unfocusedBorderColor = AppColors.border,
                cursorColor = AppColors.accent,
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text,
                focusedPlaceholderColor = AppColors.textMuted,
                unfocusedPlaceholderColor = AppColors.textMuted,
            ),
        )
        Spacer(Modifier.height(8.dp))

        val filtered = available.filter { source ->
            val matchesQuery = query.isBlank() || source.name.contains(query, ignoreCase = true) ||
                source.language.contains(query, ignoreCase = true) ||
                source.engine.displayName.contains(query, ignoreCase = true)
            val matchesEngine = filterEngine == null || source.engine == filterEngine
            val matchesInstalled = filterInstalled == null || source.installed == filterInstalled
            val matchesNsfw = !showOnlyNsfw || source.isNsfw
            matchesQuery && matchesEngine && matchesInstalled && matchesNsfw
        }
        val installedItems = filtered.filter { it.installed }.sortedBy { it.name.lowercase() }
        val availableItems = filtered.filter { !it.installed }.sortedBy { it.name.lowercase() }
        val updateCount = installedItems.count { it.hasUpdate }

        if (busy && installedItems.isEmpty() && availableItems.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.accent)
            }
        } else if (installedItems.isEmpty() && availableItems.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (config.repositories.isEmpty()) "Add a repository to discover extensions."
                        else "No extensions found.",
                        color = AppColors.textMuted,
                    )
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (updateCount > 0) {
                        item(key = "updates_banner") {
                            UpdatesAvailableBanner(
                                count = updateCount,
                                isBusy = busy,
                                onUpdateAll = {
                                    guarded {
                                        val count = manager.updateAllAvailable()
                                        operationMessage = if (count > 0) {
                                            "Updated $count extension${if (count == 1) "" else "s"}"
                                        } else {
                                            "No updates available"
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (installedItems.isNotEmpty()) {
                        item(key = "header_installed") {
                            ExtensionSectionHeader(
                                title = "Installed",
                                count = installedItems.size,
                            )
                        }
                        extensionListItems(
                            items = installedItems,
                            animatingOutIds = animatingOutIds,
                            busy = busy,
                            chipScrollState = chipScrollState,
                            onInstall = { source -> guarded { manager.install(source) } },
                            onUpdate = { source ->
                                guarded {
                                    manager.update(source)
                                    operationMessage = "Updated ${source.name}"
                                }
                            },
                            onUninstall = ::uninstallWithAnimation,
                            onRepair = { source ->
                                guarded { operationMessage = manager.repairSource(source) }
                            },
                        )
                    }
                    if (availableItems.isNotEmpty()) {
                        item(key = "header_available") {
                            ExtensionSectionHeader(
                                title = "Not installed",
                                count = availableItems.size,
                            )
                        }
                        extensionListItems(
                            items = availableItems,
                            animatingOutIds = animatingOutIds,
                            busy = busy,
                            chipScrollState = chipScrollState,
                            onInstall = { source -> guarded { manager.install(source) } },
                            onUpdate = { _ -> },
                            onUninstall = ::uninstallWithAnimation,
                            onRepair = { _ -> },
                        )
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }
}

private fun LazyListScope.extensionListItems(
    items: List<ExtensionSource>,
    animatingOutIds: Set<String>,
    busy: Boolean,
    chipScrollState: ScrollState,
    onInstall: (ExtensionSource) -> Unit,
    onUpdate: (ExtensionSource) -> Unit,
    onUninstall: (ExtensionSource) -> Unit,
    onRepair: (ExtensionSource) -> Unit,
) {
    items(items, key = { extensionItemKey(it) }) { source ->
        val itemKey = extensionItemKey(source)
        AnimatedVisibility(
            visible = itemKey !in animatingOutIds,
            enter = EnterTransition.None,
            exit = shrinkVertically(animationSpec = tween(DeleteAnimDurationMs.toInt())) +
                fadeOut(animationSpec = tween(DeleteAnimDurationMs.toInt())),
            modifier = Modifier.animateItem(),
        ) {
            ExtensionSourceCard(
                source = source,
                isBusy = busy || itemKey in animatingOutIds,
                chipScrollState = chipScrollState,
                onInstall = { onInstall(source) },
                onUpdate = { onUpdate(source) },
                onUninstall = { onUninstall(source) },
                onRepair = { onRepair(source) },
            )
        }
    }
}

@Composable
private fun ExtensionSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = AppColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            color = AppColors.textMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun UpdatesAvailableBanner(
    count: Int,
    isBusy: Boolean,
    onUpdateAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Update, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "$count update${if (count == 1) "" else "s"} available",
            color = AppColors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUpdateAll, enabled = !isBusy) {
            Text("Update all", color = AppColors.accent)
        }
    }
}

@Composable
internal fun HeaderActionButton(icon: ImageVector, contentDesc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.accent.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Icon(icon, contentDesc, tint = AppColors.accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExtensionSourceCard(
    source: ExtensionSource,
    isBusy: Boolean = false,
    chipScrollState: ScrollState,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onRepair: () -> Unit,
) {
    val accent = EngineAccent[source.engine] ?: AppColors.accent
    val engineName = source.engine.displayName
    var installing by remember { mutableStateOf(false) }
    LaunchedEffect(isBusy) {
        if (!isBusy) installing = false
    }
    val showSpinner = installing && isBusy

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIcon(source = source, accent = accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip(text = engineName, color = accent)
                    InfoChip(text = source.language.uppercase(), color = AppColors.textMuted)
                    if (source.version.isNotBlank()) {
                        InfoChip(
                            text = if (source.version.startsWith("v")) source.version else "v${source.version}",
                            color = AppColors.accent,
                        )
                    }
                    if (source.hasUpdate) {
                        InfoChip(text = "Update", color = AppColors.accent)
                    }
                    if (source.isNsfw) {
                        InfoChip(text = "18+", color = Color(0xFFE53935))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (source.installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (source.hasUpdate) {
                        ActionIconButton(
                            icon = Icons.Default.Update,
                            containerColor = AppColors.accent.copy(alpha = 0.2f),
                            iconColor = AppColors.accent,
                            contentDesc = "Update",
                            onClick = onUpdate,
                            enabled = !isBusy,
                        )
                    }
                    ActionIconButton(
                        icon = Icons.Default.Refresh,
                        containerColor = AppColors.surfaceVariant,
                        iconColor = AppColors.text,
                        contentDesc = "Repair",
                        onClick = onRepair,
                        enabled = !isBusy,
                    )
                    ActionIconButton(
                        icon = Icons.Default.Delete,
                        containerColor = Color(0xFFE53935).copy(alpha = 0.2f),
                        iconColor = Color(0xFFE53935),
                        contentDesc = "Uninstall",
                        onClick = onUninstall,
                        enabled = !isBusy,
                    )
                }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (source.engine.nativeRuntime) AppColors.accent.copy(alpha = 0.2f)
                                else AppColors.surfaceVariant
                            ),
                    ) {
                        if (showSpinner) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = AppColors.accent,
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    installing = true
                                    onInstall()
                                },
                                enabled = source.engine.nativeRuntime,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.Download, null, tint =
                                    if (source.engine.nativeRuntime) AppColors.accent else AppColors.textMuted,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun ExtensionIcon(source: ExtensionSource, accent: Color, modifier: Modifier = Modifier) {
    val outerSize = 48.dp
    val innerSize = 44.dp
    val badgeSize = 14.dp
    val dotSize = 8.dp

    Box(
        modifier = modifier.size(outerSize).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(innerSize).clip(RoundedCornerShape(10.dp)).background(AppColors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!source.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = source.iconUrl,
                    contentDescription = source.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(dotSize).clip(CircleShape).background(AppColors.surface),
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    contentDesc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
            Icon(icon, contentDesc, tint = iconColor, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RepoManagementDialog(
    config: List<ExtensionRepository>,
    runtimeState: ExtensionRuntimeState,
    manager: ExtensionManager,
    onDismiss: () -> Unit,
    onRemoveRepo: suspend (ExtensionRepository) -> Unit,
    onInstallRuntime: () -> Unit,
    onRemoveRuntime: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf<String?>(null) }
    var showWarning by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Third-party extension warning") },
            text = {
                Text(
                    "Extensions execute third-party code and may access provider websites. " +
                        "Only add repositories you trust. Anisurge does not provide, review, or endorse their content.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showWarning = false
                    scope.launch {
                        adding = true
                        repoError = null
                        try {
                            manager.acceptWarning()
                            manager.addRepositoryAutoDetect(pendingUrl.trim())
                            url = ""
                            onDismiss()
                        } catch (e: Throwable) {
                            val cause = generateSequence(e) { it.cause }.last()
                            repoError = cause.message?.takeIf { it.isNotBlank() }
                                ?: cause::class.simpleName ?: "Failed to add repository"
                        } finally {
                            adding = false
                        }
                    }
                }) { Text("I understand") }
            },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } },
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()).width(400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repositories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                RuntimeStatusInline(state = runtimeState, onInstall = onInstallRuntime, onRemove = onRemoveRuntime)

                Text("ADD REPOSITORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://raw.githubusercontent.com/...", color = AppColors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border,
                        cursorColor = AppColors.accent,
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                    ),
                )

                Button(
                    onClick = {
                        val repoUrl = url.trim()
                        if (!repoUrl.startsWith("https://") && !repoUrl.startsWith("http://")) {
                            repoError = "Enter a valid HTTP/HTTPS URL"
                            return@Button
                        }
                        if (config.any { it.url == repoUrl }) {
                            repoError = "Repository already added"
                            return@Button
                        }
                        if (!manager.config.value.acceptedWarning) {
                            pendingUrl = repoUrl
                            showWarning = true
                            return@Button
                        }
                        adding = true
                        repoError = null
                        scope.launch {
                            try {
                                manager.addRepositoryAutoDetect(repoUrl)
                                url = ""
                                onDismiss()
                            } catch (e: Throwable) {
                                val cause = generateSequence(e) { it.cause }.last()
                                repoError = cause.message?.takeIf { it.isNotBlank() }
                                    ?: cause::class.simpleName ?: "Failed to add repository"
                            } finally {
                                adding = false
                            }
                        }
                    },
                    enabled = url.isNotBlank() && !adding,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Repository")
                }
                if (adding) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                repoError?.let {
                    Text(it, color = AppColors.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "Engine type is detected automatically from the repository content.",
                    color = AppColors.textMuted, fontSize = 11.sp,
                )

                if (config.isEmpty()) {
                    Text("No repositories yet. Add one to discover extensions.", color = AppColors.textMuted, fontSize = 13.sp)
                } else {
                    config.forEach { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(AppColors.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Extension, null, tint = AppColors.textMuted, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    repo.name ?: repo.engine.displayName,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    repo.url,
                                    color = AppColors.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch { onRemoveRepo(repo) }
                            }) {
                                Icon(Icons.Default.Delete, "Remove", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusInline(
    state: ExtensionRuntimeState,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.installed) AppColors.accent.copy(alpha = 0.2f)
                            else Color(0xFFE53935).copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.text,
                        )
                    } else {
                        Icon(
                            if (state.installed) Icons.Default.Download else Icons.Default.Warning,
                            null,
                            tint = if (state.installed) AppColors.accent else Color(0xFFE53935),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.busy) "Downloading Plugin..."
                        else if (state.installed) "Plugin Installed"
                        else "Plugin Not Installed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        if (state.busy) state.status
                        else if (state.installed) "All 4 engine types ready"
                        else "Download plugin to unlock extensions",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (state.busy && state.progress > 0f) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AppColors.accent,
                    trackColor = AppColors.surfaceVariant,
                )
            }
            state.error?.let {
                Text(it, color = Color(0xFFE53935), fontSize = 12.sp)
            }
            if (state.installed && !state.busy) {
                state.version?.let { v ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Version", color = AppColors.textMuted, fontSize = 12.sp)
                        Text(v, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeStatusDialog(
    state: ExtensionRuntimeState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onBypass: () -> Unit,
    logs: List<String>,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()).width(400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Runtime Helper", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                RuntimeStatusInline(state = state, onInstall = onInstall, onRemove = onRemove)

                if (state.installed && !state.busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                        ) {
                            Text("Repair / Update", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onRemove,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Remove", fontSize = 12.sp, color = Color(0xFFE53935))
                        }
                    }
                } else if (!state.installed && !state.busy) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install Plugin")
                    }
                }

                if (logs.isNotEmpty()) {
                    Text("LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            logs.takeLast(6).forEach { line ->
                                Text(line, fontSize = 11.sp, color = AppColors.textMuted, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                if (state.installed && !state.busy) {
                    CloudflareBypassButton(onBypass = onBypass)
                }

                Text(
                    "All 4 engine types (Aniyomi, CloudStream, Mangayomi, Sora) are supported.",
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                )

                Text(state.status, color = AppColors.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SortFilterDialog(
    currentEngine: ExtensionEngine?,
    currentInstalled: Boolean?,
    showNsfw: Boolean,
    onDismiss: () -> Unit,
    onApply: (ExtensionEngine?, Boolean?, Boolean) -> Unit,
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var selectedInstalled by remember { mutableStateOf(currentInstalled) }
    var selectedNsfw by remember { mutableStateOf(showNsfw) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.background),
        ) {
            Column(
                Modifier.padding(20.dp).width(350.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort & Filter", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.textMuted)
                    }
                }

                Text("SOURCE TYPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = selectedEngine == null,
                        onClick = { selectedEngine = null },
                        label = { Text("All", fontSize = 12.sp) },
                    )
                    ExtensionEngine.entries.forEach { value ->
                        FilterChip(
                            selected = selectedEngine == value,
                            onClick = { selectedEngine = value },
                            label = { Text(value.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = (EngineAccent[value] ?: AppColors.accent).copy(alpha = 0.3f),
                            ),
                        )
                    }
                }

                Text("STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedInstalled == null, onClick = { selectedInstalled = null }, label = { Text("All", fontSize = 12.sp) })
                    FilterChip(selected = selectedInstalled == true, onClick = { selectedInstalled = true }, label = { Text("Installed", fontSize = 12.sp) })
                    FilterChip(selected = selectedInstalled == false, onClick = { selectedInstalled = false }, label = { Text("Available", fontSize = 12.sp) })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = selectedNsfw,
                        onCheckedChange = { selectedNsfw = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AppColors.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Show 18+ only", fontSize = 13.sp)
                }

                Button(
                    onClick = { onApply(selectedEngine, selectedInstalled, selectedNsfw) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
